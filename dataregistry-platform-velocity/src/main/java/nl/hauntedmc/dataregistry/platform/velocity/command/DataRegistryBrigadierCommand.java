package nl.hauntedmc.dataregistry.platform.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerProfile;
import nl.hauntedmc.dataregistry.api.player.PlayerProfileResult;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlaytimePolicyReconciliationResult;
import nl.hauntedmc.dataregistry.core.service.PlayerDeletionResult;
import nl.hauntedmc.dataregistry.core.service.PlayerPresenceRepairResult;
import nl.hauntedmc.theme.HauntedMcColor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** Builds the Velocity-native Brigadier command tree for DataRegistry administration. */
public final class DataRegistryBrigadierCommand {

    public static final String PERMISSION = "dataregistry.admin";
    public static final String PLAYER_DELETE_PERMISSION = "dataregistry.admin.players.delete";
    private static final int MAX_ROWS_TO_DISPLAY = 20;
    private static final TextColor BRAND = HauntedMcColor.BRAND.textColor();
    private static final TextColor ACCENT = HauntedMcColor.ACCENT.textColor();
    private static final TextColor SUCCESS = HauntedMcColor.SUCCESS.textColor();
    private static final TextColor WARNING = HauntedMcColor.WARNING.textColor();
    private static final TextColor ERROR = HauntedMcColor.ERROR.textColor();
    private static final TextColor MUTED = HauntedMcColor.MUTED.textColor();
    private static final TextColor TEXT = HauntedMcColor.TEXT.textColor();

    private DataRegistryBrigadierCommand() {
    }

