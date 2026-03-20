package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.apache.ignite.IgniteCache;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Boss;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for BattleManager business logic: damage randomisation, player-vs-player,
 * mob battle outcomes and boss battle state management.
 */
public class BattleManagerLogicTest {

    @Mock
    private IgniteCache<String, Boss> bossCache;

    @Mock
    private MessageChannelUnion channel;

    @Mock
    private MessageCreateAction messageAction;

    private BattleManager battleManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        // queue() returns void — no stubbing needed; Mockito ignores void calls by default
        battleManager = new BattleManager(bossCache);
    }

    // ---- randomizeDamage -------------------------------------------------------

    @Test
    public void randomizeDamage_withZeroBase_returnsZero() {
        assertEquals(0, battleManager.randomizeDamage(0));
    }

    @Test
    public void randomizeDamage_withNegativeBase_returnsZero() {
        assertEquals(0, battleManager.randomizeDamage(-5));
    }

    @Test
    public void randomizeDamage_withPositiveBase_returnsAtLeastOne() {
        // Run multiple times to account for randomness; result must always be >= 1
        for (int i = 0; i < 100; i++) {
            int dmg = battleManager.randomizeDamage(10);
            assertTrue("Damage must be >= 1 for base=10, got " + dmg, dmg >= 1);
        }
    }

    @Test
    public void randomizeDamage_withPositiveBase_staysWithin25PercentBound() {
        // With base=100, max possible is 125, min is 75 (but clamped to 1)
        for (int i = 0; i < 200; i++) {
            int dmg = battleManager.randomizeDamage(100);
            assertTrue("Damage too low: " + dmg, dmg >= 75);
            assertTrue("Damage too high: " + dmg, dmg <= 125);
        }
    }

    @Test
    public void randomizeDamage_withBase1_alwaysReturnsOne() {
        // base=1 with -25% is 0.75 → floor=0 → clamped to 1
        for (int i = 0; i < 50; i++) {
            assertEquals(1, battleManager.randomizeDamage(1));
        }
    }

    // ---- playerBattle ----------------------------------------------------------

    @Test
    public void playerBattle_emptyTeam1_returnEmptyAndSendsMessage() {
        List<Person> result = battleManager.playerBattle(
                new ArrayList<>(),
                buildTeam(1, 100, 5, 0),
                channel);
        assertTrue(result.isEmpty());
        verify(channel).sendMessage("Невозможно начать бой!");
    }

    @Test
    public void playerBattle_emptyTeam2_returnEmptyAndSendsMessage() {
        List<Person> result = battleManager.playerBattle(
                buildTeam(1, 100, 5, 0),
                new ArrayList<>(),
                channel);
        assertTrue(result.isEmpty());
        verify(channel).sendMessage("Невозможно начать бой!");
    }

    @Test
    public void playerBattle_withBothTeams_returnsCombinedList() {
        // Give team1 overwhelming strength so it wins quickly
        List<Person> team1 = buildTeam(1, 1000, 100, 50);
        List<Person> team2 = buildTeam(1, 1, 1, 0);

        List<Person> result = battleManager.playerBattle(team1, team2, channel);

        // Result must contain both teams merged (that is the implementation contract)
        assertEquals(2, result.size());
    }

    @Test
    public void playerBattle_doesNotModifyOriginalListSizes_beforeMerge() {
        List<Person> team1 = buildTeam(2, 1000, 100, 50);
        List<Person> team2 = buildTeam(1, 1, 1, 0);

        // The method internally does addAll; the returned list is team1 after mutation
        List<Person> result = battleManager.playerBattle(team1, team2, channel);

        // combined must contain all persons from both teams
        assertEquals(3, result.size());
    }

    // ---- getBossItemName -------------------------------------------------------

    @Test
    public void getBossItemName_forExistingBoss_returnsItemName() {
        Boss boss = Boss.builder().nickName("Darhalas").hp(1000).strength(10)
                .defeat(0).win(0).bossItem("корона дарха").build();
        when(bossCache.get("Darhalas")).thenReturn(boss);

        String item = battleManager.getBossItemName("Darhalas");

        assertEquals("корона дарха", item);
    }

    @Test
    public void getBossItemName_forUnknownBoss_returnsNull() {
        when(bossCache.get("Unknown")).thenReturn(null);

        String item = battleManager.getBossItemName("Unknown");

        assertNull(item);
    }

    // ---- bossBattle ------------------------------------------------------------

    @Test
    public void bossBattle_withNullBoss_sendsNotFoundMessage() {
        when(bossCache.get("NonExistent")).thenReturn(null);

        battleManager.bossBattle(buildTeam(1, 100, 5, 0), "NonExistent", channel);

        verify(channel).sendMessage("Босс не найден.");
    }

    @Test
    public void bossBattle_whenPlayersWin_incrementsBossDefeat() {
        Boss boss = Boss.builder().nickName("WeakBoss").hp(1).strength(1)
                .defeat(0).win(0).bossItem("dagger").build();
        when(bossCache.get("WeakBoss")).thenReturn(boss);

        // Players have massive HP/strength — guaranteed win
        battleManager.bossBattle(buildTeam(1, 10000, 1000, 100), "WeakBoss", channel);

        // Boss was defeated → defeat counter should have been incremented
        verify(bossCache).put(eq("WeakBoss"), argThat(b -> b.getDefeat() == 1 && b.getWin() == 0));
    }

    @Test
    public void bossBattle_whenBossWins_incrementsBossWin() {
        Boss boss = Boss.builder().nickName("StrongBoss").hp(10000).strength(1000)
                .defeat(0).win(0).bossItem("sword").build();
        when(bossCache.get("StrongBoss")).thenReturn(boss);

        // Player has 1 HP — will die immediately
        battleManager.bossBattle(buildTeam(1, 1, 1, 0), "StrongBoss", channel);

        // Boss won → win counter should have been incremented
        verify(bossCache).put(eq("StrongBoss"), argThat(b -> b.getWin() == 1 && b.getDefeat() == 0));
    }

    @Test
    public void bossBattle_bossHpIsResetAfterBattle() {
        int initialHp = 500;
        Boss boss = Boss.builder().nickName("ResetBoss").hp(initialHp).strength(10)
                .defeat(0).win(0).bossItem("item").build();
        when(bossCache.get("ResetBoss")).thenReturn(boss);

        battleManager.bossBattle(buildTeam(1, 10000, 1000, 100), "ResetBoss", channel);

        // Boss HP must be restored to initialHp after the fight
        verify(bossCache).put(eq("ResetBoss"), argThat(b -> b.getHp() == initialHp));
    }

    // ---- init (cache population) -----------------------------------------------

    @Test
    public void init_putsBossOnlyIfAbsentInCache() {
        // bossCache.get() returns null → should put
        when(bossCache.get(anyString())).thenReturn(null);

        // Calling constructor again to trigger init explicitly
        BattleManager bm = new BattleManager(bossCache);

        // At least one boss should have been put
        verify(bossCache, atLeastOnce()).put(anyString(), any(Boss.class));
    }

    @Test
    public void init_doesNotOverwriteExistingBossInCache() {
        Boss existing = Boss.builder().nickName("Darhalas").hp(500).strength(10)
                .defeat(5).win(3).bossItem("корона дарха").build();

        // Return 'existing' for Darhalas; Mockito returns null by default for unstubbed calls
        when(bossCache.get(eq("Darhalas"))).thenReturn(existing);
        // Reset invocation history from @Before so previous put() calls don't interfere
        clearInvocations(bossCache);

        BattleManager bm = new BattleManager(bossCache);

        // The existing Darhalas boss must NOT be overwritten
        verify(bossCache, never()).put(eq("Darhalas"), any(Boss.class));
        // Other bosses (null in cache) should be put
        verify(bossCache, atLeastOnce()).put(argThat(k -> !"Darhalas".equals(k)), any(Boss.class));
    }

    // ---- helpers ---------------------------------------------------------------

    private List<Person> buildTeam(int size, int hp, int strength, int armor) {
        List<Person> team = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Player p = new Player("Player" + i, "id" + i);
            p.setHp(hp);
            p.setStrength(strength);
            p.setArmor(armor);
            team.add(p);
        }
        return team;
    }
}
