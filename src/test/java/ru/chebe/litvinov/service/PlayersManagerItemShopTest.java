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

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayersManager.buyItem() and sellItem().
 *
 * Both methods are integration points: they check guards, call changeMoney,
 * call addNewItem/deleteItem, and send messages. Tests verify the full chain fires.
 */
public class PlayersManagerItemShopTest {

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

    // ---- buyItem: guard checks -----------------------------------------------

    @Test
    public void buyItem_playerNotInShop_sendsLocationError() {
        Player player = player("Hero", "p1", "лес", 100);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+купить меч");

        playersManager.buyItem(event);

        verify(channel).sendMessage(contains("Таверна и Магазин"));
        verify(playerRepository, never()).put(any(), any());
    }

    @Test
    public void buyItem_itemNotFound_sendsNotFoundMessage() {
        Player player = player("Hero", "p1", "магазин", 100);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+купить несуществующий");
        when(itemsManager.getItem("несуществующий")).thenReturn(null);
        when(itemsManager.getItemsForSale()).thenReturn("...");

        playersManager.buyItem(event);

        verify(channel).sendMessage(contains("не существует"));
        verify(playerRepository, never()).put(any(), any());
    }

    @Test
    public void buyItem_passiveItem_cannotBePurchased() {
        Player player = player("Hero", "p1", "магазин", 200);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+купить артефакт");
        Item passive = item("артефакт", 50, false, 0, 0, 0, 0);
        when(itemsManager.getItem("артефакт")).thenReturn(passive);

        playersManager.buyItem(event);

        verify(channel).sendMessage(contains("нельзя купить"));
        verify(playerRepository, never()).put(any(), any());
    }

    @Test
    public void buyItem_notEnoughMoney_sendsInsufficientFundsMessage() {
        Player player = player("Hero", "p1", "магазин", 30);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+купить зелье");
        Item potion = item("зелье", 50, true, 20, 0, 0, 0);
        when(itemsManager.getItem("зелье")).thenReturn(potion);

        playersManager.buyItem(event);

        verify(channel).sendMessage(contains("недостаточно"));
        verify(playerRepository, never()).put(any(), any());
    }

    // ---- buyItem: success path -----------------------------------------------

    @Test
    public void buyItem_validPurchase_deductsMoney() {
        Player player = player("Hero", "p1", "магазин", 100);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+купить зелье");
        Item potion = item("зелье", 50, true, 20, 0, 0, 0);
        when(itemsManager.getItem("зелье")).thenReturn(potion);

        playersManager.buyItem(event);

        assertEquals("Price must be deducted from player money", 50, player.getMoney());
    }

    @Test
    public void buyItem_validPurchase_addsItemToInventory() {
        Player player = player("Hero", "p1", "магазин", 100);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+купить зелье");
        Item potion = item("зелье", 50, true, 20, 0, 0, 0);
        when(itemsManager.getItem("зелье")).thenReturn(potion);

        playersManager.buyItem(event);

        assertTrue("Item must be in inventory after purchase",
                player.getInventory().containsKey("зелье"));
    }

    // ---- sellItem: guard checks ----------------------------------------------

    @Test
    public void sellItem_itemNotInInventory_sendsNotFoundMessage() {
        Player player = player("Hero", "p1", "магазин", 50);
        // empty inventory
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+продать меч");

        playersManager.sellItem(event);

        verify(channel).sendMessage(contains("нет в твоём инвентаре"));
        assertEquals("Money must not change", 50, player.getMoney());
    }

    // ---- sellItem: success path ---------------------------------------------

    @Test
    public void sellItem_itemInInventory_addsMoney() {
        Player player = player("Hero", "p1", "магазин", 50);
        player.getInventory().put("меч", 1);
        player.setReputation(0); // sell price = price / (2 - 0) = 50 / 2 = 25
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+продать меч");
        Item sword = item("меч", 50, false, 0, 0, 0, 0);
        when(itemsManager.getItem("меч")).thenReturn(sword);

        playersManager.sellItem(event);

        assertEquals("Money must increase by sell price", 75, player.getMoney());
    }

    @Test
    public void sellItem_itemInInventory_removesFromInventory() {
        Player player = player("Hero", "p1", "магазин", 50);
        player.getInventory().put("меч", 1);
        player.setReputation(0);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+продать меч");
        Item sword = item("меч", 50, false, 0, 0, 0, 0);
        when(itemsManager.getItem("меч")).thenReturn(sword);

        playersManager.sellItem(event);

        assertFalse("Item must be removed from inventory after sell",
                player.getInventory().containsKey("меч"));
    }

    @Test
    public void sellItem_higherReputation_getsMoreMoney() {
        // reputation=10 → divisor = 2 - 10/10 = 1 → full price
        Player player = player("Hero", "p1", "магазин", 0);
        player.setReputation(10);
        player.getInventory().put("меч", 1);
        when(playerRepository.get("p1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+продать меч");
        Item sword = item("меч", 50, false, 0, 0, 0, 0);
        when(itemsManager.getItem("меч")).thenReturn(sword);

        playersManager.sellItem(event);

        assertEquals("High reputation gives full price", 50, player.getMoney());
    }

    // ---- helpers -----------------------------------------------------------

    private Player player(String nick, String id, String location, int money) {
        Player p = new Player(nick, id);
        p.setLocation(location);
        p.setMaxHp(100);
        p.setHp(100);
        p.setMoney(money);
        p.setReputation(0);
        return p;
    }

    private Item item(String name, int price, boolean action,
                      int health, int armor, int luck, int strength) {
        return Item.builder()
                .name(name).price(price).action(action)
                .health(health).armor(armor).luck(luck).strength(strength)
                .reputation(0).xpGeneration(0)
                .build();
    }
}
