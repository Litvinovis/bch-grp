package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Idea;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class IdeaRepository {

    private static final Logger log = LoggerFactory.getLogger(IdeaRepository.class);
    private final DataSource dataSource;

    public IdeaRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Idea get(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, description, author, resolution FROM ideas WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) { log.error("Ошибка get({})", id, e); }
        return null;
    }

    public void put(int id, Idea idea) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO ideas (id, description, author, resolution) VALUES (?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET description=EXCLUDED.description, " +
                 "author=EXCLUDED.author, resolution=EXCLUDED.resolution")) {
            ps.setInt(1, id); ps.setString(2, idea.getDescription());
            ps.setString(3, idea.getAuthor()); ps.setString(4, idea.getResolution());
            ps.executeUpdate();
        } catch (Exception e) { log.error("Ошибка put({})", id, e); }
    }

    public int size() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM ideas");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { log.error("Ошибка size()", e); }
        return 0;
    }

    public List<Idea> findByResolution(String resolution) {
        List<Idea> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, description, author, resolution FROM ideas WHERE resolution = ?")) {
            ps.setString(1, resolution);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (Exception e) { log.error("Ошибка findByResolution({})", resolution, e); }
        return result;
    }

    public List<Idea> findAll() {
        List<Idea> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, description, author, resolution FROM ideas");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) { log.error("Ошибка findAll()", e); }
        return result;
    }

    private Idea mapRow(ResultSet rs) throws SQLException {
        return Idea.builder().id(rs.getInt("id")).description(rs.getString("description"))
                .author(rs.getString("author")).resolution(rs.getString("resolution")).build();
    }
}
