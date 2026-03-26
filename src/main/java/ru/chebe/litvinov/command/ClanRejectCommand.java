package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+отклонить заявки) для отклонения лидером клана всех заявок на вступление.
 */
public class ClanRejectCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду отклонения заявок в клан.
     *
     * @param playersManager менеджер игроков
     */
    public ClanRejectCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.rejectApply(event);
    }
}
