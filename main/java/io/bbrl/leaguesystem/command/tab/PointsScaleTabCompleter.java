package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class PointsScaleTabCompleter implements TabSubcommand {
    private final LeagueManager manager;
    private final League league;

    public PointsScaleTabCompleter(LeagueManager manager, League league) {
        this.manager = manager;
        this.league = league;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // /league <league> points <scaleName> <subcommand>

        if (args.length == 1) {
            List<String> scales = new ArrayList<>(league.getConfig().getCustomScales().keySet());
            scales.add("<scaleName>");
            return scales.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("list", "use", "delete", "<position>").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3) {
            String action = args[1].toLowerCase();
            try {
                Integer.parseInt(action);
                return List.of("<points>");
            } catch (NumberFormatException e) {
                return List.of();
            }
        }
        return List.of();
    }
}