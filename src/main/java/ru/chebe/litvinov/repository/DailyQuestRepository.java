package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.DailyQuest;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Репозиторий для хранения ежедневных квестов игроков в PostgreSQL.
 */
public class DailyQuestRepository {

    private static final Logger log = LoggerFactory.getLogger(DailyQuestRepository.class);

    /** Типы квестов и их цели. */
    private static final String[] QUEST_TYPES = {"KILL_NPC", "WIN_TAVERN", "EARN_GOLD", "DEFEAT_BOSS"};
    private static final int[] QUEST_REQUIRED = {5, 3, 200, 1};

    private final DataSource dataSource;

    public DailyQuestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Возвращает запись квестов на сегодня. Если её нет — создаёт новую со случайными 3 квестами.
     *
     * @param userId идентификатор игрока
     * @return запись квестов на сегодня
     */
    public DailyQuest getOrCreate(String userId) {
        LocalDate today = LocalDate.now();
        DailyQuest existing = findByUserAndDate(userId, today);
        if (existing != null) return existing;

        DailyQuest quest = createRandom(userId, today);
        insert(quest);
        return quest;
    }

    /**
     * Сохраняет изменения в записи квестов (прогресс, done, bonusClaimed).
     *
     * @param quest обновлённая запись
     */
    public void update(DailyQuest quest) {
        String sql = "UPDATE daily_quests SET " +
                "quest1_progress=?, quest1_done=?, " +
                "quest2_progress=?, quest2_done=?, " +
                "quest3_progress=?, quest3_done=?, " +
                "bonus_claimed=? " +
                "WHERE user_id=? AND quest_date=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quest.getQuest1Progress());
            ps.setBoolean(2, quest.isQuest1Done());
            ps.setInt(3, quest.getQuest2Progress());
            ps.setBoolean(4, quest.isQuest2Done());
            ps.setInt(5, quest.getQuest3Progress());
            ps.setBoolean(6, quest.isQuest3Done());
            ps.setBoolean(7, quest.isBonusClaimed());
            ps.setString(8, quest.getUserId());
            ps.setDate(9, Date.valueOf(quest.getQuestDate()));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка update daily_quests для {}", quest.getUserId(), e);
        }
    }

    /**
     * Устанавливает bonus_claimed = true для пользователя на сегодня.
     *
     * @param userId идентификатор игрока
     */
    public void claimBonus(String userId) {
        LocalDate today = LocalDate.now();
        String sql = "UPDATE daily_quests SET bonus_claimed=TRUE WHERE user_id=? AND quest_date=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setDate(2, Date.valueOf(today));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка claimBonus для {}", userId, e);
        }
    }

    // --- private helpers ---

    private DailyQuest findByUserAndDate(String userId, LocalDate date) {
        String sql = "SELECT * FROM daily_quests WHERE user_id=? AND quest_date=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.error("Ошибка findByUserAndDate({}, {})", userId, date, e);
        }
        return null;
    }

    private void insert(DailyQuest q) {
        String sql = "INSERT INTO daily_quests " +
                "(user_id, quest_date, quest1_type, quest1_progress, quest1_required, quest1_done, " +
                "quest2_type, quest2_progress, quest2_required, quest2_done, " +
                "quest3_type, quest3_progress, quest3_required, quest3_done, bonus_claimed) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q.getUserId());
            ps.setDate(2, Date.valueOf(q.getQuestDate()));
            ps.setString(3, q.getQuest1Type());
            ps.setInt(4, q.getQuest1Progress());
            ps.setInt(5, q.getQuest1Required());
            ps.setBoolean(6, q.isQuest1Done());
            ps.setString(7, q.getQuest2Type());
            ps.setInt(8, q.getQuest2Progress());
            ps.setInt(9, q.getQuest2Required());
            ps.setBoolean(10, q.isQuest2Done());
            ps.setString(11, q.getQuest3Type());
            ps.setInt(12, q.getQuest3Progress());
            ps.setInt(13, q.getQuest3Required());
            ps.setBoolean(14, q.isQuest3Done());
            ps.setBoolean(15, q.isBonusClaimed());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка insert daily_quests для {}", q.getUserId(), e);
        }
    }

    /** Создаёт новую запись с 3 случайными уникальными квестами. */
    public static DailyQuest createRandom(String userId, LocalDate date) {
        List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(indices);

        DailyQuest q = new DailyQuest();
        q.setUserId(userId);
        q.setQuestDate(date);

        q.setQuest1Type(QUEST_TYPES[indices.get(0)]);
        q.setQuest1Required(QUEST_REQUIRED[indices.get(0)]);
        q.setQuest2Type(QUEST_TYPES[indices.get(1)]);
        q.setQuest2Required(QUEST_REQUIRED[indices.get(1)]);
        q.setQuest3Type(QUEST_TYPES[indices.get(2)]);
        q.setQuest3Required(QUEST_REQUIRED[indices.get(2)]);

        return q;
    }

    private static DailyQuest mapRow(ResultSet rs) throws SQLException {
        DailyQuest q = new DailyQuest();
        q.setUserId(rs.getString("user_id"));
        q.setQuestDate(rs.getDate("quest_date").toLocalDate());
        q.setQuest1Type(rs.getString("quest1_type"));
        q.setQuest1Progress(rs.getInt("quest1_progress"));
        q.setQuest1Required(rs.getInt("quest1_required"));
        q.setQuest1Done(rs.getBoolean("quest1_done"));
        q.setQuest2Type(rs.getString("quest2_type"));
        q.setQuest2Progress(rs.getInt("quest2_progress"));
        q.setQuest2Required(rs.getInt("quest2_required"));
        q.setQuest2Done(rs.getBoolean("quest2_done"));
        q.setQuest3Type(rs.getString("quest3_type"));
        q.setQuest3Progress(rs.getInt("quest3_progress"));
        q.setQuest3Required(rs.getInt("quest3_required"));
        q.setQuest3Done(rs.getBoolean("quest3_done"));
        q.setBonusClaimed(rs.getBoolean("bonus_claimed"));
        return q;
    }
}
