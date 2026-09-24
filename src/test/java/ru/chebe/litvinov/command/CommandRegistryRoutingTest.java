package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.service.ItemsManager;
import ru.chebe.litvinov.service.interfaces.IIdeasManager;
import ru.chebe.litvinov.service.interfaces.ILocationManager;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Маршрутизация реального набора команд: короткий префикс не должен перехватывать длинный.
 */
class CommandRegistryRoutingTest {

	private IPlayersManager players;
	private IIdeasManager ideas;
	private ILocationManager locations;
	private CommandRegistry registry;
	private final MessageReceivedEvent event = mock(MessageReceivedEvent.class);

	@BeforeEach
	void setUp() {
		players = mock(IPlayersManager.class);
		ideas = mock(IIdeasManager.class);
		locations = mock(ILocationManager.class);
		registry = CommandRegistry.build(players, ideas, locations, mock(ItemsManager.class), "help", "info");
	}

	private void run(String content) {
		registry.resolve(content).orElseThrow(() -> new AssertionError("не найдено: " + content)).execute(event);
	}

	@Test
	void adminIdeaCommands_notSwallowedByPutIdea() {
		run("+идеяномер 5");
		run("+идеястатус 5 \"готово\"");

		verify(ideas).getIdea(event);
		verify(ideas).changeIdeaStatus(event);
		verify(ideas, never()).putIdea(event);
	}

	@Test
	void plainIdea_stillCreatesIdea_andIsNotAdmin() {
		run("+идея добавить драконов");

		verify(ideas).putIdea(event);
		assertFalse(registry.isAdminCommand("+идея добавить драконов"));
		assertTrue(registry.isAdminCommand("+идеяномер 5"));
	}

	@Test
	void clanMap_notSwallowedByWorldMap() {
		run("+карта кланов");
		run("+карта");

		verify(players).territoryClanMap(event);
		verify(locations).map(event);
	}

	@Test
	void helpListedCommands_areRouted() {
		run("+создать кованый меч");
		run("+еженедельные");

		verify(players).craftProfessionItem(event);
		verify(players).weeklyBoard(event);
	}

	@Test
	void resourceSale_routedSeparatelyFromItemSale() {
		run("+продать ресурс руда 3");
		run("+продать меч");

		verify(players).sellResource(event);
		verify(players).sellItem(event);
	}
}
