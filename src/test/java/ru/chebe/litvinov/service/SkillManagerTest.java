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

/**
 * Tests for the skill system (items 131-137) implemented in PlayersManager.
 * Covers: showSkills, investSkill, useAbility, chooseSecondClass.
 */
public class SkillManagerTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private LocationManager locationManager;
    @Mock private ItemsManager itemsManager;
    @Mock private BattleManager battleManager;
    @Mock private EventsManager eventsManager;
    @Mock private ClanManager clanManager;
    @Mock private Tavern tavern;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;
    @Mock private net.dv8tion.jda.api.entities.Message message;

    private PlayersManager playersManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        playersManager = new PlayersManager(playerRepository, locationManager, itemsManager,
            battleManager, eventsManager, clanManager, tavern, new NpcManager());
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(event.getMessage()).thenReturn(message);
    }

    @Test
    public void testShowSkills_noClass_showsBaseSkill() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("");
        player.setSkillPoints(3);
        when(playerRepository.get("player1")).thenReturn(player);

        playersManager.showSkills(event);

        verify(channel).sendMessage(contains("базовый удар"));
    }

    @Test
    public void testShowSkills_warrior_showsWarriorSkills() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setSkillPoints(2);
        when(playerRepository.get("player1")).thenReturn(player);

        playersManager.showSkills(event);

        verify(channel).sendMessage(contains("берсерк"));
    }

    @Test
    public void testInvestSkill_noPoints_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setSkillPoints(0);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+вложить берсерк");

        playersManager.investSkill(event);

        verify(channel).sendMessage(contains("нет очков навыков"));
    }

    @Test
    public void testInvestSkill_unknownSkill_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setSkillPoints(3);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+вложить молния");

        playersManager.investSkill(event);

        verify(channel).sendMessage(contains("не найден"));
    }

    @Test
    public void testInvestSkill_validSkill_investsPoint() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setSkillPoints(2);
        player.setSkills(new HashMap<>());
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+вложить берсерк");

        playersManager.investSkill(event);

        verify(playerRepository).put(eq("player1"), argThat(p ->
            p.getSkills().getOrDefault("берсерк", 0) == 1 && p.getSkillPoints() == 1));
    }

    @Test
    public void testInvestSkill_maxLevel_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setSkillPoints(5);
        player.setSkills(new HashMap<>());
        player.getSkills().put("берсерк", 3); // already max
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+вложить берсерк");

        playersManager.investSkill(event);

        verify(channel).sendMessage(contains("максимальном уровне"));
    }

    @Test
    public void testUseAbility_teleport_notMage_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+умение телепорт");

        playersManager.useAbility(event);

        verify(channel).sendMessage(contains("только МАГам"));
    }

    @Test
    public void testUseAbility_healing_paladin_restoresHp() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ПАЛАДИН");
        player.setHp(50);
        player.setMaxHp(200);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+умение исцеление");

        playersManager.useAbility(event);

        verify(playerRepository).put(eq("player1"), argThat(p -> p.getHp() == 100));
    }

    @Test
    public void testUseAbility_healing_notPaladin_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("МАГ");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+умение исцеление");

        playersManager.useAbility(event);

        verify(channel).sendMessage(contains("только ПАЛАДИНам"));
    }

    @Test
    public void testUseAbility_unknown_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("МАГ");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+умение огненный шар");

        playersManager.useAbility(event);

        verify(channel).sendMessage(contains("не найдено"));
    }

    @Test
    public void testChooseSecondClass_lowLevel_sendsError() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setLevel(30);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+второй класс маг");

        playersManager.chooseSecondClass(event);

        verify(channel).sendMessage(contains("50 уровня"));
    }

    @Test
    public void testChooseSecondClass_validChoice_assignsClass() {
        Player player = new Player("Test", "player1");
        player.setPlayerClass("ВОИН");
        player.setLevel(50);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+второй класс маг");

        playersManager.chooseSecondClass(event);

        verify(playerRepository).put(eq("player1"), argThat(p ->
            p.getPlayerClass() != null && p.getPlayerClass().contains("МАГ")));
    }
}
