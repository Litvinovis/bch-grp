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

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class WorldEventManagerTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;
    @Mock private net.dv8tion.jda.api.entities.Message message;

    private WorldEventManager worldEventManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        worldEventManager = new WorldEventManager(playerRepository);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(event.getMessage()).thenReturn(message);

        // Reset static state before each test
        WorldEventManager.economicCrisisActive = false;
    }

    @Test
    public void testWorldBossAttack_noBoss_sendsNotActive() {
        // worldBossHp is 0 by default
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);

        worldEventManager.worldBossAttack(event);

        verify(channel).sendMessage(contains("не активен"));
    }

    @Test
    public void testWorldBossAttack_wrongLocation_sendsError() throws Exception {
        // Use reflection to set worldBossHp > 0 and worldBossLocation
        setStaticField("worldBossHp", 5000);
        setStaticField("worldBossLocation", "мейн");

        Player player = new Player("Test", "player1");
        player.setLocation("деградач");
        when(playerRepository.get("player1")).thenReturn(player);

        worldEventManager.worldBossAttack(event);

        verify(channel).sendMessage(contains("Переместись туда"));

        setStaticField("worldBossHp", 0);
    }

    @Test
    public void testWorldBossAttack_rightLocation_dealsDamage() throws Exception {
        setStaticField("worldBossHp", 5000);
        setStaticField("worldBossLocation", "мейн");

        Player player = new Player("Test", "player1");
        player.setLocation("мейн");
        player.setStrength(15);
        when(playerRepository.get("player1")).thenReturn(player);

        worldEventManager.worldBossAttack(event);

        verify(channel).sendMessage(contains("атаковал мирового босса"));

        setStaticField("worldBossHp", 0);
    }

    @Test
    public void testInvasionStatus_sendsInfo() {
        worldEventManager.invasionStatus(event);

        verify(channel).sendMessage(contains("Нашествие"));
    }

    @Test
    public void testCrisisStatus_notActive_sendsNormal() {
        WorldEventManager.economicCrisisActive = false;

        worldEventManager.crisisStatus(event);

        verify(channel).sendMessage(contains("не активен"));
    }

    @Test
    public void testCrisisStatus_active_sendsActiveMessage() {
        WorldEventManager.economicCrisisActive = true;

        worldEventManager.crisisStatus(event);

        verify(channel).sendMessage(contains("активен"));
        WorldEventManager.economicCrisisActive = false;
    }

    @Test
    public void testShowSeason_sendsSeasonalItem() {
        worldEventManager.showSeason(event);

        verify(channel).sendMessage(contains("Сезонный предмет"));
    }

    private void setStaticField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = WorldEventManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
