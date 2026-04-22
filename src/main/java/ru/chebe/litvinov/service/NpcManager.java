package ru.chebe.litvinov.service;

import ru.chebe.litvinov.data.NpcBot;

import java.util.*;

public class NpcManager {

	// location → list of bots in that location
	private final Map<String, List<NpcBot>> botsByLocation = new HashMap<>();

	public NpcManager() {
		init();
	}

	private void init() {
		// Тир 1 — danger 10–25: hp=100, strength=6, armor=0, reward=30
		// NPC сдвинуты на 1 позицию внутри тира, чтобы не совпадать с боссом локации
		add("мейн",            "Арк-клон",    100, 6, 0, 30, 30); // boss: Labynkyr
		add("деградач",        "Ред-клон",    100, 6, 0, 30, 30); // boss: Arktulz
		add("старборд",        "Гордон-клон", 110, 7, 0, 35, 35); // boss: Red
		add("для-ботов",       "Баз-клон",    100, 6, 0, 30, 30); // boss: Gordon
		add("для-флуда",       "Ябыс-клон",   100, 6, 0, 30, 30); // boss: Buzzz
		add("качалочка",       "Орсон-клон",  110, 7, 1, 35, 35); // boss: Ябыс
		add("дорогой-дневник", "Бувк-клон",    90, 5, 0, 25, 25); // boss: Orson
		add("девочковое",      "Лаб-клон",     90, 5, 0, 25, 25); // boss: la_brioche

		// Тир 2 — danger 35: hp=180, strength=10, armor=2, reward=60
		add("рекламный",  "Бог-клон",     180, 10, 2, 60, 60); // boss: Morgott
		add("хуй-тек",    "Риан-клон",    180, 10, 2, 60, 60); // boss: Usual_god
		add("кринжборд",  "Сталкер-клон", 170,  9, 2, 55, 55); // boss: Рианель
		add("чебеграм",   "Кров-клон",    190, 11, 2, 65, 65); // boss: Stalker
		add("english",    "Моргот-клон",  170,  9, 2, 55, 55); // boss: Crown

		// Тир 3 — danger 50: hp=260, strength=14, armor=4, reward=100
		add("кушетка",   "Ушас-клон",  260, 14, 4, 100, 100); // boss: Ctin
		add("модерская", "Илья-клон",  270, 15, 4, 105, 105); // boss: Ushas
		add("политота",  "Вущт-клон",  265, 14, 4, 100, 100); // boss: Илья
		add("nsfw",      "Эдик-клон",  260, 15, 4, 100, 100); // boss: Вуъщт
		add("nsfw-gay",  "Стин-клон",  255, 13, 4,  95,  95); // boss: Eduard

		// Тир 4 — danger 70–80: hp=360, strength=18, armor=6, reward=150
		add("загадка",          "Ровер-клон", 360, 18, 6, 150, 150); // boss: cynic mansion
		add("клоунская-братва", "Чег-клон",   380, 20, 6, 160, 160); // boss: Rover
		add("nsfw2d",           "Дарх-клон",  360, 18, 6, 150, 150); // boss: Chegobnk
		add("олимп",            "Циник-клон", 420, 22, 8, 180, 180); // boss: Darhalas
	}

	private void add(String location, String name, int hp, int strength, int armor, int money, int xp) {
		NpcBot bot = NpcBot.builder()
						.nickName(name)
						.hp(hp).maxHp(hp)
						.strength(strength)
						.armor(armor)
						.moneyReward(money)
						.xpReward(xp)
						.locationName(location)
						.build();
		botsByLocation.computeIfAbsent(location, k -> new ArrayList<>()).add(bot);
	}

	public List<NpcBot> getBotsInLocation(String location) {
		return botsByLocation.getOrDefault(location, Collections.emptyList());
	}

	public NpcBot getRandomBot(String location) {
		List<NpcBot> bots = getBotsInLocation(location);
		if (bots.isEmpty()) return null;
		Random rand = new Random();
		return bots.get(rand.nextInt(bots.size()));
	}

	public List<String> getBotNamesInLocation(String location) {
		List<NpcBot> bots = getBotsInLocation(location);
		List<String> names = new ArrayList<>();
		for (NpcBot bot : bots) {
			names.add(bot.getNickName() + " [NPC, HP:" + bot.getHp() + "]");
		}
		return names;
	}

	public void respawnBot(NpcBot bot) {
		bot.respawn();
	}
}
