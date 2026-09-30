package ru.chebe.litvinov.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * +проиграть и +победить — шуточные команды для одного игрока. Его Discord ID задаётся
 * переменной окружения BCHGRP_LAB_USER_ID, а не в коде: репозиторий публичный. Остальным
 * игрокам команды не отвечают, в +помощь их нет.
 */
public final class LabCommand implements Command {

	static final String MESSAGE = "Лаб ты проиграл";

	private final String allowedUserId;

	public LabCommand(String allowedUserId) {
		this.allowedUserId = allowedUserId == null ? "" : allowedUserId.trim();
	}

	public static LabCommand fromEnv() {
		return new LabCommand(System.getenv("BCHGRP_LAB_USER_ID"));
	}

	@Override
	public void execute(MessageReceivedEvent event) {
		if (allowedUserId.isEmpty() || !allowedUserId.equals(event.getAuthor().getId())) return;
		event.getChannel().sendMessage(MESSAGE).submit();
	}
}
