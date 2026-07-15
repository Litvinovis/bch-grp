package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.util.JsonUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

public class LocationRepository {

    private static final Logger log = LoggerFactory.getLogger(LocationRepository.class);
    private final DataSource dataSource;

    public LocationRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Location get(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, dangerous, population_by_name, population_by_id, paths, pvp, boss, boss_item, teleport FROM locations WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) { log.error("Ошибка get({})", name, e); }
        return null;
    }

    public boolean contains(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM locations WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) { log.error("Ошибка contains({})", name, e); }
        return false;
    }

    public void put(String name, Location loc) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO locations (name, dangerous, population_by_name, population_by_id, paths, pvp, boss, boss_item, teleport) " +
                 "VALUES (?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (name) DO UPDATE SET dangerous=EXCLUDED.dangerous, " +
                 "population_by_name=EXCLUDED.population_by_name, population_by_id=EXCLUDED.population_by_id, " +
                 "paths=EXCLUDED.paths, pvp=EXCLUDED.pvp, boss=EXCLUDED.boss, boss_item=EXCLUDED.boss_item, teleport=EXCLUDED.teleport")) {
            ps.setString(1, name); ps.setInt(2, loc.getDangerous());
            ps.setString(3, JsonUtil.toJson(loc.getPopulationByName()));
            ps.setString(4, JsonUtil.toJson(loc.getPopulationById()));
            ps.setString(5, JsonUtil.toJson(loc.getPaths()));
            ps.setBoolean(6, loc.isPvp()); ps.setString(7, loc.getBoss());
            ps.setString(8, loc.getBossItem()); ps.setBoolean(9, loc.isTeleport());
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка put({})", name, e); }
    }

    private Location mapRow(ResultSet rs) throws SQLException {
        List<String> paths = JsonUtil.fromJsonToListString(rs.getString("paths"));
        List<String> popByName = JsonUtil.fromJsonToListString(rs.getString("population_by_name"));
        List<String> popById = JsonUtil.fromJsonToListString(rs.getString("population_by_id"));
        return Location.builder()
                .name(rs.getString("name")).dangerous(rs.getInt("dangerous"))
                .paths(paths).populationByName(popByName).populationById(popById)
                .pvp(rs.getBoolean("pvp")).boss(rs.getString("boss"))
                .bossItem(rs.getString("boss_item")).teleport(rs.getBoolean("teleport")).build();
    }
}
