package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

public class IdeaCommand implements Command {
    private final IIdeasManager ideasManager;

    public IdeaCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.putIdea(event);
    }
}
