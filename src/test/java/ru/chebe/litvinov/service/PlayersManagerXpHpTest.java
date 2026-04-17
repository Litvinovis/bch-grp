package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional tests for PlayersManager focusing on XP/level-up mechanics,
 * money changes, reputation, death, item management, and XP/HP maps.
 */
public class PlayersManagerXpHpTest {

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LocationManager locationManager;
    @Mock
    private ItemsManager itemsManager;
    @Mock
    private BattleManager battleManager;
    @Mock
    private EventsManager eventsManager;
    @Mock
    private ClanManager clanManager;
    @Mock
    private Tavern tavern;

    private PlayersManager playersManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        playersManager = new PlayersManager(
                playerRepository, locationManager, itemsManager,
                battleManager, eventsManager, clanManager, tavern);
    }

    // ---- getXp (xpMap) ---------------------------------------------------------

    @Test
    public void getXp_level1Player_requiresXpForLevel2() {
        Player player = new Player("Lvl1", "id1");
        // level=1 → xpMap.get(2) = 100
        int xpRequired = playersManager.getXp(player);
        assertEquals(100, xpRequired);
    }

    @Test
    public void getXp_level50Player_requiresXpForLevel51() {
        Player player = new Player("Lvl50", "id50");
        player.setLevel(50);
        // xpMap.get(51) = 50*100 = 5000
        int xpRequired = playersManager.getXp(player);
        assertEquals(5000, xpRequired);
    }

    // ---- getMaxHp (hpMap) ------------------------------------------------------

    @Test
    public void getMaxHp_level1Player_returns100() {
        Player player = new Player("p", "id1");
        assertEquals(100, playersManager.getMaxHp(player));
    }

    @Test
    public void getMaxHp_level10Player_returns190() {
        Player player = new Player("p", "id1");
        player.setLevel(10);
        // hpMap: level 10 = 100 + 9*10 = 190
        assertEquals(190, playersManager.getMaxHp(player));
    }

    @Test
    public void getMaxHp_level100Player_returns1090() {
        Player player = new Player("p", "id1");
        player.setLevel(100);
        // hpMap: level 100 = 100 + 99*10 = 1090
        assertEquals(1090, playersManager.getMaxHp(player));
    }

    // ---- changeXp / level-up ---------------------------------------------------

    @Test
    public void changeXp_belowThreshold_incrementsExp() {
        Player player = new Player("Hero", "id1");
        player.setExp(50);
        player.setExpToNextLvl(100);
        player.setLevel(1);
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.changeXp("id1", 30);

        assertEquals(80, player.getExp());
        assertEquals(1, player.getLevel());
        verify(playerRepository).put("id1", player);
    }

    @Test
    public void changeXp_exactlyAtThreshold_triggersLevelUp() {
        Player player = new Player("Hero", "id1");
        player.setExp(50);
        player.setExpToNextLvl(100);
        player.setLevel(1);
        player.setMaxHp(100);
        when(playerRepository.get("id1")).thenReturn(player);

        // 50 + 50 = 100 >= expToNextLvl → level up
        playersManager.changeXp("id1", 50);

        assertEquals(2, player.getLevel());
    }

    @Test
    public void changeXp_overThreshold_triggersLevelUpAndSetsExp() {
        Player player = new Player("Hero", "id1");
        player.setExp(80);
        player.setExpToNextLvl(100);
        player.setLevel(1);
        player.setMaxHp(100);
        when(playerRepository.get("id1")).thenReturn(player);

        // Level-up is triggered since 80+60=140 >= 100
        // After level up: totalXp = 140 - 100 = 40, level = 2
        playersManager.changeXp("id1", 60);

        assertEquals(2, player.getLevel());
        assertEquals(40, player.getExp());  // Остаток после повышения уровня
    }

    @Test
    public void changeXp_levelUp_restoresHpToMax() {
        Player player = new Player("Hero", "id1");
        player.setExp(90);
        player.setExpToNextLvl(100);
        player.setLevel(1);
        player.setHp(10); // low HP
        player.setMaxHp(100);
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.changeXp("id1", 20);

        // After level-up, HP should be set to the new maxHp for level 2
        // hpMap.get(2) = 110
        assertEquals(110, player.getHp());
    }

    @Test
    public void changeXp_nullPlayer_doesNotThrow() {
        when(playerRepository.get("ghost")).thenReturn(null);
        // Must not throw NPE
        playersManager.changeXp("ghost", 100);
        verify(playerRepository, never()).put(anyString(), any());
    }

    // ---- changeMoney -----------------------------------------------------------

    @Test
    public void changeMoney_increase_addsMoney() {
        Player player = new Player("Rich", "id1");
        player.setMoney(100);
        when(playerRepository.get("id1")).thenReturn(player);

        int result = playersManager.changeMoney("id1", 50, true);

        assertEquals(150, result);
        verify(playerRepository).put("id1", player);
    }

    @Test
    public void changeMoney_decrease_subtractsMoney() {
        Player player = new Player("Poor", "id1");
        player.setMoney(100);
        when(playerRepository.get("id1")).thenReturn(player);

        int result = playersManager.changeMoney("id1", 30, false);

        assertEquals(70, result);
    }

    @Test
    public void changeMoney_increase_nullPlayer_returnsZero() {
        when(playerRepository.get("none")).thenReturn(null);

        int result = playersManager.changeMoney("none", 100, true);

        assertEquals(0, result);
    }

    // ---- changeReputation ------------------------------------------------------

    @Test
    public void changeReputation_increase_addsReputation() {
        Player player = new Player("Hero", "id1");
        player.setReputation(5);
        when(playerRepository.get("id1")).thenReturn(player);

        int result = playersManager.changeReputation("id1", 3, true);

        assertEquals(8, result);
    }

    @Test
    public void changeReputation_decrease_subtractsReputation() {
        Player player = new Player("Hero", "id1");
        player.setReputation(10);
        when(playerRepository.get("id1")).thenReturn(player);

        int result = playersManager.changeReputation("id1", 4, false);

        assertEquals(6, result);
    }

    @Test
    public void changeReputation_nullPlayer_returnsZero() {
        when(playerRepository.get("gone")).thenReturn(null);

        int result = playersManager.changeReputation("gone", 5, true);

        assertEquals(0, result);
    }

    // ---- changeHp (void overload) -----------------------------------------------

    @Test
    public void changeHp_voidOverload_setsHpDirectly() {
        Player player = new Player("Hero", "id1");
        player.setHp(50);
        when(playerRepository.get("id1")).thenReturn(player);

        playersManager.changeHp("id1", 75);

        assertEquals(75, player.getHp());
        verify(playerRepository).put("id1", player);
    }

    @Test
    public void changeHp_voidOverload_nullPlayer_doesNotThrow() {
        when(playerRepository.get("null")).thenReturn(null);
        playersManager.changeHp("null", 50);
        verify(playerRepository, never()).put(anyString(), any());
    }

    // ---- changeLuck / changeStrength -------------------------------------------

    @Test
    public void changeLuck_decrease_subtractsLuck() {
        Player player = new Player("Hero", "id1");
        player.setLuck(10);
        when(playerRepository.get("id1")).thenReturn(player);

        int result = playersManager.changeLuck("id1", 3, false);

        assertEquals(7, result);
    }

    @Test
    public void changeStrength_decrease_subtractsStrength() {
        Player player = new Player("Hero", "id1");
        player.setStrength(8);
        when(playerRepository.get("id1")).thenReturn(player);

        int result = playersManager.changeStrength("id1", 2, false);

        assertEquals(6, result);
    }

    // ---- deathOfPlayer ---------------------------------------------------------

    @Test
    public void deathOfPlayer_reduces10PercentMoney() {
        Player player = new Player("Dead", "id1");
        player.setMoney(100);
        player.setHp(0);
        player.setMaxHp(100);
        player.setLocation("мейн");

        Location respawn = buildLocation("респаун");
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);

        playersManager.deathOfPlayer(player);

        assertEquals(90, player.getMoney());
    }

    @Test
    public void deathOfPlayer_restoresHpToMax() {
        Player player = new Player("Dead", "id1");
        player.setMoney(200);
        player.setHp(0);
        player.setMaxHp(150);
        player.setLocation("мейн");

        Location respawn = buildLocation("респаун");
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);

        playersManager.deathOfPlayer(player);

        assertEquals(150, player.getHp());
    }

    @Test
    public void deathOfPlayer_movesPlayerToRespawn() {
        Player player = new Player("Dead", "id1");
        player.setMoney(50);
        player.setHp(0);
        player.setMaxHp(100);
        player.setLocation("кушетка");

        Location respawn = buildLocation("респаун");
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);

        playersManager.deathOfPlayer(player);

        assertEquals("респаун", player.getLocation());
        verify(playerRepository).put("id1", player);
    }

    // ---- addNewItem ------------------------------------------------------------

    @Test
    public void addNewItem_newItemNotInInventory_addsWithCount1AndAppliesStats() {
        Player player = new Player("Hero", "id1");
        player.setReputation(0);
        player.setHp(100);
        player.setArmor(0);
        player.setLuck(5);
        player.setStrength(5);

        Item item = Item.builder().name("шлем").price(100).action(false)
                .reputation(2).health(10).armor(3).luck(1).strength(0).build();

        when(playerRepository.get("id1")).thenReturn(player);
        when(itemsManager.getItem("шлем")).thenReturn(item);

        playersManager.addNewItem("id1", "шлем");

        assertEquals(1, (int) player.getInventory().get("шлем"));
        assertEquals(2, player.getReputation());
        assertEquals(110, player.getHp());
        assertEquals(3, player.getArmor());
        assertEquals(6, player.getLuck());
    }

    @Test
    public void addNewItem_itemAlreadyInInventory_incrementsCount() {
        Player player = new Player("Hero", "id1");
        player.getInventory().put("зелье", 2);

        Item item = Item.builder().name("зелье").price(10).action(true)
                .health(30).reputation(0).armor(0).luck(0).strength(0).build();

        when(playerRepository.get("id1")).thenReturn(player);
        when(itemsManager.getItem("зелье")).thenReturn(item);

        playersManager.addNewItem("id1", "зелье");

        assertEquals(3, (int) player.getInventory().get("зелье"));
    }

    @Test
    public void addNewItem_unknownItem_doesNotModifyPlayer() {
        Player player = new Player("Hero", "id1");
        when(playerRepository.get("id1")).thenReturn(player);
        when(itemsManager.getItem("nonexistent")).thenReturn(null);

        playersManager.addNewItem("id1", "nonexistent");

        verify(playerRepository, never()).put(anyString(), any());
    }

    // ---- deleteItem ------------------------------------------------------------

    @Test
    public void deleteItem_passiveItemWithCount2_decrementCount() {
        Player player = new Player("Hero", "id1");
        player.getInventory().put("броня", 2);
        player.setArmor(5);

        Item item = Item.builder().name("броня").price(500).action(false)
                .armor(2).health(0).reputation(0).luck(0).strength(0).build();

        when(playerRepository.get("id1")).thenReturn(player);
        when(itemsManager.getItem("броня")).thenReturn(item);

        playersManager.deleteItem("id1", "броня");

        assertEquals(1, (int) player.getInventory().get("броня"));
        // Armor stat decreased
        assertEquals(3, player.getArmor());
    }

    @Test
    public void deleteItem_passiveItemWithCount1_removesFromInventory() {
        Player player = new Player("Hero", "id1");
        player.getInventory().put("меч", 1);
        player.setStrength(8);

        Item item = Item.builder().name("меч").price(300).action(false)
                .strength(3).armor(0).health(0).reputation(0).luck(0).build();

        when(playerRepository.get("id1")).thenReturn(player);
        when(itemsManager.getItem("меч")).thenReturn(item);

        playersManager.deleteItem("id1", "меч");

        assertFalse(player.getInventory().containsKey("меч"));
        assertEquals(5, player.getStrength());
    }

    @Test
    public void deleteItem_actionItem_doesNotAffectStats() {
        Player player = new Player("Hero", "id1");
        player.getInventory().put("зелье", 3);
        player.setHp(80);

        Item item = Item.builder().name("зелье").price(10).action(true)
                .health(30).armor(0).reputation(0).luck(0).strength(0).build();

        when(playerRepository.get("id1")).thenReturn(player);
        when(itemsManager.getItem("зелье")).thenReturn(item);

        playersManager.deleteItem("id1", "зелье");

        // Action items don't affect stats on delete
        assertEquals(80, player.getHp());
        assertEquals(2, (int) player.getInventory().get("зелье"));
    }

    // ---- helpers ---------------------------------------------------------------

    private Location buildLocation(String name) {
        return Location.builder()
                .name(name)
                .pvp(false)
                .dangerous(0)
                .paths(new ArrayList<>())
                .populationByName(new ArrayList<>())
                .populationById(new ArrayList<>())
                .teleport(false)
                .build();
    }

}
