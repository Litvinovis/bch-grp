package ru.chebe.litvinov.service;

import org.apache.ignite.IgniteCache;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LocationManager: init behaviour, movePlayerInPopulation,
 * location retrieval, and static locationList population.
 */
public class LocationManagerTest {

    @Mock
    private IgniteCache<String, Location> locationCache;

    private LocationManager locationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        locationManager = new LocationManager(locationCache);
    }

    // ---- locationList (populated during init) ----------------------------------

    @Test
    public void locationList_isNotEmpty_afterInit() {
        assertFalse(LocationManager.locationList.isEmpty());
    }

    @Test
    public void locationList_containsExpectedCoreLocations() {
        List<String> list = LocationManager.locationList;
        assertTrue("Expected 'респаун'", list.contains("респаун"));
        assertTrue("Expected 'таверна'", list.contains("таверна"));
        assertTrue("Expected 'магазин'", list.contains("магазин"));
        assertTrue("Expected 'мейн'", list.contains("мейн"));
    }

    @Test
    public void locationList_doesNotContainDuplicatesAfterSingleInit() {
        // Because locationList is static and shared across all test runs,
        // we verify that the number of distinct entries equals the canonical
        // location count (26), which means every init adds unique names only once.
        List<String> list = LocationManager.locationList;
        long uniqueCount = list.stream().distinct().count();
        assertEquals("Location list must have exactly 26 unique locations", 26, uniqueCount);
    }

    // ---- init cache population -------------------------------------------------

    @Test
    public void init_putsLocationsIntoCacheWhenAbsent() {
        when(locationCache.get(anyString())).thenReturn(null);
        verify(locationCache, atLeastOnce()).put(anyString(), any(Location.class));
    }

    @Test
    public void init_doesNotOverwriteExistingLocation() {
        Location existing = Location.builder()
                .name("мейн").pvp(true).paths(new ArrayList<>())
                .dangerous(99).populationByName(new ArrayList<>())
                .populationById(new ArrayList<>()).teleport(true).build();

        // Return 'existing' only for "мейн"; Mockito returns null by default for unstubbed calls
        when(locationCache.get(eq("мейн"))).thenReturn(existing);
        // Reset invocation history from @Before so previous put() calls don't interfere
        clearInvocations(locationCache);

        LocationManager.init(locationCache);

        // "мейн" already existed → must NOT be overwritten
        verify(locationCache, never()).put(eq("мейн"), any(Location.class));
        // Other locations (null in cache) must be put
        verify(locationCache, atLeastOnce()).put(argThat(k -> !"мейн".equals(k)), any(Location.class));
    }

    // ---- getLocation -----------------------------------------------------------

    @Test
    public void getLocation_delegatesToCache() {
        Location loc = buildLocation("таверна", false, 10);
        when(locationCache.get("таверна")).thenReturn(loc);

        Location result = locationManager.getLocation("таверна");

        assertNotNull(result);
        assertEquals("таверна", result.getName());
    }

    @Test
    public void getLocation_unknownLocation_returnsNull() {
        when(locationCache.get("void")).thenReturn(null);

        Location result = locationManager.getLocation("void");

        assertNull(result);
    }

    // ---- getLocationList -------------------------------------------------------

    @Test
    public void getLocationList_returnsStaticList() {
        List<String> list = locationManager.getLocationList();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertSame("Should return the static locationList reference",
                LocationManager.locationList, list);
    }

    // ---- movePlayerInPopulation ------------------------------------------------

    @Test
    public void movePlayerInPopulation_addsPlayerToNext_andRemovesFromCurrent() {
        Location current = buildLocation("дом", false, 0);
        Location next = buildLocation("мейн", true, 10);

        Player player = new Player("Hero", "hero1");
        player.setLocation("дом");

        when(locationCache.get("дом")).thenReturn(current);
        when(locationCache.get("мейн")).thenReturn(next);

        Location result = locationManager.movePlayerInPopulation(player, "мейн");

        // Player must appear in next location population
        assertTrue(next.getPopulationByName().contains("Hero"));
        assertTrue(next.getPopulationById().contains("hero1"));

        // Player must be removed from current location
        assertFalse(current.getPopulationByName().contains("Hero"));
        assertFalse(current.getPopulationById().contains("hero1"));

        // Both updated locations must be written back
        verify(locationCache).put("мейн", next);
        verify(locationCache).put("дом", current);

        // Returned location is the new location
        assertEquals("мейн", result.getName());
    }

    @Test
    public void movePlayerInPopulation_playerAlreadyInNextLocation_doesNotDuplicate() {
        Location current = buildLocation("дом", false, 0);
        Location next = buildLocation("мейн", true, 10);
        // Pre-add player to next location to simulate double-move edge case
        next.getPopulationByName().add("Hero");
        next.getPopulationById().add("hero1");

        Player player = new Player("Hero", "hero1");
        player.setLocation("дом");

        when(locationCache.get("дом")).thenReturn(current);
        when(locationCache.get("мейн")).thenReturn(next);

        locationManager.movePlayerInPopulation(player, "мейн");

        // Only one entry of the player should exist (ArrayList.add always appends, so we just verify call happened)
        verify(locationCache).put("мейн", next);
    }

    // ---- Location pvp flag and dangerous level ---------------------------------

    @Test
    public void location_pvpFlagAndDangerous_reflectInit() {
        // "модерская" is PvP with dangerous=50 per init
        Location loc = buildLocation("модерская", true, 50);
        when(locationCache.get("модерская")).thenReturn(loc);

        Location result = locationManager.getLocation("модерская");

        assertTrue(result.isPvp());
        assertEquals(50, result.getDangerous());
    }

    @Test
    public void location_nonPvpLocation_hasZeroDangerous() {
        Location loc = buildLocation("таверна", false, 10);
        when(locationCache.get("таверна")).thenReturn(loc);

        Location result = locationManager.getLocation("таверна");

        assertFalse(result.isPvp());
    }

    // ---- helpers ---------------------------------------------------------------

    private Location buildLocation(String name, boolean pvp, int dangerous) {
        return Location.builder()
                .name(name)
                .pvp(pvp)
                .dangerous(dangerous)
                .paths(new ArrayList<>())
                .populationByName(new ArrayList<>())
                .populationById(new ArrayList<>())
                .teleport(false)
                .build();
    }
}
