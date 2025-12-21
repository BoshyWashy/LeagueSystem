package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

public class TeamTabCompleter implements TabSubcommand {
    private final LeagueManager manager;

    public TeamTabCompleter(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("create", "invite", "leave", "info", "leaderboard");
        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length == 2) return manager.allLeagues().stream().map(l -> l.getId()).toList();
                if (args.length == 3) return List.of("<teamId>");
                if (args.length == 4) return List.of("#FFFFFF");
                return List.of();
            case "invite":
                if (args.length == 2) return manager.allLeagues().stream().map(l -> l.getId()).toList();
                if (args.length == 3) {
                    String leagueId = args[1];
                    return manager.getLeague(leagueId)
                            .map(l -> l.getTeams().keySet().stream().toList())
                            .orElse(List.of());
                }
                if (args.length == 4) return Bukkit.getOnlinePlayers().stream()
                        .map(p -> p.getName())
                        .toList();
                if (args.length == 5) return List.of("reserve");
                return List.of();
            case "leave", "leaderboard":
                if (args.length == 2) return manager.allLeagues().stream().map(l -> l.getId()).toList();
                return List.of();
            case "info":
                if (args.length == 2) return manager.allLeagues().stream().map(l -> l.getId()).toList();
                if (args.length == 3) {
                    String leagueId = args[1];
                    return manager.getLeague(leagueId)
                            .map(l -> l.getTeams().keySet().stream().toList())
                            .orElse(List.of());
                }
                return List.of();
        }
        return List.of();
    }
}