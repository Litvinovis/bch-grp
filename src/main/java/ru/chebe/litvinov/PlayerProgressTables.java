package ru.chebe.litvinov;

import java.util.HashMap;
import java.util.Map;

/**
 * Единый источник таблиц прогрессии персонажа.
 * XP formula: i*i*80 (quadratic). HP formula: 100 + (level-1)*10 (linear).
 */
public final class PlayerProgressTables {

	public static final Map<Integer, Integer> XP_MAP = buildXpMap();
	public static final Map<Integer, Integer> HP_MAP = buildHpMap();

	private PlayerProgressTables() {}

	private static Map<Integer, Integer> buildXpMap() {
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 2; i <= 100; i++) {
			map.put(i, i * i * 80);
		}
		return map;
	}

	private static Map<Integer, Integer> buildHpMap() {
		Map<Integer, Integer> map = new HashMap<>();
		int hp = 100;
		for (int i = 1; i <= 100; i++) {
			map.put(i, hp);
			hp += 10;
		}
		return map;
	}
}
