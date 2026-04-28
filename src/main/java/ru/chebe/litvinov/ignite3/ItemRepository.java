package ru.chebe.litvinov.ignite3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Item;

import javax.sql.DataSource;
import java.sql.*;

public class ItemRepository {

    private static final Logger log = LoggerFactory.getLogger(ItemRepository.class);
    private final DataSource dataSource;

    public ItemRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Item get(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, description, price, luck, strength, health, armor, reputation, " +
                 "xp_generation, quantity, expire_time, action FROM items WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) { log.warn("Ошибка get({}): {}", name, e.getMessage()); }
        return null;
    }

    public boolean contains(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM items WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) { log.warn("Ошибка contains({}): {}", name, e.getMessage()); }
        return false;
    }

    public void put(String name, Item item) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO items (name, description, price, luck, strength, health, armor, reputation, xp_generation, quantity, expire_time, action) " +
                 "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (name) DO UPDATE SET description=EXCLUDED.description, price=EXCLUDED.price, " +
                 "luck=EXCLUDED.luck, strength=EXCLUDED.strength, health=EXCLUDED.health, armor=EXCLUDED.armor, " +
                 "reputation=EXCLUDED.reputation, xp_generation=EXCLUDED.xp_generation, " +
                 "quantity=EXCLUDED.quantity, expire_time=EXCLUDED.expire_time, action=EXCLUDED.action")) {
            ps.setString(1, name); ps.setString(2, item.getDescription()); ps.setInt(3, item.getPrice());
            ps.setInt(4, item.getLuck()); ps.setInt(5, item.getStrength()); ps.setInt(6, item.getHealth());
            ps.setInt(7, item.getArmor()); ps.setInt(8, item.getReputation()); ps.setInt(9, item.getXpGeneration());
            ps.setInt(10, item.getQuantity()); ps.setLong(11, item.getExpireTime()); ps.setBoolean(12, item.isAction());
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка put({}): {}", name, e.getMessage()); }
    }

    private Item mapRow(ResultSet rs) throws SQLException {
        return Item.builder()
                .name(rs.getString("name")).description(rs.getString("description"))
                .price(rs.getInt("price")).luck(rs.getInt("luck")).strength(rs.getInt("strength"))
                .health(rs.getInt("health")).armor(rs.getInt("armor")).reputation(rs.getInt("reputation"))
                .xpGeneration(rs.getInt("xp_generation")).quantity(rs.getInt("quantity"))
                .expireTime(rs.getLong("expire_time")).action(rs.getBoolean("action")).build();
    }
}
