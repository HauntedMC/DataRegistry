package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.observation.DataRegistryObservations;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Permanently removes one offline canonical player identity for administrative/debugging use.
 *
 * <p>The service discovers the foreign-key graph rooted at {@code player_entity} at runtime. It explicitly removes
 * every reachable dependent row before deleting the canonical identity, regardless of whether a dependency is declared
 * as restrict, cascade, or set-null. This keeps feature-owned tables outside DataRegistry reset-safe without maintaining
 * a hardcoded list of foreign-key dependencies.</p>
 */
public final class PlayerDeletionService {

    private static final String PLAYER_TABLE = "player_entity";
    private static final String PLAYER_ID_COLUMN = "id";
    private static final String PLAYER_UUID_COLUMN = "uuid";
    private static final String ONLINE_STATUS_TABLE = "player_online_status";
    private static final int MAX_DEPENDENCY_DEPTH = 32;
    private static final int MAX_DEPENDENCY_PATHS = 2048;
    private static final List<LogicalPlayerTable> LOGICAL_PLAYER_TABLES = List.of(
            new LogicalPlayerTable("player_lifecycle_outbox", "player_id"),
            new LogicalPlayerTable("population_transition", "player_id")
    );

    private final DataRegistry dataRegistry;
    private final PlayerService playerService;
    private final ILoggerAdapter logger;
    private final DataRegistryObservations observations;

