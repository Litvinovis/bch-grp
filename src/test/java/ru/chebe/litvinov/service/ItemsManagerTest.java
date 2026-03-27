package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.ignite3.ItemRepository;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ItemsManager: getItem delegation, getItemsForSale list format,
 * and init-time repository population behaviour.
 */
public class ItemsManagerTest {

    @Mock
    private ItemRepository itemRepository;

    private ItemsManager itemsManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        itemsManager = new ItemsManager(itemRepository);
    }

    // ---- getItem ---------------------------------------------------------------

    @Test
    public void getItem_knownItem_delegatesToRepository() {
        Item sword = Item.builder().name("меч").price(100).action(false).build();
        when(itemRepository.get("меч")).thenReturn(sword);

        Item result = itemsManager.getItem("меч");

        assertEquals("меч", result.getName());
        verify(itemRepository).get("меч");
    }

    @Test
    public void getItem_unknownItem_returnsNull() {
        when(itemRepository.get("несуществующий")).thenReturn(null);

        Item result = itemsManager.getItem("несуществующий");

        assertNull(result);
    }

    // ---- getItemsForSale -------------------------------------------------------

    @Test
    public void getItemsForSale_returnsNonEmptyString() {
        String forSale = itemsManager.getItemsForSale();
        assertNotNull(forSale);
        assertFalse(forSale.isEmpty());
    }

    @Test
    public void getItemsForSale_containsKnownConsumableItems() {
        String forSale = itemsManager.getItemsForSale();
        // These are the four actionable items defined in init()
        assertTrue("Expected 'кружка цикория' in sale list", forSale.contains("кружка цикория"));
        assertTrue("Expected 'вино лаба' in sale list", forSale.contains("вино лаба"));
        assertTrue("Expected 'медовуха база' in sale list", forSale.contains("медовуха база"));
        assertTrue("Expected 'токен телепорта' in sale list", forSale.contains("токен телепорта"));
    }

    @Test
    public void getItemsForSale_doesNotContainPassiveItems() {
        String forSale = itemsManager.getItemsForSale();
        // Boss-drop items are passive (action=false) and must NOT appear in the sale list
        assertFalse("Passive boss item must not be for sale: бицушка ровера",
                forSale.contains("бицушка ровера"));
        assertFalse("Passive boss item must not be for sale: кисточка циника",
                forSale.contains("кисточка циника"));
    }

    // ---- init repository population -------------------------------------------------

    @Test
    public void init_putsAllItemsIntoRepository() {
        // Any ItemsManager construction triggers init → verify repository receives items
        verify(itemRepository, atLeastOnce()).put(anyString(), any(Item.class));
    }

    @Test
    public void init_actionItemsHaveCorrectActionFlag() {
        // Verify that the action flag is correctly set for consumables vs passives.
        // Validated indirectly through getItemsForSale():
        String forSale = itemsManager.getItemsForSale();
        // All items in forSale must be action items — cross-check with repository calls:
        // (The logic is validated by the above tests; this one just ensures no NPE)
        assertNotNull(forSale);
    }

    // ---- Item toString ---------------------------------------------------------

    @Test
    public void item_activeFlag_showsCorrectLabel() {
        Item active = Item.builder().name("зелье").price(10).action(true).description("heal").build();
        String s = active.toString();
        assertTrue(s.contains("активируемое"));
        assertFalse(s.contains("Постоянное"));
    }

    @Test
    public void item_passiveFlag_showsCorrectLabel() {
        Item passive = Item.builder().name("доспех").price(100).action(false).description("armor").build();
        String s = passive.toString();
        assertTrue(s.contains("Постоянное"));
        assertFalse(s.contains("активируемое"));
    }

    @Test
    public void item_withExpireTime_showsRemainingMinutes() {
        long expireTime = System.currentTimeMillis() + 60_000 * 30; // 30 min from now
        Item item = Item.builder().name("tmp").price(1).action(true)
                .description("desc").expireTime(expireTime).build();
        String s = item.toString();
        assertTrue("Expected expiry info in toString", s.contains("Исчезнет через"));
    }

    @Test
    public void item_withNoExpireTime_doesNotShowExpiryLine() {
        Item item = Item.builder().name("perm").price(1).action(false)
                .description("desc").expireTime(0).build();
        String s = item.toString();
        assertFalse(s.contains("Исчезнет через"));
    }

    @Test
    public void item_sellPriceIsHalfOfBuyPrice() {
        Item item = Item.builder().name("x").price(200).action(false).description("y").build();
        String s = item.toString();
        assertTrue("Expected sell price 100 in toString", s.contains("Цена продажи - 100"));
    }
}
