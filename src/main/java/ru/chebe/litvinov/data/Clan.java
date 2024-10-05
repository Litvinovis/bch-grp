package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static ru.chebe.litvinov.Constants.MAX_CLAN_SIZE;

@Getter
@Setter
public class Clan {
	private String name;
	private String leaderId;
	private List<String> members;
	private List<String> appliers;

	public Clan(String name, String leader) {
		this.name = name;
		this.leaderId = leader;
		this.members = new ArrayList<>(MAX_CLAN_SIZE);
		this.members.add(leaderId);
		this.appliers = new ArrayList<>(MAX_CLAN_SIZE);
	}
}
