package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+продать) для продажи предмета из инвентаря.
 */
public class SellItemCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду продажи предмета.
     *
     * @param playersManager менеджер игроков
     */
    public SellItemCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.sellItem(event);
    }
}
