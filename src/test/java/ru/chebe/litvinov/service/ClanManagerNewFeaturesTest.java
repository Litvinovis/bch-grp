package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.ClanRepository;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for new ClanManager features: clan bank deposit/withdraw, upgrades purchase,
 * setClanBase, promoteMember, kickMember, getAllClans.
 */
public class ClanManagerNewFeaturesTest {

    @Mock
    private ClanRepository clanRepository;

    @Mock
    private PlayerRepository playerRepository;

    private ClanManager clanManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        clanManager = new ClanManager(clanRepository, playerRepository);
    }

    // ---- clanBankDeposit -------------------------------------------------------

    @Test
    public void clanBankDeposit_increasesBalanceAndDecreasesPlayerMoney() {
        Clan clan = buildClan("Alpha", "leader1");
        when(clanRepository.get("Alpha")).thenReturn(clan);

        Player leader = new Player("LeaderNick", "leader1");
        leader.setMoney(100);
        when(clanRepository.bankTransfer("Alpha", "leader1", 40)).thenReturn(true);

        String result = clanManager.clanBankDeposit("Alpha", "leader1", 40, leader);

        assertEquals("", result);
        assertEquals(60, leader.getMoney());
        assertEquals(40, (int) clan.getClanBank().getOrDefault("монеты", 0));
        verify(clanRepository).bankTransfer("Alpha", "leader1", 40);
    }

    @Test
    public void clanBankDeposit_insufficientFunds_returnsError() {
        Clan clan = buildClan("Beta", "leader2");
        when(clanRepository.get("Beta")).thenReturn(clan);

        Player player = new Player("Nick", "leader2");
        player.setMoney(10);

        String result = clanManager.clanBankDeposit("Beta", "leader2", 50, player);

        assertFalse(result.isEmpty());
        assertEquals(10, player.getMoney()); // unchanged
    }

    @Test
    public void clanBankDeposit_clanNotFound_returnsError() {
        when(clanRepository.get("Ghost")).thenReturn(null);
        Player player = new Player("Nick", "p1");
        player.setMoney(100);

        String result = clanManager.clanBankDeposit("Ghost", "p1", 10, player);

        assertFalse(result.isEmpty());
    }

    // ---- clanBankWithdraw ------------------------------------------------------

    @Test
    public void clanBankWithdraw_leaderWithdrawsSuccessfully() {
        Clan clan = buildClan("Gamma", "leader3");
        clan.getClanBank().put("монеты", 200);
        when(clanRepository.get("Gamma")).thenReturn(clan);

        Player leader = new Player("LeaderNick", "leader3");
        leader.setMoney(0);
        when(clanRepository.bankTransfer("Gamma", "leader3", -80)).thenReturn(true);

        String result = clanManager.clanBankWithdraw("Gamma", "leader3", 80, leader);

        assertEquals("", result);
        assertEquals(80, leader.getMoney());
        assertEquals(120, (int) clan.getClanBank().get("монеты"));
        verify(clanRepository).bankTransfer("Gamma", "leader3", -80);
    }

    @Test
    public void clanBankWithdraw_nonLeaderReturnsError() {
        Clan clan = buildClan("Delta", "leader4");
        clan.getClanBank().put("монеты", 500);
        when(clanRepository.get("Delta")).thenReturn(clan);

        Player notLeader = new Player("Member", "member1");
        notLeader.setMoney(0);

        String result = clanManager.clanBankWithdraw("Delta", "member1", 50, notLeader);

        assertFalse("Non-leader should get error", result.isEmpty());
        assertEquals(0, notLeader.getMoney()); // unchanged
    }

    @Test
    public void clanBankWithdraw_insufficientBalance_returnsError() {
        Clan clan = buildClan("Epsilon", "leader5");
        clan.getClanBank().put("монеты", 10);
        when(clanRepository.get("Epsilon")).thenReturn(clan);

        Player leader = new Player("Leader", "leader5");
        leader.setMoney(0);

        String result = clanManager.clanBankWithdraw("Epsilon", "leader5", 100, leader);

        assertFalse("Insufficient balance should return error", result.isEmpty());
        assertEquals(0, leader.getMoney()); // unchanged
    }

    // ---- purchaseClanUpgrade ---------------------------------------------------

    @Test
    public void purchaseClanUpgrade_drop_successDeductsMoneyAndAddsUpgrade() {
        Clan clan = buildClan("Zeta", "leader6");
        clan.getClanBank().put("монеты", 1000);
        when(clanRepository.get("Zeta")).thenReturn(clan);

        String result = clanManager.purchaseClanUpgrade("Zeta", "leader6", "дроп");

        assertEquals("", result);
        assertTrue(clan.getClanUpgrades().contains("дроп"));
        assertEquals(500, (int) clan.getClanBank().get("монеты")); // 1000 - 500
        verify(clanRepository).put("Zeta", clan);
    }

    @Test
    public void purchaseClanUpgrade_alreadyOwned_returnsError() {
        Clan clan = buildClan("Eta", "leader7");
        clan.getClanBank().put("монеты", 2000);
        clan.getClanUpgrades().add("дроп");
        when(clanRepository.get("Eta")).thenReturn(clan);

        String result = clanManager.purchaseClanUpgrade("Eta", "leader7", "дроп");

        assertFalse("Buying owned upgrade should return error", result.isEmpty());
        // Money unchanged
        assertEquals(2000, (int) clan.getClanBank().get("монеты"));
    }

    @Test
    public void purchaseClanUpgrade_insufficientFunds_returnsError() {
        Clan clan = buildClan("Theta", "leader8");
        clan.getClanBank().put("монеты", 10);
        when(clanRepository.get("Theta")).thenReturn(clan);

        String result = clanManager.purchaseClanUpgrade("Theta", "leader8", "дроп");

        assertFalse("Insufficient funds should return error", result.isEmpty());
        assertFalse(clan.getClanUpgrades().contains("дроп"));
    }

    @Test
    public void purchaseClanUpgrade_nonLeader_returnsError() {
        Clan clan = buildClan("Iota", "leader9");
        clan.getClanBank().put("монеты", 1000);
        when(clanRepository.get("Iota")).thenReturn(clan);

        String result = clanManager.purchaseClanUpgrade("Iota", "notLeader", "дроп");

        assertFalse("Non-leader should get error", result.isEmpty());
    }

    // ---- setClanBase / getClanBase ---------------------------------------------

    @Test
    public void setClanBase_leaderSetsBase_getClanBaseReturnsIt() {
        Clan clan = buildClan("Kappa", "leader10");
        when(clanRepository.get("Kappa")).thenReturn(clan);

        String setResult = clanManager.setClanBase("Kappa", "leader10", "таверна");

        assertEquals("", setResult);
        assertEquals("таверна", clan.getClanBase());
        verify(clanRepository).put("Kappa", clan);

        // getClanBase should return the updated location
        when(clanRepository.get("Kappa")).thenReturn(clan);
        assertEquals("таверна", clanManager.getClanBase("Kappa"));
    }

    @Test
    public void setClanBase_nonLeader_returnsError() {
        Clan clan = buildClan("Lambda", "leader11");
        when(clanRepository.get("Lambda")).thenReturn(clan);

        String result = clanManager.setClanBase("Lambda", "notLeader", "таверна");

        assertFalse("Non-leader should get error", result.isEmpty());
    }

    @Test
    public void getClanBase_clanNotFound_returnsDefaultRespawn() {
        when(clanRepository.get("Missing")).thenReturn(null);

        String base = clanManager.getClanBase("Missing");

        assertEquals("респаун", base);
    }

    @Test
    public void getClanBase_blankClanBase_returnsDefaultRespawn() {
        Clan clan = buildClan("Mu", "leader12");
        clan.setClanBase("");
        when(clanRepository.get("Mu")).thenReturn(clan);

        String base = clanManager.getClanBase("Mu");

        assertEquals("респаун", base);
    }

    // ---- promoteMember ---------------------------------------------------------

    @Test
    public void promoteMember_ryadovoyToVeteran() {
        Clan clan = buildClan("Nu", "leader13");
        clan.getMembers().add("member13");
        when(clanRepository.get("Nu")).thenReturn(clan);
        when(playerRepository.get("member13")).thenReturn(new Player("MemberNick", "member13"));

        String result = clanManager.promoteMember("Nu", "leader13", "member13");

        assertTrue("Result should mention ветеран", result.contains("ветеран"));
        assertEquals("ветеран", clan.getClanRoles().get("member13"));
    }

    @Test
    public void promoteMember_veteranToOfficer() {
        Clan clan = buildClan("Xi", "leader14");
        clan.getMembers().add("member14");
        clan.getClanRoles().put("member14", "ветеран");
        when(clanRepository.get("Xi")).thenReturn(clan);
        when(playerRepository.get("member14")).thenReturn(new Player("MemberNick", "member14"));

        String result = clanManager.promoteMember("Xi", "leader14", "member14");

        assertTrue("Result should mention офицер", result.contains("офицер"));
        assertEquals("офицер", clan.getClanRoles().get("member14"));
    }

    @Test
    public void promoteMember_nonLeader_returnsError() {
        Clan clan = buildClan("Omicron", "leader15");
        clan.getMembers().add("member15");
        when(clanRepository.get("Omicron")).thenReturn(clan);

        String result = clanManager.promoteMember("Omicron", "notLeader", "member15");

        assertFalse("Non-leader should get error", result.isEmpty());
    }

    @Test
    public void promoteMember_notInClan_returnsError() {
        Clan clan = buildClan("Pi", "leader16");
        when(clanRepository.get("Pi")).thenReturn(clan);

        String result = clanManager.promoteMember("Pi", "leader16", "stranger");

        assertFalse("Non-member promote should return error", result.isEmpty());
    }

    // ---- kickMember ------------------------------------------------------------

    @Test
    public void kickMember_leaderKicksMember_memberRemovedFromClan() {
        Clan clan = buildClan("Rho", "leader17");
        clan.getMembers().add("member17");
        when(clanRepository.get("Rho")).thenReturn(clan);

        Player kicked = new Player("KickedNick", "member17");
        kicked.setClanName("Rho");
        when(playerRepository.get("member17")).thenReturn(kicked);

        String result = clanManager.kickMember("Rho", "leader17", "member17");

        assertEquals("", result);
        assertFalse(clan.getMembers().contains("member17"));
        verify(clanRepository).put("Rho", clan);
    }

    @Test
    public void kickMember_leaderKicksThemselves_returnsError() {
        Clan clan = buildClan("Sigma", "leader18");
        when(clanRepository.get("Sigma")).thenReturn(clan);

        String result = clanManager.kickMember("Sigma", "leader18", "leader18");

        assertFalse("Leader cannot kick themselves", result.isEmpty());
        assertTrue(clan.getMembers().contains("leader18")); // still in clan
    }

    @Test
    public void kickMember_nonLeaderKicks_returnsError() {
        Clan clan = buildClan("Tau", "leader19");
        clan.getMembers().add("member19");
        when(clanRepository.get("Tau")).thenReturn(clan);

        String result = clanManager.kickMember("Tau", "member19", "leader19");

        assertFalse("Non-leader kick should return error", result.isEmpty());
    }

    // ---- getAllClans ------------------------------------------------------------

    @Test
    public void getAllClans_returnsListFromRepository() {
        List<Clan> clans = new ArrayList<>();
        clans.add(buildClan("C1", "l1"));
        clans.add(buildClan("C2", "l2"));
        when(clanRepository.getAll()).thenReturn(clans);

        List<Clan> result = clanManager.getAllClans();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    // ---- getClanBankInfo -------------------------------------------------------

    @Test
    public void getClanBankInfo_returnsBalance() {
        Clan clan = buildClan("Upsilon", "leader20");
        clan.getClanBank().put("монеты", 350);
        when(clanRepository.get("Upsilon")).thenReturn(clan);

        String info = clanManager.getClanBankInfo("Upsilon");

        assertTrue(info.contains("350"));
        assertTrue(info.contains("Upsilon"));
    }

    @Test
    public void getClanBankInfo_clanNotFound_returnsError() {
        when(clanRepository.get("NoSuch")).thenReturn(null);

        String info = clanManager.getClanBankInfo("NoSuch");

        assertFalse(info.isEmpty());
        assertTrue(info.contains("не найден"));
    }

    // ---- helpers ---------------------------------------------------------------

    private Clan buildClan(String name, String leaderId) {
        return new Clan(name, leaderId);
    }
}
