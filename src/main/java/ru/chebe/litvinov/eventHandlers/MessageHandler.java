package ru.chebe.litvinov.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.Constants;
import ru.chebe.litvinov.service.*;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MessageHandler extends ListenerAdapter {
	private static final int NUM_THREADS = 8; // Количество потоков в пуле
	private static final String HELP_MESSAGE = helpMessageCreate();
	private static final String INFO_MESSAGE = infoMessageCreate();
	private static IgniteCache<String, Player> playerCache;
	private final Logger logger = LoggerFactory.getLogger("default-logger");
	private final ItemsManager itemsManager;
	private final PlayersManager playersManager;
	private final LocationManager locationManager;
	private final IdeasManager ideasManager;
	private final BattleManager battleManager;
	private final Tavern tavern;
	private final EventsManager eventsManager;
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());;

	public MessageHandler(Ignite ignite) {
		playerCache = ignite.cache("players");
		IgniteCache<String, Boss> bossCache = ignite.cache("bosses");
		IgniteCache<Integer, Idea> ideasCache = ignite.cache("ideas");
		IgniteCache<String, Location> locationCache = ignite.cache("locations");
		IgniteCache<String, Item> itemsCache = ignite.cache("items");
		this.itemsManager = new ItemsManager(playerCache, itemsCache);
		this.locationManager = new LocationManager(locationCache, playerCache);
		this.playersManager = new PlayersManager(playerCache, locationManager, itemsCache);
		this.tavern = new Tavern(playerCache);
		this.ideasManager = new IdeasManager(ideasCache);
		this.battleManager = new BattleManager(locationCache, playerCache, bossCache, playersManager);
		this.eventsManager = new EventsManager(locationCache, playerCache, locationManager, playersManager, battleManager);
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		executor.submit(() -> chooseAction(event));
	}

	private void chooseAction(MessageReceivedEvent event) {
		try {
			if (isBotAsking(event)) {
				String content = event.getMessage().getContentDisplay();
				if (content.startsWith("+регистрация")) {
					playersManager.createPlayer(event);
				} else if (playerCache.get(event.getMessage().getAuthor().getId()) == null) {
					event.getChannel().sendMessage(Constants.NEED_REGISTRATION)
									.submit();
				} else if (content.startsWith("+стата")) {
					playersManager.getPlayerInfo(event);
				} else if (content.startsWith("+кости")) {
					tavern.dieCast(event);
				} else if (content.startsWith("+помощь")) {
					event.getChannel().sendMessage(HELP_MESSAGE).submit();
				} else if (content.startsWith("+локация")) {
					locationManager.locationInfo(event);
				} else if (content.startsWith("+карта")) {
					locationManager.map(event);
				} else if (content.startsWith("+идти")) {
					if (locationManager.move(event)) {
						eventsManager.transferEvent(event);
					}
				} else if (content.startsWith("+инвентарь")) {
					playersManager.getInventoryInfo(event);
				} else if (content.startsWith("+предмет")) {
					itemsManager.getItemInfo(event);
				} else if (content.startsWith("+инфо")) {
					event.getChannel().sendMessage(INFO_MESSAGE).submit();
				} else if (content.startsWith("+убить босса")) {
					battleManager.bossFight(event);
				} else if (content.startsWith("+пвп")) {
					battleManager.playersFight(event);
				} else if (content.startsWith("+взять квест")) {
					eventsManager.assignEvent(event);
				} else if (content.startsWith("+выполнить квест")) {
					eventsManager.checkEvent(event);
				} else if (content.startsWith("+купить")) {
					itemsManager.buyItem(event);
				} else if (content.startsWith("+использовать")) {
					playersManager.useItem(event);
				} else if (content.startsWith("+продать")) {
					playersManager.sellItem(event);


					// Админские команды
				} else if (content.startsWith("+всеидеи") && isAdmin(event)) {
					ideasManager.getAllIdeas(event);
				} else if (content.startsWith("+новыеидеи") && isAdmin(event)) {
					ideasManager.getNewIdeas(event);
				} else if (content.startsWith("+идеяномер") && isAdmin(event)) {
					ideasManager.getIdea(event);
				} else if (content.startsWith("+идеястатус") && isAdmin(event)) {
					ideasManager.changeIdeaStatus(event);


					// Обычные запросы которые должны быть ниже
				} else if (content.startsWith("+идея")) {
					ideasManager.putIdea(event);
				} else {
					event.getChannel().sendMessage(Constants.UNKNOWN_COMMAND).submit();
				}
			}
		} catch (Exception e) {
			logger.error(Constants.MESSAGE_PROCESS_FAILED, e.getMessage());
		}
	}

	private boolean isBotAsking(MessageReceivedEvent event) {
		return event.getMessage().getContentDisplay().startsWith("+") && (event.getChannel().getType().toString().equals("PRIVATE") || event.getChannel().asTextChannel().getName().equals("бч-грп"));
	}

	private boolean isAdmin(MessageReceivedEvent event) {
		if (!event.getMessage().getAuthor().getId().equals("253576055317594114")) {
			event.getChannel().sendMessage(Constants.ACCESS_DENIED).submit();
			return false;
		} else {
			return true;
		}
	}

	private static String helpMessageCreate() {
		return """
						Для игры доступны следующие команды:
						+стата - выводит всю информацию о вашем игровом персонаже
						+кости (ставка) - игра в кидание кубиков, доступна только в локации таверна
						+помощь - информация о доступных командах
						+идти (название локации) - перемещает вас в указанную локацию
						+локация (название локации) - подробная информация об указанной локации
						+карта - карта бч-грп
						+убить босса - атаковать босса текущей локации, осторожно они жирные ||как ябыс||
						+пвп - атаковать случайного игрока текущей локации, работает только в пвп зонах
						+инвентарь - список ваших предметов без подробного описания
						+инвентарь (название предмета) - подробная информация о предмете в вашем инвентаре
						+взять квест - получить квест
						+выполнить квест (ответ если требуется) - выполнить квест
						+предмет (название предмета) - подробная информация о любом предмете в игре
						+инфо - общая информация об игре и создателях
						+идея (текст) - добавить идею, предложение, замечание и багрепорт.
						+купить (название предмета) - купить предмет в магазине
						+использовать (название предмета) - использовать предмет из вашего инвентаря
						+продать (название предмета) - продать предмет из вашего инвентаря
						""";
	}

	private static String infoMessageCreate() {
		return """
						БЧ-ГРП - онлайн ММА PVP MIX FIGHT UFC COOP
						RPG ShitGame, созданная по мотивам легендарной
						(хотя ещё и не вышедшей) ЧБ-РПГ. В ней вы
						можете грабить ~~корованы~~ чебешников,
						победить Boss of the gym, овладеть хуем вущъта,
						фармить мобов гордона и даже приобрести цикорий
						холодец.
						
						В игре полная свобода действий и перемещений
						||пушта мне влом придумывать сюжет||
						реалистичная боевая система и серверные ивенты
						(когда нибудь будут).
						Погрузитесь в великолепный мир Чатика безумия,
						создайте свой отряд для рейда на босса,
						заработайте 100 лвл и нагибайте ньюфагов.

						                         СОЗДАТЕЛИ:
						 Исполнительный директор             Ровер
						 Руководитель разработки               Ровер
						 Джун-макака                                Ровер
						 Арт-директор                               Чегоб
						 Лидер тестирования                      Чегоб
						 Директор по маркетингу                 Фражуз
						""";
	}
}
