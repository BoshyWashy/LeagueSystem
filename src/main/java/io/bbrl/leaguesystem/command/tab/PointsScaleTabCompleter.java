package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

import java.util.List;

public class PointsScaleTabCompleter implements TabSubcommand {
    private final LeagueManager manager;
    public PointsScaleTabCompleter(LeagueManager manager) { this.manager = manager; }
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return manager.allLeagues().stream().map(l -> l.getId()).toList();
        if (args.length == 2) return List.of("<scaleName>");
        if (args.length == 3) return List.of("list", "use", "<position>");
        if (args.length == 4) {
            String a = args[2];
            try { Integer.parseInt(a); return List.of("<points>"); } catch (Exception e) { return List.of(); }
        }
        return List.of();
    }
}