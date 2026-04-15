package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+топ [уровень|деньги|репутация]) — таблица лидеров top-10.
 */
public class TopCommand implements Command {
    private final IPlayersManager playersManager;

    public TopCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.topLeaderboard(event);
    }
}
