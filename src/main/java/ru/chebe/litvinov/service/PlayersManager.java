package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.DailyQuest;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import ru.chebe.litvinov.GameBalance;
import ru.chebe.litvinov.PlayerProgressTables;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ToIntFunction;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;

import static ru.chebe.litvinov.Constants.MIN_LVL_TO_CLAN_CREATE;
import static ru.chebe.litvinov.Constants.MIN_LVL_TO_CLAN_JOIN;

/**
 * Главный сервис управления игроками.
 * Координирует все игровые действия: создание персонажа, перемещение, бой, квесты,
 * инвентарь, торговля, игры в таверне, клановые операции и ежедневные бонусы.
 * Использует per-player блокировки для потокобезопасного изменения характеристик.
 */
public class PlayersManager implements ru.chebe.litvinov.service.interfaces.IPlayersManager {
	private static final Logger log = LoggerFactory.getLogger(PlayersManager.class);
	private final PlayerRepository playerCache;
	private final LocationManager locationManager;
	private final ItemsManager itemsManager;
	private final BattleManager battleManager;
	private final EventsManager eventsManager;
	private final ClanManager clanManager;
	private final Tavern tavern;
	private final NpcManager npcManager;
	private final Random random = new Random();

	private final ConcurrentHashMap<String, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
	private final DuelService duelService;

	// Кулдаун на убийство босса
	private static final long BOSS_COOLDOWN_HOURS = 4;
	private final java.util.concurrent.ConcurrentHashMap<String, java.time.Instant> lastBossKillTime = new java.util.concurrent.ConcurrentHashMap<>();
	private DailyQuestService dailyQuestService;

	/** Устанавливает сервис ежедневных квестов (вызывается после конструктора). */
	public void setDailyQuestService(DailyQuestService dailyQuestService) {
		this.dailyQuestService = dailyQuestService;
	}

	private ReentrantLock getPlayerLock(String id) {
		return playerLocks.computeIfAbsent(id, k -> new ReentrantLock());
	}

	private static final Map<Integer, Integer> xpMap = PlayerProgressTables.XP_MAP;
	private static final Map<Integer, Integer> hpMap = PlayerProgressTables.HP_MAP;
	List<String> words1 = List.of("Унылый", "Гейский", "Стрёмный", "Тупой", "Дрищавый", "Жирный");
	List<String> words2 = List.of("Пидор", "Мудила", "Хуй", "Гей", "Лох", "Шлюха");

	/**
	 * Создаёт менеджер игроков со всеми зависимостями.
	 *
	 * @param playerCache    репозиторий Ignite 3 для хранения данных игроков
	 * @param locationManager менеджер локаций
	 * @param itemsManager    менеджер предметов
	 * @param battleManager   менеджер боевой системы
	 * @param eventsManager   менеджер квестов и событий
	 * @param clanManager     менеджер кланов
	 * @param tavern          сервис таверны (азартные игры)
	 * @param npcManager      менеджер NPC-ботов
	 */
	public PlayersManager(PlayerRepository playerCache, LocationManager locationManager, ItemsManager itemsManager,
	                      BattleManager battleManager, EventsManager eventsManager, ClanManager clanManager,
	                      Tavern tavern, NpcManager npcManager) {
		this.playerCache = playerCache;
		this.locationManager = locationManager;
		this.itemsManager = itemsManager;
		this.battleManager = battleManager;
		this.eventsManager = eventsManager;
		this.clanManager = clanManager;
		this.tavern = tavern;
		this.npcManager = npcManager;
		this.duelService = new DuelService(playerCache, this::getPlayerLock, this::unlockAchievement);
	}

	/**
	 * Отправляет игроку его текущие характеристики.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void getPlayerInfo(MessageReceivedEvent event) {
		String id = event.getMessage().getAuthor().getId();
		removeExpiredBuffs(id);
		Player player = playerCache.get(id);
		String statsMsg = player.toString();
		String title = getTitle(player);
		String prestige = player.getPrestige() > 0 ? " ⭐×" + player.getPrestige() : "";
		String extra = "\n🏷️ Звание: **" + title + "**" + prestige;
		List<String> achs = player.getAchievements();
		if (achs != null && !achs.isEmpty()) {
			extra += "\n🌟 Редкое достижение: **" + achievementName(achs.get(achs.size() - 1)) + "**";
		}
		event.getChannel().sendMessage(statsMsg + extra).submit();
	}

	/**
	 * Возвращает количество опыта, необходимое для достижения следующего уровня.
	 *
	 * @param player игрок
	 * @return количество опыта до следующего уровня
	 */
	public int getXp(Player player) {
		return xpMap.get(player.getLevel() + 1);
	}

	/**
	 * Возвращает максимальное значение HP для текущего уровня игрока.
	 *
	 * @param player игрок
	 * @return максимальное значение HP
	 */
	public int getMaxHp(Player player) {
		return hpMap.get(player.getLevel());
	}

