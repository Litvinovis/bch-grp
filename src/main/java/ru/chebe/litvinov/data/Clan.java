package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.chebe.litvinov.Constants.MAX_CLAN_SIZE;

/**
 * Клан — объединение игроков под одним названием.
 * Содержит лидера, список участников и список поданных заявок на вступление.
 */
@Getter
@Setter
public class Clan {
	private String name;
	private String leaderId;
	private List<String> members;
	private List<String> appliers;
	private Map<String, Integer> clanBank;
	private List<String> clanUpgrades;
	private String clanBase;
	private Map<String, String> clanRoles;

	/**
	 * Создаёт новый клан с указанным именем и лидером.
	 * Лидер автоматически добавляется в список участников.
	 *
	 * @param name   название клана
	 * @param leader идентификатор игрока-лидера
	 */
	public Clan(String name, String leader) {
		this.name = name;
		this.leaderId = leader;
		this.members = new ArrayList<>(MAX_CLAN_SIZE);
		this.members.add(leaderId);
		this.appliers = new ArrayList<>(MAX_CLAN_SIZE);
		this.clanBank = new HashMap<>();
		this.clanUpgrades = new ArrayList<>();
		this.clanBase = "респаун";
		this.clanRoles = new HashMap<>();
		this.clanRoles.put(leader, "лидер");
	}
}
