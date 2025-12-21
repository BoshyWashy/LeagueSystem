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
            sender.sendMessage("§cUsage: /league points <league> <scaleName> list|use|<position> <points>");
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
        if (!p.hasPermission("bbrl." + leagueId + ".manage")) {
            sender.sendMessage("§cNo permission");
            return;
        }

        Map<Integer, Integer> map = league.getConfig().getCustomScales().computeIfAbsent(scale, k -> new HashMap<>());

        switch (action) {
            case "list":
                if (map.isEmpty()) {
                    sender.sendMessage("§cScale empty");
                    return;
                }
                map.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(e -> sender.sendMessage(e.getKey() + " → " + e.getValue()));
                break;
            case "use":
                league.getConfig().setPointSystem("custom:" + scale);
                manager.saveLeague(league);
                sender.sendMessage("§aNow using scale " + scale);
                break;
            default: // set position points
                try {
                    int pos = Integer.parseInt(action);
                    int pts = Integer.parseInt(args[3]);
                    map.put(pos, pts);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet pos " + pos + " = " + pts + " pts");
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§aNumbers required");
                }
        }
    }
}