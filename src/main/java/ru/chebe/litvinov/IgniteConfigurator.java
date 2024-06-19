package ru.chebe.litvinov;

import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import ru.chebe.litvinov.data.*;


import static org.apache.ignite.Ignition.start;

public class IgniteConfigurator {

	public Ignite getIgnite() {
		IgniteConfiguration igniteCfg = new IgniteConfiguration();

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
		CacheConfiguration<Integer, Boss> bossCfg = new CacheConfiguration<>("bosses");
		ideaCfg.setDataRegionName("Default_Region");
		ideaCfg.setCacheMode(CacheMode.PARTITIONED);
		ideaCfg.setBackups(0);

		// Конфигурируем кэш с игроками
		CacheConfiguration<String, Player> playersCfg = new CacheConfiguration<>("players");
		playersCfg.setDataRegionName("Default_Region");
		playersCfg.setCacheMode(CacheMode.PARTITIONED);
		playersCfg.setBackups(0);

		// Конфигурируем кэш с предметами
		CacheConfiguration<String, Items> itemsCfg = new CacheConfiguration<>("items");
		itemsCfg.setDataRegionName("Default_Region");
		itemsCfg.setCacheMode(CacheMode.PARTITIONED);
		itemsCfg.setBackups(0);

		// Конфигурируем кэш с локациями
		CacheConfiguration<String, Location> locCfg = new CacheConfiguration<>("locations");
		locCfg.setDataRegionName("Default_Region");
		locCfg.setCacheMode(CacheMode.PARTITIONED);
		locCfg.setBackups(0);
		igniteCfg.setCacheConfiguration(locCfg, playersCfg, itemsCfg, ideaCfg, bossCfg);

		// Start the node.
		return start(igniteCfg);
	}
}
