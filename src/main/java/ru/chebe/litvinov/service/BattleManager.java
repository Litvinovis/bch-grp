package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import ru.chebe.litvinov.data.Boss;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.BossRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Менеджер боевой системы.
 * Управляет боями между игроками, мобами и боссами, а также хранит состояние боссов в Ignite-кэше.
 */
public class BattleManager {

	private final BossRepository bossCache;
	private final Random rand = new Random();
	private final Map<String, Boss> localBossMap = new HashMap<>();
	private final Map<String, String> lastBattleLog = new ConcurrentHashMap<>();
	private PetManager petManager;

	/**
	 * Создаёт менеджер боёв и инициализирует репозиторий боссов начальными данными.
	 *
	 * @param bossCache репозиторий Ignite 3 для хранения состояния боссов
	 */
	public BattleManager(BossRepository bossCache) {
		this.bossCache = bossCache;
		init();
	}

	/**
	 * Инициализирует кэш боссов предустановленными данными.
	 * Боссы добавляются только если ещё не существуют в кэше.
	 */
	public void init() {
		Map<String, Boss> map = new HashMap<>();
		// Тир 1 — danger 10–25
		map.put("Labynkyr",  Boss.builder().nickName("Labynkyr").hp(300).strength(6).defeat(0).win(0).bossItem("шарики лаба").build());
		map.put("Arktulz",   Boss.builder().nickName("Arktulz").hp(280).strength(5).defeat(0).win(0).bossItem("вонь арктулза").build());
		map.put("Red",       Boss.builder().nickName("Red").hp(320).strength(7).defeat(0).win(0).bossItem("сиськи ред").build());
		map.put("Gordon",    Boss.builder().nickName("Gordon").hp(290).strength(6).defeat(0).win(0).bossItem("месть гордона").build());
		map.put("Buzzz",     Boss.builder().nickName("Buzzz").hp(280).strength(5).defeat(0).win(0).bossItem("хатка база").build());
		map.put("Ябыс",      Boss.builder().nickName("Ябыс").hp(340).strength(7).defeat(0).win(0).bossItem("скейт ябыса").build());
		map.put("Orson",     Boss.builder().nickName("Orson").hp(285).strength(5).defeat(0).win(0).bossItem("форточка орсона").build());
		map.put("la_brioche",Boss.builder().nickName("la_brioche").hp(290).strength(6).defeat(0).win(0).bossItem("игла бувки").build());
		// Тир 2 — danger 35
		map.put("Morgott",   Boss.builder().nickName("Morgott").hp(600).strength(10).defeat(0).win(0).bossItem("око мора").build());
		map.put("Usual_god", Boss.builder().nickName("Usual_god").hp(580).strength(9).defeat(0).win(0).bossItem("очко бога").build());
		map.put("Рианель",   Boss.builder().nickName("Рианель").hp(620).strength(11).defeat(0).win(0).bossItem("удача рианель").build());
		map.put("Stalker",   Boss.builder().nickName("Stalker").hp(640).strength(11).defeat(0).win(0).bossItem("калькулятор сталкера").build());
		map.put("Crown",     Boss.builder().nickName("Crown").hp(590).strength(9).defeat(0).win(0).bossItem("язык вороны").build());
		// Тир 3 — danger 50
		map.put("Ctin",      Boss.builder().nickName("Ctin").hp(880).strength(13).defeat(0).win(0).bossItem("кринж стина").build());
		map.put("Ushas",     Boss.builder().nickName("Ushas").hp(900).strength(14).defeat(0).win(0).bossItem("попка ушаса").build());
		map.put("Илья",      Boss.builder().nickName("Илья").hp(920).strength(14).defeat(0).win(0).bossItem("диплом ильи").build());
		map.put("Вуъщт",     Boss.builder().nickName("Вуъщт").hp(940).strength(15).defeat(0).win(0).bossItem("хуй вущъта").build());
		map.put("Eduard",    Boss.builder().nickName("Eduard").hp(890).strength(13).defeat(0).win(0).bossItem("банка эдика").build());
		// Тир 4 — danger 70–80
		map.put("cynic mansion", Boss.builder().nickName("cynic mansion").hp(1400).strength(20).defeat(0).win(0).bossItem("кисточка циника").build());
		map.put("Rover",     Boss.builder().nickName("Rover").hp(1300).strength(18).defeat(0).win(0).bossItem("бицушка ровера").build());
		map.put("Chegobnk",  Boss.builder().nickName("Chegobnk").hp(1350).strength(19).defeat(0).win(0).bossItem("кресло чегоба").build());
		map.put("Darhalas",  Boss.builder().nickName("Darhalas").hp(1600).strength(22).defeat(0).win(0).bossItem("корона дарха").build());

		localBossMap.putAll(map);
		if (bossCache != null) {
			map.forEach((name, boss) -> {
				if (!bossCache.contains(name)) {
					bossCache.put(name, boss);
				}
			});
		}
	}

