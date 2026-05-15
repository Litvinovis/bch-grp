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
        registry.register("+продать ресурс", playersManager::sellResource);
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
        registry.register("+убить нпс", playersManager::clanNpcFight);
        registry.register("+убить босса", playersManager::bossFight);
        registry.register("+пвп", playersManager::playersFight);
        registry.register("+нпс", playersManager::fightNpc);

        // --- Бонус ---
        registry.register("+бонус", playersManager::dailyBonus);

        // --- Дневные квесты ---
        registry.register("+дневные", playersManager::showDailyQuests);

        // --- Таблица лидеров (длинные префиксы раньше) ---
        registry.register("+топ кланы", playersManager::clanLeaderboard);
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
        registry.register("+клан банк", playersManager::clanBankCommand);
        registry.register("+клан положить", playersManager::clanBankCommand);
        registry.register("+клан снять", playersManager::clanBankCommand);
        registry.register("+клан улучшения", playersManager::clanUpgradesCommand);
        registry.register("+клан купить", playersManager::clanUpgradesCommand);
        registry.register("+клан база", playersManager::setClanBase);
        registry.register("+клан повысить", playersManager::promoteClanMember);
        registry.register("+клан выгнать", playersManager::kickClanMember);

        // --- Новые боевые ---
        registry.register("+последний бой", playersManager::lastBattleLog);

        // --- Локации ---
        registry.register("+путь", playersManager::locationPath);
        registry.register("+исследовать", playersManager::exploreLocation);
        registry.register("+домой", playersManager::goHome);

        // --- Инвентарь ---
        registry.register("+банк", playersManager::bankCommand);
        registry.register("+улучшить", playersManager::upgradeItem);
        registry.register("+сравнить", playersManager::compareItems);
        registry.register("+крафт", playersManager::craftItem);
        registry.register("+торговец", playersManager::merchantShop);

        // --- Квесты ---
        registry.register("+квесты", playersManager::questJournal);

        // --- Рейтинги ---
        registry.register("+сезон", playersManager::seasonLeaderboard);

        // --- Экономика ---
        registry.register("+кредит", playersManager::takeCredit);
        registry.register("+погасить", playersManager::repayCredit);
        registry.register("+покер", playersManager::playPoker);
        registry.register("+скачки", playersManager::horseRacingInfo);
        registry.register("+поставить", playersManager::betOnHorse);
        registry.register("+биржа", playersManager::exchangeInfo);

        // --- Прогрессия ---
        registry.register("+престиж", playersManager::prestige);

        // --- Социальные ---
        registry.register("+профиль", playersManager::playerProfile);
        registry.register("+зал", playersManager::hallOfFame);

        // --- Идеи (обычные) ---
        registry.register("+идея", ideasManager::putIdea);

        // --- Администраторские ---
        registry.registerAdmin("+всеидеи", ideasManager::getAllIdeas);
        registry.registerAdmin("+новыеидеи", ideasManager::getNewIdeas);
        registry.registerAdmin("+идеяномер", ideasManager::getIdea);
        registry.registerAdmin("+идеястатус", ideasManager::changeIdeaStatus);
        registry.registerAdmin("+admin reload", playersManager::adminReload);
        registry.registerAdmin("+выдать награду активности", playersManager::giveActivityReward);

        // --- Онлайн ---
        registry.register("+онлайн", playersManager::onlineCommand);

        // --- Питомцы (item 101-108) ---
        registry.register("+питомец", playersManager::petCommand);
        registry.register("+кормить", playersManager::feedPet);
        registry.register("+гонки маунтов старт", playersManager::mountRacingRun);
        registry.register("+гонки маунтов", playersManager::mountRacingInfo);

        // --- Профессии (item 109-116) ---
        registry.register("+профессия", playersManager::professionCommand);
        registry.register("+добыть", playersManager::gatherResource);
        registry.register("+рецепты", playersManager::showProfessionRecipes);
        registry.register("+биржа ресурсов", playersManager::resourceMarket);

        // --- Территории (item 117-123) ---
        registry.register("+захватить", playersManager::captureTerritory);
        registry.register("+осада", playersManager::siegeCommand);
        registry.register("+крепость", playersManager::fortressCommand);
        registry.register("+карта кланов", playersManager::territoryClanMap);
        registry.register("+альянс", playersManager::declareAlliance);

        // --- Мировые события (item 124-130) ---
        registry.register("+мировой босс", playersManager::worldBossAttack);
        registry.register("+нашествие", playersManager::invasionStatus);
        registry.register("+кризис статус", playersManager::crisisStatus);
        registry.register("+турнир сервера", playersManager::serverTournament);

        // --- Классы и навыки (item 131-137) ---
        registry.register("+скиллы", playersManager::showSkills);
        registry.register("+вложить", playersManager::investSkill);
        registry.register("+умение", playersManager::useAbility);
        registry.register("+второй класс", playersManager::chooseSecondClass);

        // --- Социальные механики (item 138-144) ---
        registry.register("+фракции", playersManager::showFactions);
        registry.register("+дневник", playersManager::diaryCommand);
        registry.register("+топ активность", playersManager::topActivity);
        registry.register("+лор", playersManager::lorePage);
        registry.register("+доска", playersManager::weeklyBoard);
        registry.register("+бонт", playersManager::placeBounty);
        registry.register("+бонты", playersManager::getBounties);

        // --- Арена и соревнования (item 145-150) ---
        registry.register("+арена 3v3", playersManager::teamArena);
        registry.register("+арена топ", playersManager::arenaLeaderboard);
        registry.register("+арена", playersManager::arenaChallenge);
        registry.register("+турнир статус", playersManager::tournamentStatus);
        registry.register("+турнир", playersManager::registerTournament);
        registry.register("+выживание", playersManager::survivalChallenge);
        registry.register("+вызвать чемпиона", playersManager::challengeChampion);
        registry.register("+чемпион", playersManager::showChampion);
        registry.register("+лига", playersManager::showLeague);

        return registry;
    }
}
