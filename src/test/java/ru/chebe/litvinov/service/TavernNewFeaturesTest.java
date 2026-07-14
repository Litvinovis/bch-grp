package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for new Tavern features: jackpot accumulation, tryClaimJackpot, playPoker,
 * horse racing info and run, horse constants.
 */
public class TavernNewFeaturesTest {

    @Mock
    private MessageReceivedEvent event;
    @Mock
    private MessageChannelUnion channel;
    @Mock
    private MessageCreateAction messageAction;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(event.getChannel()).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));

        // Reset static jackpot state between tests to avoid interference
        resetJackpotState(0, 0L);
    }

    // ---- Jackpot accumulation --------------------------------------------------

    @Test
    public void playRoulette_losingBet_jackpotIncreasedByFivePercent() throws Exception {
        // Number = 1 → "черный"; bet = "красный" → loss
        Tavern tavern = new Tavern(new FixedRandom(1));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        int initialJackpot = Tavern.getJackpot();
        tavern.playRoulette(event, player, 20, "красный"); // loss

        int expectedContrib = Math.max(1, 20 * 5 / 100); // = 1
        assertEquals(initialJackpot + expectedContrib, Tavern.getJackpot());
    }

    @Test
    public void playRoulette_winningBet_jackpotStillAccumulates() throws Exception {
        // Number = 4 → "красный" → win
        Tavern tavern = new Tavern(new FixedRandom(4));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        int initialJackpot = Tavern.getJackpot();
        tavern.playRoulette(event, player, 20, "красный"); // win

        int expectedContrib = Math.max(1, 20 * 5 / 100); // = 1
        assertEquals(initialJackpot + expectedContrib, Tavern.getJackpot());
    }

    @Test
    public void playRoulette_largeBid_jackpotContributionIsCorrect() {
        // bid=200 → 5% = 10
        Tavern tavern = new Tavern(new FixedRandom(1)); // loss
        Player player = new Player("Player", "p1");
        player.setMoney(1000);

        int initialJackpot = Tavern.getJackpot();
        tavern.playRoulette(event, player, 200, "красный");

        int expectedContrib = Math.max(1, 200 * 5 / 100); // = 10
        assertEquals(initialJackpot + expectedContrib, Tavern.getJackpot());
    }

    // ---- tryClaimJackpot -------------------------------------------------------

    @Test
    public void tryClaimJackpot_afterIntervalElapsed_returnsJackpotAndResetsToZero() throws Exception {
        // Set jackpot to 500, lastJackpotTime to 8 days ago
        int jackpotAmount = 500;
        long eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000);
        resetJackpotState(jackpotAmount, eightDaysAgo);

        Tavern tavern = new Tavern();
        int claimed = tavern.tryClaimJackpot("player1");

        assertEquals(jackpotAmount, claimed);
        assertEquals(0, Tavern.getJackpot());
    }

    @Test
    public void tryClaimJackpot_beforeIntervalElapsed_returnsZero() throws Exception {
        // Set jackpot to 500, lastJackpotTime to just now (0 days ago)
        long justNow = System.currentTimeMillis() - 1000; // 1 second ago
        resetJackpotState(500, justNow);

        Tavern tavern = new Tavern();
        int claimed = tavern.tryClaimJackpot("player1");

        assertEquals(0, claimed);
        assertEquals(500, Tavern.getJackpot()); // unchanged
    }

    @Test
    public void tryClaimJackpot_jackpotIsZero_returnsZeroEvenAfterInterval() throws Exception {
        long eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000);
        resetJackpotState(0, eightDaysAgo);

        Tavern tavern = new Tavern();
        int claimed = tavern.tryClaimJackpot("player1");

        assertEquals(0, claimed);
    }

    // ---- playPoker -------------------------------------------------------------

    @Test
    public void playPoker_challengerWins_gainsBetAndOpponentLoses() {
        // FixedRandom: first call → handChallenger=9 (Роял флеш), second → handOpponent=0 (Старшая карта)
        Tavern tavern = new Tavern(new FixedRandom(9, 0));

        Player challenger = new Player("Challenger", "c1");
        challenger.setMoney(100);
        Player opponent = new Player("Opponent", "o1");
        opponent.setMoney(100);

        Player result = tavern.playPoker(event, challenger, opponent, 30);

        assertEquals(130, result.getMoney());      // challenger wins
        assertEquals(70, opponent.getMoney());     // opponent loses
    }

    @Test
    public void playPoker_opponentWins_challengerLosesBet() {
        // first call → handChallenger=0, second → handOpponent=9
        Tavern tavern = new Tavern(new FixedRandom(0, 9));

        Player challenger = new Player("Challenger", "c2");
        challenger.setMoney(100);
        Player opponent = new Player("Opponent", "o2");
        opponent.setMoney(100);

        Player result = tavern.playPoker(event, challenger, opponent, 30);

        assertEquals(70, result.getMoney());       // challenger loses
        assertEquals(130, opponent.getMoney());    // opponent wins
    }

    @Test
    public void playPoker_tie_noMoneyChange() {
        // both get same value
        Tavern tavern = new Tavern(new FixedRandom(5, 5));

        Player challenger = new Player("Challenger", "c3");
        challenger.setMoney(100);
        Player opponent = new Player("Opponent", "o3");
        opponent.setMoney(100);

        tavern.playPoker(event, challenger, opponent, 30);

        assertEquals(100, challenger.getMoney());
        assertEquals(100, opponent.getMoney());
    }

    @Test
    public void playPoker_challengerInsufficientFunds_returnsEarly() {
        Tavern tavern = new Tavern(new FixedRandom(5, 3));

        Player challenger = new Player("Challenger", "c4");
        challenger.setMoney(5);
        Player opponent = new Player("Opponent", "o4");
        opponent.setMoney(100);

        Player result = tavern.playPoker(event, challenger, opponent, 30);

        assertEquals(5, result.getMoney()); // unchanged
        assertEquals(100, opponent.getMoney()); // unchanged
    }

    @Test
    public void playPoker_opponentInsufficientFunds_returnsEarly() {
        Tavern tavern = new Tavern(new FixedRandom(5, 3));

        Player challenger = new Player("Challenger", "c5");
        challenger.setMoney(100);
        Player opponent = new Player("Opponent", "o5");
        opponent.setMoney(5);

        Player result = tavern.playPoker(event, challenger, opponent, 30);

        assertEquals(100, result.getMoney()); // unchanged
        assertEquals(5, opponent.getMoney()); // unchanged
    }

    // ---- getHorseRacingInfo ----------------------------------------------------

    @Test
    public void getHorseRacingInfo_containsAllHorseNames() {
        Tavern tavern = new Tavern();
        String info = tavern.getHorseRacingInfo();

        assertNotNull(info);
        for (String horse : Tavern.HORSES) {
            assertTrue(info.contains(horse), "Info should contain horse name: " + horse);
        }
    }

    @Test
    public void getHorseRacingInfo_containsAllOdds() {
        Tavern tavern = new Tavern();
        String info = tavern.getHorseRacingInfo();

        for (int odds : Tavern.HORSE_ODDS) {
            assertTrue(info.contains(String.valueOf(odds)), "Info should contain odds: " + odds);
        }
    }

    // ---- runHorseRace ----------------------------------------------------------

    @Test
    public void runHorseRace_returnsValidIndex() {
        Tavern tavern = new Tavern();
        for (int i = 0; i < 50; i++) {
            int idx = tavern.runHorseRace();
            assertTrue(idx >= 0 && idx < Tavern.HORSES.length, "Horse index must be 0-4, got: " + idx);
        }
    }

    @Test
    public void runHorseRace_withFixedRandom_returnsExpectedHorse() {
        // With roll=0, the first horse (idx=0) wins (first bucket starts at cum=0)
        Tavern tavern = new Tavern(new FixedRandom(0));
        int idx = tavern.runHorseRace();
        assertEquals(0, idx);
    }

    // ---- HORSES / HORSE_ODDS constants -----------------------------------------

    @Test
    public void horsesAndOddsArrays_haveSameLength() {
        assertEquals(Tavern.HORSES.length, Tavern.HORSE_ODDS.length);
    }

    @Test
    public void horsesArray_hasFiveEntries() {
        assertEquals(5, Tavern.HORSES.length);
    }

    @Test
    public void horseOdds_hasFiveEntries() {
        assertEquals(5, Tavern.HORSE_ODDS.length);
    }

    @Test
    public void horseNames_allExpectedNamesPresent() {
        java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(Tavern.HORSES));
        assertTrue(names.contains("Буря"));
        assertTrue(names.contains("Молния"));
        assertTrue(names.contains("Гром"));
        assertTrue(names.contains("Ветер"));
        assertTrue(names.contains("Огонь"));
    }

    // ---- helpers ---------------------------------------------------------------

    /** Resets the static jackpot state in Tavern via reflection. */
    private static void resetJackpotState(int jackpotValue, long lastJackpotTimeMs) throws Exception {
        Field jackpotField = Tavern.class.getDeclaredField("jackpot");
        jackpotField.setAccessible(true);
        ((AtomicInteger) jackpotField.get(null)).set(jackpotValue);

        Field timeField = Tavern.class.getDeclaredField("lastJackpotTime");
        timeField.setAccessible(true);
        timeField.set(null, lastJackpotTimeMs);
    }

    /** Fixed Random that cycles through a sequence. */
    private static class FixedRandom extends Random {
        private final int[] values;
        private int idx = 0;

        FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int v = values[idx % values.length];
            idx++;
            // Clamp to valid range
            return Math.min(v, bound - 1);
        }
    }
}
