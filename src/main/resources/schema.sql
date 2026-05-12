-- PostgreSQL schema for BCH-GRP bot
-- Converted from Ignite 3 DDL: ZONE removed, VARCHAR -> TEXT, INT -> INTEGER, DOUBLE -> DOUBLE PRECISION
-- "exp" renamed to player_exp (cleaner, no quoting needed in JDBC code)

CREATE TABLE IF NOT EXISTS players (
    id            TEXT PRIMARY KEY,
    nick_name     TEXT NOT NULL,
    hp            INTEGER NOT NULL DEFAULT 100,
    max_hp        INTEGER NOT NULL DEFAULT 100,
    luck          INTEGER NOT NULL DEFAULT 5,
    money         INTEGER NOT NULL DEFAULT 50,
    reputation    INTEGER NOT NULL DEFAULT 0,
    armor         INTEGER NOT NULL DEFAULT 0,
    strength      INTEGER NOT NULL DEFAULT 5,
    location      TEXT NOT NULL DEFAULT 'respawn',
    level         INTEGER NOT NULL DEFAULT 1,
    player_exp    INTEGER NOT NULL DEFAULT 0,
    exp_to_next   INTEGER NOT NULL DEFAULT 100,
    inventory     TEXT NOT NULL DEFAULT '{}',
    answer        TEXT NOT NULL DEFAULT '',
    active_event  TEXT,
    daily_time    BIGINT NOT NULL DEFAULT 0,
    clan_name     TEXT NOT NULL DEFAULT '',
    daily_streak  INTEGER NOT NULL DEFAULT 0,
    player_class  TEXT NOT NULL DEFAULT '',
    achievements  TEXT NOT NULL DEFAULT '[]',
    active_buffs  TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS locations (
    name               TEXT PRIMARY KEY,
    dangerous          INTEGER NOT NULL DEFAULT 0,
    population_by_name TEXT NOT NULL DEFAULT '[]',
    population_by_id   TEXT NOT NULL DEFAULT '[]',
    paths              TEXT NOT NULL DEFAULT '[]',
    pvp                BOOLEAN NOT NULL DEFAULT FALSE,
    boss               TEXT,
    boss_item          TEXT,
    teleport           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS items (
    name          TEXT PRIMARY KEY,
    description   TEXT,
    price         INTEGER NOT NULL DEFAULT 0,
    luck          INTEGER NOT NULL DEFAULT 0,
    strength      INTEGER NOT NULL DEFAULT 0,
    health        INTEGER NOT NULL DEFAULT 0,
    armor         INTEGER NOT NULL DEFAULT 0,
    reputation    INTEGER NOT NULL DEFAULT 0,
    xp_generation INTEGER NOT NULL DEFAULT 0,
    quantity      INTEGER NOT NULL DEFAULT 0,
    expire_time   BIGINT NOT NULL DEFAULT 0,
    action        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS bosses (
    nick_name TEXT PRIMARY KEY,
    hp        INTEGER NOT NULL DEFAULT 1000,
    strength  INTEGER NOT NULL DEFAULT 10,
    armor     INTEGER NOT NULL DEFAULT 0,
    boss_item TEXT,
    defeat    INTEGER NOT NULL DEFAULT 0,
    win       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ideas (
    id          INTEGER PRIMARY KEY,
    description TEXT,
    author      TEXT,
    resolution  TEXT NOT NULL DEFAULT 'New'
);

CREATE TABLE IF NOT EXISTS clans (
    name      TEXT PRIMARY KEY,
    leader_id TEXT NOT NULL,
    members   TEXT NOT NULL DEFAULT '[]',
    appliers  TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS daily_quests (
    user_id          TEXT NOT NULL,
    quest_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    quest1_type      TEXT NOT NULL,
    quest1_progress  INTEGER NOT NULL DEFAULT 0,
    quest1_required  INTEGER NOT NULL,
    quest1_done      BOOLEAN NOT NULL DEFAULT FALSE,
    quest2_type      TEXT NOT NULL,
    quest2_progress  INTEGER NOT NULL DEFAULT 0,
    quest2_required  INTEGER NOT NULL,
    quest2_done      BOOLEAN NOT NULL DEFAULT FALSE,
    quest3_type      TEXT NOT NULL,
    quest3_progress  INTEGER NOT NULL DEFAULT 0,
    quest3_required  INTEGER NOT NULL,
    quest3_done      BOOLEAN NOT NULL DEFAULT FALSE,
    bonus_claimed    BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, quest_date)
);
