package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Player {
	private String id;
	private String nickName;
	private int hp;
	private int maxHp;
	private int luck;
	private int money;
	private int reputation;
	private int strength;
	private String location;
	private int level;
	private int exp;
	private int armor;
	private int expToNextLvl;
	private List<String> inventory;
	private String answer;
	private Event activeEvent;

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
		this.inventory = new ArrayList<>();
		this.answer = "";
		this.activeEvent = null;
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
}
