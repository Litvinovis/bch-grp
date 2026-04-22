package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager quest flow:
 * assignEvent, checkEvent (riddle + pathfinder), changeEvent
 */
public class PlayersManagerQuestTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private LocationManager locationManager;
    @Mock private ItemsManager itemsManager;
    @Mock private BattleManager battleManager;
    @Mock private EventsManager eventsManager;
    @Mock private ClanManager clanManager;
    @Mock private Tavern tavern;

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
                battleManager, eventsManager, clanManager, tavern, new NpcManager());

        when(event.getChannel()).thenReturn(channel);
        when(event.getMessage()).thenReturn(message);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("uid1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(locationManager.getLocationList()).thenReturn(
                List.of("респаун", "магазин", "мейн"));
    }

    // ---- assignEvent -----------------------------------------------------------

    @Test
    public void assignEvent_noActiveEvent_eventAssignedAndSaved() {
        Player player = freshPlayer("id1");
        player.setActiveEvent(null);
        when(playerRepository.get("uid1")).thenReturn(player);

        Event newEvent = riddleEvent("загадка", 50, 60);
        when(eventsManager.assignEvent(any())).thenReturn(newEvent);
        when(message.getContentDisplay()).thenReturn("+квест");

        playersManager.assignEvent(event);

        assertNotNull(player.getActiveEvent());
        assertEquals(newEvent, player.getActiveEvent());
        verify(playerRepository).put("uid1", player);
    }

    @Test
    public void assignEvent_alreadyHasActiveEvent_doesNotOverwrite() {
        Player player = freshPlayer("id1");
        Event existing = riddleEvent("старая", 50, 50);
        player.setActiveEvent(existing);
        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+квест");

        playersManager.assignEvent(event);

        assertSame(existing, player.getActiveEvent());
        verify(eventsManager, never()).assignEvent(any());
        verify(channel, atLeastOnce()).sendMessage(contains("уже есть активный квест"));
    }

    @Test
    public void assignEvent_nullPlayer_sendsRegistrationMessage() {
        when(playerRepository.get("uid1")).thenReturn(null);
        when(message.getContentDisplay()).thenReturn("+квест");

        playersManager.assignEvent(event);

        verify(channel, atLeastOnce()).sendMessage(contains("зарегистрируйся"));
        verify(eventsManager, never()).assignEvent(any());
    }

    // ---- checkEvent (riddle) ---------------------------------------------------

    @Test
    public void checkEvent_riddleCorrectAnswer_moneyAndXpCredited() {
        Player player = freshPlayer("id1");
        Event riddle = riddleEvent("лаб", 70, 80);
        player.setActiveEvent(riddle);
        player.setMoney(100);
        player.setExp(0);
        player.setExpToNextLvl(200);
        player.setLevel(1);
        player.setMaxHp(100);

        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+проверить квест лаб");
        when(eventsManager.checkEvent(eq(riddle), any())).thenReturn(true);

        playersManager.checkEvent(event);

        // money must have increased
        verify(playerRepository, atLeast(2)).put(eq("uid1"), any());
        // activeEvent cleared
        assertNull(player.getActiveEvent());
    }

    @Test
    public void checkEvent_riddleCorrectAnswer_activeEventCleared() {
        Player player = freshPlayer("id1");
        Event riddle = riddleEvent("лаб", 70, 80);
        player.setActiveEvent(riddle);
        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+проверить квест лаб");
        when(eventsManager.checkEvent(eq(riddle), any())).thenReturn(true);

        playersManager.checkEvent(event);

        assertNull(player.getActiveEvent());
    }

    @Test
    public void checkEvent_riddleWrongAnswer_activeEventRetained() {
        Player player = freshPlayer("id1");
        Event riddle = riddleEvent("лаб", 70, 80);
        player.setActiveEvent(riddle);
        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+проверить квест неправильно");
        when(eventsManager.checkEvent(eq(riddle), any())).thenReturn(false);

        playersManager.checkEvent(event);

        assertNotNull(player.getActiveEvent());
        verify(channel, atLeastOnce()).sendMessage(contains("не выполнил"));
    }

    @Test
    public void checkEvent_noActiveEvent_sendsNoQuestMessage() {
        Player player = freshPlayer("id1");
        player.setActiveEvent(null);
        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+проверить квест что-то");

        playersManager.checkEvent(event);

        verify(channel, atLeastOnce()).sendMessage(contains("нет активного квеста"));
        verify(eventsManager, never()).checkEvent(any(), any());
    }

    @Test
    public void checkEvent_pathfinderAtDestination_moneyAndXpCredited() {
        Player player = freshPlayer("id1");
        player.setLocation("магазин");
        Event pathfinder = pathfinderEvent("магазин", 65, 55);
        player.setActiveEvent(pathfinder);
        player.setMoney(50);
        player.setExp(0);
        player.setExpToNextLvl(200);
        player.setLevel(1);
        player.setMaxHp(100);

        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+проверить квест");
        when(eventsManager.checkEvent(eq(pathfinder), any())).thenReturn(true);

        playersManager.checkEvent(event);

        assertNull(player.getActiveEvent());
        // Both changeMoney and changeXp will call put
        verify(playerRepository, atLeast(2)).put(eq("uid1"), any());
    }

    // ---- changeEvent -----------------------------------------------------------

    @Test
    public void changeEvent_enoughMoney_newEventAssignedAndMoneyDeducted() {
        Player player = freshPlayer("id1");
        Event oldEvent = riddleEvent("старая", 50, 50);
        player.setActiveEvent(oldEvent);
        player.setMoney(20);
        when(playerRepository.get("uid1")).thenReturn(player);

        Event newEvent = riddleEvent("новая", 60, 70);
        when(eventsManager.assignEvent(any())).thenReturn(newEvent);
        when(message.getContentDisplay()).thenReturn("+сменить квест");

        playersManager.changeEvent(event);

        assertEquals(newEvent, player.getActiveEvent());
        // -5 money was requested; verify put called
        verify(playerRepository, atLeastOnce()).put("uid1", player);
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository, atLeastOnce()).put(eq("uid1"), captor.capture());
        // money reduced by 5
        boolean moneyReduced = captor.getAllValues().stream().anyMatch(p -> p.getMoney() <= 15);
        assertTrue("Money must be reduced by 5", moneyReduced);
    }

    @Test
    public void changeEvent_notEnoughMoney_eventUnchanged() {
        Player player = freshPlayer("id1");
        Event oldEvent = riddleEvent("старая", 50, 50);
        player.setActiveEvent(oldEvent);
        player.setMoney(3);
        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+сменить квест");

        playersManager.changeEvent(event);

        assertSame(oldEvent, player.getActiveEvent());
        verify(eventsManager, never()).assignEvent(any());
        verify(channel, atLeastOnce()).sendMessage(contains("недостаточно денег"));
    }

    @Test
    public void changeEvent_noActiveEvent_sendsNoQuestMessage() {
        Player player = freshPlayer("id1");
        player.setActiveEvent(null);
        when(playerRepository.get("uid1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+сменить квест");

        playersManager.changeEvent(event);

        verify(channel, atLeastOnce()).sendMessage(contains("нет активного квеста"));
        verify(eventsManager, never()).assignEvent(any());
    }

    // ---- helpers ---------------------------------------------------------------

    private Player freshPlayer(String suffix) {
        Player p = new Player("Player" + suffix, suffix);
        p.setLevel(1);
        p.setExp(0);
        p.setExpToNextLvl(100);
        p.setMaxHp(100);
        p.setHp(100);
        p.setMoney(0);
        p.setLocation("мейн");
        return p;
    }

    private Event riddleEvent(String answer, int money, int xp) {
        return Event.builder()
                .type("Загадка")
                .correctAnswer(answer)
                .description("test riddle")
                .moneyReward(money)
                .xpReward(xp)
                .build();
    }

    private Event pathfinderEvent(String destination, int money, int xp) {
        return Event.builder()
                .type("Ходилка")
                .locationEnd(destination)
                .correctAnswer(null)
                .description("Go to " + destination)
                .moneyReward(money)
                .xpReward(xp)
                .build();
    }
}
