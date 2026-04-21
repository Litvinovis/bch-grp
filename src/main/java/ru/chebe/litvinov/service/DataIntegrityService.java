package ru.chebe.litvinov.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.ignite3.PlayerRepository;

import java.util.*;

/**
 * Проверяет и исправляет целостность игровых данных при старте.
 * Обрабатывает: HP > maxHp, переполнение XP (не применённые level-up),
 * дубликаты игроков в локации, игроков в «чужих» локациях.
 */
public class DataIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(DataIntegrityService.class);

    private static final Map<Integer, Integer> XP_MAP = buildXpMap();
    private static final Map<Integer, Integer> HP_MAP = buildHpMap();

    private final PlayerRepository playerRepository;
    private final LocationManager locationManager;

    public DataIntegrityService(PlayerRepository playerRepository, LocationManager locationManager) {
        this.playerRepository = playerRepository;
        this.locationManager = locationManager;
    }

    /** Запускает все проверки и исправления. */
    public void checkAndFix() {
        log.info("DataIntegrityService: начало проверки данных");
        fixPlayerStats();
        fixLocationPopulations();
        log.info("DataIntegrityService: проверка завершена");
    }

    // ---- player stats -----------------------------------------------------------

    private void fixPlayerStats() {
        List<Player> players = playerRepository.getAll();
        for (Player p : players) {
            boolean changed = applyPendingLevelUps(p);
            changed |= capHpToMax(p);
            if (changed) {
                log.info("Исправлены данные игрока {}", p.getId());
                playerRepository.put(p.getId(), p);
            }
        }
    }

    /** Применяет накопленные level-up если exp >= expToNextLvl. */
    boolean applyPendingLevelUps(Player p) {
        boolean changed = false;
        while (p.getExp() >= p.getExpToNextLvl() && p.getLevel() < 100) {
            int leftover = p.getExp() - p.getExpToNextLvl();
            p.setLevel(p.getLevel() + 1);
            p.setExpToNextLvl(XP_MAP.getOrDefault(p.getLevel(), p.getExpToNextLvl()));
            p.setMaxHp(HP_MAP.getOrDefault(p.getLevel(), p.getMaxHp()));
            p.setHp(p.getMaxHp());
            p.setExp(leftover);
            changed = true;
            log.warn("Игрок {}: применён level-up до уровня {}", p.getId(), p.getLevel());
        }
        return changed;
    }

    /** Ограничивает HP максимальным значением если оно превышено. */
    boolean capHpToMax(Player p) {
        if (p.getHp() > p.getMaxHp()) {
            log.warn("Игрок {}: HP {} > maxHp {}, исправлено", p.getId(), p.getHp(), p.getMaxHp());
            p.setHp(p.getMaxHp());
            return true;
        }
        return false;
    }

    // ---- location populations ---------------------------------------------------

    private void fixLocationPopulations() {
        Map<String, String> declaredLocations = buildDeclaredLocationsMap();

        for (String locName : locationManager.getLocationList()) {
            Location loc = locationManager.getLocation(locName);
            if (loc == null) continue;

            List<String> ids = loc.getPopulationById();
            Set<String> validIds = new LinkedHashSet<>();
            List<String> removed = new ArrayList<>();

            for (String id : ids) {
                if (validIds.contains(id)) {
                    log.warn("Локация {}: дубликат игрока {}, удаляем", locName, id);
                    removed.add(id);
                } else if (!locName.equals(declaredLocations.get(id))) {
                    log.warn("Локация {}: игрок {} объявлен в '{}', удаляем из этой локации",
                            locName, id, declaredLocations.get(id));
                    removed.add(id);
                } else {
                    validIds.add(id);
                }
            }

            if (!removed.isEmpty()) {
                ids.clear();
                ids.addAll(validIds);
                List<String> names = loc.getPopulationByName();
                for (String id : removed) {
                    Player p = playerRepository.get(id);
                    if (p != null) names.remove(p.getNickName());
                }
                locationManager.saveLocation(loc);
            }
        }
    }

    private Map<String, String> buildDeclaredLocationsMap() {
        Map<String, String> map = new HashMap<>();
        for (Player p : playerRepository.getAll()) {
            if (p.getLocation() != null) {
                map.put(p.getId(), p.getLocation());
            }
        }
        return map;
    }

    // ---- level tables (same formula as PlayersManager) --------------------------

    private static Map<Integer, Integer> buildXpMap() {
        Map<Integer, Integer> map = new HashMap<>();
        int xp = 100;
        for (int i = 2; i <= 100; i++) {
            map.put(i, xp);
            xp += 100;
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
