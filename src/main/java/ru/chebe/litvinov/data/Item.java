package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Builder
public class Item {
	private String name;
	private String description;
	private int price;
	private int luck;
	private int strength;
	private int health;
	private int armor;
	private int reputation;
	private int xpGeneration;
	private int quantity;
	private long expireTime;
	private boolean action;

	@Override
	public String toString() {
		String act = action ? "активируемое" : "Постоянное";
		String time = "";
		if (expireTime != 0) {
			time = "\nИсчезнет через " +
							TimeUnit.MILLISECONDS.toMinutes(expireTime - System.currentTimeMillis()) + " минут";
		}
		return "Характеристики предмета:\n" +
						"Название - " + name + "\n" +
						"Цена - " + price + "\n" +
						"Цена продажи - " + (price / 2) + "\n" +
						"Увеличение брони - " + armor + "\n" +
						"Увеличение получаемого опыта - " + xpGeneration + "\n" +
						"Увеличение здоровья - " + health + "\n" +
						"Увеличение удачи - " + luck + "\n" +
						"Увеличение репутации - " + reputation + "\n" +
						"Увеличение силы - " + strength + "\n" +
						"Количество - " + quantity + "\n" +
						"Действие - " + act + "\n" +
						"Описание - " + description +
						time;
	}
}
