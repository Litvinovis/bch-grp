package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import ru.chebe.litvinov.data.Idea;

import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий идей для Ignite 3.
 * Для scan-запросов (поиск по статусу) использует SQL через IgniteClient.sql().
 */
public class IdeaRepository {

    private final KeyValueView<Tuple, Tuple> view;
    private final IgniteClient client;

    /**
     * Создаёт репозиторий.
     *
     * @param client подключённый Ignite 3 thin client
     */
    public IdeaRepository(IgniteClient client) {
        this.client = client;
        this.view = client.tables().table("ideas").keyValueView();
    }

    /**
     * Возвращает идею по номеру или null если не найдена.
     *
     * @param id номер идеи
     * @return объект Idea или null
     */
    public Idea get(int id) {
        Tuple key = Tuple.create().set("id", id);
        Tuple row = view.get(null, key);
        if (row == null) return null;
        return rowToIdea(row, id);
    }

    /**
     * Сохраняет или обновляет идею.
     *
     * @param id   номер идеи
     * @param idea объект Idea
     */
    public void put(int id, Idea idea) {
        Tuple key = Tuple.create().set("id", id);
        Tuple val = ideaToRow(idea);
        view.put(null, key, val);
    }

    /**
     * Возвращает количество идей в таблице.
     *
     * @return количество записей
     */
    public int size() {
        try (var cursor = client.sql().execute(null, "SELECT COUNT(*) FROM ideas")) {
            if (cursor.hasNext()) {
                var row = cursor.next();
                return (int) row.longValue(0);
            }
        }
        return 0;
    }

    /**
     * Возвращает все идеи с указанным статусом.
     *
     * @param resolution статус для фильтрации
     * @return список идей
     */
    public List<Idea> findByResolution(String resolution) {
        List<Idea> result = new ArrayList<>();
        try (var cursor = client.sql().execute(null,
                "SELECT id, description, author, resolution FROM ideas WHERE resolution = ?", resolution)) {
            while (cursor.hasNext()) {
                var row = cursor.next();
                result.add(Idea.builder()
                        .id(row.intValue("id"))
                        .description(row.stringValue("description"))
                        .author(row.stringValue("author"))
                        .resolution(row.stringValue("resolution"))
                        .build());
            }
        }
        return result;
    }

    /**
     * Возвращает все идеи.
     *
     * @return список всех идей
     */
    public List<Idea> findAll() {
        List<Idea> result = new ArrayList<>();
        try (var cursor = client.sql().execute(null,
                "SELECT id, description, author, resolution FROM ideas")) {
            while (cursor.hasNext()) {
                var row = cursor.next();
                result.add(Idea.builder()
                        .id(row.intValue("id"))
                        .description(row.stringValue("description"))
                        .author(row.stringValue("author"))
                        .resolution(row.stringValue("resolution"))
                        .build());
            }
        }
        return result;
    }

    // ---- маппинг ----

    private Idea rowToIdea(Tuple row, int id) {
        return Idea.builder()
                .id(id)
                .description(row.stringValue("description"))
                .author(row.stringValue("author"))
                .resolution(row.stringValue("resolution"))
                .build();
    }

    private Tuple ideaToRow(Idea idea) {
        return Tuple.create()
                .set("description", idea.getDescription())
                .set("author", idea.getAuthor())
                .set("resolution", idea.getResolution());
    }
}
