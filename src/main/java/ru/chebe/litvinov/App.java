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

@Slf4j
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    static Optional<String> resolveDiscordToken() {
        return Optional.ofNullable(System.getenv("BCHGRP_DISCORD_TOKEN"))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    public static void main(String[] args) {
        logger.info("Запуск приложения bchgrp");

        try {
            logger.info("Инициализация Apache Ignite");
            Ignite ignite = new IgniteConfigurator().getIgnite();
            logger.info("Apache Ignite успешно инициализирован");
            
            logger.info("Активация кластера Ignite");
            ignite.cluster().state(ClusterState.ACTIVE);
            logger.info("Кластер Ignite активирован");
            
            logger.info("Инициализация Discord бота");
            String token = resolveDiscordToken()
                    .orElseThrow(() -> new IllegalStateException("Не задан токен Discord. Установите переменную окружения BCHGRP_DISCORD_TOKEN"));
            BotConfig botConfig = BotConfig.load();
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new MessageHandler(ignite, botConfig.getAllowedChannelIds()))
                    .setActivity(Activity.playing("БЧ-ГРП"))
                    .build();
            logger.info("Discord бот успешно инициализирован");
            
        } catch (Exception e) {
            logger.error("Ошибка при запуске приложения: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}