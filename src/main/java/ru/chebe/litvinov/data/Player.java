package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

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

	public String inventoryInfo() {
		for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
			if (entry.getValue() == null) {
				inventory.remove(entry.getKey());
			}
		}
		return inventory.toString();
	}
}


