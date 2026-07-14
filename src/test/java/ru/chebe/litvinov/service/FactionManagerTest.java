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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class FactionManagerTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;

    private FactionManager factionManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        factionManager = new FactionManager(playerRepository);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void testShowFactionRep_sendsReputation() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 50, "МАГИ", 20, "ВОИНЫ", 80)));
        when(playerRepository.get("player1")).thenReturn(player);

        factionManager.showFactionRep(event);

        verify(channel).sendMessage(contains("Репутация у фракций"));
    }

    @Test
    public void testShowFactionRep_nullRep_sendsDefault() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(null);
        when(playerRepository.get("player1")).thenReturn(player);

        factionManager.showFactionRep(event);

        verify(channel).sendMessage(contains("ТОРГОВЦЫ"));
    }

    @Test
    public void testAddRep_increasesReputation() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 0, "МАГИ", 0, "ВОИНЫ", 0)));
        when(playerRepository.get("player1")).thenReturn(player);

        factionManager.addRep("player1", "ВОИНЫ", 30);

        verify(playerRepository).put(eq("player1"), argThat(p ->
            p.getFactionRep().getOrDefault("ВОИНЫ", 0) == 30));
    }

    @Test
    public void testAddRep_cappedAt200() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 0, "МАГИ", 0, "ВОИНЫ", 190)));
        when(playerRepository.get("player1")).thenReturn(player);

        factionManager.addRep("player1", "ВОИНЫ", 50);

        verify(playerRepository).put(eq("player1"), argThat(p ->
            p.getFactionRep().getOrDefault("ВОИНЫ", 0) == 200));
    }

    @Test
    public void testAddRep_nullPlayer_doesNothing() {
        when(playerRepository.get("player1")).thenReturn(null);

        factionManager.addRep("player1", "ВОИНЫ", 10);

        verify(playerRepository, never()).put(anyString(), any());
    }

    @Test
    public void testApplyFactionBonuses_lowRep_noBonus() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 50, "МАГИ", 0, "ВОИНЫ", 0)));

        int bonus = factionManager.applyFactionBonuses(player, "trade");

        assertEquals(0, bonus);
    }

    @Test
    public void testApplyFactionBonuses_trade_highRep_returnsDiscount() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 100, "МАГИ", 0, "ВОИНЫ", 0)));

        int bonus = factionManager.applyFactionBonuses(player, "trade");

        assertEquals(5, bonus);
    }

    @Test
    public void testApplyFactionBonuses_combat_highRep_returnsDamageBonus() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 0, "МАГИ", 0, "ВОИНЫ", 100)));

        int bonus = factionManager.applyFactionBonuses(player, "combat");

        assertEquals(5, bonus);
    }

    @Test
    public void testApplyFactionBonuses_nullRep_returnsZero() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(null);

        int bonus = factionManager.applyFactionBonuses(player, "combat");

        assertEquals(0, bonus);
    }

    @Test
    public void testShowFactionRep_highRep_showsBonus() {
        Player player = new Player("Test", "player1");
        player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 100, "МАГИ", 0, "ВОИНЫ", 0)));
        when(playerRepository.get("player1")).thenReturn(player);

        factionManager.showFactionRep(event);

        verify(channel).sendMessage(contains("скидка"));
    }
}
