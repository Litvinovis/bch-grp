package org.example.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import org.example.data.Items;
import org.example.data.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PlayersManager {
	private final IgniteCache<String, Player> playerCache;
	private static final Map<Integer, Integer> xPmap = generateXpMap();
	List<String> words1 = List.of("Унылый", "Гейский", "Стрёмный", "Тупой", "Дрищавый", "Жирный");
	List<String> words2 = List.of("Пидор", "Мудила", "Хуй", "Гей", "Лох", "Шлюха");

	public PlayersManager(IgniteCache<String, Player> playerCache) {
		this.playerCache = playerCache;
	}

	public void getPlayerInfo(MessageReceivedEvent event) {
		String nickname = event.getMessage().getAuthor().getName();
		event.getChannel().sendMessage(playerCache.get(nickname).toString()).submit();
	}

	public void changeHp(String nickname, int hp, boolean increase) {
		var player = playerCache.get(nickname);
		if (increase) {
			player.setHp(player.getHp() + hp);
		} else {
			player.setHp(player.getHp() - hp);
		}
		playerCache.put(nickname, player);
	}

	public void getInventoryInfo(MessageReceivedEvent event) {
		String nickname = event.getMessage().getAuthor().getName();
		var player = playerCache.get(nickname);
		String item = event.getMessage().getContentDisplay().substring(10).trim();
		if (item.isEmpty()) {
			if (player.getInventory().isEmpty()) {
				event.getChannel().sendMessage("Ваш инвентарь ~~пожрал лаб~~ пуст, милорд").submit();
			} else {
				event.getChannel().sendMessage(player.getInventory().toString()).submit();
			}
		} else {
			List<Items> filteredItems = player.getInventory().stream().filter(i -> i.getName().equals(item)).toList();
			if (filteredItems.isEmpty()) {
				event.getChannel().sendMessage("Такого предмета у вас нет, посмотрите список имеющихся с помощью команды +инвентарь").submit();
			} else {
				event.getChannel().sendMessage(filteredItems.get(0).toString()).submit();
			}
		}
	}

	public void changeMoney(String nickname, int money, boolean increase) {
		var player = playerCache.get(nickname);
		if (increase) {
			player.setMoney(player.getMoney() + money);
		} else {
			player.setMoney(player.getMoney() - money);
		}
		playerCache.put(nickname, player);
	}

	public void changeReputation(String nickname, int reputation, boolean increase) {
		var player = playerCache.get(nickname);
		if (increase) {
			player.setReputation(player.getReputation() + reputation);
		} else {
			player.setReputation(player.getReputation() - reputation);
		}
		playerCache.put(nickname, player);
	}

	public void changeXp(String nickname, int xp) {
		var player = playerCache.get(nickname);
		if (player.getExp() + xp >= player.getExpToNextLvl()) {
			player.setExp(player.getExp() + xp % player.getExpToNextLvl());
			player.setLevel(player.getLevel() + 1);
			player.setExpToNextLvl(xPmap.get(player.getLevel()));
		} else {
			player.setExp(player.getExp() + xp);
		}
		playerCache.put(nickname, player);
	}

	private static Map<Integer, Integer> generateXpMap() {
		Map<Integer, Integer> xpMap = new HashMap<>();
		int xp = 100;
		for (int i = 2; i <= 100; i++) {
			xpMap.put(i, xp);
			xp += 100;
		}
		return xpMap;
	}

	public void createPlayer(MessageReceivedEvent event) {
		String nickName = event.getMessage().getAuthor().getName();
		if (playerCache.get(nickName) == null) {
			playerCache.put(nickName, new Player(nickName));
			event.getChannel().sendMessage("Добро пожаловать в игру, мы внимательно проанализировали твой профиль и решили, что ник " + getCringeName() + " отлично тебе подходит \n\n" +
							"Впрочем если ты хочешь использовать ник " + nickName + " мы отнесемся к этому с пониманием, для применения этого ника сделай вдох \n" +
							"Теперь ты готов к сражениям и кринжу, скорее ко второму да, для продолжения набери +помощь чтобы отобразить доступные команды или " +
							"+карта для отображения информации куда тебе надо сходить").submit();
		} else {
			event.getChannel().sendMessage("Ты уже зарегистрирован в БЧ ГРП, просто продолжай играть и не пытайся больше обмануть меня пыдор").submit();
		}
	}

	private String getCringeName() {
		Random random = new Random();
		int index1 = random.nextInt(words1.size());
		int index2 = random.nextInt(words2.size());
		int index3 = random.nextInt(100);
		return words1.get(index1) + words2.get(index2) + index3;
	}
}
