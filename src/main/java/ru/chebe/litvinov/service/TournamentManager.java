package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Person;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;
import ru.chebe.litvinov.repository.TournamentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Менеджер турниров.
 * Управляет регистрацией, сеткой и проведением турниров.
 */
public class TournamentManager {

    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final BattleManager battleManager;

    public TournamentManager(TournamentRepository tournamentRepository, PlayerRepository playerRepository,
                              BattleManager battleManager) {
        this.tournamentRepository = tournamentRepository;
        this.playerRepository = playerRepository;
        this.battleManager = battleManager;
    }

    /** +турнир — зарегистрироваться в турнире */
    public void registerForTournament(MessageReceivedEvent event) {
        String id = event.getAuthor().getId();
        Player player = playerRepository.get(id);

        TournamentRepository.Tournament active = tournamentRepository.getActive("arena");
        int tournamentId;
        if (active == null) {
            // Создаём новый турнир
            tournamentId = tournamentRepository.createTournament("arena", new ArrayList<>());
            if (tournamentId < 0) {
                event.getChannel().sendMessage("Ошибка создания турнира.").submit();
                return;
            }
            active = tournamentRepository.getActive("arena");
        } else {
            tournamentId = active.id();
        }

        if (active != null && active.participants().contains(id)) {
            event.getChannel().sendMessage("Ты уже зарегистрирован в турнире!").submit();
            return;
        }
        if (active != null && active.participants().size() >= 8) {
            event.getChannel().sendMessage("Турнир заполнен (максимум 8 участников).").submit();
            return;
        }

        tournamentRepository.addParticipant(tournamentId, id);
        TournamentRepository.Tournament updated = tournamentRepository.getActive("arena");
        int count = updated != null ? updated.participants().size() : 1;

        event.getChannel().sendMessage("⚔️ **" + player.getNickName() + "** зарегистрирован в турнире! (" + count + "/8)\n" +
            "Начало после набора 8 участников или командой **+турнир старт** (администратор)").submit();

        if (count >= 8) {
            startTournament(updated.participants(), event.getChannel());
            tournamentRepository.updateStatus(tournamentId, "finished");
        }
    }

    /** Запуск турнира со списком участников */
    public void startTournament(List<String> participantIds, net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion channel) {
        List<Player> players = participantIds.stream()
            .map(playerRepository::get)
            .filter(p -> p != null)
            .collect(Collectors.toList());

        if (players.size() < 2) {
            channel.sendMessage("Недостаточно участников для турнира.").queue();
            return;
        }

        channel.sendMessage("🏆 **ТУРНИР НАЧИНАЕТСЯ!** Участников: " + players.size()).queue();

        // Простой турнир: последовательные бои
        List<Player> remaining = new ArrayList<>(players);
        int round = 1;
        while (remaining.size() > 1) {
            channel.sendMessage("🔔 **Раунд " + round + "**").queue();
            List<Player> winners = new ArrayList<>();
            for (int i = 0; i + 1 < remaining.size(); i += 2) {
                Player p1 = remaining.get(i);
                Player p2 = remaining.get(i + 1);
                channel.sendMessage("⚔️ **" + p1.getNickName() + "** vs **" + p2.getNickName() + "**").queue();
                battleManager.playerBattle(List.of(p1), List.of(p2), channel);
                Player winner = p1.getHp() > 0 ? p1 : p2;
                // Восстановить HP победителя
                winner.setHp(winner.getMaxHp() / 2);
                winners.add(winner);
            }
            if (remaining.size() % 2 != 0) winners.add(remaining.get(remaining.size() - 1));
            remaining = winners;
            round++;
        }

        if (!remaining.isEmpty()) {
            Player champion = remaining.get(0);
            // Дать награду
            Player p = playerRepository.get(champion.getId());
            if (p != null) {
                p.setMoney(p.getMoney() + 1000);
                if (p.getAchievements() == null) p.setAchievements(new ArrayList<>());
                if (!p.getAchievements().contains("чемпион_турнира")) p.getAchievements().add("чемпион_турнира");
                playerRepository.put(champion.getId(), p);
            }
            channel.sendMessage("🏆 **ПОБЕДИТЕЛЬ ТУРНИРА: " + champion.getNickName() + "!** +1000 монет + достижение!").queue();
        }
    }

    /** +турнир статус */
    public void tournamentStatus(MessageReceivedEvent event) {
        TournamentRepository.Tournament active = tournamentRepository.getActive("arena");
        if (active == null) {
            event.getChannel().sendMessage("Активного турнира нет. Запишись командой **+турнир**!").submit();
            return;
        }
        var sb = new StringBuilder("⚔️ **Статус турнира** (регистрация)\n");
        sb.append("Участников: **").append(active.participants().size()).append("/8**\n");
        active.participants().forEach(pid -> {
            Player p = playerRepository.get(pid);
            if (p != null) sb.append("• ").append(p.getNickName()).append("\n");
        });
        event.getChannel().sendMessage(sb.toString()).submit();
    }

    /** +турнир сервера */
    public void serverTournament(MessageReceivedEvent event) {
        event.getChannel().sendMessage("🏆 **Турнир сервера** проводится ежемесячно!\nТекущая регистрация: **+турнир**\nСтатус: **+турнир статус**").submit();
    }
}
