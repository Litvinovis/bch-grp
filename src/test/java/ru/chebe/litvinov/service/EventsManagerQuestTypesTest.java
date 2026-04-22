package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Player;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class EventsManagerQuestTypesTest {

    private EventsManager eventsManager;

    @Before
    public void setUp() {
        eventsManager = new EventsManager();
    }

    // ---------- Ходилка ----------

    @Test
    public void checkEvent_Ходилка_success_whenPlayerAtDestination() {
        Player p = new Player("t", "1");
        p.setLocation("мейн");
        Event e = Event.builder().type("Ходилка").locationEnd("мейн").build();
        p.setActiveEvent(e);
        assertTrue(eventsManager.checkEvent(e, p));
    }

    @Test
    public void checkEvent_Ходилка_fail_whenNotAtDestination() {
        Player p = new Player("t", "1");
        p.setLocation("деградач");
        Event e = Event.builder().type("Ходилка").locationEnd("мейн").build();
        p.setActiveEvent(e);
        assertFalse(eventsManager.checkEvent(e, p));
    }

    // ---------- Таймер ----------

    @Test
    public void checkEvent_Таймер_success_inTimeAndAtDestination() {
        Player p = new Player("t", "1");
        p.setLocation("кушетка");
        String future = Instant.now().plusSeconds(300).toString();
        Event e = Event.builder().type("Таймер").locationEnd("кушетка").timeEnd(future).build();
        p.setActiveEvent(e);
        assertTrue(eventsManager.checkEvent(e, p));
    }

    @Test
    public void checkEvent_Таймер_fail_timeExpired() {
        Player p = new Player("t", "1");
        p.setLocation("кушетка");
        String past = Instant.now().minusSeconds(10).toString();
        Event e = Event.builder().type("Таймер").locationEnd("кушетка").timeEnd(past).build();
        p.setActiveEvent(e);
        assertFalse(eventsManager.checkEvent(e, p));
    }

    @Test
    public void checkEvent_Таймер_fail_wrongLocation() {
        Player p = new Player("t", "1");
        p.setLocation("мейн");
        String future = Instant.now().plusSeconds(300).toString();
        Event e = Event.builder().type("Таймер").locationEnd("кушетка").timeEnd(future).build();
        p.setActiveEvent(e);
        assertFalse(eventsManager.checkEvent(e, p));
    }

    // ---------- Путешественник ----------

    @Test
    public void checkEvent_Путешественник_success_whenAttemptsReached() {
        Player p = new Player("t", "1");
        Event e = Event.builder().type("Путешественник").correctAnswer("3").attempt(3).build();
        p.setActiveEvent(e);
        assertTrue(eventsManager.checkEvent(e, p));
    }

    @Test
    public void checkEvent_Путешественник_fail_notEnoughAttempts() {
        Player p = new Player("t", "1");
        Event e = Event.builder().type("Путешественник").correctAnswer("5").attempt(2).build();
        p.setActiveEvent(e);
        assertFalse(eventsManager.checkEvent(e, p));
    }

    // ---------- Охота ----------

    @Test
    public void checkEvent_Охота_success_whenKillsReached() {
        Player p = new Player("t", "1");
        Event e = Event.builder().type("Охота").correctAnswer("4").attempt(4).build();
        p.setActiveEvent(e);
        assertTrue(eventsManager.checkEvent(e, p));
    }

    @Test
    public void checkEvent_Охота_fail_notEnoughKills() {
        Player p = new Player("t", "1");
        Event e = Event.builder().type("Охота").correctAnswer("4").attempt(1).build();
        p.setActiveEvent(e);
        assertFalse(eventsManager.checkEvent(e, p));
    }

    // ---------- Везунчик ----------

    @Test
    public void checkEvent_Везунчик_success_whenAttemptIsOne() {
        Player p = new Player("t", "1");
        Event e = Event.builder().type("Везунчик").attempt(1).build();
        p.setActiveEvent(e);
        assertTrue(eventsManager.checkEvent(e, p));
    }

    @Test
    public void checkEvent_Везунчик_fail_noWinYet() {
        Player p = new Player("t", "1");
        Event e = Event.builder().type("Везунчик").attempt(0).build();
        p.setActiveEvent(e);
        assertFalse(eventsManager.checkEvent(e, p));
    }

    // ---------- assignEvent ----------

    @Test
    public void assignEvent_returnsKnownType() {
        List<String> locations = List.of("мейн", "деградач", "старборд", "таверна");
        Set<String> validTypes = Set.of("Ходилка", "Загадка", "Таймер", "Путешественник", "Охота", "Везунчик");
        for (int i = 0; i < 50; i++) {
            Event e = eventsManager.assignEvent(locations);
            assertTrue("Unknown event type: " + e.getType(), validTypes.contains(e.getType()));
        }
    }

    @Test
    public void assignEvent_progressTypesHaveAttemptZero() {
        List<String> locations = List.of("мейн", "деградач");
        for (int i = 0; i < 100; i++) {
            Event e = eventsManager.assignEvent(locations);
            if ("Путешественник".equals(e.getType()) || "Охота".equals(e.getType())
                    || "Везунчик".equals(e.getType())) {
                assertEquals("New quest attempt must start at 0", 0, e.getAttempt());
            }
        }
    }
}
