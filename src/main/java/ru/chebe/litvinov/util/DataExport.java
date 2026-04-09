package ru.chebe.litvinov.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.SqlRow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Утилита экспорта и импорта данных для миграции Apache Ignite 3.0.0 → 3.1.0.
 *
 * <p>Использование (из корня репозитория после сборки):</p>
 * <pre>
 *   java -cp bchgrp.jar ru.chebe.litvinov.util.DataExport export /backup/dir 127.0.0.1:10300
 *   java -cp bchgrp.jar ru.chebe.litvinov.util.DataExport import /backup/dir 127.0.0.1:10300
 *   java -cp bchgrp.jar ru.chebe.litvinov.util.DataExport verify /backup/dir 127.0.0.1:10300
 * </pre>
 */
public class DataExport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] TABLES = {
        "players", "locations", "items", "bosses", "ideas", "clans"
    };

    private static final Map<String, String> TABLE_QUERIES = Map.of(
        "players",
            "SELECT id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, " +
            "location, level, \"exp\", exp_to_next, inventory, answer, active_event, " +
            "daily_time, clan_name FROM players",
        "locations",
            "SELECT name, dangerous, population_by_name, population_by_id, paths, " +
            "pvp, boss, boss_item, teleport FROM locations",
        "items",
            "SELECT name, description, price, luck, strength, health, armor, " +
            "reputation, xp_generation, quantity, expire_time, action FROM items",
        "bosses",
            "SELECT nick_name, hp, strength, armor, boss_item, defeat, win FROM bosses",
        "ideas",
            "SELECT id, description, author, resolution FROM ideas",
        "clans",
            "SELECT name, leader_id, members, appliers FROM clans"
    );

    private static final Map<String, String> TABLE_INSERT = Map.of(
        "players",
            "INSERT INTO players (id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, " +
            "location, level, \"exp\", exp_to_next, inventory, answer, active_event, daily_time, clan_name) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        "locations",
            "INSERT INTO locations (name, dangerous, population_by_name, population_by_id, paths, " +
            "pvp, boss, boss_item, teleport) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        "items",
            "INSERT INTO items (name, description, price, luck, strength, health, armor, " +
            "reputation, xp_generation, quantity, expire_time, action) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        "bosses",
            "INSERT INTO bosses (nick_name, hp, strength, armor, boss_item, defeat, win) VALUES (?, ?, ?, ?, ?, ?, ?)",
        "ideas",
            "INSERT INTO ideas (id, description, author, resolution) VALUES (?, ?, ?, ?)",
        "clans",
            "INSERT INTO clans (name, leader_id, members, appliers) VALUES (?, ?, ?, ?)"
    );

    private static final Map<String, String[]> TABLE_COLUMNS = Map.of(
        "players",
            new String[]{"id", "nick_name", "hp", "max_hp", "luck", "money", "reputation", "armor", "strength",
                         "location", "level", "exp", "exp_to_next", "inventory", "answer", "active_event",
                         "daily_time", "clan_name"},
        "locations",
            new String[]{"name", "dangerous", "population_by_name", "population_by_id", "paths",
                         "pvp", "boss", "boss_item", "teleport"},
        "items",
            new String[]{"name", "description", "price", "luck", "strength", "health", "armor",
                         "reputation", "xp_generation", "quantity", "expire_time", "action"},
        "bosses",
            new String[]{"nick_name", "hp", "strength", "armor", "boss_item", "defeat", "win"},
        "ideas",
            new String[]{"id", "description", "author", "resolution"},
        "clans",
            new String[]{"name", "leader_id", "members", "appliers"}
    );

    // Колонки, которые используют quoted identifiers в SQL (зарезервированные слова)
    private static final java.util.Set<String> QUOTED_COLUMNS = java.util.Set.of("exp");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0];
        String backupDir = args[1];
        String address = args.length >= 3 ? args[2] : "127.0.0.1:10300";

        System.out.println("[DataExport] Mode:    " + mode);
        System.out.println("[DataExport] Dir:     " + backupDir);
        System.out.println("[DataExport] Ignite:  " + address);

        switch (mode) {
            case "export" -> export(backupDir, address);
            case "import" -> importData(backupDir, address);
            case "verify" -> verify(backupDir, address);
            default -> {
                System.err.println("Unknown mode: " + mode);
                printUsage();
                System.exit(1);
            }
        }
    }

    // ---- EXPORT ----

    private static void export(String backupDir, String address) throws Exception {
        Path dir = Paths.get(backupDir);
        Files.createDirectories(dir);

        try (IgniteClient client = IgniteClient.builder().addresses(address).build()) {
            System.out.println("[Export] Connected to " + address);
            for (String table : TABLES) {
                int count = exportTable(client, table, dir);
                System.out.printf("[Export] %-20s → %d rows%n", table, count);
            }
            System.out.println("[Export] Done. Files: " + dir.toAbsolutePath());
        }
    }

    private static int exportTable(IgniteClient client, String table, Path dir) throws Exception {
        ArrayNode rows = MAPPER.createArrayNode();
        String[] columns = TABLE_COLUMNS.get(table);

        try (ResultSet<SqlRow> rs = client.sql().execute(null, TABLE_QUERIES.get(table))) {
            while (rs.hasNext()) {
                SqlRow row = rs.next();
                ObjectNode node = MAPPER.createObjectNode();
                for (String col : columns) {
                    Object val = row.value(col);
                    if (val == null) {
                        node.putNull(col);
                    } else if (val instanceof Boolean b) {
                        node.put(col, b);
                    } else if (val instanceof Long l) {
                        node.put(col, l);
                    } else if (val instanceof Integer i) {
                        node.put(col, i);
                    } else {
                        node.put(col, val.toString());
                    }
                }
                rows.add(node);
            }
        }

        File outFile = dir.resolve(table + ".json").toFile();
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outFile, rows);
        return rows.size();
    }

    // ---- IMPORT ----

    private static void importData(String backupDir, String address) throws Exception {
        Path dir = Paths.get(backupDir);

        try (IgniteClient client = IgniteClient.builder().addresses(address).build()) {
            System.out.println("[Import] Connected to " + address);
            initSchema(client);

            for (String table : TABLES) {
                File file = dir.resolve(table + ".json").toFile();
                if (!file.exists()) {
                    System.out.println("[Import] Skipping " + table + " — not found: " + file);
                    continue;
                }
                int count = importTable(client, table, file);
                System.out.printf("[Import] %-20s ← %d rows%n", table, count);
            }
            System.out.println("[Import] Done.");
        }
    }

    private static int importTable(IgniteClient client, String table, File file) throws Exception {
        var rows = MAPPER.readTree(file);
        String[] columns = TABLE_COLUMNS.get(table);
        String insertSql = TABLE_INSERT.get(table);
        int count = 0;

        for (var row : rows) {
            Object[] params = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                var node = row.get(columns[i]);
                if (node == null || node.isNull()) {
                    params[i] = null;
                } else if (node.isBoolean()) {
                    params[i] = node.booleanValue();
                } else if (node.isLong() || node.isInt()) {
                    params[i] = node.longValue();
                } else {
                    params[i] = node.asText();
                }
            }
            try {
                client.sql().execute(null, insertSql, params);
                count++;
            } catch (Exception e) {
                if (!e.getMessage().contains("duplicate") && !e.getMessage().contains("primary key")) {
                    System.err.printf("[Import] Row error in %s: %s%n", table, e.getMessage());
                }
            }
        }
        return count;
    }

    private static void initSchema(IgniteClient client) {
        String[] ddl = {
            "CREATE ZONE IF NOT EXISTS bchgrp WITH STORAGE_PROFILES='default', REPLICAS=1, PARTITIONS=25",
            "CREATE TABLE IF NOT EXISTS players (id VARCHAR PRIMARY KEY, nick_name VARCHAR NOT NULL, " +
            "hp INT NOT NULL DEFAULT 100, max_hp INT NOT NULL DEFAULT 100, luck INT NOT NULL DEFAULT 5, " +
            "money INT NOT NULL DEFAULT 50, reputation INT NOT NULL DEFAULT 0, armor INT NOT NULL DEFAULT 0, " +
            "strength INT NOT NULL DEFAULT 5, location VARCHAR NOT NULL DEFAULT 'respawn', " +
            "level INT NOT NULL DEFAULT 1, \"exp\" INT NOT NULL DEFAULT 0, exp_to_next INT NOT NULL DEFAULT 100, " +
            "inventory VARCHAR NOT NULL DEFAULT '{}', answer VARCHAR NOT NULL DEFAULT '', " +
            "active_event VARCHAR, daily_time BIGINT NOT NULL DEFAULT 0, clan_name VARCHAR NOT NULL DEFAULT '') ZONE bchgrp",
            "CREATE TABLE IF NOT EXISTS locations (name VARCHAR PRIMARY KEY, dangerous INT NOT NULL DEFAULT 0, " +
            "population_by_name VARCHAR NOT NULL DEFAULT '[]', population_by_id VARCHAR NOT NULL DEFAULT '[]', " +
            "paths VARCHAR NOT NULL DEFAULT '[]', pvp BOOLEAN NOT NULL DEFAULT FALSE, boss VARCHAR, " +
            "boss_item VARCHAR, teleport BOOLEAN NOT NULL DEFAULT FALSE) ZONE bchgrp",
            "CREATE TABLE IF NOT EXISTS items (name VARCHAR PRIMARY KEY, description VARCHAR, " +
            "price INT NOT NULL DEFAULT 0, luck INT NOT NULL DEFAULT 0, strength INT NOT NULL DEFAULT 0, " +
            "health INT NOT NULL DEFAULT 0, armor INT NOT NULL DEFAULT 0, reputation INT NOT NULL DEFAULT 0, " +
            "xp_generation INT NOT NULL DEFAULT 0, quantity INT NOT NULL DEFAULT 0, " +
            "expire_time BIGINT NOT NULL DEFAULT 0, action BOOLEAN NOT NULL DEFAULT FALSE) ZONE bchgrp",
            "CREATE TABLE IF NOT EXISTS bosses (nick_name VARCHAR PRIMARY KEY, hp INT NOT NULL DEFAULT 1000, " +
            "strength INT NOT NULL DEFAULT 10, armor INT NOT NULL DEFAULT 0, boss_item VARCHAR, " +
            "defeat INT NOT NULL DEFAULT 0, win INT NOT NULL DEFAULT 0) ZONE bchgrp",
            "CREATE TABLE IF NOT EXISTS ideas (id INT PRIMARY KEY, description VARCHAR, author VARCHAR, " +
            "resolution VARCHAR NOT NULL DEFAULT 'New') ZONE bchgrp",
            "CREATE TABLE IF NOT EXISTS clans (name VARCHAR PRIMARY KEY, leader_id VARCHAR NOT NULL, " +
            "members VARCHAR NOT NULL DEFAULT '[]', appliers VARCHAR NOT NULL DEFAULT '[]') ZONE bchgrp"
        };
        for (String stmt : ddl) {
            try {
                client.sql().execute(null, stmt);
            } catch (Exception e) {
                System.out.println("[Import] DDL skipped (exists): " + e.getMessage().split("\n")[0]);
            }
        }
    }

    // ---- VERIFY ----

    private static void verify(String backupDir, String address) throws Exception {
        Path dir = Paths.get(backupDir);
        boolean allMatch = true;

        try (IgniteClient client = IgniteClient.builder().addresses(address).build()) {
            System.out.println("[Verify] Connected to " + address);
            for (String table : TABLES) {
                File file = dir.resolve(table + ".json").toFile();
                if (!file.exists()) {
                    System.out.printf("[Verify] %-20s backup file missing%n", table);
                    allMatch = false;
                    continue;
                }
                int backupCount = MAPPER.readTree(file).size();
                int liveCount = 0;
                try (ResultSet<SqlRow> rs = client.sql().execute(null,
                        "SELECT COUNT(*) AS cnt FROM " + table)) {
                    if (rs.hasNext()) liveCount = (int) rs.next().longValue("cnt");
                }
                String status = backupCount == liveCount ? "OK" : "MISMATCH";
                System.out.printf("[Verify] %-20s backup=%d live=%d [%s]%n",
                        table, backupCount, liveCount, status);
                if (!"OK".equals(status)) allMatch = false;
            }
        }

        if (allMatch) {
            System.out.println("[Verify] All tables match.");
        } else {
            System.out.println("[Verify] WARNING: some tables do not match!");
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: DataExport <export|import|verify> <backup-dir> [ignite-address]");
        System.out.println("Default address: 127.0.0.1:10300");
    }
}
