package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+вступить в клан) для подачи заявки на вступление в клан.
 */
public class ClanJoinCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду подачи заявки на вступление в клан.
     *
     * @param playersManager менеджер игроков
     */
    public ClanJoinCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.clanJoin(event);
    }
}
