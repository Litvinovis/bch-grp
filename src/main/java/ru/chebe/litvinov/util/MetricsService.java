package ru.chebe.litvinov.util;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой Prometheus-совместимый HTTP-сервер метрик на порту 9090.
 * Запускается один раз при старте бота.
 */
public final class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    public static final AtomicLong battlesTotal = new AtomicLong(0);
    public static final AtomicLong coinsInCirculation = new AtomicLong(0);
    public static final AtomicLong activePlayersToday = new AtomicLong(0);

    private MetricsService() {}

    /** Записывает один бой. */
    public static void recordBattle() {
        battlesTotal.incrementAndGet();
    }

    /** Изменяет количество монет в обращении. */
    public static void updateCoins(long delta) {
        coinsInCirculation.addAndGet(delta);
    }

    /** Устанавливает количество активных игроков сегодня. */
    public static void updateActivePlayers(int count) {
        activePlayersToday.set(count);
    }

    /**
     * Запускает HTTP-сервер на порту 9090 (endpoint /metrics).
     * Не блокирует вызывающий поток.
     */
    public static void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(9090), 0);
            server.createContext("/metrics", exchange -> {
                String body = buildMetrics();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            server.setExecutor(null);
            server.start();
            log.info("Prometheus metrics server started on :9090/metrics");
        } catch (IOException e) {
            log.warn("Cannot start metrics server: {}", e.getMessage());
        }
    }

    private static String buildMetrics() {
        return "# HELP bchgrp_battles_total Total battles fought\n" +
               "# TYPE bchgrp_battles_total counter\n" +
               "bchgrp_battles_total " + battlesTotal.get() + "\n" +
               "# HELP bchgrp_coins_in_circulation Total coins in circulation\n" +
               "# TYPE bchgrp_coins_in_circulation gauge\n" +
               "bchgrp_coins_in_circulation " + coinsInCirculation.get() + "\n" +
               "# HELP bchgrp_active_players_today Active players today\n" +
               "# TYPE bchgrp_active_players_today gauge\n" +
               "bchgrp_active_players_today " + activePlayersToday.get() + "\n";
    }
}
