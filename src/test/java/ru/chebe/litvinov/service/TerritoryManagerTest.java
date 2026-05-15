package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.repository.TerritoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TerritoryManagerTest {

    @Mock private TerritoryRepository territoryRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private ClanManager clanManager;
    @Mock private LocationManager locationManager;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;
    @Mock private net.dv8tion.jda.api.entities.Message message;

    private TerritoryManager territoryManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        territoryManager = new TerritoryManager(territoryRepository, playerRepository, clanManager, locationManager);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(event.getMessage()).thenReturn(message);
    }

    @Test
    public void testCaptureTerritory_noClan_sendsError() {
        Player player = new Player("Test", "player1");
        player.setClanName("");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+захватить мейн");

        territoryManager.captureTerritory(event);

        verify(channel).sendMessage(contains("нужен клан"));
    }

    @Test
    public void testCaptureTerritory_invalidLocation_sendsError() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        player.setLocation("мейн");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+захватить несуществующая");
        when(locationManager.getLocation("несуществующая")).thenReturn(null);

        territoryManager.captureTerritory(event);

        verify(channel).sendMessage(contains("не существует"));
    }

    @Test
    public void testCaptureTerritory_notEnoughMembers_sendsError() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        player.setLocation("мейн");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+захватить мейн");
        when(locationManager.getLocation("мейн")).thenReturn(Location.builder().name("мейн").build());
        when(clanManager.getClanMembers("MyClan")).thenReturn(List.of("player1"));
        when(playerRepository.get("player1")).thenReturn(player);

        territoryManager.captureTerritory(event);

        verify(channel).sendMessage(contains("минимум 3"));
    }

    @Test
    public void testCaptureTerritory_enoughMembers_weakPlayer_sendsDefeat() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        player.setLocation("мейн");
        player.setStrength(3);
        player.setArmor(3);

        Player m2 = new Player("M2", "player2");
        m2.setLocation("мейн");
        Player m3 = new Player("M3", "player3");
        m3.setLocation("мейн");

        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.get("player2")).thenReturn(m2);
        when(playerRepository.get("player3")).thenReturn(m3);
        when(message.getContentDisplay()).thenReturn("+захватить мейн");
        when(locationManager.getLocation("мейн")).thenReturn(Location.builder().name("мейн").build());
        when(clanManager.getClanMembers("MyClan")).thenReturn(List.of("player1", "player2", "player3"));

        territoryManager.captureTerritory(event);

        verify(channel).sendMessage(contains("Страж территории"));
    }

    @Test
    public void testCaptureTerritory_success_upserts() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        player.setLocation("мейн");
        player.setStrength(8);
        player.setArmor(5);

        Player m2 = new Player("M2", "player2");
        m2.setLocation("мейн");
        Player m3 = new Player("M3", "player3");
        m3.setLocation("мейн");

        when(playerRepository.get("player1")).thenReturn(player);
        when(playerRepository.get("player2")).thenReturn(m2);
        when(playerRepository.get("player3")).thenReturn(m3);
        when(message.getContentDisplay()).thenReturn("+захватить мейн");
        when(locationManager.getLocation("мейн")).thenReturn(Location.builder().name("мейн").build());
        when(clanManager.getClanMembers("MyClan")).thenReturn(List.of("player1", "player2", "player3"));

        territoryManager.captureTerritory(event);

        verify(territoryRepository).upsert(eq("мейн"), eq("MyClan"), anyLong(), eq(5));
        verify(channel).sendMessage(contains("захватил территорию"));
    }

    @Test
    public void testCollectTax_noTerritory_noTax() {
        Player player = new Player("Test", "player1");
        player.setLocation("мейн");
        when(territoryRepository.get("мейн")).thenReturn(null);

        territoryManager.collectTax(player, 1000);

        verify(clanManager, never()).addToClanBank(anyString(), anyString(), anyInt());
    }

    @Test
    public void testCollectTax_ownTerritory_noTax() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        player.setLocation("мейн");
        TerritoryRepository.Territory territory = new TerritoryRepository.Territory("мейн", "MyClan", 0L, 5);
        when(territoryRepository.get("мейн")).thenReturn(territory);

        territoryManager.collectTax(player, 1000);

        verify(clanManager, never()).addToClanBank(anyString(), anyString(), anyInt());
    }

    @Test
    public void testCollectTax_otherClanTerritory_taxCollected() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        player.setLocation("мейн");
        TerritoryRepository.Territory territory = new TerritoryRepository.Territory("мейн", "EnemyClan", 0L, 10);
        when(territoryRepository.get("мейн")).thenReturn(territory);

        territoryManager.collectTax(player, 1000);

        verify(clanManager).addToClanBank("EnemyClan", "монеты", 100);
    }

    @Test
    public void testTerritoryClanMap_empty_sendsNoCaptures() {
        when(territoryRepository.getAll()).thenReturn(List.of());

        territoryManager.territoryClanMap(event);

        verify(channel).sendMessage(contains("не захвачена"));
    }

    @Test
    public void testTerritoryClanMap_withTerritories_showsMap() {
        TerritoryRepository.Territory t = new TerritoryRepository.Territory("мейн", "MyClan", 0L, 5);
        when(territoryRepository.getAll()).thenReturn(List.of(t));

        territoryManager.territoryClanMap(event);

        verify(channel).sendMessage(contains("MyClan"));
    }

    @Test
    public void testDeclareAlliance_noClan_sendsError() {
        Player player = new Player("Test", "player1");
        player.setClanName("");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+альянс otherclan");

        territoryManager.declareAlliance(event);

        verify(channel).sendMessage(contains("нужен клан"));
    }

    @Test
    public void testDeclareAlliance_validClan_savesAlliance() {
        Player player = new Player("Test", "player1");
        player.setClanName("MyClan");
        Clan myClan = new Clan("MyClan", "player1");
        Clan targetClan = new Clan("allies", "otherleader");

        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+альянс allies");
        when(clanManager.getClan("MyClan")).thenReturn(myClan);
        when(clanManager.getClan("allies")).thenReturn(targetClan);

        territoryManager.declareAlliance(event);

        verify(clanManager).saveClan(argThat(c -> c.getAlliances() != null && c.getAlliances().contains("allies")));
        verify(channel).sendMessage(contains("альянс"));
    }
}
