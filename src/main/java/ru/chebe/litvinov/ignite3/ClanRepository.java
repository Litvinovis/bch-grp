package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.util.JsonUtil;

import java.util.List;

/**
 * Репозиторий кланов для Ignite 3.
 * Поля members и appliers хранятся как JSON-массивы строк.
 */
public class ClanRepository {

    private final KeyValueView<Tuple, Tuple> view;

    /**
     * Создаёт репозиторий.
     *
     * @param client подключённый Ignite 3 thin client
     */
    public ClanRepository(IgniteClient client) {
        this.view = client.tables().table("clans").keyValueView();
    }

    /**
     * Возвращает клан по названию или null если не найден.
     *
     * @param name название клана
     * @return объект Clan или null
     */
    public Clan get(String name) {
        Tuple key = Tuple.create().set("name", name);
        Tuple row = view.get(null, key);
        if (row == null) return null;
        return rowToClan(row);
    }

    /**
     * Проверяет наличие клана в таблице.
     *
     * @param name название клана
     * @return true если клан существует
     */
    public boolean contains(String name) {
        Tuple key = Tuple.create().set("name", name);
        return view.contains(null, key);
    }

    /**
     * Сохраняет или обновляет клан.
     *
     * @param name название клана
     * @param clan объект Clan
     */
    public void put(String name, Clan clan) {
        Tuple key = Tuple.create().set("name", name);
        Tuple val = clanToRow(clan);
        view.put(null, key, val);
    }

    /**
     * Удаляет клан.
     *
     * @param name название клана
     */
    public void remove(String name) {
        Tuple key = Tuple.create().set("name", name);
        view.remove(null, key);
    }

    // ---- маппинг ----

    private Clan rowToClan(Tuple row) {
        String name = row.stringValue("name");
        String leaderId = row.stringValue("leader_id");
        List<String> members = JsonUtil.fromJsonToListString(row.stringValue("members"));
        List<String> appliers = JsonUtil.fromJsonToListString(row.stringValue("appliers"));

        Clan clan = new Clan(name, leaderId);
        // перезаписываем members (конструктор добавляет leaderId)
        clan.getMembers().clear();
        clan.getMembers().addAll(members);
        clan.getAppliers().clear();
        clan.getAppliers().addAll(appliers);
        return clan;
    }

    private Tuple clanToRow(Clan clan) {
        return Tuple.create()
                .set("leader_id", clan.getLeaderId())
                .set("members", JsonUtil.toJson(clan.getMembers()))
                .set("appliers", JsonUtil.toJson(clan.getAppliers()));
    }
}
