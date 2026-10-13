package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PointsScaleTabCompleter implements TabSubcommand {
    private final LeagueManager manager;
    private final League league;
    private final CommandSender sender;

    public PointsScaleTabCompleter(LeagueManager manager, League league, CommandSender sender) {
        this.manager = manager;
        this.league = league;
        this.sender = sender;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        boolean canManage = !(sender instanceof Player) ||
                ((Player)sender).hasPermission("league.op") ||
                ((Player)sender).hasPermission("league.leagueowner." + league.getId());

        // /league <league> points <scaleName> <subcommand>

        if (args.length == 1) {
            List<String> scales = new ArrayList<>(league.getConfig().getCustomScales().keySet());
            scales.add("<scaleName>");
            return scales.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            List<String> actions = new ArrayList<>(List.of("list"));
            if (canManage) {
                actions.addAll(List.of("use", "delete", "<position>"));
            }
            return actions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3) {
            if (!canManage) return List.of();
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