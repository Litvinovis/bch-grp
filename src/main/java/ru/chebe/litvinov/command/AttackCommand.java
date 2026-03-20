package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда атаки босса текущей локации (+убить босса).
 */
public class AttackCommand implements Command {
    private final IPlayersManager playersManager;

    public AttackCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.bossFight(event);
    }
}
