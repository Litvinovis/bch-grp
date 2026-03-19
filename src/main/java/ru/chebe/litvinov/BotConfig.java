package ru.chebe.litvinov;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BotConfig {
    private final List<String> allowedChannelIds;
    private final List<String> adminIds;
    private final String igniteLocalAddress;
    private final List<String> igniteDiscoveryAddresses;
    private final String igniteWorkDir;

    public BotConfig(List<String> allowedChannelIds, List<String> adminIds, String igniteLocalAddress, List<String> igniteDiscoveryAddresses, String igniteWorkDir) {
        this.allowedChannelIds = allowedChannelIds;
        this.adminIds = adminIds;
        this.igniteLocalAddress = igniteLocalAddress;
        this.igniteDiscoveryAddresses = igniteDiscoveryAddresses;
        this.igniteWorkDir = igniteWorkDir;
    }

    public List<String> getAllowedChannelIds() {
        return allowedChannelIds;
    }

    public List<String> getAdminIds() {
        return adminIds;
    }

    public String getIgniteLocalAddress() {
        return igniteLocalAddress;
    }

    public List<String> getIgniteDiscoveryAddresses() {
        return igniteDiscoveryAddresses;
    }

    public String getIgniteWorkDir() {
        return igniteWorkDir;
    }

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
            String igniteLocal = readString(root, "ignite", "local-address", "192.168.1.120");
            List<String> igniteDiscovery = readStringList(root, "ignite", "discovery-addresses");
            if (igniteDiscovery.isEmpty()) {
                igniteDiscovery = List.of("192.168.1.120:47650..47659");
            }
            String igniteWorkDir = readString(root, "ignite", "work-dir", "/tmp/ignite-bch-client");

            return new BotConfig(channelIds, adminIds, igniteLocal, igniteDiscovery, igniteWorkDir);
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
        return new BotConfig(Collections.emptyList(), Collections.emptyList(), "192.168.1.120", List.of("192.168.1.120:47650..47659"), "/tmp/ignite-bch-client");
    }
}

