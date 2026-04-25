package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

/**
 * Игровой предмет, который может находиться в инвентаре игрока.
 * Предметы бывают постоянными (влияют на характеристики при ношении) и активируемыми (применяются вручную).
 */
@Getter
@Setter
@Builder
public class Item {
	private String name;
	private String description;
	private int price;
	private int luck;
	private int strength;
	private int health;
	private int armor;
	private int reputation;
	private int xpGeneration;
	private int quantity;
	private long expireTime;
	private boolean action;

	@Override
	public String toString() {
		String typeLabel = action ? "🔥 Активируемое" : "📦 Постоянное";
		var sb = new StringBuilder();
		sb.append("**").append(name).append("** [").append(typeLabel).append("]\n");
		sb.append("💰 Цена: **").append(price).append("** монет  ·  Продажа: **").append(price / 2).append("** монет\n");

		var stats = new StringBuilder();
		if (health != 0)       stats.append("❤️ HP: ").append(health > 0 ? "+" : "").append(health).append("  ");
		if (strength != 0)     stats.append("⚔️ Сила: ").append(strength > 0 ? "+" : "").append(strength).append("  ");
		if (armor != 0)        stats.append("🛡️ Броня: ").append(armor > 0 ? "+" : "").append(armor).append("  ");
		if (luck != 0)         stats.append("🍀 Удача: ").append(luck > 0 ? "+" : "").append(luck).append("  ");
		if (reputation != 0)   stats.append("⭐ Репутация: ").append(reputation > 0 ? "+" : "").append(reputation).append("  ");
		if (xpGeneration != 0) stats.append("✨ XP: ").append(xpGeneration > 0 ? "+" : "").append(xpGeneration).append("  ");
		if (stats.length() > 0) sb.append(stats.toString().stripTrailing()).append("\n");

		if (expireTime != 0) {
			long minsLeft = TimeUnit.MILLISECONDS.toMinutes(expireTime - System.currentTimeMillis());
			sb.append("⏳ Исчезнет через **").append(minsLeft).append("** мин\n");
		}

		sb.append("*").append(description).append("*");
		return sb.toString();
	}
}
