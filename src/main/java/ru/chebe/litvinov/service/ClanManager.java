package ru.chebe.litvinov.service;

import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Player;

import java.util.ArrayList;
import java.util.List;

import static ru.chebe.litvinov.Constants.MAX_CLAN_SIZE;

public class ClanManager {

	private final IgniteCache<String, Clan> clanCache;
	private final IgniteCache<String, Player> playerCache;

	public ClanManager(IgniteCache<String, Clan> clanCache, IgniteCache<String, Player> playerCache) {
		this.clanCache = clanCache;
		this.playerCache = playerCache;
	}

	public String registerClan(String clanName, String leaderId) {
		if (clanCache.containsKey(clanName)) {
			return "Клан с таким именем уже существует, подай заявку на вступление или придумай другое название";
		}
		var clan = new Clan(clanName, leaderId);
		clanCache.put(clan.getName(), clan);
		return "";
	}


	public void leaveClan(String clanName, String id) {
		var clan = clanCache.get(clanName);
		clan.getMembers().remove(id);
		if (clan.getMembers().isEmpty()) {
			clanCache.remove(clanName);
		} else if (clan.getLeaderId().equals(id)) {
			clan.setLeaderId(clan.getMembers().get(0));
		}
	}

	public String joinClan(String clanName, String id) {
		var clan = clanCache.get(clanName);
		if (clan == null) {
			return "Клан с таким названием не существует";
		} else if (clan.getMembers().size() + clan.getAppliers().size() == MAX_CLAN_SIZE) {
			return "Количество мест в клане с учетом действующих заявок исчерпано, допустимое количество игроков в клане: " + MAX_CLAN_SIZE;
		} else {
			clan.getAppliers().add(id);
		}
		return "";
	}

	public String acceptApply(String clanName, String id) {
		var clan = clanCache.get(clanName);
		if (clan.getLeaderId().equals(id)) {
			if (clan.getAppliers().isEmpty()) {
				return "Нет активных заявок";
			}
			if (clan.getMembers().size() + clan.getAppliers().size() > MAX_CLAN_SIZE) {
				clan.getAppliers().clear();
				return "Произошла ошибка, количетство заявок превышает максимальное, все заявки отменены, подайте заново";
			} else {
				for (var member : clan.getAppliers()) {
					var player = playerCache.get(member);
					if (player.getClanName() != null && !player.getClanName().isEmpty()) {
						clan.getAppliers().remove(member);
						return player.getClanName() + " не может вступить в клан, т.к. он уже в другом клане, его заявка отменена, для обработки оставшихся заявок, запустите команду заново";
					} else {
						player.setClanName(clan.getName());
						playerCache.put(member, player);
						clan.getAppliers().remove(member);
						clan.getMembers().add(member);
						clanCache.put(clan.getName(), clan);
					}
				}
			}
		} else {
			return "Вы не являетесь лидером клана";
		}
		return "";
	}

	public String rejectApply(String clanName, String id) {
		var clan = clanCache.get(clanName);
		if (clan.getLeaderId().equals(id)) {
			if (clan.getAppliers().isEmpty()) {
				return "Нет активных заявок";
			} else {
				clan.getAppliers().clear();
			}
		} else {
			return "Вы не являетесь лидером клана";
		}
		return "";
	}

	public String getClanInfo(String clanName) {
		if (clanName.isEmpty()) {
			return "Вы не ввели название клана";
		}
		var clan = clanCache.get(clanName);
		if (clan == null) {
			return "Клан с таким названием не существует";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Клан: ").append(clanName).append("\n");
		sb.append("Лидер: ").append(playerCache.get(clan.getLeaderId()).getNickName()).append("\n");
		sb.append("Участники: ");
		for (var member : clan.getMembers()) {
			var player = playerCache.get(member);
			sb.append(player.getNickName()).append("\n");
		}
		sb.append("Количество заявок на вступление: ").append(clan.getAppliers().size()).append("\n");
		return sb.toString();
	}

	public List<String> getClanMembers(String clanName) {
		var clan = clanCache.get(clanName);
		if (clan == null) {
			return new ArrayList<>();
		} else {
			return clan.getMembers();
		}
	}
}
