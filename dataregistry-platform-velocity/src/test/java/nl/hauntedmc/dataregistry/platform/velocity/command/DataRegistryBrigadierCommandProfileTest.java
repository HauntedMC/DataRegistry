package nl.hauntedmc.dataregistry.platform.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerActivitySnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerConnectionSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.player.PlayerNameHistoryEntry;
import nl.hauntedmc.dataregistry.api.player.PlayerOnlineSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerProfile;
import nl.hauntedmc.dataregistry.api.player.PlayerProfileQuery;
import nl.hauntedmc.dataregistry.api.player.PlayerProfileResult;
import nl.hauntedmc.dataregistry.api.playtime.PlayerGamemodePlaytimeSnapshot;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlaytimePolicyReconciliationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRegistryBrigadierCommandProfileTest {

    private static final UUID PLAYER_UUID = UUID.fromString("a9663301-5ad7-4b4c-bcc6-38a18e8567cc");

    @Test
    void inspectionShowsStoredLifecyclePreferencesAndGamemodeDetails() throws CommandSyntaxException {
        CommandSource source = source();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(profileHandler(
                Set.of(
                        DataRegistryFeature.ONLINE_STATUS.configKey(),
                        DataRegistryFeature.ACTIVITY_SUMMARY.configKey(),
                        DataRegistryFeature.PLAYTIME.configKey(),
                        DataRegistryFeature.LANGUAGE.configKey(),
                        DataRegistryFeature.NICKNAMES.configKey(),
                        DataRegistryFeature.CONNECTION_INFO.configKey(),
                        DataRegistryFeature.NAME_HISTORY.configKey()
                ),
                fullProfile()
        ));

        assertEquals(1, dispatcher.execute("dataregistry players inspect Alice", source));

        String output = capturedOutput(source);
        assertTrue(output.contains("Player profile · Alice"));
        assertTrue(output.contains("#1 · " + PLAYER_UUID));
        assertTrue(output.contains("ONLINE · lobby"));
        assertTrue(output.contains("first seen"));
        assertTrue(output.contains("Login / logout"));
        assertTrue(output.contains("10s tracked · 8s network"));
        assertTrue(output.contains("survival  »  8s · active on survival-1"));
        assertTrue(output.contains("AUTO · effective NL"));
        assertTrue(output.contains("Nickname  »  Ali"));
        assertTrue(output.contains("IP=127.0.0.1 · host=play.example.test"));
        assertTrue(output.contains("OldAlice ("));
    }

    @Test
    void inspectionDistinguishesDisabledDomainsFromOfflineOrMissingData() throws CommandSyntaxException {
        CommandSource source = source();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(profileHandler(Set.of(), emptyProfile()));

        assertEquals(1, dispatcher.execute("dataregistry players inspect Alice", source));

        String output = capturedOutput(source);
        assertTrue(output.contains("Online  »  disabled"));
        assertTrue(output.contains("Activity  »  disabled"));
        assertTrue(output.contains("Playtime  »  disabled"));
        assertTrue(output.contains("Language  »  disabled"));
        assertTrue(output.contains("Nickname  »  disabled"));
        assertTrue(output.contains("Connection  »  disabled"));
        assertTrue(output.contains("Name history  »  disabled"));
        assertFalse(output.contains("Online  »  offline"));
    }

    @Test
    void inspectionDistinguishesEnabledDomainWithoutRowFromDisabledDomain() throws CommandSyntaxException {
        CommandSource source = source();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(profileHandler(
                Set.of(
                        DataRegistryFeature.ONLINE_STATUS.configKey(),
                        DataRegistryFeature.ACTIVITY_SUMMARY.configKey(),
                        DataRegistryFeature.PLAYTIME.configKey(),
                        DataRegistryFeature.LANGUAGE.configKey(),
                        DataRegistryFeature.NICKNAMES.configKey(),
                        DataRegistryFeature.CONNECTION_INFO.configKey(),
                        DataRegistryFeature.NAME_HISTORY.configKey()
                ),
                emptyProfile()
        ));

        assertEquals(1, dispatcher.execute("dataregistry players inspect Alice", source));

        String output = capturedOutput(source);
        assertTrue(output.contains("Online  »  no stored status"));
        assertTrue(output.contains("Activity  »  no stored summary"));
        assertTrue(output.contains("Playtime  »  no stored playtime"));
        assertTrue(output.contains("Language  »  no stored preference"));
        assertTrue(output.contains("Nickname  »  none"));
        assertTrue(output.contains("Connection  »  no stored metadata"));
        assertTrue(output.contains("Name history  »  none"));
    }

    private static CommandDispatcher<CommandSource> dispatcher(DataRegistryBrigadierCommand.Handler handler) {
        BrigadierCommand command = DataRegistryBrigadierCommand.create(handler);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.getNode());
        return dispatcher;
    }

    private static CommandSource source() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission(DataRegistryBrigadierCommand.PERMISSION)).thenReturn(true);
        return source;
    }

    private static DataRegistryBrigadierCommand.Handler profileHandler(Set<String> features, PlayerProfile profile) {
        return new DataRegistryBrigadierCommand.Handler() {
            @Override
            public DataRegistryBrigadierCommand.Status status() {
                return new DataRegistryBrigadierCommand.Status(
                        true,
                        features.contains(DataRegistryFeature.PLAYTIME.configKey()),
                        1,
                        features.contains(DataRegistryFeature.PLAYTIME.configKey()) ? 30 : 0,
                        Set.of(),
                        Set.of(),
                        features,
                        false,
                        List.of()
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
            public CompletableFuture<PlayerProfileResult> playerProfile(String identifier) {
                return CompletableFuture.completedFuture(new PlayerProfileResult(
                        PlayerLookup.identifier(identifier),
                        PlayerProfileQuery.withNameHistoryLimit(5),
                        Optional.of(profile)
                ));
            }
        };
    }

    private static PlayerProfile fullProfile() {
        Instant now = Instant.now();
        return new PlayerProfile(
                new PlayerIdentity(1L, PLAYER_UUID, "Alice"),
                Optional.of(new PlayerLanguageSettings(1L, "AUTO", "NL")),
                Optional.of("Ali"),
                Optional.of(new PlayerConnectionSnapshot(
                        1L,
                        "127.0.0.1",
                        now.minus(30, ChronoUnit.DAYS),
                        now.minus(1, ChronoUnit.MINUTES),
                        now.minus(30, ChronoUnit.SECONDS),
                        "play.example.test"
                )),
                Optional.of(new PlayerOnlineSnapshot(1L, true, "lobby", "survival-1")),
                Optional.of(new PlayerActivitySnapshot(
                        1L,
                        now.minus(60, ChronoUnit.DAYS),
                        now.minus(10, ChronoUnit.SECONDS),
                        now.minus(5, ChronoUnit.MINUTES),
                        now.minus(1, ChronoUnit.DAYS)
                )),
                Optional.of(new PlayerPlaytimeSnapshot(
                        1L,
                        PLAYER_UUID.toString(),
                        "Alice",
                        10_000L,
                        8_000L,
                        now,
                        List.of(new PlayerGamemodePlaytimeSnapshot(
                                "survival",
                                8_000L,
                                true,
                                true,
                                now.minus(10, ChronoUnit.SECONDS),
                                "survival-1",
                                now.minus(30, ChronoUnit.DAYS),
                                now,
                                4L
                        ))
                )),
                List.of(new PlayerNameHistoryEntry(
                        1L,
                        1L,
                        "OldAlice",
                        now.minus(100, ChronoUnit.DAYS)
                ))
        );
    }

    private static PlayerProfile emptyProfile() {
        return new PlayerProfile(
                new PlayerIdentity(1L, PLAYER_UUID, "Alice"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
    }

    private static String capturedOutput(CommandSource source) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(source, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream()
                .map(DataRegistryBrigadierCommandProfileTest::plainText)
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
