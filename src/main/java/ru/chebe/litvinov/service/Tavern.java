package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;

import java.util.Random;

/**
 * Таверна — место для азартных игр в игровом мире.
 * Содержит логику игр: кости, рулетка, камень-ножницы-бумага и угадай число.
 */
public class Tavern {
	private final Random random;

	/**
	 * Создаёт таверну со стандартным генератором случайных чисел.
	 */
	public Tavern() {
		this.random = new Random();
	}

	/**
	 * Создаёт таверну с указанным генератором случайных чисел.
	 * Используется в тестах для воспроизводимых результатов.
	 *
	 * @param random генератор случайных чисел
	 */
	public Tavern(Random random) {
		this.random = random;
	}

	/**
	 * Проводит игру в кости между игроком и трактирщиком.
	 * Игрок с повышенной удачей получает бонус +1 к одному из кубиков.
	 *
	 * @param event  событие Discord-сообщения
	 * @param player объект игрока
	 * @param bid    ставка (от 1 до 100)
	 * @return обновлённый объект игрока с изменённым количеством денег
	 */
	public Player diceStart(MessageReceivedEvent event, Player player, int bid) {
		try {
			int die1, die2;
			int playerDice, tavernDice;
			event.getChannel().sendMessage("По рукам, давай кидать, я первый").submit();
			Thread.sleep(500);
			die1 = random.nextInt(7);
			die2 = random.nextInt(7);
			tavernDice = die2 + die1;
			event.getChannel().sendMessage("*Трактирщик выкидывает два кубика *\n Выпало- ".concat(Integer.toString(die1)).concat(" и ").concat(Integer.toString(die2))).submit();
			event.getChannel().sendMessage("Теперь твоя очередь\n*Вы кидаете два кубика*").submit();
			Thread.sleep(500);
			die1 = random.nextInt(7);
			die2 = random.nextInt(7);
			event.getChannel().sendMessage("Выпало - ".concat(Integer.toString(die2)).concat(" и ").concat(Integer.toString(die1))).submit();
			if (player.getLuck() > 5) {
				if (die1 < 6) {
					die1++;
					event.getChannel().sendMessage("Благодаря вашей прокаченной удачи кубик делает дополнительный поворот и становится на 1 очко больше").submit();
				} else if (die2 < 6) {
					die2++;
					event.getChannel().sendMessage("Благодаря вашей прокаченной удачи кубик делает дополнительный поворот и становится на 1 очко больше").submit();
				}
			}
			playerDice = die2 + die1;
			if (playerDice > tavernDice) {
				player.setMoney(player.getMoney() + bid);
				event.getChannel().sendMessage("Штош сегодня тебе повезло, приходи завтра").submit();
				event.getChannel().sendMessage("* У вас теперь ".concat(Integer.toString(player.getMoney())).concat(" денег")).submit();
			} else if (playerDice < tavernDice) {
				player.setMoney(player.getMoney() - bid);
				event.getChannel().sendMessage("Молодец, молодец, знаешь как говорят, упорство даёт результат, может ещё разок?").submit();
				event.getChannel().sendMessage("* У вас теперь ".concat(Integer.toString(player.getMoney())).concat(" денег")).submit();
			} else {
				event.getChannel().sendMessage("Зачем за мной повторяешь, попугай что-ли?").submit();
				event.getChannel().sendMessage("* У вас осталось ".concat(Integer.toString(player.getMoney())).concat(" денег")).submit();
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return player;
	}

	/**
	 * Проводит игру в рулетку.
	 * Ставка на цвет (красный/чёрный) даёт выигрыш x2, ставка на число — x35.
	 *
	 * @param event  событие Discord-сообщения
	 * @param player объект игрока
	 * @param bid    сумма ставки
	 * @param bet    выбор игрока: «красный», «черный» или число 0-36
	 * @return обновлённый объект игрока с изменённым количеством денег
	 */
	public Player playRoulette(MessageReceivedEvent event, Player player, int bid, String bet) {
		if (player.getMoney() < bid) {
			event.getChannel().sendMessage("У вас недостаточно денег для этой ставки!").queue();
			return player;
		}

		int winNumber = random.nextInt(37);
		String color = (winNumber == 0) ? "зеленый" : (winNumber % 2 == 0) ? "красный" : "черный";

		boolean isWin = bet.equalsIgnoreCase(color) || bet.equals(String.valueOf(winNumber));
		int payout = 1;

		if (isWin) {
			if (bet.equalsIgnoreCase("красный") || bet.equalsIgnoreCase("черный")) {
				payout = 2;
			} else if (bet.equals(String.valueOf(winNumber))) {
				payout = 35;
			}
			player.setMoney(player.getMoney() + bid * payout);
			event.getChannel().sendMessage("Выигрыш! Выпало: " + winNumber + " (" + color + "). Вы получаете " + (bid * payout)).queue();
		} else {
			player.setMoney(player.getMoney() - bid);
			event.getChannel().sendMessage("Проигрыш. Выпало: " + winNumber + " (" + color + ")").queue();
		}
		return player;
	}

	/**
	 * Проводит игру «камень-ножницы-бумага» против AI.
	 * При победе игрок получает ставку, при проигрыше — теряет её.
	 *
	 * @param event  событие Discord-сообщения
	 * @param player объект игрока
	 * @param bid    сумма ставки
	 * @param choice выбор игрока: «камень», «ножницы» или «бумага»
	 * @return обновлённый объект игрока с изменённым количеством денег
	 */
	public Player rockPaperScissors(MessageReceivedEvent event, Player player, int bid, String choice) {
		if (player.getMoney() < bid) {
			event.getChannel().sendMessage("У вас недостаточно денег для этой ставки!").queue();
			return player;
		}

		String[] options = {"камень", "ножницы", "бумага"};
		String aiChoice = options[random.nextInt(3)];

		if (choice.equals(aiChoice)) {
			event.getChannel().sendMessage("Ничья! AI тоже выбрал " + aiChoice).queue();
		} else if ((choice.equals("камень") && aiChoice.equals("ножницы")) ||
						(choice.equals("ножницы") && aiChoice.equals("бумага")) ||
						(choice.equals("бумага") && aiChoice.equals("камень"))) {
			player.setMoney(player.getMoney() + bid);
			event.getChannel().sendMessage("Победа! AI выбрал: " + aiChoice + ". Вы получаете " + bid).queue();
		} else {
			player.setMoney(player.getMoney() - bid);
			event.getChannel().sendMessage("Проигрыш. AI выбрал: " + aiChoice).queue();
		}
		return player;
	}

	/**
	 * Проводит игру «угадай число» (от 1 до 10).
	 * При угадывании игрок получает пятикратную ставку.
	 *
	 * @param event  событие Discord-сообщения (может быть null в тестах)
	 * @param player объект игрока
	 * @param bid    сумма ставки
	 * @param guess  предположение игрока (число от 1 до 10)
	 * @return обновлённый объект игрока с изменённым количеством денег
	 */
	public Player guessTheNumber(MessageReceivedEvent event, Player player, int bid, int guess) {
		if (player.getMoney() < bid) {
			if (event != null) {
				event.getChannel().sendMessage("У вас недостаточно денег для этой ставки!").queue();
			}
			return player;
		}

		// Deduct the bet first
		player.setMoney(player.getMoney() - bid);
		
		int secretNumber = random.nextInt(10) + 1; // Number between 1 and 10

		if (guess == secretNumber) {
			int winAmount = bid * 5; // 5x payout for correct guess
			player.setMoney(player.getMoney() + winAmount);
			if (event != null) {
				event.getChannel().sendMessage("Поздравляем! Вы угадали число " + secretNumber + " и выиграли " + winAmount + " денег!").queue();
			}
		} else {
			if (event != null) {
				event.getChannel().sendMessage("Вы не угадали. Загаданное число было " + secretNumber + ". Вы проиграли " + bid + " денег.").queue();
			}
		}
		return player;
	}
}
