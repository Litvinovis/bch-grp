package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+использовать) для применения активируемого предмета из инвентаря.
 */
public class UseItemCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду использования предмета.
     *
     * @param playersManager менеджер игроков
     */
    public UseItemCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.useItem(event);
    }
}
