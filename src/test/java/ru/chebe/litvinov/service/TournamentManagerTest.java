package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.repository.TournamentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TournamentManagerTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private BattleManager battleManager;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;

    private TournamentManager tournamentManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        tournamentManager = new TournamentManager(tournamentRepository, playerRepository, battleManager);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        doNothing().when(messageAction).queue();
    }

    @Test
    public void testRegisterForTournament_noActive_createsTournament() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);
        when(tournamentRepository.getActive("arena")).thenReturn(null);
        when(tournamentRepository.createTournament(eq("arena"), anyList())).thenReturn(1);

        TournamentRepository.Tournament created = new TournamentRepository.Tournament(
            1, "arena", new ArrayList<>(), "{}", "open", 1);
        // After create, return the tournament with empty participants so player can be added
        when(tournamentRepository.getActive("arena"))
            .thenReturn(null)
            .thenReturn(created);

        tournamentManager.registerForTournament(event);

        verify(tournamentRepository).createTournament(eq("arena"), anyList());
        verify(tournamentRepository).addParticipant(eq(1), eq("player1"));
    }

    @Test
    public void testRegisterForTournament_alreadyRegistered_sendsError() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);

        List<String> participants = new ArrayList<>(List.of("player1"));
        TournamentRepository.Tournament active = new TournamentRepository.Tournament(
            1, "arena", participants, "{}", "open", 1);
        when(tournamentRepository.getActive("arena")).thenReturn(active);

        tournamentManager.registerForTournament(event);

        verify(channel).sendMessage(contains("уже зарегистрирован"));
    }

    @Test
    public void testRegisterForTournament_fullTournament_sendsError() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);

        List<String> participants = new ArrayList<>(
            List.of("p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9")); // 8 players
        TournamentRepository.Tournament active = new TournamentRepository.Tournament(
            1, "arena", participants, "{}", "open", 1);
        when(tournamentRepository.getActive("arena")).thenReturn(active);

        tournamentManager.registerForTournament(event);

        verify(channel).sendMessage(contains("заполнен"));
    }

    @Test
    public void testTournamentStatus_noActive_sendsNoTournament() {
        when(tournamentRepository.getActive("arena")).thenReturn(null);

        tournamentManager.tournamentStatus(event);

        verify(channel).sendMessage(contains("Активного турнира нет"));
    }

    @Test
    public void testTournamentStatus_withActive_showsParticipants() {
        Player p1 = new Player("Alpha", "p1");
        when(playerRepository.get("p1")).thenReturn(p1);

        List<String> participants = new ArrayList<>(List.of("p1"));
        TournamentRepository.Tournament active = new TournamentRepository.Tournament(
            1, "arena", participants, "{}", "open", 1);
        when(tournamentRepository.getActive("arena")).thenReturn(active);

        tournamentManager.tournamentStatus(event);

        verify(channel).sendMessage(contains("Alpha"));
    }

    @Test
    public void testServerTournament_sendsInfo() {
        tournamentManager.serverTournament(event);

        verify(channel).sendMessage(contains("Турнир сервера"));
    }

    @Test
    public void testStartTournament_notEnoughPlayers_sendsError() {
        when(playerRepository.get(anyString())).thenReturn(null);

        tournamentManager.startTournament(List.of("player1"), channel);

        verify(channel).sendMessage(contains("Недостаточно участников"));
    }

    @Test
    public void testStartTournament_twoPlayers_runsBattle() {
        Player p1 = new Player("Alpha", "p1");
        p1.setHp(100);
        p1.setMaxHp(100);
        Player p2 = new Player("Beta", "p2");
        p2.setHp(100);
        p2.setMaxHp(100);

        when(playerRepository.get("p1")).thenReturn(p1);
        when(playerRepository.get("p2")).thenReturn(p2);
        // After tournament, winner gets saved
        when(playerRepository.get(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return "p1".equals(id) ? p1 : "p2".equals(id) ? p2 : null;
        });

        tournamentManager.startTournament(List.of("p1", "p2"), channel);

        verify(battleManager, atLeastOnce()).playerBattle(anyList(), anyList(), eq(channel));
    }
}
