package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ArenaManagerTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private BattleManager battleManager;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;

    private ArenaManager arenaManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        arenaManager = new ArenaManager(playerRepository, battleManager);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void testArenaChallenge_noOpponents_sendsError() {
        Player player = new Player("Test", "player1");
        player.setArenaRating(1000);
        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.getAll()).thenReturn(List.of(player)); // only self

        arenaManager.arenaChallenge(event);

        verify(channel).sendMessage(contains("Нет доступных соперников"));
    }

    @Test
    public void testArenaChallenge_withOpponent_runsBattle() {
        Player player = new Player("Test", "player1");
        player.setArenaRating(1000);
        player.setHp(100);
        player.setMaxHp(100);

        Player opponent = new Player("Opponent", "player2");
        opponent.setArenaRating(1050);
        opponent.setHp(100);
        opponent.setMaxHp(100);

        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.get("player2")).thenReturn(opponent);
        when(playerRepository.getAll()).thenReturn(List.of(player, opponent));

        arenaManager.arenaChallenge(event);

        verify(battleManager).playerBattle(anyList(), anyList(), eq(channel));
    }

    @Test
    public void testArenaLeaderboard_sendsTopList() {
        Player p1 = new Player("Alpha", "p1");
        p1.setArenaRating(1800);
        Player p2 = new Player("Beta", "p2");
        p2.setArenaRating(1500);
        when(playerRepository.getAll()).thenReturn(new java.util.ArrayList<>(List.of(p1, p2)));

        arenaManager.arenaLeaderboard(event);

        verify(channel).sendMessage(contains("Alpha"));
    }

    @Test
    public void testShowLeague_bronze() {
        Player player = new Player("Test", "player1");
        player.setArenaRating(1000);
        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.getAll()).thenReturn(List.of(player));

        arenaManager.showLeague(event);

        verify(channel).sendMessage(contains("Бронза"));
    }

    @Test
    public void testShowLeague_silver() {
        Player player = new Player("Test", "player1");
        player.setArenaRating(1300);
        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.getAll()).thenReturn(List.of(player));

        arenaManager.showLeague(event);

        verify(channel).sendMessage(contains("Серебро"));
    }

    @Test
    public void testShowLeague_gold() {
        Player player = new Player("Test", "player1");
        player.setArenaRating(1600);
        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.getAll()).thenReturn(List.of(player));

        arenaManager.showLeague(event);

        verify(channel).sendMessage(contains("Золото"));
    }

    @Test
    public void testShowLeague_platinum() {
        Player player = new Player("Test", "player1");
        player.setArenaRating(1900);
        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.getAll()).thenReturn(List.of(player));

        arenaManager.showLeague(event);

        verify(channel).sendMessage(contains("Платина"));
    }

    @Test
    public void testShowChampion_noChampion_sendsNoChampion() {
        // Reset static champion
        try {
            java.lang.reflect.Field f = ArenaManager.class.getDeclaredField("dailyChampionId");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception ignored) {}

        when(playerRepository.getAll()).thenReturn(List.of());

        arenaManager.showChampion(event);

        verify(channel).sendMessage(contains("не назначен"));
    }

    @Test
    public void testChallengeChampion_noChampion_sendsError() {
        try {
            java.lang.reflect.Field f = ArenaManager.class.getDeclaredField("dailyChampionId");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception ignored) {}

        arenaManager.challengeChampion(event);

        verify(channel).sendMessage(contains("не назначен"));
    }

    @Test
    public void testChallengeChampion_selfIsChampion_sendsError() {
        try {
            java.lang.reflect.Field f = ArenaManager.class.getDeclaredField("dailyChampionId");
            f.setAccessible(true);
            f.set(null, "player1");
        } catch (Exception ignored) {}

        arenaManager.challengeChampion(event);

        verify(channel).sendMessage(contains("сам являешься чемпионом"));
    }

    @Test
    public void testTeamArena_notEnoughPlayers_sendsError() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.getAll()).thenReturn(List.of(player));

        arenaManager.teamArenaChallenge(event);

        verify(channel).sendMessage(contains("Недостаточно игроков"));
    }

    @Test
    public void testSurvivalChallenge_sendsComingSoon() {
        arenaManager.survivalChallenge(event);

        verify(channel).sendMessage(contains("Выживание"));
    }
}
