package org.example.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.example.data.Items;
import org.example.data.Location;
import org.example.data.Player;
import org.example.service.ItemsManager;
import org.example.service.LocationManager;
import org.example.service.PlayersManager;
import org.example.service.Tavern;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.Constants.*;

@Slf4j
public class MessageHandler extends ListenerAdapter {
	private static final String HELP_MESSAGE = helpMessageCreate();
	private static IgniteCache<String, Player> playerCache;
	private final Logger logger = LoggerFactory.getLogger("default-logger");
	private final ItemsManager itemsManager;
	private final PlayersManager playersManager;
	private final LocationManager locationManager;
	private final Tavern tavern;

	public MessageHandler(Ignite ignite) {
		playerCache = ignite.cache("players");
		IgniteCache<String, Location> locationCache = ignite.cache("locations");
		IgniteCache<String, Items> itemsCache = ignite.cache("items");
		this.itemsManager = new ItemsManager(playerCache, itemsCache);
		this.locationManager = new LocationManager(locationCache, playerCache);
		this.playersManager = new PlayersManager(playerCache);
		this.tavern = new Tavern(playerCache);
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		try {
			if (isBotAsking(event)) {
				String content = event.getMessage().getContentDisplay();
				if (content.startsWith("+регистрация")) {
					playersManager.createPlayer(event);
				} else if (playerCache.get(event.getMessage().getAuthor().getName()) == null) {
					event.getChannel().sendMessage(NEED_REGISTRATION)
									.submit();
				} else if (content.startsWith("+стата")) {
					playersManager.getPlayerInfo(event);
				} else if (content.startsWith("+кости")) {
					tavern.dieCast(event);
				} else if (content.startsWith("+помощь")) {
					event.getChannel().sendMessage(HELP_MESSAGE).submit();
				} else if (content.startsWith("+локация")) {
					locationManager.locationInfo(event);
				} else if (content.startsWith("+карта")) {
					locationManager.map(event);
				} else if (content.startsWith("+идти")) {
					locationManager.move(event);
				} else if (content.startsWith("+инвентарь")) {
					playersManager.getInventoryInfo(event);
				} else {
					event.getChannel().sendMessage(UNKNOWN_COMMAND).submit();
				}
			}
		} catch (Exception e) {
			logger.error(MESSAGE_PROCESS_FAILED, e.getMessage());
		}
	}

	private boolean isBotAsking(MessageReceivedEvent event) {
		return event.getMessage().getContentDisplay().startsWith("+") && event.getChannel().asTextChannel().getName().equals("бч-грп");
	}

	private static String helpMessageCreate() {
		return """
						Для игры доступны следующие команды:
						+стата - выводит всю информацию о вашем игровом персонаже
						+кости (ставка) - игра в кидание кубиков, доступна только в локации таверна
						+помощь - информация о доступных командах
						+идти (название локации) - перемещает вас в указанную локацию
						+локация (название локации) - подробная информация об указанной локации
						+карта - карта бч-грп
						+инвентарь - список ваших предметов без подробного описания
						+инвентарь (название предмета) - подробная информация о предмете, не обязательно из вашего инвенторя
						""";
	}
}
