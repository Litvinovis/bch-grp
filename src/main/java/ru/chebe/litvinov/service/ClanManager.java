package ru.chebe.litvinov.service;

import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.ClanRepository;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.chebe.litvinov.Constants.MAX_CLAN_SIZE;

/**
 * Менеджер кланов.
 * Управляет созданием, вступлением, выходом и управлением заявками в кланах.
 * Данные хранятся в Ignite-кэшах кланов и игроков.
 */
public class ClanManager {

	private final ClanRepository clanCache;
	private final PlayerRepository playerCache;

	/**
	 * Создаёт менеджер кланов.
	 *
	 * @param clanCache   репозиторий Ignite 3 для хранения данных кланов
	 * @param playerCache репозиторий Ignite 3 для хранения данных игроков
	 */
	public ClanManager(ClanRepository clanCache, PlayerRepository playerCache) {
		this.clanCache = clanCache;
		this.playerCache = playerCache;
	}

	/**
	 * Регистрирует новый клан с указанным именем и лидером.
	 *
	 * @param clanName название нового клана
	 * @param leaderId идентификатор игрока-лидера
	 * @return пустую строку при успехе или сообщение об ошибке
	 */
	public String registerClan(String clanName, String leaderId) {
		if (clanCache.contains(clanName)) {
			return "Клан с таким именем уже существует, подай заявку на вступление или придумай другое название";
		}
		var clan = new Clan(clanName, leaderId);
		clanCache.put(clan.getName(), clan);
		return "";
	}


	/**
	 * Удаляет игрока из клана. Если игрок был лидером — назначает нового лидера.
	 * Если клан остался пустым — удаляет его.
	 *
	 * @param clanName название клана
	 * @param id       идентификатор выходящего игрока
	 */
	public void leaveClan(String clanName, String id) {
		var clan = clanCache.get(clanName);
		clan.getMembers().remove(id);
		if (clan.getMembers().isEmpty()) {
			clanCache.remove(clanName);
		} else {
			if (clan.getLeaderId().equals(id)) {
				clan.setLeaderId(clan.getMembers().getFirst());
			}
			clanCache.put(clanName, clan);
		}
	}

	/**
	 * Подаёт заявку на вступление игрока в клан.
	 *
	 * @param clanName название клана
	 * @param id       идентификатор игрока
	 * @return пустую строку при успешной подаче заявки или сообщение об ошибке
	 */
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

	/**
	 * Принимает все заявки на вступление в клан. Доступно только лидеру клана.
	 *
	 * @param clanName название клана
	 * @param id       идентификатор игрока, выполняющего действие
	 * @return пустую строку при успехе или сообщение об ошибке
	 */
	public String acceptApply(String clanName, String id) {
		var clan = clanCache.get(clanName);
		if (clan == null) {
			return "Клан с таким названием не существует";
		}
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

	/**
	 * Отклоняет все заявки на вступление в клан. Доступно только лидеру клана.
	 *
	 * @param clanName название клана
	 * @param id       идентификатор игрока, выполняющего действие
	 * @return пустую строку при успехе или сообщение об ошибке
	 */
	public String rejectApply(String clanName, String id) {
		var clan = clanCache.get(clanName);
		if (clan == null) {
			return "Клан с таким названием не существует";
		}
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

	/**
	 * Возвращает текстовую информацию о клане: лидер, участники и количество заявок.
	 *
	 * @param clanName название клана
	 * @return строка с информацией о клане или сообщение об ошибке
	 */
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
		var leader = playerCache.get(clan.getLeaderId());
		sb.append("Лидер: ").append(leader != null ? leader.getNickName() : clan.getLeaderId()).append("\n");
		sb.append("Участники: ");
		for (var member : clan.getMembers()) {
			var player = playerCache.get(member);
			if (player != null) {
				sb.append(player.getNickName()).append("\n");
			}
		}
		sb.append("Количество заявок на вступление: ").append(clan.getAppliers().size()).append("\n");
		return sb.toString();
	}

	/**
	 * Возвращает список идентификаторов участников клана.
	 *
	 * @param clanName название клана
	 * @return список идентификаторов игроков или пустой список если клан не найден
	 */
	public List<String> getClanMembers(String clanName) {
		var clan = clanCache.get(clanName);
		if (clan == null) {
			return new ArrayList<>();
		} else {
			return clan.getMembers();
		}
	}

	public Clan getClan(String clanName) {
		return clanCache.get(clanName);
	}

	public void saveClan(Clan clan) {
		clanCache.put(clan.getName(), clan);
	}

	/** Добавляет монеты в клановый банк (налоги от территорий и т.п.) */
	public void addToClanBank(String clanName, String itemName, int amount) {
		var clan = clanCache.get(clanName);
		if (clan == null) return;
		if (clan.getClanBank() == null) clan.setClanBank(new java.util.HashMap<>());
		clan.getClanBank().merge("монеты", amount, Integer::sum);
		clanCache.put(clanName, clan);
	}

	public String clanBankDeposit(String clanName, String playerId, int amount, Player player) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		if (player.getMoney() < amount) return "Недостаточно монет";
		player.setMoney(player.getMoney() - amount);
		playerCache.put(playerId, player);
		if (clan.getClanBank() == null) clan.setClanBank(new java.util.HashMap<>());
		clan.getClanBank().merge("монеты", amount, Integer::sum);
		clanCache.put(clanName, clan);
		return "";
	}

