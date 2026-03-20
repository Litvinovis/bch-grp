package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Player;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for EventsManager: event creation, type distribution, quest check logic.
 */
public class EventsManagerTest {

    private EventsManager eventsManager;

    // Locations for the pathfinder quest — "респаун" must be present and
    // at least one more destination is required.
    private static final List<String> LOCATION_LIST = List.of(
            "респаун", "магазин", "таверна", "мейн", "дом");

    @Before
    public void setUp() {
        eventsManager = new EventsManager();
    }

    // ---- assignEvent -----------------------------------------------------------

    @Test
    public void assignEvent_returnsNonNullEvent() {
        Event event = eventsManager.assignEvent(LOCATION_LIST);
        assertNotNull(event);
    }

    @Test
    public void assignEvent_eventHasKnownType() {
        // Run many times — over the random distribution both types will appear
        for (int i = 0; i < 100; i++) {
            Event event = eventsManager.assignEvent(LOCATION_LIST);
            String type = event.getType();
            assertTrue("Unknown event type: " + type,
                    type.equals("Ходилка") || type.equals("Загадка"));
        }
    }

    @Test
    public void assignEvent_pathfinderEvent_hasLocationEndAndNoCorrectAnswer() {
        // Run enough times to hit at least one Ходилка (prob ≈ 3/4)
        Event pathfinder = null;
        for (int i = 0; i < 200; i++) {
            Event e = eventsManager.assignEvent(LOCATION_LIST);
            if ("Ходилка".equals(e.getType())) {
                pathfinder = e;
                break;
            }
        }
        assertNotNull("Did not encounter a Ходилка event in 200 attempts", pathfinder);
        assertNotNull(pathfinder.getLocationEnd());
        assertFalse("LocationEnd must not be 'респаун'",
                "респаун".equals(pathfinder.getLocationEnd()));
        assertNull(pathfinder.getCorrectAnswer());
    }

    @Test
    public void assignEvent_riddleEvent_hasCorrectAnswerAndNoLocationEnd() {
        // Run enough times to hit at least one Загадка (prob ≈ 1/4)
        Event riddle = null;
        for (int i = 0; i < 500; i++) {
            Event e = eventsManager.assignEvent(LOCATION_LIST);
            if ("Загадка".equals(e.getType())) {
                riddle = e;
                break;
            }
        }
        assertNotNull("Did not encounter a Загадка event in 500 attempts", riddle);
        assertNotNull(riddle.getCorrectAnswer());
        assertFalse(riddle.getCorrectAnswer().isBlank());
        assertNull(riddle.getLocationEnd());
    }

    @Test
    public void assignEvent_rewardsAreWithinExpectedRange() {
        for (int i = 0; i < 50; i++) {
            Event e = eventsManager.assignEvent(LOCATION_LIST);
            assertTrue("moneyReward out of range", e.getMoneyReward() >= 50 && e.getMoneyReward() < 100);
            assertTrue("xpReward out of range", e.getXpReward() >= 50 && e.getXpReward() < 100);
        }
    }

    @Test
    public void assignEvent_pathfinderDescription_isNotBlank() {
        for (int i = 0; i < 50; i++) {
            Event e = eventsManager.assignEvent(LOCATION_LIST);
            if ("Ходилка".equals(e.getType())) {
                assertFalse("Description must not be blank", e.getDescription().isBlank());
                return; // found one, test passed
            }
        }
    }

    // ---- checkEvent ------------------------------------------------------------

    @Test
    public void checkEvent_riddleWithCorrectAnswer_caseInsensitive_returnsTrue() {
        Event riddle = Event.builder()
                .type("Загадка")
                .correctAnswer("лаб")
                .description("Самый неудачный игродел в истории")
                .moneyReward(50)
                .xpReward(50)
                .build();

        Player player = new Player("tester", "t1");
        player.setAnswer("ЛАБ"); // upper-case — must still match
        // The predicate calls player.getActiveEvent().getCorrectAnswer() — must set activeEvent
        player.setActiveEvent(riddle);

        assertTrue(eventsManager.checkEvent(riddle, player));
    }

    @Test
    public void checkEvent_riddleWithWrongAnswer_returnsFalse() {
        Event riddle = Event.builder()
                .type("Загадка")
                .correctAnswer("лаб")
                .description("Самый неудачный игродел в истории")
                .moneyReward(50)
                .xpReward(50)
                .build();

        Player player = new Player("tester", "t1");
        player.setAnswer("wrong");
        player.setActiveEvent(riddle);

        assertFalse(eventsManager.checkEvent(riddle, player));
    }

    @Test
    public void checkEvent_pathfinderAtDestination_returnsTrue() {
        Event pathfinder = Event.builder()
                .type("Ходилка")
                .locationEnd("магазин")
                .correctAnswer(null)
                .description("Go to магазин")
                .moneyReward(60)
                .xpReward(70)
                .build();

        Player player = new Player("tester", "t1");
        player.setLocation("магазин");
        player.setActiveEvent(pathfinder);

        assertTrue(eventsManager.checkEvent(pathfinder, player));
    }

    @Test
    public void checkEvent_pathfinderNotAtDestination_returnsFalse() {
        Event pathfinder = Event.builder()
                .type("Ходилка")
                .locationEnd("магазин")
                .correctAnswer(null)
                .description("Go to магазин")
                .moneyReward(60)
                .xpReward(70)
                .build();

        Player player = new Player("tester", "t1");
        player.setLocation("таверна"); // wrong location
        player.setActiveEvent(pathfinder);

        assertFalse(eventsManager.checkEvent(pathfinder, player));
    }

    @Test
    public void assignEvent_excludesRespawnFromPathfinderDestination() {
        // Over 500 tries, "респаун" must never be a destination
        for (int i = 0; i < 500; i++) {
            Event e = eventsManager.assignEvent(LOCATION_LIST);
            if ("Ходилка".equals(e.getType())) {
                assertNotEquals("Pathfinder destination must not be 'респаун'",
                        "респаун", e.getLocationEnd());
            }
        }
    }
}
