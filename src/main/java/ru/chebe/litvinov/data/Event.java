package ru.chebe.litvinov.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Игровое событие (квест), которое может быть назначено игроку.
 * Поддерживает два типа: «Ходилка» (добраться до локации) и «Загадка» (дать правильный ответ).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
	String description;
	String locationEnd;
	String type;
	String timeEnd;
	String correctAnswer;
	int moneyReward;
	int xpReward;
	String itemReward;
	@lombok.Builder.Default
	int attempt = 0;

	@Override
	public String toString() {
		String time = timeEnd == null ? "" : "Время выполенния - " + timeLost() + "\n";
		String item = itemReward == null ? "" : "Награждаемый предмет - " + itemReward + "\n";
		String location = locationEnd == null ? "" : "Локация выполнения - " + locationEnd + "\n";
		String start = type.equalsIgnoreCase("загадка") ? "Вопрос - " : "Описание - ";
		return start + description + "\n" +
						location +
						"Тип - " + type + "\n" +
						time +
						"Награда в монетах - " + moneyReward + "\n" +
						"Очков опыта - " + xpReward + "\n" +
						item;
	}

	private String timeLost() {
		long remaining = Instant.parse(timeEnd).toEpochMilli() - System.currentTimeMillis();
		return String.format("%d мин. %d сек.",
						TimeUnit.MILLISECONDS.toMinutes(remaining),
						TimeUnit.MILLISECONDS.toSeconds(remaining) % 60);
	}
}


