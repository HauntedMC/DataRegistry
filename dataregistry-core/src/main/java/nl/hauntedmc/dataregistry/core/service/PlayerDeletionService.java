package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Permanently removes one offline canonical player identity for administrative/debugging use.
 *
 * <p>The service discovers every direct foreign key exported by {@code player_entity.id} at runtime. This includes
 * feature-owned tables outside DataRegistry, so adding a new player reference does not require maintaining a hardcoded
 * delete list. Restrict/no-action dependencies are removed explicitly in dependency order, while cascade/set-null
 * relationships retain their declared database semantics.</p>
 */
public final class PlayerDeletionService {

    private static final String PLAYER_TABLE = "player_entity";
    private static final String PLAYER_ID_COLUMN = "id";
    private static final String PLAYER_UUID_COLUMN = "uuid";
    private static final String ONLINE_STATUS_TABLE = "player_online_status";
    private static final List<LogicalPlayerTable> LOGICAL_PLAYER_TABLES = List.of(
            new LogicalPlayerTable("player_lifecycle_outbox", "player_id"),
            new LogicalPlayerTable("population_transition", "player_id")
    );

    private final DataRegistry dataRegistry;
    private final PlayerService playerService;
    private final ILoggerAdapter logger;

    public PlayerDeletionService(DataRegistry dataRegistry, PlayerService playerService, ILoggerAdapter logger) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    /**
     * Deletes an offline player and all rows that directly depend on its canonical player id.
     *
     * @param identity identity resolved immediately before this administrative operation.
     * @return deletion details for staff feedback and diagnostics.
     * @throws IllegalStateException when the player is active, durably online, stale, or cannot be safely deleted.
     */
    public PlayerDeletionResult delete(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        String uuid = identity.uuid().toString();
        requireInactive(uuid);

        PlayerDeletionResult result = dataRegistry.getORM().runInTransaction(session ->
                session.doReturningWork(connection -> deleteInTransaction(connection, identity))
        );

        // Defensive cache eviction after commit. The command requires an offline player, but this prevents an old
        // managed identity from surviving if another internal caller invokes the service after stale cache state.
        playerService.onPlayerQuit(identity.username(), uuid);
        logger.warn(
                "Permanently deleted DataRegistry player identity #" + identity.playerId() +
                        " uuid=" + Sanitization.safeForLog(uuid) +
                        " username=" + Sanitization.safeForLog(identity.username()) +
                        " with " + result.deletedDependentRows() + " explicitly removed dependent row(s)."
        );
        return result;
    }

    private PlayerDeletionResult deleteInTransaction(Connection connection, PlayerIdentity identity) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        TableRef playerTable = resolveTable(metadata, connection, PLAYER_TABLE);
        if (playerTable == null) {
            throw new IllegalStateException("Canonical player table '" + PLAYER_TABLE + "' was not found.");
        }

        lockAndValidatePlayer(connection, metadata, playerTable, identity);
        requireInactive(identity.uuid().toString());
        requireDurablyOffline(connection, metadata, playerTable, identity.playerId());

        List<ForeignKeyReference> playerReferences = exportedKeys(metadata, playerTable).stream()
                .filter(reference -> PLAYER_ID_COLUMN.equalsIgnoreCase(reference.parentColumn()))
                .toList();
        validateDependencyNamespace(playerTable, playerReferences);

        List<ForeignKeyReference> explicitReferences = playerReferences.stream()
                .filter(reference -> requiresExplicitDelete(reference.deleteRule()))
                .toList();
        List<TableRef> deletionOrder = orderDependentTables(metadata, explicitReferences);
        Map<TableRef, List<ForeignKeyReference>> referencesByTable = groupByTable(explicitReferences);

        LinkedHashMap<String, Integer> deletedRows = new LinkedHashMap<>();
        for (TableRef table : deletionOrder) {
            List<ForeignKeyReference> references = referencesByTable.getOrDefault(table, List.of());
            for (ForeignKeyReference reference : references) {
                int deleted = deleteByPlayerId(
                        connection,
                        metadata,
                        reference.table(),
                        reference.foreignKeyColumn(),
                        identity.playerId()
                );
                mergeDeletedRows(deletedRows, reference.table().name(), deleted);
            }
        }

        for (LogicalPlayerTable logicalTable : LOGICAL_PLAYER_TABLES) {
            TableRef table = resolveTable(metadata, connection, logicalTable.tableName());
            if (table == null || !sameNamespace(playerTable, table)) {
                continue;
            }
            int deleted = deleteByPlayerId(
                    connection,
                    metadata,
                    table,
                    logicalTable.playerIdColumn(),
                    identity.playerId()
            );
            mergeDeletedRows(deletedRows, table.name(), deleted);
        }