    public PlayerDeletionService(DataRegistry dataRegistry, PlayerService playerService, ILoggerAdapter logger) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        DataRegistryObservations runtimeObservations = dataRegistry.internalObservations();
        this.observations = runtimeObservations == null ? new DataRegistryObservations() : runtimeObservations;
    }

    /**
     * Deletes an offline player and all reachable rows that depend on its canonical identity.
     *
     * @param identity identity resolved immediately before this administrative operation.
     * @return deletion details for staff feedback and diagnostics.
     * @throws IllegalStateException when the player is active, cannot be proven offline, is stale, or cannot be safely
     *                               deleted.
     */
    public PlayerDeletionResult delete(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return observations.observe("player.delete", () -> deleteInternal(identity));
    }

    private PlayerDeletionResult deleteInternal(PlayerIdentity identity) {
        if (!dataRegistry.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)) {
            throw new IllegalStateException(
                    "Online-status tracking must be enabled to safely prove a player is offline before deletion."
            );
        }
        String uuid = identity.uuid().toString();
        requireInactive(uuid);
        validateForExternalDeletion(identity);

        List<DataRegistry.PlayerDeletionExternalDataSource> externalSources =
                dataRegistry.playerDeletionExternalDataSources();
        preflightExternalPlayerData(externalSources);
        Map<String, Integer> externalDeletedRows = deleteExternalPlayerData(identity.playerId(), externalSources);
        PlayerDeletionResult result;
        try {
            result = dataRegistry.getORM().runInTransaction(session ->
                    session.doReturningWork(connection -> deleteInTransaction(connection, identity))
            );
        } catch (RuntimeException exception) {
            if (!externalDeletedRows.isEmpty()) {
                logger.error(
                        "Configured external player data was removed, but canonical DataRegistry player deletion "
                                + "failed for player #" + identity.playerId() + ".",
                        exception
                );
            }
            throw exception;
        }
        if (!externalDeletedRows.isEmpty()) {
            LinkedHashMap<String, Integer> allDeletedRows = new LinkedHashMap<>(result.deletedRowsByTable());
            externalDeletedRows.forEach((table, count) -> allDeletedRows.merge(table, count, Integer::sum));
            result = new PlayerDeletionResult(identity, allDeletedRows);
        }

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

    /** Proves the canonical identity is safe to erase before independent external transactions can begin. */
    private void validateForExternalDeletion(PlayerIdentity identity) {
        dataRegistry.getORM().runInTransaction(session -> session.doReturningWork(connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            TableRef playerTable = resolveTable(metadata, connection, PLAYER_TABLE);
            if (playerTable == null) {
                throw new IllegalStateException("Canonical player table '" + PLAYER_TABLE + "' was not found.");
            }
            lockAndValidatePlayer(connection, metadata, playerTable, identity);
            requireInactive(identity.uuid().toString());
            requireDurablyOffline(connection, metadata, playerTable, identity.playerId());
            return Boolean.TRUE;
        }));
    }

    /**
     * Deletes data on every explicitly configured external DataProvider connection.
     *
     * <p>Schema/connection validation for every source completes before this method runs. Each source still has its
     * own JDBC transaction, because DataProvider connections can point at independent databases. A failure rolls back
     * the source currently being processed and prevents canonical deletion. A later canonical transaction failure is
     * logged prominently because cross-database atomicity requires XA support, which DataProvider does not expose.</p>
     */
    private void preflightExternalPlayerData(List<DataRegistry.PlayerDeletionExternalDataSource> externalSources) {
        for (DataRegistry.PlayerDeletionExternalDataSource source : externalSources) {
            try (Connection connection = source.dataSource().getConnection()) {
                DatabaseMetaData metadata = connection.getMetaData();
                for (TableRef table : findPlayerIdTables(metadata, connection, source.playerIdColumns())) {
                    discoverDependencyPaths(metadata, table, table);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Failed to validate configured external player data on DataProvider connection '"
                                + source.connectionId() + "'.",
                        exception
                );
            }
        }
    }

    private Map<String, Integer> deleteExternalPlayerData(
            long playerId,
            List<DataRegistry.PlayerDeletionExternalDataSource> externalSources
    ) {
        LinkedHashMap<String, Integer> deletedRows = new LinkedHashMap<>();
        for (DataRegistry.PlayerDeletionExternalDataSource source : externalSources) {
            LinkedHashMap<String, Integer> sourceDeletedRows = new LinkedHashMap<>();
            try (Connection connection = source.dataSource().getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    DatabaseMetaData metadata = connection.getMetaData();
                    for (TableRef table : findPlayerIdTables(metadata, connection, source.playerIdColumns())) {
                        for (String playerIdColumn : resolvePlayerIdColumns(metadata, table, source.playerIdColumns())) {
                            deleteDependents(connection, metadata, table, table, playerIdColumn, playerId, sourceDeletedRows,
                                    source.connectionId());
                            int deleted = deleteByColumn(connection, metadata, table, playerIdColumn, playerId);
                            mergeDeletedRows(sourceDeletedRows, source.connectionId() + ":" + table.name(), deleted);
                        }
                    }
                    connection.commit();
                    sourceDeletedRows.forEach((table, count) -> deletedRows.merge(table, count, Integer::sum));
                } catch (SQLException | RuntimeException exception) {
                    rollbackExternalConnection(connection, source.connectionId(), exception);
                    throw exception;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            } catch (SQLException exception) {
                logExternalPartialFailure(playerId, deletedRows, exception);
                throw new IllegalStateException(
                        "Failed to remove configured external player data from DataProvider connection '"
                                + source.connectionId() + "'.",
                        exception
                );
            } catch (RuntimeException exception) {
                logExternalPartialFailure(playerId, deletedRows, exception);
                throw exception;
            }
        }
        return deletedRows;
    }

    private void logExternalPartialFailure(long playerId, Map<String, Integer> completedDeletedRows, Exception failure) {
        if (completedDeletedRows.isEmpty()) {
            return;
        }
        logger.error(
                "Configured external player data was removed from one or more connections, but a later external "
                        + "cleanup failed for player #" + playerId + "; canonical DataRegistry deletion was not run.",
                failure
        );
    }

    private void rollbackExternalConnection(Connection connection, String connectionId, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            logger.error("Failed to roll back external player-data deletion for connection '" + connectionId + "'.",
                    rollbackFailure);
        }
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

        LinkedHashMap<String, Integer> deletedRows = new LinkedHashMap<>();
        deleteDependents(
                connection,
                metadata,
                playerTable,
                playerTable,
                PLAYER_ID_COLUMN,
                identity.playerId(),
                deletedRows
        );

        for (LogicalPlayerTable logicalTable : LOGICAL_PLAYER_TABLES) {
            TableRef table = resolveTable(metadata, connection, logicalTable.tableName());
            if (table == null || !sameNamespace(playerTable, table)) {
                continue;
            }
            deleteDependents(
                    connection,
                    metadata,
                    playerTable,
                    table,
                    logicalTable.playerIdColumn(),
                    identity.playerId(),
                    deletedRows
            );
            int deleted = deleteByColumn(
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
            throw new IllegalStateException(
                    "Durable online-status state is unavailable; refusing to delete a player whose offline state " +
                            "cannot be proven."
            );
        }
        String sql = "SELECT " + quoteIdentifier(metadata, "online") +
                " FROM " + quoteTable(metadata, onlineStatusTable) +
                " WHERE " + quoteIdentifier(metadata, "player_id") + " = ? FOR UPDATE";
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

    private static void deleteDependents(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef namespaceRoot,
            TableRef anchorTable,
            String anchorColumn,
            long anchorValue,
            Map<String, Integer> deletedRows
    ) throws SQLException {
        deleteDependents(connection, metadata, namespaceRoot, anchorTable, anchorColumn, anchorValue, deletedRows, null);
    }

    private static void deleteDependents(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef namespaceRoot,
            TableRef anchorTable,
            String anchorColumn,
            long anchorValue,
            Map<String, Integer> deletedRows,
            String tableNamePrefix
    ) throws SQLException {
        List<DependencyPath> paths = discoverDependencyPaths(metadata, namespaceRoot, anchorTable);
        for (DependencyPath path : paths) {
            int deleted = deleteByDependencyPath(
                    connection,
                    metadata,
                    path,
                    anchorColumn,
                    anchorValue
            );
            mergeDeletedRows(deletedRows, tableNamePrefix == null
                    ? path.target().name()
                    : tableNamePrefix + ":" + path.target().name(), deleted);
        }
    }

    private static List<TableRef> findPlayerIdTables(
            DatabaseMetaData metadata,
            Connection connection,
            Set<String> playerIdColumns
    ) throws SQLException {
        String currentCatalog = connection.getCatalog();
        String currentSchema = connection.getSchema();
        Map<String, TableRef> tables = new LinkedHashMap<>();
        collectTables(metadata, currentCatalog, currentSchema, tables);
        if (tables.isEmpty() && currentSchema != null) {
            collectTables(metadata, currentCatalog, null, tables);
        }
        List<TableRef> matches = new ArrayList<>();
        for (TableRef table : tables.values()) {
            if (!resolvePlayerIdColumns(metadata, table, playerIdColumns).isEmpty()) {
                matches.add(table);
            }
        }
        matches.sort(Comparator.comparing(TableRef::sortKey));
        return matches;
    }

    private static void collectTables(
            DatabaseMetaData metadata,
            String catalog,
            String schema,
            Map<String, TableRef> tables
    ) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                TableRef table = new TableRef(
                        resultSet.getString("TABLE_CAT"),
                        resultSet.getString("TABLE_SCHEM"),
                        tableName
                );
                tables.putIfAbsent(table.sortKey(), table);
            }
        }
    }

    private static List<String> resolvePlayerIdColumns(
            DatabaseMetaData metadata,
            TableRef table,
            Set<String> playerIdColumns
    )
            throws SQLException {
        List<String> matchingColumns = new ArrayList<>();
        try (ResultSet resultSet = metadata.getColumns(table.catalog(), table.schema(), table.name(), "%")) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                if (columnName != null && playerIdColumns.contains(columnName.toLowerCase(java.util.Locale.ROOT))) {
                    matchingColumns.add(columnName);
                }
            }
        }
        matchingColumns.sort(String.CASE_INSENSITIVE_ORDER);
        return matchingColumns;
    }

    private static List<DependencyPath> discoverDependencyPaths(
            DatabaseMetaData metadata,
            TableRef namespaceRoot,
            TableRef anchorTable
    ) throws SQLException {
        List<DependencyPath> paths = new ArrayList<>();
        Set<TableRef> ancestors = new HashSet<>();
        ancestors.add(anchorTable);
        appendDependencyPaths(metadata, namespaceRoot, anchorTable, List.of(), ancestors, paths);
        paths.sort(Comparator
                .comparingInt(DependencyPath::depth)
                .reversed()
                .thenComparing(DependencyPath::sortKey));
        return paths;
    }

    private static void appendDependencyPaths(
            DatabaseMetaData metadata,
            TableRef namespaceRoot,
            TableRef parent,
            List<ForeignKeyReference> currentPath,
            Set<TableRef> ancestors,
            List<DependencyPath> paths
    ) throws SQLException {
        if (currentPath.size() >= MAX_DEPENDENCY_DEPTH) {
            if (!exportedKeys(metadata, parent).isEmpty()) {
                throw new IllegalStateException(
                        "Player deletion dependency graph exceeds the supported depth of " + MAX_DEPENDENCY_DEPTH + "."
                );
            }
            return;
        }

        for (ForeignKeyReference reference : exportedKeys(metadata, parent)) {
            validateDependencyNamespace(namespaceRoot, reference);
            if (ancestors.contains(reference.table())) {
                throw new IllegalStateException(
                        "Cyclic foreign-key dependency detected while planning player deletion around table '" +
                                reference.table().name() + "' (constraint " +
                                Objects.toString(reference.foreignKeyName(), "unknown") + ")."
                );
            }

            List<ForeignKeyReference> nextPath = new ArrayList<>(currentPath);
            nextPath.add(reference);
            paths.add(new DependencyPath(List.copyOf(nextPath)));
            if (paths.size() > MAX_DEPENDENCY_PATHS) {
                throw new IllegalStateException(
                        "Player deletion dependency graph exceeds the supported path count of " + MAX_DEPENDENCY_PATHS +
                                "."
                );
            }

            Set<TableRef> nextAncestors = new HashSet<>(ancestors);
            nextAncestors.add(reference.table());
            appendDependencyPaths(
                    metadata,
                    namespaceRoot,
                    reference.table(),
                    nextPath,
                    nextAncestors,
                    paths
            );
        }
    }

    private static int deleteByDependencyPath(
            Connection connection,
            DatabaseMetaData metadata,
            DependencyPath path,
            String anchorColumn,
            long anchorValue
    ) throws SQLException {
        TableRef target = path.target();
        String targetQualifier = quoteIdentifier(metadata, target.name());
        String sql = "DELETE FROM " + quoteTable(metadata, target) +
                " WHERE " + buildParentExists(
                        metadata,
                        path.references(),
                        path.references().size() - 1,
                        targetQualifier,
                        anchorColumn
                );
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, anchorValue);
            return statement.executeUpdate();
        }
    }

    private static String buildParentExists(
            DatabaseMetaData metadata,
            List<ForeignKeyReference> path,
            int edgeIndex,
            String childQualifier,
            String anchorColumn
    ) throws SQLException {
        ForeignKeyReference reference = path.get(edgeIndex);
        String parentAlias = "dr_parent_" + edgeIndex;
        StringBuilder sql = new StringBuilder("EXISTS (SELECT 1 FROM ")
                .append(quoteTable(metadata, reference.parentTable()))
                .append(' ')
                .append(parentAlias)
                .append(" WHERE ");
        appendJoinPredicate(sql, metadata, reference, childQualifier, parentAlias);
        if (edgeIndex == 0) {
            sql.append(" AND ")
                    .append(parentAlias)
                    .append('.')
                    .append(quoteIdentifier(metadata, anchorColumn))
                    .append(" = ?");
        } else {
            sql.append(" AND ")
                    .append(buildParentExists(metadata, path, edgeIndex - 1, parentAlias, anchorColumn));
        }
        return sql.append(')').toString();
    }

    private static void appendJoinPredicate(
            StringBuilder sql,
            DatabaseMetaData metadata,
            ForeignKeyReference reference,
            String childQualifier,
            String parentQualifier
    ) throws SQLException {
        for (int index = 0; index < reference.columns().size(); index++) {
            if (index > 0) {
                sql.append(" AND ");
            }
            ColumnMapping column = reference.columns().get(index);
            sql.append(childQualifier)
                    .append('.')
                    .append(quoteIdentifier(metadata, column.foreignKeyColumn()))
                    .append(" = ")
                    .append(parentQualifier)
                    .append('.')
                    .append(quoteIdentifier(metadata, column.parentColumn()));
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

    private static int deleteByColumn(
            Connection connection,
            DatabaseMetaData metadata,
            TableRef table,
            String column,
            long value
    ) throws SQLException {
        String sql = "DELETE FROM " + quoteTable(metadata, table) +
                " WHERE " + quoteIdentifier(metadata, column) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            return statement.executeUpdate();
        }
    }

    private static List<ForeignKeyReference> exportedKeys(DatabaseMetaData metadata, TableRef parent) throws SQLException {
        Map<ForeignKeyKey, List<ColumnMapping>> namedReferences = new LinkedHashMap<>();
        List<ForeignKeyReference> unnamedReferences = new ArrayList<>();
        try (ResultSet resultSet = metadata.getExportedKeys(parent.catalog(), parent.schema(), parent.name())) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("FKTABLE_NAME");
                String foreignKeyColumn = resultSet.getString("FKCOLUMN_NAME");
                String parentColumn = resultSet.getString("PKCOLUMN_NAME");
                if (tableName == null || tableName.isBlank()
                        || foreignKeyColumn == null || foreignKeyColumn.isBlank()
                        || parentColumn == null || parentColumn.isBlank()) {
                    continue;
                }

                TableRef childTable = new TableRef(
                        resultSet.getString("FKTABLE_CAT"),
                        resultSet.getString("FKTABLE_SCHEM"),
                        tableName
                );
                short keySequence = resultSet.getShort("KEY_SEQ");
                ColumnMapping mapping = new ColumnMapping(foreignKeyColumn, parentColumn, keySequence);
                String foreignKeyName = normalizeName(resultSet.getString("FK_NAME"));
                if (foreignKeyName == null) {
                    if (keySequence != 1) {
                        throw new IllegalStateException(
                                "Unnamed composite foreign key on table '" + childTable.name() +
                                        "' cannot be safely planned for player deletion."
                        );
                    }
                    unnamedReferences.add(new ForeignKeyReference(
                            parent,
                            childTable,
                            List.of(mapping),
                            null
                    ));
                    continue;
                }
                namedReferences.computeIfAbsent(
                        new ForeignKeyKey(childTable, foreignKeyName),
                        ignored -> new ArrayList<>()
                ).add(mapping);
            }
        }

        List<ForeignKeyReference> references = new ArrayList<>();
        for (Map.Entry<ForeignKeyKey, List<ColumnMapping>> entry : namedReferences.entrySet()) {
            List<ColumnMapping> columns = new ArrayList<>(entry.getValue());
            columns.sort(Comparator.comparingInt(ColumnMapping::keySequence));
            references.add(new ForeignKeyReference(
                    parent,
                    entry.getKey().table(),
                    List.copyOf(columns),
                    entry.getKey().foreignKeyName()
            ));
        }
        references.addAll(unnamedReferences);
        references.sort(Comparator
                .comparing((ForeignKeyReference reference) -> reference.table().sortKey())
                .thenComparing(reference -> Objects.toString(reference.foreignKeyName(), "")));
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

    private static void validateDependencyNamespace(TableRef namespaceRoot, ForeignKeyReference reference) {
        if (!sameNamespace(namespaceRoot, reference.table())) {
            throw new IllegalStateException(
                    "Refusing to delete cross-schema player dependency '" + reference.table().name() +
                            "' (constraint " + Objects.toString(reference.foreignKeyName(), "unknown") + ")."
            );
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

    private static String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private record ColumnMapping(String foreignKeyColumn, String parentColumn, short keySequence) {
        private ColumnMapping {
            Objects.requireNonNull(foreignKeyColumn, "foreignKeyColumn must not be null");
            Objects.requireNonNull(parentColumn, "parentColumn must not be null");
        }
    }

    private record ForeignKeyKey(TableRef table, String foreignKeyName) {
        private ForeignKeyKey {
            Objects.requireNonNull(table, "table must not be null");
            Objects.requireNonNull(foreignKeyName, "foreignKeyName must not be null");
        }
    }

    private record ForeignKeyReference(
            TableRef parentTable,
            TableRef table,
            List<ColumnMapping> columns,
            String foreignKeyName
    ) {
        private ForeignKeyReference {
            Objects.requireNonNull(parentTable, "parentTable must not be null");
            Objects.requireNonNull(table, "table must not be null");
            columns = List.copyOf(Objects.requireNonNull(columns, "columns must not be null"));
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Foreign key columns must not be empty.");
            }
        }
    }

    private record DependencyPath(List<ForeignKeyReference> references) {
        private DependencyPath {
            references = List.copyOf(Objects.requireNonNull(references, "references must not be null"));
            if (references.isEmpty()) {
                throw new IllegalArgumentException("Dependency path must not be empty.");
            }
        }

        private int depth() {
            return references.size();
        }

        private TableRef target() {
            return references.getLast().table();
        }

        private String sortKey() {
            StringBuilder key = new StringBuilder();
            for (ForeignKeyReference reference : references) {
                key.append(reference.table().sortKey())
                        .append('#')
                        .append(Objects.toString(reference.foreignKeyName(), ""))
                        .append('/');
            }
            return key.toString();
        }
    }

    private record LogicalPlayerTable(String tableName, String playerIdColumn) {
    }
}
