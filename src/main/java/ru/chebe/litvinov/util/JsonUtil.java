package ru.chebe.litvinov.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Утилита для JSON-сериализации и десериализации объектов.
 * Используется для хранения коллекций (List, Map) в строковых колонках таблиц Ignite 3.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {}

    /**
     * Сериализует объект в JSON-строку.
     *
     * @param value объект для сериализации
     * @return JSON-строка или "{}" при ошибке
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            return "{}";
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
            return new ArrayList<>();
        }
    }
}
