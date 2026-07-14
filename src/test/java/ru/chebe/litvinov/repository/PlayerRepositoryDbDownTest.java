package ru.chebe.litvinov.repository;

import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Player;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlayerRepositoryDbDownTest {

	private DataSource brokenDs() throws SQLException {
		DataSource ds = mock(DataSource.class);
		when(ds.getConnection()).thenThrow(new SQLException("Connection refused: DB is down"));
		return ds;
	}

	@Test
	public void getAll_whenDbDown_returnsEmptyList() throws SQLException {
		PlayerRepository repo = new PlayerRepository(brokenDs());
		List<Player> result = repo.getAll();
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void get_whenDbDown_returnsNull() throws SQLException {
		PlayerRepository repo = new PlayerRepository(brokenDs());
		assertNull(repo.get("123"));
	}

	@Test
	public void contains_whenDbDown_returnsFalse() throws SQLException {
		PlayerRepository repo = new PlayerRepository(brokenDs());
		assertFalse(repo.contains("123"));
	}

	@Test
	public void put_whenDbDown_doesNotThrow() throws SQLException {
		PlayerRepository repo = new PlayerRepository(brokenDs());
		Player p = new Player("TestUser", "123");
		repo.put("123", p);
	}

	@Test
	public void remove_whenDbDown_doesNotThrow() throws SQLException {
		PlayerRepository repo = new PlayerRepository(brokenDs());
		repo.remove("123");
	}
}
