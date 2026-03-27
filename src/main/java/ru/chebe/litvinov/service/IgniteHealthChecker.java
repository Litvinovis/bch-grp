package ru.chebe.litvinov.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.client.IgniteClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Периодическая проверка доступности Apache Ignite 3 таблиц.
 * Запускает проверку каждые 5 минут. При недоступности — WARNING в логах.
 */
@Slf4j
public class IgniteHealthChecker {

    private static final long CHECK_INTERVAL_MINUTES = 5;

    private final IgniteClient igniteClient;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ignite-health-checker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Создаёт сервис проверки состояния Ignite 3.
     *
     * @param igniteClient подключённый Ignite 3 thin client
     */
    public IgniteHealthChecker(IgniteClient igniteClient) {
        this.igniteClient = igniteClient;
    }

    /**
     * Запускает периодическую проверку доступности Ignite-кэшей.
     * Проверка выполняется каждые {@value CHECK_INTERVAL_MINUTES} минут.
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
     * Выполняет разовую проверку доступности Ignite 3 кластера и всех основных таблиц.
     *
     * @return true если кластер доступен и все таблицы отвечают
     */
    public boolean check() {
        try {
            if (igniteClient == null) {
                markUnhealthy("IgniteClient instance is null");
                return false;
            }
            // Проверяем доступность основных таблиц
            checkTable("players");
            checkTable("locations");
            checkTable("items");
            checkTable("bosses");
            checkTable("ideas");
            checkTable("clans");

            if (!healthy.get()) {
                log.info("Ignite 3 восстановлен и доступен");
                healthy.set(true);
            }
            return true;
        } catch (Exception e) {
            markUnhealthy("Исключение при проверке: " + e.getMessage());
            return false;
        }
    }

    private void checkTable(String tableName) {
        try {
            var table = igniteClient.tables().table(tableName);
            if (table == null) {
                log.warn("[IgniteHealth] Таблица '{}' недоступна (null)", tableName);
            }
        } catch (Exception e) {
            log.warn("[IgniteHealth] Ошибка доступа к таблице '{}': {}", tableName, e.getMessage());
        }
    }

    private void markUnhealthy(String reason) {
        if (healthy.get()) {
            // Логируем WARNING только при первом переходе в нездоровое состояние
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
     * Формирует текстовый отчёт о состоянии Ignite 3 кластера и всех таблиц.
     * Используется командой +статус.
     *
     * @return строка с детальным статусом кластера и таблиц
     */
    public String getStatusReport() {
        boolean ok = check();
        StringBuilder sb = new StringBuilder();
        sb.append("**Статус Apache Ignite 3:**\n");
        sb.append(ok ? "✅ Кластер ДОСТУПЕН\n" : "❌ Кластер НЕДОСТУПЕН\n");

        if (igniteClient != null) {
            try {
                String[] tableNames = {"players", "locations", "items", "bosses", "ideas", "clans"};
                sb.append("\nТаблицы:\n");
                for (String name : tableNames) {
                    try {
                        var table = igniteClient.tables().table(name);
                        if (table != null) {
                            sb.append("  ").append(name).append(": ✅ доступна\n");
                        } else {
                            sb.append("  ").append(name).append(": ❌ недоступна\n");
                        }
                    } catch (Exception e) {
                        sb.append("  ").append(name).append(": ❌ ошибка (").append(e.getMessage()).append(")\n");
                    }
                }
            } catch (Exception e) {
                sb.append("Ошибка получения деталей: ").append(e.getMessage()).append("\n");
            }
        } else {
            sb.append("IgniteClient instance = null\n");
        }
        return sb.toString();
    }

    /**
     * Останавливает планировщик периодических проверок.
     * Необходимо вызывать при завершении работы приложения.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
