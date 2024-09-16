package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class Person {
	protected String nickName;
	protected int hp;
	protected int strength;
	protected int armor;
}
