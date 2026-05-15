package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Менеджер профессий.
 * Управляет выбором профессии, добычей ресурсов, крафтом и биржей ресурсов.
 */
public class ProfessionManager {

    private static final long GATHER_COOLDOWN_MS = 30 * 60 * 1000L; // 30 минут

    private final PlayerRepository playerRepository;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    // Хранит время последней добычи: playerId -> lastGatherTime
    private final ConcurrentHashMap<String, Long> lastGatherTime = new ConcurrentHashMap<>();

    // Биржа ресурсов: ресурс -> базовая цена
    private static final Map<String, Integer> RESOURCE_BASE_PRICES = Map.of(
        "руда", 15,
        "древесина", 10,
        "травы", 12,
        "камень", 8
    );

    // Локация -> ресурс
    private static final Map<String, String> LOCATION_RESOURCE = Map.of(
        "качалочка", "руда",
        "для-флуда", "древесина",
        "болото", "травы",
        "клоунская-братва", "камень",
        "дорогой-дневник", "древесина",
        "чебеграм", "травы",
        "деградач", "руда",
        "политота", "камень"
    );

    // Доступные профессии
    private static final Set<String> PROFESSIONS = Set.of("кузнец", "алхимик", "повар", "ювелир");

    public ProfessionManager(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    private ReentrantLock getLock(String id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    /** +профессия выбрать [профессия] / +профессия инфо */
    public void chooseProfession(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        String arg = event.getMessage().getContentDisplay().substring(10).trim().toLowerCase();

        if (arg.equals("инфо") || arg.isEmpty()) {
            String prof = player.getProfession() != null && !player.getProfession().isBlank()
                ? player.getProfession() : "не выбрана";
            event.getChannel().sendMessage("⚒️ **Профессии**\n" +
                "Текущая: **" + prof + "** (уровень " + player.getProfessionLevel() + ")\n\n" +
                "Доступные профессии: кузнец, алхимик, повар, ювелир\n" +
                "Выбрать: **+профессия выбрать [профессия]**\n\n" +
                "• **Кузнец** — добывает руду, куёт оружие (+сила)\n" +
                "• **Алхимик** — собирает травы, варит зелья (+HP)\n" +
                "• **Повар** — заготавливает дрова, готовит еду (длинные баффы)\n" +
                "• **Ювелир** — добывает камень, создаёт кольца и амулеты (+удача/сила)").submit();
            return;
        }

        if (!arg.startsWith("выбрать ")) {
            event.getChannel().sendMessage("Использование: **+профессия выбрать [профессия]** или **+профессия инфо**").submit();
            return;
        }

        String chosen = arg.substring(8).trim();
        if (!PROFESSIONS.contains(chosen)) {
            event.getChannel().sendMessage("Неизвестная профессия. Доступны: " + PROFESSIONS).submit();
            return;
        }

        ReentrantLock lock = getLock(id);
        lock.lock();
        try {
            Player p = playerRepository.get(id);
            if (p.getProfession() != null && !p.getProfession().isBlank()) {
                event.getChannel().sendMessage("У тебя уже есть профессия: **" + p.getProfession() + "**").submit();
                return;
            }
            p.setProfession(chosen);
            p.setProfessionLevel(1);
            playerRepository.put(id, p);
            event.getChannel().sendMessage("✅ Ты выбрал профессию **" + chosen + "**! Уровень: 1.\nНачни добывать ресурсы командой **+добыть**").submit();
        } finally {
            lock.unlock();
        }
    }

    /** +добыть — добыть ресурс в текущей локации */
    public void gatherResource(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);

        if (player.getProfession() == null || player.getProfession().isBlank()) {
            event.getChannel().sendMessage("Сначала выбери профессию: **+профессия выбрать [профессия]**").submit();
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastGatherTime.get(id);
        if (last != null && now - last < GATHER_COOLDOWN_MS) {
            long minLeft = (GATHER_COOLDOWN_MS - (now - last)) / 60000;
            event.getChannel().sendMessage("⏳ Добыча на кулдауне. Следующая добыча через **" + minLeft + "** мин.").submit();
            return;
        }

        String location = player.getLocation();
        String resource = LOCATION_RESOURCE.getOrDefault(location, "руда");

        lastGatherTime.put(id, now);

        ReentrantLock lock = getLock(id);
        lock.lock();
        try {
            Player p = playerRepository.get(id);
            if (p.getResources() == null) p.setResources(new java.util.HashMap<>());
            int amount = 1 + (int)(p.getProfessionLevel() / 3.0); // Больше ресурсов на высоких уровнях
            p.getResources().merge(resource, amount, Integer::sum);
            playerRepository.put(id, p);
            event.getChannel().sendMessage("⛏️ Ты добыл **" + amount + "x " + resource + "** в локации **" + location + "**!").submit();
        } finally {
            lock.unlock();
        }
    }

    /** +создать [рецепт] — создание предмета по рецепту */
    public void craftItem(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);

        if (player.getProfession() == null || player.getProfession().isBlank()) {
            event.getChannel().sendMessage("Сначала выбери профессию: **+профессия выбрать [профессия]**").submit();
            return;
        }

        String recipe = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
        if (recipe.isEmpty()) {
            showRecipesForProfession(event, player.getProfession());
            return;
        }

        ReentrantLock lock = getLock(id);
        lock.lock();
        try {
            Player p = playerRepository.get(id);
            if (p.getResources() == null) p.setResources(new java.util.HashMap<>());

            String result = tryCraft(p, recipe);
            if (result == null) {
                event.getChannel().sendMessage("❌ Неизвестный рецепт или недостаточно ресурсов. Посмотри **+рецепты**").submit();
                return;
            }

            // Опыт профессии
            p.setProfessionLevel(p.getProfessionLevel() + 1);
            // Каждые 10 крафтов +1 к уровню профессии
            // (level теперь = количеству крафтов, показывается как есть)
            playerRepository.put(id, p);
            event.getChannel().sendMessage("✅ Создан предмет: **" + result + "**! Уровень профессии: " + p.getProfessionLevel()).submit();
        } finally {
            lock.unlock();
        }
    }

    private String tryCraft(Player player, String recipe) {
        Map<String, Integer> res = player.getResources();
        switch (player.getProfession().toLowerCase()) {
            case "кузнец" -> {
                if ("кованый меч".equals(recipe)) {
                    if (res.getOrDefault("руда", 0) < 3) return null;
                    res.merge("руда", -3, Integer::sum);
                    player.setStrength(player.getStrength() + 5);
                    return "Кованый меч (+5 силы)";
                }
            }
            case "алхимик" -> {
                if ("зелье алхимика".equals(recipe)) {
                    if (res.getOrDefault("травы", 0) < 2) return null;
                    res.merge("травы", -2, Integer::sum);
                    player.setHp(Math.min(player.getHp() + 50, player.getMaxHp()));
                    return "Зелье алхимика (+50 HP)";
                }
            }
            case "повар" -> {
                if ("сытная еда".equals(recipe)) {
                    if (res.getOrDefault("древесина", 0) < 1) return null;
                    res.merge("древесина", -1, Integer::sum);
                    player.setHp(Math.min(player.getHp() + 20, player.getMaxHp()));
                    return "Сытная еда (+20 HP)";
                }
            }
            case "ювелир" -> {
                if ("кольцо силы".equals(recipe)) {
                    if (res.getOrDefault("камень", 0) < 2) return null;
                    res.merge("камень", -2, Integer::sum);
                    player.setStrength(player.getStrength() + 3);
                    if (player.getJewelry() == null) player.setJewelry(new java.util.HashMap<>());
                    player.getJewelry().merge("кольцо силы", 1, Integer::sum);
                    return "Кольцо силы (+3 силы навсегда)";
                }
                if ("амулет удачи".equals(recipe)) {
                    if (res.getOrDefault("камень", 0) < 2) return null;
                    res.merge("камень", -2, Integer::sum);
                    player.setLuck(player.getLuck() + 3);
                    if (player.getJewelry() == null) player.setJewelry(new java.util.HashMap<>());
                    player.getJewelry().merge("амулет удачи", 1, Integer::sum);
                    return "Амулет удачи (+3 удачи навсегда)";
                }
            }
        }
        return null;
    }

    /** +рецепты — список рецептов профессии */
    public void showRecipes(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player.getProfession() == null || player.getProfession().isBlank()) {
            event.getChannel().sendMessage("Сначала выбери профессию.").submit();
            return;
        }
        showRecipesForProfession(event, player.getProfession());
    }

