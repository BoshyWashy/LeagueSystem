package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class PointsScaleSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public PointsScaleSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league points <league> <scaleName> list|use|delete|<position> <points>");
            return;
        }
        String leagueId = args[0];
        String scale = args[1].toLowerCase();
        String action = args[2].toLowerCase();

        var ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        Player p = (Player) sender;

        // Allow 'list' command for anyone with league.use permission
        boolean hasManagePerm = p.hasPermission("league." + leagueId + ".manage") ||
                p.hasPermission("league.op") ||
                p.hasPermission("league.leagueowner." + leagueId);
        boolean hasUsePerm = p.hasPermission("league.use");

        if (action.equals("list")) {
            if (!hasUsePerm && !hasManagePerm) {
                sender.sendMessage("§cYou need permission 'league.use' to view point scales");
                return;
            }
        } else {
            if (!hasManagePerm) {
                sender.sendMessage("§cNo permission to modify point scales. You need 'league.leagueowner." + leagueId + "' or 'league.op'");
                return;
            }
        }

        Map<Integer, Integer> map = league.getConfig().getCustomScales().computeIfAbsent(scale, k -> new HashMap<>());

        switch (action) {
            case "list":
                if (map.isEmpty()) {
                    sender.sendMessage("§cScale empty");
                    return;
                }
                sender.sendMessage("§aPoints scale: " + scale);
                map.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(e -> sender.sendMessage("§aP" + e.getKey() + ": §f" + e.getValue() + " pts"));
                break;
            case "use":
                league.getConfig().setPointSystem("custom:" + scale);
                manager.saveLeague(league);
                manager.getStorage().savePointSystem(league, league.getConfig().getCustomScales());
                sender.sendMessage("§aNow using scale " + scale);
                break;
            case "delete":
                if (scale.equals("f1")) {
                    sender.sendMessage("§cCannot delete the default F1 scale");
                    return;
                }
                league.getConfig().getCustomScales().remove(scale);
                manager.getStorage().deletePointSystem(league, scale);
                sender.sendMessage("§aDeleted scale " + scale);
                break;
            default:
                try {
                    int pos = Integer.parseInt(action);
                    int pts = Integer.parseInt(args[3]);
                    map.put(pos, pts);
                    manager.saveLeague(league);
                    manager.getStorage().savePointSystem(league, league.getConfig().getCustomScales());
                    sender.sendMessage("§aSet P" + pos + " = " + pts + " pts in scale " + scale);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                    sender.sendMessage("§cUsage: /league points <league> <scale> <position> <points>");
                }
        }
    }
}