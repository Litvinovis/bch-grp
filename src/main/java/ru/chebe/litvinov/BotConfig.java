package ru.chebe.litvinov;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация бота, загружаемая из файла application.yml.
 * Содержит параметры Discord-каналов, администраторов и настройки Apache Ignite 3.
 */
public record BotConfig(
        List<String> allowedChannelIds,
        List<String> adminIds,
        String ignite3Address
) {

    /**
     * Загружает конфигурацию из файла application.yml в classpath.
     * При отсутствии файла или ошибке чтения возвращает конфигурацию с параметрами по умолчанию.
     *
     * @return загруженный или дефолтный объект BotConfig
     */
    @SuppressWarnings("unchecked")
    public static BotConfig load() {
        try (InputStream is = BotConfig.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) {
                return defaults();
            }
            Map<String, Object> root = new Yaml().load(is);
            if (root == null) {
                return defaults();
            }

            List<String> channelIds = readStringList(root, "discord", "allowed-channel-ids");
            List<String> adminIds = readStringList(root, "discord", "admin-ids");
            String ignite3Addr = readString(root, "ignite3", "address", "127.0.0.1:10300");

            return new BotConfig(channelIds, adminIds, ignite3Addr);
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
    private static String readString(Map<String, Object> root, String section, String key, String def) {
        Object secObj = root.get(section);
        if (!(secObj instanceof Map<?, ?> sec)) return def;
        Object obj = sec.get(key);
        if (obj == null) return def;
        String s = obj.toString().trim();
        return s.isEmpty() ? def : s;
    }

    private static BotConfig defaults() {
        return new BotConfig(Collections.emptyList(), Collections.emptyList(), "127.0.0.1:10300");
    }
}
