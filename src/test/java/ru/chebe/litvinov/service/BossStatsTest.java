package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.Boss;

import static org.junit.Assert.*;

/**
 * Проверяет что боссы инициализируются с тирированными статами.
 */
public class BossStatsTest {

    private BattleManager battleManager;

    @Before
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
        assertTrue("Tier1 HP should be < Tier4 HP", tier1.getHp() < tier4.getHp());
        assertTrue("Tier1 strength should be < Tier4 strength", tier1.getStrength() < tier4.getStrength());
    }

    @Test
    public void tier1Boss_statsInRange() {
        Boss boss = battleManager.getBoss("Labynkyr");
        assertNotNull(boss);
        assertTrue("HP should be between 250 and 400", boss.getHp() >= 250 && boss.getHp() <= 400);
        assertTrue("Strength should be 5-8", boss.getStrength() >= 5 && boss.getStrength() <= 8);
    }

    @Test
    public void tier4Boss_statsInRange() {
        Boss boss = battleManager.getBoss("Darhalas");
        assertNotNull(boss);
        assertTrue("HP should be >= 1200", boss.getHp() >= 1200);
        assertTrue("Strength should be >= 18", boss.getStrength() >= 18);
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
            assertNotNull("Boss not found: " + name, boss);
            assertTrue("Boss " + name + " HP must be > 0", boss.getHp() > 0);
            assertTrue("Boss " + name + " strength must be > 0", boss.getStrength() > 0);
        }
    }
}
