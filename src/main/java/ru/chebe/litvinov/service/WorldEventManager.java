package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.NpcBot;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер мировых событий.
 * Управляет периодическими событиями: нашествиями, мировыми боссами, турнирами и т.п.
 */
public class WorldEventManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorldEventManager.class);

    private final PlayerRepository playerRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    // Статический флаг экономического кризиса
    public static volatile boolean economicCrisisActive = false;
    private static volatile long crisisEndsAt = 0;

    // Мировой босс
    private static volatile int worldBossHp = 0;
    private static volatile String worldBossLocation = "";

    // Boss roster
    private record WorldBossData(String name, int hp, int str, String loot) {}
    private static final List<WorldBossData> BOSS_ROSTER = List.of(
        new WorldBossData("Тёмный Страж",     5000, 30, "щит чегоба"),
        new WorldBossData("Великий Дракон",   7000, 40, "корона дарха"),
        new WorldBossData("Повелитель Хаоса", 6000, 35, "очко бога"),
        new WorldBossData("Ледяной Колосс",   8000, 25, "шарики лаба"),
        new WorldBossData("Кровавый Берсерк", 4500, 50, "месть гордона")
    );
    private static volatile WorldBossData currentWorldBossData = null;

    // Invasion waves
    private record WaveData(String name, int hp, int str, int reward) {}
    private static final List<WaveData> INVASION_WAVES = List.of(
        new WaveData("Разведчик Хаоса",      80,  8,  20),
        new WaveData("Воин Хаоса",          120, 12,  30),
        new WaveData("Берсерк Хаоса",       150, 16,  40),
        new WaveData("Страж Хаоса",         180, 14,  50),
        new WaveData("Убийца Хаоса",        160, 20,  60),
        new WaveData("Маг Хаоса",           140, 18,  70),
        new WaveData("Вожак Хаоса",         220, 22,  80),
        new WaveData("Паладин Хаоса",       200, 18,  90),
        new WaveData("Лорд Хаоса",          280, 28, 100),
        new WaveData("Предводитель Хаоса",  350, 35, 150)
    );

    // Нашествие давало до ~950 монет и ~1250 XP за вызов и повторялось без ограничений
    private final Cooldowns invasionCooldown = new Cooldowns(24 * 60 * 60 * 1000L);

    private Set<String> allowedChannelIds;
    private net.dv8tion.jda.api.JDA jda;

    // Мировой босс переживает рестарт: состояние в world_events (WorldEventRepository)
    static final long WORLD_BOSS_PERIOD_MS = 72L * 60 * 60 * 1000;
    static final java.time.ZoneId GAME_ZONE = java.time.ZoneId.of("Europe/Moscow");
    // Появляется только днём по Москве: раньше таймер шёл от запуска бота, и босс выходил в 02:13
    static final int SPAWN_FROM_HOUR = 10;
    static final int SPAWN_TO_HOUR = 22;
    private final ru.chebe.litvinov.repository.WorldEventRepository worldEvents;
    private static volatile long worldBossRowId = -1;
    /** Без базы (тесты) время последнего спавна держим в памяти. */
    private volatile long lastSpawnInMemory = 0;

    public WorldEventManager(PlayerRepository playerRepository) {
        this(playerRepository, null);
    }

    public WorldEventManager(PlayerRepository playerRepository, ru.chebe.litvinov.repository.WorldEventRepository worldEvents) {
        this.playerRepository = playerRepository;
        this.worldEvents = worldEvents;
        restoreWorldBoss();
        scheduleEvents();
    }

    /** Поднять живого босса из базы после рестарта. */
    private void restoreWorldBoss() {
        if (worldEvents == null) return;
        worldEvents.activeBoss(System.currentTimeMillis()).ifPresent(b -> {
            currentWorldBossData = BOSS_ROSTER.stream().filter(d -> d.name().equals(b.name())).findFirst().orElse(BOSS_ROSTER.get(0));
            worldBossLocation = b.location();
            worldBossHp = b.hp();
            worldBossRowId = b.id();
            log.info("Мировой босс {} восстановлен: {} HP в {}", b.name(), b.hp(), b.location());
        });
    }

    /** Где мировой босс, если он жив, иначе null — для отметки на карте. */
    public static String activeBossLocation() {
        return worldBossHp > 0 && !worldBossLocation.isEmpty() ? worldBossLocation : null;
    }

    public void setJda(net.dv8tion.jda.api.JDA jda) {
        this.jda = jda;
    }

    public void setAllowedChannelIds(Set<String> allowedChannelIds) {
        this.allowedChannelIds = allowedChannelIds;
    }

    private void scheduleEvents() {
        // Проверка экономического кризиса каждый день
        scheduler.scheduleAtFixedRate(this::checkDailyCrisis, 1, 24, TimeUnit.HOURS);
        // Раз в 72 ч, но проверяем каждые 10 минут: срок считается от прошлого спавна в базе,
        // а не от запуска бота, и босс ждёт дневного окна
        scheduler.scheduleAtFixedRate(this::checkWorldBoss, 1, 10, TimeUnit.MINUTES);
    }

    private void checkDailyCrisis() {
        // 10% шанс экономического кризиса каждый день
        if (!economicCrisisActive && random.nextInt(100) < 10) {
            economicCrisisActive = true;
            crisisEndsAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
            broadcastMessage("💸 **ЭКОНОМИЧЕСКИЙ КРИЗИС!** Цены в магазине удвоены на 24 часа!");
        } else if (economicCrisisActive && System.currentTimeMillis() > crisisEndsAt) {
            economicCrisisActive = false;
            broadcastMessage("✅ Экономический кризис завершился. Цены вернулись в норму.");
        }
    }

    /** Тик: снять просроченного босса и выпустить нового, если пора и сейчас день по Москве. */
    void checkWorldBoss() {
        try {
            checkWorldBoss(System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Ошибка проверки мирового босса", e);
        }
    }

    synchronized void checkWorldBoss(long now) {
        // До подключения к Discord анонс не уйдёт — босс появился бы молча
        if (worldEvents != null && jda == null) return;
        long lastSpawn = worldEvents != null ? worldEvents.lastBossSpawnAt() : lastSpawnInMemory;
        if (worldBossHp > 0 && now - lastSpawn >= WORLD_BOSS_PERIOD_MS && !raidInProgress()) {
            // Не добили за 72 ч — уходит, место следующему
            worldBossHp = 0;
            broadcastMessage("🌫 Мировой босс **" + (currentWorldBossData != null ? currentWorldBossData.name() : "") + "** ушёл непобеждённым.");
        }
        if (worldBossHp > 0 || now - lastSpawn < WORLD_BOSS_PERIOD_MS) return;
        int hour = java.time.Instant.ofEpochMilli(now).atZone(GAME_ZONE).getHour();
        if (hour < SPAWN_FROM_HOUR || hour >= SPAWN_TO_HOUR) return;
        spawnWorldBoss(now);
    }

    private void spawnWorldBoss(long now) {
        List<String> locs = LocationManager.locationList;
        worldBossLocation = locs.isEmpty() ? "мейн" : locs.get(random.nextInt(locs.size()));
        currentWorldBossData = BOSS_ROSTER.get(random.nextInt(BOSS_ROSTER.size()));
        worldBossHp = currentWorldBossData.hp();
        lastSpawnInMemory = now;
        if (worldEvents != null) {
            worldBossRowId = worldEvents.spawnBoss(currentWorldBossData.name(), worldBossLocation, worldBossHp,
                now, now + WORLD_BOSS_PERIOD_MS);
        }
        broadcastMessage("🌍 **МИРОВОЙ БОСС — " + currentWorldBossData.name() + "** появился в **" + worldBossLocation +
            "**! [❤️ HP: " + currentWorldBossData.hp() + " | ⚔️ Сила: " + currentWorldBossData.str() + "]\nАтакуйте командой **+мировой босс**!");
    }

    private void broadcastMessage(String msg) {
        if (jda == null || allowedChannelIds == null) return;
        for (String channelId : allowedChannelIds) {
            try {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch = jda.getTextChannelById(channelId);
                if (ch == null) {
                    log.debug("Канал {} не найден — анонс мирового события не отправлен", channelId);
                    continue;
                }
                ch.sendMessage(msg).queue();
            } catch (Exception e) {
                log.debug("Не удалось отправить анонс мирового события в канал {}: {}", channelId, e.getMessage());
            }
        }
    }

    // ── Рейд на мирового босса ──
    // Бой идёт сам до конца: каждый раунд все участники бьют вместе, босс — один удар по
    // случайному участнику (ограничение ударов в единицу времени). Раньше одна команда давала
    // один удар, и на босса в 6000 HP нужно было ~90 команд.
    static final long ROUND_MS = 10_000;
    /** Сводка в канал раз в столько раундов (и на каждом выбывании) — иначе сообщение каждые 10 с. */
    static final int ROUNDS_PER_REPORT = 3;
    static final int KILL_XP_POOL = 1000;
    static final int KILL_MONEY_POOL = 400;

    private final java.util.Map<String, Integer> raidDamage = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> raidFighters = new java.util.LinkedHashSet<>();
    private net.dv8tion.jda.api.entities.channel.middleman.MessageChannel raidChannel;
    private java.util.concurrent.ScheduledFuture<?> raidTask;
    private int raidRound;
    Random raidRandom = new Random();
    private PlayerLocks playerLocks;

    public void setPlayerLocks(PlayerLocks playerLocks) {
        this.playerLocks = playerLocks;
    }

    boolean raidInProgress() {
        return raidTask != null || !raidFighters.isEmpty();
    }

    /** +мировой босс — вступить в бой (или начать его). */
    public synchronized void worldBossAttack(MessageReceivedEvent event) {
        if (worldBossHp <= 0) {
            event.getChannel().sendMessage("🌍 Мировой босс сейчас не активен. Следите за объявлениями!").submit();
            return;
        }
        if (currentWorldBossData == null) {
            currentWorldBossData = BOSS_ROSTER.get(0);
        }
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player == null) return;
        if (player.getHp() <= 0) {
            // С HP ≤ 0 игрок бил босса бесконечно, уходя в отрицательное здоровье
            event.getChannel().sendMessage("💀 У тебя нет здоровья для боя. Восстановись и возвращайся!").submit();
            return;
        }
        if (!player.getLocation().equals(worldBossLocation)) {
            event.getChannel().sendMessage("Мировой босс **" + currentWorldBossData.name() + "** находится в **" + worldBossLocation + "**. Переместись туда!").submit();
            return;
        }
        if (raidFighters.contains(id)) {
            event.getChannel().sendMessage("⚔️ Ты уже в бою с **" + currentWorldBossData.name() + "** — он идёт сам, жди сводку.").submit();
            return;
        }
        raidFighters.add(id);
        raidDamage.putIfAbsent(id, 0);
        if (raidTask == null) {
            raidChannel = event.getChannel();
            raidRound = 0;
            raidTask = scheduler.scheduleAtFixedRate(this::raidTick, ROUND_MS, ROUND_MS, TimeUnit.MILLISECONDS);
            event.getChannel().sendMessage("⚔️ **" + player.getNickName() + "** вступил в бой с **" + currentWorldBossData.name()
                + "** [❤️ " + worldBossHp + "]! Бой идёт сам до конца, раунд каждые " + (ROUND_MS / 1000) + " с.\n"
                + "Присоединяйтесь: **+мировой босс** в **" + worldBossLocation + "** — бьём вместе.").submit();
            broadcastMessage("⚔️ Начался бой с мировым боссом **" + currentWorldBossData.name() + "** в **" + worldBossLocation
                + "**! Присоединяйтесь: **+мировой босс**.");
        } else {
            sendRaid("➕ **" + player.getNickName() + "** присоединился к бою! Бойцов: " + raidFighters.size());
            if (raidChannel != null && !raidChannel.getId().equals(event.getChannel().getId())) {
                event.getChannel().sendMessage("➕ Ты в бою с **" + currentWorldBossData.name() + "**, сводки — в канале, где он начался.").submit();
            }
        }
    }

    private void raidTick() {
        try {
            playRound();
        } catch (Exception e) {
            log.error("Ошибка раунда боя с мировым боссом", e);
        }
    }

    /** Один раунд: все бьют, босс отвечает одним ударом по случайному участнику. */
    synchronized void playRound() {
        if (currentWorldBossData == null || worldBossHp <= 0) {
            endRaid();
            return;
        }
        raidRound++;
        StringBuilder events = new StringBuilder();

        int roundDamage = 0;
        for (String id : List.copyOf(raidFighters)) {
            Player p = playerRepository.get(id);
            if (p == null || p.getHp() <= 0 || !worldBossLocation.equals(p.getLocation())) {
                raidFighters.remove(id);
                events.append("🚶 ").append(p != null ? p.getNickName() : id).append(" покинул бой\n");
                continue;
            }
            int dmg = Math.max(1, p.getStrength() - 5);
            roundDamage += dmg;
            raidDamage.merge(id, dmg, Integer::sum);
            worldBossHp = Math.max(0, worldBossHp - dmg);
            if (worldBossHp <= 0) break;
        }
        if (worldEvents != null && worldBossRowId > 0) {
            worldEvents.updateBossHp(worldBossRowId, currentWorldBossData.name(), worldBossLocation, worldBossHp);
        }
        if (worldBossHp <= 0) {
            rewardRaid();
            return;
        }
        if (raidFighters.isEmpty()) {
            sendRaid(events + "🏳 Все бойцы выбыли. **" + currentWorldBossData.name() + "** остался с ❤️ " + worldBossHp + " HP.");
            endRaid();
            return;
        }

        // Босс бьёт один раз за раунд — по случайному участнику
        List<String> fighters = List.copyOf(raidFighters);
        String targetId = fighters.get(raidRandom.nextInt(fighters.size()));
        String knockout = hitFighter(targetId);
        if (knockout != null) events.append(knockout);

        if (raidFighters.isEmpty()) {
            sendRaid(events + "🏳 Все бойцы выбыли. **" + currentWorldBossData.name() + "** остался с ❤️ " + worldBossHp + " HP.");
            endRaid();
            return;
        }
        if (events.length() > 0 || raidRound % ROUNDS_PER_REPORT == 0) {
            sendRaid(events + "⚔️ Раунд " + raidRound + ": бойцы нанесли **" + roundDamage + "**, у **" + currentWorldBossData.name()
                + "** ❤️ " + worldBossHp + ". Бойцов: " + raidFighters.size());
        }
    }

    /** Удар босса по игроку; при HP ≤ 0 игрок выбывает. Возвращает строку о выбывании или null. */
    private String hitFighter(String targetId) {
        java.util.concurrent.locks.ReentrantLock lock = playerLocks != null ? playerLocks.get(targetId) : null;
        if (lock != null) lock.lock();
        try {
            Player target = playerRepository.get(targetId);
            if (target == null) {
                raidFighters.remove(targetId);
                return null;
            }
            int dmg = Math.max(1, currentWorldBossData.str() - target.getArmor());
            target.setHp(Math.max(0, target.getHp() - dmg));
            playerRepository.put(targetId, target);
            if (target.getHp() <= 0) {
                raidFighters.remove(targetId);
                return "💀 **" + target.getNickName() + "** получил " + dmg + " урона и выбыл из боя\n";
            }
            return null;
        } finally {
            if (lock != null) lock.unlock();
        }
    }

    /** Босс повержен: опыт и монеты — по доле урона, предмет — лучшему по урону. */
    private void rewardRaid() {
        int total = Math.max(1, raidDamage.values().stream().mapToInt(Integer::intValue).sum());
        String topId = raidDamage.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
        StringBuilder msg = new StringBuilder("💀 **" + currentWorldBossData.name() + "** повержен за " + raidRound + " раундов!\n");
        for (var e : raidDamage.entrySet()) {
            double share = e.getValue() / (double) total;
            int xp = Math.max(50, (int) Math.round(KILL_XP_POOL * share));
            int money = Math.max(20, (int) Math.round(KILL_MONEY_POOL * share));
            java.util.concurrent.locks.ReentrantLock lock = playerLocks != null ? playerLocks.get(e.getKey()) : null;
            if (lock != null) lock.lock();
            try {
                Player p = playerRepository.get(e.getKey());
                if (p == null) continue;
                p.setExp(p.getExp() + xp);
                p.setMoney(p.getMoney() + money);
                if (e.getKey().equals(topId)) p.getInventory().merge(currentWorldBossData.loot(), 1, Integer::sum);
                playerRepository.put(e.getKey(), p);
                msg.append("• **").append(p.getNickName()).append("** — урон ").append(e.getValue())
                   .append(": +").append(xp).append(" XP, +").append(money).append(" монет")
                   .append(e.getKey().equals(topId) ? ", предмет **" + currentWorldBossData.loot() + "**" : "").append("\n");
            } finally {
                if (lock != null) lock.unlock();
            }
        }
        sendRaid(msg.toString());
        broadcastMessage("🎉 Мировой босс **" + currentWorldBossData.name() + "** повержен! Бойцов: " + raidDamage.size());
        currentWorldBossData = null;
        endRaid();
    }

    private void endRaid() {
        if (raidTask != null) raidTask.cancel(false);
        raidTask = null;
        raidFighters.clear();
        raidDamage.clear();
        raidChannel = null;
    }

    private void sendRaid(String msg) {
        if (raidChannel != null) raidChannel.sendMessage(msg).submit();
    }

    /** +нашествие — волновой бой с мобами */
    public void invasionStatus(MessageReceivedEvent event) {
        startInvasion(event);
    }

    public void startInvasion(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);

        if (player == null) {
            event.getChannel().sendMessage("🌊 **Нашествие**: ты не зарегистрирован в игре.").submit();
            return;
        }

        if (!"модерская".equals(player.getLocation())) {
            event.getChannel().sendMessage("🌊 **Нашествие** проходит в **модерской**! Переместись туда командой **+идти модерская**.").submit();
            return;
        }
        if (player.getHp() <= 0) {
            // При HP ≤ 0 цикл боя не выполнялся, и каждая волна засчитывалась как пройденная
            event.getChannel().sendMessage("💀 У тебя нет здоровья для боя. Восстановись и возвращайся!").submit();
            return;
        }
        long wait = invasionCooldown.tryAcquire(id);
        if (wait > 0) {
            event.getChannel().sendMessage("⏳ Нашествие можно отражать раз в сутки. Следующее через **" + Cooldowns.format(wait) + "**.").submit();
            return;
        }

        event.getChannel().sendMessage("🌊 **НАШЕСТВИЕ НАЧИНАЕТСЯ!** 10 волн врагов атакуют **модерскую**!\n" +
            "Приготовься к бою, " + player.getNickName() + "!").submit();

        int totalXp = 0;
        int totalMoney = 0;

        for (int waveIdx = 0; waveIdx < INVASION_WAVES.size(); waveIdx++) {
            WaveData wave = INVASION_WAVES.get(waveIdx);
            // Re-fetch player each wave to get updated HP
            player = playerRepository.get(id);
            if (player == null) break;

            NpcBot mob = NpcBot.builder()
                .nickName(wave.name())
                .hp(wave.hp())
                .maxHp(wave.hp())
                .strength(wave.str())
                .armor(0)
                .moneyReward(wave.reward())
                .xpReward(wave.reward())
                .build();

            // Simple battle: player attacks mob, mob counter-attacks
            int playerHp = player.getHp();
            int mobHp = wave.hp();
            boolean playerDied = false;

            while (mobHp > 0 && playerHp > 0) {
                int dmgToMob = Math.max(1, player.getStrength() - mob.getArmor());
                mobHp -= dmgToMob;
                if (mobHp <= 0) break;
                int dmgToPlayer = Math.max(1, mob.getStrength() - player.getArmor());
                playerHp -= dmgToPlayer;
                if (playerHp <= 0) { playerDied = true; break; }
            }

            if (playerDied) {
                // Persist reduced HP and die
                Player dead = playerRepository.get(id);
                if (dead != null) {
                    dead.setHp(0);
                    playerRepository.put(id, dead);
                }
                event.getChannel().sendMessage("💀 **Волна " + (waveIdx + 1) + " — " + wave.name() + "** убила тебя!\n" +
                    "Ты пал на волне " + (waveIdx + 1) + " из 10. Нашествие продолжается без тебя...").submit();
                return;
            }

            // Wave cleared
            totalXp += wave.reward();
            totalMoney += wave.reward();
            int newHp = Math.min(player.getMaxHp(), playerHp + 30);
            Player updated = playerRepository.get(id);
            if (updated != null) {
                updated.setHp(newHp);
                updated.setMoney(updated.getMoney() + wave.reward());
                updated.setExp(updated.getExp() + wave.reward());
                playerRepository.put(id, updated);
            }
            event.getChannel().sendMessage("✅ **Волна " + (waveIdx + 1) + " — " + wave.name() + "** повержена! +" + wave.reward() + " монет, +" + wave.reward() + " XP | HP: " + newHp).submit();
        }

        // All 10 waves cleared
        Player winner = playerRepository.get(id);
        if (winner != null) {
            winner.setExp(winner.getExp() + 500);
            winner.setMoney(winner.getMoney() + 200);
            playerRepository.put(id, winner);
        }
        event.getChannel().sendMessage("🎉 **" + player.getNickName() + "** выжил во всех 10 волнах нашествия!\n" +
            "Бонус: +500 XP, +200 монет! Итого получено: +" + totalXp + " XP, +" + totalMoney + " монет").submit();
        broadcastMessage("🏆 **" + player.getNickName() + "** победил **НАШЕСТВИЕ** в одиночку! 10 волн пройдено!");
    }

    /** +кризис статус — статус экономического кризиса */
    public void crisisStatus(MessageReceivedEvent event) {
        if (economicCrisisActive) {
            long minsLeft = (crisisEndsAt - System.currentTimeMillis()) / 60000;
            event.getChannel().sendMessage("💸 **Экономический кризис активен!** Цены удвоены. Осталось: **" + minsLeft + "** мин.").submit();
        } else {
            event.getChannel().sendMessage("✅ Экономический кризис не активен. Цены нормальные.").submit();
        }
    }

    /** +сезон — текущий сезонный предмет */
    public void showSeason(MessageReceivedEvent event) {
        long month = System.currentTimeMillis() / (30L * 24 * 60 * 60 * 1000);
        String[] items = {"❄️ Зимний плащ (+2 броня)", "🌸 Весенний амулет (+2 удача)", "☀️ Летний щит (+2 броня)", "🎃 Тыквенный топор (+3 сила)"};
        int idx = (int) (month % items.length);
        event.getChannel().sendMessage("🌟 **Сезонный предмет:** " + items[idx] + "\nДоступен в магазине временно!").submit();
    }
}
