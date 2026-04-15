package ru.chebe.litvinov;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.eventHandlers.MessageHandler;

/**
 * Точка входа приложения BCH-GRP RPG Discord бота.
 * Инициализирует Apache Ignite кластер и подключает JDA Discord-клиент.
 */
@Slf4j
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    /**
     * Читает токен Discord из переменной окружения BCHGRP_DISCORD_TOKEN.
     *
     * @return токен, обёрнутый в Optional, или пустой Optional если переменная не задана
     */
    static Optional<String> resolveDiscordToken() {
        return Optional.ofNullable(System.getenv("BCHGRP_DISCORD_TOKEN"))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    /**
     * Главный метод запуска приложения.
     * Последовательно инициализирует Ignite, активирует кластер и запускает Discord-бота.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        logger.info("Запуск приложения bchgrp");

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

        logger.info("Инициализация Apache Ignite 3 thin client");
        Ignite3Configurator configurator = new Ignite3Configurator(botConfig.ignite3Address());
        logger.info("Apache Ignite 3 thin client инициализирован (переподключение при недоступности выполнит IgniteHealthChecker)");

        MessageHandler handler = new MessageHandler(configurator, botConfig.allowedChannelIds(), botConfig.adminIds());

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
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delaySec = Math.min(delaySec * 2, 60);
            }
        }
    }
}