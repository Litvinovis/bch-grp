package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

public class AdminIdeasAllCommand implements Command {
    private final IIdeasManager ideasManager;

    public AdminIdeasAllCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.getAllIdeas(event);
    }
}
