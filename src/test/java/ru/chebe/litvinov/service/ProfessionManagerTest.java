package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ProfessionManagerTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;
    @Mock private net.dv8tion.jda.api.entities.Message message;

    private ProfessionManager professionManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        professionManager = new ProfessionManager(playerRepository);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(event.getMessage()).thenReturn(message);
    }

    @Test
    public void testChooseProfession_noProfession_assignsProfession() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+профессия выбрать кузнец");

        professionManager.chooseProfession(event);

        verify(playerRepository).put(eq("player1"), argThat(p ->
            "кузнец".equals(p.getProfession()) && p.getProfessionLevel() == 1));
    }

    @Test
    public void testChooseProfession_alreadyHasProfession_sendsError() {
        Player player = new Player("Test", "player1");
        player.setProfession("алхимик");
        player.setProfessionLevel(3);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+профессия выбрать кузнец");

        professionManager.chooseProfession(event);

        verify(playerRepository, never()).put(anyString(), any());
        verify(channel).sendMessage(contains("уже есть профессия"));
    }

    @Test
    public void testChooseProfession_unknownProfession_sendsError() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+профессия выбрать шаман");

        professionManager.chooseProfession(event);

        verify(playerRepository, never()).put(anyString(), any());
        verify(channel).sendMessage(contains("Неизвестная профессия"));
    }

    @Test
    public void testChooseProfession_showInfo_showsInfo() {
        Player player = new Player("Test", "player1");
        player.setProfession("повар");
        player.setProfessionLevel(2);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+профессия инфо");

        professionManager.chooseProfession(event);

        verify(channel).sendMessage(contains("Профессии"));
    }

    @Test
    public void testGatherResource_noProfession_sendsError() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);

        professionManager.gatherResource(event);

        verify(channel).sendMessage(contains("Сначала выбери профессию"));
    }

    @Test
    public void testGatherResource_withProfession_addsResource() {
        Player player = new Player("Test", "player1");
        player.setProfession("кузнец");
        player.setProfessionLevel(1);
        player.setLocation("качалочка");
        player.setResources(new HashMap<>());
        when(playerRepository.get("player1")).thenReturn(player);

        professionManager.gatherResource(event);

        verify(playerRepository).put(eq("player1"), argThat(p ->
            p.getResources() != null && p.getResources().getOrDefault("руда", 0) >= 1));
    }

    @Test
    public void testGatherResource_cooldown_sendsError() {
        Player player = new Player("Test", "player1");
        player.setProfession("кузнец");
        player.setProfessionLevel(1);
        player.setLocation("качалочка");
        player.setResources(new HashMap<>());
        when(playerRepository.get("player1")).thenReturn(player);

        // First gather
        professionManager.gatherResource(event);
        // Second gather immediately — should hit cooldown
        professionManager.gatherResource(event);

        verify(channel, atLeast(1)).sendMessage(contains("кулдауне"));
    }

    @Test
    public void testCraftItem_kuznetsSword_consumesOre_addsStrength() {
        Player player = new Player("Test", "player1");
        player.setProfession("кузнец");
        player.setProfessionLevel(1);
        player.setResources(new HashMap<>());
        player.getResources().put("руда", 5);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+создать кованый меч");

        professionManager.craftItem(event);

        // Default strength is 5, crafting adds 5 → expected 10
        verify(playerRepository).put(eq("player1"), argThat(p ->
            p.getResources().getOrDefault("руда", 0) == 2 && p.getStrength() == 10));
    }

    @Test
    public void testCraftItem_notEnoughResources_sendsError() {
        Player player = new Player("Test", "player1");
        player.setProfession("кузнец");
        player.setProfessionLevel(1);
        player.setResources(new HashMap<>());
        player.getResources().put("руда", 1);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+создать кованый меч");

        professionManager.craftItem(event);

        verify(channel).sendMessage(contains("Неизвестный рецепт"));
    }

    @Test
    public void testCraftItem_alchemistPotion_restoresHp() {
        Player player = new Player("Test", "player1");
        player.setProfession("алхимик");
        player.setProfessionLevel(1);
        player.setHp(50);
        player.setMaxHp(200);
        player.setResources(new HashMap<>());
        player.getResources().put("травы", 3);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+создать зелье алхимика");

        professionManager.craftItem(event);

        verify(playerRepository).put(eq("player1"), argThat(p -> p.getHp() == 100));
    }

    @Test
    public void testCraftItem_jewelerRing_addsStrength() {
        Player player = new Player("Test", "player1");
        player.setProfession("ювелир");
        player.setProfessionLevel(1);
        player.setResources(new HashMap<>());
        player.getResources().put("камень", 4);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+создать кольцо силы");

        professionManager.craftItem(event);

        // Default strength is 5, crafting ring adds 3 → expected 8
        verify(playerRepository).put(eq("player1"), argThat(p -> p.getStrength() == 8));
    }

    @Test
    public void testShowRecipes_showsRecipesForProfession() {
        Player player = new Player("Test", "player1");
        player.setProfession("повар");
        when(playerRepository.get("player1")).thenReturn(player);

        professionManager.showRecipes(event);

        verify(channel).sendMessage(contains("Рецепты Повара"));
    }

    @Test
    public void testResourceMarket_showsPrices() {
        Player player = new Player("Test", "player1");
        player.setResources(new HashMap<>());
        when(playerRepository.get("player1")).thenReturn(player);

        professionManager.resourceMarket(event);

        verify(channel).sendMessage(contains("Биржа ресурсов"));
    }
}
