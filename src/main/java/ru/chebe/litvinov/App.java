package ru.chebe.litvinov;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.eventHandlers.MessageHandler;
import ru.chebe.litvinov.util.MetricsService;

import javax.sql.DataSource;
import java.util.Optional;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    static Optional<String> resolveDiscordToken() {
        return Optional.ofNullable(System.getenv("BCHGRP_DISCORD_TOKEN"))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    public static void main(String[] args) {
        logger.info("Запуск приложения bchgrp");
        MetricsService.start();

        BotConfig botConfig;
        try {
            botConfig = BotConfig.load();
        } catch (Exception e) {
            logger.error("Ошибка загрузки конфигурации: " + e.getMessage(), e);
            System.exit(1);
            return;
        }

        String token = resolveDiscordToken()
                .orElseThrow(() -> new IllegalStateException(
                        "Не задан токен Discord. Установите переменную окружения BCHGRP_DISCORD_TOKEN"));

        logger.info("Инициализация пула соединений PostgreSQL");
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(botConfig.dbUrl());
        hikariConfig.setUsername(botConfig.dbUsername());
        hikariConfig.setPassword(botConfig.dbPassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setKeepaliveTime(30_000);
        hikariConfig.setInitializationFailTimeout(-1);
        DataSource dataSource = new HikariDataSource(hikariConfig);
        logger.info("Пул соединений PostgreSQL инициализирован");

        MessageHandler handler = new MessageHandler(dataSource, botConfig.allowedChannelIds(), botConfig.adminIds());

        logger.info("Инициализация Discord бота");
        int delaySec = 5;
        while (true) {
            try {
                JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                        .addEventListeners(handler)
                        .setActivity(Activity.playing("БЧ-ГРП"))
                        .setMaxReconnectDelay(60)
                        .build();
                logger.info("Discord бот успешно инициализирован");
                return;
            } catch (Exception e) {
                logger.warn("Не удалось подключиться к Discord ({}), повтор через {} сек", e.getMessage(), delaySec);
                try {
                    Thread.sleep(delaySec * 1000L);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delaySec = Math.min(delaySec * 2, 60);
            }
        }
    }
}
