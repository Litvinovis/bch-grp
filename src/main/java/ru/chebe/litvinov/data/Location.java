package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Игровая локация на карте мира.
 * Содержит информацию об опасности, PvP-режиме, боссе, доступных путях и текущем населении.
 */
@Getter
@Setter
@Builder
public class Location {
	private String name;
	private int dangerous;
	private List<String> populationByName;
	private List<String> populationById;
	private List<String> paths;
	private boolean pvp;
	private String boss;
	private String bossItem;
	private boolean teleport;

	@Override
	public String toString() {
		String isPvp = pvp ? "Да" : "Нет";
		String isBoss = boss != null ?
						"Босс - " + boss + "\n" + "Выпадающий из босса предмет - " + bossItem
						: "";
		return "Информаци о локации:\n" +
						"Название - " + name + "\n" +
						"Опасность - " + dangerous + "%\n" +
						"Игроки - " + populationByName + "\n" +
						"Граничащие локации - " + paths + "\n" +
						"ПВП - " + isPvp + "\n" +
						isBoss;

	}
}
