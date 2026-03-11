package ru.chebe.litvinov.eventHandlers;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class MessageHandlerSmokeTest {

    @Test
    public void messageHandler_classAvailable() {
        assertNotNull(MessageHandler.class);
    }
}
