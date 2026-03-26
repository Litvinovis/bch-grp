package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+принять заявки) для принятия лидером клана всех заявок на вступление.
 */
public class ClanAcceptCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду принятия заявок в клан.
     *
     * @param playersManager менеджер игроков
     */
    public ClanAcceptCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.acceptApply(event);
    }
}
