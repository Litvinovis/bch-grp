package ru.chebe.litvinov.raid;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.command.Command;
import ru.chebe.litvinov.data.Player;

/**
 * Команда +присоединиться — вступить в активный рейд.
 */
public class RaidJoinCommand implements Command {

    private final RaidManager raidManager;

    /**
     * Создаёт команду вступления в рейд.
     *
     * @param raidManager менеджер рейдов
     */
    public RaidJoinCommand(RaidManager raidManager) {
        this.raidManager = raidManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        event.getChannel().sendMessage("Используйте команду +присоединиться из MessageHandler (реализация в RaidManager)").queue();
    }

    /**
     * Основной вариант вызова — с уже загруженным объектом Player.
     *
     * @param event  событие Discord-сообщения
     * @param player объект игрока, вступающего в рейд
     */
    public void execute(MessageReceivedEvent event, Player player) {
        String result = raidManager.joinRaid(player, event.getChannel());
        event.getChannel().sendMessage(result).queue();
    }
}
