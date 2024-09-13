package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Player;

import java.util.Random;

public class Tavern {
	private final IgniteCache<String, Player> playerCache;
	private final Random random = new Random();

	public Tavern(IgniteCache<String, Player> playerCache) {
		this.playerCache = playerCache;
	}

	public void dieCast(MessageReceivedEvent event) {
		Player player = playerCache.get(event.getMessage().getAuthor().getId());
		if (!player.getLocation().equals("таверна")) {
			event.getChannel().sendMessage("Как ты собрался бросить кости если ты не в таверне? Метнись кабанчиком сначала туда").submit();
			return;
		}
		String bidText = event.getMessage().getContentDisplay().substring(7);
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
		} else {
			diceStart(event, player, bid);
		}
	}

	private void diceStart(MessageReceivedEvent event, Player player, int bid) {
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
			playerCache.put(player.getId(), player);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
