package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Boss extends Person {
	String nickName;
	int hp;
	int strength;
	int armor;
	String bossItem;
	int defeat;
	int win;
}
