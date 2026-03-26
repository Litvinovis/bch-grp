package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

/**
 * Администраторская команда (+идеястатус) для изменения статуса идеи.
 */
public class AdminIdeaStatusCommand implements Command {
    private final IIdeasManager ideasManager;

    /**
     * Создаёт команду для изменения статуса идеи.
     *
     * @param ideasManager менеджер идей
     */
    public AdminIdeaStatusCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.changeIdeaStatus(event);
    }
}
