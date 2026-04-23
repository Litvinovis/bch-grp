package ru.chebe.litvinov.raid;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.service.BattleManager;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Менеджер рейдов. Управляет жизненным циклом сессий рейдов.
 *
 * <p>Один активный рейд на канал. Рейд стартует когда:
 * <ul>
 *   <li>набралось {@link RaidSession#MIN_PLAYERS} участников, или</li>
 *   <li>истёк таймаут {@link RaidSession#TIMEOUT_MS}.</li>
 * </ul>
 *
 * Лут распределяется пропорционально нанесённому урону.
 */
@Slf4j
public class RaidManager {

    private static final int LOOT_MONEY_BASE = 1500;
    private static final int LOOT_XP_BASE = 2000;

    /** Список каналов, в которых разрешены рейды (пустой = разрешено везде) */
    private final Set<String> allowedChannelIds;

    /** channelId -> активная сессия */
    private final ConcurrentHashMap<String, RaidSession> activeSessions = new ConcurrentHashMap<>();

    /** Блокировки per-channel для операций открытия/закрытия рейда */
    private final ConcurrentHashMap<String, ReentrantLock> channelLocks = new ConcurrentHashMap<>();

    private final BattleManager battleManager;
    private final IPlayersManager playersManager;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "raid-timeout-checker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Создаёт менеджер рейдов и запускает планировщик проверки таймаутов.
     *
     * @param battleManager    менеджер боевой системы
     * @param playersManager   менеджер игроков для начисления наград и обработки смерти
     * @param allowedChannelIds список идентификаторов каналов, в которых разрешены рейды
     *                          (пустое множество означает отсутствие ограничений)
     */
    public RaidManager(BattleManager battleManager,
                       IPlayersManager playersManager,
                       Set<String> allowedChannelIds) {
        this.battleManager = battleManager;
        this.playersManager = playersManager;
        this.allowedChannelIds = allowedChannelIds == null ? Collections.emptySet() : allowedChannelIds;

        // Проверяем таймауты каждые 30 секунд
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 30, 30, TimeUnit.SECONDS);
    }

    private ReentrantLock getLock(String channelId) {
        return channelLocks.computeIfAbsent(channelId, k -> new ReentrantLock());
    }

    /**
     * Создать новый рейд в указанном канале.
     *
     * @param initiator игрок, инициирующий рейд
     * @param channel   Discord-канал
     * @return сообщение для пользователя
     */
    public String createRaid(Player initiator, MessageChannelUnion channel) {
        String channelId = channel.getId();

        if (!allowedChannelIds.isEmpty() && !allowedChannelIds.contains(channelId)) {
            return "Рейды разрешены только в специальных каналах!";
        }

        ReentrantLock lock = getLock(channelId);
        lock.lock();
        try {
            RaidSession existing = activeSessions.get(channelId);
            if (existing != null && !existing.isFinished()) {
                return "В этом канале уже идёт рейд! Введи +присоединиться чтобы участвовать. " +
                        "Участников: " + existing.getParticipants().size() + "/" + RaidSession.MIN_PLAYERS;
            }

            RaidSession session = new RaidSession(channelId, channel);
            session.addParticipant(initiator);
            activeSessions.put(channelId, session);

            log.info("Рейд создан в канале {} игроком {}", channelId, initiator.getNickName());
            return "Рейд начат! Босс: **Рейд-Чебеш** (HP: 5000)\n" +
                    "Для участия введите +присоединиться\n" +
                    "Минимум участников: " + RaidSession.MIN_PLAYERS +
                    ". Таймаут: 10 минут.\n" +
                    "Участников: 1/" + RaidSession.MIN_PLAYERS;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Присоединяет игрока к активному рейду в данном канале.
     *
     * @param player  присоединяющийся игрок
     * @param channel Discord-канал
     * @return сообщение для пользователя
     */
    public String joinRaid(Player player, MessageChannelUnion channel) {
        String channelId = channel.getId();
        ReentrantLock lock = getLock(channelId);
        lock.lock();
        try {
            RaidSession session = activeSessions.get(channelId);
            if (session == null || session.isFinished()) {
                return "В этом канале нет активного рейда. Начни его командой +рейд";
            }
            if (session.isStarted()) {
                return "Рейд уже начался, к нему нельзя присоединиться!";
            }
            if (session.getParticipants().containsKey(player.getId())) {
                return "Ты уже участвуешь в рейде!";
            }

            session.addParticipant(player);
            int count = session.getParticipants().size();
            log.info("Игрок {} присоединился к рейду в канале {}", player.getNickName(), channelId);

            if (session.isReadyToStart()) {
                // Запускаем бой асинхронно, чтобы не блокировать вызывающий поток
                RaidSession toStart = session;
                new Thread(() -> executeRaid(toStart)).start();
                return player.getNickName() + " присоединился! Участников: " + count + "/" + RaidSession.MIN_PLAYERS +
                        "\nМинимум набран — рейд стартует!";
            }

            return player.getNickName() + " присоединился к рейду! Участников: " +
                    count + "/" + RaidSession.MIN_PLAYERS;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Периодически проверяем сессии с истёкшим таймаутом.
     */
    private void checkTimeouts() {
        for (Map.Entry<String, RaidSession> entry : activeSessions.entrySet()) {
            RaidSession session = entry.getValue();
            if (!session.isStarted() && !session.isFinished() && session.isTimedOut()) {
                ReentrantLock lock = getLock(entry.getKey());
                lock.lock();
                try {
                    if (!session.isStarted() && !session.isFinished() && session.isTimedOut()) {
                        int count = session.getParticipants().size();
                        if (count == 0) {
                            session.markFinished();
                            activeSessions.remove(entry.getKey());
                            continue;
                        }
                        log.info("Таймаут рейда в канале {}. Стартуем с {} игроками", entry.getKey(), count);
                        new Thread(() -> executeRaid(session)).start();
                    }
                } finally {
                    lock.unlock();
                }
            }
            // Удаляем завершённые сессии
            if (session.isFinished()) {
                activeSessions.remove(entry.getKey());
            }
        }
    }

    /**
     * Проводит рейдовый бой для указанной сессии. Вызывается в отдельном потоке.
     * Распределяет лут пропорционально нанесённому урону после победы.
     *
     * @param session активная сессия рейда
     */
    void executeRaid(RaidSession session) {
        if (!session.markStarted()) {
            return; // кто-то уже запустил
        }

        MessageChannelUnion channel = session.getChannel();
        Map<String, Player> participants = session.getParticipants();

        if (participants.isEmpty()) {
            session.markFinished();
            activeSessions.remove(session.getChannelId());
            return;
        }

        channel.sendMessage("🔥 **РЕЙД НАЧАЛСЯ!** 🔥\n" +
                "👥 Участников: **" + participants.size() + "**\n" +
                "👹 Противник: **Рейд-Чебеш** (❤️ HP: **5000** | ⚔️ Сила: **25**)").queue();

        RaidBoss boss = RaidBoss.createDefault();

        // Каждый игрок атакует босса по очереди; трекаем нанесённый урон
        Map<String, Integer> damageDealt = new LinkedHashMap<>();
        Random rand = new Random();

        List<Player> playerList = new ArrayList<>(participants.values());

        // Бой: раунды пока у босса > 0 HP и есть живые игроки
        int round = 1;
        StringBuilder roundLog = new StringBuilder();
        while (boss.getHp() > 0 && playerList.stream().anyMatch(p -> p.getHp() > 0)) {
            roundLog.setLength(0);
            roundLog.append("─────────────────────\n");
            roundLog.append("⚔️ **Раунд ").append(round++).append("** | 👹 Босс: ❤️ **").append(Math.max(0, boss.getHp())).append("** HP\n");

            for (Player player : playerList) {
                if (player.getHp() <= 0) continue;
                if (boss.getHp() <= 0) break;

                int dmg = randomizeDamage(player.getStrength(), rand);
                boss.setHp(boss.getHp() - dmg);
                damageDealt.merge(player.getId(), dmg, Integer::sum);
                roundLog.append("⚔️ **").append(player.getNickName()).append("** → 💥 **").append(dmg)
                        .append("** 🩸 | 👹 ❤️ **").append(Math.max(0, boss.getHp())).append("** HP\n");

                if (boss.getHp() <= 0) break;

                List<Player> alivePlayers = playerList.stream().filter(p -> p.getHp() > 0).toList();
                Player target = alivePlayers.get(rand.nextInt(alivePlayers.size()));
                int bossDmg = randomizeDamage(boss.getStrength() - target.getArmor(), rand);
                target.setHp(target.getHp() - bossDmg);
                roundLog.append("👹 **Рейд-Чебеш** → 💀 **").append(bossDmg)
                        .append("** по **").append(target.getNickName())
                        .append("** | ❤️ **").append(Math.max(0, target.getHp())).append("** HP\n");
            }

            if (roundLog.length() > 1800) {
                channel.sendMessage(roundLog.toString()).queue();
                roundLog.setLength(0);
            }
        }

        if (!roundLog.isEmpty()) {
            channel.sendMessage(roundLog.toString()).queue();
        }

        // Результат
        boolean bossDefeated = boss.getHp() <= 0;
        if (bossDefeated) {
            distributeRaidLoot(session, damageDealt, participants);
        } else {
            channel.sendMessage("💀 **РЕЙД ПРОВАЛЕН!** 💀\n👹 **Рейд-Чебеш** устоял... Все герои пали в бою. 😤").queue();
            for (Map.Entry<String, Player> entry : participants.entrySet()) {
                if (entry.getValue().getHp() <= 0) {
                    Player actual = playersManager.getPlayer(entry.getKey());
                    if (actual != null) playersManager.deathOfPlayer(actual);
                }
            }
        }

        session.markFinished();
        activeSessions.remove(session.getChannelId());
        log.info("Рейд в канале {} завершён. Босс повержен: {}", session.getChannelId(), bossDefeated);
    }

    private void distributeRaidLoot(RaidSession session, Map<String, Integer> damageDealt,
                                     Map<String, Player> participants) {
        MessageChannelUnion channel = session.getChannel();
        int totalDamage = damageDealt.values().stream().mapToInt(Integer::intValue).sum();
        if (totalDamage == 0) totalDamage = 1;

        StringBuilder result = new StringBuilder("🏆 **РЕЙД ПОБЕДА!** 🏆\n");
        result.append("👹 **Рейд-Чебеш** повержен! Слава рейдерам! 🎉\n");
        result.append("─────────────────────\n");
        result.append("📊 **Распределение лута:**\n");

        for (Map.Entry<String, Player> entry : participants.entrySet()) {
            String playerId = entry.getKey();
            Player player = playersManager.getPlayer(playerId);
            if (player == null) continue;
            int battleHp = entry.getValue().getHp();
            int dmg = damageDealt.getOrDefault(playerId, 0);
            double share = (double) dmg / totalDamage;
            int moneyReward = (int) (LOOT_MONEY_BASE * share * participants.size());
            int xpReward = (int) (LOOT_XP_BASE * share * participants.size());

            if (battleHp > 0) {
                playersManager.changeMoney(playerId, moneyReward, true);
                playersManager.changeXp(playerId, xpReward);
                result.append("✅ **").append(player.getNickName())
                        .append("**: ⚔️ урон **").append(dmg)
                        .append("** (").append(String.format("%.0f", share * 100)).append("%) → ")
                        .append("💰 **+").append(moneyReward).append("** монет | ")
                        .append("✨ **+").append(xpReward).append("** XP\n");
            } else {
                playersManager.deathOfPlayer(player);
                result.append("💀 **").append(player.getNickName()).append("**: погиб в бою (воскрешён на Респауне)\n");
            }
        }

        channel.sendMessage(result.toString()).queue();
    }

    private int randomizeDamage(int base, Random rand) {
        if (base <= 0) return 0;
        double pct = (rand.nextInt(51) - 25) / 100.0;
        return Math.max(0, (int) (base * (1 + pct)));
    }

    /**
     * Останавливает планировщик проверки таймаутов.
     * Необходимо вызывать при завершении работы приложения.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
