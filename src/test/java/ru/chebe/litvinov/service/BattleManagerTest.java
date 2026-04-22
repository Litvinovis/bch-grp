package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.BossRepository;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for BattleManager.mobBattle():
 * - strong player always wins and returns remaining HP
 * - player with 1 HP always loses and returns -1
 * - win/loss channel messages are sent correctly
 * - player HP is modified by the battle
 * - level < 3 player vs level >= 3 player face appropriately scaled mobs
 */
public class BattleManagerTest {

    @Mock private BossRepository bossRepository;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;

    private BattleManager battleManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        battleManager = new BattleManager(bossRepository);
    }

    // ---- mobBattle: win path ---------------------------------------------------

    @Test
    public void mobBattle_strongPlayerWins_returnsPositiveHp() {
        Player player = strongPlayer(1);
        int result = battleManager.mobBattle(player, channel);
        assertTrue("Expected positive HP on win, got: " + result, result > 0);
    }

    @Test
    public void mobBattle_strongPlayerWins_hpMatchesPlayerHpAfterBattle() {
        Player player = strongPlayer(1);
        int result = battleManager.mobBattle(player, channel);
        assertEquals(player.getHp(), result);
    }

    @Test
    public void mobBattle_strongPlayerWins_hpDecreased() {
        Player player = strongPlayer(1);
        int hpBefore = player.getHp();
        battleManager.mobBattle(player, channel);
        assertTrue("Player HP should decrease after mob fight", player.getHp() <= hpBefore);
    }

    @Test
    public void mobBattle_strongPlayerWins_sendsVictoryMessage() {
        Player player = strongPlayer(1);
        battleManager.mobBattle(player, channel);
        verify(messageAction, atLeastOnce()).submit();
    }

    // ---- mobBattle: loss path --------------------------------------------------

    @Test
    public void mobBattle_playerWith1Hp_alwaysReturnsMinusOne() {
        for (int i = 0; i < 20; i++) {
            Player player = playerWithHp(1, 1, 1);
            int result = battleManager.mobBattle(player, channel);
            assertEquals("Player with 1 HP must always lose, attempt " + i, -1, result);
        }
    }

    @Test
    public void mobBattle_playerLoses_sendsDeathMessage() {
        Player player = playerWithHp(1, 1, 1);
        battleManager.mobBattle(player, channel);
        verify(channel, atLeastOnce()).sendMessage(contains("кринж"));
    }

    // ---- mobBattle: level scaling ----------------------------------------------

    @Test
    public void mobBattle_level1Player_strongEnoughToWin() {
        // Low-level player faces a weaker mob (str=2, HP up to 25) — with str=50 always wins
        Player player = playerWithHp(500, 50, 0);
        player.setLevel(1);
        int result = battleManager.mobBattle(player, channel);
        assertTrue("Level 1 player with str=50 must beat weak mob", result > 0);
    }

    @Test
    public void mobBattle_level5Player_strongEnoughToWin() {
        // Higher-level player faces stronger mob (str=3, HP up to 35) — with str=50 still wins
        Player player = playerWithHp(500, 50, 0);
        player.setLevel(5);
        int result = battleManager.mobBattle(player, channel);
        assertTrue("Level 5 player with str=50 must beat mob", result > 0);
    }

    @Test
    public void mobBattle_level1Player_returns1HpAfterWin() {
        // Player kills boss before boss can counterattack (1-shot kill), HP unchanged
        Player player = playerWithHp(1000, 1000, 0);
        player.setLevel(1);
        int hpBefore = player.getHp();
        int result = battleManager.mobBattle(player, channel);
        // Boss HP max 25, player deals 750–1250, always 1-shots. HP may drop by 0 (boss died before counterattack)
        assertTrue("HP after win must be > 0", result > 0);
        assertEquals("Should match player's actual HP", player.getHp(), result);
    }

    // ---- helpers ---------------------------------------------------------------

    private Player strongPlayer(int level) {
        Player p = playerWithHp(10000, 1000, 0);
        p.setLevel(level);
        return p;
    }

    private Player playerWithHp(int hp, int strength, int armor) {
        Player p = new Player("TestPlayer", "test-id");
        p.setHp(hp);
        p.setStrength(strength);
        p.setArmor(armor);
        return p;
    }
}
