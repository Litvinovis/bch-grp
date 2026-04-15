package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+достижения) — список разблокированных достижений игрока.
 */
public class AchievementsCommand implements Command {
    private final IPlayersManager playersManager;

    public AchievementsCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.getAchievements(event);
    }
}
