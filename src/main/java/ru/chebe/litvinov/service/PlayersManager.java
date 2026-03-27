package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static ru.chebe.litvinov.Constants.MIN_LVL_TO_CLAN_CREATE;
import static ru.chebe.litvinov.Constants.MIN_LVL_TO_CLAN_JOIN;

/**
 * Главный сервис управления игроками.
 * Координирует все игровые действия: создание персонажа, перемещение, бой, квесты,
 * инвентарь, торговля, игры в таверне, клановые операции и ежедневные бонусы.
 * Использует per-player блокировки для потокобезопасного изменения характеристик.
 */
public class PlayersManager implements ru.chebe.litvinov.service.interfaces.IPlayersManager {
	private final PlayerRepository playerCache;
	private final LocationManager locationManager;
	private final ItemsManager itemsManager;
	private final BattleManager battleManager;
	private final EventsManager eventsManager;
	private final ClanManager clanManager;
	private final Tavern tavern;
	private final Random random = new Random();

	/**
	 * Per-player ReentrantLock для атомарных read-modify-write операций.
	 * Использование единой стратегии ReentrantLock (без смешения с synchronized).
	 */
	private final ConcurrentHashMap<String, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

	private ReentrantLock getPlayerLock(String id) {
		return playerLocks.computeIfAbsent(id, k -> new ReentrantLock());
	}
	private static final int DAILY_BONUS = 100;

	private static final Map<Integer, Integer> xpMap = generateXpMap();
	private static final Map<Integer, Integer> hpMap = generateHpMap();
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
	 */
	public PlayersManager(PlayerRepository playerCache, LocationManager locationManager, ItemsManager itemsManager,
	                      BattleManager battleManager, EventsManager eventsManager, ClanManager clanManager, Tavern tavern) {
		this.playerCache = playerCache;
		this.locationManager = locationManager;
		this.itemsManager = itemsManager;
		this.battleManager = battleManager;
		this.eventsManager = eventsManager;
		this.clanManager = clanManager;
		this.tavern = tavern;
	}

	/**
	 * Отправляет игроку его текущие характеристики.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void getPlayerInfo(MessageReceivedEvent event) {
		String id = event.getMessage().getAuthor().getId();
		event.getChannel().sendMessage(playerCache.get(id).toString()).submit();
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
			player.setHp(hp);
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
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return 0;
			if (increase) {
				player.setMoney(player.getMoney() + money);
			} else {
				player.setMoney(player.getMoney() - money);
			}
			playerCache.put(id, player);
			return player.getMoney();
		} finally {
			lock.unlock();
		}
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
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return 0;
			if (increase) {
				player.setReputation(player.getReputation() + reputation);
			} else {
				player.setReputation(player.getReputation() - reputation);
			}
			playerCache.put(id, player);
			return player.getReputation();
		} finally {
			lock.unlock();
		}
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
			if (player.getExp() + xp >= player.getExpToNextLvl()) {
				player.setExp(player.getExp() + xp % player.getExpToNextLvl());
				player.setLevel(player.getLevel() + 1);
				player.setExpToNextLvl(xpMap.get(player.getLevel()));
				player.setHp(getMaxHp(player));
			} else {
				player.setExp(player.getExp() + xp);
			}
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	private static Map<Integer, Integer> generateXpMap() {
		Map<Integer, Integer> xpMap = new HashMap<>();
		int xp = 100;
		for (int i = 2; i <= 100; i++) {
			xpMap.put(i, xp);
			xp += 100;
		}
		return xpMap;
	}

	private static Map<Integer, Integer> generateHpMap() {
		Map<Integer, Integer> hpMap = new HashMap<>();
		int hp = 100;
		for (int i = 1; i <= 100; i++) {
			hpMap.put(i, hp);
			hp += 10;
		}
		return hpMap;
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
			playerCache.put(id, new Player(nickName, id));
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

	/**
	 * Обрабатывает смерть игрока: списывает 10% денег, восстанавливает HP и перемещает на Респаун.
	 *
	 * @param dead игрок, который погиб
	 */
	public void deathOfPlayer(Player dead) {
		dead.setMoney((int) (dead.getMoney() * 0.9));
		dead.setHp(dead.getMaxHp());
		dead.setLocation("респаун");
		playerCache.put(dead.getId(), dead);
		locationManager.movePlayerInPopulation(dead, "респаун");
	}

