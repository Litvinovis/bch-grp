package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.util.JsonUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий для турниров.
 */
public class TournamentRepository {

    private static final Logger log = LoggerFactory.getLogger(TournamentRepository.class);
    private final DataSource dataSource;

    public TournamentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Tournament(int id, String type, List<String> participants, String bracket, String status, int season) {}

    public int createTournament(String type, List<String> participants) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO tournaments (type, participants, bracket, status, season) VALUES (?,?,'{}','open',1) RETURNING id")) {
            ps.setString(1, type);
            ps.setString(2, JsonUtil.toJson(participants));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.warn("Ошибка createTournament: {}", e.getMessage());
        }
        return -1;
    }

    public Tournament getActive(String type) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, type, participants, bracket, status, season FROM tournaments WHERE type = ? AND status != 'finished' ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    List<String> parts = JsonUtil.fromJsonToListString(rs.getString("participants"));
                    return new Tournament(rs.getInt("id"), rs.getString("type"), parts,
                        rs.getString("bracket"), rs.getString("status"), rs.getInt("season"));
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка getActive tournament: {}", e.getMessage());
        }
        return null;
    }

    public void addParticipant(int tournamentId, String playerId) {
        try (Connection conn = dataSource.getConnection()) {
            Tournament t = getById(conn, tournamentId);
            if (t == null) return;
            List<String> parts = new ArrayList<>(t.participants());
            if (!parts.contains(playerId)) {
                parts.add(playerId);
                try (PreparedStatement ps = conn.prepareStatement(
                         "UPDATE tournaments SET participants = ? WHERE id = ?")) {
                    ps.setString(1, JsonUtil.toJson(parts));
                    ps.setInt(2, tournamentId);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка addParticipant: {}", e.getMessage());
        }
    }

    public void updateStatus(int id, String status) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE tournaments SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Ошибка updateStatus tournament: {}", e.getMessage());
        }
    }

    private Tournament getById(Connection conn, int id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, type, participants, bracket, status, season FROM tournaments WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    List<String> parts = JsonUtil.fromJsonToListString(rs.getString("participants"));
                    return new Tournament(rs.getInt("id"), rs.getString("type"), parts,
                        rs.getString("bracket"), rs.getString("status"), rs.getInt("season"));
                }
            }
        }
        return null;
    }
}