	public void setPetManager(PetManager pm) { this.petManager = pm; }

	/** Возвращает босса по имени (для тестов и инспекции). */
	public Boss getBoss(String name) {
		if (bossCache != null) return bossCache.get(name);
		return localBossMap.get(name);
	}

	/**
	 * Проводит PvP-бой между двумя командами игроков.
	 *
	 * @param players1 атакующая команда
	 * @param players2 защищающаяся команда
	 * @param channel  Discord-канал для вывода сообщений о ходе боя
	 * @return объединённый список всех участников с актуальным HP после боя
	 */
	public List<Person> playerBattle(List<Person> players1, List<Person> players2, MessageChannelUnion channel) {
		if (players1.isEmpty() || players2.isEmpty()) {
			channel.sendMessage("Невозможно начать бой!").queue();
			return Collections.emptyList();
		}
		battleMechanic(players1, players2, channel);
		List<Person> result = new ArrayList<>(players1);
		result.addAll(players2);
		return result;
	}

	/**
	 * Проводит бой игрока со случайным мобом при переходе в опасную локацию.
	 *
	 * @param player  игрок, вступающий в бой
	 * @param channel Discord-канал для вывода сообщений
	 * @return 1 если игрок победил, -1 если проиграл
	 */
	public int mobBattle(Player player, MessageChannelUnion channel) {
		int bossStrength = player.getLevel() < 3 ? 2 : 3;
		int bossHpMin = player.getLevel() < 3 ? 10 : 15;
		int bossHpMax = player.getLevel() < 3 ? 25 : 35;

		Person boss = Boss.builder().nickName("Бандит").hp(rand.nextInt(bossHpMin, bossHpMax)).strength(bossStrength).defeat(0).win(0).bossItem(null).build();
		channel.sendMessage("⚠️ На тебя напал **Бандит**! ⚔️ Бой начинается...").submit();
		battleMechanic(List.of(player), List.of(boss), channel);
		if (boss.getHp() > 0) {
			channel.sendMessage("""
					💀 Тебя убил мелкий бандит... Позор! 😤
					Ты воскрешён на Респауне, потерял **10%** монет и возможно кое-что из инвентаря.""").queue();
			return -1;
		} else {
			channel.sendMessage("✅ Ты разобрался с бандитом при переходе! Путь свободен 💪").submit();
			return player.getHp();
		}
	}

	public String getMobTierDrop(int level) {
		if (level <= 5) {
			String[] consumables = {"кружка цикория", "вино лаба", "медовуха база", "токен телепорта"};
			return consumables[rand.nextInt(consumables.length)];
		} else if (level <= 15) {
			String[] commonDrops = {"шарики лаба", "вонь арктулза", "скейт ябыса", "форточка орсона", "месть гордона", "хатка база", "игла бувки"};
			return commonDrops[rand.nextInt(commonDrops.length)];
		} else {
			String[] rareDrops = {"калькулятор сталкера", "язык вороны", "диплом ильи", "кресло чегоба", "сиськи ред", "банка эдика", "бицушка ровера", "кисточка циника", "корона дарха"};
			return rareDrops[rand.nextInt(rareDrops.length)];
		}
	}

	public List<Person> clanNpcBattle(List<Person> players, MessageChannelUnion channel) {
		int totalStrength = players.stream().mapToInt(p -> p.getStrength()).sum();
		int avgLevel = (int) players.stream()
				.filter(p -> p instanceof Player)
				.mapToInt(p -> ((Player) p).getLevel())
				.average().orElse(1.0);

		int npcStrength = Math.max(3, avgLevel / 2 + 2);
		int npcHp = 30 + avgLevel * 5;
		Person npc = Boss.builder().nickName("Клановый НПС").hp(npcHp).strength(npcStrength).defeat(0).win(0).bossItem(null).build();
		channel.sendMessage("⚔️ Клан атакует **Клановый НПС** [❤️ HP: **" + npcHp + "**]!").submit();
		battleMechanic(players, List.of(npc), channel);
		List<Person> result = new ArrayList<>(players);
		result.add(npc);
		return result;
	}

