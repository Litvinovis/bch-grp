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
import ru.chebe.litvinov.data.NpcBot;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.fightNpc().
 *
 * This is the method that caused 4 simultaneous production bugs because
 * playerBattle threw UnsupportedOperationException on List.of() input,
 * preventing all post-battle logic from executing. These tests verify
 * that the full win/loss pipeline fires correctly end-to-end.
 */
public class PlayersManagerNpcFightTest {

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
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        doNothing().when(messageAction).queue();
    }

    // ---- guard checks -------------------------------------------------------

    @Test
    public void fightNpc_playerNotRegistered_sendsRegistrationPromptAndNoBattle() {
        when(playerRepository.get("p1")).thenReturn(null);

        playersManager.fightNpc(event);

        verify(channel).sendMessage(contains("зарегистрируйся"));
        verify(battleManager, never()).playerBattle(any(), any(), any());
    }

    @Test
    public void fightNpc_noNpcInLocation_sendsNoNpcMessageAndNoBattle() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(null);

        playersManager.fightNpc(event);

        verify(channel).sendMessage(contains("нет NPC"));
        verify(battleManager, never()).playerBattle(any(), any(), any());
    }

    // ---- win path -----------------------------------------------------------

    @Test
    public void fightNpc_playerWins_battleDamagePersistedToCache() {
        // Regression: battleMechanic modifies local player object but doesn't save it.
        // changeMoney/changeXp re-fetch from cache → pre-battle HP overwrites damage.
        // After fix: changeHp is called after battle to persist the correct HP.
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);
        // Battle damages player: HP 100 → 4
        doAnswer(inv -> {
            List<Person> team1 = inv.getArgument(0);
            team1.get(0).setHp(4);
            List<Person> team2 = inv.getArgument(1);
            team2.get(0).setHp(0);
            return java.util.Collections.emptyList();
        }).when(battleManager).playerBattle(anyList(), anyList(), any());

        playersManager.fightNpc(event);

        // HP must reflect battle damage, not pre-battle value (100)
        assertEquals("Post-battle HP must be saved to cache", 4, player.getHp());
    }

    @Test
    public void fightNpc_playerWins_moneyAwarded() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);
        killBot(bot);

        playersManager.fightNpc(event);

        assertEquals("Money must increase by bot's reward", 80, player.getMoney());
    }

    @Test
    public void fightNpc_playerWins_xpAwarded() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        player.setExp(0);
        player.setExpToNextLvl(1000);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);
        killBot(bot);

        playersManager.fightNpc(event);

        assertTrue("XP must increase after win", player.getExp() > 0);
    }

    @Test
    public void fightNpc_playerWins_npcRespawned() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);
        killBot(bot);

        playersManager.fightNpc(event);

        verify(npcManager).respawnBot(bot);
    }

    @Test
    public void fightNpc_playerWins_sendsVictoryMessage() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);
        killBot(bot);

        playersManager.fightNpc(event);

        verify(channel, atLeastOnce()).sendMessage(contains("Победа"));
    }

    // ---- loss path ----------------------------------------------------------

    @Test
    public void fightNpc_playerLoses_moneyReducedBy10Percent() {
        Player player = player("Hero", "p1", "мейн", 100, 100, 10, 100);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);
        // bot HP stays > 0 (default) → player loses

        playersManager.fightNpc(event);

        assertEquals("10% money penalty on death", 90, player.getMoney());
    }

    @Test
    public void fightNpc_playerLoses_hpRestoredAndLocationSetToRespawn() {
        Player player = player("Hero", "p1", "мейн", 100, 5, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);

        playersManager.fightNpc(event);

        assertEquals("HP must be restored to maxHp on death", 100, player.getHp());
        assertEquals("Player must be moved to respawn", "респаун", player.getLocation());
    }

    @Test
    public void fightNpc_playerLoses_npcRespawnedEvenOnLoss() {
        Player player = player("Hero", "p1", "мейн", 100, 5, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);

        playersManager.fightNpc(event);

        // NPC must reset HP even when player loses, otherwise next fight starts mid-battle
        verify(npcManager).respawnBot(bot);
    }

    @Test
    public void fightNpc_playerLoses_sendsDefeatMessage() {
        Player player = player("Hero", "p1", "мейн", 100, 5, 10, 50);
        NpcBot bot = bot("Арк-клон", 100, 5, 30, 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(npcManager.getRandomBot("мейн")).thenReturn(bot);

        playersManager.fightNpc(event);

        verify(channel, atLeastOnce()).sendMessage(contains("победил тебя"));
    }

    // ---- helpers -----------------------------------------------------------

    /** Make the mock battleManager kill the NPC when playerBattle is called. */
    private void killBot(NpcBot bot) {
        doAnswer(inv -> {
            List<Person> team2 = inv.getArgument(1);
            team2.get(0).setHp(0);
            return Collections.emptyList();
        }).when(battleManager).playerBattle(anyList(), anyList(), any());
    }

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
        return p;
    }

    private NpcBot bot(String name, int hp, int strength, int moneyReward, int xpReward) {
        return NpcBot.builder()
                .nickName(name).hp(hp).maxHp(hp).strength(strength).armor(0)
                .moneyReward(moneyReward).xpReward(xpReward).locationName("мейн")
                .build();
    }
}
