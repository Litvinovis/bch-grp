package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+пвп) для инициации PvP-боя со случайным игроком в текущей локации.
 */
public class PvpCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду PvP-боя.
     *
     * @param playersManager менеджер игроков
     */
    public PvpCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.playersFight(event);
    }
}
