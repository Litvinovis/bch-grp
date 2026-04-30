package ru.chebe.litvinov;

/**
 * Балансные параметры игры: штрафы, награды, шансы.
 * Все числовые константы, влияющие на геймплей, собраны здесь.
 */
public final class GameBalance {

	private GameBalance() {}

	// ---- Смерть ----
	public static final double DEATH_MONEY_PENALTY_LOW  = 0.05; // уровни 1-5
	public static final double DEATH_MONEY_PENALTY_MID  = 0.10; // уровни 6-15
	public static final double DEATH_MONEY_PENALTY_HIGH = 0.15; // уровни 16-30
	public static final double DEATH_MONEY_PENALTY_MAX  = 0.20; // уровни 31+
	public static final double DEATH_XP_LOSS_PCT        = 0.05;

	// ---- Победа над мобом ----
	public static final int MOB_KILL_XP    = 10;
	public static final int MOB_KILL_MONEY = 10;

	// ---- Восстановление HP после боя с мобом ----
	public static final int HP_RECOVERY_PCT_LOW_LEVEL = 75; // уровень < 3
	public static final int HP_RECOVERY_PCT_NORMAL    = 50;
	public static final int HP_RECOVERY_MIN           = 10;
	public static final int HP_RECOVERY_LOW_LEVEL_THRESHOLD = 3;

	// ---- Дроп предмета с моба ----
	public static final int DROP_CHANCE_BASE_PCT = 20;
	public static final int DROP_CHANCE_PER_LUCK = 1;
	public static final int DROP_CHANCE_MAX_PCT  = 60;

	// ---- Победа над боссом ----
	public static final int BOSS_KILL_XP    = 1000;
	public static final int BOSS_KILL_MONEY = 1000;

	// ---- PvP ----
	public static final int PVP_WIN_MONEY = 200;
	public static final int PVP_WIN_XP    = 150;

	// ---- Ежедневный бонус ----
	public static final int    DAILY_BONUS_BASE               = 50;
	public static final int    DAILY_BONUS_PER_LEVEL          = 5;
	public static final int    DAILY_STREAK_3_BONUS           = 50;
	public static final int    DAILY_STREAK_RARE_ITEM_INTERVAL = 7;
	public static final String DAILY_STREAK_RARE_ITEM         = "вино лаба";
	public static final long   ONE_DAY_MS                     = 24 * 60 * 60 * 1000L;
	public static final long   TWO_DAYS_MS                    = 48 * 60 * 60 * 1000L;

	// ---- Квесты ----
	public static final int QUEST_CHANGE_FEE = 20;
}
