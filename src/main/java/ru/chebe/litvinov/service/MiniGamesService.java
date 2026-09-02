package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Игры таверны и скачки: рулетка, камень-ножницы-бумага, угадай число,
 * кости, покер и забеги на маунтах.
 * Вынесено из PlayersManager, который совмещал их с боями, инвентарём и кланами.
 */
public class MiniGamesService {

	private final PlayerRepository playerCache;
	private final Tavern tavern;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;
	private final QuestProgressTracker quests;

	public MiniGamesService(PlayerRepository playerCache, Tavern tavern, PlayerLocks playerLocks,
	                        AchievementService achievements, QuestProgressTracker quests) {
		this.playerCache = playerCache;
		this.tavern = tavern;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
		this.quests = quests;
	}

	/**
	 * Обрабатывает команду игры в рулетку. Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой и выбором
	 */
	public void playRoulette(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Играть в рулетку можно только в таверне!").queue();
			return;
		}

		String[] parts = event.getMessage().getContentDisplay().split(" ");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +рулетка [ставка] [красный/черный/0-36]").queue();
			return;
		}

		try {
			int bid = Integer.parseInt(parts[1]);
			String bet = parts[2];
			ReentrantLock lock = playerLocks.get(player.getId());
			lock.lock();
			try {
				player = playerCache.get(player.getId());
				int moneyBeforeRoulette = player.getMoney();
				player = tavern.playRoulette(event, player, bid, bet);
				if (player.getMoney() > moneyBeforeRoulette) {
					quests.progress(player.getId(), "WIN_TAVERN", 1);
					quests.progress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBeforeRoulette);
				}
				playerCache.put(player.getId(), player);
			} finally {
				lock.unlock();
			}
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки!").queue();
		}
	}


	/**
	 * Обрабатывает команду игры «камень-ножницы-бумага». Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой и выбором
	 */
	public void rockPaperScissors(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Играть можно только в таверне!").queue();
			return;
		}

		String[] parts = event.getMessage().getContentDisplay().split(" ");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +кнб [ставка] [камень/ножницы/бумага]").queue();
			return;
		}

		try {
			int bid = Integer.parseInt(parts[1]);
			String choice = parts[2];
			ReentrantLock lock = playerLocks.get(player.getId());
			lock.lock();
			try {
				player = playerCache.get(player.getId());
				int moneyBeforeKnb = player.getMoney();
				player = tavern.rockPaperScissors(event, player, bid, choice);
				if (player.getMoney() > moneyBeforeKnb) {
					quests.progress(player.getId(), "WIN_TAVERN", 1);
					quests.progress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBeforeKnb);
				}
				playerCache.put(player.getId(), player);
			} finally {
				lock.unlock();
			}
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки!").queue();
		}
	}


	/**
	 * Обрабатывает команду игры «угадай число». Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой и числом-предположением
	 */
	public void guessTheNumber(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Играть можно только в таверне!").queue();
			return;
		}

		String[] parts = event.getMessage().getContentDisplay().split(" ");
		if (parts.length < 3) {
			event.getChannel().sendMessage("Использование: +число [ставка] [число от 1 до 10]").queue();
			return;
		}

		try {
			int bid = Integer.parseInt(parts[1]);
			int guess = Integer.parseInt(parts[2]);

			if (guess < 1 || guess > 10) {
				event.getChannel().sendMessage("Число должно быть от 1 до 10!").queue();
				return;
			}

			ReentrantLock lock = playerLocks.get(player.getId());
			lock.lock();
			try {
				player = playerCache.get(player.getId());
				int moneyBefore = player.getMoney();
				player = tavern.guessTheNumber(event, player, bid, guess);
				// Квест «Везунчик»: победа зафиксирована по увеличению денег сверх ставки
				if (player.getMoney() > moneyBefore && player.getActiveEvent() != null
						&& "Везунчик".equals(player.getActiveEvent().getType())) {
					player.getActiveEvent().setAttempt(1);
				}
				if (player.getMoney() > moneyBefore) {
					quests.progress(player.getId(), "WIN_TAVERN", 1);
					quests.progress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBefore);
				}
				playerCache.put(player.getId(), player);
			} finally {
				lock.unlock();
			}
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Неверный формат ставки или числа!").queue();
		}
	}


	/**
	 * Обрабатывает команду игры в кости. Доступно только в локации «таверна».
	 *
	 * @param event событие Discord-сообщения со ставкой
	 */
	public void dieCast(MessageReceivedEvent event) {
		ReentrantLock dieLock = playerLocks.get(event.getAuthor().getId());
		dieLock.lock();
		try {
		var player = playerCache.get(event.getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Как ты собрался бросить кости если ты не в таверне? Метнись кабанчиком сначала туда").submit();
			return;
		}
		String raw = event.getMessage().getContentDisplay();
		String bidText = raw.length() > 7 ? raw.substring(7).trim() : "";
		if (bidText.isEmpty()) {
			event.getChannel().sendMessage("Мы на деньги играем, ты забыл ставку указать, от 1 до 100").submit();
			return;
		}
		int bid;
		try {
			bid = Integer.parseInt(bidText);
		} catch (Exception e) {
			event.getChannel().sendMessage("Ну и что я с твоим " + bidText + " должен делать? Нахер он мне нужен, я только на деньги играю").submit();
			return;
		}
		if (bid < 0) {
			event.getChannel().sendMessage("Ты тестировщик или просто давно по хлебопечке не получал? Ставь нормально").submit();
			return;
		} else if (bid > 100) {
			event.getChannel().sendMessage("Ого к нам мсье мажор пожаловал и давай выёбуваться ставками, не так не пойдет, давай не больше 100").submit();
			return;
		}
		if (player.getMoney() < bid) {
			event.getChannel().sendMessage("Я ж вижу, что у тебя таких денег отродясь не было, а нанимать ябыса трясти с тебя долг я не хочу").submit();
			return;
		}
		int moneyBeforeDice = player.getMoney();
		player = tavern.diceStart(event, player, bid);
		if (player.getMoney() > moneyBeforeDice) {
			quests.progress(player.getId(), "WIN_TAVERN", 1);
			quests.progress(player.getId(), "EARN_GOLD", player.getMoney() - moneyBeforeDice);
		}
		playerCache.put(player.getId(), player);
		} finally {
			dieLock.unlock();
		}
	}


	/** +гонки маунтов — гонки на маунтах */
	public void mountRacingInfo(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		String mountStatus = player.isHasMount() ? "✅ У тебя есть маунт!" : "❌ Маунта нет (добудь **маунт ветра** из редкого дропа)";
		event.getChannel().sendMessage("🏇 **Гонки маунтов**\n" + mountStatus +
			"\nС маунтом: время перемещения сокращается вдвое.\n" +
			"Участвовать в гонке: **+гонки маунтов старт**").submit();
	}


	public void mountRacingRun(MessageReceivedEvent event) {
		String id = event.getAuthor().getId();
		Player player = playerCache.get(id);
		if (!player.isHasMount()) {
			event.getChannel().sendMessage("Для участия нужен маунт. Добудь **маунт ветра** из редкого дропа.").submit();
			return;
		}
		String[] path = {"респаун", "мейн", "деградач", "кушетка", "олимп"};
		int totalSteps = path.length - 1; // 4 steps to finish

		int playerPos = 0;
		int[] npcPos = {0, 0, 0};
		int[] npcSpeeds = {1, 2, 1};
		String[] npcNames = {"Быстрец", "Гончий", "Летун"};

		var sb = new StringBuilder("🏇 **Гонка маунтов** началась! Маршрут: " + String.join(" → ", path) + "\n\n");
		int round = 0;
		String winner = null;

		while (winner == null && round < 20) {
			round++;
			playerPos = Math.min(totalSteps, playerPos + 2);
			for (int i = 0; i < npcPos.length; i++) {
				npcPos[i] = Math.min(totalSteps, npcPos[i] + npcSpeeds[i]);
			}
			if (playerPos >= totalSteps) { winner = player.getNickName(); break; }
			for (int i = 0; i < npcPos.length; i++) {
				if (npcPos[i] >= totalSteps) { winner = npcNames[i]; break; }
			}
		}

		if (player.getNickName().equals(winner)) {
			sb.append("🏆 **").append(player.getNickName()).append("** выиграл гонку! +200 монет!\n");
			ReentrantLock lock = playerLocks.get(id);
			lock.lock();
			try {
				Player p = playerCache.get(id);
				if (p != null) { p.setMoney(p.getMoney() + 200); playerCache.put(id, p); }
			} finally { lock.unlock(); }
		} else {
			sb.append("💨 **").append(winner).append("** пересёк финиш первым. Ты занял не первое место!");
		}
		sb.append("\nТвоя позиция: ").append(path[Math.min(playerPos, totalSteps)]);
		event.getChannel().sendMessage(sb.toString()).submit();
	}


	/** +покер @игрок [ставка] (65) */
	public void playPoker(MessageReceivedEvent event) {
		var mentions = event.getMessage().getMentions().getUsers();
		if (mentions.isEmpty()) {
			event.getChannel().sendMessage("Укажите оппонента: +покер @игрок [ставка]").submit();
			return;
		}
		String senderId = event.getAuthor().getId();
		String opponentId = mentions.get(0).getId();
		if (senderId.equals(opponentId)) {
			event.getChannel().sendMessage("Нельзя играть против себя.").submit();
			return;
		}
		if (!playerCache.contains(opponentId)) {
			event.getChannel().sendMessage("Оппонент не зарегистрирован.").submit();
			return;
		}
		String raw = event.getMessage().getContentRaw();
		int mentionEnd = raw.indexOf('>') + 1;
		String rest = mentionEnd > 0 ? raw.substring(mentionEnd).trim() : "";
		int bet;
		try {
			bet = Integer.parseInt(rest);
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Укажите ставку: +покер @игрок [ставка]").submit();
			return;
		}
		if (bet <= 0) {
			event.getChannel().sendMessage("Ставка должна быть больше нуля.").submit();
			return;
		}
		Player challenger = playerCache.get(senderId);
		Player opponent = playerCache.get(opponentId);
		tavern.playPoker(event, challenger, opponent, bet);
		playerCache.putBoth(senderId, challenger, opponentId, opponent);
	}
}
