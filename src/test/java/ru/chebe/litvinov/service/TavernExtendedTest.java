package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Extended tests for Tavern game logic: roulette, rock-paper-scissors,
 * guess-the-number, and edge-cases (insufficient funds, boundary bets).
 *
 * All methods that take a MessageReceivedEvent require it to be a mock
 * because they unconditionally call event.getChannel().sendMessage().
 */
public class TavernExtendedTest {

    @Mock
    private MessageReceivedEvent event;
    @Mock
    private MessageChannelUnion channel;
    @Mock
    private MessageCreateAction messageAction;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(event.getChannel()).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        // queue() is void — Mockito stubs it as doNothing by default
    }

    // Fixed Random that cycles through the given sequence
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
            return v;
        }
    }

    // ---- playRoulette ----------------------------------------------------------

    @Test
    public void playRoulette_insufficientFunds_moneyUnchanged() {
        Tavern tavern = new Tavern(new FixedRandom(15));
        Player player = new Player("Player", "p1");
        player.setMoney(5);

        Player result = tavern.playRoulette(event, player, 50, "красный");

        assertEquals(5, result.getMoney());
    }

    @Test
    public void playRoulette_winOnEvenNumber_doublesTheBid() {
        // Win number = 4 → even → "красный"
        Tavern tavern = new Tavern(new FixedRandom(4));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.playRoulette(event, player, 20, "красный");

        // Win: payout = 2 → +20*2 = 40 → 100+40 = 140
        assertEquals(140, result.getMoney());
    }

    @Test
    public void playRoulette_winOnOddNumber_getsBlack() {
        // Win number = 3 → odd → "черный"
        Tavern tavern = new Tavern(new FixedRandom(3));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.playRoulette(event, player, 10, "черный");

        // Win on color payout = 2 → +10*2 = 20 → 100+20 = 120
        assertEquals(120, result.getMoney());
    }

    @Test
    public void playRoulette_winOnExactNumber_paysThirtyFiveX() {
        // Win number = 7, bet = "7"
        Tavern tavern = new Tavern(new FixedRandom(7));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.playRoulette(event, player, 10, "7");

        // Win: payout = 35 → 100 + 10*35 = 450
        assertEquals(450, result.getMoney());
    }

    @Test
    public void playRoulette_zero_isGreen_loseOnRed() {
        // Win number = 0 → "зеленый"
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.playRoulette(event, player, 20, "красный");

        // Player bet red but got green → loss
        assertEquals(80, result.getMoney());
    }

    @Test
    public void playRoulette_wrongNumberBet_losesTheBid() {
        // Win number = 10, player bets "5"
        Tavern tavern = new Tavern(new FixedRandom(10));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.playRoulette(event, player, 15, "5");

        assertEquals(85, result.getMoney());
    }

    // ---- rockPaperScissors -----------------------------------------------------

    @Test
    public void rps_playerWins_earnsBid() {
        // AI choice index=0 → "камень"; player plays "бумага" → win
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.rockPaperScissors(event, player, 25, "бумага");

        assertEquals(125, result.getMoney());
    }

    @Test
    public void rps_playerLoses_losesBid() {
        // AI choice index=0 → "камень"; player plays "ножницы" → lose
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.rockPaperScissors(event, player, 25, "ножницы");

        assertEquals(75, result.getMoney());
    }

    @Test
    public void rps_tie_moneyUnchanged() {
        // AI choice index=0 → "камень"; player plays "камень" → tie
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.rockPaperScissors(event, player, 25, "камень");

        assertEquals(100, result.getMoney());
    }

    @Test
    public void rps_insufficientFunds_moneyUnchanged() {
        Tavern tavern = new Tavern(new FixedRandom(1));
        Player player = new Player("Player", "p1");
        player.setMoney(10);

        Player result = tavern.rockPaperScissors(event, player, 100, "камень");

        assertEquals(10, result.getMoney());
    }

    @Test
    public void rps_scissorsBeatsRock_isCorrectWinRule() {
        // AI index=2 → "бумага"; player plays "ножницы" → win
        Tavern tavern = new Tavern(new FixedRandom(2));
        Player player = new Player("Player", "p1");
        player.setMoney(50);

        Player result = tavern.rockPaperScissors(event, player, 10, "ножницы");

        assertEquals(60, result.getMoney());
    }

    @Test
    public void rps_rockBeatsScissors_isCorrectWinRule() {
        // AI index=1 → "ножницы"; player plays "камень" → win
        Tavern tavern = new Tavern(new FixedRandom(1));
        Player player = new Player("Player", "p1");
        player.setMoney(50);

        Player result = tavern.rockPaperScissors(event, player, 10, "камень");

        assertEquals(60, result.getMoney());
    }

    // ---- guessTheNumber --------------------------------------------------------

    @Test
    public void guessTheNumber_correctGuess_fiveXPayout() {
        // Secret = 0+1 = 1; player guesses 1
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.guessTheNumber(null, player, 10, 1);

        // 100 - 10 (bet) + 10*5 (win) = 140
        assertEquals(140, result.getMoney());
    }

    @Test
    public void guessTheNumber_wrongGuess_losesBid() {
        // Secret = 3+1 = 4; player guesses 7
        Tavern tavern = new Tavern(new FixedRandom(3));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        Player result = tavern.guessTheNumber(null, player, 10, 7);

        assertEquals(90, result.getMoney());
    }

    @Test
    public void guessTheNumber_insufficientFunds_noChange() {
        Tavern tavern = new Tavern(new FixedRandom(4));
        Player player = new Player("Player", "p1");
        player.setMoney(5);

        Player result = tavern.guessTheNumber(null, player, 20, 5);

        assertEquals(5, result.getMoney());
    }

    @Test
    public void guessTheNumber_exactlyEnoughFunds_playsGame() {
        // Secret = 0+1 = 1; player guesses wrong (5) → loses exactly 10
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(10);

        Player result = tavern.guessTheNumber(null, player, 10, 5);

        assertEquals(0, result.getMoney());
    }

    @Test
    public void guessTheNumber_bidZero_correctGuess_onlyWinsZero() {
        // Secret = 0+1 = 1; player guesses correctly but bid=0 → no gain
        Tavern tavern = new Tavern(new FixedRandom(0));
        Player player = new Player("Player", "p1");
        player.setMoney(100);

        // 100 - 0 (bet) + 0*5 (win) = 100
        Player result = tavern.guessTheNumber(null, player, 0, 1);

        assertEquals(100, result.getMoney());
    }
}
