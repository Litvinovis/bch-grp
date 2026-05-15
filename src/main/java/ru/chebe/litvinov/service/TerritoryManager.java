package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Clan;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.repository.TerritoryRepository;

import java.util.List;

/**
 * Менеджер территорий и осад.
 * Управляет захватом локаций кланами, осадами, крепостями и альянсами.
 */
public class TerritoryManager {

    private final TerritoryRepository territoryRepository;
    private final PlayerRepository playerRepository;
    private final ClanManager clanManager;
    private final LocationManager locationManager;

    public TerritoryManager(TerritoryRepository territoryRepository, PlayerRepository playerRepository,
                             ClanManager clanManager, LocationManager locationManager) {
        this.territoryRepository = territoryRepository;
        this.playerRepository = playerRepository;
        this.clanManager = clanManager;
        this.locationManager = locationManager;
    }

    /** +захватить [локация] — захватить территорию */
    public void captureTerritory(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player.getClanName() == null || player.getClanName().isBlank()) {
            event.getChannel().sendMessage("Для захвата территории нужен клан.").submit();
            return;
        }

        String locationArgRaw = event.getMessage().getContentDisplay().substring(10).trim().toLowerCase();
        final String locationArg = locationArgRaw.isEmpty() ? player.getLocation() : locationArgRaw;

        if (locationManager.getLocation(locationArg) == null) {
            event.getChannel().sendMessage("Такой локации не существует.").submit();
            return;
        }

        // Проверяем, есть ли 3+ членов клана в локации
        List<Player> clanInLoc = clanManager.getClanMembers(player.getClanName()).stream()
            .map(playerRepository::get)
            .filter(p -> p != null && p.getLocation().equals(locationArg))
            .toList();

        if (clanInLoc.size() < 3) {
            event.getChannel().sendMessage("Для захвата нужно минимум 3 члена клана в локации **" + locationArg + "** (сейчас: " + clanInLoc.size() + ").").submit();
            return;
        }

        // Простая механика захвата — бой с NPC-стражником
        if (player.getStrength() + player.getArmor() < 10) {
            event.getChannel().sendMessage("💀 Страж территории отразил атаку! Твоя команда недостаточно сильна.").submit();
            return;
        }

