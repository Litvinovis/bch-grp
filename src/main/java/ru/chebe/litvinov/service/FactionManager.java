package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер фракций.
 * Управляет репутацией игроков у фракций и их бонусами.
 */
public class FactionManager {

    private final PlayerRepository playerRepository;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private static final int REP_FOR_BONUS = 100;

    public FactionManager(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    private ReentrantLock getLock(String id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    /** +фракции — показать репутацию у фракций */
    public void showFactionRep(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        Map<String, Integer> factionRep = player.getFactionRep();
        if (factionRep == null || factionRep.isEmpty()) {
            factionRep = new HashMap<>(Map.of("ТОРГОВЦЫ", 0, "МАГИ", 0, "ВОИНЫ", 0));
        }
        var sb = new StringBuilder("⚖️ **Репутация у фракций:**\n\n");
        factionRep.forEach((faction, rep) -> {
            String bonus = getBonusDescription(faction, rep);
            String bar = progressBar(rep, REP_FOR_BONUS);
            sb.append("**").append(faction).append("**: ").append(rep).append("/100 ").append(bar).append("\n");
            if (!bonus.isEmpty()) sb.append("  ↳ Бонус: ").append(bonus).append("\n");
        });
        sb.append("\nРепутация растёт от: убийств мобов (ВОИНЫ), квестов (МАГИ), торговли (ТОРГОВЦЫ).");
        event.getChannel().sendMessage(sb.toString()).submit();
    }

    private String getBonusDescription(String faction, int rep) {
        if (rep < REP_FOR_BONUS) return "";
        return switch (faction) {
            case "ТОРГОВЦЫ" -> "5% скидка в магазине";
            case "МАГИ" -> "+доп. награда за квесты";
            case "ВОИНЫ" -> "+5 урона в боях";
            default -> "";
        };
    }

    private String progressBar(int current, int total) {
        int filled = (int) (8.0 * Math.min(current, total) / total);
        return "[" + "█".repeat(filled) + "░".repeat(8 - filled) + "]";
    }

    /**
     * Начисляет репутацию фракции.
     *
     * @param playerId идентификатор игрока
     * @param faction  название фракции
     * @param amount   количество репутации
     */
    public void addRep(String playerId, String faction, int amount) {
        ReentrantLock lock = getLock(playerId);
        lock.lock();
        try {
            Player player = playerRepository.get(playerId);
            if (player == null) return;
            if (player.getFactionRep() == null) {
                player.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 0, "МАГИ", 0, "ВОИНЫ", 0)));
            }
            player.getFactionRep().merge(faction, amount, (a, b) -> Math.min(200, a + b));
            playerRepository.put(playerId, player);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Применяет бонусы фракции (вызывается в нужном контексте).
     *
     * @param player  игрок
     * @param context "trade", "quest", "combat"
     * @return дополнительный бонус (деньги, урон и т.п.)
     */
    public int applyFactionBonuses(Player player, String context) {
        if (player.getFactionRep() == null) return 0;
        return switch (context) {
            case "trade" -> {
                int rep = player.getFactionRep().getOrDefault("ТОРГОВЦЫ", 0);
                yield rep >= REP_FOR_BONUS ? 5 : 0; // % скидки
            }
            case "combat" -> {
                int rep = player.getFactionRep().getOrDefault("ВОИНЫ", 0);
                yield rep >= REP_FOR_BONUS ? 5 : 0; // +5 урона
            }
            default -> 0;
        };
    }
}
