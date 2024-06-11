package org.example.eventHandlers;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.example.data.Player;

import java.util.List;
import java.util.Random;

@Slf4j
public class RegistrationManager {
	IgniteCache<String, Player> cache;
	List<String> words1 = List.of("Унылый", "Гейский", "Стрёмный", "Тупой", "Дрищавый", "Жирный");
	List<String> words2 = List.of("Пидор", "Мудила", "Хуй", "Гей", "Лох", "Шлюха");


	public RegistrationManager(IgniteCache<String, Player> cache) {
		this.cache = cache;
	}

	public String createPlayer(String nickName) {
		if (cache.get(nickName) == null) {
			cache.put(nickName, new Player(nickName));
			return "Добро пожаловать в игру, мы внимательно проанализировали твой профиль и решили, что ник " + getCringeName() + " отлично тебе подходит\n\n" +
							"Впрочем если ты хочешь использовать ник " + nickName + " мы отнесемся к этому с пониманием, для применения этого ника сделай вдох";
		} else {
			return "Ты уже зарегистрирован в БЧ ГРП, просто продолжай играть и не пытайся больше обмануть меня пыдор";
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
