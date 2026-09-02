package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import static ru.chebe.litvinov.Constants.MIN_LVL_TO_CLAN_CREATE;
import static ru.chebe.litvinov.Constants.MIN_LVL_TO_CLAN_JOIN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Клановые команды игрока: создание, вступление, выход, приём и отклонение
 * заявок, просмотр информации о клане.
 */
public class ClanCommandService {

	private static final Logger log = LoggerFactory.getLogger(ClanCommandService.class);

	private final PlayerRepository playerCache;
	private final ClanManager clanManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;

	public ClanCommandService(PlayerRepository playerCache, ClanManager clanManager,
	                          PlayerLocks playerLocks, AchievementService achievements) {
		this.playerCache = playerCache;
		this.clanManager = clanManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
	}

	/**
	 * Обрабатывает команду создания нового клана. Требует минимального 10-го уровня.
	 *
	 * @param event событие Discord-сообщения с названием клана
	 */
	public void clanRegister(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getLevel() < MIN_LVL_TO_CLAN_CREATE) {
			event.getChannel().sendMessage("Вы не можете создать клан раньше, чем достигните 10 уровня").submit();
			return;
		}
		if (player.getClanName() != null && !player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы уже состоите в клане, сначала покиньте его").submit();
		} else {
			String clanName = event.getMessage().getContentDisplay().substring(11).trim().toLowerCase();
			String result = clanManager.registerClan(clanName, player.getId());
			if (!result.isEmpty()) {
				event.getChannel().sendMessage(result).submit();
			} else {
				player.setClanName(clanName);
				playerCache.put(player.getId(), player);
				event.getChannel().sendMessage("Вы успешно зарегистрировали клан " + clanName).submit();
			}
		}
	}

	/**
	 * Обрабатывает команду выхода игрока из клана.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void clanLeave(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане").submit();
		} else {
			clanManager.leaveClan(player.getClanName(), player.getId());
			event.getChannel().sendMessage("Вы покинули клан " + player.getClanName()).submit();
		}
	}

	/**
	 * Обрабатывает команду подачи заявки на вступление в клан. Требует минимального 3-го уровня.
	 *
	 * @param event событие Discord-сообщения с названием клана
	 */
	public void clanJoin(MessageReceivedEvent event) {
		String clanName = event.getMessage().getContentDisplay().substring(16).trim().toLowerCase();
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getLevel() < MIN_LVL_TO_CLAN_JOIN) {
			event.getChannel().sendMessage("Вы не можете присоединиться к клану раньше, чем достигните " + MIN_LVL_TO_CLAN_JOIN + " уровня").submit();
			return;
		}
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			String result = clanManager.joinClan(clanName, player.getId());
			if (result.isEmpty()) {
				achievements.unlock(player, "клановый_чел");
				playerCache.put(player.getId(), player);
				event.getChannel().sendMessage("Ваша заявка на вступление в клан " + clanName + " подана. Ожидайте подтверждения лидера").submit();
			} else {
				event.getChannel().sendMessage(result).submit();
			}
		} else {
			event.getChannel().sendMessage("Вы уже состоите в клане " + player.getClanName()).submit();
		}
	}

	/**
	 * Обрабатывает команду принятия всех заявок на вступление в клан лидером.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void acceptApply(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане").submit();
		} else {
			String result = clanManager.acceptApply(player.getClanName(), player.getId());
			if (!result.isEmpty()) {
				event.getChannel().sendMessage(result).submit();
			} else {
				event.getChannel().sendMessage("Вы приняли все заявки на вступление в клан").submit();
			}
		}
	}

	/**
	 * Обрабатывает команду отклонения всех заявок на вступление в клан лидером.
	 *
	 * @param event событие Discord-сообщения
	 */
	public void rejectApply(MessageReceivedEvent event) {
		var player = playerCache.get(event.getAuthor().getId());
		if (player.getClanName() == null || player.getClanName().isEmpty()) {
			event.getChannel().sendMessage("Вы не состоите в клане").submit();
		} else {
			String result = clanManager.rejectApply(player.getClanName(), player.getId());
			if (!result.isEmpty()) {
				event.getChannel().sendMessage(result).submit();
			} else {
				event.getChannel().sendMessage("Вы отклонили все заявки на вступление в клан").submit();
			}
		}
	}

	/**
	 * Отправляет информацию о клане по его названию.
	 *
	 * @param event событие Discord-сообщения с названием клана
	 */
	public void clanInfo(MessageReceivedEvent event) {
		String clanName = event.getMessage().getContentDisplay().substring(10).trim().toLowerCase();
		event.getChannel().sendMessage(clanManager.getClanInfo(clanName)).submit();
	}
}
