package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Command pattern interface — каждая игровая команда реализует этот интерфейс.
 */
public interface Command {
    /**
     * Выполнить команду в контексте полученного сообщения.
     *
     * @param event событие Discord-сообщения
     */
    void execute(MessageReceivedEvent event);
}