	/**
	 * Обрабатывает команду использования активируемого предмета из инвентаря игрока.
	 *
	 * @param event событие Discord-сообщения с названием предмета
	 */
	public void useItem(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		String message = event.getMessage().getContentDisplay().substring(13).trim().toLowerCase();
		if (player.getInventory().containsKey(message.toLowerCase())) {
			Item item = itemsManager.getItem(message);
			if (item.isAction()) {
				if (item.getHealth() > 0) {
					int hp = changeHp(player.getId(), item.getHealth(), true);
					event.getChannel().sendMessage("Теперь у тебя " + hp + " здоровья").submit();
				}
				if (item.getArmor() > 0) {
					int armor = changeArmor(player.getId(), item.getArmor(), true);
					event.getChannel().sendMessage("Теперь у тебя " + armor + " брони").submit();
				}
				if (item.getLuck() > 0) {
					int luck = changeLuck(player.getId(), item.getLuck(), true);
					event.getChannel().sendMessage("Теперь у тебя " + luck + " удачи").submit();
				}
				if (item.getStrength() > 0) {
					int str = changeStrength(player.getId(), item.getStrength(), true);
					event.getChannel().sendMessage("Теперь у тебя " + str + " силы").submit();
				}
				if (item.getReputation() > 0) {
					int rep = changeReputation(player.getId(), item.getReputation(), true);
					event.getChannel().sendMessage("Теперь у тебя " + rep + " репутации").submit();
				}
				deleteItem(player.getId(), item.getName());
			} else {
				event.getChannel().sendMessage("Этот предмет нельзя использовать").submit();
			}
		} else {
			event.getChannel().sendMessage("Такого предмета нет в твоём инвентаре").submit();
		}
	}

