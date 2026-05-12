package ru.chebe.litvinov.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.DailyQuest;
import ru.chebe.litvinov.repository.DailyQuestRepository;

/**
 * Сервис ежедневных квестов.
 * Отвечает за получение/создание квестов, обновление прогресса,
 * выдачу бонуса за выполнение всех трёх и форматирование вывода.
 */
public class DailyQuestService {

    private static final Logger log = LoggerFactory.getLogger(DailyQuestService.class);

    /** Бонус XP за выполнение всех трёх квестов. */
    public static final int BONUS_XP = 300;
    /** Бонус монет за выполнение всех трёх квестов. */
    public static final int BONUS_MONEY = 200;

    private final DailyQuestRepository repository;
    private final PlayersManager playersManager;

    public DailyQuestService(DailyQuestRepository repository, PlayersManager playersManager) {
        this.repository = repository;
        this.playersManager = playersManager;
    }

    /**
     * Возвращает (или создаёт) набор квестов игрока на сегодня.
     *
     * @param userId идентификатор игрока
     * @return запись квестов на сегодня
     */
    public DailyQuest getDailyQuests(String userId) {
        return repository.getOrCreate(userId);
    }

    /**
     * Увеличивает прогресс квеста указанного типа на {@code amount}.
     * Помечает квест выполненным при достижении цели.
     * Если все три выполнены и бонус ещё не выдан — начисляет бонус.
     *
     * @param userId    идентификатор игрока
     * @param questType тип квеста (например «KILL_NPC»)
     * @param amount    величина прироста
     */
    public void incrementProgress(String userId, String questType, int amount) {
        DailyQuest q = repository.getOrCreate(userId);

        boolean changed = false;

        if (!q.isQuest1Done() && questType.equals(q.getQuest1Type())) {
            int newProg = Math.min(q.getQuest1Progress() + amount, q.getQuest1Required());
            q.setQuest1Progress(newProg);
            if (newProg >= q.getQuest1Required()) q.setQuest1Done(true);
            changed = true;
        }
        if (!q.isQuest2Done() && questType.equals(q.getQuest2Type())) {
            int newProg = Math.min(q.getQuest2Progress() + amount, q.getQuest2Required());
            q.setQuest2Progress(newProg);
            if (newProg >= q.getQuest2Required()) q.setQuest2Done(true);
            changed = true;
        }
        if (!q.isQuest3Done() && questType.equals(q.getQuest3Type())) {
            int newProg = Math.min(q.getQuest3Progress() + amount, q.getQuest3Required());
            q.setQuest3Progress(newProg);
            if (newProg >= q.getQuest3Required()) q.setQuest3Done(true);
            changed = true;
        }

        if (!changed) return;

        repository.update(q);

        // Проверяем, все ли три квеста выполнены и бонус ещё не выдан
        if (q.isQuest1Done() && q.isQuest2Done() && q.isQuest3Done() && !q.isBonusClaimed()) {
            repository.claimBonus(userId);
            try {
                playersManager.changeXp(userId, BONUS_XP);
                playersManager.changeMoney(userId, BONUS_MONEY, true);
                log.info("Бонус за все дневные квесты выдан игроку {}: +{}XP +{}монет", userId, BONUS_XP, BONUS_MONEY);
            } catch (Exception e) {
                log.error("Ошибка начисления бонуса за квесты игроку {}: {}", userId, e.getMessage());
            }
        }
    }

    /**
     * Форматирует строку для Discord с отображением квестов и их прогресса.
     *
     * @param q запись квестов
     * @return строка для отправки в Discord
     */
    public String formatQuests(DailyQuest q) {
        StringBuilder sb = new StringBuilder("**Дневные квесты** (сброс каждый день)\n\n");
        sb.append(formatQuestLine(1, q.getQuest1Type(), q.getQuest1Progress(), q.getQuest1Required(), q.isQuest1Done()));
        sb.append(formatQuestLine(2, q.getQuest2Type(), q.getQuest2Progress(), q.getQuest2Required(), q.isQuest2Done()));
        sb.append(formatQuestLine(3, q.getQuest3Type(), q.getQuest3Progress(), q.getQuest3Required(), q.isQuest3Done()));

        if (q.isQuest1Done() && q.isQuest2Done() && q.isQuest3Done()) {
            if (q.isBonusClaimed()) {
                sb.append("\n🎉 Все дневные квесты выполнены! Бонус уже получен.");
            } else {
                sb.append("\n🎉 Все дневные квесты выполнены!");
            }
        } else {
            sb.append("\n🏆 **Бонус за все квесты:** +").append(BONUS_XP).append(" XP, +").append(BONUS_MONEY).append(" монет");
        }

        return sb.toString();
    }

    private String formatQuestLine(int n, String type, int progress, int required, boolean done) {
        String icon = done ? "✅" : "⬜";
        String label = questLabel(type);
        return String.format("%s %d. %s — %d/%d\n", icon, n, label, progress, required);
    }

    private String questLabel(String type) {
        return switch (type) {
            case "KILL_NPC"     -> "Убить мобов";
            case "WIN_TAVERN"   -> "Победить в таверне";
            case "EARN_GOLD"    -> "Заработать монет";
            case "DEFEAT_BOSS"  -> "Убить босса";
            default             -> type;
        };
    }
}
