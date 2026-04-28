package ru.chebe.litvinov.ignite3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.util.JsonUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

public class ClanRepository {

    private static final Logger log = LoggerFactory.getLogger(ClanRepository.class);
    private final DataSource dataSource;

    public ClanRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Clan get(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, leader_id, members, appliers FROM clans WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) { log.warn("Ошибка get({}): {}", name, e.getMessage()); }
        return null;
    }

    public boolean contains(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM clans WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) { log.warn("Ошибка contains({}): {}", name, e.getMessage()); }
        return false;
    }

    public void put(String name, Clan clan) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO clans (name, leader_id, members, appliers) VALUES (?,?,?,?) " +
                 "ON CONFLICT (name) DO UPDATE SET leader_id=EXCLUDED.leader_id, " +
                 "members=EXCLUDED.members, appliers=EXCLUDED.appliers")) {
            ps.setString(1, name); ps.setString(2, clan.getLeaderId());
            ps.setString(3, JsonUtil.toJson(clan.getMembers()));
            ps.setString(4, JsonUtil.toJson(clan.getAppliers()));
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка put({}): {}", name, e.getMessage()); }
    }

    public void remove(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM clans WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка remove({}): {}", name, e.getMessage()); }
    }

    private Clan mapRow(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        String leaderId = rs.getString("leader_id");
        List<String> members = JsonUtil.fromJsonToListString(rs.getString("members"));
        List<String> appliers = JsonUtil.fromJsonToListString(rs.getString("appliers"));
        Clan clan = new Clan(name, leaderId);
        clan.getMembers().clear();
        clan.getMembers().addAll(members);
        clan.getAppliers().clear();
        clan.getAppliers().addAll(appliers);
        return clan;
    }
}
