package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

/**
 * Команда (+бонус) для получения ежедневного бонуса (100 монет).
 */
public class DailyBonusCommand implements Command {
    private final IPlayersManager playersManager;

    /**
     * Создаёт команду получения ежедневного бонуса.
     *
     * @param playersManager менеджер игроков
     */
    public DailyBonusCommand(IPlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        playersManager.dailyBonus(event);
    }
}