	public String clanBankWithdraw(String clanName, String playerId, int amount, Player player) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		if (!clan.getLeaderId().equals(playerId)) return "Только лидер может снимать монеты";
		if (clan.getClanBank() == null) return "Клановый банк пуст";
		int balance = clan.getClanBank().getOrDefault("монеты", 0);
		if (balance < amount) return "В клановом банке недостаточно монет (есть: " + balance + ")";
		clan.getClanBank().put("монеты", balance - amount);
		clanCache.put(clanName, clan);
		player.setMoney(player.getMoney() + amount);
		playerCache.put(playerId, player);
		return "";
	}

	public String getClanBankInfo(String clanName) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		int balance = clan.getClanBank() != null ? clan.getClanBank().getOrDefault("монеты", 0) : 0;
		return "🏦 Клановый банк **" + clanName + "**: **" + balance + "** монет";
	}

	public String purchaseClanUpgrade(String clanName, String playerId, String upgrade) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		if (!clan.getLeaderId().equals(playerId)) return "Только лидер может покупать улучшения";
		if (clan.getClanUpgrades() == null) clan.setClanUpgrades(new ArrayList<>());
		if (clan.getClanUpgrades().contains(upgrade)) return "Улучшение **" + upgrade + "** уже куплено";
		int cost = switch (upgrade) {
			case "дроп" -> 500;
			case "опыт" -> 750;
			case "броня" -> 1000;
			default -> -1;
		};
		if (cost < 0) return "Неизвестное улучшение. Доступны: дроп, опыт, броня";
		int balance = clan.getClanBank() != null ? clan.getClanBank().getOrDefault("монеты", 0) : 0;
		if (balance < cost) return "Недостаточно монет в клановом банке (нужно: " + cost + ", есть: " + balance + ")";
		clan.getClanBank().put("монеты", balance - cost);
		clan.getClanUpgrades().add(upgrade);
		clanCache.put(clanName, clan);
		return "";
	}

	public String getClanUpgradesInfo(String clanName) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		var sb = new StringBuilder("🏰 Улучшения клана **" + clanName + "**:\n");
		sb.append("• дроп (+5% шанс дропа) — 500 монет");
		if (clan.getClanUpgrades() != null && clan.getClanUpgrades().contains("дроп")) sb.append(" ✅");
		sb.append("\n• опыт (+10% XP) — 750 монет");
		if (clan.getClanUpgrades() != null && clan.getClanUpgrades().contains("опыт")) sb.append(" ✅");
		sb.append("\n• броня (+2 броня в рейдах) — 1000 монет");
		if (clan.getClanUpgrades() != null && clan.getClanUpgrades().contains("броня")) sb.append(" ✅");
		return sb.toString();
	}

	public String setClanBase(String clanName, String playerId, String location) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		if (!clan.getLeaderId().equals(playerId)) return "Только лидер может устанавливать базу";
		clan.setClanBase(location);
		clanCache.put(clanName, clan);
		return "";
	}

	public String getClanBase(String clanName) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "респаун";
		String base = clan.getClanBase();
		return (base != null && !base.isBlank()) ? base : "респаун";
	}

	public String promoteMember(String clanName, String leaderId, String targetId) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		if (!clan.getLeaderId().equals(leaderId)) return "Только лидер может повышать участников";
		if (!clan.getMembers().contains(targetId)) return "Игрок не является членом клана";
		if (clan.getClanRoles() == null) clan.setClanRoles(new java.util.HashMap<>());
		String currentRole = clan.getClanRoles().getOrDefault(targetId, "рядовой");
		String newRole = switch (currentRole) {
			case "рядовой" -> "ветеран";
			case "ветеран" -> "офицер";
			default -> "офицер";
		};
		clan.getClanRoles().put(targetId, newRole);
		clanCache.put(clanName, clan);
		Player target = playerCache.get(targetId);
		return "Игрок **" + (target != null ? target.getNickName() : targetId) + "** повышен до **" + newRole + "**";
	}

	public String kickMember(String clanName, String leaderId, String targetId) {
		var clan = clanCache.get(clanName);
		if (clan == null) return "Клан не найден";
		if (!clan.getLeaderId().equals(leaderId)) return "Только лидер может исключать участников";
		if (leaderId.equals(targetId)) return "Лидер не может исключить себя. Используй +покинуть клан";
		if (!clan.getMembers().contains(targetId)) return "Игрок не является членом клана";
		clan.getMembers().remove(targetId);
		if (clan.getClanRoles() != null) clan.getClanRoles().remove(targetId);
		clanCache.put(clanName, clan);
		Player target = playerCache.get(targetId);
		if (target != null) {
			target.setClanName("");
			playerCache.put(targetId, target);
		}
		return "";
	}

	public List<Clan> getAllClans() {
		return clanCache.getAll();
	}
}
