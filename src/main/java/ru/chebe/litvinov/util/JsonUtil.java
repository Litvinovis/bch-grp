package ru.chebe.litvinov.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Утилита для JSON-сериализации и десериализации объектов.
 * Используется для хранения коллекций (List, Map) в текстовых колонках PostgreSQL.
 */
public final class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {}

    /**
     * Сериализует объект в JSON-строку.
     *
     * @param value объект для сериализации
     * @return JSON-строка
     * @throws IllegalStateException если объект не сериализуется — подстановка "{}"
     *         молча стирала инвентарь, навыки и прочие коллекции игрока
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сериализовать данные игрока в JSON", e);
        }
    }

    /**
     * Десериализует JSON-строку в Map&lt;String, Integer&gt;.
     *
     * @param json JSON-строка
     * @return десериализованная Map или пустая Map при ошибке/null
     */
    public static Map<String, Integer> fromJsonToMapStringInt(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (IOException e) {
            log.warn("Повреждённый JSON в данных игрока, прочитан как пустая коллекция: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * Десериализует JSON-строку в Map&lt;String, Long&gt;.
     *
     * @param json JSON-строка
     * @return десериализованная Map или пустая Map при ошибке/null
     */
    public static Map<String, Long> fromJsonToMapStringLong(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Long>>() {});
        } catch (IOException e) {
            log.warn("Повреждённый JSON в данных игрока, прочитан как пустая коллекция: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * Десериализует JSON-строку в List&lt;String&gt;.
     *
     * @param json JSON-строка
     * @return десериализованный List или пустой List при ошибке/null
     */
    public static List<String> fromJsonToListString(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.warn("Повреждённый JSON в данных игрока, прочитан как пустая коллекция: {}", json, e);
            return new ArrayList<>();
        }
    }

    public static Map<String, String> fromJsonToMapStringString(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            log.warn("Повреждённый JSON в данных игрока, прочитан как пустая коллекция: {}", json, e);
            return new HashMap<>();
        }
    }
}
