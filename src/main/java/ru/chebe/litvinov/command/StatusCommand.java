package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.IgniteHealthChecker;

/**
 * Команда +статус — показывает состояние Ignite-кэшей.
 */
public class StatusCommand implements Command {

    private final IgniteHealthChecker healthChecker;

    /**
     * Создаёт команду отображения статуса Ignite-кэшей.
     *
     * @param healthChecker сервис проверки состояния Ignite
     */
    public StatusCommand(IgniteHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        String report = healthChecker.getStatusReport();
        event.getChannel().sendMessage(report).queue();
    }
}
