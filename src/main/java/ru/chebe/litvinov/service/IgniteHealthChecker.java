package ru.chebe.litvinov.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Периодическая проверка доступности Apache Ignite-кэшей.
 * Запускает проверку каждые 5 минут. При недоступности — WARNING в логах.
 */
@Slf4j
public class IgniteHealthChecker {

    private static final long CHECK_INTERVAL_MINUTES = 5;

    private final Ignite ignite;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ignite-health-checker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Создаёт сервис проверки состояния Ignite.
     *
     * @param ignite запущенный экземпляр Apache Ignite
     */
    public IgniteHealthChecker(Ignite ignite) {
        this.ignite = ignite;
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
     * Выполняет разовую проверку доступности Ignite-кластера и всех основных кэшей.
     *
     * @return true если кластер доступен и все кэши отвечают
     */
    public boolean check() {
        try {
            if (ignite == null || ignite.cluster() == null) {
                markUnhealthy("Ignite instance is null");
                return false;
            }
            // Проверяем что кластер активен
            var state = ignite.cluster().state();
            if (state == null) {
                markUnhealthy("Cluster state is null");
                return false;
            }
            // Проверяем доступность основных кэшей
            checkCache("players");
            checkCache("locations");
            checkCache("items");
            checkCache("bosses");
            checkCache("ideas");
            checkCache("clans");

            if (!healthy.get()) {
                log.info("Ignite восстановлен и доступен (состояние кластера: {})", state);
                healthy.set(true);
            }
            return true;
        } catch (Exception e) {
            markUnhealthy("Исключение при проверке: " + e.getMessage());
            return false;
        }
    }

    private void checkCache(String cacheName) {
        try {
            IgniteCache<?, ?> cache = ignite.cache(cacheName);
            if (cache == null) {
                log.warn("[IgniteHealth] Кэш '{}' недоступен (null)", cacheName);
            }
        } catch (Exception e) {
            log.warn("[IgniteHealth] Ошибка доступа к кэшу '{}': {}", cacheName, e.getMessage());
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
     * Формирует текстовый отчёт о состоянии Ignite-кластера и всех кэшей.
     * Используется командой +статус.
     *
     * @return строка с детальным статусом кластера и кэшей
     */
    public String getStatusReport() {
        boolean ok = check();
        StringBuilder sb = new StringBuilder();
        sb.append("**Статус Apache Ignite:**\n");
        sb.append(ok ? "✅ Кластер ДОСТУПЕН\n" : "❌ Кластер НЕДОСТУПЕН\n");

        if (ignite != null) {
            try {
                sb.append("Состояние: ").append(ignite.cluster().state()).append("\n");
                sb.append("Узлов в кластере: ").append(ignite.cluster().nodes().size()).append("\n");

                String[] cacheNames = {"players", "locations", "items", "bosses", "ideas", "clans"};
                sb.append("\nКэши:\n");
                for (String name : cacheNames) {
                    try {
                        IgniteCache<?, ?> cache = ignite.cache(name);
                        if (cache != null) {
                            sb.append("  ").append(name).append(": ✅ доступен\n");
                        } else {
                            sb.append("  ").append(name).append(": ❌ недоступен\n");
                        }
                    } catch (Exception e) {
                        sb.append("  ").append(name).append(": ❌ ошибка (").append(e.getMessage()).append(")\n");
                    }
                }
            } catch (Exception e) {
                sb.append("Ошибка получения деталей: ").append(e.getMessage()).append("\n");
            }
        } else {
            sb.append("Ignite instance = null\n");
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
