package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.GameBalance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Экономика вне боёв: крафт, лавка торговца, кредиты банка,
 * обменник и продажа ресурсов.
 */
public class EconomyService {

	private static final Logger log = LoggerFactory.getLogger(EconomyService.class);

	private final PlayerRepository playerCache;
	private final ItemsManager itemsManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;
	private final InventoryService inventory;

	public EconomyService(PlayerRepository playerCache, ItemsManager itemsManager, PlayerLocks playerLocks,
	                      AchievementService achievements, InventoryService inventory) {
		this.playerCache = playerCache;
		this.itemsManager = itemsManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
		this.inventory = inventory;
	}
	private static final Map<String, Map<String, Integer>> CRAFT_RECIPES = Map.of(
		"зелье силы", Map.of("кружка цикория", 2, "вино лаба", 1),
		"боевой эликсир", Map.of("зелье лаба", 1, "протеин ябыса", 1),
		"счастливый амулет", Map.of("амулет рианель", 1, "шарики лаба", 1)
	);

	/** +крафт — список рецептов / +крафт [предмет] — создание (39) */
	public void craftItem(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String arg = event.getMessage().getContentDisplay().substring(6).trim().toLowerCase();
		if (arg.isEmpty()) {
			var sb = new StringBuilder("⚒️ **Рецепты крафта:**\n");
			CRAFT_RECIPES.forEach((result, ingredients) -> {
				sb.append("• **").append(result).append("**: ");
				ingredients.forEach((mat, qty) -> sb.append(qty).append("x ").append(mat).append(", "));
				sb.setLength(sb.length() - 2);
				sb.append("\n");
			});
			event.getChannel().sendMessage(sb.toString()).submit();
			return;
		}
		Map<String, Integer> recipe = CRAFT_RECIPES.get(arg);
		if (recipe == null) {
			event.getChannel().sendMessage("Рецепт **" + arg + "** не найден. Введи +крафт для списка рецептов.").submit();
			return;
		}
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			for (Map.Entry<String, Integer> e : recipe.entrySet()) {
				if (player.getInventory().getOrDefault(e.getKey(), 0) < e.getValue()) {
					event.getChannel().sendMessage("❌ Недостаточно **" + e.getKey() + "** (нужно: " + e.getValue() + ", есть: " + player.getInventory().getOrDefault(e.getKey(), 0) + ")").submit();
					return;
				}
			}
			recipe.forEach((mat, qty) -> {
				int have = player.getInventory().get(mat);
				if (have <= qty) player.getInventory().remove(mat);
				else player.getInventory().put(mat, have - qty);
			});
			player.getInventory().merge(arg, 1, Integer::sum);
			playerCache.put(id, player);
			event.getChannel().sendMessage("✅ Создан предмет: **" + arg + "**!").submit();
		} finally {
			lock.unlock();
		}
	}

	/** +торговец — случайные предметы в локации (42) */
	public void merchantShop(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		List<String> merchantItems = itemsManager.getMerchantItems(player.getLocation());
		String discountItem = itemsManager.getSeasonalDiscountItem();
		var sb = new StringBuilder("🛒 **Торговец в " + player.getLocation() + "**\n");
		for (String name : merchantItems) {
			ru.chebe.litvinov.data.Item item = itemsManager.getItem(name);
			if (item == null) continue;
			int price = item.getPrice();
			if (name.equals(discountItem)) {
				price = price / 2;
				sb.append("• **").append(name).append("** — ").append(price).append(" монет 🏷️ Скидка 50%!\n");
			} else {
				sb.append("• **").append(name).append("** — ").append(price).append(" монет\n");
			}
		}
		sb.append("Купить: +купить [предмет]");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +кредит [amount] — кредит из таверны (63) */
	public void takeCredit(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String raw = event.getMessage().getContentDisplay().substring(8).trim();
		int amount;
		try {
			amount = Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Укажите сумму кредита: +кредит [сумма]").submit();
			return;
		}
		if (amount <= 0 || amount > GameBalance.CREDIT_MAX) {
			event.getChannel().sendMessage("Сумма кредита от 1 до " + GameBalance.CREDIT_MAX + " монет.").submit();
			return;
		}
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getDebt() > 0) {
				event.getChannel().sendMessage("У вас уже есть долг: **" + player.getDebt() + "** монет. Сначала погасите его (+погасить).").submit();
				return;
			}
			player.setMoney(player.getMoney() + amount);
			player.setDebt(amount);
			playerCache.put(id, player);
			event.getChannel().sendMessage("💳 Вы взяли кредит **" + amount + "** монет. Долг: **" + amount + "** (5% в день).").submit();
		} finally {
			lock.unlock();
		}
	}

	/** +погасить — погасить кредит (63) */
	public void repayCredit(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getDebt() <= 0) {
				event.getChannel().sendMessage("У вас нет долга.").submit();
				return;
			}
			int debt = player.getDebt();
			int total = (int) (debt * 1.05);
			if (player.getMoney() < total) {
				event.getChannel().sendMessage("Недостаточно монет для погашения долга **" + total + "** (долг " + debt + " + 5% процентов).").submit();
				return;
			}
			player.setMoney(player.getMoney() - total);
			player.setDebt(0);
			playerCache.put(id, player);
			event.getChannel().sendMessage("✅ Долг погашен! Уплачено **" + total + "** монет (включая проценты).").submit();
		} finally {
			lock.unlock();
		}
	}

	/** +биржа — текущие цены на ресурсы (69); +продать ресурс [предмет] [qty] */
	public void exchangeInfo(MessageReceivedEvent event) {
		var sb = new StringBuilder("📊 **Биржа ресурсов** (цены меняются ±20% каждый день)\n\n");
		int seed = (int)(System.currentTimeMillis() / GameBalance.ONE_DAY_MS);
		Random rng = new Random(seed);
		String[] resources = {"кружка цикория", "вино лаба", "медовуха база", "протеин ябыса"};
		for (String res : resources) {
			ru.chebe.litvinov.data.Item item = itemsManager.getItem(res);
			if (item == null) continue;
			double factor = 0.8 + rng.nextDouble() * 0.4;
			int price = Math.max(1, (int)(item.getPrice() * factor));
			sb.append("• **").append(res).append("** — ").append(price).append(" монет\n");
		}
		sb.append("\nПродать: +продать ресурс [предмет] [количество]");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	public void sellResource(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String raw = event.getMessage().getContentDisplay().substring(16).trim().toLowerCase();
		String[] parts = raw.split("\\s+");
		if (parts.length < 2) {
			event.getChannel().sendMessage("Использование: +продать ресурс [предмет] [количество]").submit();
			return;
		}
		int qty;
		try {
			qty = Integer.parseInt(parts[parts.length - 1]);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Укажите количество.").submit();
			return;
		}
		if (qty <= 0) {
			event.getChannel().sendMessage("Количество должно быть больше нуля.").submit();
			return;
		}
		String itemName = raw.substring(0, raw.lastIndexOf(parts[parts.length - 1])).trim();
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			int have = player.getInventory().getOrDefault(itemName, 0);
			if (have < qty) {
				event.getChannel().sendMessage("Недостаточно **" + itemName + "** (есть: " + have + ").").submit();
				return;
			}
			ru.chebe.litvinov.data.Item item = itemsManager.getItem(itemName);
			if (item == null) {
				event.getChannel().sendMessage("Предмет не найден на бирже.").submit();
				return;
			}
			int seed = (int)(System.currentTimeMillis() / GameBalance.ONE_DAY_MS);
			double factor = 0.8 + new Random(seed + itemName.hashCode()).nextDouble() * 0.4;
			int price = Math.max(1, (int)(item.getPrice() * factor));
			int total = price * qty;
			if (have == qty) player.getInventory().remove(itemName);
			else player.getInventory().put(itemName, have - qty);
			player.setMoney(player.getMoney() + total);
			playerCache.put(id, player);
			event.getChannel().sendMessage("💱 Продано **" + qty + "x " + itemName + "** за **" + total + "** монет.").submit();
			achievements.checkRich(player);
		} finally {
			lock.unlock();
		}
	}
}
