package org.example.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Player {
	public String nickName;
	public int hp;
	public int luck;
	public int money;
	public int reputation;
	public int strength;
	public String location;

	public Player(String nickName) {
		this.nickName = nickName;
		this.hp = 100;
		this.luck = 5;
		this.money = 50;
		this.reputation = 0;
		this.strength = 5;
		this.location = null;
	}

	@Override
	public String toString() {
		return "Вот твои характристики, игрок" +
						"Имя - " + nickName + "\n" +
						"Здоровье - " + hp + "\n" +
						"Удача - " + luck + "\n" +
						"Деньги - " + money + "\n" +
						"Репутация - " + reputation + "\n" +
						"Сила - " + strength + "\n" +
						"Локация - " + location;
	}
}
