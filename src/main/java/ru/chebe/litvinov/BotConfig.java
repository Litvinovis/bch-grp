package ru.chebe.litvinov;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BotConfig {
    private final List<String> allowedChannelIds;

    public BotConfig(List<String> allowedChannelIds) {
        this.allowedChannelIds = allowedChannelIds;
    }

    public List<String> getAllowedChannelIds() {
        return allowedChannelIds;
    }

    @SuppressWarnings("unchecked")
    public static BotConfig load() {
        try (InputStream is = BotConfig.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) {
                return new BotConfig(Collections.emptyList());
            }
            Map<String, Object> root = new Yaml().load(is);
            if (root == null) {
                return new BotConfig(Collections.emptyList());
            }
            Object discordObj = root.get("discord");
            if (!(discordObj instanceof Map<?, ?> discordMap)) {
                return new BotConfig(Collections.emptyList());
            }
            Object idsObj = discordMap.get("allowed-channel-ids");
            if (!(idsObj instanceof List<?> ids)) {
                return new BotConfig(Collections.emptyList());
            }
            List<String> out = new ArrayList<>();
            for (Object id : ids) {
                if (id != null) {
                    String s = id.toString().trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
            return new BotConfig(out);
        } catch (Exception e) {
            return new BotConfig(Collections.emptyList());
        }
    }
}
