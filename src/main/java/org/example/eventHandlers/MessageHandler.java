package org.example.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.example.data.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.Constants.*;

@Slf4j
public class MessageHandler extends ListenerAdapter {
	private static Ignite ignite;
	private static IgniteCache<String, Player> cache;
	private final Logger logger = LoggerFactory.getLogger("default-logger");
	private final RegistrationManager registrationManager;
	private final PlayersManagement playersManagement;
	private final Tavern tavern;

	public MessageHandler(Ignite ignite) {
		MessageHandler.ignite = ignite;
		cache = ignite.cache("players");
		this.registrationManager = new RegistrationManager(cache);
		this.playersManagement = new PlayersManagement(cache);
		this.tavern = new Tavern(cache);
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		try {
			if (isBotAsking(event)) {
				String content = event.getMessage().getContentDisplay();
				if (content.startsWith("+регистрация")) {
					event.getChannel().sendMessage(registrationManager.createPlayer(event.getMessage().getAuthor().getName()))
									.submit();
				} else if (cache.get(event.getMessage().getAuthor().getName()) == null) {
					event.getChannel().sendMessage(NEED_REGISTRATION)
									.submit();
				} else if (content.startsWith("+стата")) {
					event.getChannel().sendMessage(playersManagement.getPlayerInfo(event.getMessage().getAuthor().getName()))
									.submit();
				} else if (content.startsWith("+кости")) {
					event.getChannel().sendMessage(tavern.dieCast(event))
									.submit();
				} else {
					event.getChannel().sendMessage(UNKNOWN_COMMAND)
									.submit();
				}
			}
		} catch (Exception e) {
			logger.error(MESSAGE_PROCESS_FAILED, e.getMessage());
		}
	}

	private boolean isBotAsking(MessageReceivedEvent event) {
		return event.getMessage().getContentDisplay().startsWith("+") && event.getChannel().asTextChannel().getName().equals("бч-грп");
	}
}
