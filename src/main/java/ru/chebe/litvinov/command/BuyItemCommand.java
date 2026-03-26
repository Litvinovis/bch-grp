package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+купить) для покупки предмета в магазине или таверне.
 */
public class BuyItemCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду покупки предмета.
     *
     * @param playersManager менеджер игроков
     */
    public BuyItemCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.buyItem(event);
    }
}