	/**
	 * Проводит бой команды игроков против босса локации.
	 * После боя HP босса восстанавливается до начального значения, обновляется статистика побед/поражений.
	 *
	 * @param players  список игроков-участников боя
	 * @param bossName имя босса из кэша
	 * @param channel  Discord-канал для вывода сообщений
	 */
	public void bossBattle(List<Person> players, String bossName, MessageChannelUnion channel) {
		Boss boss = bossCache.get(bossName);
		if (boss == null) {
			channel.sendMessage("Босс не найден.").queue();
			return;
		}
		int initialBossHp = boss.getHp();
		int initialBossWin = boss.getWin();

		battleMechanic(players, List.of(boss), channel);

		boolean bossDefeated = boss.getHp() <= 0;

		if (!bossDefeated) {
			channel.sendMessage("""
					💀 **%s** смотрит на тебя с презрением... 😵
					Штош, нужно было получше прокачаться перед таким боем!
					Ты воскрешён на Респауне, потерял **10%%** монет и возможно кое-что из предметов.""".formatted(boss.getNickName())).queue();
			boss.setWin(initialBossWin + 1);
		} else {
			channel.sendMessage("🏆 **Победа!** Ты одолел **" + boss.getNickName() + "**! Слава герою! 🎉").submit();
			boss.setDefeat(boss.getDefeat() + 1);
		}

		boss.setHp(initialBossHp);
		bossCache.put(boss.getNickName(), boss);
	}

	/**
	 * Реализует пошаговую механику боя двух команд.
	 * Бой идёт пока в каждой команде есть хотя бы один живой участник.
	 *
	 * @param team1   первая команда
	 * @param team2   вторая команда
	 * @param channel Discord-канал для вывода сообщений о ходе боя
	 */
	public void battleMechanic(List<Person> team1, List<Person> team2, MessageChannelUnion channel) {
		battleMechanicInternal(team1, team2, channel, null);
	}

	public void battleMechanicWithLog(List<Person> team1, List<Person> team2, MessageChannelUnion channel, String attackerPlayerId) {
		battleMechanicInternal(team1, team2, channel, attackerPlayerId);
	}

