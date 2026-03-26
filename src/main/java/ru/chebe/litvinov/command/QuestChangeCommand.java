package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+поменять квест) для замены текущего задания на новое за 5 монет.
 */
public class QuestChangeCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду смены квеста.
     *
     * @param playersManager менеджер игроков
     */
    public QuestChangeCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.changeEvent(event);
    }
}
