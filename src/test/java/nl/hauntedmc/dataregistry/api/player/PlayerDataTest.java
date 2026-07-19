package nl.hauntedmc.dataregistry.api.player;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerActivitySummaryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerConnectionInfoRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerLanguageRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNameHistoryRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerNicknameRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerOnlineStatusRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerPlaytimeRepository;
import nl.hauntedmc.dataregistry.backend.player.DataRegistryQueryExecutor;
import nl.hauntedmc.dataregistry.backend.player.RepositoryPlayerData;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerDataTest {

    @Test
    void findLanguageResolvesPlayerIdThroughIdentityFacade() {
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerLanguageRepository languageRepository = mock(PlayerLanguageRepository.class);
        PlayerLanguageEntity entity = new PlayerLanguageEntity();
        entity.setPlayerId(42L);
        entity.setLanguage("AUTO");
        entity.setEffectiveLanguage("EN");

        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(directory.findByUuid(uuid))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(42L, uuid, "Alice"))));
        when(languageRepository.findByPlayerId(42L)).thenReturn(Optional.of(entity));

        PlayerData playerData = playerData(directory, null, null, null, languageRepository, null, null, null);

        Optional<PlayerLanguageSettings> settings = join(playerData.findLanguage(uuid));

        assertTrue(settings.isPresent());
        assertEquals("AUTO", settings.get().language());
        assertEquals("EN", settings.get().effectiveLanguage());
    }

    @Test
    void saveNicknameByUuidDoesNotWriteWhenIdentityIsMissing() {
        UUID uuid = UUID.randomUUID();
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerNicknameRepository nicknameRepository = mock(PlayerNicknameRepository.class);
        when(directory.findActiveIdentityCached(uuid)).thenReturn(Optional.empty());
        when(directory.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        PlayerData playerData = playerData(directory, null, null, null, null, nicknameRepository, null, null);

        assertFalse(join(playerData.saveNickname(uuid, "Ghost")));
        verifyNoInteractions(nicknameRepository);
    }

    @Test
    void saveLanguageFailsClearlyWhenFeatureIsDisabled() {
        PlayerData playerData = playerData(mock(PlayerDirectory.class), null, null, null, null, null, null, null);

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> join(playerData.saveLanguage(1L, "EN", "EN"))
        );
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    void connectionAndNameHistoryReturnImmutableSnapshots() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-19T10:15:30Z");
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerConnectionInfoRepository connectionRepository = mock(PlayerConnectionInfoRepository.class);
        PlayerNameHistoryRepository nameHistoryRepository = mock(PlayerNameHistoryRepository.class);
        PlayerConnectionInfoEntity connection = new PlayerConnectionInfoEntity();
        PlayerEntity player = new PlayerEntity();
        PlayerNameHistoryEntity history = new PlayerNameHistoryEntity();

        player.setId(7L);
        player.setUuid(uuid.toString());
        player.setUsername("Alice");
        connection.setPlayerId(7L);
        connection.setIpAddress("1.2.3.4");
        connection.setFirstConnectionAt(now.minusSeconds(60L));
        connection.setLastConnectionAt(now);
        connection.setVirtualHost("play.example.net");
        history.setId(9L);
        history.setPlayer(player);
        history.setUsername("Alice");
        history.setLastSeenAt(now);

        when(connectionRepository.findByPlayerId(7L)).thenReturn(Optional.of(connection));
        when(nameHistoryRepository.findChronologicalByPlayer(7L, 10)).thenReturn(List.of(history));

        PlayerData playerData = playerData(
                directory,
                null,
                null,
                connectionRepository,
                null,
                null,
                nameHistoryRepository,
                null
        );

        Optional<PlayerConnectionSnapshot> connectionSnapshot = join(playerData.findConnection(7L));
        List<PlayerNameHistoryEntry> nameHistory = join(playerData.findNameHistory(7L, 10));

        assertEquals("1.2.3.4", connectionSnapshot.orElseThrow().ipAddress());
        assertEquals("play.example.net", connectionSnapshot.orElseThrow().virtualHost());
        assertEquals(List.of(new PlayerNameHistoryEntry(9L, 7L, "Alice", now)), nameHistory);
    }

    @Test
    void sharedIpIdentityLookupDelegatesToRepository() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerConnectionInfoRepository connectionRepository = mock(PlayerConnectionInfoRepository.class);
        PlayerIdentity identity = new PlayerIdentity(5L, UUID.randomUUID(), "Alice");
        when(connectionRepository.findIdentitiesByLastIpAddress("1.2.3.4", 5L)).thenReturn(List.of(identity));

        PlayerData playerData = playerData(directory, null, null, connectionRepository, null, null, null, null);

        assertEquals(List.of(identity), join(playerData.findIdentitiesByLastIpAddress("1.2.3.4", 5L)));
        verify(connectionRepository).findIdentitiesByLastIpAddress("1.2.3.4", 5L);
    }

    @Test
    void findNameHistoryByCurrentUsernameUsesIdentityDirectory() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-19T10:15:30Z");
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerNameHistoryRepository nameHistoryRepository = mock(PlayerNameHistoryRepository.class);
        PlayerEntity player = new PlayerEntity();
        PlayerNameHistoryEntity history = new PlayerNameHistoryEntity();

        player.setId(7L);
        player.setUuid(uuid.toString());
        player.setUsername("Alice");
        history.setId(9L);
        history.setPlayer(player);
        history.setUsername("Alpha");
        history.setLastSeenAt(now);

        when(directory.findByUsernameIgnoreCase("Alice"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(7L, uuid, "Alice"))));
        when(nameHistoryRepository.findChronologicalByPlayer(7L, 3)).thenReturn(List.of(history));

        PlayerData playerData = playerData(directory, null, null, null, null, null, nameHistoryRepository, null);

        assertEquals(
                List.of(new PlayerNameHistoryEntry(9L, 7L, "Alpha", now)),
                join(playerData.findNameHistoryByCurrentUsername("Alice", 3))
        );
    }

    @Test
    void identifierPrefixSharedIpAndProfileHelpersUseFacadeRepositories() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-19T10:15:30Z");
        PlayerDirectory directory = mock(PlayerDirectory.class);
        PlayerLanguageRepository languageRepository = mock(PlayerLanguageRepository.class);
        PlayerNicknameRepository nicknameRepository = mock(PlayerNicknameRepository.class);
        PlayerConnectionInfoRepository connectionRepository = mock(PlayerConnectionInfoRepository.class);
        PlayerOnlineStatusRepository onlineRepository = mock(PlayerOnlineStatusRepository.class);
        PlayerNameHistoryRepository nameHistoryRepository = mock(PlayerNameHistoryRepository.class);
        PlayerIdentity identity = new PlayerIdentity(7L, uuid, "Alice");
        PlayerLanguageEntity language = new PlayerLanguageEntity();
        PlayerConnectionInfoEntity connection = new PlayerConnectionInfoEntity();
        PlayerOnlineStatusEntity online = new PlayerOnlineStatusEntity();
        PlayerEntity player = new PlayerEntity();
        PlayerNameHistoryEntity history = new PlayerNameHistoryEntity();

        language.setPlayerId(7L);
        language.setLanguage("AUTO");
        language.setEffectiveLanguage("EN");
        connection.setPlayerId(7L);
        connection.setIpAddress("1.2.3.4");
        connection.setLastConnectionAt(now);
        online.setPlayerId(7L);
        online.setOnline(true);
        online.setCurrentServer("hub");
        player.setId(7L);
        player.setUuid(uuid.toString());
        player.setUsername("Alice");
        history.setId(9L);
        history.setPlayer(player);
        history.setUsername("Alpha");
        history.setLastSeenAt(now);

        when(directory.findByIdentifier("Alice")).thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));
        when(directory.findIdentity(PlayerLookup.playerId(7L)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));
        when(directory.findByUsernamePrefix("Al", 5)).thenReturn(CompletableFuture.completedFuture(List.of(identity)));
        when(directory.findByPlayerId(7L)).thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));
        when(languageRepository.findByPlayerId(7L)).thenReturn(Optional.of(language));
        when(nicknameRepository.findNicknameByPlayerId(7L)).thenReturn(Optional.of("Ghost"));
        when(connectionRepository.findByPlayerId(7L)).thenReturn(Optional.of(connection));
        when(connectionRepository.findUsernamesByLastIpAddress("1.2.3.4", 7L)).thenReturn(List.of("Bob"));
        when(onlineRepository.findByPlayerId(7L)).thenReturn(Optional.of(online));
        when(nameHistoryRepository.findChronologicalByPlayer(7L, 2)).thenReturn(List.of(history));

        PlayerData playerData = playerData(
                directory,
                null,
                onlineRepository,
                connectionRepository,
                languageRepository,
                nicknameRepository,
                nameHistoryRepository,
                null
        );

        assertEquals(Optional.of(identity), join(playerData.findIdentityByIdentifier("Alice")));
        assertEquals(Optional.of(7L), join(playerData.findPlayerIdByIdentifier("Alice")));
        assertEquals(List.of(identity), join(playerData.findIdentitiesByUsernamePrefix("Al", 5)));
        assertEquals(List.of("Bob"), join(playerData.findUsernamesSharingLastIp(7L)));

        PlayerProfile profile = join(playerData.findProfile(7L, 2)).orElseThrow();
        assertEquals(identity, profile.identity());
        assertEquals("Ghost", profile.nickname().orElseThrow());
        assertTrue(profile.isOnline());
        assertEquals(Optional.of("hub"), profile.currentServer());
        assertEquals(List.of(new PlayerNameHistoryEntry(9L, 7L, "Alpha", now)), profile.nameHistory());
    }

    @Test
    void findProfileForSuppliedIdentityUsesPersistedIdentitySnapshot() {
        PlayerDirectory directory = mock(PlayerDirectory.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        UUID currentUuid = UUID.randomUUID();
        PlayerIdentity staleIdentity = new PlayerIdentity(7L, UUID.randomUUID(), "OldName");
        PlayerEntity player = new PlayerEntity();
        player.setId(7L);
        player.setUuid(currentUuid.toString());
        player.setUsername("CurrentName");
        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerEntity.class, 7L)).thenReturn(player);

        PlayerData playerData = new RepositoryPlayerData(
                directory,
                DataRegistryQueryExecutor.immediateForTesting(),
                ormContext,
                EnumSet.allOf(DataRegistryFeature.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        PlayerProfile profile = join(playerData.findProfile(staleIdentity, 0)).orElseThrow();

        assertEquals(new PlayerIdentity(7L, currentUuid, "CurrentName"), profile.identity());
    }

    private static PlayerData playerData(
            PlayerDirectory playerDirectory,
            PlayerActivitySummaryRepository activityRepository,
            PlayerOnlineStatusRepository onlineRepository,
            PlayerConnectionInfoRepository connectionRepository,
            PlayerLanguageRepository languageRepository,
            PlayerNicknameRepository nicknameRepository,
            PlayerNameHistoryRepository nameHistoryRepository,
            PlayerPlaytimeRepository playtimeRepository
    ) {
        return new RepositoryPlayerData(
                playerDirectory,
                EnumSet.allOf(DataRegistryFeature.class),
                activityRepository,
                onlineRepository,
                connectionRepository,
                languageRepository,
                nicknameRepository,
                nameHistoryRepository,
                playtimeRepository
        );
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
