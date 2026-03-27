package ru.chebe.litvinov.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.BotConfig;
import ru.chebe.litvinov.IgniteConfigurator;
import ru.chebe.litvinov.data.*;
import ru.chebe.litvinov.ignite3.*;
import ru.chebe.litvinov.ignite3.SchemaInitializer;
import ru.chebe.litvinov.util.JsonUtil;

import javax.cache.Cache;
import java.util.List;

/**
 * Инструмент миграции данных из Apache Ignite 2.x в Apache Ignite 3.x.
 *
 * <p>Подключается к Ignite 2.x (read-only) через thin client или embedded-режим,
 * читает все кэши BCH-GRP и записывает данные в таблицы Ignite 3.
 *
 * <p>Запуск:
 * <pre>
 *   mvn exec:java@migrate \
 *     -Dignite2.address=127.0.0.1:47650..47659 \
 *     -Dignite3.address=127.0.0.1:10300
 * </pre>
 *
 * <p>Или через fat-jar:
 * <pre>
 *   java -cp target/bchgrp-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *     ru.chebe.litvinov.migration.DataMigration \
 *     [ignite2_discovery_address] [ignite3_client_address]
 * </pre>
 */
public class DataMigration {

    private static final Logger log = LoggerFactory.getLogger(DataMigration.class);

    /** Имя Ignite 2 кластера BCH-GRP */
    private static final String IGNITE2_INSTANCE_NAME = "bchgrp-migrator";

    public static void main(String[] args) {
        String ignite2Address = System.getProperty("ignite2.address",
                args.length > 0 ? args[0] : "127.0.0.1:47650..47659");
        String ignite3Address = System.getProperty("ignite3.address",
                args.length > 1 ? args[1] : "127.0.0.1:10300");

        log.info("=== BCH-GRP Data Migration: Ignite 2 -> Ignite 3 ===");
        log.info("Ignite 2.x discovery: {}", ignite2Address);
        log.info("Ignite 3.x client:    {}", ignite3Address);

        // Подключаемся к Ignite 2.x
        log.info("Подключение к Ignite 2.x...");
        Ignite ignite2 = connectIgnite2(ignite2Address);
        log.info("Ignite 2.x подключён. Состояние кластера: {}", ignite2.cluster().state());

        // Подключаемся к Ignite 3.x
        log.info("Подключение к Ignite 3.x...");
        IgniteClient ignite3 = connectIgnite3(ignite3Address);
        log.info("Ignite 3.x подключён.");

        // Инициализируем схему таблиц Ignite 3
        log.info("Инициализация схемы Ignite 3...");
        new SchemaInitializer(ignite3).init();

        // Репозитории Ignite 3
        PlayerRepository playerRepo = new PlayerRepository(ignite3);
        LocationRepository locationRepo = new LocationRepository(ignite3);
        ItemRepository itemRepo = new ItemRepository(ignite3);
        BossRepository bossRepo = new BossRepository(ignite3);
        IdeaRepository ideaRepo = new IdeaRepository(ignite3);
        ClanRepository clanRepo = new ClanRepository(ignite3);

        int total = 0;

        // === Миграция Players ===
        log.info("Миграция players...");
        int players = 0;
        try (var cursor = ignite2.cache("players").query(new ScanQuery<>())) {
            for (Object raw : cursor) {
                @SuppressWarnings("unchecked")
                Cache.Entry<String, Player> entry = (Cache.Entry<String, Player>) raw;
                try {
                    playerRepo.put(entry.getKey(), entry.getValue());
                    players++;
                } catch (Exception e) {
                    log.warn("Ошибка миграции player {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Мигрировано players: {}", players);
        total += players;

        // === Миграция Locations ===
        log.info("Миграция locations...");
        int locations = 0;
        try (var cursor = ignite2.cache("locations").query(new ScanQuery<>())) {
            for (Object raw : cursor) {
                @SuppressWarnings("unchecked")
                Cache.Entry<String, Location> entry = (Cache.Entry<String, Location>) raw;
                try {
                    locationRepo.put(entry.getKey(), entry.getValue());
                    locations++;
                } catch (Exception e) {
                    log.warn("Ошибка миграции location {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Мигрировано locations: {}", locations);
        total += locations;

        // === Миграция Items ===
        log.info("Миграция items...");
        int items = 0;
        try (var cursor = ignite2.cache("items").query(new ScanQuery<>())) {
            for (Object raw : cursor) {
                @SuppressWarnings("unchecked")
                Cache.Entry<String, Item> entry = (Cache.Entry<String, Item>) raw;
                try {
                    itemRepo.put(entry.getKey(), entry.getValue());
                    items++;
                } catch (Exception e) {
                    log.warn("Ошибка миграции item {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Мигрировано items: {}", items);
        total += items;

        // === Миграция Bosses ===
        log.info("Миграция bosses...");
        int bosses = 0;
        try (var cursor = ignite2.cache("bosses").query(new ScanQuery<>())) {
            for (Object raw : cursor) {
                @SuppressWarnings("unchecked")
                Cache.Entry<String, Boss> entry = (Cache.Entry<String, Boss>) raw;
                try {
                    bossRepo.put(entry.getKey(), entry.getValue());
                    bosses++;
                } catch (Exception e) {
                    log.warn("Ошибка миграции boss {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Мигрировано bosses: {}", bosses);
        total += bosses;

        // === Миграция Ideas ===
        log.info("Миграция ideas...");
        int ideas = 0;
        try (var cursor = ignite2.cache("ideas").query(new ScanQuery<>())) {
            for (Object raw : cursor) {
                @SuppressWarnings("unchecked")
                Cache.Entry<Integer, Idea> entry = (Cache.Entry<Integer, Idea>) raw;
                try {
                    ideaRepo.put(entry.getKey(), entry.getValue());
                    ideas++;
                } catch (Exception e) {
                    log.warn("Ошибка миграции idea {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Мигрировано ideas: {}", ideas);
        total += ideas;

        // === Миграция Clans ===
        log.info("Миграция clans...");
        int clans = 0;
        try (var cursor = ignite2.cache("clans").query(new ScanQuery<>())) {
            for (Object raw : cursor) {
                @SuppressWarnings("unchecked")
                Cache.Entry<String, Clan> entry = (Cache.Entry<String, Clan>) raw;
                try {
                    clanRepo.put(entry.getKey(), entry.getValue());
                    clans++;
                } catch (Exception e) {
                    log.warn("Ошибка миграции clan {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        log.info("Мигрировано clans: {}", clans);
        total += clans;

        log.info("=== Миграция завершена. Всего записей: {} ===", total);

        ignite3.close();
        Ignition.stop(IGNITE2_INSTANCE_NAME, true);
    }

    private static Ignite connectIgnite2(String discoveryAddress) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(IGNITE2_INSTANCE_NAME);
        cfg.setClientMode(true);

        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of(discoveryAddress));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

        return Ignition.start(cfg);
    }

    private static IgniteClient connectIgnite3(String clientAddress) {
        return IgniteClient.builder()
                .addresses(clientAddress)
                .build();
    }
}
