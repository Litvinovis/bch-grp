package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+стата) для просмотра характеристик игрового персонажа.
 */
public class StatsCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду просмотра характеристик игрока.
     *
     * @param playersManager менеджер игроков
     */
    public StatsCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.getPlayerInfo(event);
    }
}
