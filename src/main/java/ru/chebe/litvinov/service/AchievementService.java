package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Достижения игроков: выдача, названия, проверки накопительных условий
 * и анонс редких достижений в общие каналы.
 */
public class AchievementService {

	private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

	/** Достижения, о которых объявляется всему серверу. */
	private static final Set<String> RARE_ACHIEVEMENTS =
			Set.of("легенда", "100_pvp", "коллекционер", "исследователь", "победитель_рейда");

	private static final List<String> BOSS_ITEMS = List.of(
		"бицушка ровера", "кисточка циника", "корона дарха", "кринж стина", "попка ушаса",
		"око мора", "очко бога", "хуй вущъта", "удача рианель", "шарики лаба", "вонь арктулза",
		"скейт ябыса", "форточка орсона", "месть гордона", "хатка база", "игла бувки",
		"калькулятор сталкера", "язык вороны", "диплом ильи", "кресло чегоба",
		"сиськи ред", "банка эдика", "кринж стина", "корона дарха"
	);

	private final PlayerRepository players;
	private JDA jda;
	private Set<String> allowedChannelIds;

	public AchievementService(PlayerRepository players) {
		this.players = players;
	}

	public void setJda(JDA jda) {
		this.jda = jda;
	}

	public void setAllowedChannelIds(Set<String> allowedChannelIds) {
		this.allowedChannelIds = allowedChannelIds;
	}

	/** Выдаёт достижение, если его ещё нет. Игрока не сохраняет — это делает вызывающий. */
	public void unlock(Player player, String achievementId) {
		List<String> achievements = player.getAchievements();
		if (achievements == null) {
			achievements = new ArrayList<>();
			player.setAchievements(achievements);
		}
		if (!achievements.contains(achievementId)) {
			achievements.add(achievementId);
		}
	}

	/** Выдаёт достижение и объявляет о нём в каналы, если оно редкое и получено впервые. */
	public void unlockWithBroadcast(Player player, String achievementId) {
		boolean wasNew = !player.getAchievements().contains(achievementId);
		unlock(player, achievementId);
		if (wasNew && RARE_ACHIEVEMENTS.contains(achievementId) && jda != null && allowedChannelIds != null) {
			broadcast("🌟 **" + player.getNickName() + "** получил редкое достижение: **"
					+ name(achievementId) + "**!");
		}
	}

	private void broadcast(String message) {
		for (String channelId : allowedChannelIds) {
			try {
				TextChannel channel = jda.getTextChannelById(channelId);
				if (channel == null) {
					log.debug("Канал {} не найден — анонс не отправлен", channelId);
					continue;
				}
				channel.sendMessage(message).queue();
			} catch (Exception e) {
				log.debug("Не удалось отправить анонс в канал {}: {}", channelId, e.getMessage());
			}
		}
	}

	/** Человекочитаемое название достижения. */
	public String name(String id) {
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

	/** «Богач» — 10 000 монет на руках. */
	public void checkRich(Player player) {
		if (player.getMoney() >= 10000) {
			unlock(player, "богач");
			players.put(player.getId(), player);
		}
	}

	/** «Исследователь» — посещены все локации. */
	public void checkExplorer(Player player) {
		List<String> history = player.getLocationHistory();
		if (history != null && new HashSet<>(history).containsAll(LocationManager.locationList)) {
			unlock(player, "исследователь");
			players.put(player.getId(), player);
		}
	}

	/** «Коллекционер» — собраны все предметы боссов. */
	public void checkCollector(Player player) {
		Map<String, Integer> inventory = player.getInventory();
		if (inventory != null && BOSS_ITEMS.stream().distinct().allMatch(inventory::containsKey)) {
			unlock(player, "коллекционер");
			players.put(player.getId(), player);
		}
	}

	/** Список достижений игрока в читаемом виде; null, если достижений нет. */
	public String describeAll(Player player) {
		List<String> achievements = player.getAchievements();
		if (achievements == null || achievements.isEmpty()) return null;
		StringBuilder sb = new StringBuilder("Ваши достижения:\n");
		for (String id : achievements) {
			sb.append("- ").append(name(id)).append("\n");
		}
		return sb.toString();
	}
}
