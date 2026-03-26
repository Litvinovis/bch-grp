package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

/**
 * Администраторская команда (+идеяномер) для получения информации об идее по её номеру.
 */
public class AdminIdeaGetCommand implements Command {
    private final IIdeasManager ideasManager;

    /**
     * @param ideasManager менеджер идей
     */
    public AdminIdeaGetCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.getIdea(event);
    }
}
