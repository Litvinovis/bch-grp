package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
		return """
				Вот твои характристики, игрок
				Имя - %s
				Уровень - %d
				Опыт - %d/%d
				Здоровье - %d/%d
				Удача - %d
				Броня - %d
				Деньги - %d
				Репутация - %d
				Сила - %d
				Локация - %s
				%s""".formatted(nickName, level, exp, expToNextLvl, hp, maxHp, luck, armor, money, reputation, strength, location, quest);
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


