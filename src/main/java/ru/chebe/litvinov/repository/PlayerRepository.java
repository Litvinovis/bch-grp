package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.util.JsonUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerRepository.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final DataSource dataSource;

    public PlayerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Player> getAll() {
        List<Player> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, location, level, " +
                 "player_exp, exp_to_next, inventory, answer, active_event, daily_time, clan_name, daily_streak, player_class, achievements FROM players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.warn("Ошибка getAll(): {}", e.getMessage());
        }
        return result;
    }

    public Player get(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, location, level, " +
                 "player_exp, exp_to_next, inventory, answer, active_event, daily_time, clan_name, daily_streak, player_class, achievements FROM players WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.warn("Ошибка get({}): {}", id, e.getMessage());
        }
        return null;
    }

    public boolean contains(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM players WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) {
            log.warn("Ошибка contains({}): {}", id, e.getMessage());
        }
        return false;
    }

    public void put(String id, Player player) {
        String eventJson = serializeEvent(player);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO players (id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, location, level, " +
                 "player_exp, exp_to_next, inventory, answer, active_event, daily_time, clan_name, daily_streak, player_class, achievements) " +
                 "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET " +
                 "nick_name=EXCLUDED.nick_name, hp=EXCLUDED.hp, max_hp=EXCLUDED.max_hp, luck=EXCLUDED.luck, " +
                 "money=EXCLUDED.money, reputation=EXCLUDED.reputation, armor=EXCLUDED.armor, strength=EXCLUDED.strength, " +
                 "location=EXCLUDED.location, level=EXCLUDED.level, player_exp=EXCLUDED.player_exp, exp_to_next=EXCLUDED.exp_to_next, " +
                 "inventory=EXCLUDED.inventory, answer=EXCLUDED.answer, active_event=EXCLUDED.active_event, " +
                 "daily_time=EXCLUDED.daily_time, clan_name=EXCLUDED.clan_name, daily_streak=EXCLUDED.daily_streak, " +
                 "player_class=EXCLUDED.player_class, achievements=EXCLUDED.achievements")) {
            ps.setString(1, id);
            ps.setString(2, player.getNickName());
            ps.setInt(3, player.getHp());
            ps.setInt(4, player.getMaxHp());
            ps.setInt(5, player.getLuck());
            ps.setInt(6, player.getMoney());
            ps.setInt(7, player.getReputation());
            ps.setInt(8, player.getArmor());
            ps.setInt(9, player.getStrength());
            ps.setString(10, player.getLocation());
            ps.setInt(11, player.getLevel());
            ps.setInt(12, player.getExp());
            ps.setInt(13, player.getExpToNextLvl());
            ps.setString(14, JsonUtil.toJson(player.getInventory()));
            ps.setString(15, player.getAnswer() != null ? player.getAnswer() : "");
            ps.setString(16, eventJson);
            ps.setLong(17, player.getDailyTime());
            ps.setString(18, player.getClanName() != null ? player.getClanName() : "");
            ps.setInt(19, player.getDailyStreak());
            ps.setString(20, player.getPlayerClass() != null ? player.getPlayerClass() : "");
            ps.setString(21, JsonUtil.toJson(player.getAchievements() != null ? player.getAchievements() : new ArrayList<>()));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка put({}): {}", id, e.getMessage());
        }
    }

    public void remove(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM players WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка remove({}): {}", id, e.getMessage());
        }
    }

    private Player mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String nickName = rs.getString("nick_name");
        Player p = new Player(nickName, id);
        p.setHp(rs.getInt("hp"));
        p.setMaxHp(rs.getInt("max_hp"));
        p.setLuck(rs.getInt("luck"));
        p.setMoney(rs.getInt("money"));
        p.setReputation(rs.getInt("reputation"));
        p.setArmor(rs.getInt("armor"));
        p.setStrength(rs.getInt("strength"));
        p.setLocation(rs.getString("location"));
        p.setLevel(rs.getInt("level"));
        p.setExp(rs.getInt("player_exp"));
        p.setExpToNextLvl(rs.getInt("exp_to_next"));
        p.setAnswer(rs.getString("answer") != null ? rs.getString("answer") : "");
        p.setDailyTime(rs.getLong("daily_time"));
        p.setClanName(rs.getString("clan_name") != null ? rs.getString("clan_name") : "");
        p.setDailyStreak(rs.getInt("daily_streak"));
        p.setPlayerClass(rs.getString("player_class") != null ? rs.getString("player_class") : "");
        p.setAchievements(JsonUtil.fromJsonToListString(rs.getString("achievements")));
        p.setInventory(JsonUtil.fromJsonToMapStringInt(rs.getString("inventory")));

        String eventJson = rs.getString("active_event");
        if (eventJson != null && !eventJson.isBlank() && !eventJson.equals("null")) {
            try {
                p.setActiveEvent(MAPPER.readValue(eventJson, Event.class));
            } catch (Exception e) {
                log.warn("Не удалось десериализовать activeEvent для игрока {}: {}", id, e.getMessage());
                p.setActiveEvent(null);
            }
        }
        return p;
    }

    private String serializeEvent(Player player) {
        if (player.getActiveEvent() == null) return null;
        try {
            return MAPPER.writeValueAsString(player.getActiveEvent());
        } catch (Exception e) {
            log.warn("Не удалось сериализовать activeEvent для игрока {}: {}", player.getId(), e.getMessage());
            return null;
        }
    }
}
