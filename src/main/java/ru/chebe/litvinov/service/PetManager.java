package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Pet;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

/**
 * Менеджер системы питомцев.
 * Управляет питомцами игроков: получение, кормление, эволюция и боевые бонусы.
 */
public class PetManager {

    private final PlayerRepository playerRepository;

    public PetManager(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /** +питомец — информация о питомце или получение яйца */
    public void getPetInfo(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player.getPet() == null) {
            // Дать стартового питомца (КОТ)
            Pet pet = new Pet("CAT");
            player.setPet(pet);
            playerRepository.put(id, player);
            event.getChannel().sendMessage("🐱 Вы получили питомца **КОТ**! Он готов сражаться рядом с вами.\n" +
                "Уровень: 1 | Голод: 100/100 | Регенерирует 5 HP в раунд.\nКормите питомца командой **+кормить [предмет]**").submit();
        } else {
            event.getChannel().sendMessage(formatPetInfo(player.getPet())).submit();
        }
    }

    private String formatPetInfo(Pet pet) {
        String typeRu = typeToRu(pet.getType());
        String evolved = pet.getLevel() >= 2 ? " (Эволюция)" : "";
        return "🐾 **Питомец: " + typeRu + evolved + "**\n" +
            "Уровень: " + pet.getLevel() + " | Голод: " + pet.getHunger() + "/100\n" +
            "Боёв: " + pet.getBattleCount() + "/10 до эволюции\n" +
            "Бонус: " + getBonusDescription(pet);
    }

    private String typeToRu(String type) {
        if (type == null) return "Неизвестный";
        return switch (type) {
            case "WOLF" -> "ВОЛК";
            case "FOX" -> "ЛИСА";
            case "CAT" -> "КОТ";
            case "RAVEN" -> "ВОРОН";
            case "LEGENDARY" -> "ЛЕГЕНДАРНЫЙ";
            default -> type;
        };
    }

    private String getBonusDescription(Pet pet) {
        int mult = pet.getLevel() >= 2 ? 2 : 1;
        return switch (pet.getType() != null ? pet.getType() : "") {
            case "WOLF" -> "+" + (3 * mult) + " к силе в бою";
            case "FOX" -> "+" + (3 * mult) + " к удаче в бою";
            case "CAT" -> "Регенерация " + (5 * mult) + " HP в раунд";
            case "RAVEN" -> pet.getLevel() >= 2 ? "+15% уклонение" : "Видит статы врага";
            case "LEGENDARY" -> "+" + (5 * mult) + " ко всем статам";
            default -> "Нет бонуса";
        };
    }

    /** +кормить [предмет] — кормим питомца, восстанавливая голод */
    public void feedPet(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);
        if (player.getPet() == null) {
            event.getChannel().sendMessage("У тебя нет питомца. Используй **+питомец** чтобы получить.").submit();
            return;
        }
        String itemName = event.getMessage().getContentDisplay().substring(8).trim().toLowerCase();
        if (!player.getInventory().containsKey(itemName)) {
            event.getChannel().sendMessage("Такого предмета нет в инвентаре.").submit();
            return;
        }
        // Любая еда восстанавливает 30 голода
        Pet pet = player.getPet();
        int oldHunger = pet.getHunger();
        pet.setHunger(Math.min(100, oldHunger + 30));
        pet.setLastFedTime(System.currentTimeMillis());
        // Потребляем предмет
        int count = player.getInventory().get(itemName);
        if (count <= 1) player.getInventory().remove(itemName);
        else player.getInventory().put(itemName, count - 1);
        playerRepository.put(id, player);
        event.getChannel().sendMessage("🍖 Питомец накормлен **" + itemName + "**! Голод: " + pet.getHunger() + "/100").submit();
    }

    /** Попытка эволюции питомца */
    public void evolvePet(Player player) {
        Pet pet = player.getPet();
        if (pet == null || pet.getLevel() >= 2) return;
        if (pet.getBattleCount() >= 10) {
            pet.setLevel(2);
            pet.setBattleCount(0);
        }
    }

    /** Возвращает бонус к характеристике от питомца (для боёв) */
    public int getPetBattleBonus(Player player, String stat) {
        Pet pet = player.getPet();
        if (pet == null || pet.getHunger() <= 0) return 0;
        int mult = pet.getLevel() >= 2 ? 2 : 1;
        return switch (pet.getType() != null ? pet.getType() : "") {
            case "WOLF" -> "str".equals(stat) ? 3 * mult : 0;
            case "FOX" -> "luck".equals(stat) ? 3 * mult : 0;
            case "LEGENDARY" -> 5 * mult;
            default -> 0;
        };
    }

    /** Специальная атака питомца (вызывается раз за бой после 3-го раунда) */
    public String getPetSpecialAttack(Player player, Person defender) {
        Pet pet = player.getPet();
        if (pet == null || pet.getHunger() <= 0) return null;
        int mult = pet.getLevel() >= 2 ? 2 : 1;
        return switch (pet.getType() != null ? pet.getType() : "") {
            case "WOLF" -> {
                int dmg = (int) (player.getStrength() * 0.5);
                defender.setHp(defender.getHp() - dmg);
                yield "🐺 Питомец **ВОЛК** атакует — урон **" + dmg + "**!";
            }
            case "CAT" -> {
                int heal = 30 * mult;
                player.setHp(Math.min(player.getHp() + heal, player.getMaxHp()));
                yield "🐱 Питомец **КОТ** исцеляет тебя на **" + heal + "** HP!";
            }
            case "LEGENDARY" -> {
                int dmg = (int) (player.getStrength() * 0.5);
                defender.setHp(defender.getHp() - dmg);
                int heal = 15;
                player.setHp(Math.min(player.getHp() + heal, player.getMaxHp()));
                yield "✨ Легендарный питомец атакует (**-" + dmg + "** HP врагу, **+" + heal + "** HP тебе)!";
            }
            default -> null;
        };
    }

    /** Регистрирует бой питомца (для счётчика эволюции) */
    public void registerBattle(Player player) {
        Pet pet = player.getPet();
        if (pet == null) return;
        pet.setBattleCount(pet.getBattleCount() + 1);
        // Снижаем голод на 10 за бой
        pet.setHunger(Math.max(0, pet.getHunger() - 10));
        evolvePet(player);
    }
}
