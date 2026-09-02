package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.data.DailyQuest;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.GameBalance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * События и квесты: выдача и смена случайного события, его проверка,
 * ежедневный бонус со стриком, список ежедневных квестов, журнал и скрытые квесты.
 */
public class QuestEventService {

	private static final Map<String, String> HIDDEN_QUESTS = Map.ofEntries(
		Map.entry("открою тайну", "Ты нашёл тайную тропу! +50 XP"),
		Map.entry("чего это за дверь", "Скрытая комната! +100 монет"),
		Map.entry("что здесь происходит", "Странное место... +30 XP"),
		Map.entry("найди меня", "Кто-то тебя ждал! +50 монет"),
		Map.entry("секрет сервера", "Ты раскрыл секрет! +75 XP"),
		Map.entry("цикорий навсегда", "Ты один из настоящих! +60 XP + кружка цикория"),
		Map.entry("ровер лучший", "Лесть работает. +80 монет"),
		Map.entry("хочу чебурек", "В это место не завезли еду. +40 XP"),
		Map.entry("где тут касса", "Касса закрыта навсегда. +50 монет"),
		Map.entry("что посоветуешь", "Посоветую бросить это всё. +100 XP")
	);

	private static final Logger log = LoggerFactory.getLogger(QuestEventService.class);

	private final PlayerRepository playerCache;
	private final EventsManager eventsManager;
	private final LocationManager locationManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;
	private final PlayerStatsService stats;
	private final QuestProgressTracker questTracker;
	private final InventoryService inventory;

	/** Задаются после создания менеджеров. */
	private FactionManager factionManager;

	/** Задаётся после создания менеджеров. */
	private DailyQuestService dailyQuestService;

	public QuestEventService(PlayerRepository playerCache, EventsManager eventsManager, LocationManager locationManager,
	                         PlayerLocks playerLocks, AchievementService achievements,
	                         PlayerStatsService stats, QuestProgressTracker questTracker,
	                         InventoryService inventory) {
		this.playerCache = playerCache;
		this.eventsManager = eventsManager;
		this.locationManager = locationManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
		this.stats = stats;
		this.questTracker = questTracker;
		this.inventory = inventory;
	}

	public void setFactionManager(FactionManager factionManager) {
		this.factionManager = factionManager;
	}

	public void setDailyQuestService(DailyQuestService dailyQuestService) {
		this.dailyQuestService = dailyQuestService;
	}

