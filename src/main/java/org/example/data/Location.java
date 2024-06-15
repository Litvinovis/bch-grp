package org.example.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class Location {
	private String name;
	private int dangerous;
	private List<String> population;
	private List<String> paths;
	private boolean pvp;
	private String boss;
	private String bossItem;

	@Override
	public String toString() {
		String isPvp = pvp ? "Да" : "Нет";
		return "Информаци о локации:\n" +
						"Название - " + name + "\n" +
						"Опасность - " + dangerous + "%\n" +
						"Игроки - " + population + "\n" +
						"Граничащие локации - " + paths + "\n" +
						"ПВП - " + isPvp + "\n" +
						"Босс - " + boss+ "\n" +
						"Выпадающий из босса предмет - " + bossItem;

	}
}
