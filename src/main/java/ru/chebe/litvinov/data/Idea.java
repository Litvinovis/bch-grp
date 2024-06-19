package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Idea {
	private int id;
	private String description;
	private String author;
	private String resolution;

	@Override
	public String toString() {
		return "Идея № " + id + "\n" +
						"Автор - " + author + "\n" +
						"Суть - " + description + "\n" +
						"Резолюция разработчика - " + resolution;
	}
}
