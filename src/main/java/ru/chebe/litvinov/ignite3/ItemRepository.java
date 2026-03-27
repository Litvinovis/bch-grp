package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import ru.chebe.litvinov.data.Item;

/**
 * Репозиторий предметов для Ignite 3.
 */
public class ItemRepository {

    private final KeyValueView<Tuple, Tuple> view;

    /**
     * Создаёт репозиторий.
     *
     * @param client подключённый Ignite 3 thin client
     */
    public ItemRepository(IgniteClient client) {
        this.view = client.tables().table("items").keyValueView();
    }

    /**
     * Возвращает предмет по названию или null если не найден.
     *
     * @param name название предмета
     * @return объект Item или null
     */
    public Item get(String name) {
        Tuple key = Tuple.create().set("name", name);
        Tuple row = view.get(null, key);
        if (row == null) return null;
        return rowToItem(row);
    }

    /**
     * Сохраняет или обновляет предмет.
     *
     * @param name название предмета
     * @param item объект Item
     */
    public void put(String name, Item item) {
        Tuple key = Tuple.create().set("name", name);
        Tuple val = itemToRow(item);
        view.put(null, key, val);
    }

    /**
     * Проверяет наличие предмета в таблице.
     *
     * @param name название предмета
     * @return true если предмет существует
     */
    public boolean contains(String name) {
        Tuple key = Tuple.create().set("name", name);
        return view.contains(null, key);
    }

    // ---- маппинг ----

    private Item rowToItem(Tuple row) {
        return Item.builder()
                .name(row.stringValue("name"))
                .description(row.stringValue("description"))
                .price(row.intValue("price"))
                .luck(row.intValue("luck"))
                .strength(row.intValue("strength"))
                .health(row.intValue("health"))
                .armor(row.intValue("armor"))
                .reputation(row.intValue("reputation"))
                .xpGeneration(row.intValue("xp_generation"))
                .quantity(row.intValue("quantity"))
                .expireTime(row.longValue("expire_time"))
                .action(row.booleanValue("action"))
                .build();
    }

    private Tuple itemToRow(Item item) {
        return Tuple.create()
                .set("description", item.getDescription())
                .set("price", item.getPrice())
                .set("luck", item.getLuck())
                .set("strength", item.getStrength())
                .set("health", item.getHealth())
                .set("armor", item.getArmor())
                .set("reputation", item.getReputation())
                .set("xp_generation", item.getXpGeneration())
                .set("quantity", item.getQuantity())
                .set("expire_time", item.getExpireTime())
                .set("action", item.isAction());
    }
}
