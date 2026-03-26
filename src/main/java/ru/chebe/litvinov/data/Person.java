package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Базовый абстрактный класс для всех персонажей игры (игроков и боссов).
 * Содержит общие боевые характеристики: ник, здоровье, силу и броню.
 */
@Setter
@Getter
public abstract class Person {
	protected String nickName;
	protected int hp;
	protected int strength;
	protected int armor;
}
