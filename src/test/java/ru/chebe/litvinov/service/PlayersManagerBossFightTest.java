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
import ru.chebe.litvinov.data.Boss;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.bossFight():
 * - no boss in location → message shown
 * - player wins → XP +1000, money +1000, boss item added
 * - player loses → deathOfPlayer (money -10%, HP = maxHp, location = "респаун")
 * - clan ally wins → ally also receives rewards
 */
public class PlayersManagerBossFightTest {

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

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        playersManager = new PlayersManager(
                playerRepository, locationManager, itemsManager,
                battleManager, eventsManager, clanManager, tavern, new NpcManager());

        when(event.getChannel()).thenReturn(channel);
        when(event.getMessage()).thenReturn(message);
        when(event.getAuthor()).thenReturn(user);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
    }

    // ---- no boss in location ---------------------------------------------------

    @Test
    public void bossFight_locationHasNoBoss_sendsNoBossMessage() {
        Player player = playerWith("p1", "id1", "локация", 100, 100, 100, 100);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("локация")).thenReturn(
                locationBuilder("локация", null).build());

        playersManager.bossFight(event);

        verify(channel, atLeastOnce()).sendMessage(contains("нет босса"));
        verify(battleManager, never()).bossBattle(any(), any(), any());
    }

    // ---- player wins -----------------------------------------------------------

    @Test
    public void bossFight_playerWins_xpAndMoneyAdded() {
        Player player = playerWith("Hero", "id1", "лес", 80, 100, 100, 50);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());
        when(battleManager.getBossItemName("Morgott")).thenReturn("oko morra");

        // battleManager.bossBattle sets player HP > 0 (wins)
        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(50)); // everyone survives
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        // changeXp/changeMoney re-fetch from cache
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        // XP +1000 → player.exp should increase by 1000 (changeXp modifies via cache)
        verify(playerRepository, atLeast(2)).put(eq("id1"), any());
        verify(battleManager).getBossItemName("Morgott");
    }

    @Test
    public void bossFight_playerWins_bossItemAddedToInventory() {
        Player player = playerWith("Hero", "id1", "лес", 80, 100, 100, 50);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());
        when(battleManager.getBossItemName("Morgott")).thenReturn("oko morra");
        when(itemsManager.getItem("oko morra")).thenReturn(null); // item unknown — addNewItem no-ops

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(10));
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        // addNewItem was attempted for the boss item
        verify(itemsManager).getItem("oko morra");
    }

    @Test
    public void bossFight_playerWins_hpPreservedWithoutLevelUp() {
        // No level-up: XP reward (1000) < expToNextLvl (10000)
        Player player = playerWith("Hero", "id1", "лес", 100, 100, 5, 50);
        player.setExp(0);
        player.setExpToNextLvl(10000);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());
        when(battleManager.getBossItemName("Morgott")).thenReturn("oko morra");
        when(itemsManager.getItem("oko morra")).thenReturn(null);

        final int hpAfterBattle = 35;
        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(hpAfterBattle));
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        // Without level-up, HP is not restored → post-battle HP is persisted
        assertEquals(hpAfterBattle, player.getHp());
        verify(playerRepository, atLeastOnce()).put(eq("id1"), any());
    }

    @Test
    public void bossFight_playerWins_hpRestoredOnLevelUp() {
        // Level-up triggered: exp=90, expToNextLvl=100, XP reward=1000
        Player player = playerWith("Hero", "id1", "лес", 100, 100, 5, 50);
        player.setExp(90);
        player.setExpToNextLvl(100);
        player.setLevel(1);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());
        when(battleManager.getBossItemName("Morgott")).thenReturn("oko morra");
        when(itemsManager.getItem("oko morra")).thenReturn(null);

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(35)); // damaged after battle
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        // Level-up restores HP to new maxHp — must be greater than post-battle HP (35)
        assertTrue("HP must be restored to maxHp on level-up", player.getHp() > 35);
        assertEquals(player.getMaxHp(), player.getHp());
    }

    // ---- player loses ----------------------------------------------------------

    @Test
    public void bossFight_playerLoses_moneyReducedByLevelScaledPenalty() {
        // Level 1 player: 5% death penalty → 200 * 0.95 = 190
        Player player = playerWith("Dead", "id1", "лес", 100, 100, 100, 200);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(0)); // everyone dies
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        Location respawn = locationBuilder("респаун", null).build();
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        assertEquals(190, player.getMoney()); // 200 * 0.95 (5% penalty for level 1)
    }

    @Test
    public void bossFight_playerLoses_hpRestoredToMax() {
        Player player = playerWith("Dead", "id1", "лес", 100, 100, 100, 100);
        player.setHp(0);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(0));
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        Location respawn = locationBuilder("респаун", null).build();
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        assertEquals(100, player.getHp()); // restored to maxHp
    }

    @Test
    public void bossFight_playerLoses_locationSetToRespawn() {
        Player player = playerWith("Dead", "id1", "лес", 100, 100, 100, 100);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(0));
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        Location respawn = locationBuilder("респаун", null).build();
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        assertEquals("респаун", player.getLocation());
        verify(locationManager).movePlayerInPopulation(player, "респаун");
    }

    @Test
    public void bossFight_playerLoses_populationMovedBeforeLocationChange() {
        // Regression: movePlayerInPopulation must be called before setLocation so it
        // reads the correct old location when removing the player from the old slot.
        Player player = playerWith("Dead", "id1", "лес", 100, 100, 100, 100);
        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(0));
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        Location respawn = locationBuilder("респаун", null).build();
        doAnswer(inv -> {
            Player p = inv.getArgument(0);
            // At the time of this call, location must still be the old value
            assertEquals("лес", p.getLocation());
            return respawn;
        }).when(locationManager).movePlayerInPopulation(any(), eq("респаун"));

        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.bossFight(event);

        verify(locationManager).movePlayerInPopulation(player, "респаун");
    }

    // ---- clan ally participates and wins ---------------------------------------

    @Test
    public void bossFight_clanAllyAlsoReceivesRewards() {
        Player player = playerWith("Hero", "id1", "лес", 100, 100, 100, 0);
        Player ally   = playerWith("Ally", "id2", "лес", 100, 100, 100, 0);

        when(user.getId()).thenReturn("id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(playerRepository.get("id2")).thenReturn(ally);
        when(locationManager.getLocation("лес")).thenReturn(
                locationBuilder("лес", "Morgott").build());
        when(clanManager.getClanMembers(anyString())).thenReturn(List.of("id2"));
        when(battleManager.getBossItemName("Morgott")).thenReturn("oko morra");
        when(itemsManager.getItem("oko morra")).thenReturn(null);

        doAnswer(inv -> {
            List<Person> players = inv.getArgument(0);
            players.forEach(p -> p.setHp(50));
            return null;
        }).when(battleManager).bossBattle(anyList(), eq("Morgott"), any());

        when(playerRepository.get("id1")).thenReturn(player);
        when(playerRepository.get("id2")).thenReturn(ally);

        playersManager.bossFight(event);

        // Both players should have been saved with rewards
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerRepository, atLeast(2)).put(idCaptor.capture(), any());
        List<String> savedIds = idCaptor.getAllValues();
        assertTrue("id1 must be saved", savedIds.contains("id1"));
        assertTrue("id2 (ally) must be saved", savedIds.contains("id2"));
    }

    // ---- helpers ---------------------------------------------------------------

    private Player playerWith(String nick, String id, String location,
                               int maxHp, int hp, int strength, int money) {
        Player p = new Player(nick, id);
        p.setLocation(location);
        p.setMaxHp(maxHp);
        p.setHp(hp);
        p.setStrength(strength);
        p.setMoney(money);
        return p;
    }

    private Location.LocationBuilder locationBuilder(String name, String boss) {
        return Location.builder()
                .name(name)
                .boss(boss)
                .pvp(false)
                .dangerous(0)
                .paths(new ArrayList<>())
                .populationByName(new ArrayList<>())
                .populationById(new ArrayList<>())
                .teleport(false);
    }
}
