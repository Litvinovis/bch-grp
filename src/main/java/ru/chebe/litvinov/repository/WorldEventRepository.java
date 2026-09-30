package ru.chebe.litvinov.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

/**
 * Мировые события в таблице world_events. Мировой босс жил только в памяти и пропадал при
 * каждом рестарте бота (30.09.2026: объявлен, после деплоя «+мировой босс» отвечал «не
 * активен»), а таймер спавна отсчитывался заново от запуска.
 */
public class WorldEventRepository {

    private static final Logger log = LoggerFactory.getLogger(WorldEventRepository.class);
    static final String WORLD_BOSS = "world_boss";

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorldEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record BossState(long id, String name, String location, int hp, long startedAt, long endsAt) {}

    /** Живой босс: active и срок не вышел. */
    public Optional<BossState> activeBoss(long now) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, data, started_at, ends_at FROM world_events " +
                 "WHERE event_type = ? AND active = TRUE AND ends_at > ? ORDER BY started_at DESC LIMIT 1")) {
            ps.setString(1, WORLD_BOSS);
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Map<?, ?> data = mapper.readValue(rs.getString("data"), Map.class);
                return Optional.of(new BossState(rs.getLong("id"), String.valueOf(data.get("name")),
                    String.valueOf(data.get("location")), ((Number) data.get("hp")).intValue(),
                    rs.getLong("started_at"), rs.getLong("ends_at")));
            }
        } catch (Exception e) {
            log.error("Ошибка чтения мирового босса", e);
            return Optional.empty();
        }
    }

    /** Когда появлялся последний босс (мс), 0 — ещё ни разу. */
    public long lastBossSpawnAt() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COALESCE(MAX(started_at), 0) FROM world_events WHERE event_type = ?")) {
            ps.setString(1, WORLD_BOSS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            log.error("Ошибка чтения времени спавна мирового босса", e);
            // Не знаем — считаем, что босс был только что: лучше пропустить спавн, чем заспамить
            return System.currentTimeMillis();
        }
    }

    /** Новый босс; возвращает id строки или -1. Прежние боссы при этом закрываются. */
    public long spawnBoss(String name, String location, int hp, long startedAt, long endsAt) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement close = conn.prepareStatement(
                     "UPDATE world_events SET active = FALSE WHERE event_type = ? AND active = TRUE");
                 PreparedStatement ins = conn.prepareStatement(
                     "INSERT INTO world_events (event_type, data, started_at, ends_at, active) VALUES (?,?,?,?,TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
                close.setString(1, WORLD_BOSS);
                close.executeUpdate();
                ins.setString(1, WORLD_BOSS);
                ins.setString(2, mapper.writeValueAsString(Map.of("name", name, "location", location, "hp", hp)));
                ins.setLong(3, startedAt);
                ins.setLong(4, endsAt);
                ins.executeUpdate();
                long id = -1;
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    if (keys.next()) id = keys.getLong(1);
                }
                conn.commit();
                return id;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Ошибка сохранения мирового босса", e);
            return -1;
        }
    }

    /** HP после удара; при hp ≤ 0 босс закрывается. */
    public void updateBossHp(long id, String name, String location, int hp) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE world_events SET data = ?, active = ? WHERE id = ?")) {
            ps.setString(1, mapper.writeValueAsString(Map.of("name", name, "location", location, "hp", Math.max(0, hp))));
            ps.setBoolean(2, hp > 0);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка обновления HP мирового босса", e);
        }
    }
}
