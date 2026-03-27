package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import ru.chebe.litvinov.data.Boss;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.BossRepository;

import java.util.*;

/**
 * Менеджер боевой системы.
 * Управляет боями между игроками, мобами и боссами, а также хранит состояние боссов в Ignite-кэше.
 */
public class BattleManager {

	private final BossRepository bossCache;
	private final Random rand = new Random();

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
		map.put("cynic mansion", Boss.builder().nickName("cynic mansion").hp(1000).strength(10).defeat(0).win(0).bossItem("кисточка циника").build());
		map.put("Darhalas", Boss.builder().nickName("Darhalas").hp(1000).strength(10).defeat(0).win(0).bossItem("корона дарха").build());
		map.put("Ctin", Boss.builder().nickName("Ctin").hp(1000).strength(10).defeat(0).win(0).bossItem("кринж стина").build());
		map.put("Ushas", Boss.builder().nickName("Ushas").hp(1000).strength(10).defeat(0).win(0).bossItem("попка ушаса").build());
		map.put("Morgott", Boss.builder().nickName("Morgott").hp(1000).strength(10).defeat(0).win(0).bossItem("око мора").build());
		map.put("Usual_god", Boss.builder().nickName("Usual_god").hp(1000).strength(10).defeat(0).win(0).bossItem("очко бога").build());
		map.put("Red", Boss.builder().nickName("Red").hp(1000).strength(10).defeat(0).win(0).bossItem("сиськи ред").build());
		map.put("Рианель", Boss.builder().nickName("Рианель").hp(1000).strength(10).defeat(0).win(0).bossItem("удача рианель").build());
		map.put("Labynkyr", Boss.builder().nickName("Labynkyr").hp(1000).strength(10).defeat(0).win(0).bossItem("шарики лаба").build());
		map.put("Arktulz", Boss.builder().nickName("Arktulz").hp(1000).strength(10).defeat(0).win(0).bossItem("вонь арктулза").build());
		map.put("Ябыс", Boss.builder().nickName("Ябыс").hp(1000).strength(10).defeat(0).win(0).bossItem("скейт ябыса").build());
		map.put("Orson", Boss.builder().nickName("Orson").hp(1000).strength(10).defeat(0).win(0).bossItem("форточка орсона").build());
		map.put("Gordon", Boss.builder().nickName("Gordon").hp(1000).strength(10).defeat(0).win(0).bossItem("месть гордона").build());
		map.put("Buzzz", Boss.builder().nickName("Buzzz").hp(1000).strength(10).defeat(0).win(0).bossItem("хатка база").build());
		map.put("la_brioche", Boss.builder().nickName("la_brioche").hp(1000).strength(10).defeat(0).win(0).bossItem("игла бувки").build());
		map.put("Stalker", Boss.builder().nickName("Stalker").hp(1000).strength(10).defeat(0).win(0).bossItem("калькулятор сталкера").build());
		map.put("Crown", Boss.builder().nickName("Crown").hp(1000).strength(10).defeat(0).win(0).bossItem("язык вороны").build());
		map.put("Илья", Boss.builder().nickName("Илья").hp(1000).strength(10).defeat(0).win(0).bossItem("диплом ильи").build());
		map.put("Chegobnk", Boss.builder().nickName("Chegobnk").hp(1000).strength(10).defeat(0).win(0).bossItem("кресло чегоба").build());
		map.put("Вуъщт", Boss.builder().nickName("Вуъщт").hp(1000).strength(10).defeat(0).win(0).bossItem("хуй вущъта").build());
		map.put("Eduard", Boss.builder().nickName("Eduard").hp(1000).strength(10).defeat(0).win(0).bossItem("банка эдика").build());
		map.put("Rover", Boss.builder().nickName("Rover").hp(1000).strength(10).defeat(0).win(0).bossItem("бицушка ровера").build());

		if (bossCache != null) {
			map.forEach((name, boss) -> {
				if (!bossCache.contains(name)) {
					bossCache.put(name, boss);
				}
			});
		}
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
		players1.addAll(players2);
		return players1;
	}

	/**
	 * Проводит бой игрока со случайным мобом при переходе в опасную локацию.
	 *
	 * @param player  игрок, вступающий в бой
	 * @param channel Discord-канал для вывода сообщений
	 * @return 1 если игрок победил, -1 если проиграл
	 */
	public int mobBattle(Player player, MessageChannelUnion channel) {
		Person boss = Boss.builder().nickName("Бандит").hp(rand.nextInt(15, 35)).strength(3).defeat(0).win(0).bossItem(null).build();
		battleMechanic(List.of(player), List.of(boss), channel);
		if (boss.getHp() > 0) {
			channel.sendMessage("""
					Тебя убил мелкий бандит, это кринж, чувак! Ты был воскрешен на Респауне, \
					потерял 10% монет и возможно кое-что из инвентаря""").queue();
			return -1;
		} else {
			channel.sendMessage("Поздравляю ты победил тупого засланца при переходе локации").submit();
			return 1;
		}
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
			// Босс победил
			channel.sendMessage("""
					Штош нужно быть очень глупым чтобы залупаться на %s с твоими характеристиками\
					Ты умер и был воскрешен на Респауне, ты потерял 10%% монет и возможно кое-что из предметов""".formatted(boss.getNickName())).queue();
			boss.setWin(initialBossWin + 1);
		} else {
			// Игрок(и) победили
			channel.sendMessage("Поздравляю ты победил босса этой локации " + boss.getNickName()).submit();
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
		StringBuilder sb = new StringBuilder();
		while (checkHpPlayerList(team1) && checkHpPlayerList(team2)) {
			if (sb.length() > 1800) {
				channel.sendMessage(sb.toString()).submit();
				sb.setLength(0);
			}
			Person attacker = getRandomPlayer(team1);
			Person defender = getRandomPlayer(team2);

			// Атака первого на второго
			if (attacker != null && defender != null) {
				int damage = randomizeDamage(attacker.getStrength());
				defender.setHp(defender.getHp() - damage);
				sb.append(attacker.getNickName()).append(" наносит ").append(damage).append(" урона противнику, у него остаётся ").append(defender.getHp()).append(" HP ");

				// Контратака второго на первого (если второй еще жив)
				if (defender.getHp() > 0) {
					damage = randomizeDamage(defender.getStrength() - attacker.getArmor());
					attacker.setHp(attacker.getHp() - damage);
					sb.append(defender.getNickName()).append(" наносит ").append(damage).append(" урона противнику, у него остаётся ").append(attacker.getHp()).append(" HP ");
				}
			} else {
				break;
			}
		}
		if (!sb.isEmpty()) {
			channel.sendMessage(sb.toString()).submit();
		}
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