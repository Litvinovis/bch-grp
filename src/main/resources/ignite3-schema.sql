-- DDL для таблиц BCH-GRP в Apache Ignite 3.x (3.0.0)

-- Зона хранения данных BCH-GRP (STORAGE_PROFILES обязателен в 3.0.0)
CREATE ZONE IF NOT EXISTS bchgrp WITH STORAGE_PROFILES='default', REPLICAS=1, PARTITIONS=25;

-- Таблица игроков
-- inventory хранится как JSON: {"предмет": количество, ...}
-- "exp" в кавычках — зарезервированное слово SQL
CREATE TABLE IF NOT EXISTS players (
    id            VARCHAR PRIMARY KEY,
    nick_name     VARCHAR NOT NULL,
    hp            INT NOT NULL DEFAULT 100,
    max_hp        INT NOT NULL DEFAULT 100,
    luck          INT NOT NULL DEFAULT 5,
    money         INT NOT NULL DEFAULT 50,
    reputation    INT NOT NULL DEFAULT 0,
    armor         INT NOT NULL DEFAULT 0,
    strength      INT NOT NULL DEFAULT 5,
    location      VARCHAR NOT NULL DEFAULT 'respawn',
    level         INT NOT NULL DEFAULT 1,
    "exp"         INT NOT NULL DEFAULT 0,
    exp_to_next   INT NOT NULL DEFAULT 100,
    inventory     VARCHAR NOT NULL DEFAULT '{}',
    answer        VARCHAR NOT NULL DEFAULT '',
    active_event  VARCHAR,
    daily_time    BIGINT NOT NULL DEFAULT 0,
    clan_name     VARCHAR NOT NULL DEFAULT ''
) ZONE bchgrp;

-- Таблица локаций
CREATE TABLE IF NOT EXISTS locations (
    name               VARCHAR PRIMARY KEY,
    dangerous          INT NOT NULL DEFAULT 0,
    population_by_name VARCHAR NOT NULL DEFAULT '[]',
    population_by_id   VARCHAR NOT NULL DEFAULT '[]',
    paths              VARCHAR NOT NULL DEFAULT '[]',
    pvp                BOOLEAN NOT NULL DEFAULT FALSE,
    boss               VARCHAR,
    boss_item          VARCHAR,
    teleport           BOOLEAN NOT NULL DEFAULT FALSE
) ZONE bchgrp;

-- Таблица предметов
CREATE TABLE IF NOT EXISTS items (
    name          VARCHAR PRIMARY KEY,
    description   VARCHAR,
    price         INT NOT NULL DEFAULT 0,
    luck          INT NOT NULL DEFAULT 0,
    strength      INT NOT NULL DEFAULT 0,
    health        INT NOT NULL DEFAULT 0,
    armor         INT NOT NULL DEFAULT 0,
    reputation    INT NOT NULL DEFAULT 0,
    xp_generation INT NOT NULL DEFAULT 0,
    quantity      INT NOT NULL DEFAULT 0,
    expire_time   BIGINT NOT NULL DEFAULT 0,
    action        BOOLEAN NOT NULL DEFAULT FALSE
) ZONE bchgrp;

-- Таблица боссов
CREATE TABLE IF NOT EXISTS bosses (
    nick_name  VARCHAR PRIMARY KEY,
    hp         INT NOT NULL DEFAULT 1000,
    strength   INT NOT NULL DEFAULT 10,
    armor      INT NOT NULL DEFAULT 0,
    boss_item  VARCHAR,
    defeat     INT NOT NULL DEFAULT 0,
    win        INT NOT NULL DEFAULT 0
) ZONE bchgrp;

-- Таблица идей
CREATE TABLE IF NOT EXISTS ideas (
    id          INT PRIMARY KEY,
    description VARCHAR,
    author      VARCHAR,
    resolution  VARCHAR NOT NULL DEFAULT 'New'
) ZONE bchgrp;

-- Таблица кланов
CREATE TABLE IF NOT EXISTS clans (
    name      VARCHAR PRIMARY KEY,
    leader_id VARCHAR NOT NULL,
    members   VARCHAR NOT NULL DEFAULT '[]',
    appliers  VARCHAR NOT NULL DEFAULT '[]'
) ZONE bchgrp;
