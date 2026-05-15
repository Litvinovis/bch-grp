package ru.chebe.litvinov.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Event;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.util.JsonUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerRepository.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final DataSource dataSource;

    public PlayerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String SELECT_COLS =
        "id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, location, level, " +
        "player_exp, exp_to_next, inventory, answer, active_event, daily_time, clan_name, daily_streak, player_class, achievements, active_buffs, " +
        "location_history, last_explore_time, bank_inventory, completed_quests, debt, pvp_wins, mob_kills, prestige, last_horse_race, " +
        "pet, has_mount, profession, profession_level, resources, jewelry, skill_points, skills, faction_rep, diary, last_monthly_bonus, arena_rating";

    public List<Player> getAll() {
        List<Player> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + SELECT_COLS + " FROM players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (Exception e) {
            log.warn("Ошибка getAll(): {}", e.getMessage());
        }
        return result;
    }

    public Player get(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + SELECT_COLS + " FROM players WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            log.warn("Ошибка get({}): {}", id, e.getMessage());
        }
        return null;
    }

    public boolean contains(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM players WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) {
            log.warn("Ошибка contains({}): {}", id, e.getMessage());
        }
        return false;
    }

    public void put(String id, Player player) {
        String eventJson = serializeEvent(player);
        String petJson = player.getPet() != null ? JsonUtil.toJson(player.getPet()) : null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO players (id, nick_name, hp, max_hp, luck, money, reputation, armor, strength, location, level, " +
                 "player_exp, exp_to_next, inventory, answer, active_event, daily_time, clan_name, daily_streak, player_class, achievements, active_buffs, " +
                 "location_history, last_explore_time, bank_inventory, completed_quests, debt, pvp_wins, mob_kills, prestige, last_horse_race, " +
                 "pet, has_mount, profession, profession_level, resources, jewelry, skill_points, skills, faction_rep, diary, last_monthly_bonus, arena_rating) " +
                 "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                 "ON CONFLICT (id) DO UPDATE SET " +
                 "nick_name=EXCLUDED.nick_name, hp=EXCLUDED.hp, max_hp=EXCLUDED.max_hp, luck=EXCLUDED.luck, " +
                 "money=EXCLUDED.money, reputation=EXCLUDED.reputation, armor=EXCLUDED.armor, strength=EXCLUDED.strength, " +
                 "location=EXCLUDED.location, level=EXCLUDED.level, player_exp=EXCLUDED.player_exp, exp_to_next=EXCLUDED.exp_to_next, " +
                 "inventory=EXCLUDED.inventory, answer=EXCLUDED.answer, active_event=EXCLUDED.active_event, " +
                 "daily_time=EXCLUDED.daily_time, clan_name=EXCLUDED.clan_name, daily_streak=EXCLUDED.daily_streak, " +
                 "player_class=EXCLUDED.player_class, achievements=EXCLUDED.achievements, active_buffs=EXCLUDED.active_buffs, " +
                 "location_history=EXCLUDED.location_history, last_explore_time=EXCLUDED.last_explore_time, " +
                 "bank_inventory=EXCLUDED.bank_inventory, completed_quests=EXCLUDED.completed_quests, " +
                 "debt=EXCLUDED.debt, pvp_wins=EXCLUDED.pvp_wins, mob_kills=EXCLUDED.mob_kills, " +
                 "prestige=EXCLUDED.prestige, last_horse_race=EXCLUDED.last_horse_race, " +
                 "pet=EXCLUDED.pet, has_mount=EXCLUDED.has_mount, profession=EXCLUDED.profession, " +
                 "profession_level=EXCLUDED.profession_level, resources=EXCLUDED.resources, jewelry=EXCLUDED.jewelry, " +
                 "skill_points=EXCLUDED.skill_points, skills=EXCLUDED.skills, faction_rep=EXCLUDED.faction_rep, " +
                 "diary=EXCLUDED.diary, last_monthly_bonus=EXCLUDED.last_monthly_bonus, arena_rating=EXCLUDED.arena_rating")) {
            ps.setString(1, id);
            ps.setString(2, player.getNickName());
            ps.setInt(3, player.getHp());
            ps.setInt(4, player.getMaxHp());
            ps.setInt(5, player.getLuck());
            ps.setInt(6, player.getMoney());
            ps.setInt(7, player.getReputation());
            ps.setInt(8, player.getArmor());
            ps.setInt(9, player.getStrength());
            ps.setString(10, player.getLocation());
            ps.setInt(11, player.getLevel());
            ps.setInt(12, player.getExp());
            ps.setInt(13, player.getExpToNextLvl());
            ps.setString(14, JsonUtil.toJson(player.getInventory()));
            ps.setString(15, player.getAnswer() != null ? player.getAnswer() : "");
            ps.setString(16, eventJson);
            ps.setLong(17, player.getDailyTime());
            ps.setString(18, player.getClanName() != null ? player.getClanName() : "");
            ps.setInt(19, player.getDailyStreak());
            ps.setString(20, player.getPlayerClass() != null ? player.getPlayerClass() : "");
            ps.setString(21, JsonUtil.toJson(player.getAchievements() != null ? player.getAchievements() : new ArrayList<>()));
            ps.setString(22, JsonUtil.toJson(player.getActiveBuffs() != null ? player.getActiveBuffs() : new HashMap<>()));
            ps.setString(23, JsonUtil.toJson(player.getLocationHistory() != null ? player.getLocationHistory() : new ArrayList<>()));
            ps.setLong(24, player.getLastExploreTime());
            ps.setString(25, JsonUtil.toJson(player.getBankInventory() != null ? player.getBankInventory() : new HashMap<>()));
            ps.setString(26, JsonUtil.toJson(player.getCompletedQuests() != null ? player.getCompletedQuests() : new ArrayList<>()));
            ps.setInt(27, player.getDebt());
            ps.setInt(28, player.getPvpWins());
            ps.setInt(29, player.getMobKills());
            ps.setInt(30, player.getPrestige());
            ps.setLong(31, player.getLastHorseRaceTime());
            ps.setString(32, petJson);
            ps.setBoolean(33, player.isHasMount());
            ps.setString(34, player.getProfession() != null ? player.getProfession() : "");
            ps.setInt(35, player.getProfessionLevel());
            ps.setString(36, JsonUtil.toJson(player.getResources() != null ? player.getResources() : new HashMap<>()));
            ps.setString(37, JsonUtil.toJson(player.getJewelry() != null ? player.getJewelry() : new HashMap<>()));
            ps.setInt(38, player.getSkillPoints());
            ps.setString(39, JsonUtil.toJson(player.getSkills() != null ? player.getSkills() : new HashMap<>()));
            ps.setString(40, JsonUtil.toJson(player.getFactionRep() != null ? player.getFactionRep() : new HashMap<>()));
            ps.setString(41, JsonUtil.toJson(player.getDiary() != null ? player.getDiary() : new ArrayList<>()));
            ps.setLong(42, player.getLastMonthlyBonus());
            ps.setInt(43, player.getArenaRating());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка put({}): {}", id, e.getMessage());
        }
    }

    public void remove(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM players WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Ошибка remove({}): {}", id, e.getMessage());
        }
    }

    private Player mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String nickName = rs.getString("nick_name");
        Player p = new Player(nickName, id);
        p.setHp(rs.getInt("hp"));
        p.setMaxHp(rs.getInt("max_hp"));
        p.setLuck(rs.getInt("luck"));
        p.setMoney(rs.getInt("money"));
        p.setReputation(rs.getInt("reputation"));
        p.setArmor(rs.getInt("armor"));
        p.setStrength(rs.getInt("strength"));
        p.setLocation(rs.getString("location"));
        p.setLevel(rs.getInt("level"));
        p.setExp(rs.getInt("player_exp"));
        p.setExpToNextLvl(rs.getInt("exp_to_next"));
        p.setAnswer(rs.getString("answer") != null ? rs.getString("answer") : "");
        p.setDailyTime(rs.getLong("daily_time"));
        p.setClanName(rs.getString("clan_name") != null ? rs.getString("clan_name") : "");
        p.setDailyStreak(rs.getInt("daily_streak"));
        p.setPlayerClass(rs.getString("player_class") != null ? rs.getString("player_class") : "");
        p.setAchievements(JsonUtil.fromJsonToListString(rs.getString("achievements")));
        p.setInventory(JsonUtil.fromJsonToMapStringInt(rs.getString("inventory")));
        p.setActiveBuffs(JsonUtil.fromJsonToMapStringLong(rs.getString("active_buffs")));
        try { p.setLocationHistory(JsonUtil.fromJsonToListString(rs.getString("location_history"))); } catch (Exception e) { p.setLocationHistory(new ArrayList<>()); }
        try { p.setLastExploreTime(rs.getLong("last_explore_time")); } catch (Exception e) { p.setLastExploreTime(0); }
        try { p.setBankInventory(JsonUtil.fromJsonToMapStringInt(rs.getString("bank_inventory"))); } catch (Exception e) { p.setBankInventory(new HashMap<>()); }
        try { p.setCompletedQuests(JsonUtil.fromJsonToListString(rs.getString("completed_quests"))); } catch (Exception e) { p.setCompletedQuests(new ArrayList<>()); }
        try { p.setDebt(rs.getInt("debt")); } catch (Exception e) { p.setDebt(0); }
        try { p.setPvpWins(rs.getInt("pvp_wins")); } catch (Exception e) { p.setPvpWins(0); }
        try { p.setMobKills(rs.getInt("mob_kills")); } catch (Exception e) { p.setMobKills(0); }
        try { p.setPrestige(rs.getInt("prestige")); } catch (Exception e) { p.setPrestige(0); }
        try { p.setLastHorseRaceTime(rs.getLong("last_horse_race")); } catch (Exception e) { p.setLastHorseRaceTime(0); }
        // New fields (items 85-150)
        try {
            String petJson = rs.getString("pet");
            if (petJson != null && !petJson.isBlank() && !petJson.equals("null")) {
                p.setPet(MAPPER.readValue(petJson, ru.chebe.litvinov.data.Pet.class));
            }
        } catch (Exception e) { p.setPet(null); }
        try { p.setHasMount(rs.getBoolean("has_mount")); } catch (Exception e) { p.setHasMount(false); }
        try { p.setProfession(rs.getString("profession") != null ? rs.getString("profession") : ""); } catch (Exception e) { p.setProfession(""); }
        try { p.setProfessionLevel(rs.getInt("profession_level")); } catch (Exception e) { p.setProfessionLevel(0); }
        try { p.setResources(JsonUtil.fromJsonToMapStringInt(rs.getString("resources"))); } catch (Exception e) { p.setResources(new HashMap<>()); }
        try { p.setJewelry(JsonUtil.fromJsonToMapStringInt(rs.getString("jewelry"))); } catch (Exception e) { p.setJewelry(new HashMap<>()); }
        try { p.setSkillPoints(rs.getInt("skill_points")); } catch (Exception e) { p.setSkillPoints(0); }
        try { p.setSkills(JsonUtil.fromJsonToMapStringInt(rs.getString("skills"))); } catch (Exception e) { p.setSkills(new HashMap<>()); }
        try { p.setFactionRep(JsonUtil.fromJsonToMapStringInt(rs.getString("faction_rep"))); } catch (Exception e) { p.setFactionRep(new HashMap<>(Map.of("ТОРГОВЦЫ", 0, "МАГИ", 0, "ВОИНЫ", 0))); }
        try { p.setDiary(JsonUtil.fromJsonToListString(rs.getString("diary"))); } catch (Exception e) { p.setDiary(new ArrayList<>()); }
        try { p.setLastMonthlyBonus(rs.getLong("last_monthly_bonus")); } catch (Exception e) { p.setLastMonthlyBonus(0); }
        try { p.setArenaRating(rs.getInt("arena_rating")); } catch (Exception e) { p.setArenaRating(1000); }

        String eventJson = rs.getString("active_event");
        if (eventJson != null && !eventJson.isBlank() && !eventJson.equals("null")) {
            try {
                p.setActiveEvent(MAPPER.readValue(eventJson, Event.class));
            } catch (Exception e) {
                log.warn("Не удалось десериализовать activeEvent для игрока {}: {}", id, e.getMessage());
                p.setActiveEvent(null);
            }
        }
        return p;
    }

    private String serializeEvent(Player player) {
        if (player.getActiveEvent() == null) return null;
        try {
            return MAPPER.writeValueAsString(player.getActiveEvent());
        } catch (Exception e) {
            log.warn("Не удалось сериализовать activeEvent для игрока {}: {}", player.getId(), e.getMessage());
            return null;
        }
    }
}
