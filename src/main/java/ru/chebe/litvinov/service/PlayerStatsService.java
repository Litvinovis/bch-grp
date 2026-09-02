package ru.chebe.litvinov.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.GameBalance;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.PlayerProgressTables;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Характеристики игрока: здоровье, деньги, репутация, опыт с повышением уровня,
 * броня, удача, сила и обработка смерти.
 * Все изменения идут под блокировкой игрока и сохраняются сразу.
 */
public class PlayerStatsService {

	private static final Logger log = LoggerFactory.getLogger(PlayerStatsService.class);

	private static final Map<Integer, Integer> xpMap = PlayerProgressTables.XP_MAP;
	private static final Map<Integer, Integer> hpMap = PlayerProgressTables.HP_MAP;

	private final PlayerRepository playerCache;
	private final LocationManager locationManager;
	private final PlayerLocks playerLocks;
	private final AchievementService achievements;

	public PlayerStatsService(PlayerRepository playerCache, LocationManager locationManager,
	                          PlayerLocks playerLocks, AchievementService achievements) {
		this.playerCache = playerCache;
		this.locationManager = locationManager;
		this.playerLocks = playerLocks;
		this.achievements = achievements;
	}

	/**
	 * Возвращает количество опыта, необходимое для достижения следующего уровня.
	 *
	 * @param player игрок
	 * @return количество опыта до следующего уровня
	 */
	public int getXp(Player player) {
		return xpMap.get(player.getLevel() + 1);
	}

	/**
	 * Возвращает максимальное значение HP для текущего уровня игрока.
	 *
	 * @param player игрок
	 * @return максимальное значение HP
	 */
	public int getMaxHp(Player player) {
		return hpMap.get(player.getLevel());
	}

	/**
	 * Устанавливает точное значение HP игроку.
	 *
	 * @param id идентификатор игрока
	 * @param hp новое значение HP
	 */
	public void changeHp(String id, int hp) {
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return;
			player.setHp(Math.min(hp, player.getMaxHp()));
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Изменяет HP игрока на указанное значение.
	 *
	 * @param id       идентификатор игрока
	 * @param hp       величина изменения HP
	 * @param increase true — увеличить HP (не превышая максимум), false — уменьшить
	 * @return актуальное значение HP после изменения
	 */
	public int changeHp(String id, int hp, boolean increase) {
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return 0;
			if (increase) {
				int newHp = player.getHp() + hp;
				player.setHp(Math.min(newHp, player.getMaxHp()));
			} else {
				player.setHp(player.getHp() - hp);
			}
			playerCache.put(id, player);
			return player.getHp();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Изменяет количество денег игрока.
	 *
	 * @param id       идентификатор игрока
	 * @param money    сумма изменения
	 * @param increase true — добавить деньги, false — вычесть
	 * @return актуальное количество денег после изменения
	 */
	public int changeMoney(String id, int money, boolean increase) {
		return mutate(id, Player::getMoney, Player::setMoney, money, increase);
	}

	/**
	 * Изменяет репутацию игрока.
	 *
	 * @param id         идентификатор игрока
	 * @param reputation величина изменения репутации
	 * @param increase   true — увеличить, false — уменьшить
	 * @return актуальное значение репутации после изменения
	 */
	public int changeReputation(String id, int reputation, boolean increase) {
		return mutate(id, Player::getReputation, Player::setReputation, reputation, increase);
	}

	/**
	 * Начисляет опыт игроку. При наборе достаточного количества — повышает уровень и восстанавливает HP.
	 *
	 * @param id идентификатор игрока
	 * @param xp количество начисляемого опыта
	 */
	public void changeXp(String id, int xp) {
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			var player = playerCache.get(id);
			if (player == null) return;
			
			int totalXp = player.getExp() + xp;
			int expToNext = player.getExpToNextLvl();
			
			// Повышаем уровень, пока опыт превышает требуемый
			while (totalXp >= expToNext && player.getLevel() < 100) {
				totalXp -= expToNext;
				player.setLevel(player.getLevel() + 1);
				player.setExpToNextLvl(xpMap.get(player.getLevel()));
				player.setMaxHp(getMaxHp(player));  // Обновляем максимальное HP
				player.setHp(player.getMaxHp());     // Восстанавливаем HP при повышении уровня
				expToNext = player.getExpToNextLvl();
				if (player.getLevel() == 10) achievements.unlock(player, "10_уровень");
				if (player.getLevel() >= 50) achievements.unlock(player, "легенда");
			}
			
			player.setExp(totalXp);
			playerCache.put(id, player);
		} finally {
			lock.unlock();
		}
	}

	public int changeArmor(String id, int armor, boolean increase) {
		return mutate(id, Player::getArmor, Player::setArmor, armor, increase);
	}

	/**
	 * Изменяет удачу игрока.
	 *
	 * @param id       идентификатор игрока
	 * @param luck     величина изменения удачи
	 * @param increase true — увеличить, false — уменьшить
	 * @return актуальное значение удачи после изменения
	 */
	public int changeLuck(String id, int luck, boolean increase) {
		return mutate(id, Player::getLuck, Player::setLuck, luck, increase);
	}

	/**
	 * Изменяет силу игрока.
	 *
	 * @param id       идентификатор игрока
	 * @param strength величина изменения силы
	 * @param increase true — увеличить, false — уменьшить
	 * @return актуальное значение силы после изменения
	 */
	public int changeStrength(String id, int strength, boolean increase) {
		return mutate(id, Player::getStrength, Player::setStrength, strength, increase);
	}

	private int mutate(String id, ToIntFunction<Player> get, ObjIntConsumer<Player> set, int delta, boolean increase) {
		ReentrantLock lock = playerLocks.get(id);
		lock.lock();
		try {
			Player player = playerCache.get(id);
			if (player == null) return 0;
			set.accept(player, increase ? get.applyAsInt(player) + delta : get.applyAsInt(player) - delta);
			playerCache.put(id, player);
			return get.applyAsInt(player);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Обрабатывает смерть игрока: списывает 10% денег, восстанавливает HP и перемещает на Респаун.
	 *
	 * @param dead игрок, который погиб
	 */
	public void deathOfPlayer(Player dead) {
		int level = dead.getLevel();
		double moneyPenaltyPct = level <= 5  ? GameBalance.DEATH_MONEY_PENALTY_LOW
				: level <= 15 ? GameBalance.DEATH_MONEY_PENALTY_MID
				: level <= 30 ? GameBalance.DEATH_MONEY_PENALTY_HIGH
				:               GameBalance.DEATH_MONEY_PENALTY_MAX;
		dead.setMoney((int) (dead.getMoney() * (1 - moneyPenaltyPct)));

		// Lose 5% of XP earned within current level (never goes below 0 in the level)
		int xpLoss = (int) (dead.getExp() * GameBalance.DEATH_XP_LOSS_PCT);
		dead.setExp(Math.max(0, dead.getExp() - xpLoss));

		dead.setHp(dead.getMaxHp());
		// movePlayerInPopulation reads dead.getLocation() to find the current location —
		// must be called before changing it, otherwise the player is never removed from the old location
		locationManager.movePlayerInPopulation(dead, "респаун");
		dead.setLocation("респаун");
		playerCache.put(dead.getId(), dead);
	}
}
