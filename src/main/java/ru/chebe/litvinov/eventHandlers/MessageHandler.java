package ru.chebe.litvinov.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.Constants;
import ru.chebe.litvinov.command.*;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.*;
import ru.chebe.litvinov.raid.RaidManager;
import ru.chebe.litvinov.repository.DailyQuestRepository;
import ru.chebe.litvinov.service.*;
import ru.chebe.litvinov.util.MetricsService;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Обработчик Discord-сообщений.
 * Принимает входящие сообщения, маршрутизирует их на соответствующие команды,
 * обрабатывает проверку регистрации и прав доступа (администраторские команды).
 * Использует пул потоков для параллельной обработки сообщений.
 */
@Slf4j
public class MessageHandler extends ListenerAdapter {

	private static final String HELP_MESSAGE = helpMessageCreate();
	private static final String INFO_MESSAGE = infoMessageCreate();
	private static final String OPENCLAW_ROVER_BOT_ID = "1481319611533365483";

	private final Set<String> allowedChannelIds;
	private final Set<String> adminIds;
	private final Logger logger = LoggerFactory.getLogger("adminLog");

	private final PlayerRepository playerRepository;
	private final LocationManager locationManager;
	private final ItemsManager itemsManager;
	private final PlayersManager playersManager;
	private final IdeasManager ideasManager;
	private final RaidManager raidManager;
	private final CommandRegistry commandRegistry;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public MessageHandler(DataSource dataSource, java.util.List<String> allowedChannelIds, java.util.List<String> adminIds) {
		this.allowedChannelIds = new HashSet<>(allowedChannelIds == null ? java.util.List.of() : allowedChannelIds);
		this.adminIds = new HashSet<>(adminIds == null ? java.util.List.of() : adminIds);

		new SchemaInitializer(dataSource).init();

		this.playerRepository = new PlayerRepository(dataSource);
		this.locationManager = new LocationManager(new LocationRepository(dataSource));
		this.itemsManager = new ItemsManager(new ItemRepository(dataSource));
		this.ideasManager = new IdeasManager(new IdeaRepository(dataSource));
		ClanManager clanManager = new ClanManager(new ClanRepository(dataSource), playerRepository);
		BattleManager battleManager = new BattleManager(new BossRepository(dataSource));

		NpcManager npcManager = new NpcManager();
		this.playersManager = new PlayersManager(playerRepository, locationManager, itemsManager,
				battleManager, new EventsManager(), clanManager, new Tavern(), npcManager);

		DailyQuestRepository dailyQuestRepository = new DailyQuestRepository(dataSource);
		DailyQuestService dailyQuestService = new DailyQuestService(dailyQuestRepository, this.playersManager);
		this.playersManager.setDailyQuestService(dailyQuestService);

		// New managers (items 85-150)
		PetManager petManager = new PetManager(playerRepository);
		ProfessionManager professionManager = new ProfessionManager(playerRepository);
		TerritoryRepository territoryRepository = new TerritoryRepository(dataSource);
		TerritoryManager territoryManager = new TerritoryManager(territoryRepository, playerRepository, clanManager, locationManager);
		WorldEventManager worldEventManager = new WorldEventManager(playerRepository);
		worldEventManager.setAllowedChannelIds(this.allowedChannelIds);
		FactionManager factionManager = new FactionManager(playerRepository);
		BountyRepository bountyRepository = new BountyRepository(dataSource);
		BountyManager bountyManager = new BountyManager(bountyRepository, playerRepository);
		ArenaManager arenaManager = new ArenaManager(playerRepository, battleManager);
		TournamentRepository tournamentRepository = new TournamentRepository(dataSource);
		TournamentManager tournamentManager = new TournamentManager(tournamentRepository, playerRepository, battleManager);

		battleManager.setPetManager(petManager);
		this.playersManager.setPetManager(petManager);
		this.playersManager.setProfessionManager(professionManager);
		this.playersManager.setTerritoryManager(territoryManager);
		this.playersManager.setWorldEventManager(worldEventManager);
		this.playersManager.setFactionManager(factionManager);
		this.playersManager.setBountyManager(bountyManager);
		this.playersManager.setArenaManager(arenaManager);
		this.playersManager.setTournamentManager(tournamentManager);
		this.playersManager.setAllowedChannelIds(this.allowedChannelIds);

		this.raidManager = new RaidManager(battleManager, playersManager, this.allowedChannelIds);

		new DataIntegrityService(playerRepository, locationManager).checkAndFix();

		this.commandRegistry = CommandRegistry.build(
				playersManager, ideasManager, locationManager, itemsManager,
				HELP_MESSAGE, INFO_MESSAGE);

		commandRegistry.register("+статус", new StatusCommand(dataSource));
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		executor.submit(() -> chooseAction(event));
	}

