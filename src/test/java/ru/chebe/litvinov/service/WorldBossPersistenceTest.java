package ru.chebe.litvinov.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.repository.WorldEventRepository;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Мировой босс переживает рестарт бота (30.09.2026: объявлен — после деплоя «не активен»)
 * и появляется по времени прошлого спавна из базы, только днём по Москве.
 */
class WorldBossPersistenceTest {

    private static class FakeWorldEvents extends WorldEventRepository {
        Optional<BossState> active = Optional.empty();
        long lastSpawn = 0;
        final List<String> spawned = new ArrayList<>();
        final List<Integer> hpUpdates = new ArrayList<>();
        FakeWorldEvents() { super(null); }
        @Override public Optional<BossState> activeBoss(long now) { return active; }
        @Override public long lastBossSpawnAt() { return lastSpawn; }
        @Override public long spawnBoss(String name, String location, int hp, long startedAt, long endsAt) {
            spawned.add(name); lastSpawn = startedAt; return 42;
        }
        @Override public void updateBossHp(long id, String name, String location, int hp) { hpUpdates.add(hp); }
    }

    private static long msk(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(WorldEventManager.GAME_ZONE).toInstant().toEpochMilli();
    }

    private static int bossHp() throws Exception {
        Field f = WorldEventManager.class.getDeclaredField("worldBossHp");
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static WorldEventManager withJda(WorldEventManager m) {
        m.setJda(mock(net.dv8tion.jda.api.JDA.class));
        return m;
    }

    @AfterEach
    void reset() throws Exception {
        Field f = WorldEventManager.class.getDeclaredField("worldBossHp");
        f.setAccessible(true);
        f.setInt(null, 0);
    }

    @Test
    void activeBossIsRestoredAfterRestart() throws Exception {
        FakeWorldEvents repo = new FakeWorldEvents();
        repo.active = Optional.of(new WorldEventRepository.BossState(7, "Великий Дракон", "мейн", 3210, 0, Long.MAX_VALUE));

        new WorldEventManager(mock(PlayerRepository.class), repo);

        assertEquals(3210, bossHp(), "после рестарта босс тот же и с тем же HP");
    }

    @Test
    void spawnsOnlyAfter72hSinceLastSpawn_andOnlyInDaytimeMoscow() throws Exception {
        FakeWorldEvents repo = new FakeWorldEvents();
        repo.lastSpawn = msk("2026-09-27T20:00:00");
        WorldEventManager m = withJda(new WorldEventManager(mock(PlayerRepository.class), repo));

        m.checkWorldBoss(msk("2026-09-30T16:00:00"));
        assertTrue(repo.spawned.isEmpty(), "прошло 68 ч — рано");

        m.checkWorldBoss(msk("2026-10-01T03:00:00"));
        assertTrue(repo.spawned.isEmpty(), "72 ч прошло, но ночь по Москве");

        m.checkWorldBoss(msk("2026-10-01T10:05:00"));
        assertEquals(1, repo.spawned.size());
        assertTrue(bossHp() > 0);
    }

    @Test
    void notSpawnedBeforeDiscordIsConnected() {
        FakeWorldEvents repo = new FakeWorldEvents();
        WorldEventManager m = new WorldEventManager(mock(PlayerRepository.class), repo);

        m.checkWorldBoss(msk("2026-10-01T12:00:00"));

        assertTrue(repo.spawned.isEmpty(), "без JDA анонс не уйдёт — босс появился бы молча");
    }

    @Test
    void unkilledBossLeavesAfter72h() throws Exception {
        FakeWorldEvents repo = new FakeWorldEvents();
        repo.active = Optional.of(new WorldEventRepository.BossState(7, "Тёмный Страж", "мейн", 100, 0, Long.MAX_VALUE));
        repo.lastSpawn = msk("2026-09-27T12:00:00");
        WorldEventManager m = withJda(new WorldEventManager(mock(PlayerRepository.class), repo));

        m.checkWorldBoss(msk("2026-09-30T13:00:00"));

        assertEquals(1, repo.spawned.size(), "старый ушёл, вышел новый");
    }
}
