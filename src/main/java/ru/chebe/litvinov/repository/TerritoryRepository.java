package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторий для хранения данных о захваченных территориях.
 */
public class TerritoryRepository {

    private static final Logger log = LoggerFactory.getLogger(TerritoryRepository.class);
    private final DataSource dataSource;

    public TerritoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Territory(String locationName, String clanName, long capturedAt, int taxRate) {}

    public Territory get(String locationName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT location_name, clan_name, captured_at, tax_rate FROM territories WHERE location_name = ?")) {
            ps.setString(1, locationName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Territory(rs.getString("location_name"), rs.getString("clan_name"),
                        rs.getLong("captured_at"), rs.getInt("tax_rate"));
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка get territory: {}", e.getMessage());
        }
        return null;
    }

    public List<Territory> getAll() {
        List<Territory> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT location_name, clan_name, captured_at, tax_rate FROM territories");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Territory(rs.getString("location_name"), rs.getString("clan_name"),
                    rs.getLong("captured_at"), rs.getInt("tax_rate")));
            }
        } catch (Exception e) {
            log.warn("Ошибка getAll territories: {}", e.getMessage());
        }
        return list;
    }

    public void upsert(String locationName, String clanName, long capturedAt, int taxRate) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO territories (location_name, clan_name, captured_at, tax_rate) VALUES (?,?,?,?) " +
                 "ON CONFLICT (location_name) DO UPDATE SET clan_name=EXCLUDED.clan_name, captured_at=EXCLUDED.captured_at, tax_rate=EXCLUDED.tax_rate")) {
            ps.setString(1, locationName);
            ps.setString(2, clanName);
            ps.setLong(3, capturedAt);
            ps.setInt(4, taxRate);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Ошибка upsert territory: {}", e.getMessage());
        }
    }
}
