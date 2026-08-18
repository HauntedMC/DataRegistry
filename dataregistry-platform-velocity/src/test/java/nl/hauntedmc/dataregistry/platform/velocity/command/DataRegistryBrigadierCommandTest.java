package nl.hauntedmc.dataregistry.platform.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlaytimePolicyReconciliationResult;
import nl.hauntedmc.dataregistry.core.service.PlayerDeletionResult;
import nl.hauntedmc.dataregistry.core.service.PlayerPresenceRepairResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRegistryBrigadierCommandTest {

    @Test
    void commandTreeRequiresAdministrativePermission() {
        CommandSource source = source(false);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("dataregistry status", source));

        verify(source, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void statusCommandReportsRuntimeAndPlaytimePolicy() throws CommandSyntaxException {
        CommandSource source = source(true);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        int result = dispatcher.execute("dataregistry status", source);

        assertEquals(1, result);
        verify(source, times(6)).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void playtimeCommandsExposeLiteralSuggestionsAndPerformSafeOperations() throws Exception {
        CommandSource source = source(true);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        int result = dispatcher.execute("dataregistry playtime reconcile", source);
        List<String> suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("dataregistry playtime ", source)
        ).get().getList().stream().map(suggestion -> suggestion.getText()).toList();

        assertEquals(1, result);
        assertTrue(suggestions.containsAll(List.of("flush", "mappings", "reconcile", "status")));
        verify(source, times(2)).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void featuresMappingsAndFlushCommandsProvideOperationalInformation() throws CommandSyntaxException {
        CommandSource source = source(true);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        assertEquals(1, dispatcher.execute("dataregistry features", source));
        assertEquals(1, dispatcher.execute("dataregistry playtime mappings", source));
        assertEquals(1, dispatcher.execute("dataregistry playtime flush", source));

        verify(source, times(DataRegistryFeature.values().length + 5)).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void diagnosticsPlayerServiceAndPresenceCommandsExposeOperationalViews() throws Exception {
        CommandSource source = source(true);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        assertEquals(1, dispatcher.execute("dataregistry diagnostics", source));
        assertEquals(1, dispatcher.execute("dataregistry players online", source));
        assertEquals(1, dispatcher.execute("dataregistry players recent", source));
        assertEquals(1, dispatcher.execute("dataregistry players inspect Alice", source));
        assertEquals(1, dispatcher.execute("dataregistry services health", source));
        assertEquals(1, dispatcher.execute("dataregistry presence repair", source));

        List<String> suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("dataregistry ", source)
        ).get().getList().stream().map(suggestion -> suggestion.getText()).toList();
        assertTrue(suggestions.containsAll(List.of(
                "diagnostics", "players", "services", "presence", "features", "playtime", "status"
        )));
        verify(source, times(22)).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void playerDeletionIsHiddenWithoutDedicatedPermission() throws Exception {
        CommandSource source = source(true, false);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        List<String> suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("dataregistry players ", source)
        ).get().getList().stream().map(suggestion -> suggestion.getText()).toList();

        assertFalse(suggestions.contains("delete"));
        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("dataregistry players delete Alice confirm", source)
        );
    }

    @Test
    void playerDeletionRequiresExplicitConfirmation() throws CommandSyntaxException {
        CommandSource source = source(true, true);
        AtomicInteger deleteCalls = new AtomicInteger();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler(deleteCalls));

        assertEquals(1, dispatcher.execute("dataregistry players delete Alice", source));
        assertEquals(0, deleteCalls.get());

        assertEquals(1, dispatcher.execute("dataregistry players delete Alice confirm", source));
        assertEquals(1, deleteCalls.get());
        verify(source, times(9)).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    private static CommandDispatcher<CommandSource> dispatcher(DataRegistryBrigadierCommand.Handler handler) {
        BrigadierCommand command = DataRegistryBrigadierCommand.create(handler);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.getNode());
        return dispatcher;
    }

    private static CommandSource source(boolean permitted) {
        return source(permitted, false);
    }

    private static CommandSource source(boolean permitted, boolean playerDeletePermitted) {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission(DataRegistryBrigadierCommand.PERMISSION)).thenReturn(permitted);
        when(source.hasPermission(DataRegistryBrigadierCommand.PLAYER_DELETE_PERMISSION)).thenReturn(playerDeletePermitted);
        return source;
    }

    private static DataRegistryBrigadierCommand.Handler handler() {
        return handler(null);
    }

    private static DataRegistryBrigadierCommand.Handler handler(AtomicInteger deleteCalls) {
        return new DataRegistryBrigadierCommand.Handler() {
            @Override
            public DataRegistryBrigadierCommand.Status status() {
                return new DataRegistryBrigadierCommand.Status(
                        true,
                        true,
                        4,
                        30,
                        Set.of("limbo"),
                        Set.of("lobby"),
                        Set.of("playtime", "sessions", "online-status", "activity-summary"),
                        true,
                        List.of(new DataRegistryBrigadierCommand.MappingRule("lobby-*", "lobby"))
                );
            }

            @Override
            public int flushActivePlaytime() {
                return 4;
            }

            @Override
            public CompletableFuture<PlaytimePolicyReconciliationResult> reconcilePlaytimePolicy() {
                return CompletableFuture.completedFuture(new PlaytimePolicyReconciliationResult(
                        Set.of("limbo"),
                        Set.of("lobby")
                ));
            }

            @Override
            public CompletableFuture<DataRegistryBrigadierCommand.Diagnostics> diagnostics() {
                return CompletableFuture.completedFuture(new DataRegistryBrigadierCommand.Diagnostics(
                        120, 4, 4, 4, 4, 4, 900, 0, 0, true, 3, 4
                ));
            }

            @Override
            public CompletableFuture<List<DataRegistryBrigadierCommand.OnlinePlayer>> onlinePlayers() {
                return CompletableFuture.completedFuture(List.of(
                        new DataRegistryBrigadierCommand.OnlinePlayer(1L, "lobby")
                ));
            }

            @Override
            public CompletableFuture<List<DataRegistryBrigadierCommand.RecentPlayer>> recentPlayers() {
                return CompletableFuture.completedFuture(List.of(
                        new DataRegistryBrigadierCommand.RecentPlayer(1L, Instant.now())
                ));
            }

            @Override
            public CompletableFuture<PlayerDeletionResult> unsupportedDeletion() {
                return null;
            }

            @Override
            public CompletableFuture<Optional<PlayerDeletionResult>> deletePlayer(String identifier) {
                if (deleteCalls != null) {
                    deleteCalls.incrementAndGet();
                }
                return CompletableFuture.completedFuture(Optional.of(new PlayerDeletionResult(
                        new PlayerIdentity(
                                42L,
                                UUID.fromString("a9663301-5ad7-4b4c-bcc6-38a18e8567cc"),
                                identifier
                        ),
                        Map.of("player_sessions", 2)
                )));
            }

            @Override
            public CompletableFuture<List<DataRegistryBrigadierCommand.ServiceHealth>> services() {
                return CompletableFuture.completedFuture(List.of(
                        new DataRegistryBrigadierCommand.ServiceHealth("BACKEND", "lobby", "HEALTHY", 1, 1)
                ));
            }

            @Override
            public CompletableFuture<PlayerPresenceRepairResult> repairPresence() {
                return CompletableFuture.completedFuture(new PlayerPresenceRepairResult(
                        4,
                        0
                ));
            }
        };
    }
}
