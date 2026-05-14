package ru.chebe.litvinov.data;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for new Clan fields introduced in feature/items-13-84:
 * clanBank, clanUpgrades, clanBase, clanRoles.
 */
public class ClanNewFieldsTest {

    @Test
    public void clanBank_initializedAsEmptyMap() {
        Clan clan = new Clan("TestClan", "leader1");
        assertNotNull("clanBank should not be null", clan.getClanBank());
        assertTrue("clanBank should be empty", clan.getClanBank().isEmpty());
    }

    @Test
    public void clanUpgrades_initializedAsEmptyList() {
        Clan clan = new Clan("TestClan", "leader2");
        assertNotNull("clanUpgrades should not be null", clan.getClanUpgrades());
        assertTrue("clanUpgrades should be empty", clan.getClanUpgrades().isEmpty());
    }

    @Test
    public void clanBase_defaultIsRespawn() {
        Clan clan = new Clan("TestClan", "leader3");
        assertEquals("респаун", clan.getClanBase());
    }

    @Test
    public void clanRoles_initializedAsMapWithLeaderRole() {
        Clan clan = new Clan("TestClan", "leader4");
        assertNotNull("clanRoles should not be null", clan.getClanRoles());
        // Leader role is set in constructor
        assertTrue("clanRoles should contain leader entry", clan.getClanRoles().containsKey("leader4"));
        assertEquals("лидер", clan.getClanRoles().get("leader4"));
    }

    @Test
    public void clanBank_isMutable() {
        Clan clan = new Clan("TestClan", "leader5");
        clan.getClanBank().put("монеты", 500);
        assertEquals(500, (int) clan.getClanBank().get("монеты"));
    }

    @Test
    public void clanUpgrades_isMutable() {
        Clan clan = new Clan("TestClan", "leader6");
        clan.getClanUpgrades().add("дроп");
        assertEquals(1, clan.getClanUpgrades().size());
        assertTrue(clan.getClanUpgrades().contains("дроп"));
    }

    @Test
    public void clanBase_canBeUpdated() {
        Clan clan = new Clan("TestClan", "leader7");
        clan.setClanBase("таверна");
        assertEquals("таверна", clan.getClanBase());
    }

    @Test
    public void clanRoles_canAddNewRoles() {
        Clan clan = new Clan("TestClan", "leader8");
        clan.getClanRoles().put("member1", "ветеран");
        assertEquals("ветеран", clan.getClanRoles().get("member1"));
    }
}
