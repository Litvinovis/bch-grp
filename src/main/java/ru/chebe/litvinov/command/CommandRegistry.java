package ru.chebe.litvinov.command;

import ru.chebe.litvinov.service.interfaces.IIdeasManager;
import ru.chebe.litvinov.service.interfaces.ILocationManager;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;
import ru.chebe.litvinov.service.ItemsManager;

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
                                        String helpMessage,
                                        String infoMessage) {
        CommandRegistry registry = new CommandRegistry();

        // --- Регистрация ---
        registry.register("+регистрация", playersManager::createPlayer);

        // --- Справка / инфо (не требуют авторизации) ---
        registry.register("+помощь", event -> event.getChannel().sendMessage(helpMessage).submit());
        registry.register("+инфо", event -> event.getChannel().sendMessage(infoMessage).submit());

        // --- Статистика и передвижение ---
        registry.register("+стата", playersManager::getPlayerInfo);
        registry.register("+идти", playersManager::move);
        registry.register("+локация", event -> locationManager.locationInfo(event, null));
        registry.register("+карта", locationManager::map);

        // --- Инвентарь и предметы ---
        registry.register("+инвентарь", playersManager::getInventoryInfo);
        registry.register("+предмет", itemsManager::getItemInfo);
        registry.register("+купить", playersManager::buyItem);
        registry.register("+использовать", playersManager::useItem);
        registry.register("+продать", playersManager::sellItem);

        // --- Таверна ---
        registry.register("+кости", playersManager::dieCast);
        registry.register("+рулетка", playersManager::playRoulette);
        registry.register("+кнб", playersManager::rockPaperScissors);
        registry.register("+число", playersManager::guessTheNumber);

        // --- Квесты ---
        registry.register("+взять квест", playersManager::assignEvent);
        registry.register("+выполнить квест", playersManager::checkEvent);
        registry.register("+поменять квест", playersManager::changeEvent);

        // --- Бой ---
        registry.register("+убить босса", playersManager::bossFight);
        registry.register("+пвп", playersManager::playersFight);
        registry.register("+нпс", playersManager::fightNpc);

        // --- Бонус ---
        registry.register("+бонус", playersManager::dailyBonus);

        // --- Дневные квесты ---
        registry.register("+дневные", playersManager::showDailyQuests);

        // --- Таблица лидеров ---
        registry.register("+топ", playersManager::topLeaderboard);

        // --- Классы персонажей ---
        registry.register("+класс", playersManager::chooseClass);

        // --- Достижения ---
        registry.register("+достижения", playersManager::getAchievements);

        // --- Торговля (длинный префикс перед +принять) ---
        registry.register("+передать", playersManager::tradeItem);

        // --- Дуэли (+принять и +отказать до более коротких совпадений) ---
        registry.register("+вызов", playersManager::challengeDuel);
        registry.register("+принять", playersManager::acceptDuel);
        registry.register("+отказать", playersManager::declineDuel);

        // --- Клановые (длинные префиксы раньше коротких) ---
        registry.register("+новый клан", playersManager::clanRegister);
        registry.register("+покинуть клан", playersManager::clanLeave);
        registry.register("+вступить в клан", playersManager::clanJoin);
        registry.register("+принять заявки", playersManager::acceptApply);
        registry.register("+отклонить заявки", playersManager::rejectApply);
        registry.register("+клан инфо", playersManager::clanInfo);

        // --- Идеи (обычные) ---
        registry.register("+идея", ideasManager::putIdea);

        // --- Администраторские ---
        registry.registerAdmin("+всеидеи", ideasManager::getAllIdeas);
        registry.registerAdmin("+новыеидеи", ideasManager::getNewIdeas);
        registry.registerAdmin("+идеяномер", ideasManager::getIdea);
        registry.registerAdmin("+идеястатус", ideasManager::changeIdeaStatus);

        return registry;
    }
}
