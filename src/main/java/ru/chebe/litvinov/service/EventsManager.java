package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.util.*;
import java.util.function.Predicate;

public class EventsManager {

	private final IgniteCache<String, Location> locationCache;
	private final IgniteCache<String, Player> playerCache;
	private static Map<String, Predicate<Player>> predicateMap;
	private static List<String> startQuest;
	private static List<String> middleQuest;
	private static List<String> endQuest;
	private final Random rand = new Random();
	LocationManager locationManager;
	PlayersManager playersManager;
	BattleManager battleManager;

	public EventsManager(IgniteCache<String, Location> locationCache, IgniteCache<String, Player> playerCache,
	                     LocationManager locationManager, PlayersManager playersManager, BattleManager battleManager) {
		this.playerCache = playerCache;
		this.locationCache = locationCache;
		this.locationManager = locationManager;
		this.playersManager = playersManager;
		this.battleManager = battleManager;
		init();
	}

	private static void init() {
		predicateMap = new HashMap<>();
		predicateMap.put("Ходилка", player -> player.getActiveEvent().getLocationEnd().equals(player.getLocation()));
		predicateMap.put("Загадка", player -> player.getActiveEvent().getCorrectAnswer().equalsIgnoreCase(player.getAnswer()));
		startQuest = List.of("Доставьте потерянную книгу знаний ", "Принесите редкий минерал ", "Заберите волшебный меч у ", "Передайте письмо от ", "Отнесите ключ к секретному подземелью ", "Найдите забытый тотем племени ",
						"Помогите собрать ингредиенты для зелья ", "Спасите потерявшегося ребенка из лап ", "Поймайте рыбу и отдайте ее местному повару ", "Найдите семена бобов для ", "Спасите деревню от нашествия ", "Придите на помощь ",
						"Доставьте старинную рукопись ", "Приведите бродячего торговца к ", "Передайте секретные права модератора ", "Отнесите секретное поручение Лаба к ", "Передайте этот виброклинок ");
		middleQuest = List.of("Вущьту ", "Лабу ", "Баззу ", "Чегобнику ", "Орсону ", "Роверу ", "Ябысу ", "Илье ", "Таои ", "Рэд ", "Дарху ", "Обычному богу ", "Ушасу ", "Бобору ", "Стину ", "Сталкеру ", "Рианель ", "Потату ", "Фражузу ",
						"Фаерфлей ", "Бувке ", "Кел ", "Гордону ", "Лове ", "Багплею ", "Эдтку ", "Жокернику ", "Вестеру ", "Боенгу ", "Авику ", "Осовец ", "Агрипине ", "Нееретику ", "Механику ", "Ситцу ");
		endQuest = List.of(" чтобы он смог создать новое мощное оружие", " чтобы он мог защитить свой дом от врагов", " чтобы она могла исцелить тяжело раненного друга", " чтобы они могли спасти королевство от темного властелина",
						" чтобы мир узнал правду о таинственном исчезновении принцессы", " чтобы освободить плененных героев из заточения", " чтобы восстановить утраченные знания древних магов", " чтобы вернуть надежду жителям умирающего города",
						" чтобы снять проклятие с замка, которое держало его в вечном сне", " чтобы добыть рецепт волшебного зелья, способного оживлять мертвых", " чтобы собрать материалы для строительства нового храма", " чтобы победить демона, захватившего власть над миром",
						" чтобы узнать секреты прошлого и предотвратить катастрофу будущего", " чтобы доказать свою храбрость и заслужить уважение товарищей", " чтобы найти способ воскресить погибшего друга", " чтобы научиться управлять стихиями и стать настоящим мастером магии",
						" чтобы заручиться поддержкой союзников и подготовиться к битве с армией врага", " чтобы понять смысл своей жизни и найти свое истинное предназначение", " чтобы избежать зловещего пророчества и изменить судьбу мира", " чтобы защитить лес от вырубки и спасти животных, обитающих там",
						" чтобы встретить любовь всей своей жизни и создать семью", " чтобы победить еретиков из руин", " чтобы чегоб перестал ловить в свой подвал малолеток", " чтобы перестать кринжевать", " чтобы покакать", " чтобы убить злого модератора ЧБ",
						" чтобы освободить лаба из под каблука", " чтобы бухнуть с Базом", " чтобы Илья смог стать инагентом", " чтобы в мире была любовь и пасфайндер", " чтобы открыть форточку после прихода Орсона");

	}

