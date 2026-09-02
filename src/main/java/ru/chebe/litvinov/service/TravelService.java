package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.GameBalance;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Перемещение по миру: переходы между локациями, маршрут до локации,
 * исследование текущей локации, возврат на респаун и история посещений.
 */
public class TravelService {

	private static final Logger log = LoggerFactory.getLogger(TravelService.class);

	private final Random random = new Random();

	private final PlayerRepository playerCache;
	private final LocationManager locationManager;
	private final ItemsManager itemsManager;
	private final ClanManager clanManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;
	private final PlayerStatsService stats;
	private final InventoryService inventory;
	private final EventsManager eventsManager;
	private final BattleManager battleManager;

	public TravelService(PlayerRepository playerCache, LocationManager locationManager, ItemsManager itemsManager,
	                     ClanManager clanManager, PlayerLocks playerLocks,
	                     AchievementService achievements, PlayerStatsService stats,
	                     InventoryService inventory, EventsManager eventsManager, BattleManager battleManager) {
		this.playerCache = playerCache;
		this.locationManager = locationManager;
		this.itemsManager = itemsManager;
		this.clanManager = clanManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
		this.stats = stats;
		this.inventory = inventory;
		this.eventsManager = eventsManager;
		this.battleManager = battleManager;
	}

	/**
	 * Обрабатывает команду перемещения игрока в другую локацию.
	 * Поддерживает обычные переходы и телепортацию с токеном телепорта.
	 *
	 * @param event событие Discord-сообщения с названием целевой локации
	 */
	public void move(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(5).trim().toLowerCase();
		inventory.removeExpiredBuffs(event.getAuthor().getId());
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
		ReentrantLock lock = playerLocks.get(id);
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
		ReentrantLock lock = playerLocks.get(id);
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
}
