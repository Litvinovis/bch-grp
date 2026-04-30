package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Управляет состоянием дуэлей: ожидающие вызовы, принятие, отказ.
 */
class DuelService {

	private final ConcurrentHashMap<String, String> pendingDuels = new ConcurrentHashMap<>();
	private final PlayerRepository playerCache;
	private final Function<String, ReentrantLock> lockProvider;
	private final BiConsumer<Player, String> achievementUnlocker;
	private final Random random = new Random();

	DuelService(PlayerRepository playerCache,
	            Function<String, ReentrantLock> lockProvider,
	            BiConsumer<Player, String> achievementUnlocker) {
		this.playerCache = playerCache;
		this.lockProvider = lockProvider;
		this.achievementUnlocker = achievementUnlocker;
	}

	/**
	 * Вызов игрока на дуэль (+вызов @игрок).
	 */
	void challengeDuel(MessageReceivedEvent event) {
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите соперника: +вызов @игрок").submit();
			return;
		}
		String challengerId = event.getAuthor().getId();
		String challengedId = mentions.get(0).getId();

		if (challengerId.equals(challengedId)) {
			event.getChannel().sendMessage("Нельзя вызвать самого себя на дуэль.").submit();
			return;
		}
		if (!playerCache.contains(challengedId)) {
			event.getChannel().sendMessage("Игрок не зарегистрирован в игре.").submit();
			return;
		}
		if (pendingDuels.containsKey(challengedId)) {
			event.getChannel().sendMessage("У этого игрока уже есть активный вызов на дуэль.").submit();
			return;
		}
		pendingDuels.put(challengedId, challengerId);
		Player challenger = playerCache.get(challengerId);
		event.getChannel().sendMessage(mentions.get(0).getAsMention() + ", **" + challenger.getNickName()
				+ "** вызывает вас на дуэль! Напишите `+принять` или `+отказать`.").submit();
	}

	/**
	 * Принять вызов на дуэль (+принять).
	 */
	void acceptDuel(MessageReceivedEvent event) {
		String challengedId = event.getAuthor().getId();
		String challengerId = pendingDuels.remove(challengedId);
		if (challengerId == null) {
			event.getChannel().sendMessage("У вас нет активных вызовов на дуэль.").submit();
			return;
		}
		if (!playerCache.contains(challengerId)) {
			event.getChannel().sendMessage("Противник больше не в игре.").submit();
			return;
		}

		boolean challengerFirst = challengerId.compareTo(challengedId) < 0;
		ReentrantLock first = challengerFirst ? lockProvider.apply(challengerId) : lockProvider.apply(challengedId);
		ReentrantLock second = challengerFirst ? lockProvider.apply(challengedId) : lockProvider.apply(challengerId);
		first.lock();
		try {
			second.lock();
			try {
				Player challenger = playerCache.get(challengerId);
				Player challenged = playerCache.get(challengedId);

				int challengerRoll = challenger.getStrength() + random.nextInt(Math.max(challenger.getLuck(), 1)) + random.nextInt(20) + 1;
				int challengedRoll = challenged.getStrength() + random.nextInt(Math.max(challenged.getLuck(), 1)) + random.nextInt(20) + 1;

				Player winner = challengerRoll >= challengedRoll ? challenger : challenged;
				Player loser = challengerRoll >= challengedRoll ? challenged : challenger;

				int prize = 100;
				winner.setMoney(winner.getMoney() + prize);
				loser.setMoney(Math.max(0, loser.getMoney() - prize / 2));
				winner.setReputation(winner.getReputation() + 5);
				achievementUnlocker.accept(winner, "дуэлянт");

				playerCache.put(challenger.getId(), challenger);
				playerCache.put(challenged.getId(), challenged);

				event.getChannel().sendMessage(String.format(
						"Дуэль: **%s** (бросок %d) vs **%s** (бросок %d)\nПобедитель: **%s** (+%d монет, +5 репутации)\nПроигравший: **%s** (-%d монет)",
						challenger.getNickName(), challengerRoll,
						challenged.getNickName(), challengedRoll,
						winner.getNickName(), prize,
						loser.getNickName(), prize / 2
				)).submit();
			} finally {
				second.unlock();
			}
		} finally {
			first.unlock();
		}
	}

	/**
	 * Отказаться от дуэли (+отказать).
	 */
	void declineDuel(MessageReceivedEvent event) {
		String challengedId = event.getAuthor().getId();
		String challengerId = pendingDuels.remove(challengedId);
		if (challengerId == null) {
			event.getChannel().sendMessage("У вас нет активных вызовов на дуэль.").submit();
			return;
		}
		Player challenged = playerCache.get(challengedId);
		event.getChannel().sendMessage("**" + challenged.getNickName() + "** отказался от дуэли. Трус!").submit();
	}
}
