package ru.chebe.litvinov.command;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class CommandRegistryTest {

    private CommandRegistry registry;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    public void testRegisterAndResolve() {
        Command mockCmd = mock(Command.class);
        registry.register("+тест", mockCmd);

        Optional<Command> result = registry.resolve("+тест что-то");
        assertTrue(result.isPresent());
        assertSame(mockCmd, result.get());
    }

    @Test
    public void testResolveUnknownCommand() {
        Optional<Command> result = registry.resolve("+несуществующая");
        assertFalse(result.isPresent());
    }

    @Test
    public void testRegisterAdminCommand() {
        Command mockCmd = mock(Command.class);
        registry.registerAdmin("+admincmd", mockCmd);

        assertTrue(registry.isAdminCommand("+admincmd test"));
        assertFalse(registry.isAdminCommand("+обычная"));
    }

    @Test
    public void testLongerPrefixFirst() {
        Command shortCmd = mock(Command.class);
        Command longCmd = mock(Command.class);

        // Длинный регистрируется первым
        registry.register("+убить босса", longCmd);
        registry.register("+убить", shortCmd);

        Optional<Command> result = registry.resolve("+убить босса горгона");
        assertTrue(result.isPresent());
        assertSame(longCmd, result.get());
    }

    @Test
    public void testShortPrefixMatchesShort() {
        Command shortCmd = mock(Command.class);
        Command longCmd = mock(Command.class);

        registry.register("+убить босса", longCmd);
        registry.register("+убить", shortCmd);

        Optional<Command> result = registry.resolve("+убить кого-то");
        assertTrue(result.isPresent());
        // Длинный не совпадает, должен совпасть короткий
        assertSame(shortCmd, result.get());
    }

    @Test
    public void testCommandExecuteIsCalled() {
        Command mockCmd = mock(Command.class);
        registry.register("+привет", mockCmd);

        MessageReceivedEvent mockEvent = mock(MessageReceivedEvent.class);
        Optional<Command> cmd = registry.resolve("+привет мир");
        assertTrue(cmd.isPresent());
        cmd.get().execute(mockEvent);
        verify(mockCmd, times(1)).execute(mockEvent);
    }

    @Test
    public void testMultipleCommandsRegistered() {
        Command cmd1 = mock(Command.class);
        Command cmd2 = mock(Command.class);
        Command cmd3 = mock(Command.class);

        registry.register("+один", cmd1);
        registry.register("+два", cmd2);
        registry.register("+три", cmd3);

        assertSame(cmd1, registry.resolve("+один аргумент").orElse(null));
        assertSame(cmd2, registry.resolve("+два аргумент").orElse(null));
        assertSame(cmd3, registry.resolve("+три аргумент").orElse(null));
    }

    @Test
    public void testNonAdminCommandIsNotAdmin() {
        Command cmd = mock(Command.class);
        registry.register("+обычная", cmd);
        assertFalse(registry.isAdminCommand("+обычная что-то"));
    }
}
