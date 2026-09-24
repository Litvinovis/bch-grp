package ru.chebe.litvinov.service;

import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Player;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Регрессии экономики: цена продажи при высокой репутации, цена биржи, кулдауны.
 */
class EconomyAuditTest {

	private static Item item(String name, int price) {
		return Item.builder().name(name).price(price).action(true).build();
	}

	private static Player withReputation(int reputation) {
		Player p = new Player("Тест", "p1");
		p.setReputation(reputation);
		return p;
	}

	@Test
	void sellPrice_lowReputation_halfPrice() {
		assertEquals(50, InventoryService.sellPrice(item("меч", 100), withReputation(0)));
	}

	@Test
	void sellPrice_reputationAbove20_fullPriceInsteadOfDivisionByZero() {
		// Было: делитель 2 - 25/10 = 0 → ArithmeticException
		assertEquals(100, InventoryService.sellPrice(item("меч", 100), withReputation(25)));
	}

	@Test
	void sellPrice_reputationAbove30_neverNegative() {
		// Было: делитель 2 - 35/10 = -1 → продажа отнимала деньги
		assertEquals(100, InventoryService.sellPrice(item("меч", 100), withReputation(35)));
	}

	@Test
	void exchangePrice_alwaysBelowShopPrice_noBuySellArbitrage() {
		Item mead = item("медовуха база", 30);
		for (long day = 0; day < 400; day++) {
			int price = EconomyService.exchangePrice(mead, day * ru.chebe.litvinov.GameBalance.ONE_DAY_MS);
			assertTrue(price < mead.getPrice(), "день " + day + ": цена биржи " + price);
		}
	}

	@Test
	void exchangePrice_sameWithinDay() {
		Item wine = item("вино лаба", 15);
		long morning = 10 * ru.chebe.litvinov.GameBalance.ONE_DAY_MS + 1_000;
		long evening = morning + 20 * 60 * 60 * 1000L;
		assertEquals(EconomyService.exchangePrice(wine, morning), EconomyService.exchangePrice(wine, evening));
	}

	@Test
	void cooldown_blocksRepeatUntilPeriodPasses() {
		long[] now = {1_000_000};
		Cooldowns cooldowns = new Cooldowns(60_000, () -> now[0]);

		assertEquals(0, cooldowns.tryAcquire("p1"));
		assertEquals(60_000, cooldowns.tryAcquire("p1"));
		assertEquals(0, cooldowns.tryAcquire("p2"), "кулдаун у каждого игрока свой");

		now[0] += 60_000;
		assertEquals(0, cooldowns.tryAcquire("p1"));
	}
}
