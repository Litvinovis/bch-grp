package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.GameBalance;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Инвентарь и предметы: просмотр с пагинацией, покупка и продажа, использование
 * с наложением баффов, улучшение, сравнение, передача другому игроку и банк.
 * Вынесено из PlayersManager вместе с обслуживанием истекших баффов и предметов.
 */
public class InventoryService {

	private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

	private static final long BUFF_DURATION_MS = 30 * 60 * 1000L; // 30 минут
	private static final int INVENTORY_PAGE_SIZE = 10;

	/** Текущая страница инвентаря по игроку — состояние показа, не игровые данные. */
	private static final ConcurrentHashMap<String, Integer> inventoryPages = new ConcurrentHashMap<>();

	private final PlayerRepository playerCache;
	private final ItemsManager itemsManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;
	private final PlayerStatsService stats;
	private final QuestProgressTracker quests;
	private final java.util.Random random = new java.util.Random();

	/** Задаются после создания менеджеров — как и в PlayersManager. */
	private TerritoryManager territoryManager;
	private FactionManager factionManager;

	public void setTerritoryManager(TerritoryManager territoryManager) {
		this.territoryManager = territoryManager;
	}

	public void setFactionManager(FactionManager factionManager) {
		this.factionManager = factionManager;
	}

	public InventoryService(PlayerRepository playerCache, ItemsManager itemsManager, PlayerLocks playerLocks,
	                        AchievementService achievements, PlayerStatsService stats, QuestProgressTracker quests) {
		this.playerCache = playerCache;
		this.itemsManager = itemsManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
		this.stats = stats;
		this.quests = quests;
	}

