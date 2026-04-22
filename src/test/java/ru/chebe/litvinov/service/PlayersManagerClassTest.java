package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.createPlayer(), chooseClass(), and dieCast().
 *
 * All three are completely untested despite having real logic: class selection
 * permanently modifies stats, createPlayer grants an achievement, dieCast
 * has 6 distinct guard paths before delegating to Tavern.
 */
public class PlayersManagerClassTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private LocationManager locationManager;
    @Mock private ItemsManager itemsManager;
    @Mock private BattleManager battleManager;
    @Mock private EventsManager eventsManager;
    @Mock private ClanManager clanManager;
    @Mock private Tavern tavern;
    @Mock private NpcManager npcManager;

    @Mock private MessageReceivedEvent event;
    @Mock private Message message;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private User user;

    private PlayersManager playersManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        playersManager = new PlayersManager(
                playerRepository, locationManager, itemsManager,
                battleManager, eventsManager, clanManager, tavern, npcManager);

        when(event.getChannel()).thenReturn(channel);
        when(event.getMessage()).thenReturn(message);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("p1");
        when(message.getAuthor()).thenReturn(user); // createPlayer uses event.getMessage().getAuthor()
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        doNothing().when(messageAction).queue();
    }

    // ---- createPlayer -------------------------------------------------------

    @Test
    public void createPlayer_newPlayer_playerSavedToCache() {
        when(playerRepository.contains("p1")).thenReturn(false);
        when(user.getName()).thenReturn("TestPlayer");

        playersManager.createPlayer(event);

        verify(playerRepository).put(eq("p1"), argThat(p -> "TestPlayer".equals(p.getNickName())));
    }

    @Test
    public void createPlayer_newPlayer_firstStepsAchievementGranted() {
        when(playerRepository.contains("p1")).thenReturn(false);
        when(user.getName()).thenReturn("TestPlayer");

        playersManager.createPlayer(event);

        verify(playerRepository).put(eq("p1"), argThat(p ->
                p.getAchievements() != null && p.getAchievements().contains("первые_шаги")));
    }

    @Test
    public void createPlayer_alreadyRegistered_sendsAlreadyRegisteredMessage() {
        when(playerRepository.contains("p1")).thenReturn(true);

        playersManager.createPlayer(event);

        verify(channel).sendMessage(contains("уже зарегистрирован"));
        verify(playerRepository, never()).put(any(), any());
    }

    // ---- chooseClass --------------------------------------------------------

    @Test
    public void chooseClass_levelTooLow_sendsLevelRequirementMessage() {
        Player player = player("Hero", "p1", 4, null, 10, 0, 5);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+класс воин");

        playersManager.chooseClass(event);

        verify(channel).sendMessage(contains("5 уровня"));
        assertNull("Class must not be set", player.getPlayerClass());
    }

    @Test
    public void chooseClass_alreadyHasClass_sendsAlreadyChosenMessage() {
        Player player = player("Hero", "p1", 5, "ВОИН", 10, 0, 5);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+класс маг");

        playersManager.chooseClass(event);

        verify(channel).sendMessage(contains("уже выбрали класс"));
        assertEquals("Class must not change", "ВОИН", player.getPlayerClass());
    }

    @Test
    public void chooseClass_warrior_addsStrengthAndArmor() {
        Player player = player("Hero", "p1", 5, null, 10, 2, 5);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+класс воин");

        playersManager.chooseClass(event);

        assertEquals("Warrior: strength +5", 15, player.getStrength());
        assertEquals("Warrior: armor +2", 4, player.getArmor());
        assertEquals("ВОИН", player.getPlayerClass());
    }

    @Test
    public void chooseClass_rogue_addsLuck() {
        Player player = player("Hero", "p1", 5, null, 10, 2, 5);
        player.setLuck(3);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+класс разбойник");

        playersManager.chooseClass(event);

        assertEquals("Rogue: luck +5", 8, player.getLuck());
        assertEquals("РАЗБОЙНИК", player.getPlayerClass());
    }

    @Test
    public void chooseClass_mage_addsMaxHpAndLuck() {
        Player player = player("Hero", "p1", 5, null, 10, 2, 5);
        player.setLuck(1);
        player.setMaxHp(100);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+класс маг");

        playersManager.chooseClass(event);

        assertEquals("Mage: maxHp +30", 130, player.getMaxHp());
        assertEquals("Mage: luck +1", 2, player.getLuck());
        assertEquals("МАГ", player.getPlayerClass());
    }

    @Test
    public void chooseClass_unknownClass_sendsHelpMessageAndNoStatChange() {
        Player player = player("Hero", "p1", 5, null, 10, 2, 5);
        int initialStrength = player.getStrength();
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+класс паладин");

        playersManager.chooseClass(event);

        verify(channel).sendMessage(contains("Доступные классы"));
        assertNull("Unknown class must not be set", player.getPlayerClass());
        assertEquals("Stats must not change", initialStrength, player.getStrength());
    }

    // ---- dieCast ------------------------------------------------------------

    @Test
    public void dieCast_notInTavern_sendsLocationError() {
        Player player = player("Hero", "p1", 1, null, 10, 2, 50);
        player.setLocation("мейн");
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кости 50");

        playersManager.dieCast(event);

        verify(channel).sendMessage(contains("не в таверне"));
        verify(tavern, never()).diceStart(any(), any(), anyInt());
    }

    @Test
    public void dieCast_emptyBid_sendsNoBidMessage() {
        Player player = player("Hero", "p1", 1, null, 10, 2, 50);
        player.setLocation("таверна");
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кости");

        playersManager.dieCast(event);

        verify(channel).sendMessage(contains("ставку"));
        verify(tavern, never()).diceStart(any(), any(), anyInt());
    }

    @Test
    public void dieCast_negativeBid_sendsInvalidBidMessage() {
        Player player = player("Hero", "p1", 1, null, 10, 2, 50);
        player.setLocation("таверна");
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кости -10");

        playersManager.dieCast(event);

        verify(channel).sendMessage(contains("нормально"));
        verify(tavern, never()).diceStart(any(), any(), anyInt());
    }

    @Test
    public void dieCast_bidTooHigh_sendsTooHighMessage() {
        Player player = player("Hero", "p1", 1, null, 10, 2, 500);
        player.setLocation("таверна");
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кости 150");

        playersManager.dieCast(event);

        verify(channel).sendMessage(contains("не больше 100"));
        verify(tavern, never()).diceStart(any(), any(), anyInt());
    }

    @Test
    public void dieCast_notEnoughMoney_sendsInsufficientFundsMessage() {
        Player player = player("Hero", "p1", 1, null, 10, 2, 30);
        player.setLocation("таверна");
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кости 50");

        playersManager.dieCast(event);

        verify(channel).sendMessage(contains("таких денег"));
        verify(tavern, never()).diceStart(any(), any(), anyInt());
    }

    @Test
    public void dieCast_validBid_callsTavernAndSavesPlayer() {
        Player player = player("Hero", "p1", 1, null, 10, 2, 100);
        player.setLocation("таверна");
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кости 50");
        Player updatedPlayer = player("Hero", "p1", 1, null, 10, 2, 150);
        when(tavern.diceStart(any(), any(), eq(50))).thenReturn(updatedPlayer);

        playersManager.dieCast(event);

        verify(tavern).diceStart(any(), eq(player), eq(50));
        // result from tavern must be saved to cache
        verify(playerRepository).put(eq("p1"), eq(updatedPlayer));
    }

    // ---- helpers -----------------------------------------------------------

    private Player player(String nick, String id, int level, String playerClass,
                           int strength, int armor, int money) {
        Player p = new Player(nick, id);
        p.setLevel(level);
        p.setPlayerClass(playerClass);
        p.setStrength(strength);
        p.setArmor(armor);
        p.setMoney(money);
        p.setMaxHp(100);
        p.setHp(100);
        p.setLuck(0);
        p.setLocation("мейн");
        return p;
    }
}
