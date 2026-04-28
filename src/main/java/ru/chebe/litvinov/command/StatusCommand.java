package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.sql.DataSource;
import java.sql.Connection;

public class StatusCommand implements Command {

    private final DataSource dataSource;

    public StatusCommand(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        String report;
        try (Connection conn = dataSource.getConnection()) {
            report = "✅ Бот работает. БД: PostgreSQL — соединение активно.";
        } catch (Exception e) {
            report = "⚠️ Бот работает, но БД недоступна: " + e.getMessage();
        }
        event.getChannel().sendMessage(report).queue();
    }
}