	/**
	 * Устанавливает точное значение HP игроку.
	 *
	 * @param id идентификатор игрока
	 * @param hp новое значение HP
	 */
	public void changeHp(String id, int hp) {
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return;
			player.setHp(Math.min(hp, player.getMaxHp()));
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Изменяет HP игрока на указанное значение.
	 *
	 * @param id       идентификатор игрока
	 * @param hp       величина изменения HP
	 * @param increase true — увеличить HP (не превышая максимум), false — уменьшить
	 * @return актуальное значение HP после изменения
	 */
	public int changeHp(String id, int hp, boolean increase) {
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return 0;
			if (increase) {
				int newHp = player.getHp() + hp;
				player.setHp(Math.min(newHp, player.getMaxHp()));
			} else {
				player.setHp(player.getHp() - hp);
			}
			playerCache.put(id, player);
			return player.getHp();
		} finally {
			lock.unlock();
		}
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
	 * Изменяет количество денег игрока.
	 *
	 * @param id       идентификатор игрока
	 * @param money    сумма изменения
	 * @param increase true — добавить деньги, false — вычесть
	 * @return актуальное количество денег после изменения
	 */
	public int changeMoney(String id, int money, boolean increase) {
		return mutate(id, Player::getMoney, Player::setMoney, money, increase);
	}

	/**
	 * Изменяет репутацию игрока.
	 *
	 * @param id         идентификатор игрока
	 * @param reputation величина изменения репутации
	 * @param increase   true — увеличить, false — уменьшить
	 * @return актуальное значение репутации после изменения
	 */
	public int changeReputation(String id, int reputation, boolean increase) {
		return mutate(id, Player::getReputation, Player::setReputation, reputation, increase);
	}

	/**
	 * Начисляет опыт игроку. При наборе достаточного количества — повышает уровень и восстанавливает HP.
	 *
	 * @param id идентификатор игрока
	 * @param xp количество начисляемого опыта
	 */
	public void changeXp(String id, int xp) {
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return;
			
			int totalXp = player.getExp() + xp;
			int expToNext = player.getExpToNextLvl();
			
			// Повышаем уровень, пока опыт превышает требуемый
			while (totalXp >= expToNext && player.getLevel() < 100) {
				totalXp -= expToNext;
				player.setLevel(player.getLevel() + 1);
				player.setExpToNextLvl(xpMap.get(player.getLevel()));
				player.setMaxHp(getMaxHp(player));  // Обновляем максимальное HP
				player.setHp(player.getMaxHp());     // Восстанавливаем HP при повышении уровня
				expToNext = player.getExpToNextLvl();
				if (player.getLevel() == 10) unlockAchievement(player, "10_уровень");
				if (player.getLevel() >= 50) unlockAchievement(player, "легенда");
			}
			
			player.setExp(totalXp);
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Регистрирует нового игрока. Если игрок уже существует — сообщает об этом.
	 *
	 * @param event событие Discord-сообщения от регистрирующегося пользователя
	 */
	public void createPlayer(MessageReceivedEvent event) {
		String id = event.getMessage().getAuthor().getId();
		if (!playerCache.contains(id)) {
			String nickName = event.getMessage().getAuthor().getName();
			Player newPlayer = new Player(nickName, id);
			unlockAchievement(newPlayer, "первые_шаги");
			playerCache.put(id, newPlayer);
			event.getChannel().sendMessage("""
					Добро пожаловать в игру, мы внимательно проанализировали твой профиль и решили, что ник %s отлично тебе подходит

					Впрочем если ты хочешь использовать ник %s мы отнесемся к этому с пониманием.
					Теперь ты готов к сражениям и кринжу, скорее ко второму да, для продолжения набери +помощь чтобы отобразить доступные команды или +карта для отображения информации куда тебе надо сходить""".formatted(getCringeName(), nickName)).submit();
		} else {
			event.getChannel().sendMessage("Ты уже зарегистрирован в БЧ ГРП, просто продолжай играть и не пытайся больше обмануть меня пыдор").submit();
		}
	}

	private String getCringeName() {
		Random random = new Random();
		int index1 = random.nextInt(words1.size());
		int index2 = random.nextInt(words2.size());
		int index3 = random.nextInt(100);
		return words1.get(index1) + words2.get(index2) + index3;
	}

	/**
	 * Добавляет предмет в инвентарь игрока и применяет его постоянные эффекты к характеристикам.
	 *
	 * @param id   идентификатор игрока
	 * @param item название предмета
	 */
	public void addNewItem(String id, String item) {
		ReentrantLock lock = getPlayerLock(id);
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
		ReentrantLock lock = getPlayerLock(id);
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

	@Override
	public Player getPlayer(String id) {
		return playerCache.get(id);
	}

	/**
	 * Обрабатывает смерть игрока: списывает 10% денег, восстанавливает HP и перемещает на Респаун.
	 *
	 * @param dead игрок, который погиб
	 */
	public void deathOfPlayer(Player dead) {
		int level = dead.getLevel();
		double moneyPenaltyPct = level <= 5  ? GameBalance.DEATH_MONEY_PENALTY_LOW
				: level <= 15 ? GameBalance.DEATH_MONEY_PENALTY_MID
				: level <= 30 ? GameBalance.DEATH_MONEY_PENALTY_HIGH
				:               GameBalance.DEATH_MONEY_PENALTY_MAX;
		dead.setMoney((int) (dead.getMoney() * (1 - moneyPenaltyPct)));

		// Lose 5% of XP earned within current level (never goes below 0 in the level)
		int xpLoss = (int) (dead.getExp() * GameBalance.DEATH_XP_LOSS_PCT);
		dead.setExp(Math.max(0, dead.getExp() - xpLoss));

		dead.setHp(dead.getMaxHp());
		// movePlayerInPopulation reads dead.getLocation() to find the current location —
		// must be called before changing it, otherwise the player is never removed from the old location
		locationManager.movePlayerInPopulation(dead, "респаун");
		dead.setLocation("респаун");
		playerCache.put(dead.getId(), dead);
	}

	/**
	 * Обрабатывает команду использования активируемого предмета из инвентаря игрока.
	 *
	 * @param event событие Discord-сообщения с названием предмета
	 */
	private static final long BUFF_DURATION_MS = 30 * 60 * 1000L; // 30 минут

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
					ReentrantLock lock = getPlayerLock(playerId);
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
					int hp = changeHp(playerId, item.getHealth(), true);
					event.getChannel().sendMessage("Теперь у тебя " + hp + " здоровья").submit();
				}
				if (item.getArmor() > 0) {
					int armor = changeArmor(playerId, item.getArmor(), true);
					event.getChannel().sendMessage("Теперь у тебя " + armor + " брони").submit();
				}
				if (item.getLuck() > 0) {
					int luck = changeLuck(playerId, item.getLuck(), true);
					event.getChannel().sendMessage("Теперь у тебя " + luck + " удачи").submit();
				}
				if (item.getStrength() > 0) {
					int str = changeStrength(playerId, item.getStrength(), true);
					event.getChannel().sendMessage("Теперь у тебя " + str + " силы").submit();
				}
				if (item.getReputation() > 0) {
					int rep = changeReputation(playerId, item.getReputation(), true);
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
		ReentrantLock lock = getPlayerLock(playerId);
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

	private int changeArmor(String id, int armor, boolean increase) {
		return mutate(id, Player::getArmor, Player::setArmor, armor, increase);
	}

	/**
	 * Изменяет удачу игрока.
	 *
	 * @param id       идентификатор игрока
	 * @param luck     величина изменения удачи
	 * @param increase true — увеличить, false — уменьшить
	 * @return актуальное значение удачи после изменения
	 */
	public int changeLuck(String id, int luck, boolean increase) {
		return mutate(id, Player::getLuck, Player::setLuck, luck, increase);
	}

	/**
	 * Изменяет силу игрока.
	 *
	 * @param id       идентификатор игрока
	 * @param strength величина изменения силы
	 * @param increase true — увеличить, false — уменьшить
	 * @return актуальное значение силы после изменения
	 */
	public int changeStrength(String id, int strength, boolean increase) {
		return mutate(id, Player::getStrength, Player::setStrength, strength, increase);
	}

	private int mutate(String id, ToIntFunction<Player> get, ObjIntConsumer<Player> set, int delta, boolean increase) {
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player == null) return 0;
			set.accept(player, increase ? get.applyAsInt(player) + delta : get.applyAsInt(player) - delta);
			playerCache.put(id, player);
			return get.applyAsInt(player);
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
		var player = playerCache.get(event.getAuthor().getId());
		String message = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
		if (player.getInventory().containsKey(message.toLowerCase())) {
			Item item = itemsManager.getItem(message);
			int money = changeMoney(player.getId(), item.getPrice() / (2 - player.getReputation() / 10), true);
			deleteItem(player.getId(), item.getName());
			event.getChannel().sendMessage("Теперь у тебя " + money + " денег").submit();
		} else {
			event.getChannel().sendMessage("Такого предмета нет в твоём инвентаре").submit();
		}
	}

	/**
	 * Обрабатывает команду игры в кости. Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой
	 */
	public void dieCast(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Как ты собрался бросить кости если ты не в таверне? Метнись кабанчиком сначала туда").submit();
			return;
		}
		String raw = event.getMessage().getContentDisplay();
		String bidText = raw.length() > 7 ? raw.substring(7).trim() : "";
		if (bidText.isEmpty()) {
			event.getChannel().sendMessage("Мы на деньги играем, ты забыл ставку указать, от 1 до 100").submit();
			return;
		}
		int bid;
		try {
			bid = Integer.parseInt(bidText);
		} catch (Exception e) {
			event.getChannel().sendMessage("Ну и что я с твоим " + bidText + " должен делать? Нахер он мне нужен, я только на деньги играю").submit();
			return;
		}
		if (bid < 0) {
			event.getChannel().sendMessage("Ты тестировщик или просто давно по хлебопечке не получал? Ставь нормально").submit();
			return;
		} else if (bid > 100) {
			event.getChannel().sendMessage("Ого к нам мсье мажор пожаловал и давай выёбуваться ставками, не так не пойдет, давай не больше 100").submit();
			return;
		}
		if (player.getMoney() < bid) {
			event.getChannel().sendMessage("Я ж вижу, что у тебя таких денег отродясь не было, а нанимать ябыса трясти с тебя долг я не хочу").submit();
			return;
		}
		int moneyBeforeDice = player.getMoney();
		player = tavern.diceStart(event, player, bid);
		if (player.getMoney() > moneyBeforeDice) {
			questProgress(player.getId(), "WIN_TAVERN", 1);
			questProgress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBeforeDice);
		}
		playerCache.put(player.getId(), player);
	}

