package io.bbrl.leaguesystem.command;

import io.bbrl.leaguesystem.command.subcommands.*;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.HologramService;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class LeagueRouter implements LeagueCommand.Subcommand {

    private final Map<String, LeagueCommand.Subcommand> subcommands = new HashMap<>();
    private final LeagueManager manager;

    public LeagueRouter(LeagueManager manager, HologramService hologramService) {
        this.manager = manager;

        subcommands.put("info", new InfoSubcommand(manager));
        subcommands.put("option", new OptionSubcommand(manager));
        subcommands.put("team", new TeamSubcommand(manager));
        subcommands.put("points", new PointsScaleSubcommand(manager));
        subcommands.put("season", new SeasonSubcommand(manager));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league <league-id> <subcommand> ...");
            return;
        }
        String leagueId = args[0].toLowerCase();
        Optional<League> ol = LeagueManager.getInstance().getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }

        if (args[1].equalsIgnoreCase("team") && args.length >= 4 && args[3].equalsIgnoreCase("join")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can join teams");
                return;
            }
            Player p = (Player) sender;
            String teamId = args[2].toLowerCase();
            League league = ol.get();
            if (!p.hasPermission("bbrl.op") && !LeagueManager.getInstance().hasInvite(league, teamId, p.getUniqueId().toString())) {
                sender.sendMessage("§cUnfortunately you haven't been invited to this team yet :(");
                return;
            }
            LeagueManager.getInstance().consumeInvite(league, teamId, p.getUniqueId().toString());
            LeagueManager.getInstance().addPlayerToTeam(league, teamId, p.getUniqueId().toString());
            sender.sendMessage("§aJoined team " + teamId);
            return;
        }

        String subKey = args[1].toLowerCase();
        LeagueCommand.Subcommand sc = subcommands.get(subKey);
        if (sc == null) {
            sender.sendMessage("§cUnknown subcommand.");
            return;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            sc.execute(sender, rest);
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred: " + e.getMessage());
            Bukkit.getLogger().warning("Error in league router: " + e.getMessage());
            e.printStackTrace();
        }
    }
}