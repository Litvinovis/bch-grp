package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Репозиторий для постоянного хранения игровых событий.
 */
public class GameEventLogRepository {

    private static final Logger log = LoggerFactory.getLogger(GameEventLogRepository.class);
    private final DataSource dataSource;

    public GameEventLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Записывает игровое событие в базу данных.
     *
     * @param type     тип события (например "BATTLE_WIN", "QUEST_COMPLETE")
     * @param playerId идентификатор игрока
     * @param details  подробности события
     */
    public void log(String type, String playerId, String details) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO game_event_log (event_type, player_id, details, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, type);
            ps.setString(2, playerId);
            ps.setString(3, details);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Ошибка записи game_event_log: {}", e.getMessage());
        }
    }
}
