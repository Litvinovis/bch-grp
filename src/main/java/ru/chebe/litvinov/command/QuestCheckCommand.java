package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+выполнить квест) для проверки выполнения условия активного задания.
 */
public class QuestCheckCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду проверки выполнения квеста.
     *
     * @param playersManager менеджер игроков
     */
    public QuestCheckCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.checkEvent(event);
    }
}