	/**
	 * Обрабатывает команду перемещения игрока в другую локацию.
	 * Поддерживает обычные переходы и телепортацию с токеном телепорта.
	 *
	 * @param event событие Discord-сообщения с названием целевой локации
	 */
	public void move(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(5).trim().toLowerCase();
		removeExpiredBuffs(event.getAuthor().getId());
		var player = playerCache.get(event.getAuthor().getId());
		var currentLocation = locationManager.getLocation(player.getLocation());
		Location nextLocation = locationManager.getLocation(message.toLowerCase());
		if (message == null || message.isEmpty()) {
			event.getChannel().sendMessage("Для перемещения нужно указать желаемую локацию, введи \"+идти локация\" вместо локация, подставь любую из доступных: \n" + currentLocation.getPaths().toString()).submit();
			return;
		} else if (nextLocation == null) {
			event.getChannel().sendMessage("Ты не можешь переместится в эту локацию, выбери что-нибудь из доступных путей: \n" + currentLocation.getPaths().toString()).submit();
			return;
		}
		if (currentLocation.getName().equals(nextLocation.getName())) {
			event.getChannel().sendMessage("Ты уже находишься в этой локации").submit();
			return;
		}
		boolean isTeleport = false;
		if (!currentLocation.getPaths().contains(message)) {
			Integer tokenObj = player.getInventory().get("токен телепорта");
			int token = tokenObj != null ? tokenObj : 0;
			if (currentLocation.isTeleport() && nextLocation.isTeleport() && token > 0) {
				isTeleport = true;
				if (token > 1) {
					player.getInventory().put("токен телепорта", token - 1);
				} else {
					player.getInventory().remove("токен телепорта");
				}
			} else {
				event.getChannel().sendMessage("Ты не можешь переместится в эту локацию, выбери что-нибудь из доступных путей: \n" + currentLocation.getPaths().toString()).submit();
				return;
			}
		}
		String prevLocation = player.getLocation();
		nextLocation = locationManager.movePlayerInPopulation(player, nextLocation.getName());
		player.setLocation(nextLocation.getName());

		// История перемещений (27)
		if (player.getLocationHistory() == null) player.setLocationHistory(new ArrayList<>());
		player.getLocationHistory().add(prevLocation);

		// Квест «Путешественник»: считаем посещённые локации
		Event activeEvent = player.getActiveEvent();
		if (activeEvent != null && "Путешественник".equals(activeEvent.getType())) {
			activeEvent.setAttempt(activeEvent.getAttempt() + 1);
		}
		playerCache.put(player.getId(), player);
		checkExplorerAchievement(player);

		var token = player.getInventory().get("токен телепорта") == null ? 0 : player.getInventory().get("токен телепорта");
		String teleport = isTeleport ? " с помощью токена телепорта, осталось " + token : "";
		StringBuilder msg = new StringBuilder("Ты успешно переместился в локацию - ").append(nextLocation.getName()).append(teleport)
				.append("\nВ этой локации находятся следующие игроки: ").append(nextLocation.getPopulationByName().toString());

		// Подсказка пути для квестов «Ходилка» и «Таймер»
		if (activeEvent != null && ("Ходилка".equals(activeEvent.getType()) || "Таймер".equals(activeEvent.getType()))) {
			String dest = activeEvent.getLocationEnd();
			if (dest != null && !dest.equals(nextLocation.getName())) {
				String hint = locationManager.findNextStep(nextLocation.getName(), dest);
				if (hint != null) {
					msg.append("\n🗺 Для выполнения квеста следующая локация: **").append(hint).append("**");
				}
			}
		}

		event.getChannel().sendMessage(msg.toString()).submit();

		if (eventsManager.transferEvent(event, nextLocation)) {
			int battleResult = battleManager.mobBattle(player, event.getChannel());
			if (battleResult > 0) {
				int currentHp = battleResult;
				int maxHp = player.getMaxHp();
				if (currentHp < maxHp) {
					int recoveryPercent = player.getLevel() < GameBalance.HP_RECOVERY_LOW_LEVEL_THRESHOLD
							? GameBalance.HP_RECOVERY_PCT_LOW_LEVEL : GameBalance.HP_RECOVERY_PCT_NORMAL;
					int hpLost = maxHp - currentHp;
					int hpToRestore = Math.max(GameBalance.HP_RECOVERY_MIN, (hpLost * recoveryPercent) / 100);
					changeHp(player.getId(), currentHp + hpToRestore);
				}
				changeXp(player.getId(), GameBalance.MOB_KILL_XP);
				changeMoney(player.getId(), GameBalance.MOB_KILL_MONEY, true);

				// Квест «Охота»: считаем убитых мобов
				player = playerCache.get(player.getId());
				if (player != null) {
					if (player.getActiveEvent() != null && "Охота".equals(player.getActiveEvent().getType())) {
						player.getActiveEvent().setAttempt(player.getActiveEvent().getAttempt() + 1);
					}
					// Трекинг убийств мобов (71)
					player.setMobKills(player.getMobKills() + 1);
					if (player.getMobKills() >= 50) unlockAchievement(player, "ветеран");
					playerCache.put(player.getId(), player);
				}

				// Drop chance: base + 1% per luck point, capped
				int dropChancePct = Math.min(GameBalance.DROP_CHANCE_MAX_PCT,
						GameBalance.DROP_CHANCE_BASE_PCT + player.getLuck() * GameBalance.DROP_CHANCE_PER_LUCK);
				if (random.nextInt(100) < dropChancePct) {
					String drop = itemsManager.getRandomItemName();
					if (drop != null) {
						player = playerCache.get(player.getId());
						if (player != null) {
							player.getInventory().merge(drop, 1, Integer::sum);
							playerCache.put(player.getId(), player);
							event.getChannel().sendMessage("Моб выронил предмет: **" + drop + "**").submit();
						}
					}
				}
			} else {
				deathOfPlayer(player);
			}
		}
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
			changeMoney(playerId, GameBalance.QUEST_CHANGE_FEE, false);
			event.getChannel().sendMessage("Ты потратил " + GameBalance.QUEST_CHANGE_FEE + " монет и получил новое задание :\n" + player.getActiveEvent().toString()).submit();
			playerCache.put(playerId, player);
		} else {
			event.getChannel().sendMessage("У тебя недостаточно денег, сначала зарабаотай их").submit();
		}
	}

	/**
	 * Атакует случайного NPC в текущей локации игрока.
	 */
	public void fightNpc(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		var player = playerCache.get(playerId);
		if (player == null) {
			event.getChannel().sendMessage("Сначала зарегистрируйся командой +начать").submit();
			return;
		}
		var bot = npcManager.getRandomBot(player.getLocation());
		if (bot == null) {
			event.getChannel().sendMessage("В локации **" + player.getLocation() + "** нет NPC для битвы.").submit();
			return;
		}
		event.getChannel().sendMessage("⚔️ Ты атакуешь **" + bot.getNickName() + "** [❤️ HP: **" + bot.getHp() + "**]! Бой начинается...").submit();
		battleManager.playerBattle(List.of(player), List.of((ru.chebe.litvinov.data.Person) bot), event.getChannel());
		// battleMechanic modifies the local player object but doesn't write it back to cache.
		// changeMoney/changeXp re-fetch from cache and would overwrite with pre-battle HP.
		changeHp(playerId, Math.max(0, player.getHp()));
		if (bot.getHp() <= 0) {
			event.getChannel().sendMessage("🏆 **Победа над " + bot.getNickName() + "!**\n💰 +" + bot.getMoneyReward() + " монет  ✨ +" + bot.getXpReward() + " опыта").submit();
			changeMoney(playerId, bot.getMoneyReward(), true);
			changeXp(playerId, bot.getXpReward());
			questProgress(playerId, "KILL_NPC", 1);
			questProgress(playerId, "EARN_GOLD", bot.getMoneyReward());
			npcManager.respawnBot(bot);
		} else {
			int level = player.getLevel();
			int penaltyPct = level <= 5 ? 5 : level <= 15 ? 10 : level <= 30 ? 15 : 20;
			event.getChannel().sendMessage("💀 **" + bot.getNickName() + "** победил тебя! 😵 Ты воскрешён на Респауне и потерял **" + penaltyPct + "%** монет.").submit();
			npcManager.respawnBot(bot);
			deathOfPlayer(player);
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
			changeMoney(playerId, activeEvent.getMoneyReward(), true);
			changeXp(playerId, activeEvent.getXpReward());
			questProgress(playerId, "EARN_GOLD", activeEvent.getMoneyReward());
			StringBuilder reward = new StringBuilder("Ты успешно завершил свой квест! Опыт: ")
					.append(activeEvent.getXpReward()).append(", монеты: ").append(activeEvent.getMoneyReward());
			String itemReward = activeEvent.getItemReward();
			if (itemReward != null && !itemReward.isBlank()) {
				addNewItem(playerId, itemReward);
				reward.append(", предмет: **").append(itemReward).append("**");
			}
			event.getChannel().sendMessage(reward.toString()).submit();
		} else {
			event.getChannel().sendMessage("Ты не выполнил условия квеста или ответил неправильно!").submit();
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
			} else if (player.getMoney() < item.getPrice()) {
				event.getChannel().sendMessage("У вас недостаточно денег для покупки этого предмета").submit();
			} else {
				changeMoney(player.getId(), item.getPrice(), false);
				addNewItem(player.getId(), item.getName());
				event.getChannel().sendMessage("Вы купили " + item.getName() + " у вас осталось " + player.getMoney() + " денег").submit();
			}
		} else {
			event.getChannel().sendMessage("Покупать предметы можно только в локациях Таверна и Магазин").submit();
		}
	}

	/**
	 * Инициирует бой с боссом текущей локации.
	 * Клановые участники в той же локации сражаются вместе.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void bossFight(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		// Проверка кулдауна на бой с боссом
		String playerId = event.getAuthor().getId();
		java.time.Instant lastKill = lastBossKillTime.get(playerId);
		if (lastKill != null) {
			java.time.Instant nextAllowed = lastKill.plusSeconds(BOSS_COOLDOWN_HOURS * 3600);
			if (java.time.Instant.now().isBefore(nextAllowed)) {
				long minutesLeft = java.time.Duration.between(java.time.Instant.now(), nextAllowed).toMinutes() + 1;
				event.getChannel().sendMessage("⏳ Ты недавно сражался с боссом. Следующий бой доступен через **" + minutesLeft + " мин**.").submit();
				return;
			}
		}
		var loc = locationManager.getLocation(player.getLocation());
		if (loc.getBoss() == null) {
			event.getChannel().sendMessage("В этой локации нет босса, перейди в другую если хочешь присесть на бутылку").submit();
		} else {
			event.getChannel().sendMessage("Ты отважился бросить вызов боссу по имени " + loc.getBoss() + " земля тебе пухом братишка").submit();
			// Always include the player themselves; getPlayersByClan returns only clan members and may exclude the solo player
			List<Player> players = new ArrayList<>();
			players.add(player);
			getPlayersByClan(player).stream()
					.filter(p -> !p.getId().equals(player.getId()))
					.forEach(players::add);
			List<Person> playersAsPerson = players.stream()
							.map(p -> (Person) p)
							.collect(Collectors.toList());
			battleManager.bossBattle(playersAsPerson, loc.getBoss(), event.getChannel());
			for (Person play : players) {
				if (play.getHp() > 0) {
					String winnerId = ((Player) play).getId();
					changeXp(winnerId, GameBalance.BOSS_KILL_XP);
					changeMoney(winnerId, GameBalance.BOSS_KILL_MONEY, true);
					questProgress(winnerId, "DEFEAT_BOSS", 1);
					questProgress(winnerId, "EARN_GOLD", GameBalance.BOSS_KILL_MONEY);
					String bossItem = battleManager.getBossItemName(loc.getBoss());
					addNewItem(winnerId, bossItem);
					event.getChannel().sendMessage("В твой инвентарь добавлен предмет " + bossItem).submit();
					// Записываем время победы над боссом для кулдауна
					lastBossKillTime.put(((Player) play).getId(), java.time.Instant.now());
					// Достижения рейда (71)
					Player winPlayer = playerCache.get(winnerId);
					if (winPlayer != null) {
						unlockAchievement(winPlayer, "первый_рейд");
						unlockAchievement(winPlayer, "победитель_рейда");
						checkRichAchievement(winPlayer);
						checkCollectorAchievement(winPlayer);
						playerCache.put(winnerId, winPlayer);
					}
				} else {
					deathOfPlayer(((Player) play));
				}
			}
		}
	}

	/**
	 * Инициирует PvP-бой со случайным игроком в текущей локации.
	 * Доступно только в PvP-зонах; члены клана не атакуются.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void playersFight(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		var loc = locationManager.getLocation(player.getLocation());

		// Проверка PvP зоны
		if (!loc.isPvp()) {
			event.getChannel().sendMessage("В этой локации нельзя драться!").queue();
			return;
		}

		// Получение списка игроков
		List<String> population = new ArrayList<>(loc.getPopulationById());
		population.remove(player.getId()); // Убираем текущего игрока

		// Проверка наличия противников
		if (population.isEmpty()) {
			event.getChannel().sendMessage("Нет игроков для битвы").queue();
			return;
		}

		// Удаление членов клана из списка противников
		List<Player> clanMembers = getPlayersByClan(player);
		List<String> clanMemberIds = clanMembers.stream().map(Player::getId).collect(Collectors.toList());
		clanMemberIds.forEach(population::remove);

		if (population.isEmpty()) {
			event.getChannel().sendMessage("Все игроки здесь из вашего клана").queue();
			return;
		}

		// Выбор случайного противника
		String enemyId = population.get(random.nextInt(population.size()));
		Player enemy = playerCache.get(enemyId);

		if (enemy == null) {
			event.getChannel().sendMessage("Ошибка при выборе противника").queue();
			return;
		}

		// Формирование команд
		List<Person> attackers = new ArrayList<>(getPlayersByClan(player));
		if (attackers.isEmpty()) attackers.add(player); // одиночный игрок без клана
		List<Person> defenders = new ArrayList<>(getPlayersByClan(enemy).stream()
						.map(p -> (Person) p)
						.collect(Collectors.toList()));
		if (defenders.isEmpty()) defenders = List.of(enemy); // Если противник без клана

		// Проведение боя
		List<Person> battleResult = battleManager.playerBattle(attackers, defenders, event.getChannel());

		// Обработка результатов
		battleResult.forEach(p -> {
			Player pObj = (Player) p;
			if (p.getHp() > 0) {
				if (attackers.contains(p)) {
					changeMoney(pObj.getId(), GameBalance.PVP_WIN_MONEY, true);
					changeXp(pObj.getId(), GameBalance.PVP_WIN_XP);
					// Трекинг PvP побед (71)
					ReentrantLock pvpLock = getPlayerLock(pObj.getId());
					pvpLock.lock();
					try {
						Player pvpWinner = playerCache.get(pObj.getId());
						if (pvpWinner != null) {
							pvpWinner.setPvpWins(pvpWinner.getPvpWins() + 1);
							if (pvpWinner.getPvpWins() >= 100) unlockAchievement(pvpWinner, "100_pvp");
							playerCache.put(pObj.getId(), pvpWinner);
						}
					} finally {
						pvpLock.unlock();
					}
					event.getChannel().sendMessage(pObj.getNickName() + " получает награду!").queue();
				}
			} else {
				deathOfPlayer(pObj);
				event.getChannel().sendMessage(pObj.getNickName() + " погиб!").queue();
			}
		});

		Location updatedLoc = locationManager.getLocation(player.getLocation());
		event.getChannel().sendMessage("Оставшиеся игроки: " + updatedLoc.getPopulationByName()).queue();
	}

	/**
	 * Начисляет игроку ежедневный бонус (100 монет) с учётом стрика.
	 * 3 дня подряд — +50 бонус; 7 дней — редкий предмет.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void dailyBonus(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = getPlayerLock(id);
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
					unlockAchievement(player, "стрик_3");
				}
				if (streak % GameBalance.DAILY_STREAK_RARE_ITEM_INTERVAL == 0 && streak > 0) {
					String rareItem = GameBalance.DAILY_STREAK_RARE_ITEM;
					Map<String, Integer> inv = player.getInventory();
					inv.put(rareItem, inv.getOrDefault(rareItem, 0) + 1);
					msg.append("\n Стрик ").append(streak).append(" дней! Получен редкий предмет: ").append(rareItem).append("!");
					unlockAchievement(player, "стрик_7");
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

	/**
	 * Обрабатывает команду создания нового клана. Требует минимального 10-го уровня.
	 *
	 * @param event событие Discord-сообщения с названием клана
	 */
	public void clanRegister(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getLevel() < MIN_LVL_TO_CLAN_CREATE) {
			event.getChannel().sendMessage("Вы не можете создать клан раньше, чем достигните 10 уровня").submit();
			return;
		}
		if (player.getClanName() != null && !player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы уже состоите в клане, сначала покиньте его").submit();
		} else {
			String clanName = event.getMessage().getContentDisplay().substring(11).trim().toLowerCase();
			String result = clanManager.registerClan(clanName, player.getId());
			if (!result.isEmpty()) {
				event.getChannel().sendMessage(result).submit();
			} else {
				player.setClanName(clanName);
				playerCache.put(player.getId(), player);
				event.getChannel().sendMessage("Вы успешно зарегистрировали клан " + clanName).submit();
			}
		}
	}

	/**
	 * Обрабатывает команду выхода игрока из клана.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void clanLeave(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане").submit();
		} else {
			clanManager.leaveClan(player.getClanName(), player.getId());
			event.getChannel().sendMessage("Вы покинули клан " + player.getClanName()).submit();
		}
	}

	/**
	 * Обрабатывает команду подачи заявки на вступление в клан. Требует минимального 3-го уровня.
	 *
	 * @param event событие Discord-сообщения с названием клана
	 */
	public void clanJoin(MessageReceivedEvent event) {
		String clanName = event.getMessage().getContentDisplay().substring(16).trim().toLowerCase();
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getLevel() < MIN_LVL_TO_CLAN_JOIN) {
			event.getChannel().sendMessage("Вы не можете присоединиться к клану раньше, чем достигните " + MIN_LVL_TO_CLAN_JOIN + " уровня").submit();
			return;
		}
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			String result = clanManager.joinClan(clanName, player.getId());
			if (result.isEmpty()) {
				unlockAchievement(player, "клановый_чел");
				playerCache.put(player.getId(), player);
				event.getChannel().sendMessage("Ваша заявка на вступление в клан " + clanName + " подана. Ожидайте подтверждения лидера").submit();
			} else {
				event.getChannel().sendMessage(result).submit();
			}
		} else {
			event.getChannel().sendMessage("Вы уже состоите в клане " + player.getClanName()).submit();
		}
	}

	/**
	 * Обрабатывает команду принятия всех заявок на вступление в клан лидером.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void acceptApply(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане").submit();
		} else {
			String result = clanManager.acceptApply(player.getClanName(), player.getId());
			if (!result.isEmpty()) {
				event.getChannel().sendMessage(result).submit();
			} else {
				event.getChannel().sendMessage("Вы приняли все заявки на вступление в клан").submit();
			}
		}
	}

	/**
	 * Обрабатывает команду отклонения всех заявок на вступление в клан лидером.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void rejectApply(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане").submit();
		} else {
			String result = clanManager.rejectApply(player.getClanName(), player.getId());
			if (!result.isEmpty()) {
				event.getChannel().sendMessage(result).submit();
			} else {
				event.getChannel().sendMessage("Вы отклонили все заявки на вступление в клан").submit();
			}
		}
	}

	/**
	 * Отправляет информацию о клане по его названию.
	 *
	 * @param event событие Discord-сообщения с названием клана
	 */
	public void clanInfo(MessageReceivedEvent event) {
		String clanName = event.getMessage().getContentDisplay().substring(10).trim().toLowerCase();
		event.getChannel().sendMessage(clanManager.getClanInfo(clanName)).submit();
	}

	/**
	 * Выводит таблицу лидеров top-10.
	 * Синтаксис: +топ [уровень|деньги|репутация] (по умолчанию — уровень)
	 */
	public void topLeaderboard(MessageReceivedEvent event) {
		String msg = event.getMessage().getContentDisplay();
		String arg = msg.length() > 4 ? msg.substring(4).trim().toLowerCase() : "";

		List<Player> all = playerCache.getAll();
		if (all.isEmpty()) {
			event.getChannel().sendMessage("Нет зарегистрированных игроков.").submit();
			return;
		}

		java.util.Comparator<Player> comparator;
		String title;
		if ("деньги".equals(arg)) {
			comparator = java.util.Comparator.comparingInt(Player::getMoney).reversed();
			title = "Топ по деньгам";
		} else if ("репутация".equals(arg)) {
			comparator = java.util.Comparator.comparingInt(Player::getReputation).reversed();
			title = "Топ по репутации";
		} else {
			comparator = java.util.Comparator.comparingInt(Player::getLevel).reversed();
			title = "Топ по уровню";
		}

		List<Player> sorted = all.stream().sorted(comparator).limit(10).collect(Collectors.toList());
		StringBuilder sb = new StringBuilder(title + "\n");
		for (int i = 0; i < sorted.size(); i++) {
			Player p = sorted.get(i);
			String classLabel = (p.getPlayerClass() != null && !p.getPlayerClass().isEmpty()) ? " [" + p.getPlayerClass() + "]" : "";
			if ("деньги".equals(arg)) {
				sb.append(String.format("%d. %s%s — %d монет\n", i + 1, p.getNickName(), classLabel, p.getMoney()));
			} else if ("репутация".equals(arg)) {
				sb.append(String.format("%d. %s%s — %d репутации\n", i + 1, p.getNickName(), classLabel, p.getReputation()));
			} else {
				sb.append(String.format("%d. %s%s — %d ур.\n", i + 1, p.getNickName(), classLabel, p.getLevel()));
			}
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/**
	 * Выбор класса персонажа (с 5 уровня, один раз).
	 * Синтаксис: +класс [воин|разбойник|маг]
	 */
	public void chooseClass(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getLevel() < 5) {
				event.getChannel().sendMessage("Класс доступен с 5 уровня. У вас сейчас " + player.getLevel() + " уровень.").submit();
				return;
			}
			if (player.getPlayerClass() != null && !player.getPlayerClass().isEmpty()) {
				event.getChannel().sendMessage("Вы уже выбрали класс: " + player.getPlayerClass()).submit();
				return;
			}
			String arg = event.getMessage().getContentDisplay().length() > 6
					? event.getMessage().getContentDisplay().substring(6).trim().toLowerCase()
					: "";
			switch (arg) {
				case "воин":
					player.setStrength(player.getStrength() + 5);
					player.setArmor(player.getArmor() + 2);
					player.setPlayerClass("ВОИН");
					event.getChannel().sendMessage("Вы выбрали класс ВОИН! +5 к силе, +2 к броне.").submit();
					break;
				case "разбойник":
					player.setLuck(player.getLuck() + 5);
					player.setPlayerClass("РАЗБОЙНИК");
					event.getChannel().sendMessage("Вы выбрали класс РАЗБОЙНИК! +5 к удаче.").submit();
					break;
				case "маг":
					player.setMaxHp(player.getMaxHp() + 30);
					player.setLuck(player.getLuck() + 1);
					player.setPlayerClass("МАГ");
					event.getChannel().sendMessage("Вы выбрали класс МАГ! +30 к макс. HP, +1 к удаче.").submit();
					break;
				default:
					event.getChannel().sendMessage("Доступные классы: воин, разбойник, маг\nПример: +класс воин").submit();
					return;
			}
			unlockAchievement(player, "классовый");
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Показывает достижения игрока.
	 */
	public void getAchievements(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		List<String> achievements = player.getAchievements();
		if (achievements == null || achievements.isEmpty()) {
			event.getChannel().sendMessage("У вас пока нет достижений. Играйте, чтобы их получить!").submit();
			return;
		}
		StringBuilder sb = new StringBuilder("Ваши достижения:\n");
		for (String achId : achievements) {
			sb.append("- ").append(achievementName(achId)).append("\n");
		}
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
		ReentrantLock first = senderFirst ? getPlayerLock(senderId) : getPlayerLock(receiverId);
		ReentrantLock second = senderFirst ? getPlayerLock(receiverId) : getPlayerLock(senderId);
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
				unlockAchievement(sender, "торговец");
				playerCache.put(senderId, sender);
				playerCache.put(receiverId, receiver);
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

	/** Вызов игрока на дуэль (+вызов @игрок). */
	public void challengeDuel(MessageReceivedEvent event) { duelService.challengeDuel(event); }

	/** Принять вызов на дуэль (+принять). */
	public void acceptDuel(MessageReceivedEvent event) { duelService.acceptDuel(event); }

	/** Отказаться от дуэли (+отказать). */
	public void declineDuel(MessageReceivedEvent event) { duelService.declineDuel(event); }

	/** +последний бой — показывает лог последнего боя (24) */
	public void lastBattleLog(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String log = battleManager.getLastBattleLog(id);
		if (log == null || log.isBlank() || log.equals("Нет данных о последнем бое.")) {
			event.getChannel().sendMessage("У тебя ещё не было боёв, или данные не сохранились.").submit();
		} else {
			event.getChannel().sendMessage("📜 **Последний бой:**\n" + log).submit();
		}
	}

	/** +убить нпс — клановый бой против NPC (25) */
	public void clanNpcFight(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		Player player = playerCache.get(playerId);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Клановый бой доступен только участникам клана.").submit();
			return;
		}
		List<java.util.concurrent.locks.ReentrantLock> locks = new ArrayList<>();
		List<Player> clanPlayers = getPlayersByClan(player);
		if (clanPlayers.isEmpty()) {
			event.getChannel().sendMessage("Нет других членов клана в этой локации.").submit();
			return;
		}
		clanPlayers.add(0, player);
		List<ru.chebe.litvinov.data.Person> members = clanPlayers.stream()
				.map(p -> (ru.chebe.litvinov.data.Person) p)
				.collect(Collectors.toList());
		event.getChannel().sendMessage("⚔️ Клан **" + player.getClanName() + "** идёт в бой!").submit();
		List<ru.chebe.litvinov.data.Person> result = battleManager.clanNpcBattle(members, event.getChannel());
		for (ru.chebe.litvinov.data.Person p : result) {
			if (p instanceof Player pl && pl.getId() != null) {
				if (pl.getHp() > 0) {
					changeMoney(pl.getId(), GameBalance.MOB_KILL_MONEY * 2, true);
					changeXp(pl.getId(), GameBalance.MOB_KILL_XP * 2);
				} else {
					deathOfPlayer(pl);
				}
			}
		}
	}

	/** +путь — история перемещений (27) / поиск пути (41) */
	public void locationPath(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		String arg = event.getMessage().getContentDisplay().substring(5).trim().toLowerCase();
		if (arg.isEmpty()) {
			List<String> history = player.getLocationHistory();
			if (history == null || history.isEmpty()) {
				event.getChannel().sendMessage("История перемещений пуста.").submit();
			} else {
				int start = Math.max(0, history.size() - 5);
				List<String> last5 = history.subList(start, history.size());
				event.getChannel().sendMessage("🗺️ Последние локации: " + String.join(" → ", last5)).submit();
			}
		} else {
			if (!player.getInventory().containsKey("карта мира")) {
				event.getChannel().sendMessage("Для поиска пути нужна **карта мира** в инвентаре.").submit();
				return;
			}
			String nextStep = locationManager.findNextStep(player.getLocation(), arg);
			if (nextStep == null) {
				event.getChannel().sendMessage("Маршрут до **" + arg + "** не найден.").submit();
			} else {
				event.getChannel().sendMessage("🗺️ Следующий шаг до **" + arg + "**: **" + nextStep + "**").submit();
			}
		}
	}

	/** +исследовать — даёт случайный предмет или монеты раз в 6 часов (29) */
	public void exploreLocation(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			long now = System.currentTimeMillis();
			if (now - player.getLastExploreTime() < GameBalance.EXPLORE_COOLDOWN_MS) {
				long minLeft = (GameBalance.EXPLORE_COOLDOWN_MS - (now - player.getLastExploreTime())) / 60000;
				event.getChannel().sendMessage("⏳ Ты уже исследовал локацию. Следующее исследование через **" + minLeft + "** мин.").submit();
				return;
			}
			player.setLastExploreTime(now);
			if (random.nextBoolean()) {
				int money = GameBalance.EXPLORE_MONEY_MIN + random.nextInt(GameBalance.EXPLORE_MONEY_MAX - GameBalance.EXPLORE_MONEY_MIN + 1);
				player.setMoney(player.getMoney() + money);
				playerCache.put(id, player);
				event.getChannel().sendMessage("🔍 Ты исследовал локацию и нашёл **" + money + "** монет!").submit();
				checkRichAchievement(player);
			} else {
				String drop = itemsManager.getRandomItemName();
				if (drop != null) {
					player.getInventory().merge(drop, 1, Integer::sum);
					playerCache.put(id, player);
					event.getChannel().sendMessage("🔍 Ты исследовал локацию и нашёл предмет: **" + drop + "**!").submit();
				} else {
					int money = GameBalance.EXPLORE_MONEY_MIN;
					player.setMoney(player.getMoney() + money);
					playerCache.put(id, player);
					event.getChannel().sendMessage("🔍 Ты исследовал локацию и нашёл **" + money + "** монет!").submit();
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/** +домой — телепортирует в клановую базу (34) */
	public void goHome(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Ты не состоишь в клане.").submit();
			return;
		}
		String base = clanManager.getClanBase(player.getClanName());
		locationManager.movePlayerInPopulation(player, base);
		addToLocationHistory(id, player.getLocation());
		player.setLocation(base);
		playerCache.put(id, player);
		event.getChannel().sendMessage("🏠 Ты телепортировался в клановую базу: **" + base + "**").submit();
	}

	private void addToLocationHistory(String id, String location) {
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player p = playerCache.get(id);
			if (p == null) return;
			if (p.getLocationHistory() == null) p.setLocationHistory(new ArrayList<>());
			p.getLocationHistory().add(location);
			playerCache.put(id, p);
		} finally {
			lock.unlock();
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
			ReentrantLock lock = getPlayerLock(id);
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
			ReentrantLock lock = getPlayerLock(id);
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

	/** +улучшить [предмет] — улучшает предмет за 200 монет (37) */
	public void upgradeItem(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String itemName = event.getMessage().getContentDisplay().substring(9).trim().toLowerCase();
		ReentrantLock lock = getPlayerLock(id);
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

	private String diffLine(String stat, int v1, int v2) {
		int diff = v1 - v2;
		String sign = diff > 0 ? "+" : "";
		return String.format("%s: %d vs %d (%s%d)\n", stat, v1, v2, sign, diff);
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
		ReentrantLock lock = getPlayerLock(id);
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
		ReentrantLock lock = getPlayerLock(id);
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
		ReentrantLock lock = getPlayerLock(id);
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

	/** +покер @игрок [ставка] (65) */
	public void playPoker(MessageReceivedEvent event) {
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите оппонента: +покер @игрок [ставка]").submit();
			return;
		}
		String senderId = event.getAuthor().getId();
		String opponentId = mentions.get(0).getId();
		if (senderId.equals(opponentId)) {
			event.getChannel().sendMessage("Нельзя играть против себя.").submit();
			return;
		}
		if (!playerCache.contains(opponentId)) {
			event.getChannel().sendMessage("Оппонент не зарегистрирован.").submit();
			return;
		}
		String raw = event.getMessage().getContentRaw();
		int mentionEnd = raw.indexOf('>') + 1;
		String rest = mentionEnd > 0 ? raw.substring(mentionEnd).trim() : "";
		int bet;
		try {
			bet = Integer.parseInt(rest);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Укажите ставку: +покер @игрок [ставка]").submit();
			return;
		}
		if (bet <= 0) {
			event.getChannel().sendMessage("Ставка должна быть больше нуля.").submit();
			return;
		}
		Player challenger = playerCache.get(senderId);
		Player opponent = playerCache.get(opponentId);
		tavern.playPoker(event, challenger, opponent, bet);
		playerCache.put(senderId, challenger);
		playerCache.put(opponentId, opponent);
	}

	/** +скачки — информация о скачках; +поставить [лошадь] [сумма] — ставка (66) */
	public void horseRacingInfo(MessageReceivedEvent event) {
		event.getChannel().sendMessage(tavern.getHorseRacingInfo()).submit();
	}

	public void betOnHorse(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String[] parts = event.getMessage().getContentDisplay().split("\\s+");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +поставить [лошадь] [сумма]").submit();
			return;
		}
		String horseName = parts[1];
		int bet;
		try {
			bet = Integer.parseInt(parts[2]);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Укажите корректную сумму ставки.").submit();
			return;
		}
		int horseIndex = -1;
		for (int i = 0; i < Tavern.HORSES.length; i++) {
			if (Tavern.HORSES[i].equalsIgnoreCase(horseName)) { horseIndex = i; break; }
		}
		if (horseIndex < 0) {
			event.getChannel().sendMessage("Неизвестная лошадь. Доступны: " + String.join(", ", Tavern.HORSES)).submit();
			return;
		}
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			long now = System.currentTimeMillis();
			if (now - player.getLastHorseRaceTime() < GameBalance.ONE_DAY_MS) {
				long minLeft = (GameBalance.ONE_DAY_MS - (now - player.getLastHorseRaceTime())) / 60000;
				event.getChannel().sendMessage("⏳ Скачки доступны раз в день. Следующие через **" + minLeft + "** мин.").submit();
				return;
			}
			if (player.getMoney() < bet) {
				event.getChannel().sendMessage("Недостаточно монет.").submit();
				return;
			}
			player.setLastHorseRaceTime(now);
			int winnerIdx = tavern.runHorseRace();
			String winnerHorse = Tavern.HORSES[winnerIdx];
			event.getChannel().sendMessage("🏇 Скачки начались! Победитель: **" + winnerHorse + "**").submit();
			if (winnerIdx == horseIndex) {
				int win = bet * Tavern.HORSE_ODDS[horseIndex];
				player.setMoney(player.getMoney() + win - bet);
				playerCache.put(id, player);
				event.getChannel().sendMessage("🏆 Вы выиграли! **" + player.getNickName() + "** получает **" + win + "** монет (x" + Tavern.HORSE_ODDS[horseIndex] + ")!").submit();
				checkRichAchievement(player);
			} else {
				player.setMoney(player.getMoney() - bet);
				playerCache.put(id, player);
				event.getChannel().sendMessage("💸 Ваша лошадь **" + horseName + "** не выиграла. Потеряно **" + bet + "** монет.").submit();
			}
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
		String itemName = raw.substring(0, raw.lastIndexOf(parts[parts.length - 1])).trim();
		ReentrantLock lock = getPlayerLock(id);
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
			checkRichAchievement(player);
		} finally {
			lock.unlock();
		}
	}

	/** +топ кланы — рейтинг кланов (56) */
	public void clanLeaderboard(MessageReceivedEvent event) {
		List<ru.chebe.litvinov.data.Clan> clans = clanManager.getAllClans();
		if (clans.isEmpty()) {
			event.getChannel().sendMessage("Кланов пока нет.").submit();
			return;
		}
		clans.sort((a, b) -> {
			int diff = b.getMembers().size() - a.getMembers().size();
			if (diff != 0) return diff;
			int aLvl = a.getMembers().stream().mapToInt(mid -> {
				Player p = playerCache.get(mid);
				return p != null ? p.getLevel() : 0;
			}).sum();
			int bLvl = b.getMembers().stream().mapToInt(mid -> {
				Player p = playerCache.get(mid);
				return p != null ? p.getLevel() : 0;
			}).sum();
			return bLvl - aLvl;
		});
		var sb = new StringBuilder("🏰 **Топ кланов:**\n");
		for (int i = 0; i < Math.min(10, clans.size()); i++) {
			ru.chebe.litvinov.data.Clan c = clans.get(i);
			int totalLvl = c.getMembers().stream().mapToInt(mid -> {
				Player p = playerCache.get(mid);
				return p != null ? p.getLevel() : 0;
			}).sum();
			sb.append(String.format("%d. **%s** — %d уч., %d суммарный ур.\n", i + 1, c.getName(), c.getMembers().size(), totalLvl));
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +клан банк / +клан положить [amount] / +клан снять [amount] (54) */
	public void clanBankCommand(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане.").submit();
			return;
		}
		String content = event.getMessage().getContentDisplay().trim().toLowerCase();
		if (content.startsWith("+клан положить")) {
			String amountStr = content.substring(14).trim();
			int amount;
			try { amount = Integer.parseInt(amountStr); } catch (NumberFormatException e) {
				event.getChannel().sendMessage("Укажите сумму.").submit(); return;
			}
			String result = clanManager.clanBankDeposit(player.getClanName(), id, amount, player);
			if (result.isEmpty()) {
				event.getChannel().sendMessage("✅ Внесено **" + amount + "** монет в клановый банк.").submit();
			} else {
				event.getChannel().sendMessage("❌ " + result).submit();
			}
		} else if (content.startsWith("+клан снять")) {
			String amountStr = content.substring(11).trim();
			int amount;
			try { amount = Integer.parseInt(amountStr); } catch (NumberFormatException e) {
				event.getChannel().sendMessage("Укажите сумму.").submit(); return;
			}
			String result = clanManager.clanBankWithdraw(player.getClanName(), id, amount, player);
			if (result.isEmpty()) {
				event.getChannel().sendMessage("✅ Снято **" + amount + "** монет из кланового банка.").submit();
			} else {
				event.getChannel().sendMessage("❌ " + result).submit();
			}
		} else {
			event.getChannel().sendMessage(clanManager.getClanBankInfo(player.getClanName())).submit();
		}
	}

	/** +клан улучшения / +клан купить [улучшение] (55) */
	public void clanUpgradesCommand(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане.").submit();
			return;
		}
		String content = event.getMessage().getContentDisplay().trim().toLowerCase();
		if (content.startsWith("+клан купить")) {
			String upgrade = content.substring(12).trim();
			String result = clanManager.purchaseClanUpgrade(player.getClanName(), id, upgrade);
			if (result.isEmpty()) {
				event.getChannel().sendMessage("✅ Улучшение **" + upgrade + "** куплено!").submit();
			} else {
				event.getChannel().sendMessage("❌ " + result).submit();
			}
		} else {
			event.getChannel().sendMessage(clanManager.getClanUpgradesInfo(player.getClanName())).submit();
		}
	}

	/** +клан база [локация] — установить базу клана (57) */
	public void setClanBase(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане.").submit();
			return;
		}
		String location = event.getMessage().getContentDisplay().substring("+клан база".length()).trim().toLowerCase();
		if (location.isEmpty()) {
			event.getChannel().sendMessage("Укажите локацию: +клан база [локация]").submit();
			return;
		}
		if (locationManager.getLocation(location) == null) {
			event.getChannel().sendMessage("Такой локации не существует.").submit();
			return;
		}
		String result = clanManager.setClanBase(player.getClanName(), id, location);
		if (result.isEmpty()) {
			event.getChannel().sendMessage("✅ Клановая база установлена: **" + location + "**").submit();
		} else {
			event.getChannel().sendMessage("❌ " + result).submit();
		}
	}

	/** +война [клан] — вызов на клановую войну (58) */
	public void clanWar(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане.").submit();
			return;
		}
		String targetClan = event.getMessage().getContentDisplay().substring(7).trim().toLowerCase();
		if (targetClan.isEmpty()) {
			event.getChannel().sendMessage("Укажите название клана: +война [клан]").submit();
			return;
		}
		if (targetClan.equals(player.getClanName())) {
			event.getChannel().sendMessage("Нельзя объявить войну своему клану.").submit();
			return;
		}
		ru.chebe.litvinov.data.Clan enemyClan = clanManager.getClan(targetClan);
		if (enemyClan == null) {
			event.getChannel().sendMessage("Клан **" + targetClan + "** не найден.").submit();
			return;
		}
		List<Player> attackers = clanManager.getClanMembers(player.getClanName()).stream()
				.map(playerCache::get).filter(Objects::nonNull).collect(Collectors.toList());
		List<Player> defenders = clanManager.getClanMembers(targetClan).stream()
				.map(playerCache::get).filter(Objects::nonNull).collect(Collectors.toList());
		if (attackers.isEmpty() || defenders.isEmpty()) {
			event.getChannel().sendMessage("Недостаточно участников для войны.").submit();
			return;
		}
		event.getChannel().sendMessage("⚔️ **Клановая война:** **" + player.getClanName() + "** vs **" + targetClan + "**!").submit();
		List<ru.chebe.litvinov.data.Person> atk = attackers.stream().map(p -> (ru.chebe.litvinov.data.Person) p).collect(Collectors.toList());
		List<ru.chebe.litvinov.data.Person> def = defenders.stream().map(p -> (ru.chebe.litvinov.data.Person) p).collect(Collectors.toList());
		battleManager.playerBattle(atk, def, event.getChannel());
		boolean attackersWon = atk.stream().anyMatch(p -> p.getHp() > 0);
		if (attackersWon) {
			event.getChannel().sendMessage("🏆 Клан **" + player.getClanName() + "** победил! Каждый участник получает **" + GameBalance.CLAN_WAR_WIN_MONEY + "** монет!").submit();
			attackers.forEach(p -> changeMoney(p.getId(), GameBalance.CLAN_WAR_WIN_MONEY, true));
		} else {
			event.getChannel().sendMessage("🏆 Клан **" + targetClan + "** победил! Каждый участник получает **" + GameBalance.CLAN_WAR_WIN_MONEY + "** монет!").submit();
			defenders.forEach(p -> changeMoney(p.getId(), GameBalance.CLAN_WAR_WIN_MONEY, true));
		}
	}

	/** +клан повысить @user — повысить роль (59) */
	public void promoteClanMember(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане.").submit();
			return;
		}
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите игрока: +клан повысить @игрок").submit();
			return;
		}
		String targetId = mentions.get(0).getId();
		String result = clanManager.promoteMember(player.getClanName(), id, targetId);
		event.getChannel().sendMessage(result).submit();
	}

	/** +клан выгнать @user — исключить из клана (62) */
	public void kickClanMember(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане.").submit();
			return;
		}
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите игрока: +клан выгнать @игрок").submit();
			return;
		}
		String targetId = mentions.get(0).getId();
		String result = clanManager.kickMember(player.getClanName(), id, targetId);
		if (result.isEmpty()) {
			event.getChannel().sendMessage("✅ Игрок исключён из клана.").submit();
		} else {
			event.getChannel().sendMessage("❌ " + result).submit();
		}
	}

	/** +сезон — топ-5 сезонного рейтинга (73) */
	public void seasonLeaderboard(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		all.sort(Comparator.comparingInt(Player::getLevel).reversed());
		var sb = new StringBuilder("🏅 **Сезонный рейтинг** (топ-5 по уровню)\n");
		for (int i = 0; i < Math.min(5, all.size()); i++) {
			Player p = all.get(i);
			sb.append(String.format("%d. %s — Ур. %d%s\n", i + 1, p.getNickName(), p.getLevel(), p.getPrestige() > 0 ? " ⭐×" + p.getPrestige() : ""));
		}
		sb.append("\n*Топ-3 получат уникальные предметы в конце месяца*");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +престиж — престиж на 100 уровне (74) */
	public void prestige(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getLevel() < GameBalance.PRESTIGE_REQUIRED_LEVEL) {
				event.getChannel().sendMessage("Для престижа нужен **100 уровень** (текущий: " + player.getLevel() + ").").submit();
				return;
			}
			player.setPrestige(player.getPrestige() + 1);
			player.setLevel(1);
			player.setExp(0);
			player.setExpToNextLvl(100);
			player.setStrength(player.getStrength() + GameBalance.PRESTIGE_STAT_BONUS);
			player.setArmor(player.getArmor() + GameBalance.PRESTIGE_STAT_BONUS);
			player.setLuck(player.getLuck() + GameBalance.PRESTIGE_STAT_BONUS);
			player.setMaxHp(player.getMaxHp() + GameBalance.PRESTIGE_STAT_BONUS * 10);
			player.setHp(player.getMaxHp());
			playerCache.put(id, player);
			event.getChannel().sendMessage("⭐ **Престиж " + player.getPrestige() + "!** Уровень сброшен, получено +5 к всем базовым статам.").submit();
		} finally {
			lock.unlock();
		}
	}

	/** +профиль @игрок — профиль другого игрока (79) */
	public void playerProfile(MessageReceivedEvent event) {
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите игрока: +профиль @игрок").submit();
			return;
		}
		String targetId = mentions.get(0).getId();
		Player target = playerCache.get(targetId);
		if (target == null) {
			event.getChannel().sendMessage("Игрок не зарегистрирован.").submit();
			return;
		}
		var sb = new StringBuilder("👤 **Профиль " + target.getNickName() + "**\n");
		sb.append("🎮 Уровень: **").append(target.getLevel()).append("**");
		if (target.getPrestige() > 0) sb.append(" ⭐×").append(target.getPrestige());
		sb.append("\n");
		if (target.getPlayerClass() != null && !target.getPlayerClass().isBlank())
			sb.append("⚔️ Класс: **").append(target.getPlayerClass()).append("**\n");
		if (target.getClanName() != null && !target.getClanName().isBlank())
			sb.append("🏰 Клан: **").append(target.getClanName()).append("**\n");
		sb.append("🏆 Звание: **").append(getTitle(target)).append("**\n");
		List<String> achs = target.getAchievements();
		if (achs != null && !achs.isEmpty())
			sb.append("🌟 Лучшее достижение: **").append(achievementName(achs.get(achs.size() - 1))).append("**\n");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +зал — зал славы сервера (84) */
	public void hallOfFame(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		if (all.isEmpty()) {
			event.getChannel().sendMessage("Зал славы пуст.").submit();
			return;
		}
		Player richest = all.stream().max(Comparator.comparingInt(Player::getMoney)).orElse(null);
		Player highest = all.stream().max(Comparator.comparingInt(Player::getLevel)).orElse(null);
		Player streakKing = all.stream().max(Comparator.comparingInt(Player::getDailyStreak)).orElse(null);
		Player mostAch = all.stream().max(Comparator.comparingInt(p -> p.getAchievements() == null ? 0 : p.getAchievements().size())).orElse(null);
		var sb = new StringBuilder("🏛️ **Зал Славы БЧ-ГРП**\n\n");
		if (richest != null) sb.append("💰 Богатейший: **").append(richest.getNickName()).append("** — ").append(richest.getMoney()).append(" монет\n");
		if (highest != null) sb.append("⭐ Высший уровень: **").append(highest.getNickName()).append("** — ").append(highest.getLevel()).append(" ур.\n");
		if (streakKing != null) sb.append("🔥 Самый длинный стрик: **").append(streakKing.getNickName()).append("** — ").append(streakKing.getDailyStreak()).append(" дн.\n");
		if (mostAch != null) sb.append("🏆 Больше всех достижений: **").append(mostAch.getNickName()).append("** — ").append(mostAch.getAchievements() == null ? 0 : mostAch.getAchievements().size()).append(" достижений\n");
		List<ru.chebe.litvinov.data.Clan> clans = clanManager.getAllClans();
		if (!clans.isEmpty()) {
			ru.chebe.litvinov.data.Clan strongestClan = clans.stream().max(Comparator.comparingInt(c -> c.getMembers().size())).orElse(null);
			if (strongestClan != null) sb.append("🏰 Сильнейший клан: **").append(strongestClan.getName()).append("** — ").append(strongestClan.getMembers().size()).append(" участников");
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	private String getTitle(Player player) {
		int achCount = player.getAchievements() == null ? 0 : player.getAchievements().size();
		if (achCount >= 10) return "Легенда";
		if (achCount >= 5) return "Герой";
		if (achCount >= 3) return "Искатель";
		return "Новичок";
	}

	private void checkRichAchievement(Player player) {
		if (player.getMoney() >= 10000) {
			unlockAchievement(player, "богач");
			playerCache.put(player.getId(), player);
		}
	}

	private void checkExplorerAchievement(Player player) {
		List<String> history = player.getLocationHistory();
		if (history != null && new java.util.HashSet<>(history).containsAll(LocationManager.locationList)) {
			unlockAchievement(player, "исследователь");
			playerCache.put(player.getId(), player);
		}
	}

	private static final List<String> BOSS_ITEMS = List.of(
		"бицушка ровера", "кисточка циника", "корона дарха", "кринж стина", "попка ушаса",
		"око мора", "очко бога", "хуй вущъта", "удача рианель", "шарики лаба", "вонь арктулза",
		"скейт ябыса", "форточка орсона", "месть гордона", "хатка база", "игла бувки",
		"калькулятор сталкера", "язык вороны", "диплом ильи", "кресло чегоба",
		"сиськи ред", "банка эдика", "кринж стина", "корона дарха"
	);

	private void checkCollectorAchievement(Player player) {
		Map<String, Integer> inv = player.getInventory();
		if (inv != null && BOSS_ITEMS.stream().distinct().allMatch(inv::containsKey)) {
			unlockAchievement(player, "коллекционер");
			playerCache.put(player.getId(), player);
		}
	}

	// ---- achievements helpers ----

	private void unlockAchievement(Player player, String achievementId) {
		List<String> achievements = player.getAchievements();
		if (achievements == null) {
			achievements = new ArrayList<>();
			player.setAchievements(achievements);
		}
		if (!achievements.contains(achievementId)) {
			achievements.add(achievementId);
		}
	}

	private String achievementName(String id) {
		return switch (id) {
			case "первые_шаги" -> "Первые шаги — зарегистрироваться в игре";
			case "стрик_3"     -> "Постоянство — получить бонус 3 дня подряд";
			case "стрик_7"     -> "Недельный игрок — получить бонус 7 дней подряд";
			case "классовый"   -> "Классовый игрок — выбрать класс персонажа";
			case "торговец"    -> "Торговец — передать предмет другому игроку";
			case "дуэлянт"    -> "Дуэлянт — победить в дуэли";
			case "первый_рейд" -> "Первый рейд — участие в рейде";
			case "победитель_рейда" -> "Победитель рейда — победа в рейде";
			case "100_pvp"     -> "100 PvP побед — одержать 100 побед в PvP";
			case "10_уровень"  -> "10 уровень — достичь 10 уровня";
			case "богач"       -> "Богач — накопить 10 000 монет";
			case "коллекционер" -> "Коллекционер — собрать все предметы боссов";
			case "исследователь" -> "Исследователь — посетить все локации";
			case "ветеран"     -> "Ветеран — убить 50 мобов";
			case "клановый_чел" -> "Клановый — вступить в клан";
			case "легенда"     -> "Легенда — достичь 50 уровня";
			default -> id;
		};
	}

	/** Безопасно увеличивает прогресс ежедневного квеста (игнорирует если сервис не задан). */
	private void questProgress(String userId, String type, int amount) {
		if (dailyQuestService != null) {
			dailyQuestService.incrementProgress(userId, type, amount);
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

	// Возвращает список игроков клана в текущей локации
	private List<Player> getPlayersByClan(Player player) {
		return clanManager.getClanMembers(player.getClanName()).stream()
						.map(playerCache::get)
						.filter(p -> p != null && p.getLocation().equals(player.getLocation()))
						.collect(Collectors.toList());
	}

	/**
	 * Обрабатывает команду игры в рулетку. Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой и выбором
	 */
	public void playRoulette(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Играть в рулетку можно только в таверне!").queue();
			return;
		}

		String[] parts = event.getMessage().getContentDisplay().split(" ");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +рулетка [ставка] [красный/черный/0-36]").queue();
			return;
		}

		try {
			int bid = Integer.parseInt(parts[1]);
			String bet = parts[2];
			int moneyBeforeRoulette = player.getMoney();
			player = tavern.playRoulette(event, player, bid, bet);
			if (player.getMoney() > moneyBeforeRoulette) {
				questProgress(player.getId(), "WIN_TAVERN", 1);
				questProgress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBeforeRoulette);
			}
			playerCache.put(player.getId(), player);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки!").queue();
		}
	}

	/**
	 * Обрабатывает команду игры «камень-ножницы-бумага». Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой и выбором
	 */
	public void rockPaperScissors(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Играть можно только в таверне!").queue();
			return;
		}

		String[] parts = event.getMessage().getContentDisplay().split(" ");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +кнб [ставка] [камень/ножницы/бумага]").queue();
			return;
		}

		try {
			int bid = Integer.parseInt(parts[1]);
			String choice = parts[2];
			int moneyBeforeKnb = player.getMoney();
			player = tavern.rockPaperScissors(event, player, bid, choice);
			if (player.getMoney() > moneyBeforeKnb) {
				questProgress(player.getId(), "WIN_TAVERN", 1);
				questProgress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBeforeKnb);
			}
			playerCache.put(player.getId(), player);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки!").queue();
		}
	}

	/**
	 * Обрабатывает команду игры «угадай число». Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой и числом-предположением
	 */
	public void guessTheNumber(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Играть можно только в таверне!").queue();
			return;
		}

		String[] parts = event.getMessage().getContentDisplay().split(" ");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +число [ставка] [число от 1 до 10]").queue();
			return;
		}

		try {
			int bid = Integer.parseInt(parts[1]);
			int guess = Integer.parseInt(parts[2]);

			if (guess < 1 || guess > 10) {
				event.getChannel().sendMessage("Число должно быть от 1 до 10!").queue();
				return;
			}

			int moneyBefore = player.getMoney();
			player = tavern.guessTheNumber(event, player, bid, guess);
			// Квест «Везунчик»: победа зафиксирована по увеличению денег сверх ставки
			if (player.getMoney() > moneyBefore && player.getActiveEvent() != null
					&& "Везунчик".equals(player.getActiveEvent().getType())) {
				player.getActiveEvent().setAttempt(1);
			}
			if (player.getMoney() > moneyBefore) {
				questProgress(player.getId(), "WIN_TAVERN", 1);
				questProgress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBefore);
			}
			playerCache.put(player.getId(), player);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки или числа!").queue();
		}
	}
}