	/**
	 * Отправляет игроку информацию об инвентаре или конкретном предмете.
	 * Если имя предмета не указано — выводит весь инвентарь.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void getInventoryInfo(MessageReceivedEvent event) {
		String id = event.getMessage().getAuthor().getId();
		var player = playerCache.get(id);
		purgeExpiredItems(id, player);
		player = playerCache.get(id);
		String itemName = event.getMessage().getContentDisplay().substring(10).trim();
		if (itemName.isEmpty()) {
			if (player.getInventory().isEmpty()) {
				event.getChannel().sendMessage("Ваш инвентарь ~~пожрал лаб~~ пуст, милорд").submit();
			} else {
				event.getChannel().sendMessage(player.inventoryInfo()).submit();
			}
		} else {
			Item item = itemsManager.getItem(itemName);
			if (item == null) {
				event.getChannel().sendMessage("Такого предмета у вас нет, посмотрите список имеющихся с помощью команды +инвентарь").submit();
			} else {
				event.getChannel().sendMessage(item.toString()).submit();
			}
		}
	}

	/**
	 * Добавляет предмет в инвентарь игрока и применяет его постоянные эффекты к характеристикам.
	 *
	 * @param id   идентификатор игрока
	 * @param item название предмета
	 */
	public void addNewItem(String id, String item) {
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return;
			Item newItem = itemsManager.getItem(item);
			if (newItem == null) return;
			player.setReputation(newItem.getReputation() > 0 ? player.getReputation() + newItem.getReputation() : player.getReputation());
			player.setHp(newItem.getHealth() > 0 ? player.getHp() + newItem.getHealth() : player.getHp());
			player.setArmor(newItem.getArmor() > 0 ? player.getArmor() + newItem.getArmor() : player.getArmor());
			player.setLuck(newItem.getLuck() > 0 ? player.getLuck() + newItem.getLuck() : player.getLuck());
			player.setStrength(newItem.getStrength() > 0 ? player.getStrength() + newItem.getStrength() : player.getStrength());
			var inventory = player.getInventory();
			if (inventory.get(item) != null) {
				inventory.put(item, inventory.get(item) + 1);
			} else {
				inventory.put(item, 1);
			}
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Удаляет предмет из инвентаря игрока и откатывает его постоянные эффекты (если предмет не активируемый).
	 *
	 * @param id   идентификатор игрока
	 * @param item название предмета
	 */
	public void deleteItem(String id, String item) {
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return;
			Item deleteItem = itemsManager.getItem(item);
			if (deleteItem == null) return;
			if (!deleteItem.isAction()) {
				player.setReputation(deleteItem.getReputation() > 0 ? player.getReputation() - deleteItem.getReputation() : player.getReputation());
				player.setHp(deleteItem.getHealth() > 0 ? player.getHp() - deleteItem.getHealth() : player.getHp());
				player.setArmor(deleteItem.getArmor() > 0 ? player.getArmor() - deleteItem.getArmor() : player.getArmor());
				player.setLuck(deleteItem.getLuck() > 0 ? player.getLuck() - deleteItem.getLuck() : player.getLuck());
				player.setStrength(deleteItem.getStrength() > 0 ? player.getStrength() - deleteItem.getStrength() : player.getStrength());
			}
			var inventory = player.getInventory();
			Integer count = inventory.get(item);
			if (count != null && count > 1) {
				inventory.put(item, count - 1);
			} else {
				inventory.remove(item);
			}
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	public void useItem(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		removeExpiredBuffs(playerId);
		var player = playerCache.get(playerId);
		String message = event.getMessage().getContentDisplay().substring(13).trim().toLowerCase();
		if (player.getInventory().containsKey(message.toLowerCase())) {
			Item item = itemsManager.getItem(message);
			if (item.getExpireTime() != 0 && item.getExpireTime() < System.currentTimeMillis()) {
				deleteItem(playerId, item.getName());
				event.getChannel().sendMessage("Предмет **" + item.getName() + "** истёк и был удалён из инвентаря").submit();
				return;
			}
			if (item.isAction()) {
				boolean hasBuff = item.getArmor() > 0 || item.getLuck() > 0
						|| item.getStrength() > 0 || item.getReputation() > 0;

				if (hasBuff) {
					ReentrantLock lock = playerLocks.get(playerId);
					lock.lock();
					try {
						Player p = playerCache.get(playerId);
						if (p == null) return;
						if (p.getActiveBuffs() == null) p.setActiveBuffs(new java.util.HashMap<>());
						// Re-check conflict inside lock so check-and-set is atomic
						String conflicting = findActiveBuffOfSameType(p, item);
						if (conflicting != null) {
							event.getChannel().sendMessage("❌ У тебя уже активен бафф **" + conflicting + "** того же типа. Дождись его окончания.").submit();
							return;
						}
						p.getActiveBuffs().put(item.getName(), System.currentTimeMillis() + BUFF_DURATION_MS);
						playerCache.put(playerId, p);
					} finally {
						lock.unlock();
					}
					event.getChannel().sendMessage("Бафф **" + item.getName() + "** активен 30 минут").submit();
				}

				if (item.getHealth() > 0) {
					int hp = stats.changeHp(playerId, item.getHealth(), true);
					event.getChannel().sendMessage("Теперь у тебя " + hp + " здоровья").submit();
				}
				if (item.getArmor() > 0) {
					int armor = stats.changeArmor(playerId, item.getArmor(), true);
					event.getChannel().sendMessage("Теперь у тебя " + armor + " брони").submit();
				}
				if (item.getLuck() > 0) {
					int luck = stats.changeLuck(playerId, item.getLuck(), true);
					event.getChannel().sendMessage("Теперь у тебя " + luck + " удачи").submit();
				}
				if (item.getStrength() > 0) {
					int str = stats.changeStrength(playerId, item.getStrength(), true);
					event.getChannel().sendMessage("Теперь у тебя " + str + " силы").submit();
				}
				if (item.getReputation() > 0) {
					int rep = stats.changeReputation(playerId, item.getReputation(), true);
					event.getChannel().sendMessage("Теперь у тебя " + rep + " репутации").submit();
				}

				deleteItem(playerId, item.getName());
			} else {
				event.getChannel().sendMessage("Этот предмет нельзя использовать").submit();
			}
		} else {
			event.getChannel().sendMessage("Такого предмета нет в твоём инвентаре").submit();
		}
	}

	/**
	 * Возвращает имя активного баффа, который буcтит ту же стату, что и newItem.
	 * Один тип стата (броня, удача, сила, репутация) — один активный бафф одновременно.
	 */
	private String findActiveBuffOfSameType(Player player, Item newItem) {
		if (player.getActiveBuffs() == null || player.getActiveBuffs().isEmpty()) return null;
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Long> entry : player.getActiveBuffs().entrySet()) {
			if (now >= entry.getValue()) continue;
			Item existing = itemsManager.getItem(entry.getKey());
			if (existing == null) continue;
			if ((newItem.getArmor() > 0 && existing.getArmor() > 0)
					|| (newItem.getLuck() > 0 && existing.getLuck() > 0)
					|| (newItem.getStrength() > 0 && existing.getStrength() > 0)
					|| (newItem.getReputation() > 0 && existing.getReputation() > 0)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** Снимает истёкшие баффы и откатывает статы игрока. */
	public void removeExpiredBuffs(String playerId) {
		ReentrantLock lock = playerLocks.get(playerId);
		lock.lock();
		try {
			Player player = playerCache.get(playerId);
			if (player == null || player.getActiveBuffs() == null || player.getActiveBuffs().isEmpty()) return;
			long now = System.currentTimeMillis();
			List<String> expired = new ArrayList<>();
			for (Map.Entry<String, Long> entry : player.getActiveBuffs().entrySet()) {
				if (now >= entry.getValue()) expired.add(entry.getKey());
			}
			if (expired.isEmpty()) return;
			for (String buffName : expired) {
				Item item = itemsManager.getItem(buffName);
				if (item != null) {
					if (item.getArmor() > 0)      player.setArmor(player.getArmor() - item.getArmor());
					if (item.getLuck() > 0)        player.setLuck(player.getLuck() - item.getLuck());
					if (item.getStrength() > 0)    player.setStrength(player.getStrength() - item.getStrength());
					if (item.getReputation() > 0)  player.setReputation(player.getReputation() - item.getReputation());
				}
				player.getActiveBuffs().remove(buffName);
			}
			playerCache.put(playerId, player);
			log.debug("Сняты баффы у {}: {}", playerId, expired);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Продаёт предмет из инвентаря игрока. Цена продажи зависит от репутации игрока.
	 *
	 * @param event событие Discord-сообщения с названием продаваемого предмета
	 */
	public void sellItem(MessageReceivedEvent event) {
		ReentrantLock lock = playerLocks.get(event.getAuthor().getId());
		lock.lock();
		try {
			var player = playerCache.get(event.getAuthor().getId());
			String message = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
			if (player.getInventory().containsKey(message.toLowerCase())) {
				Item item = itemsManager.getItem(message);
				int money = stats.changeMoney(player.getId(), item.getPrice() / (2 - player.getReputation() / 10), true);
				deleteItem(player.getId(), item.getName());
				event.getChannel().sendMessage("Теперь у тебя " + money + " денег").submit();
			} else {
				event.getChannel().sendMessage("Такого предмета нет в твоём инвентаре").submit();
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Обрабатывает покупку предмета игроком. Доступно только в локациях «магазин» и «таверна».
	 *
	 * @param event событие Discord-сообщения с названием предмета
	 */
	public void buyItem(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(7).trim().toLowerCase();
		Player player = playerCache.get(event.getAuthor().getId());
		if (player.getLocation().equalsIgnoreCase("магазин") || player.getLocation().equalsIgnoreCase("таверна")) {
			Item item = itemsManager.getItem(message);
			if (item == null) {
				event.getChannel().sendMessage("Такого предмета не существует, ты можешь купить следующие предметы - " + itemsManager.getItemsForSale() +
								", набери +предмет (название) чтобы узнать его характеристики").submit();
			} else if (!item.isAction()) {
				event.getChannel().sendMessage("Этот предмет нельзя купить").submit();
			} else {
				int price = item.getPrice();
				ReentrantLock lock = playerLocks.get(player.getId());
				lock.lock();
				try {
					player = playerCache.get(player.getId());
					if (player.getMoney() < price) {
						event.getChannel().sendMessage("У вас недостаточно денег для покупки этого предмета").submit();
						return;
					}
					int moneyLeft = stats.changeMoney(player.getId(), price, false);
					addNewItem(player.getId(), item.getName());
					event.getChannel().sendMessage("Вы купили " + item.getName() + " у вас осталось " + moneyLeft + " денег").submit();
				} finally {
					lock.unlock();
				}
				if (territoryManager != null) territoryManager.collectTax(player, price);
				if (factionManager != null) factionManager.addRep(player.getId(), "ТОРГОВЦЫ", 1);
			}
		} else {
			event.getChannel().sendMessage("Покупать предметы можно только в локациях Таверна и Магазин").submit();
		}
	}

	/** +улучшить [предмет] — улучшает предмет за 200 монет (37) */
	public void upgradeItem(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String itemName = event.getMessage().getContentDisplay().substring(9).trim().toLowerCase();
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (!player.getInventory().containsKey(itemName)) {
				event.getChannel().sendMessage("Такого предмета нет в вашем инвентаре.").submit();
				return;
			}
			if (player.getMoney() < GameBalance.ITEM_UPGRADE_COST) {
				event.getChannel().sendMessage("Недостаточно монет (нужно " + GameBalance.ITEM_UPGRADE_COST + ").").submit();
				return;
			}
			player.setMoney(player.getMoney() - GameBalance.ITEM_UPGRADE_COST);
			int statIdx = random.nextInt(4);
			String statName;
			switch (statIdx) {
				case 0 -> { player.setStrength(player.getStrength() + 1); statName = "⚔️ Сила"; }
				case 1 -> { player.setArmor(player.getArmor() + 1); statName = "🛡️ Броня"; }
				case 2 -> { player.setLuck(player.getLuck() + 1); statName = "🍀 Удача"; }
				default -> { player.setMaxHp(player.getMaxHp() + 10); statName = "❤️ HP"; }
			}
			playerCache.put(id, player);
			event.getChannel().sendMessage("✨ Предмет **" + itemName + "** улучшен! +" + statName + " (стоимость: " + GameBalance.ITEM_UPGRADE_COST + " монет)").submit();
		} finally {
			lock.unlock();
		}
	}

	/** +сравнить [предмет1] с [предмет2] — сравнение предметов (38) */
	public void compareItems(MessageReceivedEvent event) {
		String raw = event.getMessage().getContentDisplay().substring(9).trim().toLowerCase();
		String[] parts = raw.split("\\s+с\\s+", 2);
		if (parts.length < 2) {
			event.getChannel().sendMessage("Использование: +сравнить [предмет1] с [предмет2]").submit();
			return;
		}
		ru.chebe.litvinov.data.Item item1 = itemsManager.getItem(parts[0].trim());
		ru.chebe.litvinov.data.Item item2 = itemsManager.getItem(parts[1].trim());
		if (item1 == null || item2 == null) {
			event.getChannel().sendMessage("Один из предметов не найден.").submit();
			return;
		}
		var sb = new StringBuilder("⚖️ **Сравнение:** " + item1.getName() + " vs " + item2.getName() + "\n");
		sb.append(diffLine("❤️ HP", item1.getHealth(), item2.getHealth()));
		sb.append(diffLine("⚔️ Сила", item1.getStrength(), item2.getStrength()));
		sb.append(diffLine("🛡️ Броня", item1.getArmor(), item2.getArmor()));
		sb.append(diffLine("🍀 Удача", item1.getLuck(), item2.getLuck()));
		sb.append(diffLine("⭐ Репутация", item1.getReputation(), item2.getReputation()));
		sb.append(diffLine("✨ XP", item1.getXpGeneration(), item2.getXpGeneration()));
		sb.append(diffLine("💰 Цена", item1.getPrice(), item2.getPrice()));
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/**
	 * Передаёт предмет другому зарегистрированному игроку.
	 * Синтаксис: +передать @игрок предмет [количество]
	 */
	public void tradeItem(MessageReceivedEvent event) {
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите игрока: +передать @игрок предмет [количество]").submit();
			return;
		}
		net.dv8tion.jda.api.entities.User targetUser = mentions.get(0);
		String senderId = event.getAuthor().getId();
		String receiverId = targetUser.getId();

		if (senderId.equals(receiverId)) {
			event.getChannel().sendMessage("Нельзя передать предмет самому себе.").submit();
			return;
		}
		if (!playerCache.contains(receiverId)) {
			event.getChannel().sendMessage("Игрок " + targetUser.getName() + " не зарегистрирован в игре.").submit();
			return;
		}

		// Извлекаем имя предмета и количество из raw-контента
		String raw = event.getMessage().getContentRaw();
		int mentionEnd = raw.indexOf('>') + 1;
		if (mentionEnd <= 0) {
			event.getChannel().sendMessage("Укажите предмет: +передать @игрок предмет [количество]").submit();
			return;
		}
		String rest = raw.substring(mentionEnd).trim();
		if (rest.isEmpty()) {
			event.getChannel().sendMessage("Укажите предмет: +передать @игрок предмет [количество]").submit();
			return;
		}

		String[] parts = rest.split("\\s+");
		int quantity = 1;
		String itemName;
		if (parts.length > 1) {
			try {
				quantity = Integer.parseInt(parts[parts.length - 1]);
				itemName = rest.substring(0, rest.lastIndexOf(parts[parts.length - 1])).trim().toLowerCase();
			} catch (NumberFormatException e) {
				itemName = rest.toLowerCase();
			}
		} else {
			itemName = rest.toLowerCase();
		}
		if (quantity <= 0) {
			event.getChannel().sendMessage("Количество должно быть больше нуля.").submit();
			return;
		}

		// Блокировки в фиксированном порядке во избежание дедлока
		boolean senderFirst = senderId.compareTo(receiverId) < 0;
		ReentrantLock first = senderFirst ? playerLocks.get(senderId) : playerLocks.get(receiverId);
		ReentrantLock second = senderFirst ? playerLocks.get(receiverId) : playerLocks.get(senderId);
		first.lock();
		try {
			second.lock();
			try {
				Player sender = playerCache.get(senderId);
				Player receiver = playerCache.get(receiverId);
				Map<String, Integer> senderInv = sender.getInventory();
				int have = senderInv.getOrDefault(itemName, 0);
				if (have < quantity) {
					event.getChannel().sendMessage("У вас недостаточно предмета \"" + itemName + "\" (есть: " + have + ").").submit();
					return;
				}
				if (have == quantity) {
					senderInv.remove(itemName);
				} else {
					senderInv.put(itemName, have - quantity);
				}
				Map<String, Integer> receiverInv = receiver.getInventory();
				receiverInv.put(itemName, receiverInv.getOrDefault(itemName, 0) + quantity);
				achievements.unlock(sender, "торговец");
				// Обе стороны сохраняются одной транзакцией: иначе предмет исчезал
				// у отправителя, не появившись у получателя
				playerCache.putBoth(senderId, sender, receiverId, receiver);
				event.getChannel().sendMessage("Вы передали " + quantity + "x " + itemName + " игроку " + targetUser.getName() + ".").submit();
				// DM уведомление получателю (81)
				final String finalItemName = itemName;
				final int finalQuantity = quantity;
				try {
					targetUser.openPrivateChannel().submit()
							.thenAccept(ch -> ch.sendMessage("🎁 Вам передан предмет **" + finalItemName + "** ×" + finalQuantity + " от **" + event.getAuthor().getName() + "**!").submit());
				} catch (Exception ignored) {}
			} finally {
				second.unlock();
			}
		} finally {
			first.unlock();
		}
	}

	/** +банк — показывает банк или выполняет операцию (35) */
	public void bankCommand(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		String raw = event.getMessage().getContentDisplay().substring(5).trim().toLowerCase();
		if (raw.isEmpty()) {
			Map<String, Integer> bank = player.getBankInventory();
			if (bank == null || bank.isEmpty()) {
				event.getChannel().sendMessage("🏦 Ваш банк пуст.").submit();
			} else {
				var sb = new StringBuilder("🏦 **Банк " + player.getNickName() + "**\n");
				bank.entrySet().stream().sorted(Map.Entry.comparingByKey())
						.forEach(e -> sb.append(ru.chebe.litvinov.data.Player.itemIcon(e.getKey()))
								.append(" ").append(e.getKey()).append(" — ×").append(e.getValue()).append("\n"));
				event.getChannel().sendMessage(sb.toString().stripTrailing()).submit();
			}
		} else if (raw.startsWith("положить ")) {
			String itemName = raw.substring(9).trim();
			ReentrantLock lock = playerLocks.get(id);
			lock.lock();
			try {
				Player p = playerCache.get(id);
				if (!p.getInventory().containsKey(itemName)) {
					event.getChannel().sendMessage("Такого предмета нет в инвентаре.").submit();
					return;
				}
				if (p.getBankInventory() == null) p.setBankInventory(new java.util.HashMap<>());
				if (p.getBankInventory().size() >= 20) {
					event.getChannel().sendMessage("Банк заполнен (максимум 20 предметов).").submit();
					return;
				}
				Integer count = p.getInventory().get(itemName);
				if (count != null && count > 1) p.getInventory().put(itemName, count - 1);
				else p.getInventory().remove(itemName);
				p.getBankInventory().merge(itemName, 1, Integer::sum);
				playerCache.put(id, p);
				event.getChannel().sendMessage("✅ Предмет **" + itemName + "** помещён в банк.").submit();
			} finally {
				lock.unlock();
			}
		} else if (raw.startsWith("взять ")) {
			String itemName = raw.substring(6).trim();
			ReentrantLock lock = playerLocks.get(id);
			lock.lock();
			try {
				Player p = playerCache.get(id);
				if (p.getBankInventory() == null || !p.getBankInventory().containsKey(itemName)) {
					event.getChannel().sendMessage("Такого предмета нет в банке.").submit();
					return;
				}
				Integer count = p.getBankInventory().get(itemName);
				if (count != null && count > 1) p.getBankInventory().put(itemName, count - 1);
				else p.getBankInventory().remove(itemName);
				p.getInventory().merge(itemName, 1, Integer::sum);
				playerCache.put(id, p);
				event.getChannel().sendMessage("✅ Предмет **" + itemName + "** перемещён из банка в инвентарь.").submit();
			} finally {
				lock.unlock();
			}
		} else {
			event.getChannel().sendMessage("Использование: +банк / +банк положить [предмет] / +банк взять [предмет]").submit();
		}
	}

	/** Удаляет истёкшие предметы из инвентаря игрока. */
	private void purgeExpiredItems(String playerId, Player player) {
		if (player == null || player.getInventory() == null) return;
		long now = System.currentTimeMillis();
		List<String> toRemove = player.getInventory().keySet().stream()
				.filter(name -> {
					Item item = itemsManager.getItem(name);
					return item != null && item.getExpireTime() != 0 && item.getExpireTime() < now;
				})
				.collect(Collectors.toList());
		if (!toRemove.isEmpty()) {
			toRemove.forEach(name -> deleteItem(playerId, name));
		}
	}

	private String diffLine(String stat, int v1, int v2) {
		int diff = v1 - v2;
		String sign = diff > 0 ? "+" : "";
		return String.format("%s: %d vs %d (%s%d)\n", stat, v1, v2, sign, diff);
	}
}