	public void assignEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getActiveEvent() != null) {
			event.getChannel().sendMessage("У тебя уже есть активный квест, сначала заверши его").submit();
		} else {
			player.setActiveEvent(rand.nextInt(4) == 1 ? createAnswerEvent() : createPathFinderEvent());
			playerCache.put(player.getId(), player);
			event.getChannel().sendMessage("Ты получил новое задание :\n" + player.getActiveEvent().toString()).submit();
		}
	}

	public void changeEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getActiveEvent() == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
		} else if (player.getMoney() >= 5) {
			player.setActiveEvent(rand.nextInt(4) == 1 ? createAnswerEvent() : createPathFinderEvent());
			player.setMoney(player.getMoney() - 5);
			playerCache.put(player.getId(), player);
			event.getChannel().sendMessage("Ты потартил 5 денег и получил новое задание :\n" + player.getActiveEvent().toString()).submit();
		} else {
			event.getChannel().sendMessage("У тебя недостаточно денег, сначала зарабаотай их").submit();
		}
	}

	public void checkEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		String message = event.getMessage().getContentDisplay().substring(16).trim().toLowerCase();
		player.setAnswer(message);
		var activeEvent = player.getActiveEvent();
		if (activeEvent == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
		} else if (predicateMap.get(activeEvent.getType()).test(player)) {
			player.setActiveEvent(null);
			playerCache.put(player.getId(), player);
			playersManager.changeMoney(player.getId(), activeEvent.getMoneyReward(), true);
			playersManager.changeXp(player.getId(), activeEvent.getXpReward());
			event.getChannel().sendMessage("Ты успешно завершил свой квест, опыт " + activeEvent.getXpReward() + " и деньги " + activeEvent.getMoneyReward() + " зачислены на твой счёт").submit();
		} else {
			event.getChannel().sendMessage("Ты не выполнил условия квеста или ответил неправильно!").submit();
		}
	}

	public void transferEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		var activeEvent = player.getActiveEvent();
		if (activeEvent == null && locationCache.get(player.getLocation()).isPvp() && rand.nextInt(100) > locationCache.get(player.getLocation()).getDangerous()) {
			event.getChannel().sendMessage("Во время перемещения ты наткнулся на зомбака из руин...тебе придётся сразится с ним").submit();
			battleManager.mobFight(event, player);
		}
	}

	private Event createPathFinderEvent() {
		String endLocation = locationManager.getLocationList().get(rand.nextInt(locationManager.getLocationList().size() - 1));
		return Event.builder()
						.locationEnd(endLocation)
						.type("Ходилка")
						.correctAnswer(null)
						.description(createDescription(endLocation))
						.itemReward(null)
						.moneyReward(rand.nextInt(50, 100))
						.timeEnd(null)
						.xpReward(rand.nextInt(50, 100))
						.build();
	}

	private Event createAnswerEvent() {
		return Event.builder()
						.locationEnd(null)
						.type("Загадка")
						.correctAnswer("лаб")
						.description("Самый неудачный игродел в истории")
						.itemReward(null)
						.moneyReward(rand.nextInt(50, 100))
						.timeEnd(null)
						.xpReward(rand.nextInt(50, 100))
						.build();
	}

	private Event createBattle() {
		return Event.builder()
						.locationEnd(null)
						.type("Битва")
						.correctAnswer(null)
						.description("Тебе встретился противник...битва неизбежна...приготовься")
						.itemReward(null)
						.moneyReward(rand.nextInt(50, 100))
						.timeEnd(null)
						.xpReward(rand.nextInt(50, 100))
						.build();
	}
	
	private String createDescription(String location) {
		return startQuest.get(rand.nextInt(startQuest.size()))
						+ middleQuest.get(rand.nextInt(middleQuest.size()))
						+ "в " + location
						+ endQuest.get(rand.nextInt(middleQuest.size()));
	}
}
