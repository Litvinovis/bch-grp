package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

/**
 * Команда (+идея) для подачи предложения или баг-репорта разработчикам.
 */
public class IdeaCommand implements Command {
    private final IIdeasManager ideasManager;

    /**
     * Создаёт команду подачи идеи.
     *
     * @param ideasManager менеджер идей
     */
    public IdeaCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.putIdea(event);
    }
}
