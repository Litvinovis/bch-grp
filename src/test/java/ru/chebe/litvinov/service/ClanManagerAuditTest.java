package ru.chebe.litvinov.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.ClanRepository;
import ru.chebe.litvinov.repository.PlayerRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Регрессии кланов: заявки сохраняются, приём нескольких заявок не падает.
 */
class ClanManagerAuditTest {

	private ClanRepository clanRepository;
	private PlayerRepository playerRepository;
	private ClanManager clanManager;

	@BeforeEach
	void setUp() {
		clanRepository = mock(ClanRepository.class);
		playerRepository = mock(PlayerRepository.class);
		clanManager = new ClanManager(clanRepository, playerRepository);
	}

	@Test
	void joinClan_persistsApplication() {
		Clan clan = new Clan("волки", "leader");
		when(clanRepository.get("волки")).thenReturn(clan);

		assertEquals("", clanManager.joinClan("волки", "p1"));

		assertTrue(clan.getAppliers().contains("p1"));
		verify(clanRepository).put("волки", clan);
	}

	@Test
	void joinClan_duplicateApplication_rejected() {
		Clan clan = new Clan("волки", "leader");
		clan.getAppliers().add("p1");
		when(clanRepository.get("волки")).thenReturn(clan);

		assertFalse(clanManager.joinClan("волки", "p1").isEmpty());

		assertEquals(1, clan.getAppliers().size());
		verify(clanRepository, never()).put(anyString(), any());
	}

	@Test
	void acceptApply_severalApplicants_allJoinWithoutConcurrentModification() {
		Clan clan = new Clan("волки", "leader");
		clan.getAppliers().add("p1");
		clan.getAppliers().add("p2");
		when(clanRepository.get("волки")).thenReturn(clan);
		Player p1 = new Player("Первый", "p1");
		Player p2 = new Player("Второй", "p2");
		when(playerRepository.get("p1")).thenReturn(p1);
		when(playerRepository.get("p2")).thenReturn(p2);

		assertEquals("", clanManager.acceptApply("волки", "leader"));

		assertTrue(clan.getMembers().containsAll(java.util.List.of("leader", "p1", "p2")));
		assertTrue(clan.getAppliers().isEmpty());
		assertEquals("волки", p1.getClanName());
		assertEquals("волки", p2.getClanName());
	}

	@Test
	void rejectApply_persistsClearedApplications() {
		Clan clan = new Clan("волки", "leader");
		clan.getAppliers().add("p1");
		when(clanRepository.get("волки")).thenReturn(clan);

		assertEquals("", clanManager.rejectApply("волки", "leader"));

		assertTrue(clan.getAppliers().isEmpty());
		verify(clanRepository).put("волки", clan);
	}
}
