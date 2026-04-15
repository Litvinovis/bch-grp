package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+класс [воин|разбойник|маг]) — выбор класса персонажа с 5 уровня.
 */
public class ChooseClassCommand implements Command {
    private final IPlayersManager playersManager;

    public ChooseClassCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.chooseClass(event);
    }
}