        int rootDeleted = deleteCanonicalPlayer(connection, metadata, playerTable, identity);
        if (rootDeleted != 1) {
            throw new IllegalStateException(
                    "Player identity changed while it was being deleted; transaction was rolled back."
            );
        }
        return new PlayerDeletionResult(identity, deletedRows);
    }

    private void requireInactive(String uuid) {
        if (playerService.getActivePlayer(uuid).isPresent()) {
            throw new IllegalStateException("Player is active in DataRegistry and must be offline before deletion.");
        }
    }

    private static void lockAndValidatePlayer(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef playerTable,
            PlayerIdentity identity
    ) throws SQLException {
        String sql = "SELECT " + quoteIdentifier(metadata, PLAYER_UUID_COLUMN) +
                " FROM " + quoteTable(metadata, playerTable) +
                " WHERE " + quoteIdentifier(metadata, PLAYER_ID_COLUMN) + " = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, identity.playerId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Player identity no longer exists.");
                }
                String storedUuid = resultSet.getString(1);
                if (!identity.uuid().toString().equalsIgnoreCase(storedUuid)) {
                    throw new IllegalStateException(
                            "Resolved player identity is stale; transaction was rolled back without deleting data."
                    );
                }
            }
        }
    }

    private static void requireDurablyOffline(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef playerTable,
            long playerId
    ) throws SQLException {
        TableRef onlineStatusTable = resolveTable(metadata, connection, ONLINE_STATUS_TABLE);
        if (onlineStatusTable == null || !sameNamespace(playerTable, onlineStatusTable)) {
            return;
        }
        String sql = "SELECT " + quoteIdentifier(metadata, "online") +
                " FROM " + quoteTable(metadata, onlineStatusTable) +
                " WHERE " + quoteIdentifier(metadata, "player_id") + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getBoolean(1)) {
                    throw new IllegalStateException(
                            "Player is marked online in durable DataRegistry state and cannot be deleted."
                    );
                }
            }
        }
    }

    private static int deleteCanonicalPlayer(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef playerTable,
            PlayerIdentity identity
    ) throws SQLException {
        String sql = "DELETE FROM " + quoteTable(metadata, playerTable) +
                " WHERE " + quoteIdentifier(metadata, PLAYER_ID_COLUMN) + " = ?" +
                " AND " + quoteIdentifier(metadata, PLAYER_UUID_COLUMN) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, identity.playerId());
            statement.setString(2, identity.uuid().toString());
            return statement.executeUpdate();
        }
    }

    private static int deleteByPlayerId(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef table,
            String playerIdColumn,
            long playerId
    ) throws SQLException {
        String sql = "DELETE FROM " + quoteTable(metadata, table) +
                " WHERE " + quoteIdentifier(metadata, playerIdColumn) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, playerId);
            return statement.executeUpdate();
        }
    }

    private static List<TableRef> orderDependentTables(
            DatabaseMetaData metadata,
            List<ForeignKeyReference> references
    ) throws SQLException {
        Set<TableRef> tables = new HashSet<>();
        references.forEach(reference -> tables.add(reference.table()));
        List<TableRef> sortedRoots = tables.stream()
                .sorted(Comparator.comparing(TableRef::sortKey))
                .toList();
        List<TableRef> ordered = new ArrayList<>();
        Set<TableRef> visiting = new HashSet<>();
        Set<TableRef> visited = new HashSet<>();
        for (TableRef table : sortedRoots) {
            appendDependentTable(metadata, table, tables, visiting, visited, ordered);
        }
        return ordered;
    }

    private static void appendDependentTable(
            DatabaseMetaData metadata,
            TableRef table,
            Set<TableRef> directPlayerTables,
            Set<TableRef> visiting,
            Set<TableRef> visited,
            List<TableRef> ordered
    ) throws SQLException {
        if (visited.contains(table)) {
            return;
        }
        if (!visiting.add(table)) {
            throw new IllegalStateException(
                    "Cyclic foreign-key dependency detected while planning player deletion around table '" +
                            table.name() + "'."
            );
        }
        List<TableRef> dependents = exportedKeys(metadata, table).stream()
                .map(ForeignKeyReference::table)
                .filter(directPlayerTables::contains)
                .distinct()
                .sorted(Comparator.comparing(TableRef::sortKey))
                .toList();
        for (TableRef dependent : dependents) {
            appendDependentTable(metadata, dependent, directPlayerTables, visiting, visited, ordered);
        }
        visiting.remove(table);
        visited.add(table);
        ordered.add(table);
    }

    private static Map<TableRef, List<ForeignKeyReference>> groupByTable(List<ForeignKeyReference> references) {
        Map<TableRef, List<ForeignKeyReference>> grouped = new HashMap<>();
        for (ForeignKeyReference reference : references) {
            grouped.computeIfAbsent(reference.table(), ignored -> new ArrayList<>()).add(reference);
        }
        return grouped;
    }

    private static List<ForeignKeyReference> exportedKeys(DatabaseMetaData metadata, TableRef parent) throws SQLException {
        List<ForeignKeyReference> references = new ArrayList<>();
        try (ResultSet resultSet = metadata.getExportedKeys(parent.catalog(), parent.schema(), parent.name())) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("FKTABLE_NAME");
                String columnName = resultSet.getString("FKCOLUMN_NAME");
                String parentColumn = resultSet.getString("PKCOLUMN_NAME");
                if (tableName == null || tableName.isBlank() || columnName == null || columnName.isBlank()) {
                    continue;
                }
                references.add(new ForeignKeyReference(
                        new TableRef(
                                resultSet.getString("FKTABLE_CAT"),
                                resultSet.getString("FKTABLE_SCHEM"),
                                tableName
                        ),
                        columnName,
                        parentColumn,
                        resultSet.getShort("DELETE_RULE"),
                        resultSet.getString("FK_NAME")
                ));
            }
        }
        return references;
    }

    private static TableRef resolveTable(DatabaseMetaData metadata, Connection connection, String tableName)
            throws SQLException {
        String currentCatalog = connection.getCatalog();
        String currentSchema = connection.getSchema();
        TableRef exact = findTable(metadata, currentCatalog, currentSchema, tableName);
        if (exact != null) {
            return exact;
        }
        exact = findTable(metadata, currentCatalog, null, tableName);
        if (exact != null) {
            return exact;
        }
        try (ResultSet resultSet = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String candidateName = resultSet.getString("TABLE_NAME");
                if (candidateName != null && candidateName.equalsIgnoreCase(tableName)) {
                    TableRef candidate = new TableRef(
                            resultSet.getString("TABLE_CAT"),
                            resultSet.getString("TABLE_SCHEM"),
                            candidateName
                    );
                    if (currentCatalog == null || candidate.catalog() == null
                            || currentCatalog.equalsIgnoreCase(candidate.catalog())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static TableRef findTable(
            DatabaseMetaData metadata,
            String catalog,
            String schema,
            String tableName
    ) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            if (!resultSet.next()) {
                return null;
            }
            return new TableRef(
                    resultSet.getString("TABLE_CAT"),
                    resultSet.getString("TABLE_SCHEM"),
                    resultSet.getString("TABLE_NAME")
            );
        }
    }

    private static void validateDependencyNamespace(
            TableRef playerTable,
            List<ForeignKeyReference> references
    ) {
        for (ForeignKeyReference reference : references) {
            if (!sameNamespace(playerTable, reference.table())) {
                throw new IllegalStateException(
                        "Refusing to delete cross-schema player dependency '" + reference.table().name() +
                                "' (constraint " + Objects.toString(reference.foreignKeyName(), "unknown") + ")."
                );
            }
        }
    }

    private static boolean sameNamespace(TableRef left, TableRef right) {
        return equalNullableIgnoreCase(left.catalog(), right.catalog())
                && equalNullableIgnoreCase(left.schema(), right.schema());
    }

    private static boolean equalNullableIgnoreCase(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return right != null && left.equalsIgnoreCase(right);
    }

    private static boolean requiresExplicitDelete(short deleteRule) {
        return deleteRule != DatabaseMetaData.importedKeyCascade
                && deleteRule != DatabaseMetaData.importedKeySetNull;
    }

    private static String quoteTable(DatabaseMetaData metadata, TableRef table) throws SQLException {
        return quoteIdentifier(metadata, table.name());
    }

    private static String quoteIdentifier(DatabaseMetaData metadata, String identifier) throws SQLException {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Database identifier must not be blank.");
        }
        String quote = metadata.getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private static void mergeDeletedRows(Map<String, Integer> deletedRows, String table, int count) {
        if (count > 0) {
            deletedRows.merge(table, count, Integer::sum);
        }
    }

    private record TableRef(String catalog, String schema, String name) {
        private TableRef {
            Objects.requireNonNull(name, "name must not be null");
        }

        private String sortKey() {
            return (Objects.toString(catalog, "") + "." + Objects.toString(schema, "") + "." + name).toLowerCase();
        }
    }

    private record ForeignKeyReference(
            TableRef table,
            String foreignKeyColumn,
            String parentColumn,
            short deleteRule,
            String foreignKeyName
    ) {
    }

    private record LogicalPlayerTable(String tableName, String playerIdColumn) {
    }
}
