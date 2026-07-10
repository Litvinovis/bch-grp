package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.util.JsonUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ClanRepository {

    private static final Logger log = LoggerFactory.getLogger(ClanRepository.class);
    private final DataSource dataSource;

    public ClanRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Clan get(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, leader_id, members, appliers, clan_bank, clan_upgrades, clan_base, clan_roles FROM clans WHERE name = ?")) {
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
                 "INSERT INTO clans (name, leader_id, members, appliers, clan_bank, clan_upgrades, clan_base, clan_roles) VALUES (?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (name) DO UPDATE SET leader_id=EXCLUDED.leader_id, " +
                 "members=EXCLUDED.members, appliers=EXCLUDED.appliers, " +
                 "clan_bank=EXCLUDED.clan_bank, clan_upgrades=EXCLUDED.clan_upgrades, " +
                 "clan_base=EXCLUDED.clan_base, clan_roles=EXCLUDED.clan_roles")) {
            ps.setString(1, name);
            ps.setString(2, clan.getLeaderId());
            ps.setString(3, JsonUtil.toJson(clan.getMembers()));
            ps.setString(4, JsonUtil.toJson(clan.getAppliers()));
            ps.setString(5, JsonUtil.toJson(clan.getClanBank() != null ? clan.getClanBank() : new HashMap<>()));
            ps.setString(6, JsonUtil.toJson(clan.getClanUpgrades() != null ? clan.getClanUpgrades() : new ArrayList<>()));
            ps.setString(7, clan.getClanBase() != null ? clan.getClanBase() : "респаун");
            ps.setString(8, JsonUtil.toJson(clan.getClanRoles() != null ? clan.getClanRoles() : new HashMap<>()));
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка put({}): {}", name, e.getMessage()); }
    }

    /**
     * Атомарно переводит монеты между игроком и банком клана в одной транзакции.
     * amount > 0 — взнос игрока в банк, amount < 0 — выдача из банка игроку.
     * Строка клана блокируется (FOR UPDATE), списание с игрока — условное,
     * поэтому ни сбой между обновлениями, ни гонка не создают и не теряют деньги.
     *
     * @return true, если перевод выполнен; false — недостаточно средств или ошибка
     */
    public boolean bankTransfer(String clanName, String playerId, int amount) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String bankJson;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT clan_bank FROM clans WHERE name = ? FOR UPDATE")) {
                    ps.setString(1, clanName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        bankJson = rs.getString(1);
                    }
                }
                HashMap<String, Integer> bank = bankJson != null && !bankJson.isBlank()
                        ? new HashMap<>(JsonUtil.fromJsonToMapStringInt(bankJson)) : new HashMap<>();
                int newBalance = bank.getOrDefault("монеты", 0) + amount;
                if (newBalance < 0) { conn.rollback(); return false; }
                bank.put("монеты", newBalance);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE players SET money = money - ? WHERE id = ? AND money - ? >= 0")) {
                    ps.setInt(1, amount);
                    ps.setString(2, playerId);
                    ps.setInt(3, amount);
                    if (ps.executeUpdate() == 0) { conn.rollback(); return false; }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE clans SET clan_bank = ? WHERE name = ?")) {
                    ps.setString(1, JsonUtil.toJson(bank));
                    ps.setString(2, clanName);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                try { conn.rollback(); } catch (SQLException ignored) { }
                log.error("Ошибка bankTransfer({}, {}, {}): {}", clanName, playerId, amount, e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Ошибка bankTransfer({}, {}, {}): {}", clanName, playerId, amount, e.getMessage());
            return false;
        }
    }

    public void remove(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM clans WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка remove({}): {}", name, e.getMessage()); }
    }

    public List<Clan> getAll() {
        List<Clan> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, leader_id, members, appliers, clan_bank, clan_upgrades, clan_base, clan_roles FROM clans");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) { log.warn("Ошибка getAll(): {}", e.getMessage()); }
        return result;
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
        try {
            String bankJson = rs.getString("clan_bank");
            clan.setClanBank(bankJson != null ? JsonUtil.fromJsonToMapStringInt(bankJson) : new HashMap<>());
        } catch (Exception e) { clan.setClanBank(new HashMap<>()); }
        try {
            String upgradesJson = rs.getString("clan_upgrades");
            clan.setClanUpgrades(upgradesJson != null ? JsonUtil.fromJsonToListString(upgradesJson) : new ArrayList<>());
        } catch (Exception e) { clan.setClanUpgrades(new ArrayList<>()); }
        try {
            String base = rs.getString("clan_base");
            clan.setClanBase(base != null ? base : "респаун");
        } catch (Exception e) { clan.setClanBase("респаун"); }
        try {
            String rolesJson = rs.getString("clan_roles");
            clan.setClanRoles(rolesJson != null ? JsonUtil.fromJsonToMapStringString(rolesJson) : new HashMap<>());
        } catch (Exception e) { clan.setClanRoles(new HashMap<>()); }
        return clan;
    }
}
