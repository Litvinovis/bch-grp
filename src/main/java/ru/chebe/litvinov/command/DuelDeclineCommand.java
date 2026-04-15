package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+отказать) — отказаться от дуэли.
 */
public class DuelDeclineCommand implements Command {
    private final IPlayersManager playersManager;

    public DuelDeclineCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.declineDuel(event);
    }
}
