package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Босс игрового мира — особый персонаж, привязанный к конкретной локации.
 * Хранит статистику побед и поражений, а также название уникального предмета, который выпадает при победе над боссом.
 */
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
