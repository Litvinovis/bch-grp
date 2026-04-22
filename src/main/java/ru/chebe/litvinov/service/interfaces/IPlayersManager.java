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
}
