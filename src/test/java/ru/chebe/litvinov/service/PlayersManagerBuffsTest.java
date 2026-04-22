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
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.useItem() and removeExpiredBuffs().
 *
 * The buff system is entirely untested: applying stats, registering 30-min expiry,
 * and rolling back stats when a buff expires. A broken rollback would silently
 * leave ghost stats on players indefinitely.
 */
public class PlayersManagerBuffsTest {

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

    // ---- useItem: guard checks -----------------------------------------------

    @Test
    public void useItem_itemNotInInventory_sendsNoItemMessage() {
        Player player = player("Hero", "p1", 100, 0, 0, 0);
        // inventory is empty
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+использовать зелье");

        playersManager.useItem(event);

        verify(channel).sendMessage(contains("нет в твоём инвентаре"));
        verify(playerRepository, never()).put(eq("p1"), any());
    }

    @Test
    public void useItem_passiveItemInInventory_sendsCannotUseMessage() {
        Player player = player("Hero", "p1", 50, 0, 0, 0);
        player.getInventory().put("камень", 1);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+использовать камень");
        Item passive = item("камень", false, 20, 0, 0, 0);
        when(itemsManager.getItem("камень")).thenReturn(passive);

        playersManager.useItem(event);

        verify(channel).sendMessage(contains("нельзя использовать"));
    }

    // ---- useItem: HP item ---------------------------------------------------

    @Test
    public void useItem_hpItem_restoresHp() {
        Player player = player("Hero", "p1", 50, 0, 0, 0);
        player.setMaxHp(100);
        player.setHp(60);
        player.getInventory().put("зелье", 1);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+использовать зелье");
        Item potion = item("зелье", true, 30, 0, 0, 0); // +30 HP
        when(itemsManager.getItem("зелье")).thenReturn(potion);

        playersManager.useItem(event);

        assertEquals("HP must increase by item health value", 90, player.getHp());
    }

    @Test
    public void useItem_hpItem_deletedFromInventoryAfterUse() {
        Player player = player("Hero", "p1", 50, 0, 0, 0);
        player.setMaxHp(100);
        player.setHp(60);
        player.getInventory().put("зелье", 1);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+использовать зелье");
        Item potion = item("зелье", true, 30, 0, 0, 0);
        when(itemsManager.getItem("зелье")).thenReturn(potion);

        playersManager.useItem(event);

        assertFalse("Consumable must be removed from inventory after use",
                player.getInventory().containsKey("зелье"));
    }

    // ---- useItem: buff item -------------------------------------------------

    @Test
    public void useItem_armorBuff_increasesArmor() {
        Player player = player("Hero", "p1", 50, 5, 0, 0);
        player.getInventory().put("щит", 1);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+использовать щит");
        Item shield = itemWithArmor("щит", true, 3); // +3 armor buff
        when(itemsManager.getItem("щит")).thenReturn(shield);

        playersManager.useItem(event);

        assertEquals("Armor must increase by item armor value", 8, player.getArmor());
    }

    @Test
    public void useItem_buffItem_registersExpiryInActiveBuffs() {
        Player player = player("Hero", "p1", 50, 5, 0, 0);
        player.getInventory().put("щит", 1);
        if (player.getActiveBuffs() == null) player.setActiveBuffs(new HashMap<>());
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+использовать щит");
        Item shield = itemWithArmor("щит", true, 3);
        when(itemsManager.getItem("щит")).thenReturn(shield);

        long before = System.currentTimeMillis();
        playersManager.useItem(event);
        long after = System.currentTimeMillis();

        assertTrue("Active buff must be registered", player.getActiveBuffs().containsKey("щит"));
        long expiry = player.getActiveBuffs().get("щит");
        assertTrue("Buff expiry must be ~30 min in future",
                expiry >= before + 29 * 60 * 1000L && expiry <= after + 31 * 60 * 1000L);
    }

    // ---- removeExpiredBuffs -------------------------------------------------

    @Test
    public void removeExpiredBuffs_expiredBuff_rollsBackArmor() {
        Player player = player("Hero", "p1", 50, 8, 0, 0); // current armor = 8
        player.setActiveBuffs(new HashMap<>());
        // Buff expired 1 second ago
        player.getActiveBuffs().put("щит", System.currentTimeMillis() - 1000);
        when(playerRepository.get("p1")).thenReturn(player);
        Item shield = itemWithArmor("щит", true, 3); // buff gave +3 armor
        when(itemsManager.getItem("щит")).thenReturn(shield);

        playersManager.removeExpiredBuffs("p1");

        assertEquals("Armor must be rolled back after buff expires", 5, player.getArmor());
        assertFalse("Expired buff must be removed from activeBuffs",
                player.getActiveBuffs().containsKey("щит"));
    }

    @Test
    public void removeExpiredBuffs_activeBuff_keepsStats() {
        Player player = player("Hero", "p1", 50, 8, 0, 0);
        player.setActiveBuffs(new HashMap<>());
        // Buff expires 30 min from now
        player.getActiveBuffs().put("щит", System.currentTimeMillis() + 30 * 60 * 1000L);
        when(playerRepository.get("p1")).thenReturn(player);

        playersManager.removeExpiredBuffs("p1");

        assertEquals("Active buff must not change armor", 8, player.getArmor());
        assertTrue("Active buff must remain in activeBuffs",
                player.getActiveBuffs().containsKey("щит"));
    }

    @Test
    public void removeExpiredBuffs_noBuffs_doesNothing() {
        Player player = player("Hero", "p1", 50, 5, 0, 0);
        // no activeBuffs set
        when(playerRepository.get("p1")).thenReturn(player);

        // Should not throw
        playersManager.removeExpiredBuffs("p1");

        assertEquals("Stats unchanged when no buffs", 5, player.getArmor());
    }

    @Test
    public void removeExpiredBuffs_nullPlayer_doesNotThrow() {
        when(playerRepository.get("unknown")).thenReturn(null);

        playersManager.removeExpiredBuffs("unknown"); // must not throw
    }

    // ---- helpers -----------------------------------------------------------

    private Player player(String nick, String id, int hp, int armor, int luck, int strength) {
        Player p = new Player(nick, id);
        p.setMaxHp(100);
        p.setHp(hp);
        p.setArmor(armor);
        p.setLuck(luck);
        p.setStrength(strength);
        p.setMoney(50);
        return p;
    }

    /** Action item with health bonus only (no buff). */
    private Item item(String name, boolean action, int health, int armor, int luck, int strength) {
        return Item.builder()
                .name(name).price(10).action(action)
                .health(health).armor(armor).luck(luck).strength(strength)
                .reputation(0).xpGeneration(0)
                .build();
    }

    /** Action item with armor buff (no health, triggers hasBuff=true path). */
    private Item itemWithArmor(String name, boolean action, int armor) {
        return Item.builder()
                .name(name).price(10).action(action)
                .health(0).armor(armor).luck(0).strength(0)
                .reputation(0).xpGeneration(0)
                .build();
    }
}
