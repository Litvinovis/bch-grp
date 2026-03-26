package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;

/**
 * Администраторская команда (+всеидеи) для получения всех поданных идей.
 */
public class AdminIdeasAllCommand implements Command {
    private final IIdeasManager ideasManager;

    /**
     * Создаёт команду для получения всех идей.
     *
     * @param ideasManager менеджер идей
     */
    public AdminIdeasAllCommand(IIdeasManager ideasManager) {
        this.ideasManager = ideasManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        ideasManager.getAllIdeas(event);
    }
}
