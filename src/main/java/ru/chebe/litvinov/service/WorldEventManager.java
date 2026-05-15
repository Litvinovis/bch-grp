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

    private Set<String> allowedChannelIds;
    private net.dv8tion.jda.api.JDA jda;

    public WorldEventManager(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        scheduleEvents();
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
        // Спавн мирового босса каждые 3 дня
        scheduler.scheduleAtFixedRate(this::spawnWorldBoss, 3, 72, TimeUnit.HOURS);
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

    private void spawnWorldBoss() {
        List<String> locs = LocationManager.locationList;
        worldBossLocation = locs.isEmpty() ? "мейн" : locs.get(random.nextInt(locs.size()));
        currentWorldBossData = BOSS_ROSTER.get(random.nextInt(BOSS_ROSTER.size()));
        worldBossHp = currentWorldBossData.hp();
        broadcastMessage("🌍 **МИРОВОЙ БОСС — " + currentWorldBossData.name() + "** появился в **" + worldBossLocation +
            "**! [❤️ HP: " + currentWorldBossData.hp() + " | ⚔️ Сила: " + currentWorldBossData.str() + "]\nАтакуйте командой **+мировой босс**!");
    }

    private void broadcastMessage(String msg) {
        if (jda == null || allowedChannelIds == null) return;
        for (String channelId : allowedChannelIds) {
            try {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch = jda.getTextChannelById(channelId);
                if (ch != null) ch.sendMessage(msg).queue();
            } catch (Exception ignored) {}
        }
    }

    /** +мировой босс — атака мирового босса */
    public void worldBossAttack(MessageReceivedEvent event) {
        if (worldBossHp <= 0) {
            event.getChannel().sendMessage("🌍 Мировой босс сейчас не активен. Следите за объявлениями!").submit();
            return;
        }
        // Fallback boss data if currentWorldBossData is null (e.g. set via reflection in tests)
        if (currentWorldBossData == null) {
            currentWorldBossData = BOSS_ROSTER.get(0);
        }
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (!player.getLocation().equals(worldBossLocation)) {
            event.getChannel().sendMessage("Мировой босс **" + currentWorldBossData.name() + "** находится в **" + worldBossLocation + "**. Переместись туда!").submit();
            return;
        }
        int damage = Math.max(1, player.getStrength() - 5);
        worldBossHp = Math.max(0, worldBossHp - damage);

        // Boss counter-damages player
        int counterDmg = Math.max(1, currentWorldBossData.str() - player.getArmor());
        player.setHp(player.getHp() - counterDmg);
        playerRepository.put(id, player);

        if (worldBossHp <= 0) {
            // Give boss loot to player
            Player freshPlayer = playerRepository.get(id);
            if (freshPlayer != null) {
                freshPlayer.setMoney(freshPlayer.getMoney() + 200);
                freshPlayer.setExp(freshPlayer.getExp() + 500);
                freshPlayer.getInventory().merge(currentWorldBossData.loot(), 1, Integer::sum);
                playerRepository.put(id, freshPlayer);
            }
            event.getChannel().sendMessage("💀 **" + player.getNickName() + "** нанёс финальный удар **" + currentWorldBossData.name() + "**!\n" +
                "⭐ Получено: +500 XP, +200 монет, предмет: **" + currentWorldBossData.loot() + "**!").submit();
            broadcastMessage("🎉 **" + currentWorldBossData.name() + "** повержен благодаря **" + player.getNickName() + "**!");
            currentWorldBossData = null;
        } else {
            event.getChannel().sendMessage("⚔️ Ты атаковал мирового босса **" + currentWorldBossData.name() + "**! Урон: **" + damage + "**. Осталось HP: **" + worldBossHp + "**\n" +
                "💥 Босс ударил в ответ: **" + counterDmg + "** урона! Твоё HP: **" + Math.max(0, player.getHp()) + "**").submit();
        }
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
