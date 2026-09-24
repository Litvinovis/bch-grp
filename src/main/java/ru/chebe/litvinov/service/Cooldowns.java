package ru.chebe.litvinov.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Кулдаун активности на игрока.
 * Хранится в памяти: после рестарта бота кулдаун сбрасывается, что допустимо для
 * активностей, где он нужен лишь против бесконечного фарма монет повтором команды.
 */
public class Cooldowns {

	private final long periodMs;
	private final LongSupplier clock;
	private final ConcurrentHashMap<String, Long> lastUse = new ConcurrentHashMap<>();

	public Cooldowns(long periodMs) {
		this(periodMs, System::currentTimeMillis);
	}

	Cooldowns(long periodMs, LongSupplier clock) {
		this.periodMs = periodMs;
		this.clock = clock;
	}

	/**
	 * Атомарно занимает кулдаун игрока.
	 *
	 * @param playerId идентификатор игрока
	 * @return 0, если активность разрешена (кулдаун запущен), иначе оставшееся время в мс
	 */
	public long tryAcquire(String playerId) {
		long now = clock.getAsLong();
		long[] remaining = {0};
		lastUse.compute(playerId, (id, last) -> {
			if (last != null && now - last < periodMs) {
				remaining[0] = periodMs - (now - last);
				return last;
			}
			return now;
		});
		return remaining[0];
	}

	/** Форматирует оставшееся время для сообщения игроку. */
	public static String format(long remainingMs) {
		long minutes = Math.max(1, (remainingMs + 59_999) / 60_000);
		return minutes >= 60 ? (minutes / 60) + " ч " + (minutes % 60) + " мин" : minutes + " мин";
	}
}
