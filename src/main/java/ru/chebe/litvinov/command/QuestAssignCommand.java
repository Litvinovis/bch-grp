package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+взять квест) для получения нового случайного задания.
 */
public class QuestAssignCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду получения нового квеста.
     *
     * @param playersManager менеджер игроков
     */
    public QuestAssignCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.assignEvent(event);
    }
}
