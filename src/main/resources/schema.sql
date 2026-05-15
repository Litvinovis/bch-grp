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
    achievements       TEXT NOT NULL DEFAULT '[]',
    active_buffs       TEXT NOT NULL DEFAULT '{}',
    location_history   TEXT NOT NULL DEFAULT '[]',
    last_explore_time  BIGINT NOT NULL DEFAULT 0,
    bank_inventory     TEXT NOT NULL DEFAULT '{}',
    completed_quests   TEXT NOT NULL DEFAULT '[]',
    debt               INTEGER NOT NULL DEFAULT 0,
    pvp_wins           INTEGER NOT NULL DEFAULT 0,
    mob_kills          INTEGER NOT NULL DEFAULT 0,
    prestige           INTEGER NOT NULL DEFAULT 0,
    last_horse_race    BIGINT NOT NULL DEFAULT 0
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
    name         TEXT PRIMARY KEY,
    leader_id    TEXT NOT NULL,
    members      TEXT NOT NULL DEFAULT '[]',
    appliers     TEXT NOT NULL DEFAULT '[]',
    clan_bank    TEXT NOT NULL DEFAULT '{}',
    clan_upgrades TEXT NOT NULL DEFAULT '[]',
    clan_base    TEXT NOT NULL DEFAULT 'респаун',
    clan_roles   TEXT NOT NULL DEFAULT '{}'
);

ALTER TABLE clans ADD COLUMN IF NOT EXISTS clan_bank     TEXT NOT NULL DEFAULT '{}';
ALTER TABLE clans ADD COLUMN IF NOT EXISTS clan_upgrades TEXT NOT NULL DEFAULT '[]';
ALTER TABLE clans ADD COLUMN IF NOT EXISTS clan_base     TEXT NOT NULL DEFAULT 'респаун';
ALTER TABLE clans ADD COLUMN IF NOT EXISTS clan_roles    TEXT NOT NULL DEFAULT '{}';

ALTER TABLE players ADD COLUMN IF NOT EXISTS location_history  TEXT NOT NULL DEFAULT '[]';
ALTER TABLE players ADD COLUMN IF NOT EXISTS last_explore_time BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS bank_inventory    TEXT NOT NULL DEFAULT '{}';
ALTER TABLE players ADD COLUMN IF NOT EXISTS completed_quests  TEXT NOT NULL DEFAULT '[]';
ALTER TABLE players ADD COLUMN IF NOT EXISTS debt              INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS pvp_wins          INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS mob_kills         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS prestige          INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS last_horse_race   BIGINT NOT NULL DEFAULT 0;

-- Items 85-150: new columns for existing tables
ALTER TABLE players ADD COLUMN IF NOT EXISTS pet              TEXT DEFAULT NULL;
ALTER TABLE players ADD COLUMN IF NOT EXISTS has_mount        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE players ADD COLUMN IF NOT EXISTS profession       TEXT NOT NULL DEFAULT '';
ALTER TABLE players ADD COLUMN IF NOT EXISTS profession_level INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS resources        TEXT NOT NULL DEFAULT '{}';
ALTER TABLE players ADD COLUMN IF NOT EXISTS jewelry          TEXT NOT NULL DEFAULT '{}';
ALTER TABLE players ADD COLUMN IF NOT EXISTS skill_points     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS skills           TEXT NOT NULL DEFAULT '{}';
ALTER TABLE players ADD COLUMN IF NOT EXISTS faction_rep      TEXT NOT NULL DEFAULT '{}';
ALTER TABLE players ADD COLUMN IF NOT EXISTS diary            TEXT NOT NULL DEFAULT '[]';
ALTER TABLE players ADD COLUMN IF NOT EXISTS last_monthly_bonus BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS arena_rating     INTEGER NOT NULL DEFAULT 1000;
ALTER TABLE players ADD COLUMN IF NOT EXISTS last_teleport_time BIGINT NOT NULL DEFAULT 0;

ALTER TABLE clans ADD COLUMN IF NOT EXISTS alliances         TEXT NOT NULL DEFAULT '[]';
ALTER TABLE clans ADD COLUMN IF NOT EXISTS fortress_upgrades TEXT NOT NULL DEFAULT '[]';
ALTER TABLE clans ADD COLUMN IF NOT EXISTS active_sieges     TEXT NOT NULL DEFAULT '{}';

-- Item 90: Game event log
CREATE TABLE IF NOT EXISTS game_event_log (
    id         SERIAL PRIMARY KEY,
    event_type TEXT NOT NULL,
    player_id  TEXT NOT NULL,
    details    TEXT,
    created_at BIGINT NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
);

-- Item 117-123: Territories
CREATE TABLE IF NOT EXISTS territories (
    location_name TEXT PRIMARY KEY,
    clan_name     TEXT NOT NULL DEFAULT '',
    captured_at   BIGINT NOT NULL DEFAULT 0,
    tax_rate      INTEGER NOT NULL DEFAULT 5
);

-- Items 124-130: World events
CREATE TABLE IF NOT EXISTS world_events (
    id         SERIAL PRIMARY KEY,
    event_type TEXT NOT NULL,
    data       TEXT NOT NULL DEFAULT '{}',
    started_at BIGINT NOT NULL,
    ends_at    BIGINT NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE
);

-- Items 138-144: Bounties
CREATE TABLE IF NOT EXISTS bounties (
    id         SERIAL PRIMARY KEY,
    target_id  TEXT NOT NULL,
    placer_id  TEXT NOT NULL,
    reward     INTEGER NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL
);

-- Items 145-150: Tournaments
CREATE TABLE IF NOT EXISTS tournaments (
    id           SERIAL PRIMARY KEY,
    type         TEXT NOT NULL,
    participants TEXT NOT NULL DEFAULT '[]',
    bracket      TEXT NOT NULL DEFAULT '{}',
    status       TEXT NOT NULL DEFAULT 'open',
    season       INTEGER NOT NULL DEFAULT 1
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
