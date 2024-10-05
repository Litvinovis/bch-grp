package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Player;

import java.util.Random;

public class Tavern {
	private final Random random = new Random();

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
}
