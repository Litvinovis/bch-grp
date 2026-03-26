package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+регистрация) для создания нового игрового персонажа.
 */
public class RegistrationCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду регистрации нового игрока.
     *
     * @param playersManager менеджер игроков
     */
    public RegistrationCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.createPlayer(event);
    }
}
