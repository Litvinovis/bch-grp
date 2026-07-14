package ru.chebe.litvinov.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Boss;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет что боссы инициализируются с тирированными статами.
 */
public class BossStatsTest {

    private BattleManager battleManager;

    @BeforeEach
    public void setUp() {
        battleManager = new BattleManager(null); // null repo — только init в памяти
        battleManager.init();
    }

    @Test
    public void tier1Boss_hasLowerStatsThanTier4() {
        // Labynkyr — тир 1, Darhalas — тир 4
        Boss tier1 = battleManager.getBoss("Labynkyr");
        Boss tier4 = battleManager.getBoss("Darhalas");
        assertNotNull(tier1);
        assertNotNull(tier4);
        assertTrue(tier1.getHp() < tier4.getHp(), "Tier1 HP should be < Tier4 HP");
        assertTrue(tier1.getStrength() < tier4.getStrength(), "Tier1 strength should be < Tier4 strength");
    }

    @Test
    public void tier1Boss_statsInRange() {
        Boss boss = battleManager.getBoss("Labynkyr");
        assertNotNull(boss);
        assertTrue(boss.getHp() >= 250 && boss.getHp() <= 400, "HP should be between 250 and 400");
        assertTrue(boss.getStrength() >= 5 && boss.getStrength() <= 8, "Strength should be 5-8");
    }

    @Test
    public void tier4Boss_statsInRange() {
        Boss boss = battleManager.getBoss("Darhalas");
        assertNotNull(boss);
        assertTrue(boss.getHp() >= 1200, "HP should be >= 1200");
        assertTrue(boss.getStrength() >= 18, "Strength should be >= 18");
    }

    @Test
    public void allBosses_havePositiveStats() {
        String[] bossNames = {
            "Labynkyr","Arktulz","Red","Gordon","Buzzz","Ябыс","Orson","la_brioche",
            "Morgott","Usual_god","Рианель","Stalker","Crown",
            "Ctin","Ushas","Илья","Вуъщт","Eduard",
            "cynic mansion","Rover","Chegobnk","Darhalas"
        };
        for (String name : bossNames) {
            Boss boss = battleManager.getBoss(name);
            assertNotNull(boss, "Boss not found: " + name);
            assertTrue(boss.getHp() > 0, "Boss " + name + " HP must be > 0");
            assertTrue(boss.getStrength() > 0, "Boss " + name + " strength must be > 0");
        }
    }
}