	private void chooseAction(MessageReceivedEvent event) {
		try {
			String content = event.getMessage().getContentDisplay();

			// Check hidden quests for any non-bot message in allowed channel (no + prefix required)
			if (!event.getAuthor().isBot() && (content == null || !content.startsWith("+"))) {
				if (event.isFromGuild() && allowedChannelIds.contains(event.getChannel().getId())) {
					Player hqPlayer = playerRepository.get(event.getAuthor().getId());
					if (hqPlayer != null) playersManager.checkHiddenQuest(event);
				}
				return;
			}

			if (!isBotAsking(event)) return;

			// OpenClaw Rover логируем отдельно
			if (OPENCLAW_ROVER_BOT_ID.equals(event.getAuthor().getId())) {
				logger.info("OpenClaw message observed: channelId={}, content='{}'",
						event.getChannel().getId(), content);
			}

			// +регистрация и +помощь — без проверки регистрации
			if (content.startsWith("+регистрация")) {
				playersManager.createPlayer(event);
				return;
			}
			if (content.startsWith("+помощь")) {
				sendLongMessage(event, HELP_MESSAGE);
				return;
			}
			if (content.startsWith("+инфо")) {
				sendLongMessage(event, INFO_MESSAGE);
				return;
			}

			// Проверка доступности Ignite
			if (playerRepository == null) {
				event.getChannel().sendMessage("Сервис базы данных временно недоступен, попробуйте через минуту.").submit();
				return;
			}

			// Проверка регистрации
			Player player = playerRepository.get(event.getMessage().getAuthor().getId());
			if (player == null) {
				event.getChannel().sendMessage(Constants.NEED_REGISTRATION).submit();
				return;
			}

			// Для +локация нужен текущий location игрока
			if (content.startsWith("+локация")) {
				locationManager.locationInfo(event, player.getLocation());
				return;
			}

			// Рейдовые команды — требуют передачи Player
			if (content.startsWith("+рейд")) {
				raidManager.createRaid(player, event.getChannel());
				return;
			}
			if (content.startsWith("+присоединиться")) {
				raidManager.joinRaid(player, event.getChannel());
				return;
			}

			// Проверяем, является ли команда администраторской
			if (commandRegistry.isAdminCommand(content) && !isAdmin(event)) {
				return; // isAdmin уже отправил сообщение об отказе
			}

			// Поиск в реестре
			Optional<Command> cmd = commandRegistry.resolve(content);
			if (cmd.isPresent()) {
				cmd.get().execute(event);
			} else {
				event.getChannel().sendMessage(Constants.UNKNOWN_COMMAND).submit();
			}

		} catch (Exception e) {
			logger.error(Constants.MESSAGE_PROCESS_FAILED, e.getMessage());
		}
	}

