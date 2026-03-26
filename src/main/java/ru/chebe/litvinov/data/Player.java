package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

import java.util.HashMap;
import java.util.Map;

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
	@QuerySqlField(index = true)
	private int money;
	@QuerySqlField(index = true)
	private int reputation;
	@QuerySqlField(index = true)
	private String location;
	@QuerySqlField(index = true)
	private int level;
	@QuerySqlField(index = true)
	private int exp;
	private int expToNextLvl;
	private Map<String, Integer> inventory;
	private String answer;
	private Event activeEvent;
	private long dailyTime;
	private String clanName;

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
		String quest = activeEvent == null ? "" : "Квест - " + activeEvent.getDescription() + "\n";
		return "Вот твои характристики, игрок\n" +
						"Имя - " + nickName + "\n" +
						"Уровень - " + level + "\n" +
						"Опыт - " + exp + "/" + expToNextLvl + "\n" +
						"Здоровье - " + hp + "/" + maxHp + "\n" +
						"Удача - " + luck + "\n" +
						"Броня - " + armor + "\n" +
						"Деньги - " + money + "\n" +
						"Репутация - " + reputation + "\n" +
						"Сила - " + strength + "\n" +
						"Локация - " + location + "\n" +
						quest;
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
		return inventory.toString();
	}
}


