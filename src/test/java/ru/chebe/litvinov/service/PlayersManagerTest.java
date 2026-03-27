package ru.chebe.litvinov.service;

import junit.framework.TestCase;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class PlayersManagerTest extends TestCase {

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
        playersManager = new PlayersManager(playerRepository, locationManager, itemsManager, battleManager, eventsManager, clanManager, tavern);
    }

    @Test
    public void testChangeHpIncrease() {
        Player player = new Player("TestPlayer", "123");
        player.setHp(50);
        player.setMaxHp(100);

        when(playerRepository.get("123")).thenReturn(player);

        int newHp = playersManager.changeHp("123", 30, true);

        assertEquals(80, newHp);
        verify(playerRepository).put("123", player);
    }

    @Test
    public void testChangeHpDecrease() {
        Player player = new Player("TestPlayer", "123");
        player.setHp(50);

        when(playerRepository.get("123")).thenReturn(player);

        int newHp = playersManager.changeHp("123", 30, false);

        assertEquals(20, newHp);
        verify(playerRepository).put("123", player);
    }

    @Test
    public void testChangeHpIncreaseDoesNotExceedMax() {
        Player player = new Player("TestPlayer", "123");
        player.setHp(90);
        player.setMaxHp(100);

        when(playerRepository.get("123")).thenReturn(player);

        int newHp = playersManager.changeHp("123", 30, true);

        assertEquals(100, newHp); // Should not exceed maxHp
        verify(playerRepository).put("123", player);
    }

    @Test
    public void testChangeHpWithNullPlayer_doesNotThrow() {
        when(playerRepository.get("unknown")).thenReturn(null);
        // Must not throw NPE — null player guard added as part of bug fix
        int result = playersManager.changeHp("unknown", 10, true);
        assertEquals(0, result);
        verify(playerRepository, never()).put(anyString(), any());
    }

    @Test
    public void testChangeLuckIncrease_modifiesLuckNotReputation() {
        Player player = new Player("TestPlayer", "123");
        player.setLuck(5);
        player.setReputation(0);

        when(playerRepository.get("123")).thenReturn(player);

        int newLuck = playersManager.changeLuck("123", 3, true);

        assertEquals(8, newLuck);
        assertEquals(0, player.getReputation()); // reputation must be unchanged
        verify(playerRepository).put("123", player);
    }

    @Test
    public void testChangeStrengthIncrease_modifiesStrengthNotReputation() {
        Player player = new Player("TestPlayer", "123");
        player.setStrength(5);
        player.setReputation(0);

        when(playerRepository.get("123")).thenReturn(player);

        int newStrength = playersManager.changeStrength("123", 4, true);

        assertEquals(9, newStrength);
        assertEquals(0, player.getReputation()); // reputation must be unchanged
        verify(playerRepository).put("123", player);
    }

    @Test
    public void testChangeMoneyWithNullPlayer_doesNotThrow() {
        when(playerRepository.get("ghost")).thenReturn(null);
        int result = playersManager.changeMoney("ghost", 50, true);
        assertEquals(0, result);
        verify(playerRepository, never()).put(anyString(), any());
    }
}
