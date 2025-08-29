package ru.chebe.litvinov.service;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.Player;

import java.util.Random;

import static org.mockito.Mockito.*;

public class TavernTest extends TestCase {

    private Tavern tavern;
    
    // Custom Random subclass for testing
    private static class TestRandom extends Random {
        private int nextIntValue;
        
        public TestRandom(int nextIntValue) {
            this.nextIntValue = nextIntValue;
        }
        
        @Override
        public int nextInt(int bound) {
            return nextIntValue;
        }
    }

    @Before
    public void setUp() {
        tavern = new Tavern();
    }

    @Test
    public void testGuessTheNumberWin() {
        // Create a tavern with a predictable random generator
        Tavern testTavern = new Tavern(new TestRandom(4)); // Will generate 5 (4+1)
        
        Player player = new Player("TestPlayer", "123");
        player.setMoney(100);
        
        // Test with a guess that matches the predictable random number (5)
        Player result = testTavern.guessTheNumber(null, player, 10, 5);
        
        // Player should win: 100 (initial) - 10 (bet) + 50 (win) = 140
        assertEquals(140, result.getMoney());
    }

    @Test
    public void testGuessTheNumberLoss() {
        // Create a tavern with a predictable random generator
        Tavern testTavern = new Tavern(new TestRandom(2)); // Will generate 3 (2+1)
        
        Player player = new Player("TestPlayer", "123");
        player.setMoney(100);
        
        // Test with a guess that doesn't match the predictable random number (5)
        Player result = testTavern.guessTheNumber(null, player, 10, 5);
        
        // Player should lose: 100 (initial) - 10 (bet) = 90
        assertEquals(90, result.getMoney());
    }

    @Test
    public void testGuessTheNumberWithInsufficientFunds() {
        Player player = new Player("TestPlayer", "123");
        player.setMoney(5); // Not enough for a 10 bid
        
        // Test with insufficient funds
        Player result = tavern.guessTheNumber(null, player, 10, 5);
        
        // Player should still have only 5 money (no change)
        assertEquals(5, result.getMoney());
    }
}