package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.Mentions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.BountyRepository;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class BountyManagerTest {

    @Mock private BountyRepository bountyRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;
    @Mock private net.dv8tion.jda.api.entities.Message message;
    @Mock private Mentions mentions;

    private BountyManager bountyManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        bountyManager = new BountyManager(bountyRepository, playerRepository);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(event.getMessage()).thenReturn(message);
        when(message.getMentions()).thenReturn(mentions);
    }

    @Test
    public void testPlaceBounty_noMention_sendsUsage() {
        when(mentions.getUsers()).thenReturn(List.of());

        bountyManager.placeBounty(event);

        verify(channel).sendMessage(contains("Использование"));
    }

    @Test
    public void testPlaceBounty_selfTarget_sendsError() {
        net.dv8tion.jda.api.entities.User target = mock(net.dv8tion.jda.api.entities.User.class);
        when(target.getId()).thenReturn("player1"); // same as placer
        when(mentions.getUsers()).thenReturn(List.of(target));

        bountyManager.placeBounty(event);

        verify(channel).sendMessage(contains("Нельзя"));
    }

    @Test
    public void testPlaceBounty_targetNotRegistered_sendsError() {
        net.dv8tion.jda.api.entities.User target = mock(net.dv8tion.jda.api.entities.User.class);
        when(target.getId()).thenReturn("player2");
        when(mentions.getUsers()).thenReturn(List.of(target));
        when(playerRepository.contains("player2")).thenReturn(false);

        bountyManager.placeBounty(event);

        verify(channel).sendMessage(contains("не зарегистрирован"));
    }

    @Test
    public void testPlaceBounty_noAmountSpecified_sendsError() {
        net.dv8tion.jda.api.entities.User target = mock(net.dv8tion.jda.api.entities.User.class);
        when(target.getId()).thenReturn("player2");
        when(mentions.getUsers()).thenReturn(List.of(target));
        when(playerRepository.contains("player2")).thenReturn(true);
        when(message.getContentRaw()).thenReturn("+бонт <@player2>");

        bountyManager.placeBounty(event);

        verify(channel).sendMessage(contains("Укажите сумму"));
    }

    @Test
    public void testPlaceBounty_notEnoughMoney_sendsError() {
        net.dv8tion.jda.api.entities.User target = mock(net.dv8tion.jda.api.entities.User.class);
        when(target.getId()).thenReturn("player2");
        when(mentions.getUsers()).thenReturn(List.of(target));
        when(playerRepository.contains("player2")).thenReturn(true);
        when(message.getContentRaw()).thenReturn("+бонт <@player2> 500");

        Player placer = new Player("Placer", "player1");
        placer.setMoney(100);
        when(playerRepository.get("player1")).thenReturn(placer);

        bountyManager.placeBounty(event);

        verify(channel).sendMessage(contains("Недостаточно монет"));
    }

    @Test
    public void testPlaceBounty_success_placesBounty() {
        net.dv8tion.jda.api.entities.User target = mock(net.dv8tion.jda.api.entities.User.class);
        when(target.getId()).thenReturn("player2");
        when(mentions.getUsers()).thenReturn(List.of(target));
        when(playerRepository.contains("player2")).thenReturn(true);
        when(message.getContentRaw()).thenReturn("+бонт <@player2> 200");

        Player placer = new Player("Placer", "player1");
        placer.setMoney(500);
        Player targetPlayer = new Player("Target", "player2");
        when(playerRepository.get("player1")).thenReturn(placer);
        when(playerRepository.get("player2")).thenReturn(targetPlayer);

        bountyManager.placeBounty(event);

        verify(bountyRepository).place("player2", "player1", 200);
        verify(channel).sendMessage(contains("Награда"));
    }

    @Test
    public void testGetBounties_empty_sendsNoBounties() {
        when(bountyRepository.getActive()).thenReturn(List.of());

        bountyManager.getBounties(event);

        verify(channel).sendMessage(contains("Активных наград"));
    }

    @Test
    public void testGetBounties_withBounties_showsList() {
        BountyRepository.Bounty b = new BountyRepository.Bounty(1, "player2", "player1", 200, true, System.currentTimeMillis());
        when(bountyRepository.getActive()).thenReturn(List.of(b));
        Player target = new Player("Target", "player2");
        when(playerRepository.get("player2")).thenReturn(target);

        bountyManager.getBounties(event);

        verify(channel).sendMessage(contains("Target"));
    }

    @Test
    public void testClaimBounty_noBounty_returnsZero() {
        when(bountyRepository.claimAndGetReward("player2")).thenReturn(0);

        int reward = bountyManager.claimBounty("player1", "player2");

        assertEquals(0, reward);
        verify(playerRepository, never()).put(anyString(), any());
    }

    @Test
    public void testClaimBounty_hasBounty_addsMoney() {
        when(bountyRepository.claimAndGetReward("player2")).thenReturn(300);
        Player killer = new Player("Killer", "player1");
        killer.setMoney(100);
        when(playerRepository.get("player1")).thenReturn(killer);

        int reward = bountyManager.claimBounty("player1", "player2");

        assertEquals(300, reward);
        verify(playerRepository).put(eq("player1"), argThat(p -> p.getMoney() == 400));
    }
}
