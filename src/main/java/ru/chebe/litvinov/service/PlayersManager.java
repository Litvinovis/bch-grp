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

	private final PlayerLocks playerLocks = new PlayerLocks();
	private final AchievementService achievements;
	private final DuelService duelService;

	// Кулдаун на убийство босса
	private DailyQuestService dailyQuestService;
	private final QuestProgressTracker quests = new QuestProgressTracker();
	private final MiniGamesService miniGames;
	private final PlayerStatsService stats;
	private final InventoryService inventory;
	private final CombatService combat;

	// Новые менеджеры (items 85-150)
	private PetManager petManager;
	private ProfessionManager professionManager;
	private TerritoryManager territoryManager;
	private WorldEventManager worldEventManager;
	private FactionManager factionManager;
	private BountyManager bountyManager;
	private ArenaManager arenaManager;
	private TournamentManager tournamentManager;

	// Пагинация инвентаря (item 96)
	private static final java.util.concurrent.ConcurrentHashMap<String, Integer> inventoryPages = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int INVENTORY_PAGE_SIZE = 10;

	// Редкие достижения для анонсов (item 100)
	private net.dv8tion.jda.api.JDA jda;
	private java.util.Set<String> allowedChannelIds;

	/** Устанавливает сервис ежедневных квестов (вызывается после конструктора). */
	public void setDailyQuestService(DailyQuestService dailyQuestService) {
		quests.setDailyQuestService(dailyQuestService);
		this.dailyQuestService = dailyQuestService;
	}

	public void setPetManager(PetManager petManager) { this.petManager = petManager; }
	public void setProfessionManager(ProfessionManager professionManager) { this.professionManager = professionManager; }
	public void setTerritoryManager(TerritoryManager territoryManager) {
		this.territoryManager = territoryManager;
		inventory.setTerritoryManager(territoryManager);
	}
	public void setWorldEventManager(WorldEventManager worldEventManager) { this.worldEventManager = worldEventManager; }
	public void setFactionManager(FactionManager factionManager) {
		this.factionManager = factionManager;
		inventory.setFactionManager(factionManager);
		combat.setFactionManager(factionManager);
	}
	public void setBountyManager(BountyManager bountyManager) {
		this.bountyManager = bountyManager;
		combat.setBountyManager(bountyManager);
	}
	public void setArenaManager(ArenaManager arenaManager) { this.arenaManager = arenaManager; }
	public void setTournamentManager(TournamentManager tournamentManager) { this.tournamentManager = tournamentManager; }
	public void setJda(net.dv8tion.jda.api.JDA jda) {
		this.jda = jda;
		achievements.setJda(jda);
	}
	public void setAllowedChannelIds(java.util.Set<String> allowedChannelIds) {
		this.allowedChannelIds = allowedChannelIds;
		achievements.setAllowedChannelIds(allowedChannelIds);
	}

	private ReentrantLock getPlayerLock(String id) {
		return playerLocks.get(id);
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
		this.achievements = new AchievementService(playerCache);
		this.stats = new PlayerStatsService(playerCache, locationManager, playerLocks, achievements);
		this.inventory = new InventoryService(playerCache, itemsManager, playerLocks, achievements, stats, quests);
		this.combat = new CombatService(playerCache, battleManager, npcManager, clanManager, locationManager,
				playerLocks, achievements, stats, quests, inventory);
		this.miniGames = new MiniGamesService(playerCache, tavern, playerLocks, achievements, quests);
		this.duelService = new DuelService(playerCache, this::getPlayerLock, achievements::unlock);
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
			extra += "\n🌟 Редкое достижение: **" + achievements.name(achs.get(achs.size() - 1)) + "**";
		}
		event.getChannel().sendMessage(statsMsg + extra).submit();
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
			achievements.unlock(newPlayer, "первые_шаги");
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



	/** Делегат к {@link PlayerStatsService}. */
	public int getXp(Player player) { return stats.getXp(player); }

	/** Делегат к {@link PlayerStatsService}. */
	public int getMaxHp(Player player) { return stats.getMaxHp(player); }

	/** Делегат к {@link PlayerStatsService}. */
	public void changeHp(String id, int hp) { stats.changeHp(id, hp); }

	/** {@inheritDoc} */
	@Override
	public int changeHp(String id, int hp, boolean increase) { return stats.changeHp(id, hp, increase); }

	/** {@inheritDoc} */
	@Override
	public int changeMoney(String id, int money, boolean increase) { return stats.changeMoney(id, money, increase); }

	/** Делегат к {@link PlayerStatsService}. */
	public int changeReputation(String id, int reputation, boolean increase) { return stats.changeReputation(id, reputation, increase); }

	/** {@inheritDoc} */
	@Override
	public void changeXp(String id, int xp) { stats.changeXp(id, xp); }

	/** Делегат к {@link PlayerStatsService}. */
	public int changeLuck(String id, int luck, boolean increase) { return stats.changeLuck(id, luck, increase); }

	/** Делегат к {@link PlayerStatsService}. */
	public int changeStrength(String id, int strength, boolean increase) { return stats.changeStrength(id, strength, increase); }

	/** {@inheritDoc} */
	@Override
	public void deathOfPlayer(Player dead) { stats.deathOfPlayer(dead); }

	/** {@inheritDoc} */
	@Override
	public void getInventoryInfo(MessageReceivedEvent event) { inventory.getInventoryInfo(event); }

	/** Делегат к {@link InventoryService}. */
	public void addNewItem(String id, String item) { inventory.addNewItem(id, item); }

	/** Делегат к {@link InventoryService}. */
	public void deleteItem(String id, String item) { inventory.deleteItem(id, item); }

	/** {@inheritDoc} */
	@Override
	public void useItem(MessageReceivedEvent event) { inventory.useItem(event); }

	/** Делегат к {@link InventoryService}. */
	public void removeExpiredBuffs(String playerId) { inventory.removeExpiredBuffs(playerId); }

	/** {@inheritDoc} */
	@Override
	public void sellItem(MessageReceivedEvent event) { inventory.sellItem(event); }

	/** {@inheritDoc} */
	@Override
	public void buyItem(MessageReceivedEvent event) { inventory.buyItem(event); }

	/** {@inheritDoc} */
	@Override
	public void upgradeItem(MessageReceivedEvent event) { inventory.upgradeItem(event); }

	/** {@inheritDoc} */
	@Override
	public void compareItems(MessageReceivedEvent event) { inventory.compareItems(event); }

	/** {@inheritDoc} */
	@Override
	public void tradeItem(MessageReceivedEvent event) { inventory.tradeItem(event); }

	/** {@inheritDoc} */
	@Override
	public void bankCommand(MessageReceivedEvent event) { inventory.bankCommand(event); }

	/** {@inheritDoc} */
	@Override
	public void fightNpc(MessageReceivedEvent event) { combat.fightNpc(event); }

	/** {@inheritDoc} */
	@Override
	public void bossFight(MessageReceivedEvent event) { combat.bossFight(event); }

	/** {@inheritDoc} */
	@Override
	public void playersFight(MessageReceivedEvent event) { combat.playersFight(event); }

	/** {@inheritDoc} */
	@Override
	public void clanNpcFight(MessageReceivedEvent event) { combat.clanNpcFight(event); }

	/** {@inheritDoc} */
	@Override
	public void lastBattleLog(MessageReceivedEvent event) { combat.lastBattleLog(event); }

	public Player getPlayer(String id) {
		return playerCache.get(id);
	}


	/**
	 * Обрабатывает команду использования активируемого предмета из инвентаря игрока.
	 *
	 * @param event событие Discord-сообщения с названием предмета
	 */
	private static final long BUFF_DURATION_MS = 30 * 60 * 1000L; // 30 минут









	/** {@inheritDoc} */
	@Override
	public void dieCast(MessageReceivedEvent event) { miniGames.dieCast(event); }

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
		achievements.checkExplorer(player);

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
					stats.changeHp(player.getId(), currentHp + hpToRestore);
				}
				stats.changeXp(player.getId(), GameBalance.MOB_KILL_XP);
				stats.changeMoney(player.getId(), GameBalance.MOB_KILL_MONEY, true);

				// Квест «Охота»: считаем убитых мобов
				player = playerCache.get(player.getId());
				if (player != null) {
					if (player.getActiveEvent() != null && "Охота".equals(player.getActiveEvent().getType())) {
						player.getActiveEvent().setAttempt(player.getActiveEvent().getAttempt() + 1);
					}
					// Трекинг убийств мобов (71)
					player.setMobKills(player.getMobKills() + 1);
					if (player.getMobKills() >= 50) achievements.unlock(player, "ветеран");
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
				stats.deathOfPlayer(player);
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
			quests.progress(playerId, "EARN_GOLD", activeEvent.getMoneyReward());
			if (factionManager != null) factionManager.addRep(playerId, "МАГИ", 2);
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
				achievements.unlock(player, "клановый_чел");
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
			achievements.unlock(player, "классовый");
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
		String description = achievements.describeAll(player);
		event.getChannel().sendMessage(description != null
				? description
				: "У вас пока нет достижений. Играйте, чтобы их получить!").submit();
	}


	/** Вызов игрока на дуэль (+вызов @игрок). */
	public void challengeDuel(MessageReceivedEvent event) { duelService.challengeDuel(event); }

	/** Принять вызов на дуэль (+принять). */
	public void acceptDuel(MessageReceivedEvent event) { duelService.acceptDuel(event); }

	/** Отказаться от дуэли (+отказать). */
	public void declineDuel(MessageReceivedEvent event) { duelService.declineDuel(event); }



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
				achievements.checkRich(player);
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

	/** {@inheritDoc} */
	@Override
	public void playPoker(MessageReceivedEvent event) { miniGames.playPoker(event); }

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
		if (bet <= 0) {
			event.getChannel().sendMessage("Ставка должна быть больше нуля.").submit();
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
				achievements.checkRich(player);
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
		if (qty <= 0) {
			event.getChannel().sendMessage("Количество должно быть больше нуля.").submit();
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
			achievements.checkRich(player);
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
			attackers.forEach(p -> stats.changeMoney(p.getId(), GameBalance.CLAN_WAR_WIN_MONEY, true));
		} else {
			event.getChannel().sendMessage("🏆 Клан **" + targetClan + "** победил! Каждый участник получает **" + GameBalance.CLAN_WAR_WIN_MONEY + "** монет!").submit();
			defenders.forEach(p -> stats.changeMoney(p.getId(), GameBalance.CLAN_WAR_WIN_MONEY, true));
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
			sb.append("🌟 Лучшее достижение: **").append(achievements.name(achs.get(achs.size() - 1))).append("**\n");
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





	// ---- achievements helpers ----




	// ---- Item 91: Admin hot reload ----
	/** +admin reload — перезагрузка конфигов */
	public void adminReload(MessageReceivedEvent event) {
		try {
			LocationManager.init(null);
			event.getChannel().sendMessage("✅ Конфиги перезагружены.").submit();
		} catch (Exception e) {
			event.getChannel().sendMessage("❌ Ошибка перезагрузки: " + e.getMessage()).submit();
		}
	}

	// ---- Item 98: Quest progress bar ----
	public static String progressBar(int current, int total) {
		if (total <= 0) return "[░░░░░░░░] 0/0";
		int filled = (int) (8.0 * Math.min(current, total) / total);
		return "[" + "█".repeat(filled) + "░".repeat(8 - filled) + "] " + current + "/" + total;
	}

	// ---- Item 99: Online command ----
	/** +онлайн — количество активных игроков */
	public void onlineCommand(MessageReceivedEvent event) {
		long now = System.currentTimeMillis();
		long oneDayAgo = now - 24 * 60 * 60 * 1000L;
		List<Player> all = playerCache.getAll();
		List<Player> online = all.stream()
			.filter(p -> p.getDailyTime() > oneDayAgo)
			.collect(Collectors.toList());
		var sb = new StringBuilder("🟢 **Онлайн за последние 24 часа:** " + online.size() + " игроков\n");
		online.stream().limit(20).forEach(p -> sb.append("• ").append(p.getNickName()).append("\n"));
		if (online.size() > 20) sb.append("...и ещё ").append(online.size() - 20).append(" игроков");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	// ---- Item 100: Rare achievement broadcast ----

	// ---- Items 101-108: Pet system ----
	/** Grants a pet of the given type to the player if they have no pet. */
	@Override
	public void grantPetIfNone(String playerId, String petType) {
		ReentrantLock lock = getPlayerLock(playerId);
		lock.lock();
		try {
			Player p = playerCache.get(playerId);
			if (p != null && p.getPet() == null) {
				p.setPet(new ru.chebe.litvinov.data.Pet(petType));
				playerCache.put(playerId, p);
			}
		} finally { lock.unlock(); }
	}

	/** +питомец — информация о питомце */
	public void petCommand(MessageReceivedEvent event) {
		if (petManager != null) petManager.getPetInfo(event);
		else event.getChannel().sendMessage("Система питомцев недоступна.").submit();
	}

	/** +кормить [предмет] — кормить питомца */
	public void feedPet(MessageReceivedEvent event) {
		if (petManager != null) petManager.feedPet(event);
		else event.getChannel().sendMessage("Система питомцев недоступна.").submit();
	}

	/** {@inheritDoc} */
	@Override
	public void mountRacingInfo(MessageReceivedEvent event) { miniGames.mountRacingInfo(event); }

	/** {@inheritDoc} */
	@Override
	public void mountRacingRun(MessageReceivedEvent event) { miniGames.mountRacingRun(event); }

	// ---- Items 109-116: Professions ----
	/** +профессия — выбор/инфо профессии */
	public void professionCommand(MessageReceivedEvent event) {
		if (professionManager != null) professionManager.chooseProfession(event);
		else event.getChannel().sendMessage("Система профессий недоступна.").submit();
	}

	/** +добыть — добыть ресурс */
	public void gatherResource(MessageReceivedEvent event) {
		if (professionManager != null) professionManager.gatherResource(event);
		else event.getChannel().sendMessage("Система профессий недоступна.").submit();
	}

	/** +создать [рецепт] — крафт предмета профессии */
	public void professionCraftItem(MessageReceivedEvent event) {
		if (professionManager != null) professionManager.craftItem(event);
		else event.getChannel().sendMessage("Система профессий недоступна.").submit();
	}

	/** +рецепты — список рецептов профессии */
	public void showProfessionRecipes(MessageReceivedEvent event) {
		if (professionManager != null) professionManager.showRecipes(event);
		else event.getChannel().sendMessage("Система профессий недоступна.").submit();
	}

	/** +биржа ресурсов — биржа ресурсов */
	public void resourceMarket(MessageReceivedEvent event) {
		if (professionManager != null) professionManager.resourceMarket(event);
		else event.getChannel().sendMessage("Система профессий недоступна.").submit();
	}

	// ---- Items 117-123: Territories ----
	/** +захватить [локация] — захватить территорию */
	public void captureTerritory(MessageReceivedEvent event) {
		if (territoryManager != null) territoryManager.captureTerritory(event);
		else event.getChannel().sendMessage("Система территорий недоступна.").submit();
	}

	/** +осада / +осада статус */
	public void siegeCommand(MessageReceivedEvent event) {
		if (territoryManager != null) territoryManager.siegeStart(event);
		else event.getChannel().sendMessage("Система осад недоступна.").submit();
	}

	/** +крепость */
	public void fortressCommand(MessageReceivedEvent event) {
		if (territoryManager != null) territoryManager.buildFortress(event);
		else event.getChannel().sendMessage("Система крепостей недоступна.").submit();
	}

	/** +карта кланов */
	public void territoryClanMap(MessageReceivedEvent event) {
		if (territoryManager != null) territoryManager.territoryClanMap(event);
		else event.getChannel().sendMessage("Карта кланов недоступна.").submit();
	}

	/** +альянс [клан] */
	public void declareAlliance(MessageReceivedEvent event) {
		if (territoryManager != null) territoryManager.declareAlliance(event);
		else event.getChannel().sendMessage("Система альянсов недоступна.").submit();
	}

	// ---- Items 124-130: World events ----
	/** +мировой босс */
	public void worldBossAttack(MessageReceivedEvent event) {
		if (worldEventManager != null) worldEventManager.worldBossAttack(event);
		else event.getChannel().sendMessage("Мировой босс недоступен.").submit();
	}

	/** +нашествие */
	public void invasionStatus(MessageReceivedEvent event) {
		if (worldEventManager != null) worldEventManager.invasionStatus(event);
		else event.getChannel().sendMessage("Нашествие недоступно.").submit();
	}

	/** +кризис статус */
	public void crisisStatus(MessageReceivedEvent event) {
		if (worldEventManager != null) worldEventManager.crisisStatus(event);
		else event.getChannel().sendMessage("Статус кризиса недоступен.").submit();
	}

	/** +сезон — сезонный предмет */
	public void showSeason(MessageReceivedEvent event) {
		if (worldEventManager != null) worldEventManager.showSeason(event);
		else event.getChannel().sendMessage("Сезон недоступен.").submit();
	}

	/** +турнир сервера */
	public void serverTournament(MessageReceivedEvent event) {
		if (tournamentManager != null) tournamentManager.serverTournament(event);
		else event.getChannel().sendMessage("Турнир сервера недоступен.").submit();
	}

	// ---- Items 131-137: Classes & Skills ----
	/** +скиллы — список навыков */
	public void showSkills(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		String playerClass = player.getPlayerClass() != null ? player.getPlayerClass() : "";
		int points = player.getSkillPoints();
		Map<String, Integer> skills = player.getSkills() != null ? player.getSkills() : new java.util.HashMap<>();
		var sb = new StringBuilder("⚡ **Навыки** | Очков доступно: **" + points + "**\n\n");
		sb.append("Класс: **").append(playerClass.isEmpty() ? "не выбран" : playerClass).append("**\n\n");
		getAvailableSkills(playerClass).forEach((skillName, desc) -> {
			int invested = skills.getOrDefault(skillName, 0);
			sb.append("• **").append(skillName).append("** (").append(invested).append(" ур.) — ").append(desc).append("\n");
		});
		sb.append("\n+1 очко навыков каждые 5 уровней. Вложить: **+вложить [навык]**");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	private Map<String, String> getAvailableSkills(String playerClass) {
		return switch (playerClass) {
			case "ВОИН" -> Map.of("берсерк", "+15% урона", "второе дыхание", "реген 20 HP при <20% HP", "стальная кожа", "+5 брони");
			case "МАГ" -> Map.of("молния", "первая атака 150% урона", "телепорт", "бесплатное перемещение раз в день", "щит маны", "+30% уклонение");
			case "РАЗБОЙНИК" -> Map.of("ядовитый клинок", "10% яд за раунд", "уклонение мастера", "+20% уклонение", "воровство", "10% денег при победе");
			case "ПАЛАДИН" -> Map.of("священный щит", "+10 брони", "исцеление", "50 HP раз в бой", "аура защиты", "+5 брони союзникам");
			case "НЕКРОМАНТ" -> Map.of("армия мертвых", "призвать 2 нежити", "проклятие", "-20% урона врага на 3 хода", "жизнеотнятие", "кража 20 HP");
			case "СЛЕДОПЫТ" -> Map.of("следопыт", "+20% ресурсов", "природная ловушка", "враг пропускает ход", "охотничий инстинкт", "+20% дроп");
			default -> Map.of("базовый удар", "+5% урона");
		};
	}

	/** +вложить [навык] — вложить очко в навык */
	public void investSkill(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String skillName = event.getMessage().getContentDisplay().substring(9).trim().toLowerCase();
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getSkillPoints() <= 0) {
				event.getChannel().sendMessage("У тебя нет очков навыков. Повышай уровень!").submit();
				return;
			}
			String playerClass = player.getPlayerClass() != null ? player.getPlayerClass() : "";
			if (!getAvailableSkills(playerClass).containsKey(skillName)) {
				event.getChannel().sendMessage("Навык **" + skillName + "** не найден для твоего класса. Посмотри **+скиллы**").submit();
				return;
			}
			if (player.getSkills() == null) player.setSkills(new java.util.HashMap<>());
			int current = player.getSkills().getOrDefault(skillName, 0);
			if (current >= 3) {
				event.getChannel().sendMessage("Навык **" + skillName + "** уже на максимальном уровне (3).").submit();
				return;
			}
			player.getSkills().put(skillName, current + 1);
			player.setSkillPoints(player.getSkillPoints() - 1);
			playerCache.put(id, player);
			event.getChannel().sendMessage("✅ Навык **" + skillName + "** прокачан до уровня **" + (current + 1) + "**!").submit();
		} finally {
			lock.unlock();
		}
	}

	/** +умение [название] — активное умение */
	public void useAbility(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String abilityName = event.getMessage().getContentDisplay().substring(7).trim().toLowerCase();
		Player player = playerCache.get(id);
		String playerClass = player.getPlayerClass() != null ? player.getPlayerClass() : "";
		switch (abilityName) {
			case "телепорт" -> {
				if (!"МАГ".equals(playerClass) || player.getSkills() == null || !player.getSkills().containsKey("телепорт")) {
					event.getChannel().sendMessage("Умение **телепорт** доступно только МАГам со скиллом.").submit();
					return;
				}
				// Check cooldown: 1 per day
				long now = System.currentTimeMillis();
				long todayStart = now - (now % (24 * 60 * 60 * 1000));
				ReentrantLock lock = getPlayerLock(id);
				lock.lock();
				try {
					Player p = playerCache.get(id);
					if (p.getLastTeleportTime() != 0 && p.getLastTeleportTime() > todayStart) {
						event.getChannel().sendMessage("🌀 Телепорт уже использован сегодня!").submit();
						return;
					}
					// Get adjacent locations
					ru.chebe.litvinov.data.Location loc = locationManager.getLocation(p.getLocation());
					if (loc == null || loc.getPaths().isEmpty()) {
						event.getChannel().sendMessage("Нет доступных локаций для телепорта.").submit();
						return;
					}
					// Teleport to first adjacent location
					String dest = loc.getPaths().get(0);
					locationManager.movePlayerInPopulation(p, dest);
					p.setLocation(dest);
					p.setLastTeleportTime(now);
					playerCache.put(id, p);
					event.getChannel().sendMessage("🌀 **Телепорт!** Ты перемещён в **" + dest + "**!").submit();
				} finally { lock.unlock(); }
			}
			case "исцеление" -> {
				if (!"ПАЛАДИН".equals(playerClass)) {
					event.getChannel().sendMessage("Умение **исцеление** доступно только ПАЛАДИНам.").submit();
					return;
				}
				ReentrantLock lock = getPlayerLock(id);
				lock.lock();
				try {
					Player p = playerCache.get(id);
					if (p != null) {
						p.setHp(Math.min(p.getHp() + 50, p.getMaxHp()));
						playerCache.put(id, p);
					}
				} finally { lock.unlock(); }
				event.getChannel().sendMessage("💚 Паладин исцелился на **50 HP**!").submit();
			}
			default -> event.getChannel().sendMessage("Умение **" + abilityName + "** не найдено. Посмотри **+скиллы**").submit();
		}
	}

	/** +второй класс [класс] — второй класс на уровне 50 */
	public void chooseSecondClass(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getLevel() < 50) {
				event.getChannel().sendMessage("Второй класс доступен с 50 уровня.").submit();
				return;
			}
			String arg = event.getMessage().getContentDisplay().substring(14).trim().toLowerCase();
			Set<String> validClasses = Set.of("воин", "разбойник", "маг", "паладин", "некромант", "следопыт");
			if (!validClasses.contains(arg)) {
				event.getChannel().sendMessage("Доступные классы: " + validClasses).submit();
				return;
			}
			String existing = player.getPlayerClass() != null ? player.getPlayerClass() : "";
			player.setPlayerClass(existing + "/" + arg.toUpperCase());
			// Половина бонусов второго класса
			switch (arg) {
				case "воин" -> { player.setStrength(player.getStrength() + 2); player.setArmor(player.getArmor() + 1); }
				case "разбойник" -> player.setLuck(player.getLuck() + 2);
				case "маг" -> player.setMaxHp(player.getMaxHp() + 15);
				case "паладин" -> player.setArmor(player.getArmor() + 2);
				case "некромант" -> player.setStrength(player.getStrength() + 1);
				case "следопыт" -> player.setLuck(player.getLuck() + 1);
			}
			playerCache.put(id, player);
			event.getChannel().sendMessage("✅ Второй класс **" + arg.toUpperCase() + "** добавлен! Текущий класс: **" + player.getPlayerClass() + "**").submit();
		} finally {
			lock.unlock();
		}
	}

	// ---- Items 138-144: Social mechanics ----
	/** +фракции — репутация у фракций */
	public void showFactions(MessageReceivedEvent event) {
		if (factionManager != null) factionManager.showFactionRep(event);
		else event.getChannel().sendMessage("Система фракций недоступна.").submit();
	}

	/** +дневник / +дневник [текст] */
	public void diaryCommand(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String content = event.getMessage().getContentDisplay();
		String arg = content.length() > 9 ? content.substring(9).trim() : "";
		ReentrantLock lock = getPlayerLock(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player.getDiary() == null) player.setDiary(new ArrayList<>());
			if (arg.isEmpty()) {
				// Показать последние 5 записей
				List<String> diary = player.getDiary();
				if (diary.isEmpty()) {
					event.getChannel().sendMessage("📔 Дневник пуст. Добавь запись: **+дневник [текст]**").submit();
				} else {
					var sb = new StringBuilder("📔 **Дневник " + player.getNickName() + "**\n\n");
					int start = Math.max(0, diary.size() - 5);
					for (int i = start; i < diary.size(); i++) {
						sb.append(i + 1).append(". ").append(diary.get(i)).append("\n");
					}
					event.getChannel().sendMessage(sb.toString()).submit();
				}
			} else {
				// Добавить запись
				if (arg.length() > 200) {
					event.getChannel().sendMessage("Запись слишком длинная (максимум 200 символов).").submit();
					return;
				}
				List<String> diary = player.getDiary();
				if (diary.size() >= 20) diary.remove(0); // Удаляем старую запись
				diary.add(arg);
				playerCache.put(id, player);
				event.getChannel().sendMessage("📝 Запись добавлена в дневник!").submit();
			}
		} finally {
			lock.unlock();
		}
	}

	/** +топ активность — топ активных игроков */
	public void topActivity(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		all.sort((a, b) -> {
			int scoreA = a.getPvpWins() + a.getMobKills() + (a.getCompletedQuests() != null ? a.getCompletedQuests().size() : 0);
			int scoreB = b.getPvpWins() + b.getMobKills() + (b.getCompletedQuests() != null ? b.getCompletedQuests().size() : 0);
			return scoreB - scoreA;
		});
		var sb = new StringBuilder("🏆 **Топ активности:**\n\n");
		for (int i = 0; i < Math.min(10, all.size()); i++) {
			Player p = all.get(i);
			int score = p.getPvpWins() + p.getMobKills() + (p.getCompletedQuests() != null ? p.getCompletedQuests().size() : 0);
			sb.append(String.format("%d. **%s** — %d очков активности\n", i + 1, p.getNickName(), score));
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	// Карта скрытых квестов
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
					addNewItem(id, "кружка цикория");
				}
				return true;
			}
		}
		return false;
	}

	/** +лор — лор мира */
	public void lorePage(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		List<String> history = player.getLocationHistory() != null ? player.getLocationHistory() : new ArrayList<>();
		java.util.Set<String> visited = new java.util.HashSet<>(history);

		var sb = new StringBuilder("📚 **Лор Мира БЧ-ГРП**\n\n");
		sb.append("**Фрагмент 1 — Начало:**\n_Когда-то этот сервер был просто чатом. Но потом всё изменилось..._\n\n");
		if (visited.size() >= 5) {
			sb.append("**Фрагмент 2 — Путешественник:**\n_Тот, кто посетил пять локаций, начинает видеть скрытые связи между ними..._\n\n");
		}
		if (visited.contains("олимп")) {
			sb.append("**Фрагмент 3 — Олимп:**\n_На вершине Олимпа обитает Дархалас — существо, говорящее только цитатами из деловой переписки._\n\n");
		}
		if (visited.size() >= 10) {
			sb.append("**Фрагмент 4 — Исследователь:**\n_Посетивший десять мест знает: границы этого мира определяются числом каналов в Discord._\n\n");
		}
		if (visited.contains("загадка")) {
			sb.append("**Фрагмент 5 — Загадка:**\n_В этом месте слова теряют смысл. Говорят, что Циник построил особняк именно здесь, чтобы никто не мог задать правильный вопрос._\n\n");
		}
		if (visited.contains("болото")) {
			sb.append("**Фрагмент 6 — Болото:**\n_Болото появилось не само по себе. Арктулз превратил целую локацию в топь, когда понял, что её не читает никто кроме него._\n\n");
		}
		if (player.getLevel() >= 20) {
			sb.append("**Фрагмент 7 — Ветеран:**\n_На двадцатом уровне приходит понимание: игра — это не прогрессия, это бесконечный разговор между людьми, которым просто нечем заняться._\n\n");
		}
		if (player.getMobKills() >= 50) {
			sb.append("**Фрагмент 8 — Охотник:**\n_После пятидесяти убийств ни один моб не кажется страшным. Страшным кажется тот, кто их всех победил._\n\n");
		}
		if (player.getPrestige() > 0) {
			sb.append("**Фрагмент 9 — Престиж:**\n_Те, кто вернулся с нуля, видят мир иначе. Они знают: максимальный уровень — не конец, а начало настоящего испытания._\n\n");
		}
		if (visited.contains("олимп") && visited.contains("загадка") && visited.contains("модерская")) {
			sb.append("**Фрагмент 10 — Финал:**\n_Прошедший три великих локации достигает понимания. Мир БЧ-ГРП — это зеркало. В нём каждый видит то, что хочет увидеть._\n\n");
		}
		sb.append("_Посещай новые локации, чтобы открывать новые фрагменты лора!_");
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +доска — еженедельная доска почёта */
	public void weeklyBoard(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		Player topPvp = all.stream().max(Comparator.comparingInt(Player::getPvpWins)).orElse(null);
		Player topMobs = all.stream().max(Comparator.comparingInt(Player::getMobKills)).orElse(null);
		Player topMoney = all.stream().max(Comparator.comparingInt(Player::getMoney)).orElse(null);

		net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
			.setTitle("📊 Доска почёта — Эта неделя")
			.setColor(new java.awt.Color(255, 215, 0));
		if (topPvp != null) embed.addField("⚔️ Лучший в PvP", topPvp.getNickName() + " — " + topPvp.getPvpWins() + " побед", false);
		if (topMobs != null) embed.addField("🗡️ Охотник на мобов", topMobs.getNickName() + " — " + topMobs.getMobKills() + " убийств", false);
		if (topMoney != null) embed.addField("💰 Богатейший", topMoney.getNickName() + " — " + topMoney.getMoney() + " монет", false);
		event.getChannel().sendMessageEmbeds(embed.build()).submit();
	}

	// ---- Items 138-144: Bounty ----
	/** +бонт @player [reward] */
	public void placeBounty(MessageReceivedEvent event) {
		if (bountyManager != null) bountyManager.placeBounty(event);
		else event.getChannel().sendMessage("Система наград недоступна.").submit();
	}

	/** +бонты */
	public void getBounties(MessageReceivedEvent event) {
		if (bountyManager != null) bountyManager.getBounties(event);
		else event.getChannel().sendMessage("Система наград недоступна.").submit();
	}

	// ---- Items 145-150: Arena ----
	/** +арена */
	public void arenaChallenge(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.arenaChallenge(event);
		else event.getChannel().sendMessage("Арена недоступна.").submit();
	}

	/** +арена топ */
	public void arenaLeaderboard(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.arenaLeaderboard(event);
		else event.getChannel().sendMessage("Арена недоступна.").submit();
	}

	/** +арена 3v3 */
	public void teamArena(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.teamArenaChallenge(event);
		else event.getChannel().sendMessage("Арена 3v3 недоступна.").submit();
	}

	/** +выживание */
	public void survivalChallenge(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.survivalChallenge(event);
		else event.getChannel().sendMessage("Выживание недоступно.").submit();
	}

	/** +чемпион */
	public void showChampion(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.showChampion(event);
		else event.getChannel().sendMessage("Система чемпиона недоступна.").submit();
	}

	/** +вызвать чемпиона */
	public void challengeChampion(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.challengeChampion(event);
		else event.getChannel().sendMessage("Система чемпиона недоступна.").submit();
	}

	/** +лига */
	public void showLeague(MessageReceivedEvent event) {
		if (arenaManager != null) arenaManager.showLeague(event);
		else event.getChannel().sendMessage("Лига недоступна.").submit();
	}

	/** +турнир */
	public void registerTournament(MessageReceivedEvent event) {
		if (tournamentManager != null) tournamentManager.registerForTournament(event);
		else event.getChannel().sendMessage("Турнир недоступен.").submit();
	}

	/** +турнир статус */
	public void tournamentStatus(MessageReceivedEvent event) {
		if (tournamentManager != null) tournamentManager.tournamentStatus(event);
		else event.getChannel().sendMessage("Турнир недоступен.").submit();
	}

	/** +выдать награду активности — admin */
	public void giveActivityReward(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		all.sort((a, b) -> {
			int scoreA = a.getPvpWins() + a.getMobKills() + (a.getCompletedQuests() != null ? a.getCompletedQuests().size() : 0);
			int scoreB = b.getPvpWins() + b.getMobKills() + (b.getCompletedQuests() != null ? b.getCompletedQuests().size() : 0);
			return scoreB - scoreA;
		});
		var sb = new StringBuilder("🏆 **Награды за активность выданы:**\n");
		for (int i = 0; i < Math.min(3, all.size()); i++) {
			Player p = all.get(i);
			ReentrantLock lock = getPlayerLock(p.getId());
			lock.lock();
			try {
				Player fresh = playerCache.get(p.getId());
				if (fresh != null) {
					fresh.setMoney(fresh.getMoney() + 1000);
					playerCache.put(fresh.getId(), fresh);
					sb.append(i + 1).append(". **").append(fresh.getNickName()).append("** — +1000 монет\n");
				}
			} finally { lock.unlock(); }
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}


	// Возвращает список игроков клана в текущей локации

	/** {@inheritDoc} */
	@Override
	public void playRoulette(MessageReceivedEvent event) { miniGames.playRoulette(event); }

	/** {@inheritDoc} */
	@Override
	public void rockPaperScissors(MessageReceivedEvent event) { miniGames.rockPaperScissors(event); }

	/** {@inheritDoc} */
	@Override
	public void guessTheNumber(MessageReceivedEvent event) { miniGames.guessTheNumber(event); }
}