	private void battleMechanicInternal(List<Person> team1, List<Person> team2, MessageChannelUnion channel, String logPlayerId) {
		StringBuilder sb = new StringBuilder();
		StringBuilder fullLog = new StringBuilder();
		int round = 0;
		while (checkHpPlayerList(team1) && checkHpPlayerList(team2)) {
			round++;
			if (sb.length() > 1800) {
				channel.sendMessage(sb.toString()).submit();
				sb.setLength(0);
			}

			// Воин: регенерация 5% HP в начале раунда (19)
			for (Person p : team1) {
				if (p instanceof Player pl && "ВОИН".equals(pl.getPlayerClass()) && pl.getHp() > 0) {
					int regen = Math.max(1, pl.getMaxHp() / 20);
					pl.setHp(Math.min(pl.getMaxHp(), pl.getHp() + regen));
				}
			}
			for (Person p : team2) {
				if (p instanceof Player pl && "ВОИН".equals(pl.getPlayerClass()) && pl.getHp() > 0) {
					int regen = Math.max(1, pl.getMaxHp() / 20);
					pl.setHp(Math.min(pl.getMaxHp(), pl.getHp() + regen));
				}
			}

			Person attacker = getRandomPlayer(team1);
			Person defender = getRandomPlayer(team2);

			if (attacker != null && defender != null) {
				String roundLabel = "**Раунд " + round + "**\n";
				sb.append(roundLabel);
				fullLog.append(roundLabel);

				// Маг: первый удар без контратаки (17)
				boolean isMageFirstStrike = attacker instanceof Player
						&& "МАГ".equals(((Player) attacker).getPlayerClass())
						&& round == 1;

				// Pet bonus to strength (item 9)
				int attackerStrength = attacker.getStrength();
				if (attacker instanceof Player playerAttacker && playerAttacker.getPet() != null
						&& playerAttacker.getPet().getHunger() > 0) {
					int petStrBonus = petManager != null ? petManager.getPetBattleBonus(playerAttacker, "str") : 0;
					attackerStrength += petStrBonus;
					if (petStrBonus > 0) {
						String petMsg = "🐾 Питомец добавляет **+" + petStrBonus + "** к силе!\n";
						sb.append(petMsg);
						fullLog.append(petMsg);
					}
				}

				// Следопыт: природная ловушка — 20% шанс враг пропускает ход
				if (attacker instanceof Player trapPl && trapPl.getSkills() != null
						&& trapPl.getSkills().getOrDefault("природная ловушка", 0) > 0
						&& rand.nextInt(100) < 20) {
					String trapMsg = "🌿 **Природная ловушка!** **" + defender.getNickName() + "** пропускает ход!\n";
					sb.append(trapMsg);
					fullLog.append(trapMsg);
					continue;
				}

				// Рассчитываем шанс крита: базовый 10%, у игрока +luck/2
				int critChance = 10;
				if (attacker instanceof Player) {
					critChance = 10 + ((Player) attacker).getLuck() / 2;
				}
				boolean crit = isCriticalHit(critChance);
				int damage = randomizeDamage(attackerStrength);
				if (crit) {
					damage *= 2;
				}

				// Skill bonuses for attacker (item 10)
				if (attacker instanceof Player pl) {
					Map<String, Integer> skills = pl.getSkills();
					if (skills != null) {
						// берсерк: +15% damage
						if (skills.getOrDefault("берсерк", 0) > 0) {
							damage = (int)(damage * 1.15);
						}
						// молния: first attack 150%
						if (round == 1 && skills.getOrDefault("молния", 0) > 0) {
							damage = (int)(damage * 1.5);
						}
					}
				}

				// Блок защитника (16)
				int defenderArmor = defender instanceof Player ? ((Player) defender).getArmor() : defender.getArmor();
				int blockChance = defenderArmor * 3;
				boolean blocked = rand.nextInt(100) < blockChance;
				if (blocked) {
					damage = damage / 2;
					String blockMsg = "🛡️ **" + defender.getNickName() + "** заблокировал!\n";
					sb.append(blockMsg);
					fullLog.append(blockMsg);
				}

				// Уклонение защитника (15)
				int defenderLuck = defender instanceof Player ? ((Player) defender).getLuck() : 0;
				int dodgeChance = 5 + defenderLuck * 2;
				// Skill dodge bonuses (item 10)
				if (defender instanceof Player defPl && defPl.getSkills() != null) {
					if (defPl.getSkills().getOrDefault("щит маны", 0) > 0) dodgeChance += 30;
					if (defPl.getSkills().getOrDefault("уклонение мастера", 0) > 0) dodgeChance += 20;
				}
				boolean dodged = rand.nextInt(100) < dodgeChance;

				if (dodged) {
					String dodgeMsg = "💨 **" + defender.getNickName() + "** уклонился!\n";
					sb.append(dodgeMsg);
					fullLog.append(dodgeMsg);

					// Контратака разбойника при уклонении (18)
					if (defender instanceof Player defPlayer && "РАЗБОЙНИК".equals(defPlayer.getPlayerClass())) {
						int counterDmg = randomizeDamage(defPlayer.getStrength() / 2);
						attacker.setHp(attacker.getHp() - counterDmg);
						String counterMsg = "🗡️ **" + defPlayer.getNickName() + "** контратакует: **" + counterDmg
								+ "** 🩸 → **" + attacker.getNickName()
								+ "** (❤️ **" + Math.max(0, attacker.getHp()) + "** HP)\n";
						sb.append(counterMsg);
						fullLog.append(counterMsg);
					}
				} else {
					// Яд (ядовитый клинок): 10% chance per round for extra 5 damage
					if (attacker instanceof Player poisonPl && poisonPl.getSkills() != null
							&& poisonPl.getSkills().getOrDefault("ядовитый клинок", 0) > 0
							&& rand.nextInt(100) < 10) {
						damage += 5;
						String poisonMsg = "☠️ **Яд!** +5 к урону!\n";
						sb.append(poisonMsg);
						fullLog.append(poisonMsg);
					}

					int defenderHpBefore = defender.getHp();
					defender.setHp(defender.getHp() - damage);

					// второе дыхание: if hp drops below 20%, add 20 HP
					if (defender instanceof Player defPl && defPl.getSkills() != null
							&& defPl.getSkills().getOrDefault("второе дыхание", 0) > 0
							&& defPl.getHp() > 0 && defPl.getHp() < defPl.getMaxHp() * 0.2
							&& defenderHpBefore >= defPl.getMaxHp() * 0.2) {
						defPl.setHp(defPl.getHp() + 20);
						String breathMsg = "💪 **Второе дыхание!** **" + defPl.getNickName() + "** восстанавливает 20 HP!\n";
						sb.append(breathMsg);
						fullLog.append(breathMsg);
					}

					String hitPrefix = crit ? "💥 КРИТ! **" : "⚔️ **";
					String attackMsg = hitPrefix + attacker.getNickName() + "** наносит **" + damage
							+ "** 🩸 → **" + defender.getNickName()
							+ "** (❤️ **" + Math.max(0, defender.getHp()) + "** HP)\n";
					sb.append(attackMsg);
					fullLog.append(attackMsg);

					// Pet special attack after round 3
					if (round > 3 && attacker instanceof Player petAttacker && petAttacker.getPet() != null
							&& petAttacker.getPet().getHunger() > 0 && petManager != null) {
						int petDmg = petManager.getPetBattleBonus(petAttacker, "str");
						if (petDmg > 0) {
							defender.setHp(defender.getHp() - petDmg);
							String petAtkMsg = "🐾 **Питомец атакует** " + defender.getNickName() + " на **" + petDmg + "** HP!\n";
							sb.append(petAtkMsg);
							fullLog.append(petAtkMsg);
						}
					}

					if (defender.getHp() <= 0) {
						String defeatMsg = "💀 **" + defender.getNickName() + "** повержен!\n";
						sb.append(defeatMsg);
						fullLog.append(defeatMsg);

						// Чистая победа — убийство с одного удара (20)
						if (defenderHpBefore > 0 && attacker instanceof Player pl) {
							pl.setReputation(pl.getReputation() + 5);
							String cleanKillMsg = "✨ Чистая победа! **" + pl.getNickName() + "** получает +5 репутации!\n";
							sb.append(cleanKillMsg);
							fullLog.append(cleanKillMsg);
						}
					} else if (!isMageFirstStrike) {
						// Контратака защитника
						damage = randomizeDamage(defender.getStrength() - attacker.getArmor());
						attacker.setHp(attacker.getHp() - damage);
						String counterMsg = "↩️ **" + defender.getNickName() + "** отвечает **" + damage
								+ "** 🩸 → **" + attacker.getNickName()
								+ "** (❤️ **" + Math.max(0, attacker.getHp()) + "** HP)\n";
						sb.append(counterMsg);
						fullLog.append(counterMsg);
					}
				}
			} else {
				break;
			}
		}
		if (!sb.isEmpty()) {
			channel.sendMessage(sb.toString()).submit();
		}
		if (logPlayerId != null) {
			lastBattleLog.put(logPlayerId, fullLog.toString());
		}
	}

