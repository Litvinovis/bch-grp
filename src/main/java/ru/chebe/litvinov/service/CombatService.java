package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.GameBalance;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Бои: NPC, боссы, PvP и клановые сражения, а также лог последнего боя.
 * Кулдаун на боссов хранится здесь же — он относится к бою, а не к игроку.
 */
public class CombatService {

	private static final Logger log = LoggerFactory.getLogger(CombatService.class);

	private static final long BOSS_COOLDOWN_HOURS = 4;

	private final ConcurrentHashMap<String, Instant> lastBossKillTime = new ConcurrentHashMap<>();
	private final Random random = new Random();

	private final PlayerRepository playerCache;
	private final BattleManager battleManager;
	private final ClanManager clanManager;
	private final NpcManager npcManager;
	private final LocationManager locationManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;
	private final PlayerStatsService stats;
	private final QuestProgressTracker quests;
	private final InventoryService inventory;

	/** Задаются после создания менеджеров. */
	private BountyManager bountyManager;
	private FactionManager factionManager;

	public CombatService(PlayerRepository playerCache, BattleManager battleManager, NpcManager npcManager,
	                     ClanManager clanManager, LocationManager locationManager, PlayerLocks playerLocks,
	                     AchievementService achievements, PlayerStatsService stats, QuestProgressTracker quests,
	                     InventoryService inventory) {
		this.playerCache = playerCache;
		this.battleManager = battleManager;
		this.npcManager = npcManager;
		this.clanManager = clanManager;
		this.locationManager = locationManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
		this.stats = stats;
		this.quests = quests;
		this.inventory = inventory;
	}

	public void setBountyManager(BountyManager bountyManager) {
		this.bountyManager = bountyManager;
	}

	public void setFactionManager(FactionManager factionManager) {
		this.factionManager = factionManager;
	}

	/**
	 * Атакует случайного NPC в текущей локации игрока.
	 */
	public void fightNpc(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		var player = playerCache.get(playerId);
		if (player == null) {
			event.getChannel().sendMessage("Сначала зарегистрируйся командой +начать").submit();
			return;
		}
		var bot = npcManager.getRandomBot(player.getLocation());
		if (bot == null) {
			event.getChannel().sendMessage("В локации **" + player.getLocation() + "** нет NPC для битвы.").submit();
			return;
		}
		event.getChannel().sendMessage("⚔️ Ты атакуешь **" + bot.getNickName() + "** [❤️ HP: **" + bot.getHp() + "**]! Бой начинается...").submit();
		battleManager.playerBattle(List.of(player), List.of((ru.chebe.litvinov.data.Person) bot), event.getChannel());
		// battleMechanic modifies the local player object but doesn't write it back to cache.
		// changeMoney/changeXp re-fetch from cache and would overwrite with pre-battle HP.
		stats.changeHp(playerId, Math.max(0, player.getHp()));
		if (bot.getHp() <= 0) {
			event.getChannel().sendMessage("🏆 **Победа над " + bot.getNickName() + "!**\n💰 +" + bot.getMoneyReward() + " монет  ✨ +" + bot.getXpReward() + " опыта").submit();
			stats.changeMoney(playerId, bot.getMoneyReward(), true);
			stats.changeXp(playerId, bot.getXpReward());
			quests.progress(playerId, "KILL_NPC", 1);
			quests.progress(playerId, "EARN_GOLD", bot.getMoneyReward());
			if (factionManager != null) factionManager.addRep(playerId, "ВОИНЫ", 1);
			npcManager.respawnBot(bot);
		} else {
			int level = player.getLevel();
			int penaltyPct = level <= 5 ? 5 : level <= 15 ? 10 : level <= 30 ? 15 : 20;
			event.getChannel().sendMessage("💀 **" + bot.getNickName() + "** победил тебя! 😵 Ты воскрешён на Респауне и потерял **" + penaltyPct + "%** монет.").submit();
			npcManager.respawnBot(bot);
			stats.deathOfPlayer(player);
		}
	}

