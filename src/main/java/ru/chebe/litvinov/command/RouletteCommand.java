package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+рулетка) для игры в рулетку в таверне.
 */
public class RouletteCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду игры в рулетку.
     *
     * @param playersManager менеджер игроков
     */
    public RouletteCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.playRoulette(event);
    }
}
