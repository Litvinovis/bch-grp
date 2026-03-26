package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+кости) для игры в кости на деньги в таверне.
 */
public class DieCastCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду игры в кости.
     *
     * @param playersManager менеджер игроков
     */
    public DieCastCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.dieCast(event);
    }
}
