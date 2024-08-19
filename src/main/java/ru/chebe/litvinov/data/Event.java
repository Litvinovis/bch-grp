package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

@Getter
@Setter
@Builder
public class Event {
	String description;
	String locationEnd;
	String type;
	String timeEnd;
	String correctAnswer;
	int moneyReward;
	int xpReward;
	String itemReward;
	int attempt = 0;

	@Override
	public String toString() {
		String time = timeEnd == null ? "" : "Время выполенния - " + timeLost() + "\n";
		String item = itemReward == null ? "" : "Награждаемый предмет - " + itemReward + "\n";
		return "Описание - " + description + "\n" +
						"Локация выполнения - " + locationEnd + "\n" +
						"Тип - " + type + "\n" +
						time +
						"Награда в монетах - " + moneyReward + "\n" +
						"Очков опыта - " + xpReward + "\n" +
						item;
	}

	private String timeLost() {
		return "timeEnd";
	}
}


