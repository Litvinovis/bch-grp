package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Idea;
import ru.chebe.litvinov.ignite3.IdeaRepository;

/**
 * Менеджер пользовательских идей и предложений.
 * Позволяет игрокам подавать идеи по улучшению игры, а администраторам — просматривать и обрабатывать их.
 */
public class IdeasManager implements ru.chebe.litvinov.service.interfaces.IIdeasManager {

	private final IdeaRepository ideaCache;

	/**
	 * Создаёт менеджер идей.
	 *
	 * @param ideaCache репозиторий Ignite 3 для хранения идей
	 */
	public IdeasManager(IdeaRepository ideaCache) {
		this.ideaCache = ideaCache;
	}

	/**
	 * Отправляет в канал информацию об идее по её номеру из текста сообщения.
	 *
	 * @param event событие Discord-сообщения с номером идеи
	 */
	public void getIdea(MessageReceivedEvent event) {
		event.getChannel().sendMessage(findIdea(event.getMessage().getContentDisplay().substring(10)).toString()).submit();
	}

	/**
	 * Сохраняет новую идею от игрока в кэш.
	 * Текст идеи берётся из содержимого сообщения, автор — из профиля Discord.
	 *
	 * @param event событие Discord-сообщения с текстом идеи
	 */
	public void putIdea(MessageReceivedEvent event) {
		String content = event.getMessage().getContentDisplay();
		if (content.length() <= 5) {
			event.getChannel().sendMessage("Пожалуйста, укажите текст идеи после команды +идея").submit();
			return;
		}
		
		int id = ideaCache.size();
		ideaCache.put(id, Idea.builder()
						.id(id)
						.author(event.getAuthor().getName())
						.description(content.substring(5).trim())
						.resolution("New")
						.build());
		event.getChannel().sendMessage("Ваша идея успешно зарегистрирована под номером " + id + " срок рассмотрения составляет 38 рабочих дней, за исключением сред").submit();
	}

	/**
	 * Отправляет в канал все идеи со статусом «Новая». Предназначено для администраторов.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void getNewIdeas(MessageReceivedEvent event) {
		ideaCache.findByResolution("New")
				.forEach(idea -> event.getChannel().sendMessage(idea.toString()).submit());
	}

	/**
	 * Изменяет статус идеи по её номеру. Предназначено для администраторов.
	 * Новый статус берётся из текста сообщения в кавычках.
	 *
	 * @param event событие Discord-сообщения с номером идеи и новым статусом
	 */
	public void changeIdeaStatus(MessageReceivedEvent event) {
		String message = event.getMessage().getContentDisplay().substring(12);
		Idea idea = findIdea(message);
		String status = message.trim().substring(message.indexOf("\"") + 1, message.trim().lastIndexOf("\""));
		idea.setResolution(status);
		ideaCache.put(idea.getId(), idea);
		event.getChannel().sendMessage("Статус идеи № " + idea.getId() + " успешно изменён на " + status).queue();
	}

	/**
	 * Отправляет в канал все существующие идеи. Предназначено для администраторов.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void getAllIdeas(MessageReceivedEvent event) {
		ideaCache.findAll()
				.forEach(idea -> event.getChannel().sendMessage(idea.toString()).submit());
	}

	/**
	 * Находит идею по её номеру.
	 *
	 * @param ideaNumber строка с номером идеи (может содержать дополнительный текст после номера)
	 * @return объект Idea или null если идея не найдена
	 */
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
