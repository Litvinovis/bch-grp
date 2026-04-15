package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+передать @игрок предмет [количество]) — передача предмета другому игроку.
 */
public class TradeItemCommand implements Command {
    private final IPlayersManager playersManager;

    public TradeItemCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.tradeItem(event);
    }
}
