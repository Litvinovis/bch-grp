package ru.chebe.litvinov.service;

import junit.framework.TestCase;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class PlayersManagerTest extends TestCase {

    @Mock
    private IgniteCache<String, Player> playerCache;

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
        playersManager = new PlayersManager(playerCache, locationManager, itemsManager, battleManager, eventsManager, clanManager, tavern);
    }

    @Test
    public void testChangeHpIncrease() {
        Player player = new Player("TestPlayer", "123");
        player.setHp(50);
        player.setMaxHp(100);
        
        when(playerCache.get("123")).thenReturn(player);
        
        int newHp = playersManager.changeHp("123", 30, true);
        
        assertEquals(80, newHp);
        verify(playerCache).put("123", player);
    }

    @Test
    public void testChangeHpDecrease() {
        Player player = new Player("TestPlayer", "123");
        player.setHp(50);
        
        when(playerCache.get("123")).thenReturn(player);
        
        int newHp = playersManager.changeHp("123", 30, false);
        
        assertEquals(20, newHp);
        verify(playerCache).put("123", player);
    }

    @Test
    public void testChangeHpIncreaseDoesNotExceedMax() {
        Player player = new Player("TestPlayer", "123");
        player.setHp(90);
        player.setMaxHp(100);
        
        when(playerCache.get("123")).thenReturn(player);
        
        int newHp = playersManager.changeHp("123", 30, true);
        
        assertEquals(100, newHp); // Should not exceed maxHp
        verify(playerCache).put("123", player);
    }
}