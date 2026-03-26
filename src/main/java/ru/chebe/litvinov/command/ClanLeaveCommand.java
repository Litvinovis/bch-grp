package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+покинуть клан) для выхода игрока из клана.
 */
public class ClanLeaveCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду выхода из клана.
     *
     * @param playersManager менеджер игроков
     */
    public ClanLeaveCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.clanLeave(event);
    }
}
