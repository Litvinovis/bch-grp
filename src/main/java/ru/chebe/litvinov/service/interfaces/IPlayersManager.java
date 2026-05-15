package ru.chebe.litvinov.service.interfaces;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;

/**
 * Интерфейс для сервиса управления игроками.
 */
public interface IPlayersManager {

    /**
     * Регистрирует нового игрового персонажа.
     *
     * @param event событие Discord-сообщения от регистрирующегося пользователя
     */
    void createPlayer(MessageReceivedEvent event);

    /**
     * Отправляет игроку его текущие характеристики.
     *
     * @param event событие Discord-сообщения
     */
    void getPlayerInfo(MessageReceivedEvent event);

    /**
     * Отправляет информацию об инвентаре или конкретном предмете.
     *
     * @param event событие Discord-сообщения
     */
    void getInventoryInfo(MessageReceivedEvent event);

    /**
     * Перемещает игрока в указанную локацию.
     *
     * @param event событие Discord-сообщения с названием целевой локации
     */
    void move(MessageReceivedEvent event);

    /**
     * Инициирует бой с боссом текущей локации.
     *
     * @param event событие Discord-сообщения
     */
    void bossFight(MessageReceivedEvent event);

    /**
     * Инициирует PvP-бой со случайным игроком в текущей локации.
     *
     * @param event событие Discord-сообщения
     */
    void playersFight(MessageReceivedEvent event);

    /**
     * Назначает игроку новый случайный квест.
     *
     * @param event событие Discord-сообщения
     */
    void assignEvent(MessageReceivedEvent event);

    /**
     * Проверяет выполнение условия активного квеста.
     *
     * @param event событие Discord-сообщения с ответом игрока
     */
    void checkEvent(MessageReceivedEvent event);

    /**
     * Заменяет текущий квест игрока на новый за 5 монет.
     *
     * @param event событие Discord-сообщения
     */
    void changeEvent(MessageReceivedEvent event);

    /**
     * Обрабатывает покупку предмета игроком.
     *
     * @param event событие Discord-сообщения с названием предмета
     */
    void buyItem(MessageReceivedEvent event);

    /**
     * Применяет активируемый предмет из инвентаря игрока.
     *
     * @param event событие Discord-сообщения с названием предмета
     */
    void useItem(MessageReceivedEvent event);

    /**
     * Продаёт предмет из инвентаря игрока.
     *
     * @param event событие Discord-сообщения с названием продаваемого предмета
     */
    void sellItem(MessageReceivedEvent event);

    /**
     * Начисляет игроку ежедневный бонус (100 монет).
     *
     * @param event событие Discord-сообщения
     */
    void dailyBonus(MessageReceivedEvent event);

    /**
     * Проводит игру в кости в таверне.
     *
     * @param event событие Discord-сообщения со ставкой
     */
    void dieCast(MessageReceivedEvent event);

    /**
     * Проводит игру в рулетку в таверне.
     *
     * @param event событие Discord-сообщения со ставкой и выбором
     */
    void playRoulette(MessageReceivedEvent event);

    /**
     * Проводит игру «камень-ножницы-бумага» в таверне.
     *
     * @param event событие Discord-сообщения со ставкой и выбором
     */
    void rockPaperScissors(MessageReceivedEvent event);

    /**
     * Проводит игру «угадай число» в таверне.
     *
     * @param event событие Discord-сообщения со ставкой и числом
     */
    void guessTheNumber(MessageReceivedEvent event);

    /**
     * Атакует случайного NPC в текущей локации игрока.
     */
    void fightNpc(MessageReceivedEvent event);

    /**
     * Обрабатывает создание нового клана.
     *
     * @param event событие Discord-сообщения с названием клана
     */
    void clanRegister(MessageReceivedEvent event);

    /**
     * Обрабатывает выход игрока из клана.
     *
     * @param event событие Discord-сообщения
     */
    void clanLeave(MessageReceivedEvent event);

    /**
     * Обрабатывает подачу заявки на вступление в клан.
     *
     * @param event событие Discord-сообщения с названием клана
     */
    void clanJoin(MessageReceivedEvent event);

    /**
     * Принимает все заявки на вступление в клан (только для лидера).
     *
     * @param event событие Discord-сообщения
     */
    void acceptApply(MessageReceivedEvent event);

    /**
     * Отклоняет все заявки на вступление в клан (только для лидера).
     *
     * @param event событие Discord-сообщения
     */
    void rejectApply(MessageReceivedEvent event);

    /**
     * Отправляет информацию о клане по его названию.
     *
     * @param event событие Discord-сообщения с названием клана
     */
    void clanInfo(MessageReceivedEvent event);

    /**
     * Возвращает актуального игрока из кэша по его ID.
     *
     * @param id идентификатор игрока
     * @return игрок или null если не найден
     */
    Player getPlayer(String id);

    /**
     * Обрабатывает смерть игрока: списывает 10% монет, восстанавливает HP и перемещает на Респаун.
     *
     * @param player игрок, который погиб
     */
    void deathOfPlayer(Player player);

    /**
     * Изменяет количество денег игрока.
     *
     * @param id       идентификатор игрока
     * @param money    сумма изменения
     * @param increase true — добавить деньги, false — вычесть
     * @return актуальное количество денег после изменения
     */
    int changeMoney(String id, int money, boolean increase);

