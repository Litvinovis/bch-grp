package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Игровой персонаж, привязанный к Discord-пользователю.
 * Хранит все характеристики игрока: уровень, опыт, деньги, инвентарь, текущую локацию и активный квест.
 */
@Getter
@Setter
public class Player extends Person {
	private String id;
	private int maxHp;
	private int luck;
private int money;
private int reputation;
private String location;
private int level;
private int exp;
	private int expToNextLvl;
	private Map<String, Integer> inventory;
	private String answer;
	private Event activeEvent;
	private long dailyTime;
	private String clanName;
	private int dailyStreak;
	private String playerClass;
	private List<String> achievements;
	private Map<String, Long> activeBuffs;

	private static final Map<String, String> ITEM_ICONS = Map.ofEntries(
		// consumables
		Map.entry("кружка цикория",      "☕"),
		Map.entry("вино лаба",           "🍷"),
		Map.entry("медовуха база",        "🍺"),
		Map.entry("токен телепорта",     "🌀"),
		Map.entry("протеин ябыса",       "💪"),
		Map.entry("амулет рианель",      "🍀"),
		Map.entry("щит чегоба",          "🛡️"),
		Map.entry("речь ильи",           "📢"),
		Map.entry("зелье лаба",          "🧪"),
		// boss drops
		Map.entry("бицушка ровера",      "🏋️"),
		Map.entry("кисточка циника",     "🖌️"),
		Map.entry("корона дарха",        "👑"),
		Map.entry("кринж стина",         "😬"),
		Map.entry("попка ушаса",         "🍑"),
		Map.entry("око мора",            "👁️"),
		Map.entry("очко бога",           "🎱"),
		Map.entry("хуй вущъта",          "🍆"),
		Map.entry("удача рианель",       "🌟"),
		Map.entry("шарики лаба",         "🔮"),
		Map.entry("вонь арктулза",       "💩"),
		Map.entry("скейт ябыса",         "🛹"),
		Map.entry("форточка орсона",     "🪟"),
		Map.entry("месть гордона",       "🔪"),
		Map.entry("хатка база",          "🏠"),
		Map.entry("игла бувки",          "🪡"),
		Map.entry("калькулятор сталкера","🧮"),
		Map.entry("язык вороны",         "🐦"),
		Map.entry("диплом ильи",         "📜"),
		Map.entry("кресло чегоба",       "🪑"),
		Map.entry("сиськи ред",          "🍒"),
		Map.entry("банка эдика",         "🫙")
	);

	public static String itemIcon(String name) {
		return ITEM_ICONS.getOrDefault(name.toLowerCase(), "📦");
	}

	/**
	 * Создаёт нового игрока с начальными характеристиками и стартовым инвентарём.
	 *
	 * @param nickName никнейм игрока из Discord
	 * @param id       Discord-идентификатор пользователя
	 */
	public Player(String nickName, String id) {
		this.id = id;
		this.nickName = nickName;
		this.hp = 100;
		this.maxHp = 100;
		this.luck = 5;
		this.money = 50;
		this.reputation = 0;
		this.armor = 0;
		this.strength = 5;
		this.location = "респаун";
		this.level = 1;
		this.exp = 0;
		this.expToNextLvl = 100;
		this.inventory = startInventory();
		this.answer = "";
		this.activeEvent = null;
		this.dailyTime = 0;
		this.clanName = "";
		this.dailyStreak = 0;
		this.playerClass = "";
		this.achievements = new ArrayList<>();
		this.activeBuffs = new HashMap<>();
	}

	private Map<String, Integer> startInventory() {
		Map<String, Integer> inventory = new HashMap<>();
		inventory.put("токен телепорта", 5);
		inventory.put("кружка цикория", 3);
		inventory.put("вино лаба", 2);
		inventory.put("медовуха база", 1);
		return inventory;
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		sb.append("🎮 **").append(nickName).append("**");
		if (playerClass != null && !playerClass.isBlank()) {
			sb.append(" [").append(playerClass).append("]");
		}
		sb.append(" — Уровень ").append(level).append("\n");

		sb.append("❤️ HP: **").append(hp).append("/").append(maxHp).append("**")
		  .append("  ⚔️ Сила: **").append(strength).append("**")
		  .append("  🛡️ Броня: **").append(armor).append("**\n");

		sb.append("🍀 Удача: **").append(luck).append("**")
		  .append("  ⭐ Репутация: **").append(reputation).append("**")
		  .append("  💰 Деньги: **").append(money).append("**\n");

		sb.append("✨ Опыт: **").append(exp).append("/").append(expToNextLvl).append("**")
		  .append("  📍 Локация: **").append(location).append("**\n");

		if (activeEvent != null) {
			sb.append("🗺️ Квест: ").append(activeEvent.getDescription()).append("\n");
		}

		if (activeBuffs != null && !activeBuffs.isEmpty()) {
			long now = System.currentTimeMillis();
			var buffs = new StringJoiner(", ");
			for (Map.Entry<String, Long> e : activeBuffs.entrySet()) {
				long minsLeft = TimeUnit.MILLISECONDS.toMinutes(e.getValue() - now);
				if (minsLeft > 0) buffs.add(e.getKey() + " (" + minsLeft + " мин)");
			}
			String buffStr = buffs.toString();
			if (!buffStr.isEmpty()) {
				sb.append("⚡ Баффы: ").append(buffStr).append("\n");
			}
		}

		return sb.toString().stripTrailing();
	}

	/**
	 * Возвращает строковое представление инвентаря игрока.
	 * Исправляет некорректные значения количества предметов (null заменяется на 3).
	 *
	 * @return строка с содержимым инвентаря
	 */
	public String inventoryInfo() {
		for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
			if (entry.getValue() == null) {
				inventory.put(entry.getKey(), 3);
			}
		}
		var sb = new StringBuilder();
		sb.append("🎒 **Инвентарь ").append(nickName).append("**\n");
		inventory.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(e -> sb.append(itemIcon(e.getKey()))
				.append(" ").append(e.getKey())
				.append(" — ×").append(e.getValue()).append("\n"));
		return sb.toString().stripTrailing();
	}
}


