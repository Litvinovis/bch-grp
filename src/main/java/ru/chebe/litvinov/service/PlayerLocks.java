package ru.chebe.litvinov.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Блокировки по игроку, общие для всех сервисов.
 * Один и тот же игрок не должен изменяться параллельно из разных команд,
 * поэтому карта локов живёт в одном месте, а не в каждом сервисе своя.
 */
public class PlayerLocks {

	private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

	/** Возвращает лок игрока, создавая его при первом обращении. */
	public ReentrantLock get(String playerId) {
		return locks.computeIfAbsent(playerId, k -> new ReentrantLock());
	}

	/**
	 * Захватывает локи двух игроков в порядке их id.
	 * Фиксированный порядок исключает дедлок при встречных обменах.
	 */
	public ReentrantLock[] getOrdered(String firstId, String secondId) {
		boolean firstIsLower = firstId.compareTo(secondId) < 0;
		return firstIsLower
				? new ReentrantLock[]{get(firstId), get(secondId)}
				: new ReentrantLock[]{get(secondId), get(firstId)};
	}
}
