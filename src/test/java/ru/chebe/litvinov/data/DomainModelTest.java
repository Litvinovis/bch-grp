package ru.chebe.litvinov.data;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class DomainModelTest {

    @Test
    public void player_defaultsAndInventoryInfo_areValid() {
        Player p = new Player("nick", "id1");
        assertEquals("nick", p.getNickName());
        assertEquals("id1", p.getId());
        assertEquals(100, p.getHp());
        assertTrue(p.getInventory().containsKey("токен телепорта"));
        assertNotNull(p.inventoryInfo());
        assertTrue(p.toString().contains("**nick**"));
    }

    @Test
    public void clan_constructor_setsLeaderAsMember() {
        Clan clan = new Clan("test", "u1");
        assertEquals("test", clan.getName());
        assertEquals("u1", clan.getLeaderId());
        assertEquals(1, clan.getMembers().size());
        assertEquals("u1", clan.getMembers().get(0));
    }

    @Test
    public void idea_toString_containsFields() {
        Idea idea = Idea.builder().id(7).author("me").description("desc").resolution("ok").build();
        String s = idea.toString();
        assertTrue(s.contains("Идея № 7"));
        assertTrue(s.contains("Автор - me"));
    }

    @Test
    public void item_toString_containsPriceAndDescription() {
        Item item = Item.builder()
                .name("меч")
                .description("острый")
                .price(100)
                .armor(1)
                .xpGeneration(2)
                .health(3)
                .luck(4)
                .reputation(5)
                .strength(6)
                .quantity(1)
                .action(true)
                .expireTime(System.currentTimeMillis() + 60_000)
                .build();
        String s = item.toString();
        assertTrue(s.contains("**меч**"));
        assertTrue(s.contains("Цена: **100**"));
        assertTrue(s.contains("острый"));
    }

    @Test
    public void event_toString_formatsRiddleAndTimer() {
        Event event = Event.builder()
                .description("2+2?")
                .type("загадка")
                .timeEnd(Instant.now().plusSeconds(120).toString())
                .moneyReward(10)
                .xpReward(20)
                .locationEnd("лес")
                .itemReward("ключ")
                .build();
        String s = event.toString();
        assertTrue(s.contains("Вопрос - 2+2?"));
        assertTrue(s.contains("Тип - загадка"));
        assertTrue(s.contains("Награда в монетах - 10"));
    }

    @Test
    public void location_toString_containsCoreFields() {
        Location l = Location.builder()
                .name("база")
                .dangerous(12)
                .populationByName(List.of("u1"))
                .paths(List.of("лес"))
                .pvp(true)
                .boss("босс")
                .bossItem("дроп")
                .build();
        String s = l.toString();
        assertTrue(s.contains("Название - база"));
        assertTrue(s.contains("Опасность - 12%"));
        assertTrue(s.contains("Босс - босс"));
    }

    @Test
    public void boss_builder_setsValues() {
        Boss boss = Boss.builder().nickName("orc").hp(100).strength(10).armor(2).bossItem("loot").defeat(1).win(2).build();
        assertEquals("orc", boss.getNickName());
        assertEquals(100, boss.getHp());
        assertEquals("loot", boss.getBossItem());
    }
}