        territoryRepository.upsert(locationArg, player.getClanName(), System.currentTimeMillis(), 5);
        event.getChannel().sendMessage("🏰 Клан **" + player.getClanName() + "** захватил территорию **" + locationArg + "**! Налог: 5%").submit();
    }

    /** +осада [клан] / +осада статус */
    public void siegeStart(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        String arg = event.getMessage().getContentDisplay().substring(6).trim().toLowerCase();

        if (arg.equals("статус")) {
            siegeStatus(event);
            return;
        }

        if (player.getClanName() == null || player.getClanName().isBlank()) {
            event.getChannel().sendMessage("Для осады нужен клан.").submit();
            return;
        }

        if (arg.isEmpty()) {
            event.getChannel().sendMessage("Укажите цель: **+осада [клан]** или **+осада статус**").submit();
            return;
        }

        Clan targetClan = clanManager.getClan(arg);
        if (targetClan == null) {
            event.getChannel().sendMessage("Клан **" + arg + "** не найден.").submit();
            return;
        }

        // Записываем осаду в поле клана
        Clan myClan = clanManager.getClan(player.getClanName());
        if (myClan == null) return;
        if (myClan.getActiveSieges() == null) myClan.setActiveSieges(new java.util.HashMap<>());
        long endTime = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000;
        myClan.getActiveSieges().put(arg, endTime);
        clanManager.saveClan(myClan);
        event.getChannel().sendMessage("⚔️ Клан **" + player.getClanName() + "** начал осаду клана **" + arg + "**! Осада продлится 7 дней.").submit();
    }

    public void siegeStatus(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player.getClanName() == null || player.getClanName().isBlank()) {
            event.getChannel().sendMessage("Вы не состоите в клане.").submit();
            return;
        }
        Clan clan = clanManager.getClan(player.getClanName());
        if (clan == null || clan.getActiveSieges() == null || clan.getActiveSieges().isEmpty()) {
            event.getChannel().sendMessage("Активных осад нет.").submit();
            return;
        }
        var sb = new StringBuilder("⚔️ **Активные осады клана " + player.getClanName() + ":**\n");
        clan.getActiveSieges().forEach((target, end) -> {
            long daysLeft = (end - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
            sb.append("• **").append(target).append("** — осталось ").append(daysLeft).append(" дн.\n");
        });
        event.getChannel().sendMessage(sb.toString()).submit();
    }

    /** +крепость строить / +крепость улучшить [кузня/таверна/башня] */
    public void buildFortress(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        String arg = event.getMessage().getContentDisplay().substring(9).trim().toLowerCase();

        if (player.getClanName() == null || player.getClanName().isBlank()) {
            event.getChannel().sendMessage("Для строительства нужен клан.").submit();
            return;
        }
        Clan clan = clanManager.getClan(player.getClanName());
        if (clan == null) return;

        if (arg.equals("строить")) {
            if (player.getMoney() < 1000) {
                event.getChannel().sendMessage("Строительство крепости стоит **1000 монет**.").submit();
                return;
            }
            player.setMoney(player.getMoney() - 1000);
            playerRepository.put(id, player);
            if (clan.getFortressUpgrades() == null) clan.setFortressUpgrades(new java.util.ArrayList<>());
            if (!clan.getFortressUpgrades().contains("крепость")) {
                clan.getFortressUpgrades().add("крепость");
                clanManager.saveClan(clan);
            }
            event.getChannel().sendMessage("🏰 Крепость клана **" + player.getClanName() + "** построена в **" + player.getLocation() + "**!").submit();
        } else if (arg.startsWith("улучшить ")) {
            buildUpgrade(event, player, clan, arg.substring(9).trim());
        } else {
            event.getChannel().sendMessage("Использование: **+крепость строить** или **+крепость улучшить [кузня/таверна/башня]**").submit();
        }
    }

    private void buildUpgrade(MessageReceivedEvent event, Player player, Clan clan, String upgrade) {
        int cost = switch (upgrade) {
            case "кузня" -> 500;
            case "таверна" -> 300;
            case "башня" -> 700;
            default -> -1;
        };
        if (cost < 0) {
            event.getChannel().sendMessage("Доступные улучшения: **кузня** (500), **таверна** (300), **башня** (700)").submit();
            return;
        }
        if (player.getMoney() < cost) {
            event.getChannel().sendMessage("Недостаточно монет (нужно " + cost + ").").submit();
            return;
        }
        if (clan.getFortressUpgrades() == null) clan.setFortressUpgrades(new java.util.ArrayList<>());
        if (clan.getFortressUpgrades().contains(upgrade)) {
            event.getChannel().sendMessage("Улучшение **" + upgrade + "** уже построено.").submit();
            return;
        }
        player.setMoney(player.getMoney() - cost);
        playerRepository.put(player.getId(), player);
        clan.getFortressUpgrades().add(upgrade);
        clanManager.saveClan(clan);
        event.getChannel().sendMessage("✅ Улучшение **" + upgrade + "** построено в крепости!").submit();
    }

    /** Собирает налог при транзакции в захваченной территории */
    public void collectTax(Player player, int transactionAmount) {
        String location = player.getLocation();
        TerritoryRepository.Territory territory = territoryRepository.get(location);
        if (territory == null || territory.clanName().isBlank()) return;
        if (territory.clanName().equals(player.getClanName())) return; // Своя территория — без налога

        int tax = (int) (transactionAmount * territory.taxRate() / 100.0);
        if (tax <= 0) return;

        // Начисляем налог в клановый банк
        clanManager.addToClanBank(territory.clanName(), "монеты", tax);
    }

    /** +карта кланов — все территории и владельцы */
    public void territoryClanMap(MessageReceivedEvent event) {
        List<TerritoryRepository.Territory> territories = territoryRepository.getAll();
        if (territories.isEmpty()) {
            event.getChannel().sendMessage("🗺️ Ни одна территория пока не захвачена.").submit();
            return;
        }
        var sb = new StringBuilder("🗺️ **Карта кланов:**\n\n");
        territories.forEach(t -> sb.append("• **").append(t.locationName()).append("** → **")
            .append(t.clanName().isBlank() ? "Нейтральная" : t.clanName()).append("**\n"));
        event.getChannel().sendMessage(sb.toString()).submit();
    }

    /** +альянс [клан] — заключить альянс */
    public void declareAlliance(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player.getClanName() == null || player.getClanName().isBlank()) {
            event.getChannel().sendMessage("Для альянса нужен клан.").submit();
            return;
        }
        String targetClanName = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
        Clan myClan = clanManager.getClan(player.getClanName());
        Clan targetClan = clanManager.getClan(targetClanName);
        if (targetClan == null) {
            event.getChannel().sendMessage("Клан **" + targetClanName + "** не найден.").submit();
            return;
        }
        if (myClan == null) return;
        if (myClan.getAlliances() == null) myClan.setAlliances(new java.util.ArrayList<>());
        if (!myClan.getAlliances().contains(targetClanName)) {
            myClan.getAlliances().add(targetClanName);
            clanManager.saveClan(myClan);
        }
        event.getChannel().sendMessage("🤝 Клан **" + player.getClanName() + "** заключил альянс с **" + targetClanName + "**! +10% урона в рейдах вместе.").submit();
    }
}
