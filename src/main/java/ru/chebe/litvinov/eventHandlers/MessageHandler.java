package ru.chebe.litvinov.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.ignite.client.IgniteClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.Constants;
import ru.chebe.litvinov.command.*;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.*;
import ru.chebe.litvinov.raid.RaidCommand;
import ru.chebe.litvinov.raid.RaidJoinCommand;
import ru.chebe.litvinov.raid.RaidManager;
import ru.chebe.litvinov.service.*;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Обработчик Discord-сообщений.
 * Принимает входящие сообщения, маршрутизирует их на соответствующие команды,
 * обрабатывает проверку регистрации и прав доступа (администраторские команды).
 * Использует пул потоков для параллельной обработки сообщений.
 */
@Slf4j
public class MessageHandler extends ListenerAdapter {

	private static final int NUM_THREADS = 4;
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
	private final IgniteHealthChecker healthChecker;
	private final CommandRegistry commandRegistry;

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
			NUM_THREADS, NUM_THREADS, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

	/**
	 * Создаёт обработчик сообщений, инициализируя все игровые сервисы через Ignite 3 репозитории.
	 *
	 * @param igniteClient      подключённый Ignite 3 thin client
	 * @param allowedChannelIds список идентификаторов Discord-каналов, в которых работает бот
	 * @param adminIds          список идентификаторов Discord-пользователей с правами администратора
	 */
	public MessageHandler(IgniteClient igniteClient, java.util.List<String> allowedChannelIds, java.util.List<String> adminIds) {
		this.allowedChannelIds = new HashSet<>(allowedChannelIds == null ? java.util.List.of() : allowedChannelIds);
		this.adminIds = new HashSet<>(adminIds == null ? java.util.List.of() : adminIds);

		// Инициализация схемы Ignite 3
		new SchemaInitializer(igniteClient).init();

		this.playerRepository = new PlayerRepository(igniteClient);
		this.locationManager = new LocationManager(new LocationRepository(igniteClient));
		this.itemsManager = new ItemsManager(new ItemRepository(igniteClient));
		this.ideasManager = new IdeasManager(new IdeaRepository(igniteClient));
		ClanManager clanManager = new ClanManager(new ClanRepository(igniteClient), playerRepository);
		BattleManager battleManager = new BattleManager(new BossRepository(igniteClient));

		this.playersManager = new PlayersManager(playerRepository, locationManager, itemsManager,
				battleManager, new EventsManager(), clanManager, new Tavern());

		this.raidManager = new RaidManager(battleManager, playersManager, this.allowedChannelIds);

		this.healthChecker = new IgniteHealthChecker(igniteClient);
		this.healthChecker.start();

		this.commandRegistry = CommandRegistry.build(
				playersManager, ideasManager, locationManager, itemsManager,
				raidManager, HELP_MESSAGE, INFO_MESSAGE);

		// Регистрируем дополнительные команды, которым нужен healthChecker
		commandRegistry.register("+статус", new StatusCommand(healthChecker));
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		executor.submit(() -> chooseAction(event));
	}

	private void chooseAction(MessageReceivedEvent event) {
		try {
			if (!isBotAsking(event)) return;

			String content = event.getMessage().getContentDisplay();

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
			if (content.startsWith("+помощь") || content.startsWith("+инфо")) {
				Optional<Command> cmd = commandRegistry.resolve(content);
				cmd.ifPresent(c -> c.execute(event));
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

						Вспомогательные:
						+идея (текст) - добавить идею/предложение/багрепорт
						+инфо - общая информация об игре
						+помощь - эта справка
						+статус - состояние Ignite-кэшей (база данных)
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
