package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Классы и навыки: выбор основного и второго класса, дерево навыков,
 * вложение очков и применение способностей.
 */
public class SkillsService {

	private static final Logger log = LoggerFactory.getLogger(SkillsService.class);

	private final PlayerRepository playerCache;
	private final LocationManager locationManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;

	public SkillsService(PlayerRepository playerCache, LocationManager locationManager,
	                     PlayerLocks playerLocks, AchievementService achievements) {
		this.playerCache = playerCache;
		this.locationManager = locationManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
	}

	/**
	 * Выбор класса персонажа (с 5 уровня, один раз).
	 * Синтаксис: +класс [воин|разбойник|маг]
	 */
	public void chooseClass(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = playerLocks.get(id);
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

	/** +второй класс [класс] — второй класс на уровне 50 */
	public void chooseSecondClass(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		ReentrantLock lock = playerLocks.get(id);
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
		ReentrantLock lock = playerLocks.get(id);
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
				ReentrantLock lock = playerLocks.get(id);
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
				ReentrantLock lock = playerLocks.get(id);
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
}
