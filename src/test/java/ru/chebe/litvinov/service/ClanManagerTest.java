package ru.chebe.litvinov.service;

import org.apache.ignite.IgniteCache;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Player;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static ru.chebe.litvinov.Constants.MAX_CLAN_SIZE;

/**
 * Tests for ClanManager: registration, join/leave, apply acceptance/rejection,
 * member queries. Edge cases: duplicate clan names, size limits, leader transitions.
 */
public class ClanManagerTest {

    @Mock
    private IgniteCache<String, Clan> clanCache;

    @Mock
    private IgniteCache<String, Player> playerCache;

    private ClanManager clanManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        clanManager = new ClanManager(clanCache, playerCache);
    }

    // ---- registerClan ----------------------------------------------------------

    @Test
    public void registerClan_newName_createsClanAndReturnsEmpty() {
        when(clanCache.containsKey("TestClan")).thenReturn(false);

        String result = clanManager.registerClan("TestClan", "leader1");

        assertEquals("", result);
        verify(clanCache).put(eq("TestClan"), any(Clan.class));
    }

    @Test
    public void registerClan_duplicateName_returnsErrorMessage() {
        when(clanCache.containsKey("ExistingClan")).thenReturn(true);

        String result = clanManager.registerClan("ExistingClan", "leader2");

        assertFalse(result.isEmpty());
        verify(clanCache, never()).put(anyString(), any(Clan.class));
    }

    // ---- joinClan --------------------------------------------------------------

    @Test
    public void joinClan_nonExistentClan_returnsErrorMessage() {
        when(clanCache.get("Ghost")).thenReturn(null);

        String result = clanManager.joinClan("Ghost", "player1");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("не существует"));
    }

    @Test
    public void joinClan_clanAtCapacity_returnsCapacityMessage() {
        Clan full = buildClan("Full", "leader", MAX_CLAN_SIZE, 0);
        when(clanCache.get("Full")).thenReturn(full);

        String result = clanManager.joinClan("Full", "newcomer");

        assertFalse(result.isEmpty());
        assertTrue(result.contains(String.valueOf(MAX_CLAN_SIZE)));
    }

    @Test
    public void joinClan_clanWithRoomForApplier_addsApplierAndReturnsEmpty() {
        // Empty clan with one leader-member — room for (MAX_CLAN_SIZE - 1) appliers
        Clan clan = buildClan("OpenClan", "leader1", 1, 0);
        when(clanCache.get("OpenClan")).thenReturn(clan);

        String result = clanManager.joinClan("OpenClan", "newPlayer");

        assertEquals("", result);
        assertTrue(clan.getAppliers().contains("newPlayer"));
    }

    @Test
    public void joinClan_membersAndAppliersExceedCap_returnsCapacityMessage() {
        // members=1, appliers=MAX_CLAN_SIZE-1 → total slots occupied
        Clan clan = buildClan("FullWithAppliers", "leader", 1, MAX_CLAN_SIZE - 1);
        when(clanCache.get("FullWithAppliers")).thenReturn(clan);

        String result = clanManager.joinClan("FullWithAppliers", "extra");

        assertFalse(result.isEmpty());
    }

    // ---- leaveClan -------------------------------------------------------------

    @Test
    public void leaveClan_lastMemberLeaves_removesEntireClan() {
        Clan clan = buildClan("Solo", "only", 1, 0);
        when(clanCache.get("Solo")).thenReturn(clan);

        clanManager.leaveClan("Solo", "only");

        verify(clanCache).remove("Solo");
    }

    @Test
    public void leaveClan_leaderLeaves_transfersLeadershipToFirstRemaining() {
        Clan clan = buildClan("Alpha", "leader", 2, 0);
        // members = ["leader", "member2"]
        String secondMember = clan.getMembers().get(1);
        when(clanCache.get("Alpha")).thenReturn(clan);

        clanManager.leaveClan("Alpha", "leader");

        assertFalse(clan.getMembers().contains("leader"));
        assertEquals(secondMember, clan.getLeaderId());
    }

    @Test
    public void leaveClan_nonLeaderLeaves_clanIntact() {
        Clan clan = buildClan("Beta", "leader", 2, 0);
        String member = clan.getMembers().get(1);
        when(clanCache.get("Beta")).thenReturn(clan);

        clanManager.leaveClan("Beta", member);

        assertFalse(clan.getMembers().contains(member));
        assertEquals("leader", clan.getLeaderId()); // leader unchanged
        verify(clanCache, never()).remove("Beta");
    }

    // ---- rejectApply -----------------------------------------------------------

    @Test
    public void rejectApply_byLeader_withActiveAppliers_clearsAppliersAndReturnsEmpty() {
        Clan clan = buildClan("Clan1", "lead", 1, 1);
        when(clanCache.get("Clan1")).thenReturn(clan);

        String result = clanManager.rejectApply("Clan1", "lead");

        assertEquals("", result);
        assertTrue(clan.getAppliers().isEmpty());
    }

    @Test
    public void rejectApply_byLeader_noAppliers_returnsNoApplicationsMessage() {
        Clan clan = buildClan("Clan2", "lead", 1, 0);
        when(clanCache.get("Clan2")).thenReturn(clan);

        String result = clanManager.rejectApply("Clan2", "lead");

        assertFalse(result.isEmpty());
    }

    @Test
    public void rejectApply_byNonLeader_returnsAccessDeniedMessage() {
        Clan clan = buildClan("Clan3", "lead", 1, 1);
        when(clanCache.get("Clan3")).thenReturn(clan);

        String result = clanManager.rejectApply("Clan3", "intruder");

        assertTrue(result.contains("не являетесь лидером"));
    }

    // ---- getClanInfo -----------------------------------------------------------

    @Test
    public void getClanInfo_emptyName_returnsInputErrorMessage() {
        String result = clanManager.getClanInfo("");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("не ввели"));
    }

    @Test
    public void getClanInfo_nonExistentClan_returnsNotFoundMessage() {
        when(clanCache.get("NoSuchClan")).thenReturn(null);

        String result = clanManager.getClanInfo("NoSuchClan");

        assertTrue(result.contains("не существует"));
    }

    @Test
    public void getClanInfo_existingClan_containsClanNameAndLeader() {
        Clan clan = buildClan("Warriors", "lead1", 2, 0);
        when(clanCache.get("Warriors")).thenReturn(clan);

        // Set up player lookup for members
        Player leader = new Player("LeaderNick", "lead1");
        Player member2 = new Player("Member2Nick", clan.getMembers().get(1));
        when(playerCache.get("lead1")).thenReturn(leader);
        when(playerCache.get(clan.getMembers().get(1))).thenReturn(member2);

        String result = clanManager.getClanInfo("Warriors");

        assertTrue(result.contains("Warriors"));
        assertTrue(result.contains("LeaderNick"));
    }

    // ---- getClanMembers --------------------------------------------------------

    @Test
    public void getClanMembers_forExistingClan_returnsMemberList() {
        Clan clan = buildClan("Gamma", "leader", 2, 0);
        when(clanCache.get("Gamma")).thenReturn(clan);

        List<String> members = clanManager.getClanMembers("Gamma");

        assertEquals(2, members.size());
        assertTrue(members.contains("leader"));
    }

    @Test
    public void getClanMembers_forNonExistentClan_returnsEmptyList() {
        when(clanCache.get("Gone")).thenReturn(null);

        List<String> members = clanManager.getClanMembers("Gone");

        assertNotNull(members);
        assertTrue(members.isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Build a clan with a given number of members (leader + (memberCount-1) others)
     * and the specified number of pending appliers.
     */
    private Clan buildClan(String name, String leaderId, int memberCount, int applierCount) {
        Clan clan = new Clan(name, leaderId);
        for (int i = 1; i < memberCount; i++) {
            clan.getMembers().add("member" + i);
        }
        for (int i = 0; i < applierCount; i++) {
            clan.getAppliers().add("applier" + i);
        }
        return clan;
    }
}
