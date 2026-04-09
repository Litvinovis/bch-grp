package ru.chebe.litvinov.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.client.IgniteClient;
import ru.chebe.litvinov.Ignite3Configurator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис периодической проверки доступности Apache Ignite 3 с механизмом переподключения.
 *
 * <p>Каждые {@value #CHECK_INTERVAL_MINUTES} минут проверяет доступность основных таблиц.
 * При обнаружении сбоя немедленно запускает цикл переподключения с интервалом
 * {@value #RECONNECT_INTERVAL_SEC} секунд. После успешного переподключения цикл
 * останавливается.
 */
@Slf4j
public class IgniteHealthChecker {

    private static final long CHECK_INTERVAL_MINUTES = 5;
    private static final long RECONNECT_INTERVAL_SEC = 30;

    private final Ignite3Configurator configurator;
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ignite-health-checker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Создаёт сервис проверки состояния Ignite 3.
     *
     * @param configurator менеджер подключения Ignite 3
     */
    public IgniteHealthChecker(Ignite3Configurator configurator) {
        this.configurator = configurator;
    }

    /**
     * Запускает периодическую проверку доступности Ignite-таблиц.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::check,
                CHECK_INTERVAL_MINUTES,
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
        log.info("IgniteHealthChecker запущен: проверка каждые {} минут", CHECK_INTERVAL_MINUTES);
    }

    /**
     * Выполняет разовую проверку доступности Ignite 3 кластера и основных таблиц.
     * При сбое инициирует цикл переподключения.
     *
     * @return true если кластер доступен
     */
    public boolean check() {
        IgniteClient client = configurator.getClient();
        try {
            if (client == null) {
                markUnhealthy("IgniteClient instance is null");
                scheduleReconnect();
                return false;
            }
            checkTable(client, "players");
            checkTable(client, "locations");
            checkTable(client, "items");
            checkTable(client, "bosses");
            checkTable(client, "ideas");
            checkTable(client, "clans");

            if (!healthy.get()) {
                log.info("Ignite 3 восстановлен и доступен");
                healthy.set(true);
                reconnecting.set(false);
            }
            return true;
        } catch (Exception e) {
            markUnhealthy("Исключение при проверке: " + e.getMessage());
            scheduleReconnect();
            return false;
        }
    }

    /**
     * Запускает цикл переподключения, если он ещё не активен.
     */
    private void scheduleReconnect() {
        if (reconnecting.compareAndSet(false, true)) {
            log.info("IgniteHealthChecker: запускаю цикл переподключения (интервал {}с)", RECONNECT_INTERVAL_SEC);
            scheduler.schedule(this::attemptReconnect, RECONNECT_INTERVAL_SEC, TimeUnit.SECONDS);
        }
    }

    /**
     * Одна попытка переподключения. При неудаче планирует следующую.
     */
    private void attemptReconnect() {
        log.info("IgniteHealthChecker: попытка переподключения к Ignite 3...");
        boolean ok = configurator.reconnect();
        if (ok) {
            IgniteClient fresh = configurator.getClient();
            try {
                checkTable(fresh, "players");
                healthy.set(true);
                reconnecting.set(false);
                log.info("IgniteHealthChecker: переподключение успешно, кластер доступен");
            } catch (Exception e) {
                log.warn("IgniteHealthChecker: переподключение выполнено, но верификация не прошла: {} — повтор через {}с",
                        e.getMessage(), RECONNECT_INTERVAL_SEC);
                scheduler.schedule(this::attemptReconnect, RECONNECT_INTERVAL_SEC, TimeUnit.SECONDS);
            }
        } else {
            log.warn("IgniteHealthChecker: переподключение не удалось — повтор через {}с", RECONNECT_INTERVAL_SEC);
            scheduler.schedule(this::attemptReconnect, RECONNECT_INTERVAL_SEC, TimeUnit.SECONDS);
        }
    }

    private void checkTable(IgniteClient client, String tableName) {
        try {
            var table = client.tables().table(tableName);
            if (table == null) {
                log.warn("[IgniteHealth] Таблица '{}' недоступна (null)", tableName);
            }
        } catch (Exception e) {
            log.warn("[IgniteHealth] Ошибка доступа к таблице '{}': {}", tableName, e.getMessage());
        }
    }

    private void markUnhealthy(String reason) {
        if (healthy.get()) {
            log.warn("[IgniteHealth] WARNING: Apache Ignite недоступен! Причина: {}", reason);
        } else {
            log.warn("[IgniteHealth] Apache Ignite всё ещё недоступен. Причина: {}", reason);
        }
        healthy.set(false);
    }

    /**
     * Возвращает результат последней проверки доступности Ignite.
     *
     * @return true если последняя проверка прошла успешно
     */
    public boolean isHealthy() {
        return healthy.get();
    }

    /**
     * Формирует текстовый отчёт о состоянии Ignite 3 кластера.
     * Используется командой +статус.
     *
     * @return строка с детальным статусом кластера и таблиц
     */
    public String getStatusReport() {
        IgniteClient client = configurator.getClient();
        boolean ok = false;
        try {
            if (client != null) {
                checkTable(client, "players");
                ok = true;
            }
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Статус Apache Ignite 3:**\n");
        sb.append(ok ? "✅ Кластер ДОСТУПЕН\n" : "❌ Кластер НЕДОСТУПЕН\n");

        if (client != null) {
            String[] tableNames = {"players", "locations", "items", "bosses", "ideas", "clans"};
            sb.append("\nТаблицы:\n");
            for (String name : tableNames) {
                try {
                    var table = client.tables().table(name);
                    if (table != null) {
                        sb.append("  ").append(name).append(": ✅ доступна\n");
                    } else {
                        sb.append("  ").append(name).append(": ❌ недоступна\n");
                    }
                } catch (Exception e) {
                    sb.append("  ").append(name).append(": ❌ ошибка (").append(e.getMessage()).append(")\n");
                }
            }
        } else {
            sb.append("IgniteClient instance = null\n");
        }
        if (reconnecting.get()) {
            sb.append("\n⏳ Идёт переподключение...\n");
        }
        return sb.toString();
    }

    /**
     * Останавливает планировщик периодических проверок.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
