package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+число) для игры «угадай число» в таверне.
 */
public class GuessNumberCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду игры «угадай число».
     *
     * @param playersManager менеджер игроков
     */
    public GuessNumberCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.guessTheNumber(event);
    }
}
