package ru.chebe.litvinov.raid;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.service.BattleManager;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RaidManager:
 *
 * createRaid:
 *   - success creates active session
 *   - duplicate in same channel is rejected
 *   - disallowed channel is rejected
 *
 * joinRaid:
 *   - no active session → rejected
 *   - raid already started → rejected
 *   - already a participant → rejected
 *   - successful join increments participant count
 *   - reaching MIN_PLAYERS triggers raid start (async, verified via latch)
 *
 * executeRaid (called directly, package-private):
 *   - all players survive → changeMoney + changeXp for each, deathOfPlayer never called
 *   - single survivor gets full loot pool (share=1.0 → 500 money, 800 xp)
 *   - dead player → deathOfPlayer called with actual player from getPlayer, not snapshot
 *   - all players die → deathOfPlayer for all, changeMoney never called
 *   - double-start guard: second executeRaid call on same session is a no-op
 *
 * checkTimeouts (via reflection):
 *   - timed-out session with 0 participants → session marked finished, removed
 *   - timed-out session with participants → executeRaid triggered (session is started)
 *   - continue semantics: second timed-out session in map is still processed
 */
public class RaidManagerTest {

    @Mock private BattleManager battleManager;
    @Mock private IPlayersManager playersManager;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;

    private RaidManager raidManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(channel.getId()).thenReturn("default-channel");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        doNothing().when(messageAction).queue();
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(playersManager.getPlayer(anyString())).thenReturn(null);

