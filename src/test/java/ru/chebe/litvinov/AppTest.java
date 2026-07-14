package ru.chebe.litvinov;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {

    @Test
    public void resolveDiscordToken_handlesMissingOrPresentEnv() {
        // В тестовом окружении переменная может быть как задана, так и нет —
        // проверяем корректную обработку обоих случаев.
        var token = App.resolveDiscordToken();
        if (System.getenv("BCHGRP_DISCORD_TOKEN") == null || System.getenv("BCHGRP_DISCORD_TOKEN").trim().isEmpty()) {
            assertTrue(token.isEmpty());
        } else {
            assertTrue(token.isPresent());
            assertFalse(token.get().isBlank());
        }
    }
}