	public String getLastBattleLog(String playerId) {
		return lastBattleLog.getOrDefault(playerId, "Нет данных о последнем бое.");
	}

	/**
	 * Возвращает название предмета, выпадающего с указанного босса.
	 *
	 * @param bossName имя босса
	 * @return название предмета или null если босс не найден
	 */
	public String getBossItemName(String bossName) {
		Boss boss = bossCache.get(bossName);
		return boss != null ? boss.getBossItem() : null;
	}

	private boolean checkHpPlayerList(List<Person> players) {
		for (Person player : players) {
			if (player != null && player.getHp() > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Вычисляет случайный урон на основе базового значения с отклонением ±25%.
	 *
	 * @param baseDamage базовое значение урона
	 * @return итоговое значение урона (минимум 1, если baseDamage > 0; 0 если baseDamage <= 0)
	 */
	protected int randomizeDamage(int baseDamage) {
		if (baseDamage <= 0) return 0;
		double percentageChange = (rand.nextInt(51) - 25) / 100.0; // От -25% до +25%
		return Math.max(1, (int) (baseDamage * (1 + percentageChange)));
	}

	/**
	 * Определяет, является ли удар критическим.
	 *
	 * @param luckPercent шанс крита в процентах (например, 10 = 10%)
	 * @return true если удар критический
	 */
	protected boolean isCriticalHit(int luckPercent) {
		return rand.nextInt(100) < luckPercent;
	}

	private Person getRandomPlayer(List<Person> players) {
		if (players == null || players.isEmpty()) {
			return null;
		}

		List<Person> alivePlayers = new ArrayList<>();
		for (Person p : players) {
			if (p != null && p.getHp() > 0) {
				alivePlayers.add(p);
			}
		}

		if (alivePlayers.isEmpty()) {
			return null;
		}

		return alivePlayers.get(rand.nextInt(alivePlayers.size()));
	}
}