package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+идти) для перемещения игрока в другую локацию.
 */
public class MoveCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду перемещения игрока.
     *
     * @param playersManager менеджер игроков
     */
    public MoveCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.move(event);
    }
}
