package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+вызов @игрок) — вызов на дуэль.
 */
public class DuelChallengeCommand implements Command {
    private final IPlayersManager playersManager;

    public DuelChallengeCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.challengeDuel(event);
    }
}
