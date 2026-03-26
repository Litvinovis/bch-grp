package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+клан инфо) для просмотра информации о клане.
 */
public class ClanInfoCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду просмотра информации о клане.
     *
     * @param playersManager менеджер игроков
     */
    public ClanInfoCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.clanInfo(event);
    }
}
