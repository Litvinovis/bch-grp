package ru.chebe.litvinov.service;

/**
 * Тонкая обёртка над DailyQuestService: сервис задаётся после создания
 * менеджеров, поэтому вызовы прогресса должны молча пропускаться,
 * пока он не установлен.
 */
public class QuestProgressTracker {

	private DailyQuestService dailyQuestService;

	public void setDailyQuestService(DailyQuestService dailyQuestService) {
		this.dailyQuestService = dailyQuestService;
	}

	/** Увеличивает прогресс ежедневного квеста; без сервиса — ничего не делает. */
	public void progress(String userId, String type, int amount) {
		if (dailyQuestService != null) {
			dailyQuestService.incrementProgress(userId, type, amount);
		}
	}
}
