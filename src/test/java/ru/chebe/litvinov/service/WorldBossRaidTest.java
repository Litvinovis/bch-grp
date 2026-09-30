package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Совместный бой с мировым боссом: идёт сам до конца, все бьют вместе, босс — один удар в раунд. */
class WorldBossRaidTest {

    private final Map<String, Player> players = new HashMap<>();
    private final List<String> channelMessages = new ArrayList<>();
    private PlayerRepository repo;
    private MessageChannelUnion channel;
    private WorldEventManager manager;

    @BeforeEach
    void setUp() throws Exception {
        repo = mock(PlayerRepository.class);
        when(repo.get(anyString())).thenAnswer(inv -> players.get(inv.getArgument(0)));
        doAnswer(inv -> { players.put(inv.getArgument(0), inv.getArgument(1)); return null; }).when(repo).put(anyString(), any());
        channel = mock(MessageChannelUnion.class);
        when(channel.getId()).thenReturn("c1");
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(action.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(channel.sendMessage(anyString())).thenAnswer(inv -> { channelMessages.add(inv.getArgument(0)); return action; });
        manager = new WorldEventManager(repo);
        manager.raidRandom = new Random(1);
        setStatic("worldBossLocation", "мейн");
        setStatic("worldBossHp", 300);
        setStatic("currentWorldBossData", null);   // статика — не наследуем босса из других тестов
    }

    @AfterEach
    void tearDown() throws Exception {
        setStatic("worldBossHp", 0);
        setStatic("currentWorldBossData", null);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field f = WorldEventManager.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static int bossHp() throws Exception {
        Field f = WorldEventManager.class.getDeclaredField("worldBossHp");
        f.setAccessible(true);
        return f.getInt(null);
    }

    private Player fighter(String id, int str, int hp) {
        Player p = new Player(id.toUpperCase(), id);
        p.setLocation("мейн");
        p.setStrength(str);
        p.setHp(hp);
        p.setMaxHp(hp);
        players.put(id, p);
        return p;
    }

    private void join(String id) {
        MessageReceivedEvent e = mock(MessageReceivedEvent.class);
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(e.getAuthor()).thenReturn(u);
        when(e.getChannel()).thenReturn(channel);
        when(e.getMessage()).thenReturn(mock(Message.class));
        manager.worldBossAttack(e);
    }

    @Test
    void everyoneHitsEachRound_bossHitsOnlyOneRandomFighter() throws Exception {
        Player a = fighter("a", 25, 500);   // 20 урона за раунд
        Player b = fighter("b", 15, 500);   // 10 урона за раунд
        join("a");
        join("b");

        manager.playRound();

        assertEquals(270, bossHp(), "оба бойца ударили в одном раунде");
        int hurt = (a.getHp() < 500 ? 1 : 0) + (b.getHp() < 500 ? 1 : 0);
        assertEquals(1, hurt, "босс бьёт одного участника за раунд");
    }

    @Test
    void secondCommandDoesNotAddExtraHits() throws Exception {
        fighter("a", 25, 500);
        join("a");
        join("a");

        manager.playRound();

        assertEquals(280, bossHp());
        assertTrue(channelMessages.stream().anyMatch(m -> m.contains("уже в бою")));
    }

    @Test
    void fightRunsToTheEnd_rewardsSplitByDamage_topGetsLoot() throws Exception {
        Player a = fighter("a", 105, 5000);  // 100 за раунд
        Player b = fighter("b", 55, 5000);   // 50 за раунд
        int aLootBefore = a.getInventory().getOrDefault("щит чегоба", 0);
        int bLootBefore = b.getInventory().getOrDefault("щит чегоба", 0);
        join("a");
        join("b");

        for (int i = 0; i < 5 && bossHp() > 0; i++) manager.playRound();

        assertEquals(0, bossHp());
        assertTrue(a.getExp() > b.getExp(), "больше урона — больше опыта");
        // первый в ростере — Тёмный Страж, трофей «щит чегоба»
        assertEquals(aLootBefore + 1, a.getInventory().getOrDefault("щит чегоба", 0), "предмет — лучшему по урону");
        assertEquals(bLootBefore, b.getInventory().getOrDefault("щит чегоба", 0));
        assertTrue(channelMessages.stream().anyMatch(m -> m.contains("повержен")));
        assertFalse(manager.raidInProgress());
    }

    @Test
    void knockedOutFighterLeaves_andFightEndsWhenNobodyLeft() throws Exception {
        setStatic("worldBossHp", 100000);
        Player a = fighter("a", 10, 1);
        join("a");

        manager.playRound();

        assertEquals(0, a.getHp());
        assertFalse(manager.raidInProgress(), "бойцов не осталось — бой окончен");
        assertTrue(channelMessages.stream().anyMatch(m -> m.contains("выбыл")));
        assertTrue(bossHp() < 100000, "урон сохранился за боссом");
    }
}
