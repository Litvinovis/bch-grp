package ru.chebe.litvinov.eventHandlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MessageHandlerSmokeTest {

    @Test
    public void messageHandler_classAvailable() {
        assertNotNull(MessageHandler.class);
    }
}
