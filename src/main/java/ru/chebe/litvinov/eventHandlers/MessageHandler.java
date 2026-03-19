package ru.chebe.litvinov.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.Constants;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.service.*;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MessageHandler extends ListenerAdapter {
	private static final int NUM_THREADS = 4; // Количество потоков в пуле
	private static final String HELP_MESSAGE = helpMessageCreate();
	private static final String INFO_MESSAGE = infoMessageCreate();
	private static final String OPENCLAW_ROVER_BOT_ID = "1481319611533365483";
	private static IgniteCache<String, Player> playerCache;
	private final java.util.Set<String> allowedChannelIds;
	private final java.util.Set<String> adminIds;
	private final Logger logger = LoggerFactory.getLogger("adminLog");
	private final ItemsManager itemsManager;
	private final PlayersManager playersManager;
	private final LocationManager locationManager;
	private final IdeasManager ideasManager;
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

	public MessageHandler(Ignite ignite, java.util.List<String> allowedChannelIds, java.util.List<String> adminIds) {
		this.allowedChannelIds = new java.util.HashSet<>(allowedChannelIds == null ? java.util.List.of() : allowedChannelIds);
		this.adminIds = new java.util.HashSet<>(adminIds == null ? java.util.List.of() : adminIds);
		playerCache = ignite.getOrCreateCache("players");
		this.locationManager = new LocationManager(ignite.getOrCreateCache("locations"));
		this.itemsManager = new ItemsManager(ignite.getOrCreateCache("items"));
		this.ideasManager = new IdeasManager(ignite.getOrCreateCache("ideas"));
		ClanManager clanManager = new ClanManager(ignite.getOrCreateCache("clans"), playerCache);
		this.playersManager = new PlayersManager(playerCache, locationManager, itemsManager,
						new BattleManager(ignite.getOrCreateCache("bosses")), new EventsManager(), clanManager, new Tavern());
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		executor.submit(() -> chooseAction(event));
	}

	private void chooseAction(MessageReceivedEvent event) {
		try {
			boolean asking = isBotAsking(event);
			if (OPENCLAW_ROVER_BOT_ID.equals(event.getAuthor().getId())) {
				logger.info("OpenClaw message observed: channelId={}, content='{}', asking={}",
						event.getChannel().getId(), event.getMessage().getContentDisplay(), asking);
			}
			if (asking) {
				String content = event.getMessage().getContentDisplay();
				if (content.startsWith("+регистрация")) {
					playersManager.createPlayer(event);
				} else if (content.startsWith("+помощь")) {
					event.getChannel().sendMessage(HELP_MESSAGE).submit();
				} else if (playerCache == null) {
					event.getChannel().sendMessage("Сервис базы данных временно недоступен, попробуйте через минуту.").submit();
				} else if (playerCache.get(event.getMessage().getAuthor().getId()) == null) {
					event.getChannel().sendMessage(Constants.NEED_REGISTRATION)
									.submit();
				} else if (content.startsWith("+стата")) {
					playersManager.getPlayerInfo(event);
				} else if (content.startsWith("+кости")) {
					playersManager.dieCast(event);
				} else if (content.startsWith("+рулетка")) {
					playersManager.playRoulette(event);
				} else if (content.startsWith("+кнб")) {
					playersManager.rockPaperScissors(event);
				} else if (content.startsWith("+число")) {
					playersManager.guessTheNumber(event);
				} else if (content.startsWith("+локация")) {
					locationManager.locationInfo(event, playerCache.get(event.getMessage().getAuthor().getId()).getLocation());
				} else if (content.startsWith("+карта")) {
					locationManager.map(event);
				} else if (content.startsWith("+идти")) {
					playersManager.move(event);
				} else if (content.startsWith("+инвентарь")) {
					playersManager.getInventoryInfo(event);
				} else if (content.startsWith("+предмет")) {
					itemsManager.getItemInfo(event);
				} else if (content.startsWith("+инфо")) {
					event.getChannel().sendMessage(INFO_MESSAGE).submit();
				} else if (content.startsWith("+убить босса")) {
					playersManager.bossFight(event);
				} else if (content.startsWith("+пвп")) {
					playersManager.playersFight(event);
				} else if (content.startsWith("+взять квест")) {
					playersManager.assignEvent(event);
				} else if (content.startsWith("+выполнить квест")) {
					playersManager.checkEvent(event);
				} else if (content.startsWith("+поменять квест")) {
					playersManager.changeEvent(event);
				} else if (content.startsWith("+купить")) {
					playersManager.buyItem(event);
				} else if (content.startsWith("+использовать")) {
					playersManager.useItem(event);
				} else if (content.startsWith("+продать")) {
					playersManager.sellItem(event);
				} else if (content.startsWith("+бонус")) {
					playersManager.dailyBonus(event);

					// Клановые команды
				} else if (content.startsWith("+новый клан")) {
					playersManager.clanRegister(event);
				} else if (content.startsWith("+покинуть клан")) {
					playersManager.clanLeave(event);
				} else if (content.startsWith("+вступить в клан")) {
					playersManager.clanJoin(event);
				} else if (content.startsWith("+принять заявки")) {
					playersManager.acceptApply(event);
				} else if (content.startsWith("+отклонить заявки")) {
					playersManager.rejectApply(event);
				} else if (content.startsWith("+клан инфо")) {
					playersManager.clanInfo(event);


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
		String content = event.getMessage().getContentDisplay();
		if (content == null || !content.startsWith("+")) {
			return false;
		}

		// Игнорируем других ботов, кроме доверенного OpenClawRover для автопроверок
		if (event.getAuthor().isBot() && !OPENCLAW_ROVER_BOT_ID.equals(event.getAuthor().getId())) {
			return false;
		}

		if (event.getChannel().getType().toString().equals("PRIVATE")) {
			return true;
		}

		// Разрешаем команды в guild только в каналах из конфигурации (application.yml)
		if (event.isFromGuild() && allowedChannelIds.contains(event.getChannel().getId())) {
			return true;
		}

		return false;
	}

	private boolean isAdmin(MessageReceivedEvent event) {
		if (!adminIds.contains(event.getMessage().getAuthor().getId())) {
			event.getChannel().sendMessage(Constants.ACCESS_DENIED).submit();
			return false;
		} else {
			return true;
		}
	}

	private static String helpMessageCreate() {
		return """
						Для игры доступны следующие команды:
						
						Главные команды:
						+стата - выводит всю информацию о вашем игровом персонаже
						+идти (название локации) - перемещает вас в указанную локацию
						+локация (моя / название локации) - подробная информация об указанной или текущей локации
						+карта - карта бч-грп
						
						Предметы:
						+инвентарь - список ваших предметов без подробного описания
						+инвентарь (название предмета) - подробная информация о предмете в вашем инвентаре
						+предмет (название предмета) - подробная информация о любом предмете в игре
						+купить (название предмета) - купить предмет в магазине
						+использовать (название предмета) - использовать предмет из вашего инвентаря
						+продать (название предмета) - продать предмет из вашего инвентаря
						
						Игры в таверне:
						+кости (ставка) - игра в кидание кубиков (ставка от 1 до 100)
						+рулетка (ставка) (ставка на цвет/число) - игра в рулетку
						  Доступные ставки: красный/черный (x2), число 0-36 (x35)
						  Пример: +рулетка 50 красный
						+кнб (ставка) (камень/ножницы/бумага) - игра "камень-ножницы-бумага"
						  Пример: +кнб 30 камень
						+число (ставка) (число от 1 до 10) - угадай число от 1 до 10 (x5)
						  Пример: +число 20 7
						
						Активности:
						+взять квест - получить квест
						+выполнить квест (ответ если требуется) - выполнить квест
						+поменять квест - изменить квест за 5 денег
						+убить босса - атаковать босса текущей локации
						+пвп - атаковать случайного игрока текущей локации (только в PVP зонах)
						+бонус - получить ежедневный бонус
						
						Клановые:
						+новый клан (название клана) - создать новый клан
						+покинуть клан - покинуть клан
						+вступить в клан (название клана) - вступить в клан
						+принять заявки - принять все заявки в клан
						+отклонить заявки - отклонить все заявки в клан
						+клан инфо (название клана) - информация о клане
						
						Вспомогательные:
						+идея (текст) - добавить идею/предложение/багрепорт
						+инфо - общая информация об игре
						+помощь - эта справка
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
