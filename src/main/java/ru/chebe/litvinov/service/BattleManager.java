package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Boss;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class BattleManager {

	private final IgniteCache<String, Location> locationCache;
	private final IgniteCache<String, Player> playerCache;
	private final IgniteCache<String, Boss> bossCache;
	private final PlayersManager playersManager;
	private final Random rand = new Random();

	public BattleManager(IgniteCache<String, Location> locationCache, IgniteCache<String, Player> playerCache,
	                     IgniteCache<String, Boss> bossCache, PlayersManager playersManager) {
		this.locationCache = locationCache;
		this.playerCache = playerCache;
		this.bossCache = bossCache;
		this.playersManager = playersManager;
		init();
	}

	public void init() {
		Map<String, Boss> map = new HashMap<>();
		map.put("cynic mansion", Boss.builder().name("cynic mansion").hp(1000).strength(10).defeat(0).win(0).bossItem("кисточка циника").build());
		map.put("Darhalas", Boss.builder().name("Darhalas").hp(1000).strength(10).defeat(0).win(0).bossItem("корона дарха").build());
		map.put("Ctin", Boss.builder().name("Ctin").hp(1000).strength(10).defeat(0).win(0).bossItem("кринж стина").build());
		map.put("Ushas", Boss.builder().name("Ushas").hp(1000).strength(10).defeat(0).win(0).bossItem("попка ушаса").build());
		map.put("Morgott", Boss.builder().name("Morgott").hp(1000).strength(10).defeat(0).win(0).bossItem("око мора").build());
		map.put("Usual_god", Boss.builder().name("Usual_god").hp(1000).strength(10).defeat(0).win(0).bossItem("очко бога").build());
		map.put("Red", Boss.builder().name("Red").hp(1000).strength(10).defeat(0).win(0).bossItem("сиськи ред").build());
		map.put("Рианель", Boss.builder().name("Рианель").hp(1000).strength(10).defeat(0).win(0).bossItem("удача рианель").build());
		map.put("Labynkyr", Boss.builder().name("Labynkyr").hp(1000).strength(10).defeat(0).win(0).bossItem("шарики лаба").build());
		map.put("Arktulz", Boss.builder().name("Arktulz").hp(1000).strength(10).defeat(0).win(0).bossItem("вонь арктулза").build());
		map.put("Ябыс", Boss.builder().name("Ябыс").hp(1000).strength(10).defeat(0).win(0).bossItem("скейт ябыса").build());
		map.put("Orson", Boss.builder().name("Orson").hp(1000).strength(10).defeat(0).win(0).bossItem("форточка орсона").build());
		map.put("Gordon", Boss.builder().name("Gordon").hp(1000).strength(10).defeat(0).win(0).bossItem("месть гордона").build());
		map.put("Buzzz", Boss.builder().name("Buzzz").hp(1000).strength(10).defeat(0).win(0).bossItem("хатка база").build());
		map.put("la_brioche", Boss.builder().name("la_brioche").hp(1000).strength(10).defeat(0).win(0).bossItem("игла бувки").build());
		map.put("Stalker", Boss.builder().name("Stalker").hp(1000).strength(10).defeat(0).win(0).bossItem("калькулятор сталкера").build());
		map.put("Crown", Boss.builder().name("Crown").hp(1000).strength(10).defeat(0).win(0).bossItem("язык вороны").build());
		map.put("Илья", Boss.builder().name("Илья").hp(1000).strength(10).defeat(0).win(0).bossItem("диплом ильи").build());
		map.put("Chegobnk", Boss.builder().name("Chegobnk").hp(1000).strength(10).defeat(0).win(0).bossItem("кресло чегоба").build());
		map.put("Вуъщт", Boss.builder().name("Вуъщт").hp(1000).strength(10).defeat(0).win(0).bossItem("хуй вущъта").build());
		map.put("Eduard", Boss.builder().name("Eduard").hp(1000).strength(10).defeat(0).win(0).bossItem("банка эдика").build());
		map.put("Rover", Boss.builder().name("Rover").hp(1000).strength(10).defeat(0).win(0).bossItem("бицушка ровера").build());

		if (bossCache != null) {
			map.forEach((name, boss) -> {
				if (bossCache.get(name) == null) {
					bossCache.put(name, boss);
				}
			});
		}
	}

	public void bossFight(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getName());
		var loc = locationCache.get(player.getLocation());
		if (loc.getBoss() == null) {
			event.getChannel().sendMessage("В этой локации нет босса, перейди в другую если хочешь присесть на бутылку").submit();
		} else {
			event.getChannel().sendMessage("Ты отважился бросить вызов боссу по имени " + loc.getBoss() + " земля тебе пухом братишка").submit();
			battleMechanic(player, null, bossCache.get(loc.getBoss()), event.getChannel());
		}
	}

	public void playersFight(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getName());
		var loc = locationCache.get(player.getLocation());
		if (!loc.isPvp()) {
			event.getChannel().sendMessage("В этой локации нельзя драться, я щас милицию вызову!!!").submit();
		}
		if (loc.getPopulation().size() < 2) {
			event.getChannel().sendMessage("В этой локации нет игроков, желаете набить ебало самому себе?").submit();
		} else {
			List<String> population = loc.getPopulation();
			population.remove(player.getNickName());
			int size = population.size();
			Player players2 = playerCache.get(population.get(rand.nextInt(size - 1)));
			event.getChannel().sendMessage("Судьба свела тебя в битве против " + players2.getNickName() + " одному из вас не уйти живым").submit();
			battleMechanic(player, players2, null, event.getChannel());
		}
	}

	public void mobFight(MessageReceivedEvent event, Player player) {
		Boss mob = Boss.builder().name("mob").hp(rand.nextInt(15,35)).strength(3).defeat(0).win(0).bossItem(null).build();
		battleMechanic(player, null, mob, event.getChannel());
	}


	private void battleMechanic(Player player1, Player player2, Boss boss, MessageChannelUnion channel) {
		if (boss == null) {
			while (player1.getHp() > 0 && player2.getHp() > 0) {
				int damage = randomizeDamage(player1.getStrength() - player2.getArmor());
				player2.setHp(player2.getHp() - damage);
				channel.sendMessage("Игрок " + player1.getNickName() + " наносит " + damage + " урона противнику, у него остаётся " + player2.getHp() + " HP").submit();
				if (player2.getHp() < 0) {
					continue;
				}
				damage = randomizeDamage(player2.getStrength() - player1.getArmor());
				player1.setHp(player1.getHp() - damage);
				channel.sendMessage("Игрок " + player2.getNickName() + " наносит " + damage + " урона противнику, у него остаётся " + player1.getHp() + " HP").submit();
			}
		} else {
			while (player1.getHp() > 0 && boss.getHp() > 0) {
				int damage = randomizeDamage(player1.getStrength());
				boss.setHp(boss.getHp() - damage);
				channel.sendMessage("Игрок " + player1.getNickName() + " наносит " + damage + " урона противнику, у него остаётся " + boss.getHp() + " HP").submit();
				damage = randomizeDamage(boss.getStrength() - player1.getArmor());
				player1.setHp(player1.getHp() - damage);
				channel.sendMessage("Босс " + boss.getName() + " наносит " + damage + " урона противнику, у него остаётся " + player1.getHp() + " HP").submit();
			}
		}
		if (boss != null) {
			if (boss.getHp() > 0) {
				channel.sendMessage("Штош нужно быть очень глупым чтобы залупаться на " + boss.getName() + " с твоими характеристиками").submit();
				channel.sendMessage("Ты умер и был воскрешен на Респауне, ты потерял 10% монет и возможно кое-что из предметов").submit();
				playersManager.deathOfPlayer(player1);
				boss = bossCache.get(boss.getName());
				boss.setWin(boss.getWin() + 1);
				bossCache.put(boss.getName(), boss);
			} else {
				if (boss.getName().equals("mob")) {
					channel.sendMessage("Поздравляю ты победил тупого засланца при переходе локации").submit();
					playersManager.changeXp(player1.getNickName(), 10);
					playersManager.changeMoney(player1.getNickName(), 10, true);
					playersManager.changeXp(player1.getNickName(), player1.getHp());
				} else {
					channel.sendMessage("Поздравляю ты победил босса этой локации " + boss.getName()).submit();
					playersManager.changeXp(player1.getNickName(), 1000);
					playersManager.changeMoney(player1.getNickName(), 1000, true);
					playersManager.changeXp(player1.getNickName(), player1.getHp());
				}
				if (boss.getBossItem() != null) {
					playersManager.addNewItem(player1.getNickName(), boss.getBossItem());
					channel.sendMessage("В твой инвентарь добавлен предмет " + boss.getBossItem()).submit();
				}
			}
		} else {
			Player defeat = player1.getHp() > 0 ? player1 : player2;
			Player winner = player1.getHp() > 0 ? player2 : player1;
			int xp = playersManager.getXp(defeat);
			int money = defeat.getMoney() / 2;
			channel.sendMessage("Игрок " + defeat.getNickName() + " побеждает в этой славной битве и получает " + xp + " опыта и " + money + " монет").submit();
			channel.sendMessage("Игрок " + defeat.getNickName() + " умер и был воскрешен на Респауне, он потерял 10% монет и возможно кое-что из предметов").submit();
			playersManager.changeMoney(winner.getNickName(), money, true);
			playersManager.changeHp(winner.getNickName(), winner.getHp());
			playersManager.changeXp(winner.getNickName(), xp);
			playersManager.deathOfPlayer(defeat);
		}
	}

	private int randomizeDamage(int damage) {
		double percentageChange = (rand.nextInt(51) - 25) / 100.0; // От -25% до +25%
		return (int)(damage + (damage * percentageChange)) * 3;
	}
}