    public static BrigadierCommand create(Handler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("dataregistry")
                .requires(source -> source.hasPermission(PERMISSION))
                .executes(context -> sendHelp(context.getSource()))
                .then(BrigadierCommand.literalArgumentBuilder("help")
                        .executes(context -> sendHelp(context.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("status")
                        .executes(context -> sendStatus(context.getSource(), handler.status())))
                .then(BrigadierCommand.literalArgumentBuilder("features")
                        .executes(context -> sendFeatures(context.getSource(), handler.status())))
                .then(BrigadierCommand.literalArgumentBuilder("diagnostics")
                        .executes(context -> {
                            runAsync(context.getSource(), "Collecting DataRegistry diagnostics", handler.diagnostics(),
                                    DataRegistryBrigadierCommand::sendDiagnostics);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(playersCommand(handler))
                .then(servicesCommand(handler))
                .then(presenceCommand(handler))
                .then(playtimeCommand(handler)));
    }

    private static LiteralArgumentBuilder<CommandSource> playersCommand(Handler handler) {
        return BrigadierCommand.literalArgumentBuilder("players")
                .executes(context -> {
                    if (!isFeatureEnabled(handler, DataRegistryFeature.ONLINE_STATUS)) {
                        context.getSource().sendMessage(error("Online-status tracking is disabled."));
                        return Command.SINGLE_SUCCESS;
                    }
                    runAsync(context.getSource(), "Loading durable online players", handler.onlinePlayers(),
                            DataRegistryBrigadierCommand::sendOnlinePlayers);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.literalArgumentBuilder("online")
                        .executes(context -> {
                            if (!isFeatureEnabled(handler, DataRegistryFeature.ONLINE_STATUS)) {
                                context.getSource().sendMessage(error("Online-status tracking is disabled."));
                                return Command.SINGLE_SUCCESS;
                            }
                            runAsync(context.getSource(), "Loading durable online players", handler.onlinePlayers(),
                                    DataRegistryBrigadierCommand::sendOnlinePlayers);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierCommand.literalArgumentBuilder("recent")
                        .executes(context -> {
                            if (!isFeatureEnabled(handler, DataRegistryFeature.ACTIVITY_SUMMARY)) {
                                context.getSource().sendMessage(error("Activity-summary tracking is disabled."));
                                return Command.SINGLE_SUCCESS;
                            }
                            runAsync(context.getSource(), "Loading recently active players", handler.recentPlayers(),
                                    DataRegistryBrigadierCommand::sendRecentPlayers);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierCommand.literalArgumentBuilder("inspect")
                        .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                                .executes(context -> {
                                    String identifier = StringArgumentType.getString(context, "player");
                                    runAsync(context.getSource(), "Loading player profile", handler.playerProfile(identifier),
                                            DataRegistryBrigadierCommand::sendPlayerProfile);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(BrigadierCommand.literalArgumentBuilder("delete")
                        .requires(source -> source.hasPermission(PLAYER_DELETE_PERMISSION))
                        .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                                .executes(context -> sendPlayerDeleteConfirmation(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")
                                ))
                                .then(BrigadierCommand.literalArgumentBuilder("confirm")
                                        .executes(context -> {
                                            String identifier = StringArgumentType.getString(context, "player");
                                            runAsync(
                                                    context.getSource(),
                                                    "Deleting offline player identity",
                                                    handler.deletePlayer(identifier),
                                                    DataRegistryBrigadierCommand::sendPlayerDeletion
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }

    private static LiteralArgumentBuilder<CommandSource> servicesCommand(Handler handler) {
        return BrigadierCommand.literalArgumentBuilder("services")
                .executes(context -> {
                    runAsync(context.getSource(), "Collecting service-registry health", handler.services(),
                            DataRegistryBrigadierCommand::sendServices);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.literalArgumentBuilder("health")
                        .executes(context -> {
                            runAsync(context.getSource(), "Collecting service-registry health", handler.services(),
                                    DataRegistryBrigadierCommand::sendServices);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSource> presenceCommand(Handler handler) {
        return BrigadierCommand.literalArgumentBuilder("presence")
                .executes(context -> sendPresenceHelp(context.getSource()))
                .then(BrigadierCommand.literalArgumentBuilder("repair")
                        .executes(context -> {
                            runAsync(context.getSource(), "Repairing stale presence and refreshing live status",
                                    handler.repairPresence(), DataRegistryBrigadierCommand::sendPresenceRepair);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSource> playtimeCommand(Handler handler) {
        return BrigadierCommand.literalArgumentBuilder("playtime")
                .executes(context -> sendPlaytimeStatus(context.getSource(), handler.status()))
                .then(BrigadierCommand.literalArgumentBuilder("status")
                        .executes(context -> sendPlaytimeStatus(context.getSource(), handler.status())))
                .then(BrigadierCommand.literalArgumentBuilder("mappings")
                        .executes(context -> sendMappings(context.getSource(), handler.status())))
                .then(BrigadierCommand.literalArgumentBuilder("flush")
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            if (!handler.status().playtimeEnabled()) {
                                source.sendMessage(error("Playtime tracking is disabled."));
                                return Command.SINGLE_SUCCESS;
                            }
                            try {
                                source.sendMessage(success("Queued playtime flushes for " + handler.flushActivePlaytime()
                                        + " active player(s)."));
                            } catch (RuntimeException failure) {
                                source.sendMessage(error("Unable to queue a playtime flush: " + describeFailure(failure)));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierCommand.literalArgumentBuilder("reconcile")
                        .executes(context -> {
                            runAsync(context.getSource(), "Reloading and reconciling playtime policy",
                                    handler.reconcilePlaytimePolicy(), DataRegistryBrigadierCommand::sendReconciliationResult);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static int sendHelp(CommandSource source) {
        header(source, "DataRegistry administration");
        source.sendMessage(command("/dr status", "runtime and live-proxy overview"));
        source.sendMessage(command("/dr features", "all built-in feature switches and capabilities"));
        source.sendMessage(command("/dr diagnostics", "durable row counts, open state, and lifecycle health"));
        source.sendMessage(command(
                "/dr players <online|recent|inspect <player>|delete <player> confirm>",
                "durable playerbase, activity, and debugging controls"
        ));
        source.sendMessage(command("/dr services [health]", "service registry instances and probe health"));
        source.sendMessage(command("/dr presence repair", "force-refresh durable online status from this proxy"));
        source.sendMessage(command("/dr playtime <status|mappings|flush|reconcile>", "playtime policy controls"));
        source.sendMessage(note("All database reads and repairs run asynchronously; broad presence closure is never performed."));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendStatus(CommandSource source, Status status) {
        header(source, "DataRegistry status");
        source.sendMessage(field("Runtime", status.ready() ? "READY" : "NOT READY", status.ready() ? SUCCESS : ERROR));
        source.sendMessage(field("Proxy players", Integer.toString(status.onlinePlayerCount()), ACCENT));
        source.sendMessage(field("Enabled features", status.enabledFeatureKeys().size() + " / " + DataRegistryFeature.values().length, SUCCESS));
        source.sendMessage(field("Playtime", status.playtimeEnabled()
                ? "enabled · flush every " + status.flushIntervalSeconds() + "s"
                : "disabled", status.playtimeEnabled() ? SUCCESS : MUTED));
        source.sendMessage(note("Use /dr diagnostics for durable database health and /dr presence repair after an unclean stop."));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendFeatures(CommandSource source, Status status) {
        header(source, "Feature matrix");
        for (DataRegistryFeature feature : DataRegistryFeature.values()) {
            boolean enabled = status.enabledFeatureKeys().contains(feature.configKey());
            source.sendMessage(field(feature.configKey(), enabled ? "ENABLED" : "DISABLED", enabled ? SUCCESS : MUTED));
        }
        source.sendMessage(note("Feature switches define the active schema and lifecycle write path; changing them requires a restart."));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendDiagnostics(CommandSource source, Diagnostics diagnostics) {
        header(source, "Diagnostics");
        source.sendMessage(field("Known players", Long.toString(diagnostics.knownPlayers()), ACCENT));
        source.sendMessage(field("Online status", diagnostics.durableOnlinePlayers() + " durable / "
                + diagnostics.proxyOnlinePlayers() + " proxy", diagnostics.presenceConsistent() ? SUCCESS : WARNING));
        source.sendMessage(field("Open lifecycle state", "sessions=" + diagnostics.openSessions()
                + ", visits=" + diagnostics.openVisits() + ", playtime segments=" + diagnostics.openPlaytimeSegments(), ACCENT));
        source.sendMessage(field("Lifecycle ledger", Long.toString(diagnostics.lifecycleEvents()), ACCENT));
        source.sendMessage(field("Lifecycle queues", "active=" + diagnostics.activeLifecyclePipelines()
                + ", awaiting recovery=" + diagnostics.disconnectsAwaitingRecovery(),
                diagnostics.disconnectsAwaitingRecovery() == 0 ? SUCCESS : WARNING));
        if (diagnostics.serviceRegistryEnabled()) {
            source.sendMessage(field("Service registry", diagnostics.runningServiceInstances() + " running / "
                    + diagnostics.serviceCount() + " services", SUCCESS));
        } else {
            source.sendMessage(field("Service registry", "disabled", MUTED));
        }
        if (!diagnostics.presenceConsistent()) {
            source.sendMessage(note("Counts may differ briefly during joins/quits. Refresh this proxy's live rows with /dr presence repair."));
        }
    }

    private static void sendOnlinePlayers(CommandSource source, List<OnlinePlayer> players) {
        header(source, "Durable online players");
        if (players.isEmpty()) {
            source.sendMessage(note("No players are currently marked online in durable storage."));
            return;
        }
        for (OnlinePlayer player : players.subList(0, Math.min(players.size(), MAX_ROWS_TO_DISPLAY))) {
            source.sendMessage(field("#" + player.playerId(), blankAsUnknown(player.currentServer()), SUCCESS));
        }
        sendRemainder(source, players.size());
    }

    private static void sendRecentPlayers(CommandSource source, List<RecentPlayer> players) {
        header(source, "Recently active players");
        if (players.isEmpty()) {
            source.sendMessage(note("No activity-summary rows are available."));
            return;
        }
        for (RecentPlayer player : players.subList(0, Math.min(players.size(), MAX_ROWS_TO_DISPLAY))) {
            source.sendMessage(field("#" + player.playerId(), "last seen " + formatAge(player.lastSeenAt()), ACCENT));
        }
        sendRemainder(source, players.size());
    }

    private static void sendPlayerProfile(CommandSource source, PlayerProfileResult result) {
        if (result.profile().isEmpty()) {
            source.sendMessage(error("No DataRegistry profile was found for " + result.lookup().text() + "."));
            return;
        }
        PlayerProfile profile = result.profile().get();
        header(source, "Player profile · " + profile.identity().username());
        source.sendMessage(field("Identity", "#" + profile.identity().playerId() + " · " + profile.identity().uuid(), ACCENT));
        source.sendMessage(field("Online", profile.isOnline() ? "ONLINE · " + blankAsUnknown(profile.currentServer().orElse(null))
                : "offline", profile.isOnline() ? SUCCESS : MUTED));
        profile.activity().ifPresent(activity -> source.sendMessage(field(
                "Activity", "last seen " + formatAge(activity.lastSeenAt()), ACCENT
        )));
        profile.playtime().ifPresent(playtime -> source.sendMessage(field(
                "Playtime", formatDuration(playtime.trackedTotalMillis()) + " tracked · "
                        + formatDuration(playtime.networkTotalMillis()) + " network", ACCENT
        )));
        profile.language().ifPresent(language -> source.sendMessage(field(
                "Language", language.language() + " · effective " + language.effectiveLanguage(), TEXT
        )));
        profile.nickname().ifPresent(nickname -> source.sendMessage(field("Nickname", nickname, TEXT)));
        profile.connection().ifPresent(connection -> source.sendMessage(field(
                "Connection", "IP=" + blankAsUnknown(connection.ipAddress()) + " · host="
                        + blankAsUnknown(connection.virtualHost()), WARNING
        )));
        source.sendMessage(field("Name history", profile.nameHistory().isEmpty()
                ? "none" : profile.nameHistory().size() + " stored entry/entries", MUTED));
    }

    private static int sendPlayerDeleteConfirmation(CommandSource source, String identifier) {
        header(source, "Delete player identity");
        source.sendMessage(field("Player", identifier, WARNING));
        source.sendMessage(note("This permanently removes the canonical identity and its dependent player rows."));
        source.sendMessage(command(
                "/dr players delete " + identifier + " confirm",
                "confirm permanent deletion; the player must be fully offline"
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendPlayerDeletion(CommandSource source, Optional<PlayerDeletionResult> deletion) {
        if (deletion.isEmpty()) {
            source.sendMessage(error("No DataRegistry player identity was found."));
            return;
        }
        PlayerDeletionResult result = deletion.get();
        header(source, "Player identity deleted");
        source.sendMessage(field(
                "Player",
                result.deletedIdentity().username() + " (#" + result.deletedIdentity().playerId() + ")",
                SUCCESS
        ));
        source.sendMessage(field("UUID", result.deletedIdentity().uuid().toString(), ACCENT));
        source.sendMessage(field(
                "Dependent rows",
                result.deletedDependentRows() + " across " + result.deletedTableCount() + " table(s)",
                ACCENT
        ));
        source.sendMessage(note("The next join will create a new DataRegistry player identity and player ID."));
    }

    private static void sendServices(CommandSource source, List<ServiceHealth> services) {
        header(source, "Service registry health");
        if (services.isEmpty()) {
            source.sendMessage(note("No service health records are available, or service-registry is disabled."));
            return;
        }
        for (ServiceHealth service : services.subList(0, Math.min(services.size(), MAX_ROWS_TO_DISPLAY))) {
            TextColor color = switch (service.status()) {
                case "HEALTHY" -> SUCCESS;
                case "DEGRADED", "UNKNOWN" -> WARNING;
                default -> ERROR;
            };
            source.sendMessage(field(service.kind() + " / " + service.name(), service.status() + " · "
                    + service.runningInstances() + "/" + service.totalInstances() + " running", color));
        }
        sendRemainder(source, services.size());
    }

    private static int sendPresenceHelp(CommandSource source) {
        header(source, "Presence repair");
        source.sendMessage(command("/dr presence repair", "force-refreshes online status for this proxy's live players"));
        source.sendMessage(note("It never marks absent players offline, so it remains safe for shared multi-proxy databases."));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendPresenceRepair(CommandSource source, PlayerPresenceRepairResult result) {
        header(source, "Presence repair complete");
        source.sendMessage(field("Live statuses refreshed", Integer.toString(result.onlineStatusesRefreshed()), SUCCESS));
        if (result.livePlayersMissing() > 0) {
            source.sendMessage(field("Live players without durable identity", Integer.toString(result.livePlayersMissing()), WARNING));
        }
    }

    private static int sendPlaytimeStatus(CommandSource source, Status status) {
        if (!status.playtimeEnabled()) {
            source.sendMessage(error("Playtime tracking is disabled."));
            return Command.SINGLE_SUCCESS;
        }
        header(source, "Playtime policy");
        source.sendMessage(field("Flush interval", status.flushIntervalSeconds() + " seconds", ACCENT));
        source.sendMessage(field("Ignored gamemodes", describeKeys(status.ignoredGamemodes()), WARNING));
        source.sendMessage(field("Excluded from total", describeKeys(status.excludedFromNetworkTotalGamemodes()), WARNING));
        source.sendMessage(field("Server mappings", status.serverGamemodeRules().size() + " ordered rule(s)", ACCENT));
        source.sendMessage(note("Unknown servers " + (status.resolveUnknownServersAsGamemode()
                ? "resolve to a valid normalized gamemode key." : "are not tracked.")));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendMappings(CommandSource source, Status status) {
        if (!status.playtimeEnabled()) {
            source.sendMessage(error("Playtime tracking is disabled."));
            return Command.SINGLE_SUCCESS;
        }
        header(source, "Playtime server mappings");
        if (status.serverGamemodeRules().isEmpty()) {
            source.sendMessage(note("No server-to-gamemode mapping rules are configured."));
            return Command.SINGLE_SUCCESS;
        }
        for (int index = 0; index < Math.min(status.serverGamemodeRules().size(), MAX_ROWS_TO_DISPLAY); index++) {
            MappingRule rule = status.serverGamemodeRules().get(index);
            source.sendMessage(field((index + 1) + ". " + rule.match(), rule.gamemodeKey(), ACCENT));
        }
        sendRemainder(source, status.serverGamemodeRules().size());
        return Command.SINGLE_SUCCESS;
    }

    private static void sendReconciliationResult(CommandSource source, PlaytimePolicyReconciliationResult result) {
        source.sendMessage(success("Playtime policy reconciled; historic playtime was retained."));
    }

    private static <T> void runAsync(
            CommandSource source,
            String action,
            CompletionStage<T> operation,
            BiConsumer<CommandSource, T> successHandler
    ) {
        source.sendMessage(note(action + "…"));
        operation.whenComplete((result, failure) -> {
            if (failure != null) {
                source.sendMessage(error(action + " failed: " + describeFailure(failure)));
                return;
            }
            successHandler.accept(source, result);
        });
    }

    private static void header(CommandSource source, String title) {
        source.sendMessage(Component.text("◆ ", BRAND).append(Component.text(title, BRAND))
                .append(Component.text(" ─────────────────", MUTED)));
    }

    private static Component field(String label, String value, TextColor color) {
        return Component.text("  " + label, TEXT).append(Component.text("  »  ", MUTED)).append(Component.text(value, color));
    }

    private static Component command(String command, String description) {
        return Component.text("  " + command, ACCENT).append(Component.text("  —  " + description, MUTED));
    }

    private static Component note(String message) {
        return Component.text("  " + message, MUTED);
    }

    private static Component success(String message) {
        return Component.text("✓ " + message, SUCCESS);
    }

    private static Component error(String message) {
        return Component.text("✕ " + message, ERROR);
    }

    private static void sendRemainder(CommandSource source, int total) {
        if (total > MAX_ROWS_TO_DISPLAY) {
            source.sendMessage(note("… and " + (total - MAX_ROWS_TO_DISPLAY) + " more."));
        }
    }

    private static boolean isFeatureEnabled(Handler handler, DataRegistryFeature feature) {
        return handler.status().enabledFeatureKeys().contains(feature.configKey());
    }

    private static String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "server unknown" : value;
    }

    private static String formatAge(Instant instant) {
        if (instant == null) {
            return "unknown";
        }
        Duration age = Duration.between(instant, Instant.now()).abs();
        if (age.toDays() > 0) {
            return age.toDays() + "d ago";
        }
        if (age.toHours() > 0) {
            return age.toHours() + "h ago";
        }
        if (age.toMinutes() > 0) {
            return age.toMinutes() + "m ago";
        }
        return "just now";
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        if (duration.toDays() > 0) {
            return duration.toDays() + "d " + duration.toHoursPart() + "h";
        }
        if (duration.toHours() > 0) {
            return duration.toHours() + "h " + duration.toMinutesPart() + "m";
        }
        return duration.toMinutes() + "m";
    }

    private static String describeKeys(Set<String> keys) {
        return keys.isEmpty() ? "none" : keys.stream().sorted().collect(Collectors.joining(", "));
    }

    private static String describeFailure(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public interface Handler {

        Status status();

        int flushActivePlaytime();

        CompletionStage<PlaytimePolicyReconciliationResult> reconcilePlaytimePolicy();

        default CompletionStage<Diagnostics> diagnostics() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Diagnostics are unavailable."));
        }

        default CompletionStage<List<OnlinePlayer>> onlinePlayers() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Online-player view is unavailable."));
        }

        default CompletionStage<List<RecentPlayer>> recentPlayers() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Recent-player view is unavailable."));
        }

        default CompletionStage<PlayerProfileResult> playerProfile(String identifier) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Player profiles are unavailable."));
        }

        default CompletionStage<Optional<PlayerDeletionResult>> deletePlayer(String identifier) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Player deletion is unavailable."));
        }

        default CompletionStage<List<ServiceHealth>> services() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Service health is unavailable."));
        }

        default CompletionStage<PlayerPresenceRepairResult> repairPresence() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Presence repair is unavailable."));
        }
    }

    public record Status(
            boolean ready,
            boolean playtimeEnabled,
            int onlinePlayerCount,
            int flushIntervalSeconds,
            Set<String> ignoredGamemodes,
            Set<String> excludedFromNetworkTotalGamemodes,
            Set<String> enabledFeatureKeys,
            boolean resolveUnknownServersAsGamemode,
            List<MappingRule> serverGamemodeRules
    ) {

        public Status {
            if (onlinePlayerCount < 0 || flushIntervalSeconds < 0) {
                throw new IllegalArgumentException("Status counts must not be negative.");
            }
            ignoredGamemodes = Set.copyOf(Objects.requireNonNull(ignoredGamemodes, "ignoredGamemodes must not be null"));
            excludedFromNetworkTotalGamemodes = Set.copyOf(Objects.requireNonNull(
                    excludedFromNetworkTotalGamemodes, "excludedFromNetworkTotalGamemodes must not be null"
            ));
            enabledFeatureKeys = Set.copyOf(Objects.requireNonNull(enabledFeatureKeys, "enabledFeatureKeys must not be null"));
            serverGamemodeRules = List.copyOf(Objects.requireNonNull(serverGamemodeRules, "serverGamemodeRules must not be null"));
        }
    }

    public record Diagnostics(
            long knownPlayers,
            long durableOnlinePlayers,
            int proxyOnlinePlayers,
            long openSessions,
            long openVisits,
            long openPlaytimeSegments,
            long lifecycleEvents,
            int activeLifecyclePipelines,
            int disconnectsAwaitingRecovery,
            boolean serviceRegistryEnabled,
            long serviceCount,
            long runningServiceInstances
    ) {
        public Diagnostics {
            if (knownPlayers < 0 || durableOnlinePlayers < 0 || proxyOnlinePlayers < 0 || openSessions < 0
                    || openVisits < 0 || openPlaytimeSegments < 0 || lifecycleEvents < 0
                    || activeLifecyclePipelines < 0 || disconnectsAwaitingRecovery < 0 || serviceCount < 0
                    || runningServiceInstances < 0) {
                throw new IllegalArgumentException("Diagnostic counts must not be negative.");
            }
        }

        public boolean presenceConsistent() {
            return durableOnlinePlayers == proxyOnlinePlayers;
        }
    }

    public record OnlinePlayer(long playerId, String currentServer) {
    }

    public record RecentPlayer(long playerId, Instant lastSeenAt) {
    }

    public record ServiceHealth(String kind, String name, String status, long totalInstances, long runningInstances) {
    }

    public record MappingRule(String match, String gamemodeKey) {
        public MappingRule {
            if (match == null || match.isBlank() || gamemodeKey == null || gamemodeKey.isBlank()) {
                throw new IllegalArgumentException("Mapping rules require non-blank match and gamemode key values.");
            }
        }
    }
}
