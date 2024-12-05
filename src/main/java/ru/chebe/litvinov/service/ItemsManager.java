package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import ru.chebe.litvinov.data.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemsManager {

	private final IgniteCache<String, Item> itemsCache;
	private static final List<String> itemsForSale = new ArrayList<>(100);

	public ItemsManager(IgniteCache<String, Item> itemsCache) {
		this.itemsCache = itemsCache;
		init(itemsCache);
	}

	private static void init(IgniteCache<String, Item> itemsCache) {
		Map<String, Item> map = new HashMap<>();
		// Предметы боссов
		map.put("бицушка ровера", Item.builder().name("бицушка ровера").armor(0).price(1000).luck(0).health(10).reputation(0).strength(2).xpGeneration(0).action(false)
						.description("Легендарная бицушка-пушка, позволяет выкашивать своих врагов типа Ильи одной левой").build());
		map.put("кисточка циника", Item.builder().name("кисточка циника").armor(0).price(1000).luck(1).health(0).reputation(3).strength(0).xpGeneration(0).action(false)
						.description("Ты доволен? Ты рисуешь косарей под дождём, заткнись, заткнись!").build());
		map.put("корона дарха", Item.builder().name("корона дарха").armor(2).price(1000).luck(0).health(10).reputation(1).strength(0).xpGeneration(0).action(false)
						.description("Коммуницировать целесообразно так, чтобы вас поняла любая перципиентная личность").build());
		map.put("кринж стина", Item.builder().name("кринж стина").armor(2).price(1000).luck(0).health(0).reputation(0).strength(0).xpGeneration(2).action(false)
						.description("С ним вас боится даже кафка").build());
		map.put("попка ушаса", Item.builder().name("попка ушаса").armor(0).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("удивительно, но вам донатят на твиче хотя вы не тян, а продавщица угощает барбарысками").build());
		map.put("око мора", Item.builder().name("око мора").armor(1).price(1000).luck(0).health(0).reputation(1).strength(1).xpGeneration(0).action(false)
						.description("Вашего взора боятся, особенно если вы заговорите").build());
		map.put("очко бога", Item.builder().name("очко бога").armor(0).price(1000).luck(2).health(10).reputation(1).strength(0).xpGeneration(0).action(false)
						.description("Никто не знает почему и как оно работает, да и работает ли").build());
		map.put("хуй вущъта", Item.builder().name("хуй вущъта").armor(2).price(1000).luck(0).health(10).reputation(0).strength(0).xpGeneration(2).action(false)
						.description("Пошёл нахуй, просто пошёл нахуй, зачем ты добавил это в игру").build());
		map.put("удача рианель", Item.builder().name("удача рианель").armor(0).price(1000).luck(3).health(0).reputation(1).strength(0).xpGeneration(1).action(false)
						.description("С вами постоянно что-то происходит, но пока вы живы и даже покушали M&Ms").build());
		map.put("шарики лаба", Item.builder().name("шарики лаба").armor(0).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("два легендарных шара, к сожалению один разбили, а другой потеряли ||стоит поискать под каблуком?||").build());
		map.put("вонь арктулза", Item.builder().name("вонь арктулза").armor(0).price(10).luck(-1).health(-20).reputation(-2).strength(-1).xpGeneration(0).action(false)
						.description("ну не каждый предмет полезен, хотя вас теперь будут избегать, тоже плюс").build());
		map.put("скейт ябыса", Item.builder().name("скейт ябыса").armor(0).price(1000).luck(1).health(0).reputation(1).strength(1).xpGeneration(1).action(false)
						.description("позволяет курсировать между Спермью и Расчленинградом со скоростью 1000 км/ч").build());
		map.put("форточка орсона", Item.builder().name("форточка орсона").armor(3).price(1000).luck(0).health(0).reputation(0).strength(0).xpGeneration(0).action(false)
						.description("к сожалению не работает, зато вы в безопасности").build());
		map.put("месть гордона", Item.builder().name("месть гордона").armor(0).price(1000).luck(0).health(0).reputation(0).strength(3).xpGeneration(0).action(false)
						.description("у вас есть нож и билет в джорджию, что вы будете делать?").build());
		map.put("хатка база", Item.builder().name("хатка база").armor(1).price(1000).luck(0).health(10).reputation(1).strength(1).xpGeneration(0).action(false)
						.description("уютная норка с отличным видом, можно призвать чебешников или просто попить пивка").build());
		map.put("игла бувки", Item.builder().name("игла бувки").armor(0).price(1000).luck(1).health(0).reputation(0).strength(2).xpGeneration(1).action(false)
						.description("можно шить милых лягушек, а можно тыкать партнёра когда он распизделся дохуя").build());
		map.put("калькулятор сталкера", Item.builder().name("калькулятор сталкера").armor(0).price(1000).luck(2).health(0).reputation(1).strength(1).xpGeneration(0).action(false)
						.description("и риски должны быть просчитаны").build());
		map.put("язык вороны", Item.builder().name("язык вороны").armor(1).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("ENGLISH, MOTHERFUCKER! DO YOU SPEAK IT?!").build());
		map.put("диплом ильи", Item.builder().name("диплом ильи").armor(1).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("вы знаете кто правил Ираном в 1384 году, теперь вы можете .... ну .... это ... рассказывать об этом, вот.").build());
		map.put("кресло чегоба", Item.builder().name("кресло чегоба").armor(1).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("встроенный страпон и полное собрание правил пасфайндера, уничтожайте врагов физически и морально").build());
		map.put("сиськи ред", Item.builder().name("сиськи ред").armor(2).price(1000).luck(0).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("весомый аргумент при торговле и драке").build());
		map.put("банка эдика", Item.builder().name("банка эдика").armor(0).price(1000).luck(1).health(0).reputation(0).strength(0).xpGeneration(2).action(false)
						.description("обращайтесь с ней очень осторожно, жалательно не собирать в неё звезды").build());


		// активируемое
		map.put("кружка цикория", Item.builder().name("кружка цикория").armor(0).price(10).luck(0).health(30).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Восстанавливает 30 HP").build());
		map.put("вино лаба", Item.builder().name("вино лаба").armor(0).price(15).luck(0).health(50).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Восстанавливает 50 HP").build());
		map.put("медовуха база", Item.builder().name("медовуха база").armor(0).price(30).luck(0).health(100).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Восстанавливает 100 HP").build());
		map.put("токен телепорта", Item.builder().name("токен телепорта").armor(0).price(2).luck(0).health(0).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Позволяет использовать телепорты").build());


		if (itemsCache != null) {
			map.forEach(itemsCache::put);
		}

		map.forEach((key, value) -> {
			if (value.isAction()) {
				itemsForSale.add(key);
			}
		});
	}

	public void getItemInfo(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
		var item = itemsCache.get(message);
		if (item != null) {
			event.getChannel().sendMessage(item.toString()).submit();
		} else {
			event.getChannel().sendMessage("Такого предмета не существует, но ты можешь обратиться к разработчикам с помощью команды +идея и мы подумаем о его добавлении за умеренную плату").submit();
		}
	}

	public Item getItem(String itemName) {
		return itemsCache.get(itemName);
	}

	public String getItemsForSale() {
		return itemsForSale.toString();
	}
}
