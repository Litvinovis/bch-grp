package ru.chebe.litvinov.ignite3;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.Ignite3Configurator;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Репозиторий игроков для Ignite 3.
 * Маппит Player (с Map-инвентарём и Event-объектом) в/из строковых JSON-колонок таблицы.
 * Получает клиент через {@link Ignite3Configurator} — view автоматически сбрасывается
 * при смене клиента после переподключения.
 */
public class PlayerRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerRepository.class);
    private static final String TABLE = "players";

    private final Ignite3Configurator configurator;
    private volatile IgniteClient lastClient;
    private volatile KeyValueView<Tuple, Tuple> view;

    /**
     * Создаёт репозиторий.
     *
     * @param configurator менеджер подключения Ignite 3
     */
    public PlayerRepository(Ignite3Configurator configurator) {
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
     * Возвращает всех игроков из таблицы (для лидерборда).
     *
     * @return список всех игроков
     */
    public List<Player> getAll() {
        List<Player> result = new ArrayList<>();
        IgniteClient current = configurator.getClient();
        if (current == null) return result;
        try (ResultSet<SqlRow> rs = current.sql().execute(null, "SELECT id FROM players")) {
            while (rs.hasNext()) {
                SqlRow row = rs.next();
                String id = row.stringValue("id");
                Player p = get(id);
                if (p != null) result.add(p);
            }
        } catch (Exception e) {
            log.warn("Ошибка getAll(): {}", e.getMessage());
        }
        return result;
    }

    /**
     * Возвращает игрока по Discord ID или null если не найден.
     *
     * @param id Discord-идентификатор игрока
     * @return объект Player или null
     */
    public Player get(String id) {
        Tuple key = Tuple.create().set("id", id);
        Tuple row = view().get(null, key);
        if (row == null) return null;
        return rowToPlayer(row, id);
    }

    /**
     * Проверяет наличие игрока в таблице.
     *
     * @param id Discord-идентификатор игрока
     * @return true если игрок существует
     */
    public boolean contains(String id) {
        Tuple key = Tuple.create().set("id", id);
        return view().contains(null, key);
    }

    /**
     * Сохраняет или обновляет игрока.
     *
     * @param id     Discord-идентификатор игрока
     * @param player объект Player
     */
    public void put(String id, Player player) {
        Tuple key = Tuple.create().set("id", id);
        Tuple val = playerToRow(player);
        view().put(null, key, val);
    }

    /**
     * Удаляет игрока из таблицы.
     *
     * @param id Discord-идентификатор игрока
     */
    public void remove(String id) {
        Tuple key = Tuple.create().set("id", id);
        view().remove(null, key);
    }

    // ---- маппинг ----

    private Player rowToPlayer(Tuple row, String id) {
        String nickName = row.stringValue("nick_name");
        Player p = new Player(nickName, id);
        p.setHp(row.intValue("hp"));
        p.setMaxHp(row.intValue("max_hp"));
        p.setLuck(row.intValue("luck"));
        p.setMoney(row.intValue("money"));
        p.setReputation(row.intValue("reputation"));
        p.setArmor(row.intValue("armor"));
        p.setStrength(row.intValue("strength"));
        p.setLocation(row.stringValue("location"));
        p.setLevel(row.intValue("level"));
        p.setExp(row.intValue("\"exp\""));
        p.setExpToNextLvl(row.intValue("exp_to_next"));
        p.setAnswer(row.stringValue("answer") != null ? row.stringValue("answer") : "");
        p.setDailyTime(row.longValue("daily_time"));
        p.setClanName(row.stringValue("clan_name") != null ? row.stringValue("clan_name") : "");
        p.setDailyStreak(row.intValue("daily_streak"));
        p.setPlayerClass(row.stringValue("player_class") != null ? row.stringValue("player_class") : "");

        String achievementsJson = row.stringValue("achievements");
        List<String> achievements = JsonUtil.fromJsonToListString(achievementsJson);
        p.setAchievements(achievements);

        String inventoryJson = row.stringValue("inventory");
        Map<String, Integer> inventory = JsonUtil.fromJsonToMapStringInt(inventoryJson);
        p.setInventory(inventory);

        String eventJson = row.stringValue("active_event");
        if (eventJson != null && !eventJson.isBlank() && !eventJson.equals("null")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Event event = mapper.readValue(eventJson, Event.class);
                p.setActiveEvent(event);
            } catch (Exception e) {
                log.warn("Не удалось десериализовать activeEvent для игрока {}: {}", id, e.getMessage());
                p.setActiveEvent(null);
            }
        } else {
            p.setActiveEvent(null);
        }

        return p;
    }

    private Tuple playerToRow(Player p) {
        String inventoryJson = JsonUtil.toJson(p.getInventory());
        String eventJson = null;
        if (p.getActiveEvent() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                eventJson = mapper.writeValueAsString(p.getActiveEvent());
            } catch (Exception e) {
                log.warn("Не удалось сериализовать activeEvent для игрока {}: {}", p.getId(), e.getMessage());
            }
        }

        return Tuple.create()
                .set("nick_name", p.getNickName())
                .set("hp", p.getHp())
                .set("max_hp", p.getMaxHp())
                .set("luck", p.getLuck())
                .set("money", p.getMoney())
                .set("reputation", p.getReputation())
                .set("armor", p.getArmor())
                .set("strength", p.getStrength())
                .set("location", p.getLocation())
                .set("level", p.getLevel())
                .set("\"exp\"", p.getExp())
                .set("exp_to_next", p.getExpToNextLvl())
                .set("inventory", inventoryJson)
                .set("answer", p.getAnswer() != null ? p.getAnswer() : "")
                .set("active_event", eventJson)
                .set("daily_time", p.getDailyTime())
                .set("clan_name", p.getClanName() != null ? p.getClanName() : "")
                .set("daily_streak", p.getDailyStreak())
                .set("player_class", p.getPlayerClass() != null ? p.getPlayerClass() : "")
                .set("achievements", JsonUtil.toJson(p.getAchievements() != null ? p.getAchievements() : new ArrayList<>()));
    }
}
