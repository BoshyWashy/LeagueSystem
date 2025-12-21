package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

import java.util.List;

public class OptionTabCompleter implements TabSubcommand {
    private final LeagueManager manager;

    public OptionTabCompleter(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return manager.allLeagues().stream()
                .map(l -> l.getId())
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .toList();
        if (args.length == 2) return List.of(
                "maxteamownership", "maxdriversperteam", "maxreservesperteam", "pointsystem"
        );
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("pointsystem")) return List.of("standard", "sprint", "custom");
            return List.of("<value>");
        }
        return List.of();
    }
}