package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Boss;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;

import java.util.*;

public class BattleManager {

	private final IgniteCache<String, Boss> bossCache;
	private final Random rand = new Random();

	public BattleManager(IgniteCache<String, Boss> bossCache) {
		this.bossCache = bossCache;
		init();
	}

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
				if (bossCache.get(name) == null) {
					bossCache.put(name, boss);
				}
			});
		}
	}

	public List<Person> playerBattle(List<Person> players1, List<Person> players2, MessageChannelUnion channel) {
		if (players1.isEmpty() || players2.isEmpty()) {
			channel.sendMessage("Невозможно начать бой!").queue();
			return Collections.emptyList();
		}
		battleMechanic(players1, players2, channel);
		players1.addAll(players2);
		return players1;
	}

	public int mobBattle(Player player, MessageChannelUnion channel) {
		Person boss = Boss.builder().nickName("Бандит").hp(rand.nextInt(15, 35)).strength(3).defeat(0).win(0).bossItem(null).build();
		battleMechanic(List.of(player), List.of(boss), channel);
		if (boss.getHp() > 0) {
			channel.sendMessage("Тебя убил мелкий бандит, это кринж, чувак! Ты был воскрешен на Респауне, " +
							"потерял 10% монет и возможно кое-что из инвентаря").queue();
		} else {
			channel.sendMessage("Поздравляю ты победил тупого засланца при переходе локации").submit();
		}
		return Math.max(player.getHp(), 0);
	}

	public void bossBattle(List<Person> player, String bossName, MessageChannelUnion channel) {
		Boss boss = bossCache.get(bossName);
		int bossHp = boss.getHp();
		battleMechanic(player, List.of(boss), channel);
		if (boss.getHp() > 0) {
			channel.sendMessage("Штош нужно быть очень глупым чтобы залупаться на " + boss.getNickName() + " с твоими характеристиками" +
							"Ты умер и был воскрешен на Респауне, ты потерял 10% монет и возможно кое-что из предметов").queue();
			boss.setWin(boss.getWin() + 1);
		} else {
			channel.sendMessage("Поздравляю ты победил босса этой локации " + boss.getNickName()).submit();
		}
		boss.setHp(bossHp);
		bossCache.put(boss.getNickName(), boss);
	}

	public void battleMechanic(List<Person> player1, List<Person> player2, MessageChannelUnion channel) {
		StringBuilder sb = new StringBuilder();
		while (checkHpPlayerList(player1) && checkHpPlayerList(player2)) {
			if (sb.length() > 1800) {
				channel.sendMessage(sb.toString()).submit();
				sb.setLength(0);
			}
			Person playerAttack = getRandomPlayer(player1);
			Person playerDef = getRandomPlayer(player2);
			int damage = randomizeDamage(playerAttack.getStrength());
			playerDef.setHp(playerDef.getHp() - damage);
			sb.append(playerAttack.getNickName()).append(" наносит ").append(damage).append(" урона противнику, у него остаётся ").append(playerDef.getHp()).append(" HP\n");
			damage = randomizeDamage(playerDef.getStrength() - playerAttack.getArmor());
			playerAttack.setHp(playerAttack.getHp() - damage);
			sb.append(playerDef.getNickName()).append(" наносит ").append(damage).append(" урона противнику, у него остаётся ").append(playerAttack.getHp()).append(" HP\n");
		}
		channel.sendMessage(sb.toString()).submit();
	}

	private int randomizeDamage(int damage) {
		double percentageChange = (rand.nextInt(51) - 25) / 100.0; // От -25% до +25%
		return (int) (damage + (damage * percentageChange)) * 3;
	}

	public String getBossItemName(String bossName) {
		return bossCache.get(bossName).getBossItem();
	}

	private boolean checkHpPlayerList(List<Person> players) {
		for (Person player : players) {
			if (player.getHp() > 0) {
				return true;
			}
		}
		return false;
	}

	private Person getRandomPlayer(List<Person> players) {
		Person player = players.get(rand.nextInt(players.size()));
		while (player.getHp() <= 0) {
			player = players.get(rand.nextInt(players.size()));
		}
		return player;
	}
}
