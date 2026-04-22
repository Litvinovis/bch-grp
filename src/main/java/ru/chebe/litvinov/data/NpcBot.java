package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NpcBot extends Person {
	private String nickName;
	private int hp;
	private int maxHp;
	private int strength;
	private int armor;
	private int moneyReward;
	private int xpReward;
	private String locationName;

	public void respawn() {
		this.hp = this.maxHp;
	}
}
