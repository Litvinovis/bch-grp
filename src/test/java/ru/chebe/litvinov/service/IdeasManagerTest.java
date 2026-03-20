package ru.chebe.litvinov.service;

import org.apache.ignite.IgniteCache;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Idea;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for IdeasManager.findIdea — the core logic that parses the idea number
 * from raw command strings and delegates to the cache.
 */
public class IdeasManagerTest {

    @Mock
    private IgniteCache<Integer, Idea> ideaCache;

    private IdeasManager ideasManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ideasManager = new IdeasManager(ideaCache);
    }

    // ---- findIdea --------------------------------------------------------------

    @Test
    public void findIdea_shortInput_trimAndParsesNumber() {
        Idea expected = Idea.builder().id(5).author("me").description("desc").resolution("Новая").build();
        when(ideaCache.get(5)).thenReturn(expected);

        Idea result = ideasManager.findIdea(" 5");

        assertNotNull(result);
        assertEquals(5, result.getId());
        verify(ideaCache).get(5);
    }

    @Test
    public void findIdea_shortInputNoSpaces_parsesExactNumber() {
        Idea expected = Idea.builder().id(0).author("bot").description("x").resolution("ok").build();
        when(ideaCache.get(0)).thenReturn(expected);

        Idea result = ideasManager.findIdea("0");

        assertEquals(0, result.getId());
    }

    @Test
    public void findIdea_longInput_takesNumberBeforeFirstSpace() {
        // Input length > 4 → takes substring up to first space
        Idea expected = Idea.builder().id(12).author("user").description("long text here").resolution("Новая").build();
        when(ideaCache.get(12)).thenReturn(expected);

        // "12 some reason" — length > 4
        Idea result = ideasManager.findIdea("12 some reason");

        assertEquals(12, result.getId());
        verify(ideaCache).get(12);
    }

    @Test
    public void findIdea_cacheReturnsNull_returnsNull() {
        when(ideaCache.get(999)).thenReturn(null);

        Idea result = ideasManager.findIdea("999");

        assertNull(result);
    }

    @Test
    public void findIdea_shortInputWithSpaces_trimmedProperly() {
        Idea expected = Idea.builder().id(3).author("a").description("b").resolution("c").build();
        when(ideaCache.get(3)).thenReturn(expected);

        Idea result = ideasManager.findIdea("  3");

        assertEquals(3, result.getId());
    }

    // ---- Idea toString sanity (cross-check with DomainModelTest) ---------------

    @Test
    public void ideaToString_includesIdAuthorDescriptionResolution() {
        Idea idea = Idea.builder().id(42).author("tester").description("my idea").resolution("В работе").build();
        String s = idea.toString();

        assertTrue(s.contains("42"));
        assertTrue(s.contains("tester"));
        assertTrue(s.contains("my idea"));
        assertTrue(s.contains("В работе"));
    }
}
