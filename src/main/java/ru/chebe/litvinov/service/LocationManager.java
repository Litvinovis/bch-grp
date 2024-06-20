package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.ignite.IgniteCache;
import org.apache.logging.log4j.util.Strings;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationManager {

	private final IgniteCache<String, Location> locationCache;
	private final IgniteCache<String, Player> playerCache;


	public LocationManager(IgniteCache<String, Location> locationCache, IgniteCache<String, Player> playerCache) {
		this.playerCache = playerCache;
		this.locationCache = locationCache;
		init(locationCache);
	}

	public static void init(IgniteCache<String, Location> locationCache) {
		Map<String, Location> map = new HashMap<>();
		map.put("загадка", Location.builder().name("загадка").pvp(true).paths(new ArrayList<>(List.of("кушетка"))).dangerous(70).population(new ArrayList<>()).boss("cynic mansion").bossItem("кисточка циника").build());
		map.put("олимп", Location.builder().name("олимп").pvp(true).paths(new ArrayList<>(List.of("модерская"))).dangerous(80).population(new ArrayList<>()).boss("Darhalas").bossItem("корона дарха").build());
		map.put("магазин", Location.builder().name("магазин").pvp(false).paths(new ArrayList<>(List.of("рекламный"))).dangerous(0).population(new ArrayList<>()).build());
		map.put("таверна", Location.builder().name("таверна").pvp(false).paths(new ArrayList<>(List.of("деградач"))).dangerous(10).population(new ArrayList<>()).build());
		map.put("кушетка", Location.builder().name("кушетка").pvp(true).paths(new ArrayList<>(List.of("загадка", "модерская", "хуй-тек"))).dangerous(50).population(new ArrayList<>()).boss("Ctin").bossItem("кринж стина").build());
		map.put("модерская", Location.builder().name("модерская").pvp(true).paths(new ArrayList<>(List.of("олимп", "кушетка", "старборд"))).dangerous(50).population(new ArrayList<>()).boss("Ushas").bossItem("попка ушаса").build());
		map.put("рекламный", Location.builder().name("рекламный").pvp(true).paths(new ArrayList<>(List.of("кринжборд", "магазин"))).dangerous(35).population(new ArrayList<>()).boss("Morgott").bossItem("око мора").build());
		map.put("хуй-тек", Location.builder().name("хуй-тек").pvp(true).paths(new ArrayList<>(List.of("кушетка", "старборд"))).dangerous(35).population(new ArrayList<>()).boss("Usual_god").bossItem("очко бога").build());
		map.put("старборд", Location.builder().name("старборд").pvp(true).paths(new ArrayList<>(List.of("хуй-тек", "кринжборд", "дом", "модерская"))).dangerous(25).population(new ArrayList<>()).boss("Red").bossItem("сиськи ред").build());
		map.put("кринжборд", Location.builder().name("кринжборд").pvp(true).paths(new ArrayList<>(List.of("рекламный", "старборд"))).dangerous(35).population(new ArrayList<>()).boss("Рианель").bossItem("удача рианель").build());
		map.put("респаун", Location.builder().name("респаун").pvp(false).paths(new ArrayList<>(List.of("дом"))).dangerous(0).population(new ArrayList<>()).build());
		map.put("дом", Location.builder().name("дом").pvp(false).paths(new ArrayList<>(List.of("старборд", "мейн", "дорогой-дневник"))).dangerous(0).population(new ArrayList<>()).build());
		map.put("мейн", Location.builder().name("мейн").pvp(true).paths(new ArrayList<>(List.of("деградач", "для-ботов", "дом"))).dangerous(10).population(new ArrayList<>()).boss("Labynkyr").bossItem("шарики лаба").build());
		map.put("деградач", Location.builder().name("деградач").pvp(true).paths(new ArrayList<>(List.of("мейн", "таверна"))).dangerous(25).population(new ArrayList<>()).boss("Arktulz").bossItem("вонь арктулза").build());
		map.put("качалочка", Location.builder().name("качалочка").pvp(true).paths(new ArrayList<>(List.of("дорогой-дневник", "девочковое"))).dangerous(25).population(new ArrayList<>()).boss("Ябыс").bossItem("скейт ябыса").build());
		map.put("дорогой-дневник", Location.builder().name("дорогой-дневник").pvp(true).paths(new ArrayList<>(List.of("качалочка", "дом", "чебеграм"))).dangerous(10).population(new ArrayList<>()).boss("Orson").bossItem("форточка орсона").build());
		map.put("для-ботов", Location.builder().name("для-ботов").pvp(true).paths(new ArrayList<>(List.of("мейн", "для-флуда", "english"))).dangerous(25).population(new ArrayList<>()).boss("Gordon").bossItem("месть гордона").build());
		map.put("для-флуда", Location.builder().name("для-флуда").pvp(true).paths(new ArrayList<>(List.of("политота", "для-ботов"))).dangerous(25).population(new ArrayList<>()).boss("Buzzz").bossItem("хатка база").build());
		map.put("девочковое", Location.builder().name("девочковое").pvp(true).paths(new ArrayList<>(List.of("чебеграм", "качалочка"))).dangerous(10).population(new ArrayList<>()).boss("la_brioche").bossItem("игла бувки").build());
		map.put("чебеграм", Location.builder().name("чебеграм").pvp(true).paths(new ArrayList<>(List.of("девочковое", "дорогой-дневник", "nsfw"))).dangerous(35).population(new ArrayList<>()).boss("Stalker").bossItem("калькулятор сталкера").build());
		map.put("english", Location.builder().name("english").pvp(true).paths(new ArrayList<>(List.of("nsfw-gay", "для-ботов"))).dangerous(35).population(new ArrayList<>()).boss("Crown").bossItem("язык вороны").build());
		map.put("политота", Location.builder().name("политота").pvp(true).paths(new ArrayList<>(List.of("для-флуда", "клоунская-братва"))).dangerous(50).population(new ArrayList<>()).boss("Илья").bossItem("диплом ильи").build());
		map.put("nsfw2d", Location.builder().name("nsfw2d").pvp(true).paths(new ArrayList<>(List.of("nsfw"))).dangerous(70).population(new ArrayList<>()).boss("Chegobnk").bossItem("кресло чегоба").build());
		map.put("nsfw", Location.builder().name("nsfw").pvp(true).paths(new ArrayList<>(List.of("nsfw2d", "nsfw-gay", "чебеграм"))).dangerous(50).population(new ArrayList<>()).boss("Вуъщт").bossItem("хуй вущъта").build());
		map.put("nsfw-gay", Location.builder().name("nsfw-gay").pvp(true).paths(new ArrayList<>(List.of("english", "клоунская-братва", "nsfw"))).dangerous(50).population(new ArrayList<>()).boss("Eduard").bossItem("банка эдика").build());
		map.put("клоунская-братва", Location.builder().name("клоунская-братва").pvp(true).paths(new ArrayList<>(List.of("политота", "nsfw-gay"))).dangerous(70).population(new ArrayList<>()).boss("Rover").bossItem("бицушка ровера").build());

		if (locationCache != null) {
			map.forEach((name, loc) -> {
				if (locationCache.get(name) == null) {
					locationCache.put(name, loc);
				}
			});
		}
	}

	public void move(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(5).trim().toLowerCase();
		var player = playerCache.get(event.getAuthor().getName());
		var currentLocation = locationCache.get(player.getLocation());
		if (Strings.isEmpty(message)) {
			event.getChannel().sendMessage("Для перемещения нужно указать желаемую локацию, введи \"+идти локация\" вместо локация, подставь любую из доступных: \n" + currentLocation.getPaths().toString()).submit();
			return;
		}
		if (!currentLocation.getPaths().contains(message)) {
			event.getChannel().sendMessage("Ты не можешь переместится в эту локацию, выбери что-нибудь из доступных путей: \n" + currentLocation.getPaths().toString()).submit();
			return;
		}
		Location nextLocation = locationCache.get(message.toLowerCase());
		nextLocation.getPopulation().add(player.getNickName());
		currentLocation.getPopulation().remove(player.getNickName());
		locationCache.put(nextLocation.getName(), nextLocation);
		locationCache.put(currentLocation.getName(), currentLocation);
		player.setLocation(nextLocation.getName());
		playerCache.put(player.getNickName(), player);
		event.getChannel().sendMessage("Ты успешно переместился в локацию - " + nextLocation.getName()
						+ "\nВ этой локации находятся следующие игроки: " + nextLocation.getPopulation().toString()).submit();
	}

	public void locationInfo(MessageReceivedEvent event) {
		String target = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
		if (locationCache.get(target) != null) {
			event.getChannel().sendMessage(locationCache.get(target).toString()).submit();
		} else {
			event.getChannel().sendMessage("Такой локации не существует, попробуй ещё раз или посмотри название на карте с помощью команды +карта").submit();
		}
	}

	public void map(MessageReceivedEvent event) {
		FileUpload file = FileUpload.fromData(new File("src/main/resources/map.png"), "map.png");

		MessageEmbed embed = new EmbedBuilder()
						.setDescription("Карта БЧ-РПГ")
						.setImage("attachment://map.png")
						.build();
		event.getChannel().sendMessageEmbeds(embed) // send the embed
						.addFiles(file)
						.queue();
	}

	public void movePerson(Player player, String location) {
		var loc = locationCache.get(player.getLocation());
		loc.getPopulation().remove(player.getNickName());
		var nextLoc = locationCache.get(location);
		nextLoc.getPopulation().add(player.getNickName());
		locationCache.put(nextLoc.getName(), nextLoc);
		locationCache.put(loc.getName(), loc);
		player.setLocation(location);
		playerCache.put(player.getNickName(), player);
	}
}
