package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import ru.chebe.litvinov.Ignite3Configurator;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.util.JsonUtil;

import java.util.List;

/**
 * Репозиторий локаций для Ignite 3.
 * Маппит Location (с List-полями paths/populationByName/populationById) через JSON.
 * Получает клиент через {@link Ignite3Configurator} — view автоматически сбрасывается
 * при смене клиента после переподключения.
 */
public class LocationRepository {

    private static final String TABLE = "locations";

    private final Ignite3Configurator configurator;
    private volatile IgniteClient lastClient;
    private volatile KeyValueView<Tuple, Tuple> view;

    /**
     * Создаёт репозиторий.
     *
     * @param configurator менеджер подключения Ignite 3
     */
    public LocationRepository(Ignite3Configurator configurator) {
        this.configurator = configurator;
    }

    private KeyValueView<Tuple, Tuple> view() {
        IgniteClient current = configurator.getClient();
        if (view == null || current != lastClient) {
            synchronized (this) {
                current = configurator.getClient();
                if (view == null || current != lastClient) {
                    view = current.tables().table(TABLE).keyValueView();
                    lastClient = current;
                }
            }
        }
        return view;
    }

    /**
     * Возвращает локацию по названию или null если не найдена.
     *
     * @param name название локации
     * @return объект Location или null
     */
    public Location get(String name) {
        Tuple key = Tuple.create().set("name", name);
        Tuple row = view().get(null, key);
        if (row == null) return null;
        return rowToLocation(row, name);
    }

    /**
     * Проверяет наличие локации в таблице.
     *
     * @param name название локации
     * @return true если локация существует
     */
    public boolean contains(String name) {
        Tuple key = Tuple.create().set("name", name);
        return view().contains(null, key);
    }

    /**
     * Сохраняет или обновляет локацию.
     *
     * @param name     название локации
     * @param location объект Location
     */
    public void put(String name, Location location) {
        Tuple key = Tuple.create().set("name", name);
        Tuple val = locationToRow(location);
        view().put(null, key, val);
    }

    // ---- маппинг ----

    private Location rowToLocation(Tuple row, String name) {
        List<String> paths = JsonUtil.fromJsonToListString(row.stringValue("paths"));
        List<String> popByName = JsonUtil.fromJsonToListString(row.stringValue("population_by_name"));
        List<String> popById = JsonUtil.fromJsonToListString(row.stringValue("population_by_id"));

        return Location.builder()
                .name(name)
                .dangerous(row.intValue("dangerous"))
                .paths(paths)
                .populationByName(popByName)
                .populationById(popById)
                .pvp(row.booleanValue("pvp"))
                .boss(row.stringValue("boss"))
                .bossItem(row.stringValue("boss_item"))
                .teleport(row.booleanValue("teleport"))
                .build();
    }

    private Tuple locationToRow(Location loc) {
        return Tuple.create()
                .set("dangerous", loc.getDangerous())
                .set("paths", JsonUtil.toJson(loc.getPaths()))
                .set("population_by_name", JsonUtil.toJson(loc.getPopulationByName()))
                .set("population_by_id", JsonUtil.toJson(loc.getPopulationById()))
                .set("pvp", loc.isPvp())
                .set("boss", loc.getBoss())
                .set("boss_item", loc.getBossItem())
                .set("teleport", loc.isTeleport());
    }
}
