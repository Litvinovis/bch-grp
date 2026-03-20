package ru.chebe.litvinov.service.interfaces;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Интерфейс для сервиса управления идеями.
 */
public interface IIdeasManager {
    void putIdea(MessageReceivedEvent event);
    void getAllIdeas(MessageReceivedEvent event);
    void getNewIdeas(MessageReceivedEvent event);
    void getIdea(MessageReceivedEvent event);
    void changeIdeaStatus(MessageReceivedEvent event);
}
