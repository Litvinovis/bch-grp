package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Item;
import ru.chebe.litvinov.data.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PlayersManager {
	private final IgniteCache<String, Player> playerCache;
	private final IgniteCache<String, Item> itemsCache;
	private static final Map<Integer, Integer> xPmap = generateXpMap();
	private final LocationManager locationManager;
	List<String> words1 = List.of("Унылый", "Гейский", "Стрёмный", "Тупой", "Дрищавый", "Жирный");
	List<String> words2 = List.of("Пидор", "Мудила", "Хуй", "Гей", "Лох", "Шлюха");

	public PlayersManager(IgniteCache<String, Player> playerCache, LocationManager locationManager, IgniteCache<String, Item> itemsCache) {
		this.playerCache = playerCache;
		this.locationManager = locationManager;
		this.itemsCache = itemsCache;
	}

	public void getPlayerInfo(MessageReceivedEvent event) {
		String id = event.getMessage().getAuthor().getId();
		event.getChannel().sendMessage(playerCache.get(id).toString()).submit();
	}

	public int getXp(Player player) {
		return xPmap.get(player.getLevel() + 1);
	}

	public void changeHp(String id, int hp) {
		var player = playerCache.get(id);
		player.setHp(hp);
		playerCache.put(id, player);
	}

	public void changeHp(String id, int hp, boolean increase) {
		var player = playerCache.get(id);
		if (increase) {
			player.setHp(player.getHp() + hp);
		} else {
			player.setHp(player.getHp() - hp);
		}
		playerCache.put(id, player);
	}

	public void getInventoryInfo(MessageReceivedEvent event) {
		String id = event.getMessage().getAuthor().getId();
		var player = playerCache.get(id);
		String item = event.getMessage().getContentDisplay().substring(10).trim();
		if (item.isEmpty()) {
			if (player.getInventory().isEmpty()) {
				event.getChannel().sendMessage("Ваш инвентарь ~~пожрал лаб~~ пуст, милорд").submit();
			} else {
				event.getChannel().sendMessage(player.getInventory().toString()).submit();
			}
		} else {
			List<String> filteredItems = player.getInventory().stream().filter(i -> i.equals(item)).toList();
			if (filteredItems.isEmpty()) {
				event.getChannel().sendMessage("Такого предмета у вас нет, посмотрите список имеющихся с помощью команды +инвентарь").submit();
			} else {
				event.getChannel().sendMessage(filteredItems.get(0)).submit();
			}
		}
	}

	public void changeMoney(String id, int money, boolean increase) {
		var player = playerCache.get(id);
		if (increase) {
			player.setMoney(player.getMoney() + money);
		} else {
			player.setMoney(player.getMoney() - money);
		}
		playerCache.put(id, player);
	}

	public void changeReputation(String id, int reputation, boolean increase) {
		var player = playerCache.get(id);
		if (increase) {
			player.setReputation(player.getReputation() + reputation);
		} else {
			player.setReputation(player.getReputation() - reputation);
		}
		playerCache.put(id, player);
	}

	public void changeXp(String id, int xp) {
		var player = playerCache.get(id);
		if (player.getExp() + xp >= player.getExpToNextLvl()) {
			player.setExp(player.getExp() + xp % player.getExpToNextLvl());
			player.setLevel(player.getLevel() + 1);
			player.setExpToNextLvl(xPmap.get(player.getLevel()));
		} else {
			player.setExp(player.getExp() + xp);
		}
		playerCache.put(id, player);
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
		String id = event.getMessage().getAuthor().getId();
		if (playerCache.get(id) == null) {
			String nickName = event.getMessage().getAuthor().getName();
			playerCache.put(id, new Player(nickName, id));
			event.getChannel().sendMessage("Добро пожаловать в игру, мы внимательно проанализировали твой профиль и решили, что ник " + getCringeName() + " отлично тебе подходит \n\n" +
							"Впрочем если ты хочешь использовать ник " + nickName + " мы отнесемся к этому с пониманием, для применения этого используй любую команду \n" +
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

	public void addNewItem(String id, String item) {
		var player = playerCache.get(id);
		Item newItem = itemsCache.get(item);
		player.setReputation(newItem.getReputation() > 0 ? player.getReputation() + newItem.getReputation() : player.getReputation());
		player.setHp(newItem.getHealth() > 0 ? player.getHp() + newItem.getHealth() : player.getHp());
		player.setArmor(newItem.getArmor() > 0 ? player.getArmor() + newItem.getArmor() : player.getArmor());
		player.setLuck(newItem.getLuck() > 0 ? player.getLuck() + newItem.getLuck() : player.getLuck());
		player.setStrength(newItem.getStrength() > 0 ? player.getStrength() + newItem.getStrength() : player.getStrength());
		player.getInventory().add(item);
		playerCache.put(id, player);
	}

	public void deleteItem(String id, String item) {
		var player = playerCache.get(id);
		Item deleteItem = itemsCache.get(item);
		if (!deleteItem.isAction()) {
			player.setReputation(deleteItem.getReputation() > 0 ? player.getReputation() - deleteItem.getReputation() : player.getReputation());
			player.setHp(deleteItem.getHealth() > 0 ? player.getHp() - deleteItem.getHealth() : player.getHp());
			player.setArmor(deleteItem.getArmor() > 0 ? player.getArmor() - deleteItem.getArmor() : player.getArmor());
			player.setLuck(deleteItem.getLuck() > 0 ? player.getLuck() - deleteItem.getLuck() : player.getLuck());
			player.setStrength(deleteItem.getStrength() > 0 ? player.getStrength() - deleteItem.getStrength() : player.getStrength());
		}
		player.getInventory().remove(item);
		playerCache.put(id, player);
	}

	public void deathOfPlayer(Player dead) {
		var player = playerCache.get(dead.getId());
		player.setMoney((int) (player.getMoney() * 0.9));
		playerCache.put(player.getId(), player);
		locationManager.movePerson(player, "респаун");
	}

	public void useItem(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		String message = event.getMessage().getContentDisplay().substring(13).trim().toLowerCase();
		if (player.getInventory().contains(message.toLowerCase())) {
			Item item = itemsCache.get(message);
			if (item.isAction()) {
				if (item.getHealth() > 0) {
					changeHp(player.getId(), item.getHealth(), true);
					event.getChannel().sendMessage("Теперь у тебя " + (item.getHealth() + player.getHp()) + " здоровья").submit();
				}
				if (item.getArmor() > 0) {
					changeArmor(player.getId(), item.getArmor(), true);
					event.getChannel().sendMessage("Теперь у тебя " + (item.getArmor() + player.getArmor()) + " брони").submit();
				}
				if (item.getLuck() > 0) {
					changeLuck(player.getId(), item.getLuck(), true);
					event.getChannel().sendMessage("Теперь у тебя " + (item.getLuck() + player.getLuck()) + " удачи").submit();
				}
				if (item.getStrength() > 0) {
					changeStrength(player.getId(), item.getStrength(), true);
					event.getChannel().sendMessage("Теперь у тебя " + (item.getStrength() + player.getStrength()) + " силы").submit();
				}
				if (item.getReputation() > 0) {
					changeReputation(player.getId(), item.getReputation(), true);
					event.getChannel().sendMessage("Теперь у тебя " + (item.getReputation() + player.getReputation()) + " репутации").submit();
				}
				deleteItem(player.getId(), item.getName());
			} else {
				event.getChannel().sendMessage("Этот предмет нельзя использовать").submit();
			}
		} else {
			event.getChannel().sendMessage("Такого предмета нет в твоём инвентаре").submit();
		}
	}

	private void changeArmor(String id, int armor, boolean increase) {
		var player = playerCache.get(id);
		if (increase) {
			player.setReputation(player.getReputation() + armor);
		} else {
			player.setReputation(player.getReputation() - armor);
		}
		playerCache.put(id, player);
	}

	public void changeLuck(String id, int luck, boolean increase) {
		var player = playerCache.get(id);
		if (increase) {
			player.setReputation(player.getReputation() + luck);
		} else {
			player.setReputation(player.getReputation() - luck);
		}
		playerCache.put(id, player);
	}

	public void changeStrength(String id, int strength, boolean increase) {
		var player = playerCache.get(id);
		if (increase) {
			player.setReputation(player.getReputation() + strength);
		} else {
			player.setReputation(player.getReputation() - strength);
		}
		playerCache.put(id, player);
	}

	public void sellItem(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		String message = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
		if (player.getInventory().contains(message.toLowerCase())) {
			Item item = itemsCache.get(message);
			int sellPrice = item.getPrice() / (2 - player.getReputation() / 10);
			changeMoney(player.getId(), sellPrice, true);
			deleteItem(player.getId(), item.getName());
			event.getChannel().sendMessage("Теперь у тебя " + (player.getMoney() + sellPrice) + " денег").submit();
		} else {
			event.getChannel().sendMessage("Такого предмета нет в твоём инвентаре").submit();
		}
	}
}
