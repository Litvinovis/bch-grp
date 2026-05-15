package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
    private static final int WORLD_BOSS_MAX_HP = 5000;
    private static final int WORLD_BOSS_STR = 30;

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
        String[] locations = {"мейн", "деградач", "кушетка", "модерская", "политота"};
        worldBossLocation = locations[random.nextInt(locations.length)];
        worldBossHp = WORLD_BOSS_MAX_HP;
        broadcastMessage("🌍 **МИРОВОЙ БОСС** появился в **" + worldBossLocation + "**! [❤️ HP: 5000 | ⚔️ Сила: 30]\nАтакуйте командой **+мировой босс**!");
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
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (!player.getLocation().equals(worldBossLocation)) {
            event.getChannel().sendMessage("Мировой босс находится в **" + worldBossLocation + "**. Переместись туда!").submit();
            return;
        }
        int damage = Math.max(1, player.getStrength() - 5);
        worldBossHp = Math.max(0, worldBossHp - damage);

        if (worldBossHp <= 0) {
            event.getChannel().sendMessage("💀 **" + player.getNickName() + "** нанёс финальный удар мировому боссу!\n" +
                "⭐ Получено: +500 XP, +200 монет!").submit();
            player.setMoney(player.getMoney() + 200);
            playerRepository.put(id, player);
            broadcastMessage("🎉 Мировой босс повержен благодаря **" + player.getNickName() + "**!");
        } else {
            event.getChannel().sendMessage("⚔️ Ты атаковал мирового босса! Урон: **" + damage + "**. Осталось HP: **" + worldBossHp + "**").submit();
        }
    }

    /** +нашествие — статус нашествия */
    public void invasionStatus(MessageReceivedEvent event) {
        event.getChannel().sendMessage("🌊 **Нашествие** — еженедельное событие!\n" +
            "Все игроки в **модерской** сражаются с 10 волнами мобов.\n" +
            "Награда: +500 XP, +200 монет за участие.\n" +
            "Следующее нашествие: воскресенье.").submit();
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
