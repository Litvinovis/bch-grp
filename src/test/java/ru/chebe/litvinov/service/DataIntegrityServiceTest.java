package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DataIntegrityService:
 * - HP > maxHp → capped
 * - XP overflow → level-up applied
 * - Duplicate player ID in location → deduplicated
 * - Player recorded in wrong location → removed from that location's population
 * - Clean data → no writes
 */
public class DataIntegrityServiceTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private LocationManager locationManager;

    private DataIntegrityService service;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        service = new DataIntegrityService(playerRepository, locationManager);
        when(locationManager.getLocationList()).thenReturn(List.of("мейн", "арена", "респаун"));
    }

    // ---- HP cap -----------------------------------------------------------------

    @Test
    public void checkAndFix_hpExceedsMaxHp_cappedToMaxHp() {
        Player p = playerWith("p1", "мейн", 1, 150, 100, 0, 100); // exp=0 so no level-up
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(100, p.getHp());
        verify(playerRepository).put("p1", p);
    }

    @Test
    public void checkAndFix_hpWithinLimit_noPlayerWrite() {
        Player p = playerWith("p1", "мейн", 1, 80, 100, 0, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(80, p.getHp());
        verify(playerRepository, never()).put(anyString(), any());
    }

    @Test
    public void checkAndFix_hpEqualsMaxHp_noPlayerWrite() {
        Player p = playerWith("p1", "мейн", 1, 100, 100, 0, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        verify(playerRepository, never()).put(anyString(), any());
    }

    // ---- XP overflow / level-up -------------------------------------------------

    @Test
    public void checkAndFix_xpOverflows_levelUpApplied() {
        // level=1, exp=150, expToNextLvl=100 → should level up to 2
        Player p = playerWith("p1", "мейн", 1, 100, 100, 150, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(2, p.getLevel());
        assertEquals(50, p.getExp()); // 150 - 100
        verify(playerRepository).put("p1", p);
    }

    @Test
    public void checkAndFix_xpExactlyAtThreshold_levelUpApplied() {
        Player p = playerWith("p1", "мейн", 1, 100, 100, 100, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(2, p.getLevel());
        assertEquals(0, p.getExp());
        verify(playerRepository).put("p1", p);
    }

    @Test
    public void checkAndFix_multipleLevelUpsAtOnce_allApplied() {
        // xpMap: level 2→100, level 3→200, level 4→300
        // level=1, exp=350, expToNextLvl=100
        // lvl1→2: spend 100, remain 250, expToNext=xpMap[2]=100
        // lvl2→3: spend 100, remain 150, expToNext=xpMap[3]=200
        // lvl3: 150 < 200, stop → lvl3, exp=150
        Player p = playerWith("p1", "мейн", 1, 100, 100, 350, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(3, p.getLevel());
        assertEquals(150, p.getExp());
        verify(playerRepository).put("p1", p);
    }

    @Test
    public void checkAndFix_xpBelowThreshold_noChange() {
        Player p = playerWith("p1", "мейн", 1, 100, 100, 99, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(1, p.getLevel());
        assertEquals(99, p.getExp());
        verify(playerRepository, never()).put(anyString(), any());
    }

    @Test
    public void checkAndFix_levelUpRestoresHp() {
        Player p = playerWith("p1", "мейн", 1, 50, 100, 150, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        // Level 2 maxHp = 110 (100 + 1*10)
        assertEquals(110, p.getMaxHp());
        assertEquals(p.getMaxHp(), p.getHp());
    }

    @Test
    public void checkAndFix_atMaxLevel100_noLevelUpEvenIfXpOverflows() {
        Player p = playerWith("p1", "мейн", 100, 100, 100, 999, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        stubEmptyLocations();

        service.checkAndFix();

        assertEquals(100, p.getLevel()); // capped at 100
    }

    // ---- location population duplicates ----------------------------------------

    @Test
    public void checkAndFix_duplicatePlayerInLocation_deduplicated() {
        Player p = playerWith("p1", "мейн", 1, 100, 100, 0, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        when(playerRepository.get("p1")).thenReturn(p);

        Location loc = locationWithPop("мейн", new ArrayList<>(List.of("p1", "p1")),
                new ArrayList<>(List.of("Nick_p1", "Nick_p1")));
        when(locationManager.getLocation("мейн")).thenReturn(loc);
        when(locationManager.getLocation("арена")).thenReturn(emptyLoc("арена"));
        when(locationManager.getLocation("респаун")).thenReturn(emptyLoc("респаун"));

        service.checkAndFix();

        assertEquals(1, loc.getPopulationById().size());
        assertEquals("p1", loc.getPopulationById().get(0));
        verify(locationManager).saveLocation(loc);
    }

    @Test
    public void checkAndFix_playerInWrongLocation_removedFromPopulation() {
        // Player declares location="арена", but is listed in "мейн"
        Player p = playerWith("p1", "арена", 1, 100, 100, 0, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        when(playerRepository.get("p1")).thenReturn(p);

        Location wrongLoc = locationWithPop("мейн", new ArrayList<>(List.of("p1")),
                new ArrayList<>(List.of("Nick_p1")));
        when(locationManager.getLocation("мейн")).thenReturn(wrongLoc);
        when(locationManager.getLocation("арена")).thenReturn(emptyLoc("арена"));
        when(locationManager.getLocation("респаун")).thenReturn(emptyLoc("респаун"));

        service.checkAndFix();

        assertTrue(wrongLoc.getPopulationById().isEmpty());
        assertTrue(wrongLoc.getPopulationByName().isEmpty());
        verify(locationManager).saveLocation(wrongLoc);
    }

    @Test
    public void checkAndFix_playerInCorrectLocation_notRemoved() {
        Player p = playerWith("p1", "мейн", 1, 100, 100, 0, 100);
        when(playerRepository.getAll()).thenReturn(List.of(p));
        when(playerRepository.get("p1")).thenReturn(p);

        Location loc = locationWithPop("мейн", new ArrayList<>(List.of("p1")),
                new ArrayList<>(List.of("Nick")));
        when(locationManager.getLocation("мейн")).thenReturn(loc);
        when(locationManager.getLocation("арена")).thenReturn(emptyLoc("арена"));
        when(locationManager.getLocation("респаун")).thenReturn(emptyLoc("респаун"));

        service.checkAndFix();

        assertEquals(1, loc.getPopulationById().size());
        verify(locationManager, never()).saveLocation(any());
    }

    @Test
    public void checkAndFix_emptyLocations_noLocationWrite() {
        when(playerRepository.getAll()).thenReturn(List.of());
        stubEmptyLocations();

        service.checkAndFix();

        verify(locationManager, never()).saveLocation(any());
    }

    // ---- helpers ---------------------------------------------------------------

    private Player playerWith(String id, String location, int level,
                               int hp, int maxHp, int exp, int expToNextLvl) {
        Player p = new Player("Nick_" + id, id);
        p.setLocation(location);
        p.setLevel(level);
        p.setHp(hp);
        p.setMaxHp(maxHp);
        p.setExp(exp);
        p.setExpToNextLvl(expToNextLvl);
        return p;
    }

    private Location locationWithPop(String name, List<String> byId, List<String> byName) {
        return Location.builder()
                .name(name)
                .pvp(false)
                .dangerous(0)
                .boss(null)
                .paths(new ArrayList<>())
                .populationById(byId)
                .populationByName(byName)
                .teleport(false)
                .build();
    }

    private Location emptyLoc(String name) {
        return locationWithPop(name, new ArrayList<>(), new ArrayList<>());
    }

    private void stubEmptyLocations() {
        when(locationManager.getLocation(anyString())).thenReturn(emptyLoc("x"));
    }
}
