package org.example;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterState;
import org.example.eventHandlers.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class App {
    public static void main(String[] args) {
        Logger start = LoggerFactory.getLogger("default-logger");
        start.trace("Стартуем приложение");
        Ignite ignite = new IgniteConfigurator().getIgnite();
        ignite.cluster().state(ClusterState.ACTIVE);
        JDA jda = JDABuilder.createDefault("MTI0ODY2ODcyNTA2MjY2NDIwMw.Gx4e3S.b2-QX3Q_4jNe43FPWvfGVvAhLfF91SwwLHAVOM")
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new MessageHandler(ignite))
                .setActivity(Activity.playing("БЧ-ГРП"))
                .build();
    }
}
