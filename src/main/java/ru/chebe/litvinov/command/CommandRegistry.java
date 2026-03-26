package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;
import ru.chebe.litvinov.service.interfaces.ILocationManager;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;
import ru.chebe.litvinov.service.ItemsManager;
import ru.chebe.litvinov.raid.RaidCommand;
import ru.chebe.litvinov.raid.RaidJoinCommand;
import ru.chebe.litvinov.raid.RaidManager;

import java.util.*;

/**
 * Реестр команд — маппит строковые префиксы на реализации {@link Command}.
 * Команды проверяются в порядке регистрации (более длинные префиксы должны идти раньше).
 */
public class CommandRegistry {

    /** Пара (prefix, command) — порядок важен. */
    private final List<Map.Entry<String, Command>> entries = new ArrayList<>();

    /** Выделенный список команд, требующих прав администратора. */
    private final Map<String, Command> adminCommands = new LinkedHashMap<>();

    /**
     * Зарегистрировать обычную команду.
     *
     * @param prefix  текстовый префикс (например "+убить босса")
     * @param command реализация команды
     */
    public void register(String prefix, Command command) {
        entries.add(Map.entry(prefix, command));
    }

    /**
     * Зарегистрировать команду только для администраторов.
     *
     * @param prefix  текстовый префикс
     * @param command реализация команды
     */
    public void registerAdmin(String prefix, Command command) {
        adminCommands.put(prefix, command);
        entries.add(Map.entry(prefix, command));
    }

    /**
     * Найти команду по тексту сообщения.
     *
     * @param content содержимое сообщения
     * @return найденная команда или {@link Optional#empty()}
     */
    public Optional<Command> resolve(String content) {
        for (Map.Entry<String, Command> e : entries) {
            if (content.startsWith(e.getKey())) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Проверяет, является ли текст сообщения администраторской командой.
     *
     * @param content содержимое сообщения
     * @return true если сообщение начинается с префикса администраторской команды
     */
    public boolean isAdminCommand(String content) {
        for (String prefix : adminCommands.keySet()) {
            if (content.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Создаёт стандартный реестр со всеми игровыми командами.
     *
     * @param playersManager  менеджер игроков
     * @param ideasManager    менеджер идей
     * @param locationManager менеджер локаций
     * @param itemsManager    менеджер предметов
     * @param raidManager     менеджер рейдов
     * @param helpMessage     текст справочного сообщения для команды +помощь
     * @param infoMessage     текст информационного сообщения для команды +инфо
     * @return настроенный реестр команд
     */
    public static CommandRegistry build(IPlayersManager playersManager,
                                        IIdeasManager ideasManager,
                                        ILocationManager locationManager,
                                        ItemsManager itemsManager,
                                        RaidManager raidManager,
                                        String helpMessage,
                                        String infoMessage) {
        CommandRegistry registry = new CommandRegistry();

        // --- Регистрация ---
        registry.register("+регистрация", new RegistrationCommand(playersManager));

        // --- Справка / инфо (не требуют авторизации) ---
        registry.register("+помощь", event ->
                event.getChannel().sendMessage(helpMessage).submit());
        registry.register("+инфо", event ->
                event.getChannel().sendMessage(infoMessage).submit());

        // --- Статистика и передвижение ---
        registry.register("+стата", new StatsCommand(playersManager));
        registry.register("+идти", new MoveCommand(playersManager));
        registry.register("+локация", event ->
                locationManager.locationInfo(event,
                        null /* будет определено внутри LocationManager */));
        registry.register("+карта", event -> locationManager.map(event));

        // --- Инвентарь и предметы ---
        registry.register("+инвентарь", new InventoryCommand(playersManager));
        registry.register("+предмет", event -> itemsManager.getItemInfo(event));
        registry.register("+купить", new BuyItemCommand(playersManager));
        registry.register("+использовать", new UseItemCommand(playersManager));
        registry.register("+продать", new SellItemCommand(playersManager));

        // --- Таверна ---
        registry.register("+кости", new DieCastCommand(playersManager));
        registry.register("+рулетка", new RouletteCommand(playersManager));
        registry.register("+кнб", new RockPaperScissorsCommand(playersManager));
        registry.register("+число", new GuessNumberCommand(playersManager));

        // --- Квесты ---
        registry.register("+взять квест", new QuestAssignCommand(playersManager));
        registry.register("+выполнить квест", new QuestCheckCommand(playersManager));
        registry.register("+поменять квест", new QuestChangeCommand(playersManager));

        // --- Бой ---
        registry.register("+убить босса", new AttackCommand(playersManager));
        registry.register("+пвп", new PvpCommand(playersManager));

        // --- Рейды ---
        registry.register("+рейд", new RaidCommand(raidManager));
        registry.register("+присоединиться", new RaidJoinCommand(raidManager));

        // --- Бонус ---
        registry.register("+бонус", new DailyBonusCommand(playersManager));

        // --- Клановые (длинные префиксы раньше коротких) ---
        registry.register("+новый клан", new ClanRegisterCommand(playersManager));
        registry.register("+покинуть клан", new ClanLeaveCommand(playersManager));
        registry.register("+вступить в клан", new ClanJoinCommand(playersManager));
        registry.register("+принять заявки", new ClanAcceptCommand(playersManager));
        registry.register("+отклонить заявки", new ClanRejectCommand(playersManager));
        registry.register("+клан инфо", new ClanInfoCommand(playersManager));

        // --- Идеи (обычные) ---
        registry.register("+идея", new IdeaCommand(ideasManager));

        // --- Администраторские ---
        registry.registerAdmin("+всеидеи", new AdminIdeasAllCommand(ideasManager));
        registry.registerAdmin("+новыеидеи", new AdminIdeasNewCommand(ideasManager));
        registry.registerAdmin("+идеяномер", new AdminIdeaGetCommand(ideasManager));
        registry.registerAdmin("+идеястатус", new AdminIdeaStatusCommand(ideasManager));

        return registry;
    }
}
