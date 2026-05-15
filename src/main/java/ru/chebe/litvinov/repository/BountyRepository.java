package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий для системы наград за голову (bounty).
 */
public class BountyRepository {

    private static final Logger log = LoggerFactory.getLogger(BountyRepository.class);
    private final DataSource dataSource;

    public BountyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Bounty(int id, String targetId, String placerId, int reward, boolean active, long createdAt) {}

    public void place(String targetId, String placerId, int reward) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO bounties (target_id, placer_id, reward, active, created_at) VALUES (?,?,?,TRUE,?)")) {
            ps.setString(1, targetId);
            ps.setString(2, placerId);
            ps.setInt(3, reward);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Ошибка place bounty: {}", e.getMessage());
        }
    }

    public List<Bounty> getActive() {
        List<Bounty> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, target_id, placer_id, reward, active, created_at FROM bounties WHERE active = TRUE ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Bounty(rs.getInt("id"), rs.getString("target_id"), rs.getString("placer_id"),
                    rs.getInt("reward"), rs.getBoolean("active"), rs.getLong("created_at")));
            }
        } catch (Exception e) {
            log.warn("Ошибка getActive bounties: {}", e.getMessage());
        }
        return list;
    }

    public int claimAndGetReward(String targetId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement sel = conn.prepareStatement(
                     "SELECT id, reward FROM bounties WHERE target_id = ? AND active = TRUE LIMIT 1");
                 PreparedStatement upd = conn.prepareStatement(
                     "UPDATE bounties SET active = FALSE WHERE id = ?")) {
                sel.setString(1, targetId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        int reward = rs.getInt("reward");
                        upd.setInt(1, id);
                        upd.executeUpdate();
                        conn.commit();
                        return reward;
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.warn("Ошибка claimAndGetReward: {}", e.getMessage());
        }
        return 0;
    }
}
