package ru.chebe.litvinov.service.interfaces;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;

/**
 * Интерфейс для сервиса управления игроками.
 */
public interface IPlayersManager {
    void createPlayer(MessageReceivedEvent event);
    void getPlayerInfo(MessageReceivedEvent event);
    void getInventoryInfo(MessageReceivedEvent event);
    void move(MessageReceivedEvent event);
    void bossFight(MessageReceivedEvent event);
    void playersFight(MessageReceivedEvent event);
    void assignEvent(MessageReceivedEvent event);
    void checkEvent(MessageReceivedEvent event);
    void changeEvent(MessageReceivedEvent event);
    void buyItem(MessageReceivedEvent event);
    void useItem(MessageReceivedEvent event);
    void sellItem(MessageReceivedEvent event);
    void dailyBonus(MessageReceivedEvent event);
    void dieCast(MessageReceivedEvent event);
    void playRoulette(MessageReceivedEvent event);
    void rockPaperScissors(MessageReceivedEvent event);
    void guessTheNumber(MessageReceivedEvent event);
    void clanRegister(MessageReceivedEvent event);
    void clanLeave(MessageReceivedEvent event);
    void clanJoin(MessageReceivedEvent event);
    void acceptApply(MessageReceivedEvent event);
    void rejectApply(MessageReceivedEvent event);
    void clanInfo(MessageReceivedEvent event);
    void deathOfPlayer(Player player);
    int changeMoney(String id, int money, boolean increase);
    void changeXp(String id, int xp);
    int changeHp(String id, int hp, boolean increase);
}
