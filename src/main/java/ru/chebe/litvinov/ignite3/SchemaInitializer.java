package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Инициализатор схемы базы данных для Apache Ignite 3.
 * Читает DDL из ignite3-schema.sql и выполняет через IgniteClient SQL API.
 */
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final IgniteClient client;

    /**
     * Создаёт инициализатор схемы.
     *
     * @param client подключённый Ignite 3 thin client
     */
    public SchemaInitializer(IgniteClient client) {
        this.client = client;
    }

    /**
     * Выполняет ALTER TABLE миграции для колонок, добавленных в новых версиях схемы.
     * Вызывается перед init() чтобы привести существующие таблицы к актуальной схеме.
     * Ошибки логируются, но не останавливают процесс (колонка может уже существовать).
     */
    public void migrate() {
        log.info("Запуск миграций схемы Ignite 3...");
        String[] migrations = {
            "ALTER TABLE players ADD COLUMN daily_streak  INT     NOT NULL DEFAULT 0",
            "ALTER TABLE players ADD COLUMN player_class  VARCHAR NOT NULL DEFAULT ''",
            "ALTER TABLE players ADD COLUMN achievements  VARCHAR NOT NULL DEFAULT '[]'"
        };
        int ok = 0;
        int failed = 0;
        for (String stmt : migrations) {
            try {
                client.sql().execute(null, stmt);
                log.info("Миграция выполнена: {}", stmt);
                ok++;
            } catch (Exception e) {
                if (isAlreadyExistsError(e)) {
                    log.debug("Миграция пропущена — колонка уже существует: {}", stmt);
                } else {
                    log.warn("Миграция пропущена ({}): {}", e.getMessage(), stmt);
                }
                failed++;
            }
        }
        log.info("Миграции схемы завершены: {} успешно, {} пропущено/ошибок", ok, failed);
    }

    /**
     * Выполняет DDL-скрипт из classpath-ресурса ignite3-schema.sql.
     * Операторы разделяются по ';'. Каждый выполняется отдельно.
     * Ошибки логируются, но не останавливают процесс (таблица может уже существовать).
     */
    public void init() {
        log.info("Инициализация схемы Ignite 3...");
        String sql = loadSqlResource();
        if (sql == null || sql.isBlank()) {
            log.warn("DDL-скрипт ignite3-schema.sql пуст или не найден — пропуск инициализации схемы");
            return;
        }

        // Разбиваем по ';', фильтруем комментарии и пустые строки
        String[] statements = sql.split(";");
        int ok = 0;
        int failed = 0;
        for (String raw : statements) {
            String stmt = Arrays.stream(raw.split("\n"))
                    .filter(line -> !line.trim().startsWith("--") && !line.trim().isEmpty())
                    .collect(Collectors.joining("\n"))
                    .trim();
            if (stmt.isEmpty()) continue;

            try {
                client.sql().execute(null, stmt);
                ok++;
            } catch (Exception e) {
                // IF NOT EXISTS — всё равно может выбросить при наличии объекта в части реализаций
                log.warn("DDL statement failed: {} | stmt: {}", e.getMessage(), stmt.replace("\n", " "));
                failed++;
            }
        }
        log.info("Схема Ignite 3 инициализирована: {} успешно, {} пропущено/ошибок", ok, failed);
    }

    private static boolean isAlreadyExistsError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("already exists")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String loadSqlResource() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("ignite3-schema.sql")) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error("Ошибка чтения ignite3-schema.sql: {}", e.getMessage());
            return null;
        }
    }
}
