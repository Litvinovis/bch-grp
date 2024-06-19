package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Boss {

	String name;
	int hp;
	int strength;
	String bossItem;
	int defeat;
	int win;
}
