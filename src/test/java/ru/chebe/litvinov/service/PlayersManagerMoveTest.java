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
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.move().
 *
 * move() is a critical integration hub: it calls eventsManager.transferEvent,
 * battleManager.mobBattle, changeXp, changeMoney, and deathOfPlayer.
 * Each branch must fire the correct side effects.
 */
public class PlayersManagerMoveTest {

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

    /** "+идти лес" - target location "лес" */
    private static final String MOVE_CMD = "+идти лес";

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
        when(message.getContentDisplay()).thenReturn(MOVE_CMD);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        doNothing().when(messageAction).queue();

        // Default: movePlayerInPopulation returns a location named after its target argument
        when(locationManager.movePlayerInPopulation(any(), anyString()))
                .thenAnswer(inv -> namedLocation(inv.getArgument(1)));
    }

    // ---- guard checks -------------------------------------------------------

    @Test
    public void move_targetLocationNotFound_sendsErrorAndNoBattle() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("другая")));
        when(locationManager.getLocation("лес")).thenReturn(null);

        playersManager.move(event);

        verify(channel).sendMessage(contains("доступных путей"));
        verify(battleManager, never()).mobBattle(any(), any());
    }

    @Test
    public void move_sameLocation_sendsAlreadyHereMessage() {
        Player player = player("Hero", "p1", "лес", 100, 100, 10, 50);
        when(message.getContentDisplay()).thenReturn("+идти лес");
        when(playerRepository.get("p1")).thenReturn(player);
        Location loc = locationWithPaths("лес", List.of("мейн"));
        when(locationManager.getLocation("лес")).thenReturn(loc);

        playersManager.move(event);

        verify(channel).sendMessage(contains("уже находишься"));
        verify(battleManager, never()).mobBattle(any(), any());
    }

    @Test
    public void move_locationNotInPathsAndNoTeleportToken_sendsErrorMessage() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        // мейн paths do NOT include "лес"
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", new ArrayList<>()));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", new ArrayList<>()));

        playersManager.move(event);

        verify(channel).sendMessage(contains("доступных путей"));
        verify(battleManager, never()).mobBattle(any(), any());
    }

    // ---- successful move, no battle -----------------------------------------

    @Test
    public void move_safePath_playerLocationChanges() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("лес")));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", List.of("мейн")));
        when(eventsManager.transferEvent(any(), any())).thenReturn(false);

        playersManager.move(event);

        assertEquals("лес", player.getLocation());
        verify(battleManager, never()).mobBattle(any(), any());
    }

    // ---- move + mob battle win ----------------------------------------------

    @Test
    public void move_mobBattleWin_awardsXpAndMoney() {
        Player player = player("Hero", "p1", "мейн", 100, 50, 10, 50);
        player.setExp(0);
        player.setExpToNextLvl(1000);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("лес")));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", List.of("мейн")));
        when(eventsManager.transferEvent(any(), any())).thenReturn(true);
        when(battleManager.mobBattle(any(), any())).thenReturn(50);

        playersManager.move(event);

        assertEquals("XP +10 after mob kill", 10, player.getExp());
        assertEquals("Money +10 after mob kill", 60, player.getMoney());
    }

    @Test
    public void move_mobBattleWin_recoversSomeHp() {
        // level 1 player: recovery = 75% of hpLost, min 10
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        player.setLevel(1);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("лес")));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", List.of("мейн")));
        when(eventsManager.transferEvent(any(), any())).thenReturn(true);
        // Mob left player at 50 HP
        when(battleManager.mobBattle(any(), any())).thenReturn(50);

        playersManager.move(event);

        // hpLost=50, recovery=75%, hpToRestore=37, setHp(87)
        assertEquals("HP should be partially restored after win", 87, player.getHp());
    }

    @Test
    public void move_mobBattleWin_huntQuestAttemptIncremented() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        Event huntQuest = Event.builder().type("Охота").attempt(0).build();
        player.setActiveEvent(huntQuest);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("лес")));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", List.of("мейн")));
        when(eventsManager.transferEvent(any(), any())).thenReturn(true);
        when(battleManager.mobBattle(any(), any())).thenReturn(80);

        playersManager.move(event);

        assertEquals("Hunt quest attempt must increment on mob kill", 1, player.getActiveEvent().getAttempt());
    }

    // ---- move + mob battle loss ---------------------------------------------

    @Test
    public void move_mobBattleLoss_moneyReducedByLevelScaledPenalty() {
        // Level 1 player: 5% death penalty → 100 * 0.95 = 95
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 100);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("лес")));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", List.of("мейн")));
        when(eventsManager.transferEvent(any(), any())).thenReturn(true);
        when(battleManager.mobBattle(any(), any())).thenReturn(-1);

        playersManager.move(event);

        assertEquals("5% money penalty for level-1 player on mob death", 95, player.getMoney());
    }

    @Test
    public void move_mobBattleLoss_playerSentToRespawn() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        when(playerRepository.get("p1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(locationWithPaths("мейн", List.of("лес")));
        when(locationManager.getLocation("лес")).thenReturn(locationWithPaths("лес", List.of("мейн")));
        when(eventsManager.transferEvent(any(), any())).thenReturn(true);
        when(battleManager.mobBattle(any(), any())).thenReturn(-1);

        playersManager.move(event);

        assertEquals("Dead player must go to respawn", "респаун", player.getLocation());
        assertEquals("HP must be restored to maxHp on death", 100, player.getHp());
    }

    // ---- helpers -----------------------------------------------------------

    private Player player(String nick, String id, String location,
                           int maxHp, int hp, int strength, int money) {
        Player p = new Player(nick, id);
        p.setLocation(location);
        p.setMaxHp(maxHp);
        p.setHp(hp);
        p.setStrength(strength);
        p.setMoney(money);
        p.setExp(0);
        p.setExpToNextLvl(1000);
        p.setLevel(1);
        return p;
    }

    private Location locationWithPaths(String name, List<String> paths) {
        return Location.builder()
                .name(name).paths(new ArrayList<>(paths)).boss(null).pvp(false)
                .dangerous(0).teleport(false)
                .populationByName(new ArrayList<>()).populationById(new ArrayList<>())
                .build();
    }

    private Location namedLocation(String name) {
        return Location.builder()
                .name(name).paths(new ArrayList<>()).boss(null).pvp(false)
                .dangerous(0).teleport(false)
                .populationByName(new ArrayList<>()).populationById(new ArrayList<>())
                .build();
    }
}
