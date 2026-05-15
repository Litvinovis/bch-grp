package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Менеджер арены с ELO-рейтингом.
 * Управляет боями на арене, командными боями 3v3 и выживанием.
 */
public class ArenaManager {

    private final PlayerRepository playerRepository;
    private final BattleManager battleManager;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    // Дневной чемпион
    private static volatile String dailyChampionId = null;
    private static volatile long championSetTime = 0;

    public ArenaManager(PlayerRepository playerRepository, BattleManager battleManager) {
        this.playerRepository = playerRepository;
        this.battleManager = battleManager;
    }

    private ReentrantLock getLock(String id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    /** +арена — найти соперника с близким ELO и сразиться */
    public void arenaChallenge(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);

        List<Player> all = playerRepository.getAll();
        // Найти соперника с ближайшим ELO (не самого игрока)
        Player opponent = all.stream()
            .filter(p -> !p.getId().equals(id))
            .min(Comparator.comparingInt(p -> Math.abs(p.getArenaRating() - player.getArenaRating())))
            .orElse(null);

        if (opponent == null) {
            event.getChannel().sendMessage("Нет доступных соперников на арене. Зарегистрируйся первым!").submit();
            return;
        }

        event.getChannel().sendMessage("⚔️ **Арена!** **" + player.getNickName() + "** [ELO: " + player.getArenaRating() +
            "] vs **" + opponent.getNickName() + "** [ELO: " + opponent.getArenaRating() + "]").submit();

        List<Person> team1 = List.of(player);
        List<Person> team2 = List.of(opponent);
        battleManager.playerBattle(team1, team2, event.getChannel());

        boolean playerWon = player.getHp() > 0;
        int[] eloChanges = calculateElo(player.getArenaRating(), opponent.getArenaRating(), playerWon);

        ReentrantLock lock1 = getLock(id);
        ReentrantLock lock2 = getLock(opponent.getId());
        // Lock in consistent order to avoid deadlock
        ReentrantLock first = id.compareTo(opponent.getId()) < 0 ? lock1 : lock2;
        ReentrantLock second = id.compareTo(opponent.getId()) < 0 ? lock2 : lock1;
        first.lock();
        try {
            second.lock();
            try {
                Player p1 = playerRepository.get(id);
                Player p2 = playerRepository.get(opponent.getId());
                if (p1 != null) {
                    p1.setArenaRating(Math.max(100, p1.getArenaRating() + eloChanges[0]));
                    p1.setHp(Math.max(1, player.getHp())); // Восстанавливаем после боя
                    playerRepository.put(id, p1);
                }
                if (p2 != null) {
                    p2.setArenaRating(Math.max(100, p2.getArenaRating() + eloChanges[1]));
                    p2.setHp(Math.max(1, opponent.getHp()));
                    playerRepository.put(opponent.getId(), p2);
                }
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }

        String winner = playerWon ? player.getNickName() : opponent.getNickName();
        event.getChannel().sendMessage("🏆 Победитель: **" + winner + "**!\n" +
            "ELO изменения: **" + player.getNickName() + "** " + formatEloChange(eloChanges[0]) +
            " | **" + opponent.getNickName() + "** " + formatEloChange(eloChanges[1])).submit();
    }

    private int[] calculateElo(int rating1, int rating2, boolean player1Won) {
        double expected1 = 1.0 / (1 + Math.pow(10, (rating2 - rating1) / 400.0));
        int change1 = (int) Math.round(32 * ((player1Won ? 1 : 0) - expected1));
        return new int[]{change1, -change1};
    }

    private String formatEloChange(int change) {
        return (change >= 0 ? "+" : "") + change;
    }

    /** +арена топ — топ-10 по ELO */
    public void arenaLeaderboard(MessageReceivedEvent event) {
        List<Player> all = playerRepository.getAll();
        all.sort(Comparator.comparingInt(Player::getArenaRating).reversed());
        var sb = new StringBuilder("🏆 **Топ Арены (ELO):**\n\n");
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            Player p = all.get(i);
            String league = getLeague(p.getArenaRating());
            sb.append(String.format("%d. **%s** — %d ELO [%s]\n", i + 1, p.getNickName(), p.getArenaRating(), league));
        }
        event.getChannel().sendMessage(sb.toString()).submit();
    }

    /** +лига — показать лигу игрока */
    public void showLeague(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        String league = getLeague(player.getArenaRating());
        List<Player> all = playerRepository.getAll();
        long rank = all.stream().filter(p -> p.getArenaRating() > player.getArenaRating()).count() + 1;
        event.getChannel().sendMessage("🏅 **Лига " + player.getNickName() + ":** **" + league + "**\n" +
            "ELO: **" + player.getArenaRating() + "** | Место в рейтинге: **#" + rank + "**").submit();
    }

