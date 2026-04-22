package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.LocationRepository;

import java.io.File;
import java.util.*;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Менеджер игровых локаций.
 * Инициализирует карту мира в Ignite-кэше, обрабатывает перемещение игроков между локациями
 * и предоставляет информацию о локациях.
 */
public class LocationManager implements ru.chebe.litvinov.service.interfaces.ILocationManager {

	private final LocationRepository locationCache;
	public static final List<String> locationList = new ArrayList<>(50);

	/**
	 * Создаёт менеджер локаций и инициализирует карту мира в репозитории.
	 *
	 * @param locationCache репозиторий Ignite 3 для хранения локаций
	 */
	public LocationManager(LocationRepository locationCache) {
		this.locationCache = locationCache;
		init(locationCache);
	}

	/**
	 * Инициализирует репозиторий локаций полным набором игровых локаций.
	 * Локации добавляются только если ещё не существуют в репозитории.
	 *
	 * @param locationCache репозиторий Ignite 3 для инициализации
	 */
	public static void init(LocationRepository locationCache) {
		Map<String, Location> map = new HashMap<>();
		map.put("загадка", Location.builder().name("загадка").pvp(true).paths(new ArrayList<>(List.of("кушетка"))).dangerous(70).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).populationById(new ArrayList<>()).boss("cynic mansion").bossItem("кисточка циника").teleport(false).build());
		map.put("олимп", Location.builder().name("олимп").pvp(true).paths(new ArrayList<>(List.of("модерская"))).dangerous(80).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Darhalas").bossItem("корона дарха").teleport(false).build());
		map.put("магазин", Location.builder().name("магазин").pvp(false).paths(new ArrayList<>(List.of("рекламный"))).dangerous(0).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).teleport(false).build());
		map.put("таверна", Location.builder().name("таверна").pvp(false).paths(new ArrayList<>(List.of("деградач"))).dangerous(10).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).teleport(false).build());
		map.put("кушетка", Location.builder().name("кушетка").pvp(true).paths(new ArrayList<>(List.of("загадка", "модерская", "хуй-тек"))).dangerous(50).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Ctin").bossItem("кринж стина").teleport(false).build());
		map.put("модерская", Location.builder().name("модерская").pvp(true).paths(new ArrayList<>(List.of("олимп", "кушетка", "старборд"))).dangerous(50).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Ushas").bossItem("попка ушаса").teleport(true).build());
		map.put("рекламный", Location.builder().name("рекламный").pvp(true).paths(new ArrayList<>(List.of("кринжборд", "магазин"))).dangerous(35).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Morgott").bossItem("око мора").teleport(false).build());
		map.put("хуй-тек", Location.builder().name("хуй-тек").pvp(true).paths(new ArrayList<>(List.of("кушетка", "старборд"))).dangerous(35).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Usual_god").bossItem("очко бога").teleport(false).build());
		map.put("старборд", Location.builder().name("старборд").pvp(true).paths(new ArrayList<>(List.of("хуй-тек", "кринжборд", "дом", "модерская"))).dangerous(25).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Red").bossItem("сиськи ред").teleport(false).build());
		map.put("кринжборд", Location.builder().name("кринжборд").pvp(true).paths(new ArrayList<>(List.of("рекламный", "старборд"))).dangerous(35).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Рианель").bossItem("удача рианель").teleport(true).build());
		map.put("респаун", Location.builder().name("респаун").pvp(false).paths(new ArrayList<>(List.of("дом"))).dangerous(0).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).teleport(false).build());
		map.put("дом", Location.builder().name("дом").pvp(false).paths(new ArrayList<>(List.of("старборд", "мейн", "дорогой-дневник"))).dangerous(0).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).teleport(false).build());
		map.put("мейн", Location.builder().name("мейн").pvp(true).paths(new ArrayList<>(List.of("деградач", "для-ботов", "дом"))).dangerous(10).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Labynkyr").bossItem("шарики лаба").teleport(true).build());
		map.put("деградач", Location.builder().name("деградач").pvp(true).paths(new ArrayList<>(List.of("мейн", "таверна"))).dangerous(25).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Arktulz").bossItem("вонь арктулза").teleport(false).build());
		map.put("качалочка", Location.builder().name("качалочка").pvp(true).paths(new ArrayList<>(List.of("дорогой-дневник", "девочковое"))).dangerous(25).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Ябыс").bossItem("скейт ябыса").teleport(false).build());
		map.put("дорогой-дневник", Location.builder().name("дорогой-дневник").pvp(true).paths(new ArrayList<>(List.of("качалочка", "дом", "чебеграм"))).dangerous(10).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Orson").bossItem("форточка орсона").teleport(false).build());
		map.put("для-ботов", Location.builder().name("для-ботов").pvp(true).paths(new ArrayList<>(List.of("мейн", "для-флуда", "english"))).dangerous(25).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Gordon").bossItem("месть гордона").teleport(false).build());
		map.put("для-флуда", Location.builder().name("для-флуда").pvp(true).paths(new ArrayList<>(List.of("политота", "для-ботов"))).dangerous(25).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Buzzz").bossItem("хатка база").teleport(false).build());
		map.put("девочковое", Location.builder().name("девочковое").pvp(true).paths(new ArrayList<>(List.of("чебеграм", "качалочка"))).dangerous(10).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("la_brioche").bossItem("игла бувки").teleport(false).build());
		map.put("чебеграм", Location.builder().name("чебеграм").pvp(true).paths(new ArrayList<>(List.of("девочковое", "дорогой-дневник", "nsfw"))).dangerous(35).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Stalker").bossItem("калькулятор сталкера").teleport(true).build());
		map.put("english", Location.builder().name("english").pvp(true).paths(new ArrayList<>(List.of("nsfw-gay", "для-ботов"))).dangerous(35).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Crown").bossItem("язык вороны").teleport(false).build());
		map.put("политота", Location.builder().name("политота").pvp(true).paths(new ArrayList<>(List.of("для-флуда", "клоунская-братва"))).dangerous(50).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Илья").bossItem("диплом ильи").teleport(true).build());
		map.put("nsfw2d", Location.builder().name("nsfw2d").pvp(true).paths(new ArrayList<>(List.of("nsfw"))).dangerous(70).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Chegobnk").bossItem("кресло чегоба").teleport(false).build());
		map.put("nsfw", Location.builder().name("nsfw").pvp(true).paths(new ArrayList<>(List.of("nsfw2d", "nsfw-gay", "чебеграм"))).dangerous(50).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Вуъщт").bossItem("хуй вущъта").teleport(false).build());
		map.put("nsfw-gay", Location.builder().name("nsfw-gay").pvp(true).paths(new ArrayList<>(List.of("english", "клоунская-братва", "nsfw"))).dangerous(50).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Eduard").bossItem("банка эдика").teleport(false).build());
		map.put("клоунская-братва", Location.builder().name("клоунская-братва").pvp(true).paths(new ArrayList<>(List.of("политота", "nsfw-gay"))).dangerous(70).populationByName(new ArrayList<>()).populationById(new ArrayList<>()).boss("Rover").bossItem("бицушка ровера").teleport(false).build());

		if (locationCache != null) {
			map.forEach((name, loc) -> {
				if (!locationCache.contains(name)) {
					locationCache.put(name, loc);
				}
			});
		}

		locationList.addAll(map.keySet());
	}

	/**
	 * Перемещает игрока между локациями, обновляя списки населения обеих локаций в кэше.
	 *
	 * @param player       игрок, который перемещается
	 * @param nextLocation название целевой локации
	 * @return обновлённый объект целевой локации
	 */
	public Location movePlayerInPopulation(Player player, String nextLocation) {
		Location current = locationCache.get(player.getLocation());
		Location next = locationCache.get(nextLocation);
		next.getPopulationByName().add(player.getNickName());
		next.getPopulationById().add(player.getId());
		current.getPopulationByName().remove(player.getNickName());
		current.getPopulationById().remove(player.getId());
		locationCache.put(next.getName(), next);
		locationCache.put(current.getName(), current);
		return next;
	}

	/**
	 * Отправляет игроку информацию о запрошенной или текущей локации.
	 *
	 * @param event           событие Discord-сообщения
	 * @param currentLocation текущая локация игрока (используется при запросе «моя»)
	 */
	public void locationInfo(MessageReceivedEvent event, String currentLocation) {
		String target = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
		if (target.equals("моя")) {
			event.getChannel().sendMessage(locationCache.get(currentLocation).toString()).submit();
		} else if (locationCache.get(target) != null) {
			event.getChannel().sendMessage(locationCache.get(target).toString()).submit();
		} else {
			event.getChannel().sendMessage("Такой локации не существует, попробуй ещё раз или посмотри название на карте с помощью команды +карта").submit();
		}
	}

	/**
	 * Отправляет изображение карты игрового мира в Discord-канал.
	 *
	 * @param event событие Discord-сообщения
	 */
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

	/**
	 * BFS: находит следующую локацию на пути из from в to.
	 * Возвращает null если путь не найден.
	 */
	public String findNextStep(String from, String to) {
		if (from.equals(to)) return to;
		Map<String, String> parent = new HashMap<>();
		Queue<String> queue = new LinkedList<>();
		queue.add(from);
		parent.put(from, null);
		while (!queue.isEmpty()) {
			String current = queue.poll();
			Location loc = locationCache.get(current);
			if (loc == null) continue;
			for (String neighbor : loc.getPaths()) {
				if (!parent.containsKey(neighbor)) {
					parent.put(neighbor, current);
					if (neighbor.equals(to)) {
						String step = to;
						while (!from.equals(parent.get(step))) {
							step = parent.get(step);
						}
						return step;
					}
					queue.add(neighbor);
				}
			}
		}
		return null;
	}

	/**
	 * Возвращает список всех названий локаций в игре.
	 *
	 * @return список названий локаций
	 */
	public List<String> getLocationList() {
		return locationList;
	}

	/**
	 * Возвращает локацию по её названию.
	 *
	 * @param location название локации
	 * @return объект Location или null если локация не найдена
	 */
	public Location getLocation(String location) {
		return locationCache.get(location);
	}

	/**
	 * Сохраняет обновлённую локацию в репозиторий.
	 *
	 * @param location объект локации с изменёнными данными
	 */
	public void saveLocation(Location location) {
		locationCache.put(location.getName(), location);
	}
}
