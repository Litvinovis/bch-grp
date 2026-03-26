package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+новый клан) для создания нового клана.
 */
public class ClanRegisterCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду создания нового клана.
     *
     * @param playersManager менеджер игроков
     */
    public ClanRegisterCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.clanRegister(event);
    }
}
