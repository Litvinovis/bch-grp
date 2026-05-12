package ru.chebe.litvinov.service;

import org.junit.Before;
import org.junit.Test;
import ru.chebe.litvinov.data.DailyQuest;
import ru.chebe.litvinov.repository.DailyQuestRepository;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для DailyQuestService.
 */
public class DailyQuestServiceTest {

    private DailyQuestRepository repository;
    private PlayersManager playersManager;
    private DailyQuestService service;

    @Before
    public void setUp() {
        repository = mock(DailyQuestRepository.class);
        playersManager = mock(PlayersManager.class);
        service = new DailyQuestService(repository, playersManager);
    }

    // --- 1. Новый пользователь получает 3 разных квеста ---

    @Test
    public void newUser_gets3DifferentQuests() {
        DailyQuest q = DailyQuestRepository.createRandom("u1", LocalDate.now());

        Set<String> types = Set.of(q.getQuest1Type(), q.getQuest2Type(), q.getQuest3Type());
        assertEquals("Все 3 квеста должны быть разного типа", 3, types.size());
    }

    @Test
    public void newUser_questTypesAreValid() {
        Set<String> validTypes = Set.of("KILL_NPC", "WIN_TAVERN", "EARN_GOLD", "DEFEAT_BOSS");
        DailyQuest q = DailyQuestRepository.createRandom("u1", LocalDate.now());

        assertTrue(validTypes.contains(q.getQuest1Type()));
        assertTrue(validTypes.contains(q.getQuest2Type()));
        assertTrue(validTypes.contains(q.getQuest3Type()));
    }

    @Test
    public void newUser_progressStartsAtZero() {
        DailyQuest q = DailyQuestRepository.createRandom("u1", LocalDate.now());

        assertEquals(0, q.getQuest1Progress());
        assertEquals(0, q.getQuest2Progress());
        assertEquals(0, q.getQuest3Progress());
    }

    // --- 2. incrementProgress корректно увеличивает прогресс ---

    @Test
    public void incrementProgress_increasesProgressForMatchingQuest() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "KILL_NPC", 2);

        assertEquals(2, q.getQuest1Progress());
        assertEquals(0, q.getQuest2Progress());
        assertEquals(0, q.getQuest3Progress());
    }

    @Test
    public void incrementProgress_noMatchingType_doesNotUpdate() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "DEFEAT_BOSS", 1);

        verify(repository, never()).update(any());
    }

    @Test
    public void incrementProgress_cappedAtRequired() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Required(5);
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "KILL_NPC", 100);

        assertEquals(5, q.getQuest1Progress());
    }

    // --- 3. Квест помечается done при достижении цели ---

    @Test
    public void incrementProgress_marksQuestDoneWhenGoalReached() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Required(5);
        q.setQuest1Progress(4);
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "KILL_NPC", 1);

        assertTrue(q.isQuest1Done());
    }

    @Test
    public void incrementProgress_doesNotMarkDoneIfShort() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Required(5);
        q.setQuest1Progress(3);
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "KILL_NPC", 1);

        assertFalse(q.isQuest1Done());
    }

    @Test
    public void incrementProgress_alreadyDone_skipsUpdate() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Done(true);
        q.setQuest1Progress(5);
        q.setQuest1Required(5);
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "KILL_NPC", 1);

        // Already done — no matching undone quest of this type, no update
        verify(repository, never()).update(any());
    }

    // --- 4. bonus_claimed устанавливается когда все 3 выполнены ---

    @Test
    public void allQuestsDone_bonusIsClaimed() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Done(true);
        q.setQuest2Done(true);
        // Quest 3: one step away
        q.setQuest3Required(3);
        q.setQuest3Progress(2);
        q.setBonusClaimed(false);
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "EARN_GOLD", 1);

        assertTrue(q.isQuest3Done());
        verify(repository).claimBonus("u1");
        verify(playersManager).changeXp("u1", DailyQuestService.BONUS_XP);
        verify(playersManager).changeMoney("u1", DailyQuestService.BONUS_MONEY, true);
    }

    @Test
    public void allQuestsDone_butBonusAlreadyClaimed_noDuplicateBonus() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Done(true);
        q.setQuest2Done(true);
        q.setQuest3Required(3);
        q.setQuest3Progress(2);
        q.setBonusClaimed(true); // already claimed
        when(repository.getOrCreate("u1")).thenReturn(q);

        service.incrementProgress("u1", "EARN_GOLD", 1);

        verify(repository, never()).claimBonus(any());
        verify(playersManager, never()).changeXp(any(), anyInt());
    }

    // --- 5. Квесты сбрасываются на следующий день ---

    @Test
    public void questsResetNextDay_newRecordCreated() {
        // Today's quests — not in repo for tomorrow's date
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        DailyQuest tomorrowQuest = DailyQuestRepository.createRandom("u1", tomorrow);

        // When called with tomorrow's date, getOrCreate returns new quests
        assertNotNull(tomorrowQuest);
        assertEquals(tomorrow, tomorrowQuest.getQuestDate());
        assertEquals(0, tomorrowQuest.getQuest1Progress());
        assertFalse(tomorrowQuest.isBonusClaimed());
    }

    @Test
    public void getDailyQuests_delegatesToRepository() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        when(repository.getOrCreate("u1")).thenReturn(q);

        DailyQuest result = service.getDailyQuests("u1");

        assertSame(q, result);
        verify(repository).getOrCreate("u1");
    }

    // --- formatQuests ---

    @Test
    public void formatQuests_containsQuestLabels() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Required(5);
        q.setQuest2Required(3);
        q.setQuest3Required(200);

        String output = service.formatQuests(q);

        assertTrue(output.contains("Убить мобов"));
        assertTrue(output.contains("Победить в таверне"));
        assertTrue(output.contains("Заработать монет"));
    }

    @Test
    public void formatQuests_doneQuestShowsCheckmark() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Required(5);
        q.setQuest1Progress(5);
        q.setQuest1Done(true);
        q.setQuest2Required(3);
        q.setQuest3Required(200);

        String output = service.formatQuests(q);

        assertTrue(output.contains("✅"));
        assertTrue(output.contains("⬜"));
    }

    @Test
    public void formatQuests_allDone_showsCelebrationMessage() {
        DailyQuest q = questWithTypes("KILL_NPC", "WIN_TAVERN", "EARN_GOLD");
        q.setQuest1Done(true); q.setQuest1Required(5); q.setQuest1Progress(5);
        q.setQuest2Done(true); q.setQuest2Required(3); q.setQuest2Progress(3);
        q.setQuest3Done(true); q.setQuest3Required(200); q.setQuest3Progress(200);
        q.setBonusClaimed(false);

        String output = service.formatQuests(q);

        assertTrue(output.contains("🎉 Все дневные квесты выполнены!"));
    }

    // --- helpers ---

    private DailyQuest questWithTypes(String t1, String t2, String t3) {
        DailyQuest q = new DailyQuest();
        q.setUserId("u1");
        q.setQuestDate(LocalDate.now());
        q.setQuest1Type(t1); q.setQuest1Required(5);
        q.setQuest2Type(t2); q.setQuest2Required(3);
        q.setQuest3Type(t3); q.setQuest3Required(200);
        return q;
    }
}