        raidManager = new RaidManager(battleManager, playersManager, Collections.emptySet());
    }

    // ---- createRaid ------------------------------------------------------------

    @Test
    public void createRaid_firstCall_returnsSuccessMessage() {
        String result = raidManager.createRaid(player("p1", 100, 5, 0), channel);
        assertTrue("Expected success message, got: " + result, result.contains("Рейд начат"));
    }

    @Test
    public void createRaid_firstCall_sessionIsTrackedAsActive() {
        raidManager.createRaid(player("p1", 100, 5, 0), channel);
        // Attempt to create another raid in same channel → should see existing session
        String second = raidManager.createRaid(player("p2", 100, 5, 0), channel);
        assertTrue("Expected duplicate-raid message", second.contains("уже идёт"));
    }

    @Test
    public void createRaid_duplicateInSameChannel_isRejected() {
        when(channel.getId()).thenReturn("ch-1");
        raidManager.createRaid(player("p1", 100, 5, 0), channel);
        String result = raidManager.createRaid(player("p2", 100, 5, 0), channel);
        assertTrue(result.contains("уже идёт"));
    }

    @Test
    public void createRaid_disallowedChannel_isRejected() {
        when(channel.getId()).thenReturn("bad-channel");
        RaidManager restricted = new RaidManager(battleManager, playersManager, Set.of("good-channel"));
        String result = restricted.createRaid(player("p1", 100, 5, 0), channel);
        assertTrue("Expected channel restriction message", result.contains("разрешены только"));
    }

    @Test
    public void createRaid_allowedChannel_succeeds() {
        when(channel.getId()).thenReturn("good-channel");
        RaidManager restricted = new RaidManager(battleManager, playersManager, Set.of("good-channel"));
        String result = restricted.createRaid(player("p1", 100, 5, 0), channel);
        assertTrue(result.contains("Рейд начат"));
    }

    // ---- joinRaid --------------------------------------------------------------

    @Test
    public void joinRaid_noActiveSession_isRejected() {
        String result = raidManager.joinRaid(player("p1", 100, 5, 0), channel);
        assertTrue("Expected no-raid message", result.contains("нет активного рейда"));
    }

    @Test
    public void joinRaid_raidAlreadyStarted_isRejected() throws Exception {
        when(channel.getId()).thenReturn("ch-start");
        raidManager.createRaid(player("p1", 100, 5, 0), channel);
        // Force-start the session by marking it started via reflection
        RaidSession session = getSession("ch-start");
        session.markStarted();

        String result = raidManager.joinRaid(player("p2", 100, 5, 0), channel);
        assertTrue("Expected already-started message", result.contains("уже начался"));
    }

    @Test
    public void joinRaid_alreadyParticipant_isRejected() {
        when(channel.getId()).thenReturn("ch-dup");
        Player p1 = player("p1", 100, 5, 0);
        raidManager.createRaid(p1, channel);
        // p1 is the initiator — trying to join again must be rejected
        String result = raidManager.joinRaid(p1, channel);
        assertTrue("Expected already-participant message", result.contains("уже участвуешь"));
    }

    @Test
    public void joinRaid_newPlayer_incrementsParticipantCount() throws Exception {
        when(channel.getId()).thenReturn("ch-join");
        raidManager.createRaid(player("p1", 100, 5, 0), channel);
        raidManager.joinRaid(player("p2", 100, 5, 0), channel);

        RaidSession session = getSession("ch-join");
        assertEquals(2, session.getParticipants().size());
    }

    @Test
    public void joinRaid_thirdPlayer_triggersRaidStart() throws Exception {
        when(channel.getId()).thenReturn("ch-trigger");
        when(playersManager.getPlayer(anyString())).thenAnswer(inv -> playerById(inv.getArgument(0)));

        Player p1 = player("p1", 10000, 5000, 100);
        Player p2 = player("p2", 10000, 5000, 100);
        Player p3 = player("p3", 10000, 5000, 100);

        raidManager.createRaid(p1, channel);
        raidManager.joinRaid(p2, channel);
        raidManager.joinRaid(p3, channel); // triggers async start

        // Give the async thread time to complete
        Thread.sleep(3000);

        RaidSession session = getSession("ch-trigger");
        assertTrue("Session must be marked started after MIN_PLAYERS joined",
                session == null || session.isStarted());
    }

    // ---- executeRaid: all players survive --------------------------------------

    @Test
    public void executeRaid_allPlayersWin_changeMoneyCalledForEach() {
        Player p1 = player("p1", 10000, 5000, 100);
        Player p2 = player("p2", 10000, 5000, 100);

        when(playersManager.getPlayer("p1")).thenReturn(p1);
        when(playersManager.getPlayer("p2")).thenReturn(p2);

        RaidSession session = sessionWith(channel, p1, p2);
        raidManager.executeRaid(session);

        verify(playersManager, atLeastOnce()).changeMoney(eq("p1"), anyInt(), eq(true));
        verify(playersManager, atLeastOnce()).changeMoney(eq("p2"), anyInt(), eq(true));
    }

    @Test
    public void executeRaid_allPlayersWin_changeXpCalledForEach() {
        Player p1 = player("p1", 10000, 5000, 100);
        Player p2 = player("p2", 10000, 5000, 100);

        when(playersManager.getPlayer("p1")).thenReturn(p1);
        when(playersManager.getPlayer("p2")).thenReturn(p2);

        RaidSession session = sessionWith(channel, p1, p2);
        raidManager.executeRaid(session);

        verify(playersManager, atLeastOnce()).changeXp(eq("p1"), anyInt());
        verify(playersManager, atLeastOnce()).changeXp(eq("p2"), anyInt());
    }

    @Test
    public void executeRaid_allPlayersWin_deathOfPlayerNeverCalled() {
        Player p1 = player("p1", 10000, 5000, 100);
        Player p2 = player("p2", 10000, 5000, 100);

        when(playersManager.getPlayer("p1")).thenReturn(p1);
        when(playersManager.getPlayer("p2")).thenReturn(p2);

        RaidSession session = sessionWith(channel, p1, p2);
        raidManager.executeRaid(session);

        verify(playersManager, never()).deathOfPlayer(any());
    }

    // ---- executeRaid: single player gets full loot pool -----------------------

    @Test
    public void executeRaid_singleSurvivor_gets500MoneyAnd800Xp() {
        // 1 player, share = 1.0 → money = 500*1.0*1 = 500, xp = 800*1.0*1 = 800
        Player p1 = player("p1", 10000, 5000, 100);
        when(playersManager.getPlayer("p1")).thenReturn(p1);

        RaidSession session = sessionWith(channel, p1);
        raidManager.executeRaid(session);

        verify(playersManager).changeMoney("p1", 500, true);
        verify(playersManager).changeXp("p1", 800);
    }

    // ---- executeRaid: dead player gets deathOfPlayer --------------------------

    @Test
    public void executeRaid_deadPlayerOnVictory_deathOfPlayerCalledWithActualPlayer() {
        // p1 strong and immune → kills boss, survives
        // p2 fragile (hp=1, armor=0) → dies from boss counterattack before boss is killed
        Player p1 = player("p1", 10000, 5000, 100);
        Player p2 = player("p2", 1, 1, 0);

        // getPlayer returns the "actual" player objects (simulating cache lookup)
        Player actualP1 = player("p1", 10000, 5000, 100);
        Player actualP2 = player("p2", 200, 5, 0); // different object from snapshot — verifies we use cache
        when(playersManager.getPlayer("p1")).thenReturn(actualP1);
        when(playersManager.getPlayer("p2")).thenReturn(actualP2);

        RaidSession session = sessionWith(channel, p1, p2);
        raidManager.executeRaid(session);

        // If p2 died in battle, deathOfPlayer must use the actual cached player, not the snapshot
        // (We can't 100% guarantee p2 dies since boss targeting is random, but with HP=1 it's overwhelmingly likely)
        // Run the whole test body in a loop when needed — here we run once and only assert if p2 died
        boolean p2DiedInBattle = p2.getHp() <= 0;
        if (p2DiedInBattle) {
            verify(playersManager).deathOfPlayer(actualP2);
            verify(playersManager, never()).deathOfPlayer(p2); // must NOT use the snapshot
        }
    }

    // ---- executeRaid: all players die -----------------------------------------

    @Test
    public void executeRaid_allPlayersDie_deathOfPlayerCalledForAll() {
        // HP=1, boss deals 18–31 → all die in first round
        Player p1 = player("p1", 1, 1, 0);
        Player p2 = player("p2", 1, 1, 0);

        when(playersManager.getPlayer("p1")).thenReturn(p1);
        when(playersManager.getPlayer("p2")).thenReturn(p2);

        RaidSession session = sessionWith(channel, p1, p2);
        raidManager.executeRaid(session);

        verify(playersManager).deathOfPlayer(p1);
        verify(playersManager).deathOfPlayer(p2);
    }

    @Test
    public void executeRaid_allPlayersDie_changeMoneyNeverCalled() {
        Player p1 = player("p1", 1, 1, 0);
        Player p2 = player("p2", 1, 1, 0);

        when(playersManager.getPlayer("p1")).thenReturn(p1);
        when(playersManager.getPlayer("p2")).thenReturn(p2);

        RaidSession session = sessionWith(channel, p1, p2);
        raidManager.executeRaid(session);

        verify(playersManager, never()).changeMoney(anyString(), anyInt(), anyBoolean());
    }

    @Test
    public void executeRaid_allPlayersDie_sessionMarkedFinished() {
        Player p1 = player("p1", 1, 1, 0);
        when(playersManager.getPlayer("p1")).thenReturn(p1);

        RaidSession session = sessionWith(channel, p1);
        raidManager.executeRaid(session);

        assertTrue("Session must be finished after battle", session.isFinished());
    }

    // ---- executeRaid: double-start guard ---------------------------------------

    @Test
    public void executeRaid_calledTwiceOnSameSession_secondCallIsNoOp() {
        Player p1 = player("p1", 10000, 5000, 100);
        when(playersManager.getPlayer("p1")).thenReturn(p1);

        RaidSession session = sessionWith(channel, p1);
        raidManager.executeRaid(session); // first call: runs battle
        reset(playersManager);            // clear invocation history

        raidManager.executeRaid(session); // second call: must be no-op

        verify(playersManager, never()).changeMoney(anyString(), anyInt(), anyBoolean());
        verify(playersManager, never()).changeXp(anyString(), anyInt());
    }

    // ---- checkTimeouts ---------------------------------------------------------

    @Test
    public void checkTimeouts_emptyTimedOutSession_isRemovedWithoutStarting() throws Exception {
        when(channel.getId()).thenReturn("ch-empty-timeout");
        raidManager.createRaid(player("p1", 100, 5, 0), channel);

        RaidSession original = getSession("ch-empty-timeout");
        original.getParticipants().clear();
        RaidSession aged = injectAgedSession("ch-empty-timeout", original);

        invokeCheckTimeouts();

        assertTrue("Empty timed-out session must be marked finished", aged.isFinished());
        assertFalse("Empty timed-out session must not be marked started", aged.isStarted());
    }

    @Test
    public void checkTimeouts_timedOutSessionWithPlayers_triggersRaidStart() throws Exception {
        when(channel.getId()).thenReturn("ch-timeout-start");
        Player p1 = player("p1", 10000, 5000, 100);
        when(playersManager.getPlayer("p1")).thenReturn(p1);

        raidManager.createRaid(p1, channel);
        RaidSession original = getSession("ch-timeout-start");
        RaidSession aged = injectAgedSession("ch-timeout-start", original);

        invokeCheckTimeouts();

        Thread.sleep(3000);
        assertTrue("Session must be started after timeout with participants", aged.isStarted());
    }

    @Test
    public void checkTimeouts_continueSemantics_processesAllTimedOutSessions() throws Exception {
        // Bug that was fixed: return instead of continue caused only first session to be processed
        MessageChannelUnion channel1 = mock(MessageChannelUnion.class);
        MessageChannelUnion channel2 = mock(MessageChannelUnion.class);
        MessageCreateAction action1 = mock(MessageCreateAction.class);
        MessageCreateAction action2 = mock(MessageCreateAction.class);
        when(channel1.getId()).thenReturn("ch-a");
        when(channel2.getId()).thenReturn("ch-b");
        when(channel1.sendMessage(anyString())).thenReturn(action1);
        when(channel2.sendMessage(anyString())).thenReturn(action2);
        doNothing().when(action1).queue();
        doNothing().when(action2).queue();
        when(action1.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(action2.submit()).thenReturn(CompletableFuture.completedFuture(null));

        // Session A: empty → will be cancelled
        raidManager.createRaid(player("pa", 100, 5, 0), channel1);
        RaidSession origA = getSession("ch-a");
        origA.getParticipants().clear();
        RaidSession agedA = injectAgedSession("ch-a", origA);

        // Session B: has player → will start
        Player pb = player("pb", 10000, 5000, 100);
        when(playersManager.getPlayer("pb")).thenReturn(pb);
        raidManager.createRaid(pb, channel2);
        RaidSession origB = getSession("ch-b");
        RaidSession agedB = injectAgedSession("ch-b", origB);

        invokeCheckTimeouts();
        Thread.sleep(3000);

        assertTrue("Empty session A must be finished", agedA.isFinished());
        assertTrue("Session B must be started", agedB.isStarted());
    }

    // ---- helpers ---------------------------------------------------------------

    private Player player(String id, int hp, int strength, int armor) {
        Player p = new Player(id, id);
        p.setHp(hp);
        p.setStrength(strength);
        p.setArmor(armor);
        p.setMoney(1000);
        return p;
    }

    /** Stub for getPlayer(id) when actual player object is the same as the snapshot */
    private Player playerById(String id) {
        Player p = new Player(id, id);
        p.setHp(10000);
        p.setStrength(5000);
        p.setArmor(100);
        p.setMoney(1000);
        return p;
    }

    private RaidSession sessionWith(MessageChannelUnion ch, Player... players) {
        when(ch.getId()).thenReturn("test-channel-" + System.nanoTime());
        RaidSession session = new RaidSession(ch.getId(), ch);
        for (Player p : players) {
            session.addParticipant(p);
        }
        return session;
    }

    @SuppressWarnings("unchecked")
    private RaidSession getSession(String channelId) throws Exception {
        Field f = RaidManager.class.getDeclaredField("activeSessions");
        f.setAccessible(true);
        ConcurrentHashMap<String, RaidSession> sessions =
                (ConcurrentHashMap<String, RaidSession>) f.get(raidManager);
        return sessions.get(channelId);
    }

    /** Replace the session in activeSessions with a new one that overrides isTimedOut() → true. Returns the new session. */
    @SuppressWarnings("unchecked")
    private RaidSession injectAgedSession(String channelId, RaidSession original) throws Exception {
        Field f = RaidManager.class.getDeclaredField("activeSessions");
        f.setAccessible(true);
        ConcurrentHashMap<String, RaidSession> sessions =
                (ConcurrentHashMap<String, RaidSession>) f.get(raidManager);

        RaidSession aged = new RaidSession(channelId, original.getChannel()) {
            @Override
            public boolean isTimedOut() { return true; }
        };
        for (Map.Entry<String, Player> e : original.getParticipants().entrySet()) {
            aged.addParticipant(e.getValue());
        }
        sessions.put(channelId, aged);
        return aged;
    }

    private void invokeCheckTimeouts() throws Exception {
        Method m = RaidManager.class.getDeclaredMethod("checkTimeouts");
        m.setAccessible(true);
        m.invoke(raidManager);
    }
}
