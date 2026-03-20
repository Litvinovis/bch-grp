package ru.chebe.litvinov.raid;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.command.Command;
import ru.chebe.litvinov.data.Player;

/**
 * Команда +рейд — создать новый рейд в текущем канале.
 */
public class RaidCommand implements Command {

    private final RaidManager raidManager;

    public RaidCommand(RaidManager raidManager) {
        this.raidManager = raidManager;
    }

    @Override
    public void execute(MessageReceivedEvent event) {
        // Player передаётся через контекст — здесь используем делегирование через RaidManagerFacade
        // Фактически Player получается в MessageHandler и прокидывается через RaidManager.
        // Для единообразия команда принимает event и сама получает игрока через channel.
        event.getChannel().sendMessage("Используйте команду +рейд из MessageHandler (реализация в RaidManager)").queue();
    }

    /**
     * Основной вариант вызова — с уже загруженным объектом Player.
     */
    public void execute(MessageReceivedEvent event, Player player) {
        String result = raidManager.createRaid(player, event.getChannel());
        event.getChannel().sendMessage(result).queue();
    }
}
