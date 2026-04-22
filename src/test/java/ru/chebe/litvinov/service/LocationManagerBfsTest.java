package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.ignite3.LocationRepository;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class LocationManagerBfsTest {

    /** Минимальный stub LocationRepository на основе HashMap. */
    private static class StubLocationRepository extends LocationRepository {
        private final Map<String, Location> store = new HashMap<>();

        public StubLocationRepository() {
            super(null); // Ignite3Configurator = null — соединение не нужно
        }

        @Override public Location get(String key) { return store.get(key); }
        @Override public void put(String key, Location value) { store.put(key, value); }
        @Override public boolean contains(String key) { return store.containsKey(key); }
    }

    private LocationManager locationManager;

    @Before
    public void setUp() {
        locationManager = new LocationManager(new StubLocationRepository());
    }

    @Test
    public void findNextStep_directNeighbor() {
        // деградач → мейн (прямые соседи)
        String next = locationManager.findNextStep("деградач", "мейн");
        assertEquals("мейн", next);
    }

    @Test
    public void findNextStep_sameLocation() {
        String next = locationManager.findNextStep("мейн", "мейн");
        assertEquals("мейн", next);
    }

    @Test
    public void findNextStep_shortestPath_notDirect() {
        // мейн → кушетка: мейн→дом→старборд→хуй-тек→кушетка (или через модерскую)
        // первый шаг должен быть один из соседей мейна на пути к кушетке
        String next = locationManager.findNextStep("мейн", "кушетка");
        assertNotNull("Path from мейн to кушетка should exist", next);
        // Следующий шаг должен быть соседом мейна
        Location мейн = locationManager.getLocation("мейн");
        assertTrue("Next step must be a neighbor of мейн",
                мейн.getPaths().contains(next));
    }

    @Test
    public void findNextStep_unreachableLocation_returnsNull() {
        // Несуществующая локация недостижима
        String next = locationManager.findNextStep("мейн", "нигде");
        assertNull(next);
    }

    @Test
    public void findNextStep_путьКОлимпу() {
        // олимп имеет только путь из модерской, значит любой узел должен идти в сторону модерской
        String next = locationManager.findNextStep("мейн", "олимп");
        assertNotNull(next);
        Location мейн = locationManager.getLocation("мейн");
        assertTrue(мейн.getPaths().contains(next));
    }
}