	/**
	 * Назначает игроку новый случайный квест.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void assignEvent(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		var player = playerCache.get(playerId);
		if (player == null) {
			event.getChannel().sendMessage("Сначала зарегистрируйся командой +начать").submit();
			return;
		}
		if (player.getActiveEvent() != null) {
			event.getChannel().sendMessage("У тебя уже есть активный квест, сначала заверши его").submit();
		} else {
			Event newEvent = eventsManager.assignEvent(locationManager.getLocationList());
			log.debug("Выдан новый квест игроку {}: {}", playerId, newEvent);
			player.setActiveEvent(newEvent);
			event.getChannel().sendMessage("Ты получил новое задание :\n" + player.getActiveEvent().toString()).submit();
			playerCache.put(playerId, player);
			log.debug("Игрок {} сохранён с активным квестом", playerId);
		}
	}

	/**
	 * Заменяет текущий квест игрока на новый за 5 монет.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void changeEvent(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		var player = playerCache.get(playerId);
		if (player == null) {
			event.getChannel().sendMessage("Сначала зарегистрируйся командой +начать").submit();
			return;
		}
		if (player.getActiveEvent() == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
		} else if (player.getMoney() >= GameBalance.QUEST_CHANGE_FEE) {
			player.setActiveEvent(eventsManager.assignEvent(locationManager.getLocationList()));
			stats.changeMoney(playerId, GameBalance.QUEST_CHANGE_FEE, false);
			event.getChannel().sendMessage("Ты потратил " + GameBalance.QUEST_CHANGE_FEE + " монет и получил новое задание :\n" + player.getActiveEvent().toString()).submit();
			playerCache.put(playerId, player);
		} else {
			event.getChannel().sendMessage("У тебя недостаточно денег, сначала зарабаотай их").submit();
		}
	}

	/**
	 * Проверяет выполнение условия активного квеста.
	 * При успехе начисляет награду и снимает квест.
	 *
	 * @param event событие Discord-сообщения с ответом игрока (для квестов-загадок)
	 */
	public void checkEvent(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		var player = playerCache.get(playerId);
		if (player == null) {
			event.getChannel().sendMessage("Сначала зарегистрируйся командой +начать").submit();
			return;
		}
		String content = event.getMessage().getContentDisplay();
		String message = content.length() > 16 ? content.substring(16).trim().toLowerCase() : "";
		player.setAnswer(message);
		var activeEvent = player.getActiveEvent();
		if (activeEvent == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
			return;
		}
		
		boolean isCompleted = eventsManager.checkEvent(activeEvent, player);
		if (isCompleted) {
			// Добавляем квест в журнал (48)
			if (player.getCompletedQuests() == null) player.setCompletedQuests(new ArrayList<>());
			player.getCompletedQuests().add(activeEvent.getDescription());

			player.setActiveEvent(null);
			playerCache.put(playerId, player);
			stats.changeMoney(playerId, activeEvent.getMoneyReward(), true);
			stats.changeXp(playerId, activeEvent.getXpReward());
			questTracker.progress(playerId, "EARN_GOLD", activeEvent.getMoneyReward());
			if (factionManager != null) factionManager.addRep(playerId, "МАГИ", 2);
			StringBuilder reward = new StringBuilder("Ты успешно завершил свой квест! Опыт: ")
					.append(activeEvent.getXpReward()).append(", монеты: ").append(activeEvent.getMoneyReward());
			String itemReward = activeEvent.getItemReward();
			if (itemReward != null && !itemReward.isBlank()) {
				inventory.addNewItem(playerId, itemReward);
				reward.append(", предмет: **").append(itemReward).append("**");
			}
			event.getChannel().sendMessage(reward.toString()).submit();
		} else {
			event.getChannel().sendMessage("Ты не выполнил условия квеста или ответил неправильно!").submit();
		}
	}

