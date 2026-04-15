package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import ru.chebe.litvinov.Ignite3Configurator;
import ru.chebe.litvinov.data.Boss;

/**
 * Репозиторий боссов для Ignite 3.
 * Получает клиент через {@link Ignite3Configurator} — view автоматически сбрасывается
 * при смене клиента после переподключения.
 */
public class BossRepository {

    private static final String TABLE = "bosses";

    private final Ignite3Configurator configurator;
    private volatile IgniteClient lastClient;
    private volatile KeyValueView<Tuple, Tuple> view;

    /**
     * Создаёт репозиторий.
     *
     * @param configurator менеджер подключения Ignite 3
     */
    public BossRepository(Ignite3Configurator configurator) {
        this.configurator = configurator;
    }

    private KeyValueView<Tuple, Tuple> view() {
        IgniteClient current = configurator.getClient();
        if (current == null) {
            throw new IllegalStateException("Ignite 3 недоступен — соединение ещё не установлено");
        }
        if (view == null || current != lastClient) {
            synchronized (this) {
                current = configurator.getClient();
                if (current == null) {
                    throw new IllegalStateException("Ignite 3 недоступен — соединение ещё не установлено");
                }
                if (view == null || current != lastClient) {
                    view = current.tables().table(TABLE).keyValueView();
                    lastClient = current;
                }
            }
        }
        return view;
    }

    /**
     * Возвращает босса по имени или null если не найден.
     *
     * @param name имя босса
     * @return объект Boss или null
     */
    public Boss get(String name) {
        Tuple key = Tuple.create().set("nick_name", name);
        Tuple row = view().get(null, key);
        if (row == null) return null;
        return rowToBoss(row, name);
    }

    /**
     * Проверяет наличие босса в таблице.
     *
     * @param name имя босса
     * @return true если босс существует
     */
    public boolean contains(String name) {
        Tuple key = Tuple.create().set("nick_name", name);
        return view().contains(null, key);
    }

    /**
     * Сохраняет или обновляет босса.
     *
     * @param name название (ключ)
     * @param boss объект Boss
     */
    public void put(String name, Boss boss) {
        Tuple key = Tuple.create().set("nick_name", name);
        Tuple val = bossToRow(boss);
        view().put(null, key, val);
    }

    // ---- маппинг ----

    private Boss rowToBoss(Tuple row, String name) {
        return Boss.builder()
                .nickName(name)
                .hp(row.intValue("hp"))
                .strength(row.intValue("strength"))
                .armor(row.intValue("armor"))
                .bossItem(row.stringValue("boss_item"))
                .defeat(row.intValue("defeat"))
                .win(row.intValue("win"))
                .build();
    }

    private Tuple bossToRow(Boss boss) {
        return Tuple.create()
                .set("hp", boss.getHp())
                .set("strength", boss.getStrength())
                .set("armor", boss.getArmor())
                .set("boss_item", boss.getBossItem())
                .set("defeat", boss.getDefeat())
                .set("win", boss.getWin());
    }
}