	/**
	 * Инициирует бой с боссом текущей локации.
	 * Клановые участники в той же локации сражаются вместе.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void bossFight(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		// Проверка кулдауна на бой с боссом
		String playerId = event.getAuthor().getId();
		java.time.Instant lastKill = lastBossKillTime.get(playerId);
		if (lastKill != null) {
			java.time.Instant nextAllowed = lastKill.plusSeconds(BOSS_COOLDOWN_HOURS * 3600);
			if (java.time.Instant.now().isBefore(nextAllowed)) {
				long minutesLeft = java.time.Duration.between(java.time.Instant.now(), nextAllowed).toMinutes() + 1;
				event.getChannel().sendMessage("⏳ Ты недавно сражался с боссом. Следующий бой доступен через **" + minutesLeft + " мин**.").submit();
				return;
			}
		}
		var loc = locationManager.getLocation(player.getLocation());
		if (loc.getBoss() == null) {
			event.getChannel().sendMessage("В этой локации нет босса, перейди в другую если хочешь присесть на бутылку").submit();
		} else {
			event.getChannel().sendMessage("Ты отважился бросить вызов боссу по имени " + loc.getBoss() + " земля тебе пухом братишка").submit();
			// Always include the player themselves; getPlayersByClan returns only clan members and may exclude the solo player
			List<Player> players = new ArrayList<>();
			players.add(player);
			getPlayersByClan(player).stream()
					.filter(p -> !p.getId().equals(player.getId()))
					.forEach(players::add);
			List<Person> playersAsPerson = players.stream()
							.map(p -> (Person) p)
							.collect(Collectors.toList());
			battleManager.bossBattle(playersAsPerson, loc.getBoss(), event.getChannel());
			for (Person play : players) {
				if (play.getHp() > 0) {
					String winnerId = ((Player) play).getId();
					stats.changeXp(winnerId, GameBalance.BOSS_KILL_XP);
					stats.changeMoney(winnerId, GameBalance.BOSS_KILL_MONEY, true);
					quests.progress(winnerId, "DEFEAT_BOSS", 1);
					quests.progress(winnerId, "EARN_GOLD", GameBalance.BOSS_KILL_MONEY);
					String bossItem = battleManager.getBossItemName(loc.getBoss());
					inventory.addNewItem(winnerId, bossItem);
					event.getChannel().sendMessage("В твой инвентарь добавлен предмет " + bossItem).submit();
					// Записываем время победы над боссом для кулдауна
					lastBossKillTime.put(((Player) play).getId(), java.time.Instant.now());
					// Достижения рейда (71)
					Player winPlayer = playerCache.get(winnerId);
					if (winPlayer != null) {
						achievements.unlock(winPlayer, "первый_рейд");
						achievements.unlock(winPlayer, "победитель_рейда");
						achievements.checkRich(winPlayer);
						achievements.checkCollector(winPlayer);
						// 2% chance of pet egg from boss kill
						if (new Random().nextInt(100) < 2 && winPlayer.getPet() == null) {
							String[] petTypes = {"WOLF", "FOX", "CAT", "RAVEN"};
							String petType = petTypes[new Random().nextInt(petTypes.length)];
							ru.chebe.litvinov.data.Pet newPet = new ru.chebe.litvinov.data.Pet(petType);
							winPlayer.setPet(newPet);
							event.getChannel().sendMessage("🥚 **Редкий дроп!** Ты нашёл яйцо питомца! Появился **" + petType + "**! Используй **+питомец** для информации.").submit();
						}
						playerCache.put(winnerId, winPlayer);
					}
				} else {
					stats.deathOfPlayer(((Player) play));
				}
			}
		}
	}

	/**
	 * Инициирует PvP-бой со случайным игроком в текущей локации.
	 * Доступно только в PvP-зонах; члены клана не атакуются.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void playersFight(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		var loc = locationManager.getLocation(player.getLocation());

		// Проверка PvP зоны
		if (!loc.isPvp()) {
			event.getChannel().sendMessage("В этой локации нельзя драться!").queue();
			return;
		}

		// Получение списка игроков
		List<String> population = new ArrayList<>(loc.getPopulationById());
		population.remove(player.getId()); // Убираем текущего игрока

		// Проверка наличия противников
		if (population.isEmpty()) {
			event.getChannel().sendMessage("Нет игроков для битвы").queue();
			return;
		}

		// Удаление членов клана из списка противников
		List<Player> clanMembers = getPlayersByClan(player);
		List<String> clanMemberIds = clanMembers.stream().map(Player::getId).collect(Collectors.toList());
		clanMemberIds.forEach(population::remove);

		if (population.isEmpty()) {
			event.getChannel().sendMessage("Все игроки здесь из вашего клана").queue();
			return;
		}

		// Выбор случайного противника
		String enemyId = population.get(random.nextInt(population.size()));
		Player enemy = playerCache.get(enemyId);

		if (enemy == null) {
			event.getChannel().sendMessage("Ошибка при выборе противника").queue();
			return;
		}

		// Формирование команд
		List<Person> attackers = new ArrayList<>(getPlayersByClan(player));
		if (attackers.isEmpty()) attackers.add(player); // одиночный игрок без клана
		List<Person> defenders = new ArrayList<>(getPlayersByClan(enemy).stream()
						.map(p -> (Person) p)
						.collect(Collectors.toList()));
		if (defenders.isEmpty()) defenders = List.of(enemy); // Если противник без клана

		// Проведение боя
		List<Person> battleResult = battleManager.playerBattle(attackers, defenders, event.getChannel());

		// Обработка результатов
		// Find winner id and loser id for bounty claim
		String pvpWinnerId = null;
		String pvpLoserId = null;
		for (Person p : battleResult) {
			Player pObj = (Player) p;
			if (p.getHp() > 0 && attackers.contains(p)) pvpWinnerId = pObj.getId();
			if (p.getHp() <= 0 && defenders.contains(p)) pvpLoserId = pObj.getId();
		}
		final String finalWinnerId = pvpWinnerId;
		final String finalLoserId = pvpLoserId;

		battleResult.forEach(p -> {
			Player pObj = (Player) p;
			if (p.getHp() > 0) {
				if (attackers.contains(p)) {
					stats.changeMoney(pObj.getId(), GameBalance.PVP_WIN_MONEY, true);
					stats.changeXp(pObj.getId(), GameBalance.PVP_WIN_XP);
					// Трекинг PvP побед (71)
					ReentrantLock pvpLock = playerLocks.get(pObj.getId());
					pvpLock.lock();
					try {
						Player pvpWinner = playerCache.get(pObj.getId());
						if (pvpWinner != null) {
							pvpWinner.setPvpWins(pvpWinner.getPvpWins() + 1);
							if (pvpWinner.getPvpWins() >= 100) achievements.unlock(pvpWinner, "100_pvp");
							playerCache.put(pObj.getId(), pvpWinner);
						}
					} finally {
						pvpLock.unlock();
					}
					// Bounty hook
					if (bountyManager != null && finalWinnerId != null && finalLoserId != null && pObj.getId().equals(finalWinnerId)) {
						int bountyReward = bountyManager.claimBounty(finalWinnerId, finalLoserId);
						if (bountyReward > 0) {
							Player loserPlayer = playerCache.get(finalLoserId);
							event.getChannel().sendMessage("🎯 **" + pObj.getNickName() + "** получил бонт **" + bountyReward + "** монет за голову **" + (loserPlayer != null ? loserPlayer.getNickName() : finalLoserId) + "**!").queue();
						}
					}
					event.getChannel().sendMessage(pObj.getNickName() + " получает награду!").queue();
				}
			} else {
				stats.deathOfPlayer(pObj);
				event.getChannel().sendMessage(pObj.getNickName() + " погиб!").queue();
			}
		});

		Location updatedLoc = locationManager.getLocation(player.getLocation());
		event.getChannel().sendMessage("Оставшиеся игроки: " + updatedLoc.getPopulationByName()).queue();
	}

	/** +убить нпс — клановый бой против NPC (25) */
	public void clanNpcFight(MessageReceivedEvent event) {
		String playerId = event.getAuthor().getId();
		Player player = playerCache.get(playerId);
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Клановый бой доступен только участникам клана.").submit();
			return;
		}
		List<java.util.concurrent.locks.ReentrantLock> locks = new ArrayList<>();
		List<Player> clanPlayers = getPlayersByClan(player);
		if (clanPlayers.isEmpty()) {
			event.getChannel().sendMessage("Нет других членов клана в этой локации.").submit();
			return;
		}
		clanPlayers.add(0, player);
		List<ru.chebe.litvinov.data.Person> members = clanPlayers.stream()
				.map(p -> (ru.chebe.litvinov.data.Person) p)
				.collect(Collectors.toList());
		event.getChannel().sendMessage("⚔️ Клан **" + player.getClanName() + "** идёт в бой!").submit();
		List<ru.chebe.litvinov.data.Person> result = battleManager.clanNpcBattle(members, event.getChannel());
		for (ru.chebe.litvinov.data.Person p : result) {
			if (p instanceof Player pl && pl.getId() != null) {
				if (pl.getHp() > 0) {
					stats.changeMoney(pl.getId(), GameBalance.MOB_KILL_MONEY * 2, true);
					stats.changeXp(pl.getId(), GameBalance.MOB_KILL_XP * 2);
				} else {
					stats.deathOfPlayer(pl);
				}
			}
		}
	}

	/** +последний бой — показывает лог последнего боя (24) */
	public void lastBattleLog(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		String log = battleManager.getLastBattleLog(id);
		if (log == null || log.isBlank() || log.equals("Нет данных о последнем бое.")) {
			event.getChannel().sendMessage("У тебя ещё не было боёв, или данные не сохранились.").submit();
		} else {
			event.getChannel().sendMessage("📜 **Последний бой:**\n" + log).submit();
		}
	}

	private List<Player> getPlayersByClan(Player player) {
		return clanManager.getClanMembers(player.getClanName()).stream()
						.map(playerCache::get)
						.filter(p -> p != null && p.getLocation().equals(player.getLocation()))
						.collect(Collectors.toList());
	}
}
