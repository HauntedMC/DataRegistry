package nl.hauntedmc.dataregistry.platform.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlaytimePolicyReconciliationResult;
import nl.hauntedmc.dataregistry.core.service.PlayerDeletionResult;
import nl.hauntedmc.dataregistry.core.service.PlayerPresenceRepairResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
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
    void playerListsShowUsernamesAndExplicitlyReportTruncation() throws CommandSyntaxException {
        List<DataRegistryBrigadierCommand.OnlinePlayer> players = new ArrayList<>();
        for (int index = 1; index <= 21; index++) {
            players.add(new DataRegistryBrigadierCommand.OnlinePlayer(index, "Player" + index, "lobby"));
        }
        CommandSource source = source(true);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(onlineHandler(players));

        assertEquals(1, dispatcher.execute("dataregistry players online", source));

        String output = capturedOutput(source);
        assertTrue(output.contains("Player1 (#1)"));
        assertTrue(output.contains("Player20 (#20)"));
        assertFalse(output.contains("Player21 (#21)"));
        assertTrue(output.contains("additional rows exist; showing the first 20"));
    }

    @Test
    void disabledPresenceDiagnosticsDoNotRecommendImpossibleRepair() throws CommandSyntaxException {
        CommandSource source = source(true);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(disabledDiagnosticsHandler());

        assertEquals(1, dispatcher.execute("dataregistry diagnostics", source));

        String output = capturedOutput(source);
        assertTrue(output.contains("Online status  »  disabled"));
        assertTrue(output.contains("sessions=disabled, visits=disabled, playtime segments=disabled"));
        assertFalse(output.contains("/dr presence repair"));
    }

    @Test
    void disabledFeatureCommandsFailFastWithoutCallingUnavailableHandlers() throws CommandSyntaxException {
        CommandSource source = source(true);
        DataRegistryBrigadierCommand.Handler disabled = disabledDiagnosticsHandler();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(disabled);

        assertEquals(1, dispatcher.execute("dataregistry services health", source));
        assertEquals(1, dispatcher.execute("dataregistry presence repair", source));
        assertEquals(1, dispatcher.execute("dataregistry playtime reconcile", source));

        String output = capturedOutput(source);
        assertTrue(output.contains("Service-registry tracking is disabled."));
        assertTrue(output.contains("Online-status tracking is disabled."));
        assertTrue(output.contains("Playtime tracking is disabled."));
    }

    @Test
    void playerDeletionRequiresDedicatedPermission() {
        CommandSource source = source(true, false);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(handler());

        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("dataregistry players delete Alice confirm", source)
        );
        verify(source, never()).sendMessage(org.mockito.ArgumentMatchers.any());
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
        verify(source, times(10)).sendMessage(org.mockito.ArgumentMatchers.any());
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
                        Set.of(
                                DataRegistryFeature.PLAYTIME.configKey(),
                                DataRegistryFeature.SESSIONS.configKey(),
                                DataRegistryFeature.SESSION_VISITS.configKey(),
                                DataRegistryFeature.ONLINE_STATUS.configKey(),
                                DataRegistryFeature.ACTIVITY_SUMMARY.configKey(),
                                DataRegistryFeature.SERVICE_REGISTRY.configKey()
                        ),
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
                        new DataRegistryBrigadierCommand.OnlinePlayer(1L, "Alice", "lobby")
                ));
            }

            @Override
            public CompletableFuture<List<DataRegistryBrigadierCommand.RecentPlayer>> recentPlayers() {
                return CompletableFuture.completedFuture(List.of(
                        new DataRegistryBrigadierCommand.RecentPlayer(1L, "Alice", Instant.now())
                ));
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

    private static DataRegistryBrigadierCommand.Handler onlineHandler(
            List<DataRegistryBrigadierCommand.OnlinePlayer> onlinePlayers
    ) {
        return new DataRegistryBrigadierCommand.Handler() {
            @Override
            public DataRegistryBrigadierCommand.Status status() {
                return DataRegistryBrigadierCommandTest.status(
                        Set.of(DataRegistryFeature.ONLINE_STATUS.configKey()),
                        false
                );
            }

            @Override
            public int flushActivePlaytime() {
                return 0;
            }

            @Override
            public CompletableFuture<PlaytimePolicyReconciliationResult> reconcilePlaytimePolicy() {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public CompletableFuture<List<DataRegistryBrigadierCommand.OnlinePlayer>> onlinePlayers() {
                return CompletableFuture.completedFuture(onlinePlayers);
            }
        };
    }

    private static DataRegistryBrigadierCommand.Handler disabledDiagnosticsHandler() {
        return new DataRegistryBrigadierCommand.Handler() {
            @Override
            public DataRegistryBrigadierCommand.Status status() {
                return DataRegistryBrigadierCommandTest.status(Set.of(), false);
            }

            @Override
            public int flushActivePlaytime() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<PlaytimePolicyReconciliationResult> reconcilePlaytimePolicy() {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            public CompletableFuture<DataRegistryBrigadierCommand.Diagnostics> diagnostics() {
                return CompletableFuture.completedFuture(new DataRegistryBrigadierCommand.Diagnostics(
                        120, 0, 4, 0, 0, 0, 900, 0, 0, false, 0, 0
                ));
            }
        };
    }

    private static DataRegistryBrigadierCommand.Status status(Set<String> enabledFeatures, boolean playtimeEnabled) {
        return new DataRegistryBrigadierCommand.Status(
                true,
                playtimeEnabled,
                4,
                playtimeEnabled ? 30 : 0,
                Set.of(),
                Set.of(),
                enabledFeatures,
                false,
                List.of()
        );
    }

    private static String capturedOutput(CommandSource source) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(source, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream()
                .map(DataRegistryBrigadierCommandTest::plainText)
                .collect(Collectors.joining("\n"));
    }

    private static String plainText(Component component) {
        StringBuilder output = new StringBuilder();
        if (component instanceof TextComponent text) {
            output.append(text.content());
        }
        for (Component child : component.children()) {
            output.append(plainText(child));
        }
        return output.toString();
    }
}