    private String getLeague(int rating) {
        if (rating < 1200) return "Бронза 🥉";
        if (rating < 1500) return "Серебро 🥈";
        if (rating < 1800) return "Золото 🥇";
        return "Платина 💎";
    }

    /** +арена 3v3 — командный бой 3 на 3 */
    public void teamArenaChallenge(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        List<Player> all = playerRepository.getAll();

        // Упрощённая механика: берём 3 случайных игрока для каждой команды
        List<Player> others = all.stream().filter(p -> !p.getId().equals(id)).limit(5).collect(Collectors.toList());
        if (others.size() < 2) {
            event.getChannel().sendMessage("Недостаточно игроков для 3v3 арены.").submit();
            return;
        }

        List<Person> team1 = new ArrayList<>();
        team1.add(player);
        List<Person> team2 = new ArrayList<>();
        for (int i = 0; i < Math.min(others.size(), 2); i++) {
            if (i == 0) team1.add(others.get(i));
            else team2.add(others.get(i));
        }
        if (team2.isEmpty()) {
            event.getChannel().sendMessage("Недостаточно игроков для формирования второй команды.").submit();
            return;
        }

        event.getChannel().sendMessage("⚔️ **Арена 3v3!** Команда **" + player.getNickName() + "** vs противники!").submit();
        battleManager.playerBattle(team1, team2, event.getChannel());

        boolean team1Won = team1.stream().anyMatch(p -> p.getHp() > 0);
        event.getChannel().sendMessage(team1Won ? "🏆 Твоя команда победила!" : "💀 Твоя команда проиграла!").submit();
    }

    /** +выживание — бой последний выживший */
    public void survivalChallenge(MessageReceivedEvent event) {
        event.getChannel().sendMessage("⚔️ **Выживание** — скоро! Система в разработке. Участвуй в **+арена** пока что.").submit();
    }

    /** +чемпион — назначить/показать дневного чемпиона */
    public void showChampion(MessageReceivedEvent event) {
        long now = System.currentTimeMillis();
        long todayStart = now - (now % (24 * 60 * 60 * 1000));

        if (dailyChampionId == null || championSetTime < todayStart) {
            // Назначаем случайного онлайн-игрока
            List<Player> all = playerRepository.getAll();
            List<Player> active = all.stream()
                .filter(p -> p.getDailyTime() > todayStart)
                .collect(Collectors.toList());
            if (active.isEmpty()) active = all;
            if (!active.isEmpty()) {
                Player champion = active.get(new Random().nextInt(active.size()));
                dailyChampionId = champion.getId();
                championSetTime = now;
            }
        }

        if (dailyChampionId != null) {
            Player champion = playerRepository.get(dailyChampionId);
            if (champion != null) {
                event.getChannel().sendMessage("👑 **Дневной чемпион:** **" + champion.getNickName() + "** [ELO: " +
                    champion.getArenaRating() + "]\nВызови его командой **+вызвать чемпиона**!").submit();
                return;
            }
        }
        event.getChannel().sendMessage("Чемпион дня ещё не назначен.").submit();
    }

    /** +вызвать чемпиона — бой с чемпионом */
    public void challengeChampion(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        if (dailyChampionId == null) {
            event.getChannel().sendMessage("Чемпион дня ещё не назначен. Используй **+чемпион** для назначения.").submit();
            return;
        }
        if (id.equals(dailyChampionId)) {
            event.getChannel().sendMessage("Ты сам являешься чемпионом дня!").submit();
            return;
        }

        Player challenger = playerRepository.get(id);
        Player champion = playerRepository.get(dailyChampionId);
        if (champion == null) {
            event.getChannel().sendMessage("Чемпион не найден.").submit();
            return;
        }

        event.getChannel().sendMessage("👑 **Вызов чемпиону!** **" + challenger.getNickName() + "** vs **" + champion.getNickName() + "**!").submit();
        battleManager.playerBattle(List.of(challenger), List.of(champion), event.getChannel());

        if (challenger.getHp() > 0) {
            // Победитель получает награду
            ReentrantLock lock = getLock(id);
            lock.lock();
            try {
                Player p = playerRepository.get(id);
                if (p != null) {
                    p.setMoney(p.getMoney() + 500);
                    if (p.getAchievements() == null) p.setAchievements(new ArrayList<>());
                    if (!p.getAchievements().contains("победитель_чемпиона")) {
                        p.getAchievements().add("победитель_чемпиона");
                    }
                    playerRepository.put(id, p);
                }
            } finally {
                lock.unlock();
            }
            event.getChannel().sendMessage("🏆 **" + challenger.getNickName() + "** победил чемпиона! +500 монет + достижение!").submit();
            dailyChampionId = id; // Новый чемпион!
        } else {
            event.getChannel().sendMessage("💀 Чемпион **" + champion.getNickName() + "** отстоял своё звание!").submit();
        }
    }
}
