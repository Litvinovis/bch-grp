package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Boss;

import javax.sql.DataSource;
import java.sql.*;

public class BossRepository {

    private static final Logger log = LoggerFactory.getLogger(BossRepository.class);
    private final DataSource dataSource;

    public BossRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Boss get(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT nick_name, hp, strength, armor, boss_item, defeat, win FROM bosses WHERE nick_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) { log.error("Ошибка get({})", name, e); }
        return null;
    }

    public boolean contains(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM bosses WHERE nick_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) { log.error("Ошибка contains({})", name, e); }
        return false;
    }

    public void put(String name, Boss boss) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO bosses (nick_name, hp, strength, armor, boss_item, defeat, win) VALUES (?,?,?,?,?,?,?) " +
                 "ON CONFLICT (nick_name) DO UPDATE SET hp=EXCLUDED.hp, strength=EXCLUDED.strength, " +
                 "armor=EXCLUDED.armor, boss_item=EXCLUDED.boss_item, defeat=EXCLUDED.defeat, win=EXCLUDED.win")) {
            ps.setString(1, name); ps.setInt(2, boss.getHp()); ps.setInt(3, boss.getStrength());
            ps.setInt(4, boss.getArmor()); ps.setString(5, boss.getBossItem());
            ps.setInt(6, boss.getDefeat()); ps.setInt(7, boss.getWin());
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка put({})", name, e); }
    }

    private Boss mapRow(ResultSet rs) throws SQLException {
        return Boss.builder().nickName(rs.getString("nick_name")).hp(rs.getInt("hp"))
                .strength(rs.getInt("strength")).armor(rs.getInt("armor"))
                .bossItem(rs.getString("boss_item")).defeat(rs.getInt("defeat")).win(rs.getInt("win")).build();
    }
}
