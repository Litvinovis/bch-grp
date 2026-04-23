package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * Менеджер игровых событий (квестов).
 * Генерирует квесты двух типов: «Ходилка» (достичь определённой локации) и «Загадка» (дать правильный ответ).
 * Также обрабатывает случайные встречи с мобами при перемещении.
 */
public class EventsManager {

	private static Map<String, Predicate<Player>> predicateMap;
	private static List<String> startQuest;
	private static List<String> middleQuest;
	private static List<String> endQuest;
	private final Random rand = new Random();

	/**
	 * Создаёт менеджер событий и инициализирует наборы данных для генерации квестов.
	 */
	public EventsManager() {
		init();
	}

	private static void init() {
		predicateMap = new HashMap<>();
		predicateMap.put("Ходилка", player -> player.getActiveEvent().getLocationEnd().equals(player.getLocation()));
		predicateMap.put("Загадка", player -> player.getActiveEvent().getCorrectAnswer().equalsIgnoreCase(player.getAnswer()));
		predicateMap.put("Таймер", player -> player.getActiveEvent().getLocationEnd().equals(player.getLocation())
				&& Instant.now().toEpochMilli() < Instant.parse(player.getActiveEvent().getTimeEnd()).toEpochMilli());
		predicateMap.put("Путешественник", player -> player.getActiveEvent().getAttempt()
				>= Integer.parseInt(player.getActiveEvent().getCorrectAnswer()));
		predicateMap.put("Охота", player -> player.getActiveEvent().getAttempt()
				>= Integer.parseInt(player.getActiveEvent().getCorrectAnswer()));
		predicateMap.put("Везунчик", player -> player.getActiveEvent().getAttempt() >= 1);
		startQuest = List.of("Доставьте потерянную книгу знаний ", "Принесите редкий минерал ", "Отнесите закрепленное в NSFW фото ", "Передайте от Гордона письмо ", "Отнесите ключ к секретному подземелью ", "Найдите забытый тотем племени ",
						"Помогите собрать ингредиенты для зелья ", "Спасите потерявшегося ребенка из лап ", "Поймайте рыбу и отдайте ее местному повару ", "Помогите найти semen бобов ", "Предложите сыграть в пасфайндер ", "Придите на помощь ",
						"Доставьте старинную рукопись ", "Приведите бродячего торговца к ", "Передайте секретные права модератора ", "Отнесите секретное поручение Лаба к ", "Передайте этот виброклинок ", "");
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

	/**
	 * Генерирует случайное игровое событие (квест).
	 * С вероятностью 25% создаётся квест-загадка, иначе — квест-ходилка.
	 *
	 * @param locationList список доступных игровых локаций
	 * @return сгенерированное игровое событие
	 */
	public Event assignEvent(List<String> locationList) {
		return switch (rand.nextInt(6)) {
			case 0 -> createAnswerEvent();
			case 1 -> createTimerEvent(locationList);
			case 2 -> createTravelerEvent();
			case 3 -> createHuntEvent();
			case 4 -> createLuckyEvent();
			default -> createPathFinderEvent(locationList);
		};
	}

	/**
	 * Проверяет, выполнил ли игрок условие активного квеста.
	 *
	 * @param activeEvent активный квест игрока
	 * @param player      игрок, выполняющий квест
	 * @return true если условие квеста выполнено
	 */
	public boolean checkEvent(Event activeEvent, Player player) {
		if (activeEvent == null || activeEvent.getType() == null) {
			return false;
		}
		Predicate<Player> predicate = predicateMap.get(activeEvent.getType());
		return predicate != null && predicate.test(player);
	}

	/**
	 * Определяет, произошла ли случайная встреча с мобом при переходе в новую локацию.
	 * Встреча возможна только в PvP-локациях с учётом уровня опасности.
	 *
	 * @param event    событие Discord-сообщения
	 * @param location локация, в которую переходит игрок
	 * @return true если произошла встреча с мобом
	 */
	public boolean transferEvent(MessageReceivedEvent event, Location location) {
		if (location.isPvp() && rand.nextInt(100) > location.getDangerous()) {
			event.getChannel().sendMessage("Во время перемещения ты наткнулся на зомбака из руин...тебе придётся сразится с ним").submit();
			return true;
		}
		return false;
	}

	private Event createPathFinderEvent(List<String> locationList ) {
		ArrayList<String> startQuest = new ArrayList<>(locationList);
		startQuest.remove("респаун");
		String endLocation = startQuest.get(rand.nextInt(startQuest.size()));
		return Event.builder()
						.locationEnd(endLocation)
						.type("Ходилка")
						.correctAnswer(null)
						.description(createDescription(endLocation))
						.itemReward(null)
						.moneyReward(rand.nextInt(150, 250))
						.timeEnd(null)
						.xpReward(rand.nextInt(150, 250))
						.build();
	}

	private Event createAnswerEvent() {
		return Event.builder()
						.locationEnd(null)
						.type("Загадка")
						.correctAnswer("лаб")
						.description("Самый неудачный игродел в истории")
						.itemReward(null)
						.moneyReward(rand.nextInt(150, 250))
						.timeEnd(null)
						.xpReward(rand.nextInt(150, 250))
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
	
	private Event createTimerEvent(List<String> locationList) {
		ArrayList<String> locs = new ArrayList<>(locationList);
		locs.remove("респаун");
		String endLocation = locs.get(rand.nextInt(locs.size()));
		String deadline = Instant.now().plusSeconds(600).toString(); // 10 минут
		return Event.builder()
				.locationEnd(endLocation)
				.type("Таймер")
				.correctAnswer(null)
				.description("Доберись до локации " + endLocation + " за 10 минут!")
				.itemReward(null)
				.moneyReward(rand.nextInt(200, 350))
				.timeEnd(deadline)
				.xpReward(rand.nextInt(200, 350))
				.build();
	}

	private Event createTravelerEvent() {
		int required = rand.nextInt(3, 8);
		return Event.builder()
				.locationEnd(null)
				.type("Путешественник")
				.correctAnswer(String.valueOf(required))
				.description("Посети " + required + " различных локаций.")
				.itemReward(null)
				.moneyReward(rand.nextInt(180, 300))
				.timeEnd(null)
				.xpReward(rand.nextInt(180, 300))
				.attempt(0)
				.build();
	}

	private Event createHuntEvent() {
		int required = rand.nextInt(2, 6);
		return Event.builder()
				.locationEnd(null)
				.type("Охота")
				.correctAnswer(String.valueOf(required))
				.description("Победи " + required + " мобов.")
				.itemReward(null)
				.moneyReward(rand.nextInt(200, 320))
				.timeEnd(null)
				.xpReward(rand.nextInt(200, 320))
				.attempt(0)
				.build();
	}

	private Event createLuckyEvent() {
		return Event.builder()
				.locationEnd(null)
				.type("Везунчик")
				.correctAnswer(null)
				.description("Выиграй одну игру в таверне (угадай число).")
				.itemReward(null)
				.moneyReward(rand.nextInt(150, 250))
				.timeEnd(null)
				.xpReward(rand.nextInt(150, 250))
				.attempt(0)
				.build();
	}

	private String createDescription(String location) {
		return startQuest.get(rand.nextInt(startQuest.size()))
						+ middleQuest.get(rand.nextInt(middleQuest.size()))
						+ "в " + location
						+ endQuest.get(rand.nextInt(endQuest.size()));
	}
}
