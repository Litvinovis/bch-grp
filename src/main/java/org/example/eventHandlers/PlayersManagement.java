package org.example.eventHandlers;

import org.apache.ignite.IgniteCache;
import org.example.data.Player;

public class PlayersManagement {
	IgniteCache<String, Player> cache;

	public PlayersManagement(IgniteCache<String, Player> cache) {
		this.cache = cache;
	}

	public String getPlayerInfo(String nickname) {
		return cache.get(nickname).toString();
	}
}
