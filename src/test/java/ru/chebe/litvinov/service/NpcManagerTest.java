package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.NpcBot;

import java.util.*;

import static org.junit.Assert.*;

public class NpcManagerTest {

    private NpcManager npcManager;

    // location → boss short name (prefix of expected NPC name)
    private static final Map<String, String> LOCATION_BOSS_PREFIX = new LinkedHashMap<>();

    static {
        LOCATION_BOSS_PREFIX.put("мейн",            "Лаб");
        LOCATION_BOSS_PREFIX.put("деградач",        "Арк");
        LOCATION_BOSS_PREFIX.put("старборд",        "Ред");
        LOCATION_BOSS_PREFIX.put("для-ботов",       "Гордон");
        LOCATION_BOSS_PREFIX.put("для-флуда",       "Баз");
        LOCATION_BOSS_PREFIX.put("качалочка",       "Ябыс");
        LOCATION_BOSS_PREFIX.put("дорогой-дневник", "Орсон");
        LOCATION_BOSS_PREFIX.put("девочковое",      "Бувк");
        LOCATION_BOSS_PREFIX.put("рекламный",       "Моргот");
        LOCATION_BOSS_PREFIX.put("хуй-тек",         "Бог");
        LOCATION_BOSS_PREFIX.put("кринжборд",       "Риан");
        LOCATION_BOSS_PREFIX.put("чебеграм",        "Сталкер");
        LOCATION_BOSS_PREFIX.put("english",         "Кров");
        LOCATION_BOSS_PREFIX.put("кушетка",         "Стин");
        LOCATION_BOSS_PREFIX.put("модерская",       "Ушас");
        LOCATION_BOSS_PREFIX.put("политота",        "Илья");
        LOCATION_BOSS_PREFIX.put("nsfw",            "Вущт");
        LOCATION_BOSS_PREFIX.put("nsfw-gay",        "Эдик");
        LOCATION_BOSS_PREFIX.put("загадка",         "Циник");
        LOCATION_BOSS_PREFIX.put("клоунская-братва","Ровер");
        LOCATION_BOSS_PREFIX.put("nsfw2d",          "Чег");
        LOCATION_BOSS_PREFIX.put("олимп",           "Дарх");
    }

    @Before
    public void setUp() {
        npcManager = new NpcManager();
    }

    @Test
    public void npcInLocation_doesNotShareNameWithBoss() {
        for (Map.Entry<String, String> entry : LOCATION_BOSS_PREFIX.entrySet()) {
            String location = entry.getKey();
            String bossPrefix = entry.getValue().toLowerCase();
            List<NpcBot> bots = npcManager.getBotsInLocation(location);
            assertFalse("No NPC found in location: " + location, bots.isEmpty());
            for (NpcBot bot : bots) {
                String npcName = bot.getNickName().toLowerCase();
                assertFalse(
                    "NPC '" + bot.getNickName() + "' in location '" + location
                        + "' matches boss prefix '" + bossPrefix + "'",
                    npcName.startsWith(bossPrefix)
                );
            }
        }
    }

    @Test
    public void getBotsInLocation_returnsEmptyForUnknownLocation() {
        List<NpcBot> bots = npcManager.getBotsInLocation("несуществующая");
        assertTrue(bots.isEmpty());
    }

    @Test
    public void getRandomBot_returnsNullForEmptyLocation() {
        NpcBot bot = npcManager.getRandomBot("несуществующая");
        assertNull(bot);
    }

    @Test
    public void getRandomBot_returnsNpcForKnownLocation() {
        NpcBot bot = npcManager.getRandomBot("мейн");
        assertNotNull(bot);
        assertNotNull(bot.getNickName());
        assertTrue(bot.getHp() > 0);
        assertTrue(bot.getStrength() > 0);
    }

    @Test
    public void respawnBot_restoresHp() {
        NpcBot bot = npcManager.getRandomBot("мейн");
        assertNotNull(bot);
        int maxHp = bot.getMaxHp();
        bot.setHp(1);
        npcManager.respawnBot(bot);
        assertEquals(maxHp, bot.getHp());
    }

    @Test
    public void getBotNamesInLocation_containsHpInfo() {
        List<String> names = npcManager.getBotNamesInLocation("олимп");
        assertFalse(names.isEmpty());
        assertTrue(names.get(0).contains("HP:"));
    }

    @Test
    public void allTier4Locations_haveStrongerBotsThantier1() {
        NpcBot tier1 = npcManager.getRandomBot("мейн");
        NpcBot tier4 = npcManager.getRandomBot("олимп");
        assertNotNull(tier1);
        assertNotNull(tier4);
        assertTrue("Tier4 HP should exceed Tier1 HP", tier4.getHp() > tier1.getHp());
        assertTrue("Tier4 strength should exceed Tier1 strength", tier4.getStrength() > tier1.getStrength());
    }
}
