package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.lang.IgniteBiPredicate;
import ru.chebe.litvinov.data.Idea;

import javax.cache.Cache;

public class IdeasManager {

	private final IgniteCache<Integer, Idea> ideaCache;

	public IdeasManager(IgniteCache<Integer, Idea> ideaCache) {
		this.ideaCache = ideaCache;
	}

	public void getIdea(MessageReceivedEvent event) {
		event.getChannel().sendMessage(findIdea(event.getMessage().getContentDisplay().substring(10)).toString()).submit();
	}

	public void putIdea(MessageReceivedEvent event) {
		int id = ideaCache.size();
		ideaCache.put(id, Idea.builder()
						.id(id)
						.author(event.getAuthor().getName())
						.description(event.getMessage().getContentDisplay().substring(5))
						.resolution("Новая")
						.build());
		event.getChannel().sendMessage("Ваша идея успешно зарегистрирована под номером " + id + " срок рассмотрения составляет 38 рабочих дней, за исключением сред").submit();
	}

	public void getNewIdeas(MessageReceivedEvent event) {
		IgniteBiPredicate<Integer, Idea> filter = (key, p) -> p.getResolution().equals("Новая");
		try (QueryCursor<Cache.Entry<Integer, Idea>> qryCursor = ideaCache.query(new ScanQuery<>(filter))) {
			qryCursor.forEach(
							entry -> event.getChannel().sendMessage(entry.getValue().toString()).submit());
		}
	}

	public void changeIdeaStatus(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(12);
		Idea idea = findIdea(message);
		String status = message.trim().substring(message.indexOf("\"") + 1, message.trim().lastIndexOf("\""));
		idea.setResolution(status);
		ideaCache.put(idea.getId(), idea);
		event.getChannel().sendMessage("Статус идеи № " + idea.getId() + " успешно изменён на " + status).queue();
	}

	public void getAllIdeas(MessageReceivedEvent event) {
		try (QueryCursor<Cache.Entry<Integer, Idea>> qryCursor = ideaCache.query(new ScanQuery<>())) {
			qryCursor.forEach(entry -> event.getChannel().sendMessage(entry.getValue().toString()).submit());
		}
	}

	public Idea findIdea(String ideaNumber) {
		int number;
		if (ideaNumber.length() > 4) {
			number = Integer.parseInt(ideaNumber.substring(0, ideaNumber.indexOf(" ")));
		} else {
			number = Integer.parseInt(ideaNumber.trim());
		}
		return ideaCache.get(number);
	}
}
