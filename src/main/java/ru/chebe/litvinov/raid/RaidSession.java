package ru.chebe.litvinov.raid;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import ru.chebe.litvinov.data.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Активная сессия рейда. Хранит список участников, состояние и канал.
 */
public class RaidSession {

    public static final int MIN_PLAYERS = 3;
    /** 10 минут в миллисекундах */
    public static final long TIMEOUT_MS = 10 * 60 * 1000L;

    private final String channelId;
    private final MessageChannelUnion channel;
    private final long createdAt;

    /** id игрока -> Player snapshot (на момент входа в рейд) */
    private final Map<String, Player> participants = new ConcurrentHashMap<>();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * Создаёт новую сессию рейда.
     *
     * @param channelId идентификатор Discord-канала, в котором проходит рейд
     * @param channel   объект Discord-канала для отправки сообщений
     */
    public RaidSession(String channelId, MessageChannelUnion channel) {
        this.channelId = channelId;
        this.channel = channel;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Возвращает идентификатор Discord-канала рейда.
     *
     * @return идентификатор канала
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * Возвращает объект Discord-канала.
     *
     * @return канал для отправки сообщений
     */
    public MessageChannelUnion getChannel() {
        return channel;
    }

    /**
     * Возвращает время создания сессии рейда.
     *
     * @return время создания в миллисекундах (System.currentTimeMillis())
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Возвращает карту участников рейда.
     *
     * @return карта: идентификатор игрока → объект Player
     */
    public Map<String, Player> getParticipants() {
        return participants;
    }

    /**
     * Добавляет игрока в список участников рейда.
     * Добавление невозможно если рейд уже начался или завершён.
     *
     * @param player добавляемый игрок
     * @return true если игрок успешно добавлен
     */
    public boolean addParticipant(Player player) {
        if (started.get() || finished.get()) return false;
        participants.put(player.getId(), player);
        return true;
    }

    /**
     * Проверяет, набралось ли минимальное количество участников для старта рейда.
     *
     * @return true если рейд ещё не начался и участников достаточно
     */
    public boolean isReadyToStart() {
        return !started.get() && participants.size() >= MIN_PLAYERS;
    }

    /**
     * Проверяет, истёк ли таймаут ожидания участников рейда.
     *
     * @return true если с момента создания прошло более {@link #TIMEOUT_MS} миллисекунд
     */
    public boolean isTimedOut() {
        return System.currentTimeMillis() - createdAt >= TIMEOUT_MS;
    }

    /**
     * Помечает рейд как начавшийся с использованием атомарной операции CAS.
     *
     * @return true если рейд был успешно переведён в состояние «начат» (первый вызов),
     *         false если рейд уже начался ранее
     */
    public boolean markStarted() {
        return started.compareAndSet(false, true);
    }

    /**
     * Помечает рейд как завершённый.
     * После вызова этого метода новые участники не могут присоединиться к рейду.
     */
    public void markFinished() {
        finished.set(true);
    }

    /**
     * Проверяет, начался ли рейд.
     *
     * @return true если рейд начался
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * Проверяет, завершён ли рейд.
     *
     * @return true если рейд завершён
     */
    public boolean isFinished() {
        return finished.get();
    }
}