    /**
     * Начисляет опыт игроку, при необходимости повышает уровень.
     *
     * @param id идентификатор игрока
     * @param xp количество начисляемого опыта
     */
    void changeXp(String id, int xp);

    /**
     * Изменяет HP игрока на указанное значение.
     *
     * @param id       идентификатор игрока
     * @param hp       величина изменения HP
     * @param increase true — увеличить HP, false — уменьшить
     * @return актуальное значение HP после изменения
     */
    int changeHp(String id, int hp, boolean increase);

    /** Таблица лидеров top-10. Синтаксис: +топ [уровень|деньги|репутация] */
    void topLeaderboard(MessageReceivedEvent event);

    /** Выбор класса персонажа (с 5 уровня, один раз). Синтаксис: +класс [воин|разбойник|маг] */
    void chooseClass(MessageReceivedEvent event);

    /** Показывает достижения игрока. */
    void getAchievements(MessageReceivedEvent event);

    /** Передаёт предмет другому игроку. Синтаксис: +передать @игрок предмет [количество] */
    void tradeItem(MessageReceivedEvent event);

    /** Вызов игрока на дуэль. Синтаксис: +вызов @игрок */
    void challengeDuel(MessageReceivedEvent event);

    /** Принять вызов на дуэль. */
    void acceptDuel(MessageReceivedEvent event);

    /** Отказаться от дуэли. */
    void declineDuel(MessageReceivedEvent event);

    /** Показывает ежедневные квесты игрока. */
    void showDailyQuests(MessageReceivedEvent event);

    void lastBattleLog(MessageReceivedEvent event);
    void clanNpcFight(MessageReceivedEvent event);
    void locationPath(MessageReceivedEvent event);
    void exploreLocation(MessageReceivedEvent event);
    void goHome(MessageReceivedEvent event);
    void bankCommand(MessageReceivedEvent event);
    void upgradeItem(MessageReceivedEvent event);
    void compareItems(MessageReceivedEvent event);
    void craftItem(MessageReceivedEvent event);
    void merchantShop(MessageReceivedEvent event);
    void questJournal(MessageReceivedEvent event);
    void takeCredit(MessageReceivedEvent event);
    void repayCredit(MessageReceivedEvent event);
    void playPoker(MessageReceivedEvent event);
    void horseRacingInfo(MessageReceivedEvent event);
    void betOnHorse(MessageReceivedEvent event);
    void exchangeInfo(MessageReceivedEvent event);
    void sellResource(MessageReceivedEvent event);
    void clanLeaderboard(MessageReceivedEvent event);
    void clanBankCommand(MessageReceivedEvent event);
    void clanUpgradesCommand(MessageReceivedEvent event);
    void setClanBase(MessageReceivedEvent event);
    void clanWar(MessageReceivedEvent event);
    void promoteClanMember(MessageReceivedEvent event);
    void kickClanMember(MessageReceivedEvent event);
    void seasonLeaderboard(MessageReceivedEvent event);
    void prestige(MessageReceivedEvent event);
    void playerProfile(MessageReceivedEvent event);
    void hallOfFame(MessageReceivedEvent event);

    // Items 85-150
    void adminReload(MessageReceivedEvent event);
    void giveActivityReward(MessageReceivedEvent event);
    void onlineCommand(MessageReceivedEvent event);
    void petCommand(MessageReceivedEvent event);
    void feedPet(MessageReceivedEvent event);
    void mountRacingRun(MessageReceivedEvent event);
    void mountRacingInfo(MessageReceivedEvent event);
    void professionCommand(MessageReceivedEvent event);
    void gatherResource(MessageReceivedEvent event);
    void showProfessionRecipes(MessageReceivedEvent event);
    void resourceMarket(MessageReceivedEvent event);
    void captureTerritory(MessageReceivedEvent event);
    void siegeCommand(MessageReceivedEvent event);
    void fortressCommand(MessageReceivedEvent event);
    void territoryClanMap(MessageReceivedEvent event);
    void declareAlliance(MessageReceivedEvent event);
    void worldBossAttack(MessageReceivedEvent event);
    void invasionStatus(MessageReceivedEvent event);
    void crisisStatus(MessageReceivedEvent event);
    void showSeason(MessageReceivedEvent event);
    void serverTournament(MessageReceivedEvent event);
    void showSkills(MessageReceivedEvent event);
    void investSkill(MessageReceivedEvent event);
    void useAbility(MessageReceivedEvent event);
    void chooseSecondClass(MessageReceivedEvent event);
    void showFactions(MessageReceivedEvent event);
    void diaryCommand(MessageReceivedEvent event);
    void topActivity(MessageReceivedEvent event);
    void lorePage(MessageReceivedEvent event);
    void weeklyBoard(MessageReceivedEvent event);
    void placeBounty(MessageReceivedEvent event);
    void getBounties(MessageReceivedEvent event);
    void arenaChallenge(MessageReceivedEvent event);
    void arenaLeaderboard(MessageReceivedEvent event);
    void teamArena(MessageReceivedEvent event);
    void survivalChallenge(MessageReceivedEvent event);
    void showChampion(MessageReceivedEvent event);
    void challengeChampion(MessageReceivedEvent event);
    void showLeague(MessageReceivedEvent event);
    void registerTournament(MessageReceivedEvent event);
    void tournamentStatus(MessageReceivedEvent event);
    boolean checkHiddenQuest(MessageReceivedEvent event);
}
