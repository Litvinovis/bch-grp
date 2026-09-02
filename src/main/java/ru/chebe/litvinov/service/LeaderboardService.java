package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Таблицы лидеров и сводки: топ игроков, топ активности,
 * недельная доска и список игроков онлайн.
 */
public class LeaderboardService {

	private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

	private final PlayerRepository playerCache;

	/** Задаётся после подключения бота. */
	private net.dv8tion.jda.api.JDA jda;

	public LeaderboardService(PlayerRepository playerCache) {
		this.playerCache = playerCache;
	}

	public void setJda(net.dv8tion.jda.api.JDA jda) {
		this.jda = jda;
	}

	/**
	 * Выводит таблицу лидеров top-10.
	 * Синтаксис: +топ [уровень|деньги|репутация] (по умолчанию — уровень)
	 */
	public void topLeaderboard(MessageReceivedEvent event) {
		String msg = event.getMessage().getContentDisplay();
		String arg = msg.length() > 4 ? msg.substring(4).trim().toLowerCase() : "";

		List<Player> all = playerCache.getAll();
		if (all.isEmpty()) {
			event.getChannel().sendMessage("Нет зарегистрированных игроков.").submit();
			return;
		}

		java.util.Comparator<Player> comparator;
		String title;
		if ("деньги".equals(arg)) {
			comparator = java.util.Comparator.comparingInt(Player::getMoney).reversed();
			title = "Топ по деньгам";
		} else if ("репутация".equals(arg)) {
			comparator = java.util.Comparator.comparingInt(Player::getReputation).reversed();
			title = "Топ по репутации";
		} else {
			comparator = java.util.Comparator.comparingInt(Player::getLevel).reversed();
			title = "Топ по уровню";
		}

		List<Player> sorted = all.stream().sorted(comparator).limit(10).collect(Collectors.toList());
		StringBuilder sb = new StringBuilder(title + "\n");
		for (int i = 0; i < sorted.size(); i++) {
			Player p = sorted.get(i);
			String classLabel = (p.getPlayerClass() != null && !p.getPlayerClass().isEmpty()) ? " [" + p.getPlayerClass() + "]" : "";
			if ("деньги".equals(arg)) {
				sb.append(String.format("%d. %s%s — %d монет\n", i + 1, p.getNickName(), classLabel, p.getMoney()));
			} else if ("репутация".equals(arg)) {
				sb.append(String.format("%d. %s%s — %d репутации\n", i + 1, p.getNickName(), classLabel, p.getReputation()));
			} else {
				sb.append(String.format("%d. %s%s — %d ур.\n", i + 1, p.getNickName(), classLabel, p.getLevel()));
			}
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +топ активность — топ активных игроков */
	public void topActivity(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		all.sort((a, b) -> {
			int scoreA = a.getPvpWins() + a.getMobKills() + (a.getCompletedQuests() != null ? a.getCompletedQuests().size() : 0);
			int scoreB = b.getPvpWins() + b.getMobKills() + (b.getCompletedQuests() != null ? b.getCompletedQuests().size() : 0);
			return scoreB - scoreA;
		});
		var sb = new StringBuilder("🏆 **Топ активности:**\n\n");
		for (int i = 0; i < Math.min(10, all.size()); i++) {
			Player p = all.get(i);
			int score = p.getPvpWins() + p.getMobKills() + (p.getCompletedQuests() != null ? p.getCompletedQuests().size() : 0);
			sb.append(String.format("%d. **%s** — %d очков активности\n", i + 1, p.getNickName(), score));
		}
		event.getChannel().sendMessage(sb.toString()).submit();
	}

	/** +доска — еженедельная доска почёта */
	public void weeklyBoard(MessageReceivedEvent event) {
		List<Player> all = playerCache.getAll();
		Player topPvp = all.stream().max(Comparator.comparingInt(Player::getPvpWins)).orElse(null);
		Player topMobs = all.stream().max(Comparator.comparingInt(Player::getMobKills)).orElse(null);
		Player topMoney = all.stream().max(Comparator.comparingInt(Player::getMoney)).orElse(null);

		net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
			.setTitle("📊 Доска почёта — Эта неделя")
			.setColor(new java.awt.Color(255, 215, 0));
		if (topPvp != null) embed.addField("⚔️ Лучший в PvP", topPvp.getNickName() + " — " + topPvp.getPvpWins() + " побед", false);
		if (topMobs != null) embed.addField("🗡️ Охотник на мобов", topMobs.getNickName() + " — " + topMobs.getMobKills() + " убийств", false);
		if (topMoney != null) embed.addField("💰 Богатейший", topMoney.getNickName() + " — " + topMoney.getMoney() + " монет", false);
		event.getChannel().sendMessageEmbeds(embed.build()).submit();
	}

	/** +онлайн — количество активных игроков */
	public void onlineCommand(MessageReceivedEvent event) {
		long now = System.currentTimeMillis();
		long oneDayAgo = now - 24 * 60 * 60 * 1000L;
		List<Player> all = playerCache.getAll();
		List<Player> online = all.stream()
			.filter(p -> p.getDailyTime() > oneDayAgo)
			.collect(Collectors.toList());
		var sb = new StringBuilder("🟢 **Онлайн за последние 24 часа:** " + online.size() + " игроков\n");
		online.stream().limit(20).forEach(p -> sb.append("• ").append(p.getNickName()).append("\n"));
		if (online.size() > 20) sb.append("...и ещё ").append(online.size() - 20).append(" игроков");
		event.getChannel().sendMessage(sb.toString()).submit();
	}
}
