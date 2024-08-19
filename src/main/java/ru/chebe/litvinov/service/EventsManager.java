package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

public class EventsManager {

	private final IgniteCache<String, Location> locationCache;
	private final IgniteCache<String, Player> playerCache;
	private static Map<String, Predicate<Player>> predicateMap;
	private final Random rand = new Random();
	LocationManager locationManager;
	PlayersManager playersManager;

	public EventsManager(IgniteCache<String, Location> locationCache, IgniteCache<String, Player> playerCache, LocationManager locationManager, PlayersManager playersManager) {
		this.playerCache = playerCache;
		this.locationCache = locationCache;
		this.locationManager = locationManager;
		this.playersManager = playersManager;
		init();
	}

	private static void init() {
		predicateMap = new HashMap<>();
		predicateMap.put("Ходилка", player -> player.getActiveEvent().getLocationEnd().equals(player.getLocation()));
		predicateMap.put("Загадка", player -> player.getActiveEvent().getCorrectAnswer().equalsIgnoreCase(player.getAnswer()));
	}

	public void assignEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getName());
		if (player.getActiveEvent() != null) {
			event.getChannel().sendMessage("У тебя уже есть активный квест, сначала заверши его").submit();
		} else {
			player.setActiveEvent(rand.nextInt(2) == 1 ? createPathFinderEvent() : createAnswerEvent());
			playerCache.put(player.getNickName(), player);
			event.getChannel().sendMessage("Ты получил новое задание :\n" + player.getActiveEvent().toString()).submit();
		}
	}

	public void checkEvent(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getName());
		var activeEvent = player.getActiveEvent();
		if (activeEvent == null) {
			event.getChannel().sendMessage("У тебя нет активного квеста, сначала возьми его").submit();
		} else if (predicateMap.get(activeEvent.getType()).test(player)) {
			event.getChannel().sendMessage("Ты успешно завершил свой квест, опыт и деньги зачислены на твой счёт").submit();
			player.setActiveEvent(null);
			playerCache.put(player.getNickName(), player);
			playersManager.changeMoney(player.getNickName(), activeEvent.getMoneyReward(), true);
			playersManager.changeXp(player.getNickName(), activeEvent.getXpReward());
		} else {
			event.getChannel().sendMessage("Ты не выполнил условия квеста или ответил неправильно!").submit();
		}
	}

	private Event createPathFinderEvent() {
		String endLocation = locationManager.getLocationList().get(rand.nextInt(locationManager.getLocationList().size() - 1));
		return Event.builder()
						.locationEnd(endLocation)
						.type("Ходилка")
						.correctAnswer(null)
						.description("Достигни нужной локации путник")
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
}
