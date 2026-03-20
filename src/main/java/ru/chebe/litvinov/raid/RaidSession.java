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

    public RaidSession(String channelId, MessageChannelUnion channel) {
        this.channelId = channelId;
        this.channel = channel;
        this.createdAt = System.currentTimeMillis();
    }

    public String getChannelId() {
        return channelId;
    }

    public MessageChannelUnion getChannel() {
        return channel;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Map<String, Player> getParticipants() {
        return participants;
    }

    public boolean addParticipant(Player player) {
        if (started.get() || finished.get()) return false;
        participants.put(player.getId(), player);
        return true;
    }

    public boolean isReadyToStart() {
        return !started.get() && participants.size() >= MIN_PLAYERS;
    }

    public boolean isTimedOut() {
        return System.currentTimeMillis() - createdAt >= TIMEOUT_MS;
    }

    /**
     * Пометить рейд как начавшийся. Возвращает true только один раз (CAS).
     */
    public boolean markStarted() {
        return started.compareAndSet(false, true);
    }

    /**
     * Пометить рейд как завершённый.
     */
    public void markFinished() {
        finished.set(true);
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isFinished() {
        return finished.get();
    }
}
