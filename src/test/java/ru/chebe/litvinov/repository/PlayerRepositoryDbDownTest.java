package ru.chebe.litvinov.repository;

import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Player;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Недоступная БД должна быть видна вызывающему коду.
 * Раньше репозиторий глушил исключение: put() «сохранял» прогресс в никуда,
 * get() возвращал null и игрок выглядел незарегистрированным.
 */
class PlayerRepositoryDbDownTest {

    private DataSource brokenDataSource() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("Connection refused: DB is down"));
        return ds;
    }

    @Test
    void getAll_whenDbDown_throws() throws SQLException {
        PlayerRepository repo = new PlayerRepository(brokenDataSource());
        assertThrows(IllegalStateException.class, repo::getAll);
    }

    @Test
    void get_whenDbDown_throws() throws SQLException {
        PlayerRepository repo = new PlayerRepository(brokenDataSource());
        assertThrows(IllegalStateException.class, () -> repo.get("id1"),
                "null вместо ошибки читался как «игрок не зарегистрирован»");
    }

    @Test
    void contains_whenDbDown_throws() throws SQLException {
        PlayerRepository repo = new PlayerRepository(brokenDataSource());
        assertThrows(IllegalStateException.class, () -> repo.contains("id1"));
    }

    @Test
    void put_whenDbDown_throws() throws SQLException {
        PlayerRepository repo = new PlayerRepository(brokenDataSource());
        assertThrows(IllegalStateException.class, () -> repo.put("id1", new Player("Ник", "id1")),
                "Молчаливый провал записи означал потерянный прогресс при успешном ответе бота");
    }

    @Test
    void putBoth_whenDbDown_throws() throws SQLException {
        PlayerRepository repo = new PlayerRepository(brokenDataSource());
        assertThrows(IllegalStateException.class,
                () -> repo.putBoth("id1", new Player("Ник", "id1"), "id2", new Player("Ник2", "id2")));
    }

    @Test
    void remove_whenDbDown_throws() throws SQLException {
        PlayerRepository repo = new PlayerRepository(brokenDataSource());
        assertThrows(IllegalStateException.class, () -> repo.remove("id1"));
    }
}
