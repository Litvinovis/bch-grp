package ru.chebe.litvinov;

import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.chebe.litvinov.data.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;

import static org.apache.ignite.Ignition.start;

public class IgniteConfigurator {
	private static final Logger logger = LoggerFactory.getLogger(IgniteConfigurator.class);

	public Ignite getIgnite() {
		IgniteConfiguration igniteCfg = new IgniteConfiguration();

		// Load configuration from application.properties
		Properties props = new Properties();
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
			if (input != null) {
				props.load(input);
				logger.info("Загружены настройки из application.properties");
			} else {
				logger.warn("Файл application.properties не найден, используются настройки по умолчанию");
			}
		} catch (IOException e) {
			logger.error("Ошибка при загрузке application.properties: " + e.getMessage());
			e.printStackTrace();
		}

		// Configure discovery SPI with custom port for standalone mode
		TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
		
		// Get port configuration from properties
		int port = Integer.parseInt(props.getProperty("ignite.port", "47500"));
		int portRange = Integer.parseInt(props.getProperty("ignite.port.range", "100"));
		
		discoverySpi.setLocalPort(port);
		discoverySpi.setLocalPortRange(portRange);
		
		// Use VM IP finder for standalone mode
		TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
		ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + port + ".." + (port + portRange)));
		discoverySpi.setIpFinder(ipFinder);
		
		igniteCfg.setDiscoverySpi(discoverySpi);
		
		// Log port configuration
		logger.info("Настройка портов Ignite: базовый порт = {}, диапазон = {}", port, portRange);

		// Set Ignite to work in standalone mode
		igniteCfg.setClientMode(false);
		igniteCfg.setIgniteInstanceName("bchgrp-standalone");

		// Конфигурация датарегиона
		DataStorageConfiguration storageCfg = new DataStorageConfiguration();
		DataRegionConfiguration defaultRegion = new DataRegionConfiguration();
		defaultRegion.setName("Default_Region");
		defaultRegion.setPersistenceEnabled(true);
		defaultRegion.setInitialSize(50L * 1024 * 1024);
		defaultRegion.setMaxSize(200L * 1024 * 1024);
		storageCfg.setDefaultDataRegionConfiguration(defaultRegion);
		igniteCfg.setDataStorageConfiguration(storageCfg);

		// Конфигурируем кэш с идеями
		CacheConfiguration<Integer, Idea> ideaCfg = new CacheConfiguration<>("ideas");
		ideaCfg.setDataRegionName("Default_Region");
		ideaCfg.setCacheMode(CacheMode.PARTITIONED);
		ideaCfg.setBackups(0);

		// Конфигурируем кэш с боссами
		CacheConfiguration<String, Boss> bossCfg = new CacheConfiguration<>("bosses");
		bossCfg.setDataRegionName("Default_Region");
		bossCfg.setCacheMode(CacheMode.PARTITIONED);
		bossCfg.setBackups(0);

		// Конфигурируем кэш с игроками
		CacheConfiguration<String, Player> playersCfg = new CacheConfiguration<>("players");
		playersCfg.setDataRegionName("Default_Region");
		playersCfg.setCacheMode(CacheMode.PARTITIONED);
		playersCfg.setBackups(0);

		// Конфигурируем кэш с предметами
		CacheConfiguration<String, Item> itemsCfg = new CacheConfiguration<>("items");
		itemsCfg.setDataRegionName("Default_Region");
		itemsCfg.setCacheMode(CacheMode.PARTITIONED);
		itemsCfg.setBackups(0);

		// Конфигурируем кэш с локациями
		CacheConfiguration<String, Location> locCfg = new CacheConfiguration<>("locations");
		locCfg.setDataRegionName("Default_Region");
		locCfg.setCacheMode(CacheMode.PARTITIONED);
		locCfg.setBackups(0);

		// Конфигурируем кэш с кланами
		CacheConfiguration<String, Clan> clanCfg = new CacheConfiguration<>("clans");
		clanCfg.setDataRegionName("Default_Region");
		clanCfg.setCacheMode(CacheMode.PARTITIONED);
		clanCfg.setBackups(0);

		igniteCfg.setCacheConfiguration(locCfg, playersCfg, itemsCfg, ideaCfg, bossCfg, clanCfg);

		logger.info("Запуск узла Ignite с конфигурацией: порт={}, диапазон={}, имя_экземпляра={}", 
		           port, portRange, igniteCfg.getIgniteInstanceName());
		
		// Start the node.
		return start(igniteCfg);
	}
}