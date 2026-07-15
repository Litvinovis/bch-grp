package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        String sql = loadResource("schema.sql");
        if (sql == null || sql.isBlank()) {
            log.warn("SchemaInitializer: schema.sql не найден или пустой — пропуск");
            return;
        }
        int ok = 0, skipped = 0;
        try (Connection conn = dataSource.getConnection()) {
            for (String raw : sql.split(";")) {
                String stmt = raw.lines()
                        .filter(l -> !l.trim().startsWith("--") && !l.trim().isEmpty())
                        .collect(Collectors.joining("\n"))
                        .trim();
                if (stmt.isEmpty()) continue;
                try (Statement s = conn.createStatement()) {
                    s.execute(stmt);
                    ok++;
                } catch (Exception e) {
                    log.debug("DDL пропущен: {}", e.getMessage());
                    skipped++;
                }
            }
        } catch (Exception e) {
            log.error("SchemaInitializer: ошибка подключения к БД", e);
        }
        log.info("Схема PostgreSQL инициализирована: {} успешно, {} пропущено", ok, skipped);
    }

    private String loadResource(String name) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            if (is == null) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.error("Ошибка чтения {}", name, e);
            return null;
        }
    }
}
