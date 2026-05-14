package ru.chebe.litvinov.data;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for new Player fields introduced in feature/items-13-84:
 * locationHistory, lastExploreTime, bankInventory, completedQuests,
 * debt, pvpWins, mobKills, prestige, lastHorseRaceTime.
 */
public class PlayerNewFieldsTest {

    @Test
    public void locationHistory_initializedAsEmptyList() {
        Player player = new Player("Nick", "id1");
        assertNotNull("locationHistory should not be null", player.getLocationHistory());
        assertTrue("locationHistory should be empty", player.getLocationHistory().isEmpty());
    }

    @Test
    public void bankInventory_initializedAsEmptyMap() {
        Player player = new Player("Nick", "id2");
        assertNotNull("bankInventory should not be null", player.getBankInventory());
        assertTrue("bankInventory should be empty", player.getBankInventory().isEmpty());
    }

    @Test
    public void completedQuests_initializedAsEmptyList() {
        Player player = new Player("Nick", "id3");
        assertNotNull("completedQuests should not be null", player.getCompletedQuests());
        assertTrue("completedQuests should be empty", player.getCompletedQuests().isEmpty());
    }

    @Test
    public void debt_defaultIsZero() {
        Player player = new Player("Nick", "id4");
        assertEquals(0, player.getDebt());
    }

    @Test
    public void pvpWins_defaultIsZero() {
        Player player = new Player("Nick", "id5");
        assertEquals(0, player.getPvpWins());
    }

    @Test
    public void mobKills_defaultIsZero() {
        Player player = new Player("Nick", "id6");
        assertEquals(0, player.getMobKills());
    }

    @Test
    public void prestige_defaultIsZero() {
        Player player = new Player("Nick", "id7");
        assertEquals(0, player.getPrestige());
    }

    @Test
    public void lastExploreTime_defaultIsZero() {
        Player player = new Player("Nick", "id8");
        assertEquals(0L, player.getLastExploreTime());
    }

    @Test
    public void lastHorseRaceTime_defaultIsZero() {
        Player player = new Player("Nick", "id9");
        assertEquals(0L, player.getLastHorseRaceTime());
    }

    @Test
    public void newPlayer_fieldsAreMutable() {
        Player player = new Player("Nick", "id10");

        player.getLocationHistory().add("таверна");
        assertEquals(1, player.getLocationHistory().size());

        player.getBankInventory().put("меч", 1);
        assertEquals(1, (int) player.getBankInventory().get("меч"));

        player.getCompletedQuests().add("quest1");
        assertEquals(1, player.getCompletedQuests().size());

        player.setDebt(100);
        assertEquals(100, player.getDebt());

        player.setPvpWins(5);
        assertEquals(5, player.getPvpWins());

        player.setMobKills(10);
        assertEquals(10, player.getMobKills());

        player.setPrestige(2);
        assertEquals(2, player.getPrestige());

        player.setLastExploreTime(12345L);
        assertEquals(12345L, player.getLastExploreTime());

        player.setLastHorseRaceTime(99999L);
        assertEquals(99999L, player.getLastHorseRaceTime());
    }
}
