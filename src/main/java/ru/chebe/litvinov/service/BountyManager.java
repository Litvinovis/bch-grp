package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.BountyRepository;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер системы наград за голову (bounty).
 * Управляет размещением и получением наград.
 */
public class BountyManager {

    private final BountyRepository bountyRepository;
    private final PlayerRepository playerRepository;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public BountyManager(BountyRepository bountyRepository, PlayerRepository playerRepository) {
        this.bountyRepository = bountyRepository;
        this.playerRepository = playerRepository;
    }

    private ReentrantLock getLock(String id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    /** +бонт @player [reward] — поставить награду за голову */
    public void placeBounty(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        var mentions = event.getMessage().getMentions().getUsers();
        if (mentions.isEmpty()) {
            event.getChannel().sendMessage("Использование: **+бонт @игрок [сумма]**").submit();
            return;
        }
        String targetId = mentions.get(0).getId();
        if (targetId.equals(id)) {
            event.getChannel().sendMessage("Нельзя поставить награду на себя.").submit();
            return;
        }
        if (!playerRepository.contains(targetId)) {
            event.getChannel().sendMessage("Игрок не зарегистрирован.").submit();
            return;
        }
        String raw = event.getMessage().getContentRaw();
        int mentionEnd = raw.indexOf('>') + 1;
        String rest = mentionEnd > 0 ? raw.substring(mentionEnd).trim() : "";
        int reward;
        try {
            reward = Integer.parseInt(rest);
        } catch (NumberFormatException e) {
            event.getChannel().sendMessage("Укажите сумму: **+бонт @игрок [сумма]**").submit();
            return;
        }
        if (reward <= 0) {
            event.getChannel().sendMessage("Сумма должна быть больше нуля.").submit();
            return;
        }

        ReentrantLock lock = getLock(id);
        lock.lock();
        try {
            Player player = playerRepository.get(id);
            if (player.getMoney() < reward) {
                event.getChannel().sendMessage("Недостаточно монет.").submit();
                return;
            }
            player.setMoney(player.getMoney() - reward);
            playerRepository.put(id, player);
        } finally {
            lock.unlock();
        }

        bountyRepository.place(targetId, id, reward);
        Player target = playerRepository.get(targetId);
        event.getChannel().sendMessage("🎯 Награда **" + reward + "** монет назначена за голову **" +
            (target != null ? target.getNickName() : targetId) + "**!").submit();
    }

    /** +бонты — список активных наград */
    public void getBounties(MessageReceivedEvent event) {
        List<BountyRepository.Bounty> bounties = bountyRepository.getActive();
        if (bounties.isEmpty()) {
            event.getChannel().sendMessage("🎯 Активных наград за голову нет.").submit();
            return;
        }
        var sb = new StringBuilder("🎯 **Активные награды за голову:**\n\n");
        for (BountyRepository.Bounty b : bounties) {
            Player target = playerRepository.get(b.targetId());
            String targetName = target != null ? target.getNickName() : b.targetId();
            sb.append("• **").append(targetName).append("** — ").append(b.reward()).append(" монет\n");
        }
        sb.append("\nПолучить: победи цель в PvP!");
        event.getChannel().sendMessage(sb.toString()).submit();
    }

    /**
     * Проверяет и выплачивает награду за убийство цели.
     *
     * @param killerId идентификатор убийцы
     * @param targetId идентификатор жертвы
     * @return сумма награды (0 если не было)
     */
    public int claimBounty(String killerId, String targetId) {
        int reward = bountyRepository.claimAndGetReward(targetId);
        if (reward > 0) {
            ReentrantLock lock = getLock(killerId);
            lock.lock();
            try {
                Player killer = playerRepository.get(killerId);
                if (killer != null) {
                    killer.setMoney(killer.getMoney() + reward);
                    playerRepository.put(killerId, killer);
                }
            } finally {
                lock.unlock();
            }
        }
        return reward;
    }
}
