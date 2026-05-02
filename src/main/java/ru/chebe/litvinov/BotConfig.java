package ru.chebe.litvinov;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record BotConfig(
        List<String> allowedChannelIds,
        List<String> adminIds,
        String dbUrl,
        String dbUsername,
        String dbPassword
) {

    @SuppressWarnings("unchecked")
    public static BotConfig load() {
        try (InputStream is = BotConfig.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) return defaults();
            Map<String, Object> root = new Yaml().load(is);
            if (root == null) return defaults();

            List<String> channelIds = readStringList(root, "discord", "allowed-channel-ids");
            List<String> adminIds   = readStringList(root, "discord", "admin-ids");
            String dbUrl      = envOr("DB_URL",      readString(root, "db", "url",      "jdbc:postgresql://127.0.0.1:5432/bchgrp"));
            String dbUsername = envOr("DB_USERNAME", readString(root, "db", "username", "bchgrp"));
            String dbPassword = envOr("DB_PASSWORD", readString(root, "db", "password", ""));

            return new BotConfig(channelIds, adminIds, dbUrl, dbUsername, dbPassword);
        } catch (Exception e) {
            return defaults();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> root, String section, String key) {
        Object secObj = root.get(section);
        if (!(secObj instanceof Map<?, ?> sec)) return new ArrayList<>();
        Object obj = sec.get(key);
        if (!(obj instanceof List<?> list)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String s = item.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String envOr(String envKey, String fallback) {
        String val = System.getenv(envKey);
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private static String readString(Map<String, Object> root, String section, String key, String def) {
        Object secObj = root.get(section);
        if (!(secObj instanceof Map<?, ?> sec)) return def;
        Object obj = sec.get(key);
        if (obj == null) return def;
        String s = obj.toString().trim();
        return s.isEmpty() ? def : s;
    }

    private static BotConfig defaults() {
        return new BotConfig(
                Collections.emptyList(),
                Collections.emptyList(),
                "jdbc:postgresql://127.0.0.1:5432/bchgrp",
                "bchgrp",
                ""
        );
    }
}
