package org.example;

import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.example.data.Items;
import org.example.data.Location;
import org.example.data.Player;


import static org.apache.ignite.Ignition.start;

public class IgniteConfigurator {

	public Ignite getIgnite() {
		IgniteConfiguration igniteCfg = new IgniteConfiguration();

		// Конфигурация датарегиона
		DataStorageConfiguration storageCfg = new DataStorageConfiguration();
		DataRegionConfiguration defaultRegion = new DataRegionConfiguration();
		defaultRegion.setName("Default_Region");
		defaultRegion.setPersistenceEnabled(true);
		defaultRegion.setInitialSize(100L * 1024 * 1024);
		storageCfg.setDefaultDataRegionConfiguration(defaultRegion);
		igniteCfg.setDataStorageConfiguration(storageCfg);

		// Конфигурируем кэш с играками
		CacheConfiguration<String, Player> playersCfg = new CacheConfiguration<>("players");
		playersCfg.setDataRegionName("Default_Region");
		playersCfg.setCacheMode(CacheMode.PARTITIONED);

		// Конфигурируем кэш с играками
		CacheConfiguration<String, Items> itemsCfg = new CacheConfiguration<>("items");
		itemsCfg.setDataRegionName("Default_Region");
		itemsCfg.setCacheMode(CacheMode.PARTITIONED);

		// Конфигурируем кэш с играками
		CacheConfiguration<String, Location> locCfg = new CacheConfiguration<>("locations");
		locCfg.setDataRegionName("Default_Region");
		locCfg.setCacheMode(CacheMode.PARTITIONED);
		igniteCfg.setCacheConfiguration(locCfg, playersCfg);

		// Start the node.
		return start(igniteCfg);
	}
}
