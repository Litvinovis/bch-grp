package org.example.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import org.example.data.Items;
import org.example.data.Player;

import java.util.HashMap;
import java.util.Map;

public class ItemsManager {

	private final IgniteCache<String, Items> itemsCache;
	private final IgniteCache<String, Player> playerCache;

	public ItemsManager(IgniteCache<String, Player> playerCache, IgniteCache<String, Items> itemsCache) {
		this.itemsCache = itemsCache;
		this.playerCache = playerCache;
		init(itemsCache);
	}

	private static void init(IgniteCache<String, Items> itemsCache) {
		Map<String, Items> map = new HashMap<>();
		// Предметы боссов
		map.put("бицушка ровера", Items.builder().name("бицушка ровера").armor(0).price(1000).luck(0).health(10).reputation(0).strength(2).xpGeneration(0).action(false)
						.description("Легендарная бицушка-пушка, позволяет выкашивать своих врагов типа Ильи одной левой").build());
		map.put("кисточка циника", Items.builder().name("кисточка циника").armor(0).price(1000).luck(1).health(0).reputation(3).strength(0).xpGeneration(0).action(false)
						.description("Ты доволен? Ты рисуешь косарей под дождём, заткнись, заткнись!").build());
		map.put("корона дарха", Items.builder().name("корона дарха").armor(2).price(1000).luck(0).health(10).reputation(1).strength(0).xpGeneration(0).action(false)
						.description("Коммуницировать целесообразно так, чтобы вас поняла любая перципиентная личность").build());
		map.put("кринж стина", Items.builder().name("кринж стина").armor(2).price(1000).luck(0).health(0).reputation(0).strength(0).xpGeneration(2).action(false)
						.description("С ним вас боится даже кафка").build());
		map.put("попка ушаса", Items.builder().name("попка ушаса").armor(0).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("удивительно, но вам донатят на твиче хотя вы не тян, а продавщица угощает барбарысками").build());
		map.put("око мора", Items.builder().name("око мора").armor(1).price(1000).luck(0).health(0).reputation(1).strength(1).xpGeneration(0).action(false)
						.description("Вашего взора боятся, особенно если вы заговорите").build());
		map.put("очко бога", Items.builder().name("очко бога").armor(0).price(1000).luck(2).health(10).reputation(1).strength(0).xpGeneration(0).action(false)
						.description("Никто не знает почему и как оно работает, да и работает ли").build());
		map.put("хуй вущъта", Items.builder().name("хуй вущъта").armor(2).price(1000).luck(0).health(10).reputation(0).strength(0).xpGeneration(2).action(false)
						.description("Пошёл нахуй, просто пошёл нахуй, зачем ты добавил это в игру").build());
		map.put("удача рианель", Items.builder().name("удача рианель").armor(0).price(1000).luck(3).health(0).reputation(1).strength(0).xpGeneration(1).action(false)
						.description("С вами постоянно что-то происходит, но пока вы живы и даже покушали M&Ms").build());
		map.put("шарики лаба", Items.builder().name("шарики лаба").armor(0).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("два легендарных шара, к сожалению один разбили, а другой потеряли ||стоит поискать под каблуком?||").build());
		map.put("вонь арктулза", Items.builder().name("шарики лаба").armor(0).price(10).luck(-1).health(-20).reputation(-2).strength(-1).xpGeneration(0).action(false)
						.description("ну не каждый предмет полезен, хотя вас теперь будут избегать, тоже плюс").build());
		map.put("скейт ябыса", Items.builder().name("скейт ябыса").armor(0).price(1000).luck(1).health(0).reputation(1).strength(1).xpGeneration(1).action(false)
						.description("позволяет курсировать между Спермью и Расчленинградом со скоростью 1000 км/ч").build());
		map.put("форточка орсона", Items.builder().name("форточка орсона").armor(3).price(1000).luck(0).health(0).reputation(0).strength(0).xpGeneration(0).action(false)
						.description("к сожалению не работает, зато вы в безопасности").build());
		map.put("месть гордона", Items.builder().name("месть гордона").armor(0).price(1000).luck(0).health(0).reputation(0).strength(3).xpGeneration(0).action(false)
						.description("у вас есть нож и билет в джорджию, что вы будете делать?").build());
		map.put("хатка база", Items.builder().name("хатка база").armor(1).price(1000).luck(0).health(10).reputation(1).strength(1).xpGeneration(0).action(false)
						.description("уютная норка с отличным видом, можно призвать чебешников или просто попить пивка").build());
		map.put("игла бувки", Items.builder().name("игла бувки").armor(0).price(1000).luck(1).health(0).reputation(0).strength(2).xpGeneration(1).action(false)
						.description("можно шить милых лягушек, а можно тыкать партнёра когда он распизделся дохуя").build());
		map.put("калькулятор сталкера", Items.builder().name("калькулятор сталкера").armor(0).price(1000).luck(2).health(0).reputation(1).strength(1).xpGeneration(0).action(false)
						.description("и риски должны быть просчитаны").build());
		map.put("язык вороны", Items.builder().name("язык вороны").armor(1).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("ENGLISH, MOTHERFUCKER! DO YOU SPEAK IT?!").build());
		map.put("диплом ильи", Items.builder().name("диплом ильи").armor(1).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("вы знаете кто правил Ираном в 1384 году, теперь вы можете .... ну .... это ... рассказывать об этом, вот.").build());
		map.put("кресло чегоба", Items.builder().name("кресло чегоба").armor(1).price(1000).luck(1).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("встроенный страпон и полное собрание правил пасфайндера, уничтожайте врагов физически и морально").build());
		map.put("сиськи ред", Items.builder().name("сиськи ред").armor(2).price(1000).luck(0).health(0).reputation(2).strength(0).xpGeneration(0).action(false)
						.description("весомый аргумент при торговле и драке").build());
		map.put("банка эдика", Items.builder().name("банка эдика").armor(0).price(1000).luck(1).health(0).reputation(0).strength(0).xpGeneration(2).action(false)
						.description("обращайтесь с ней очень осторожно, жалательно не собирать в неё звезды").build());


		// активируемое
		map.put("кружка цикория", Items.builder().name("кружка цикория").armor(0).price(50).luck(0).health(30).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Восстанавливает 30 HP").build());
		map.put("вино лаба", Items.builder().name("вино лаба").armor(0).price(100).luck(0).health(50).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Восстанавливает 50 HP").build());
		map.put("медовуха база", Items.builder().name("медовуха база").armor(0).price(150).luck(0).health(100).reputation(0).strength(0).xpGeneration(0).action(true)
						.description("Восстанавливает 100 HP").build());


		if (itemsCache != null) {
			map.forEach(itemsCache::put);
		}
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
}
