package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Person;
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
 * Tests for PlayersManager PvP and duel mechanics:
 * - playersFight: non-PvP zone, no opponents, all clan, attacker wins, loser dies
 * - acceptDuel: winner gets money+reputation, loser loses money
 * - declineDuel: challenge removed, message sent
 */
public class PlayersManagerPvpDuelTest {

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
        doNothing().when(messageAction).queue();
    }

    // ---- playersFight: precondition checks ------------------------------------

    @Test
    public void playersFight_nonPvpLocation_sendsNoPvpMessage() {
        Player player = playerAt("uid1", "мейн");
        when(playerRepository.get("uid1")).thenReturn(player);
        when(locationManager.getLocation("мейн")).thenReturn(
                locationWith("мейн", false, List.of()));

        playersManager.playersFight(event);

        verify(channel, atLeastOnce()).sendMessage(contains("нельзя драться"));
        verify(battleManager, never()).playerBattle(any(), any(), any());
    }

    @Test
    public void playersFight_noOtherPlayers_sendsNoOpponentsMessage() {
        Player player = playerAt("uid1", "арена");
        when(playerRepository.get("uid1")).thenReturn(player);
        when(locationManager.getLocation("арена")).thenReturn(
                locationWith("арена", true, List.of("uid1"))); // only self
        when(clanManager.getClanMembers(anyString())).thenReturn(Collections.emptyList());

        playersManager.playersFight(event);

        verify(channel, atLeastOnce()).sendMessage(contains("Нет игроков"));
        verify(battleManager, never()).playerBattle(any(), any(), any());
    }

    @Test
    public void playersFight_onlyClanMembersPresent_sendsClanMessage() {
        Player player = playerAt("uid1", "арена");
        Player ally   = playerAt("uid2", "арена");
        when(playerRepository.get("uid1")).thenReturn(player);
        when(playerRepository.get("uid2")).thenReturn(ally);
        when(locationManager.getLocation("арена")).thenReturn(
                locationWith("арена", true, List.of("uid1", "uid2")));
        when(clanManager.getClanMembers(anyString())).thenReturn(List.of("uid2"));

        playersManager.playersFight(event);

        verify(channel, atLeastOnce()).sendMessage(contains("клан"));
        verify(battleManager, never()).playerBattle(any(), any(), any());
    }

    // ---- playersFight: solo player (no clan) can fight -------------------------

    @Test
    public void playersFight_soloPlayerNoClan_battleStarts() {
        Player player = playerAt("uid1", "арена"); // clanName="" by default
        Player enemy  = playerAt("uid2", "арена");

        when(playerRepository.get("uid1")).thenReturn(player);
        when(playerRepository.get("uid2")).thenReturn(enemy);
        when(locationManager.getLocation("арена"))
                .thenReturn(locationWith("арена", true, List.of("uid1", "uid2")))
                .thenReturn(locationWith("арена", true, List.of("uid1")));
        when(clanManager.getClanMembers("")).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            List<Person> team1 = inv.getArgument(0);
            List<Person> team2 = inv.getArgument(1);
            team1.forEach(p -> p.setHp(50));
            team2.forEach(p -> p.setHp(0));
            team1.addAll(team2);
            return team1;
        }).when(battleManager).playerBattle(anyList(), anyList(), any());

        Location respawn = locationWith("респаун", false, new ArrayList<>());
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);

        playersManager.playersFight(event);

        // Solo player must be able to start a battle even without a clan
        verify(battleManager).playerBattle(anyList(), anyList(), any());
    }

    // ---- playersFight: attacker wins ------------------------------------------

    @Test
    public void playersFight_attackerWins_moneyAndXpAdded() {
        Player attacker = playerAt("uid1", "арена"); // clanName="" — solo player uses fix
        Player enemy    = playerAt("uid2", "арена");
        attacker.setMoney(0);

        when(playerRepository.get("uid1")).thenReturn(attacker);
        when(playerRepository.get("uid2")).thenReturn(enemy);
        // First call: full population for battle setup; second call: updated population for end message
        when(locationManager.getLocation("арена"))
                .thenReturn(locationWith("арена", true, List.of("uid1", "uid2")))
                .thenReturn(locationWith("арена", true, List.of("uid1")));
        when(clanManager.getClanMembers("")).thenReturn(Collections.emptyList());

        // Battle result: attacker has HP > 0, enemy HP = 0
        doAnswer(inv -> {
            List<Person> team1 = inv.getArgument(0);
            List<Person> team2 = inv.getArgument(1);
            team1.forEach(p -> p.setHp(50));
            team2.forEach(p -> p.setHp(0));
            team1.addAll(team2);
            return team1;
        }).when(battleManager).playerBattle(anyList(), anyList(), any());

        Location respawn = locationWith("респаун", false, new ArrayList<>());
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);

        playersManager.playersFight(event);

        // Attacker should have received rewards (changeMoney/changeXp → put called)
        verify(playerRepository, atLeastOnce()).put(eq("uid1"), any());
    }

    // ---- playersFight: loser dies ---------------------------------------------

    @Test
    public void playersFight_loserDies_locationSetToRespawn() {
        Player attacker = playerAt("uid1", "арена"); // clanName="" — solo player uses fix
        Player enemy    = playerAt("uid2", "арена");

        when(playerRepository.get("uid1")).thenReturn(attacker);
        when(playerRepository.get("uid2")).thenReturn(enemy);
        when(locationManager.getLocation("арена"))
                .thenReturn(locationWith("арена", true, List.of("uid1", "uid2")))
                .thenReturn(locationWith("арена", true, List.of()));
        when(clanManager.getClanMembers("")).thenReturn(Collections.emptyList());

        doAnswer(inv -> {
            List<Person> team1 = inv.getArgument(0);
            List<Person> team2 = inv.getArgument(1);
            team1.forEach(p -> p.setHp(50));
            team2.forEach(p -> p.setHp(0));
            team1.addAll(team2);
            return team1;
        }).when(battleManager).playerBattle(anyList(), anyList(), any());

        Location respawn = locationWith("респаун", false, new ArrayList<>());
        when(locationManager.movePlayerInPopulation(any(), eq("респаун"))).thenReturn(respawn);

        playersManager.playersFight(event);

        assertEquals("респаун", enemy.getLocation());
    }

    // ---- acceptDuel -----------------------------------------------------------

    @Test
    public void acceptDuel_winnerGetsPrizeAndReputation() {
        Player challenger = playerAt("chal", "мейн");
        challenger.setStrength(10);
        challenger.setLuck(1);
        challenger.setMoney(0);
        challenger.setReputation(0);

        Player challenged = playerAt("uid1", "мейн");
        challenged.setStrength(1);
        challenged.setLuck(1);
        challenged.setMoney(500);
        challenged.setReputation(0);

        // Challenger wins because strength 10 >> 1 in most rolls
        when(playerRepository.get("uid1")).thenReturn(challenged);
        when(playerRepository.get("chal")).thenReturn(challenger);
        when(playerRepository.contains("chal")).thenReturn(true);

        // Inject pending duel: challenged (uid1) has a duel from challenger (chal)
        // We do this by calling challengeDuel indirectly via reflection or by using
        // challengeDuel method itself with mocked event
        MessageReceivedEvent challengeEvent = mock(MessageReceivedEvent.class);
        Message challengeMsg = mock(Message.class);
        Mentions mentions = mock(Mentions.class);
        User targetUser = mock(User.class);

        when(challengeEvent.getAuthor()).thenReturn(mock(User.class));
        when(challengeEvent.getAuthor().getId()).thenReturn("chal");
        when(challengeEvent.getMessage()).thenReturn(challengeMsg);
        when(challengeEvent.getChannel()).thenReturn(channel);
        when(challengeMsg.getMentions()).thenReturn(mentions);
        when(mentions.getUsers()).thenReturn(List.of(targetUser));
        when(targetUser.getId()).thenReturn("uid1");
        when(targetUser.getAsMention()).thenReturn("<@uid1>");
        when(targetUser.getName()).thenReturn("Challenged");
        when(playerRepository.contains("uid1")).thenReturn(true);

        playersManager.challengeDuel(challengeEvent);

        // Now uid1 accepts
        playersManager.acceptDuel(event);

        // Winner gets +100 money and +5 reputation; duel resolved
        assertTrue("One player must have +5 reputation (winner)",
                challenger.getReputation() == 5 || challenged.getReputation() == 5);
        // Total prize goes to winner; verify both were saved
        verify(playerRepository, atLeastOnce()).put(eq("chal"), any());
        verify(playerRepository, atLeastOnce()).put(eq("uid1"), any());
    }

    @Test
    public void acceptDuel_noPendingDuel_sendsMissingChallengeMessage() {
        // uid1 accepts but has no pending duel
        when(playerRepository.get("uid1")).thenReturn(playerAt("uid1", "мейн"));

        playersManager.acceptDuel(event);

        verify(channel, atLeastOnce()).sendMessage(contains("нет активных вызовов"));
    }

    // ---- declineDuel ----------------------------------------------------------

    @Test
    public void declineDuel_challengeRemovedFromPending() {
        Player challenged = playerAt("uid1", "мейн");
        when(playerRepository.get("uid1")).thenReturn(challenged);
        when(playerRepository.get("chal")).thenReturn(playerAt("chal", "мейн"));
        when(playerRepository.contains("uid1")).thenReturn(true);

        // Set up challenge first
        MessageReceivedEvent challengeEvent = mock(MessageReceivedEvent.class);
        Message challengeMsg = mock(Message.class);
        Mentions mentions = mock(Mentions.class);
        User targetUser = mock(User.class);
        User challengerUser = mock(User.class);

        when(challengeEvent.getAuthor()).thenReturn(challengerUser);
        when(challengerUser.getId()).thenReturn("chal");
        when(challengeEvent.getMessage()).thenReturn(challengeMsg);
        when(challengeEvent.getChannel()).thenReturn(channel);
        when(challengeMsg.getMentions()).thenReturn(mentions);
        when(mentions.getUsers()).thenReturn(List.of(targetUser));
        when(targetUser.getId()).thenReturn("uid1");
        when(targetUser.getAsMention()).thenReturn("<@uid1>");
        when(targetUser.getName()).thenReturn("Challenged");

        playersManager.challengeDuel(challengeEvent);
        playersManager.declineDuel(event);

        // Verify decline message was sent
        verify(channel, atLeastOnce()).sendMessage(contains("отказался"));

        // After declining, acceptDuel should say no pending duel
        playersManager.acceptDuel(event);
        verify(channel, atLeastOnce()).sendMessage(contains("нет активных вызовов"));
    }

    // ---- helpers ---------------------------------------------------------------

    private Player playerAt(String id, String location) {
        Player p = new Player("Player_" + id, id);
        p.setLocation(location);
        p.setHp(100);
        p.setMaxHp(100);
        p.setStrength(5);
        p.setLuck(1);
        p.setMoney(0);
        p.setArmor(0);
        return p;
    }

    private Location locationWith(String name, boolean pvp, List<String> population) {
        return Location.builder()
                .name(name)
                .pvp(pvp)
                .dangerous(0)
                .boss(null)
                .paths(new ArrayList<>())
                .populationByName(new ArrayList<>())
                .populationById(new ArrayList<>(population))
                .teleport(false)
                .build();
    }
}