    private void showRecipesForProfession(MessageReceivedEvent event, String prof) {
        String info = switch (prof.toLowerCase()) {
            case "кузнец" -> "**Рецепты Кузнеца:**\n• **кованый меч** — 3x руда → +5 силы";
            case "алхимик" -> "**Рецепты Алхимика:**\n• **зелье алхимика** — 2x травы → +50 HP";
            case "повар" -> "**Рецепты Повара:**\n• **сытная еда** — 1x древесина → +20 HP";
            case "ювелир" -> "**Рецепты Ювелира:**\n• **кольцо силы** — 2x камень → +3 силы (навсегда)\n• **амулет удачи** — 2x камень → +3 удачи (навсегда)";
            default -> "Рецепты для профессии **" + prof + "** не найдены.";
        };
        event.getChannel().sendMessage(info + "\n\nТекущие ресурсы смотри в **+стата**. Команда: **+создать [рецепт]**").submit();
    }

    /** +биржа ресурсов — список цен на ресурсы */
    public void resourceMarket(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        var sb = new StringBuilder("💹 **Биржа ресурсов**\n\n");
        RESOURCE_BASE_PRICES.forEach((res, price) -> {
            int have = player.getResources() != null ? player.getResources().getOrDefault(res, 0) : 0;
            sb.append("• **").append(res).append("** — ").append(price).append(" монет (у тебя: ").append(have).append(")\n");
        });
        sb.append("\nПродать: **+продать ресурс [ресурс] [количество]**");
        event.getChannel().sendMessage(sb.toString()).submit();
    }
}