	private static void sendLongMessage(MessageReceivedEvent event, String text) {
		int limit = 1900;
		if (text.length() <= limit) {
			event.getChannel().sendMessage(text).submit();
			return;
		}
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(start + limit, text.length());
			// откатываемся к ближайшему переносу строки, чтобы не рвать слова
			if (end < text.length()) {
				int nl = text.lastIndexOf('\n', end);
				if (nl > start) end = nl + 1;
			}
			event.getChannel().sendMessage(text.substring(start, end)).submit();
			start = end;
		}
	}

	private boolean isBotAsking(MessageReceivedEvent event) {
		String content = event.getMessage().getContentDisplay();
		if (content == null || !content.startsWith("+")) {
			return false;
		}
		if (event.getAuthor().isBot() && !OPENCLAW_ROVER_BOT_ID.equals(event.getAuthor().getId())) {
			return false;
		}
		if (event.getChannel().getType().toString().equals("PRIVATE")) {
			return true;
		}
		return event.isFromGuild() && allowedChannelIds.contains(event.getChannel().getId());
	}

	private boolean isAdmin(MessageReceivedEvent event) {
		if (!adminIds.contains(event.getMessage().getAuthor().getId())) {
			event.getChannel().sendMessage(Constants.ACCESS_DENIED).submit();
			return false;
		}
		return true;
	}

	private static String helpMessageCreate() {
		return """
						Для игры доступны следующие команды:

						Главные команды:
						+регистрация - зарегистрироваться в игре (первый шаг!)
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
						+бонус - получить ежедневный бонус (стрик: 3 дня = +50 монет, 7 дней = редкий предмет)
						+дневные - показать ежедневные квесты (3 квеста в день, бонус за все: +300 XP, +200 монет)

						Классы и прогресс:
						+класс (воин/разбойник/маг) - выбрать класс с 5 уровня (один раз)
						+достижения - показать ваши достижения
						+топ [уровень/деньги/репутация] - таблица лидеров top-10

						Дуэли:
						+вызов @игрок - вызвать игрока на дуэль
						+принять - принять вызов на дуэль
						+отказать - отказаться от дуэли

						Торговля:
						+передать @игрок предмет [количество] - передать предмет другому игроку

						Рейды:
						+рейд - начать рейд на общего босса (только в определённых каналах)
						+присоединиться - вступить в активный рейд

						Клановые:
						+новый клан (название клана) - создать новый клан
						+покинуть клан - покинуть клан
						+вступить в клан (название клана) - вступить в клан
						+принять заявки - принять все заявки в клан
						+отклонить заявки - отклонить все заявки в клан
						+клан инфо (название клана) - информация о клане

						Питомцы и маунты:
						+питомец - информация о питомце (первый раз — получить кота)
						+кормить (предмет) - покормить питомца
						+гонки маунтов - информация о маунтах
						+гонки маунтов старт - участвовать в гонке маунтов

						Профессии:
						+профессия инфо - просмотр профессий
						+профессия выбрать (кузнец/алхимик/повар/ювелир) - выбрать профессию
						+добыть - добыть ресурс в текущей локации (кд 30 мин)
						+создать (рецепт) - создать предмет по рецепту
						+рецепты - список рецептов профессии
						+биржа ресурсов - цены на ресурсы
						+продать ресурс (ресурс) (кол-во) - продать ресурс

						Территории и кланы+:
						+захватить (локация) - захватить территорию (нужно 3+ членов клана)
						+осада (клан) - начать осаду клана
						+осада статус - статус осад клана
						+крепость строить - построить крепость клана (1000 монет)
						+крепость улучшить (кузня/таверна/башня) - улучшить крепость
						+карта кланов - карта захваченных территорий
						+альянс (клан) - заключить альянс с другим кланом

						Мировые события:
						+мировой босс - атаковать мирового босса
						+нашествие - статус нашествия
						+кризис статус - статус экономического кризиса
						+сезон - текущий сезонный предмет

						Навыки и классы+:
						+скиллы - показать навыки персонажа
						+вложить (навык) - вложить очко в навык
						+умение (название) - использовать активное умение
						+второй класс (класс) - выбрать второй класс с 50 уровня (паладин/некромант/следопыт)

						Фракции и дневник:
						+фракции - репутация у фракций (ТОРГОВЦЫ/МАГИ/ВОИНЫ)
						+дневник - записи дневника приключений
						+топ активности - топ-10 самых активных игроков

						Охота и награды:
						+бонт @игрок (сумма) - поставить награду за голову
						+бонты - список активных наград за голову
						+лор (страница) - страницы лора мира

						Арена и турниры:
						+арена - бой на арене с ELO-рейтингом
						+арена топ - топ-10 по ELO
						+арена 3v3 - командный бой 3 на 3
						+выживание - режим выживания
						+чемпион - дневной чемпион арены
						+вызвать чемпиона - вызвать чемпиона на бой
						+лига - показать свою лигу (бронза/серебро/золото/платина)
						+турнир - зарегистрироваться в турнире (старт при 8 игроках)
						+турнир статус - статус текущего турнира
						+турнир сервера - информация о серверном турнире

						Онлайн и достижения:
						+онлайн - кто был активен за последние 24 часа
						+еженедельные - еженедельная доска активности

						Вспомогательные:
						+идея (текст) - добавить идею/предложение/багрепорт
						+инфо - общая информация об игре
						+помощь - эта справка
						+статус - состояние базы данных
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