	private int changeArmor(String id, int armor, boolean increase) {
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player == null) return 0;
			player.setArmor(increase ?
							player.getArmor() + armor :
							player.getArmor() - armor);
			playerCache.put(id, player);
			return player.getArmor();
		} finally {
			lock.unlock();
		}
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
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return 0;
			if (increase) {
				player.setLuck(player.getLuck() + luck);
			} else {
				player.setLuck(player.getLuck() - luck);
			}
			playerCache.put(id, player);
			return player.getLuck();
		} finally {
			lock.unlock();
		}
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
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return 0;
			if (increase) {
				player.setStrength(player.getStrength() + strength);
			} else {
				player.setStrength(player.getStrength() - strength);
			}
			playerCache.put(id, player);
			return player.getStrength();
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
		String bidText = event.getMessage().getContentDisplay().substring(7);
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
		player = tavern.diceStart(event, player, bid);
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
		nextLocation = locationManager.movePlayerInPopulation(player, nextLocation.getName());
		player.setLocation(nextLocation.getName());
		playerCache.put(player.getId(), player);
		var token = player.getInventory().get("токен телепорта") == null ? 0 : player.getInventory().get("токен телепорта");
		String teleport = isTeleport ? " с помощью токена телепорта, осталось " + token : "";
		event.getChannel().sendMessage("Ты успешно переместился в локацию - " + nextLocation.getName() + teleport
						+ "\nВ этой локации находятся следующие игроки: " + nextLocation.getPopulationByName().toString()).submit();
		if (eventsManager.transferEvent(event, nextLocation)) {
			int playerHp = battleManager.mobBattle(player, event.getChannel());
			if (playerHp > 0) {
				changeHp(player.getId(), playerHp);
				changeXp(player.getId(), 10);
				changeMoney(player.getId(), 10, true);
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
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getActiveEvent() != null) {
			event.getChannel().sendMessage("У тебя уже есть активный квест, сначала заверши его").submit();
		} else {
			player.setActiveEvent(eventsManager.assignEvent(locationManager.getLocationList()));
			event.getChannel().sendMessage("Ты получил новое задание :\n" + player.getActiveEvent().toString()).submit();
			playerCache.put(event.getAuthor().getId(), player);
		}
	}

	/**
	 * Заменяет текущий квест игрока на новый за 5 монет.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void changeEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getActiveEvent() == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
		} else if (player.getMoney() >= 5) {
			player.setActiveEvent(eventsManager.assignEvent(locationManager.getLocationList()));
			changeMoney(event.getAuthor().getId(), 5, false);
			event.getChannel().sendMessage("Ты потартил 5 денег и получил новое задание :\n" + player.getActiveEvent().toString()).submit();
			playerCache.put(event.getAuthor().getId(), player);
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
		var player = playerCache.get(event.getAuthor().getId());
		String message = event.getMessage().getContentDisplay().substring(16).trim().toLowerCase();
		player.setAnswer(message);
		var activeEvent = player.getActiveEvent();
		if (activeEvent == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
		} else if (eventsManager.checkEvent(activeEvent, player)) {
			player.setActiveEvent(null);
			playerCache.put(event.getAuthor().getId(), player);
			changeMoney(player.getId(), activeEvent.getMoneyReward(), true);
			changeXp(player.getId(), activeEvent.getXpReward());
			event.getChannel().sendMessage("Ты успешно завершил свой квест, опыт " + activeEvent.getXpReward() + " и деньги " + activeEvent.getMoneyReward() + " зачислены на твой счёт").submit();
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
		var loc = locationManager.getLocation(player.getLocation());
		if (loc.getBoss() == null) {
			event.getChannel().sendMessage("В этой локации нет босса, перейди в другую если хочешь присесть на бутылку").submit();
		} else {
			event.getChannel().sendMessage("Ты отважился бросить вызов боссу по имени " + loc.getBoss() + " земля тебе пухом братишка").submit();
			var players = getPlayersByClan(player);
			// Convert List<Player> to List<Person>
			List<Person> playersAsPerson = players.stream()
							.map(p -> (Person) p)
							.collect(Collectors.toList());
			battleManager.bossBattle(playersAsPerson, loc.getBoss(), event.getChannel());
			for (Person play : players) {
				if (play.getHp() > 0) {
					changeHp(((Player) play).getId(), play.getHp());
					changeXp(((Player) play).getId(), 1000);
					changeMoney(((Player) play).getId(), 1000, true);
					String bossItem = battleManager.getBossItemName(loc.getBoss());
					addNewItem(((Player) play).getId(), bossItem);
					event.getChannel().sendMessage("В твой инвентарь добавлен предмет " + bossItem).submit();
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
					// Используем методы PlayersManager для изменений
					changeMoney(pObj.getId(), 200, true);
					changeXp(pObj.getId(), 150);
					event.getChannel().sendMessage(pObj.getNickName() + " получает награду!").queue();
				}
			} else {
				// Используем метод deathOfPlayer из PlayersManager
				deathOfPlayer(pObj);
				event.getChannel().sendMessage(pObj.getNickName() + " погиб!").queue();
			}
		});

		Location updatedLoc = locationManager.getLocation(player.getLocation());
		event.getChannel().sendMessage("Оставшиеся игроки: " + updatedLoc.getPopulationByName()).queue();
	}

	/**
	 * Начисляет игроку ежедневный бонус (100 монет).
	 * Бонус доступен раз в 24 часа.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void dailyBonus(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getDailyTime() < System.currentTimeMillis() - (24 * 60 * 60 * 1000)) {
			event.getChannel().sendMessage("Вы получили ежедневный бонус").submit();
			player.setDailyTime(System.currentTimeMillis());
			playerCache.put(player.getId(), player);
			changeMoney(player.getId(), DAILY_BONUS, true);
		} else {
			int hours = (int) (24 - (((System.currentTimeMillis() - player.getDailyTime()) / (60 * 60 * 1000))));
			event.getChannel().sendMessage("Вы уже получили ежедневный бонус приходите через " + hours + " часов").submit();
		}
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
			event.getChannel().sendMessage("Вы не можете присоединиться к клану раньше, чем достигните 10 уровня").submit();
			return;
		}
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			String result = clanManager.joinClan(clanName, player.getId());
			if (!result.isEmpty()) {
				player.setClanName(result);
				playerCache.put(player.getId(), player);
				event.getChannel().sendMessage("Вы присоединились к клану " + player.getClanName()).submit();
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
			player = tavern.playRoulette(event, player, bid, bet);
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
			player = tavern.rockPaperScissors(event, player, bid, choice);
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
			
			player = tavern.guessTheNumber(event, player, bid, guess);
			playerCache.put(player.getId(), player);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки или числа!").queue();
		}
	}
}