	/**
	 * Начисляет игроку ежедневный бонус (100 монет) с учётом стрика.
	 * 3 дня подряд — +50 бонус; 7 дней — редкий предмет.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void dailyBonus(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			long now = System.currentTimeMillis();
			if (player.getDailyTime() < now - GameBalance.ONE_DAY_MS) {
				if (player.getDailyTime() == 0 || player.getDailyTime() < now - GameBalance.TWO_DAYS_MS) {
					player.setDailyStreak(1);
				} else {
					player.setDailyStreak(player.getDailyStreak() + 1);
				}
				int streak = player.getDailyStreak();
				player.setDailyTime(now);
				int dailyBonus = GameBalance.DAILY_BONUS_BASE + player.getLevel() * GameBalance.DAILY_BONUS_PER_LEVEL;
				player.setMoney(player.getMoney() + dailyBonus);

				StringBuilder msg = new StringBuilder("Вы получили ежедневный бонус " + dailyBonus + " монет! (Стрик: " + streak + " дн.)");
				if (streak == 3) {
					player.setMoney(player.getMoney() + GameBalance.DAILY_STREAK_3_BONUS);
					msg.append("\n Стрик 3 дня! Бонус +" + GameBalance.DAILY_STREAK_3_BONUS + " монет!");
					achievements.unlock(player, "стрик_3");
				}
				if (streak % GameBalance.DAILY_STREAK_RARE_ITEM_INTERVAL == 0 && streak > 0) {
					String rareItem = GameBalance.DAILY_STREAK_RARE_ITEM;
					Map<String, Integer> inv = player.getInventory();
					inv.put(rareItem, inv.getOrDefault(rareItem, 0) + 1);
					msg.append("\n Стрик ").append(streak).append(" дней! Получен редкий предмет: ").append(rareItem).append("!");
					achievements.unlock(player, "стрик_7");
				}

				// Налог на богатство (68)
				if (player.getMoney() > GameBalance.WEALTH_TAX_THRESHOLD) {
					int tax = (int)(player.getMoney() * GameBalance.WEALTH_TAX_RATE);
					player.setMoney(player.getMoney() - tax);
					msg.append("\n💰 Налог на богатство: -").append(tax).append(" монет");
				}

				// Процент по долгу (63)
				if (player.getDebt() > 0) {
					int interest = (int)(player.getDebt() * GameBalance.CREDIT_DAILY_INTEREST);
					if (player.getMoney() >= interest) {
						player.setMoney(player.getMoney() - interest);
						msg.append("\n💳 Проценты по кредиту: -").append(interest).append(" монет (долг: ").append(player.getDebt()).append(")");
					}
				}

				playerCache.put(id, player);
				event.getChannel().sendMessage(msg.toString()).submit();
			} else {
				int hours = (int) (24 - (now - player.getDailyTime()) / (GameBalance.ONE_DAY_MS / 24));
				event.getChannel().sendMessage("Вы уже получили ежедневный бонус, приходите через " + hours + " часов. Текущий стрик: " + player.getDailyStreak() + " дн.").submit();
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Показывает ежедневные квесты игрока.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void showDailyQuests(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		DailyQuest quests = dailyQuestService.getDailyQuests(id);
		event.getChannel().sendMessage(dailyQuestService.formatQuests(quests)).submit();
	}

	/** +квесты — журнал выполненных квестов (48) */
	public void questJournal(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		List<String> completed = player.getCompletedQuests();
		if (completed == null || completed.isEmpty()) {
			event.getChannel().sendMessage("📜 Ты ещё не выполнил ни одного квеста.").submit();
			return;
		}
		int start = Math.max(0, completed.size() - 10);
		List<String> last10 = completed.subList(start, completed.size());
		var sb = new StringBuilder("📜 **Журнал квестов:**\n");
		for (int i = 0; i < last10.size(); i++) {
			sb.append(i + 1).append(". ").append(last10.get(i)).append("\n");
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** Проверяет скрытые квесты по тексту сообщения */
	public boolean checkHiddenQuest(MessageReceivedEvent event) {
		String content = event.getMessage().getContentDisplay().toLowerCase();
		for (Map.Entry<String, String> entry : HIDDEN_QUESTS.entrySet()) {
			if (content.contains(entry.getKey())) {
				String id = event.getAuthor().getId();
				event.getChannel().sendMessage("🔮 " + entry.getValue()).submit();
				String reward = entry.getValue();
				// Parse XP amount
				if (reward.contains("XP")) {
					int xpAmount = 50;
					try {
						// Extract number before " XP"
						int xpIdx = reward.indexOf("XP");
						String part = reward.substring(0, xpIdx).trim();
						String[] words = part.split("\\s+");
						xpAmount = Integer.parseInt(words[words.length - 1].replace("+", ""));
					} catch (Exception ignored) {}
					stats.changeXp(id, xpAmount);
				}
				if (reward.contains("монет")) {
					int moneyAmount = 50;
					try {
						int moneyIdx = reward.indexOf("монет");
						String part = reward.substring(0, moneyIdx).trim();
						String[] words = part.split("\\s+");
						moneyAmount = Integer.parseInt(words[words.length - 1].replace("+", ""));
					} catch (Exception ignored) {}
					stats.changeMoney(id, moneyAmount, true);
				}
				if (reward.contains("кружка цикория")) {
					inventory.addNewItem(id, "кружка цикория");
				}
				return true;
			}
		}
		return false;
	}
}
