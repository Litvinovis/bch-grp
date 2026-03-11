package ru.chebe.litvinov;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConstantsTest {

    @Test
    public void constants_haveExpectedSanityValues() {
        assertNotNull(Constants.UNKNOWN_COMMAND);
        assertTrue(Constants.MAX_CLAN_SIZE > 0);
        assertTrue(Constants.MIN_LVL_TO_CLAN_CREATE >= Constants.MIN_LVL_TO_CLAN_JOIN);
    }
}
