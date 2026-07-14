package ru.chebe.litvinov.service;

import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.util.InputValidator;
import ru.chebe.litvinov.util.MetricsService;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for items 85-93:
 * - YAML config loading (items 85-87)
 * - InputValidator (item 88)
 * - MetricsService counters (item 89)
 * - Progress bar via PlayersManager helper (item 98)
 */
public class Items85to93Test {

    // --- Item 88: InputValidator ---

    @Test
    public void testValidateAmount_valid() {
        int result = InputValidator.validateAmount("150", 1, 1000);
        assertEquals(150, result);
    }

    @Test
    public void testValidateAmount_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount(null, 1, 100));
    }

    @Test
    public void testValidateAmount_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount("  ", 1, 100));
    }

    @Test
    public void testValidateAmount_nonNumeric_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount("abc", 1, 100));
    }

    @Test
    public void testValidateAmount_belowMin_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount("0", 1, 100));
    }

    @Test
    public void testValidateAmount_aboveMax_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount("101", 1, 100));
    }

    @Test
    public void testValidateAmount_atBoundaries_succeeds() {
        assertEquals(1, InputValidator.validateAmount("1", 1, 100));
        assertEquals(100, InputValidator.validateAmount("100", 1, 100));
    }

    @Test
    public void testValidateName_valid() {
        String result = InputValidator.validateName("  Alice  ", 20);
        assertEquals("Alice", result);
    }

    @Test
    public void testValidateName_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateName(null, 20));
    }

    @Test
    public void testValidateName_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateName("   ", 20));
    }

    @Test
    public void testValidateName_tooLong_throws() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateName("A".repeat(21), 20));
    }

    @Test
    public void testValidateName_exactMaxLength_succeeds() {
        String name = "A".repeat(20);
        assertEquals(name, InputValidator.validateName(name, 20));
    }

    // --- Item 89: MetricsService ---

    @Test
    public void testMetricsService_recordBattle_increments() {
        long before = MetricsService.battlesTotal.get();
        MetricsService.recordBattle();
        assertEquals(before + 1, MetricsService.battlesTotal.get());
    }

    @Test
    public void testMetricsService_updateCoins_delta() {
        MetricsService.coinsInCirculation.set(1000);
        MetricsService.updateCoins(500);
        assertEquals(1500, MetricsService.coinsInCirculation.get());
        MetricsService.updateCoins(-200);
        assertEquals(1300, MetricsService.coinsInCirculation.get());
    }

    @Test
    public void testMetricsService_updateActivePlayers_sets() {
        MetricsService.updateActivePlayers(42);
        assertEquals(42, MetricsService.activePlayersToday.get());
        MetricsService.updateActivePlayers(7);
        assertEquals(7, MetricsService.activePlayersToday.get());
    }

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
