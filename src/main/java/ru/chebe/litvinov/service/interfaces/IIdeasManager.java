package ru.chebe.litvinov.service.interfaces;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Интерфейс для сервиса управления идеями.
 */
public interface IIdeasManager {

    /**
     * Сохраняет новую идею от игрока.
     *
     * @param event событие Discord-сообщения с текстом идеи
     */
    void putIdea(MessageReceivedEvent event);

    /**
     * Отправляет в канал все существующие идеи.
     *
     * @param event событие Discord-сообщения
     */
    void getAllIdeas(MessageReceivedEvent event);

    /**
     * Отправляет в канал идеи со статусом «Новая».
     *
     * @param event событие Discord-сообщения
     */
    void getNewIdeas(MessageReceivedEvent event);

    /**
     * Отправляет в канал информацию об идее по её номеру.
     *
     * @param event событие Discord-сообщения с номером идеи
     */
    void getIdea(MessageReceivedEvent event);

    /**
     * Изменяет статус идеи по её номеру.
     *
     * @param event событие Discord-сообщения с номером идеи и новым статусом
     */
    void changeIdeaStatus(MessageReceivedEvent event);
}
