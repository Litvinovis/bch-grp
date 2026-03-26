package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+кнб) для игры «камень-ножницы-бумага» против AI в таверне.
 */
public class RockPaperScissorsCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду игры «камень-ножницы-бумага».
     *
     * @param playersManager менеджер игроков
     */
    public RockPaperScissorsCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.rockPaperScissors(event);
    }
}
