package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

/**
 * Администраторская команда (+новыеидеи) для просмотра идей со статусом «Новая».
 */
public class AdminIdeasNewCommand implements Command {
    private final IIdeasManager ideasManager;

    /**
     * Создаёт команду для просмотра новых идей.
     *
     * @param ideasManager менеджер идей
     */
    public AdminIdeasNewCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.getNewIdeas(event);
    }
}
