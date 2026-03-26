package ru.chebe.litvinov;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.Optional;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterState;
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

        try {
            BotConfig botConfig = BotConfig.load();

            logger.info("Инициализация Apache Ignite");
            Ignite ignite = new IgniteConfigurator(botConfig.getIgniteLocalAddress(), botConfig.getIgniteDiscoveryAddresses(), botConfig.getIgniteWorkDir()).getIgnite();
            logger.info("Apache Ignite успешно инициализирован");
            
            logger.info("Активация кластера Ignite");
            ignite.cluster().state(ClusterState.ACTIVE);
            logger.info("Кластер Ignite активирован");
            
            logger.info("Инициализация Discord бота");
            String token = resolveDiscordToken()
                    .orElseThrow(() -> new IllegalStateException("Не задан токен Discord. Установите переменную окружения BCHGRP_DISCORD_TOKEN"));
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new MessageHandler(ignite, botConfig.getAllowedChannelIds(), botConfig.getAdminIds()))
                    .setActivity(Activity.playing("БЧ-ГРП"))
                    .setMaxReconnectDelay(60)
                    .build();
            logger.info("Discord бот успешно инициализирован");
            
        } catch (Exception e) {
            logger.error("Ошибка при запуске приложения: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}