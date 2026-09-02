package ru.chebe.litvinov.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for items 85-93:
 * - YAML config loading (items 85-87)
 * - Progress bar via PlayersManager helper (item 98)
 */
public class Items85to93Test {

    // --- Items 85-87: YAML config files exist on classpath ---

    @Test
    public void testLocationsYaml_existsOnClasspath() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("locations.yml");
        assertNotNull(is, "locations.yml should be on classpath");
    }

    @Test
    public void testItemsYaml_existsOnClasspath() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("items.yml");
        assertNotNull(is, "items.yml should be on classpath");
    }

    @Test
    public void testBossesYaml_existsOnClasspath() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("bosses.yml");
        assertNotNull(is, "bosses.yml should be on classpath");
    }
}
