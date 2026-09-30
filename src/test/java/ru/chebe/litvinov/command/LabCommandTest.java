package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LabCommandTest {

	private static MessageReceivedEvent event(String authorId, MessageChannelUnion channel) {
		MessageReceivedEvent e = mock(MessageReceivedEvent.class);
		User u = mock(User.class);
		when(u.getId()).thenReturn(authorId);
		when(e.getAuthor()).thenReturn(u);
		when(e.getChannel()).thenReturn(channel);
		return e;
	}

	private static MessageChannelUnion channel() {
		MessageChannelUnion ch = mock(MessageChannelUnion.class);
		MessageCreateAction action = mock(MessageCreateAction.class);
		when(action.submit()).thenReturn(CompletableFuture.completedFuture(null));
		when(ch.sendMessage(anyString())).thenReturn(action);
		return ch;
	}

	@Test
	void allowedUser_getsTheMessage() {
		MessageChannelUnion ch = channel();
		new LabCommand("111").execute(event("111", ch));
		verify(ch).sendMessage("Лаб ты проиграл");
	}

	@Test
	void otherUsers_andUnsetId_getNothing() {
		MessageChannelUnion ch = channel();
		new LabCommand("111").execute(event("222", ch));
		new LabCommand(null).execute(event("111", ch));
		verify(ch, never()).sendMessage(anyString());
	}
}
