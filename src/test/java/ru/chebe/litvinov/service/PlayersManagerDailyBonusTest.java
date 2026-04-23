package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.dailyBonus():
 * - first claim
 * - already claimed today
 * - 3-day streak (+50 bonus)
 * - 7-day streak (rare item)
 * - streak reset after >48h gap
 */
public class PlayersManagerDailyBonusTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private LocationManager locationManager;
    @Mock private ItemsManager itemsManager;
    @Mock private BattleManager battleManager;
    @Mock private EventsManager eventsManager;
    @Mock private ClanManager clanManager;
    @Mock private Tavern tavern;

    @Mock private MessageReceivedEvent event;
    @Mock private Message message;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private User user;

    private PlayersManager playersManager;

    private static final long ONE_DAY_MS  = 24L * 60 * 60 * 1000;
    private static final long TWO_DAYS_MS = 48L * 60 * 60 * 1000;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        playersManager = new PlayersManager(
                playerRepository, locationManager, itemsManager,
                battleManager, eventsManager, clanManager, tavern, new NpcManager());

        when(event.getChannel()).thenReturn(channel);
        when(event.getMessage()).thenReturn(message);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("uid1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
    }

    // ---- first-time claim ------------------------------------------------------

    @Test
    public void dailyBonus_firstClaim_moneyIncreasedByLevelScaledBonus() {
        // level=1: DAILY_BONUS_BASE(50) + 1*5 = 55
        Player player = freshPlayer(0);
        player.setDailyTime(0); // never claimed
        player.setMoney(50);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(105, player.getMoney()); // 50 + 55
    }

    @Test
    public void dailyBonus_firstClaim_streakSetTo1() {
        Player player = freshPlayer(0);
        player.setDailyTime(0);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(1, player.getDailyStreak());
    }

    @Test
    public void dailyBonus_firstClaim_playerIsSaved() {
        Player player = freshPlayer(0);
        player.setDailyTime(0);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        verify(playerRepository).put("uid1", player);
    }

    // ---- already claimed -------------------------------------------------------

    @Test
    public void dailyBonus_alreadyClaimedToday_moneyUnchanged() {
        Player player = freshPlayer(1);
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS / 2); // 12h ago
        player.setMoney(200);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(200, player.getMoney());
        verify(playerRepository, never()).put(anyString(), any());
    }

    @Test
    public void dailyBonus_alreadyClaimedToday_sendsComeBackMessage() {
        Player player = freshPlayer(1);
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS / 2);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(channel, atLeastOnce()).sendMessage(captor.capture());
        boolean found = captor.getAllValues().stream()
                .anyMatch(s -> s.contains("уже получили") || s.contains("приходите"));
        assertTrue("Should tell player to come back", found);
    }

    // ---- 3-day streak ----------------------------------------------------------

    @Test
    public void dailyBonus_thirdDay_bonusMoney() {
        // streak = 2, now claiming day 3: base(55) + streak-3 bonus(50) = 105
        Player player = freshPlayer(2);
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS - 1000); // >24h ago
        player.setMoney(0);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        // level=1: 55 base + 50 streak bonus = 105
        assertEquals(105, player.getMoney());
    }

    @Test
    public void dailyBonus_thirdDay_streakBecomesThree() {
        Player player = freshPlayer(2);
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS - 1000);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(3, player.getDailyStreak());
    }

    @Test
    public void dailyBonus_thirdDay_achievementUnlocked() {
        Player player = freshPlayer(2);
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS - 1000);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertTrue(player.getAchievements().contains("стрик_3"));
    }

    // ---- 7-day streak ----------------------------------------------------------

    @Test
    public void dailyBonus_seventhDay_rareItemAdded() {
        Player player = freshPlayer(6); // streak = 6, claiming day 7
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS - 1000);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(7, player.getDailyStreak());
        // Player starts with 2 "вино лаба" from startInventory(), streak adds 1 more → 3
        assertEquals(3, (int) player.getInventory().getOrDefault("вино лаба", 0));
    }

    @Test
    public void dailyBonus_seventhDay_achievementUnlocked() {
        Player player = freshPlayer(6);
        player.setDailyTime(System.currentTimeMillis() - ONE_DAY_MS - 1000);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertTrue(player.getAchievements().contains("стрик_7"));
    }

    // ---- streak reset after gap ------------------------------------------------

    @Test
    public void dailyBonus_gapMoreThan48Hours_streakResetsTo1() {
        Player player = freshPlayer(5);
        // Last claim was >48h ago — streak should reset
        player.setDailyTime(System.currentTimeMillis() - TWO_DAYS_MS - 1000);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(1, player.getDailyStreak());
    }

    @Test
    public void dailyBonus_gapMoreThan48Hours_noStreakBonusApplied() {
        // level=1: DAILY_BONUS_BASE(50) + 1*5 = 55, no streak bonus
        Player player = freshPlayer(5);
        player.setDailyTime(System.currentTimeMillis() - TWO_DAYS_MS - 1000);
        player.setMoney(0);
        when(playerRepository.get("uid1")).thenReturn(player);

        playersManager.dailyBonus(event);

        assertEquals(55, player.getMoney()); // only base bonus, no streak bonus
    }

    // ---- helpers ---------------------------------------------------------------

    private Player freshPlayer(int streak) {
        Player p = new Player("TestPlayer", "uid1");
        p.setLevel(1);
        p.setMoney(0);
        p.setDailyStreak(streak);
        p.setDailyTime(0);
        return p;
    }
}
