package io.bbrl.leaguesystem.command;

import io.bbrl.leaguesystem.command.subcommands.*;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.HologramService;
import io.bbrl.leaguesystem.service.LeaderboardManager;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class LeagueRouter implements LeagueCommand.Subcommand {

    private final Map<String, LeagueCommand.Subcommand> subcommands = new HashMap<>();
    private final LeagueManager manager;
    private final LeaderboardManager leaderboardManager;

    public LeagueRouter(LeagueManager manager, HologramService hologramService, LeaderboardManager leaderboardManager) {
        this.manager = manager;
        this.leaderboardManager = leaderboardManager;

        subcommands.put("info", new InfoSubcommand(manager));
        subcommands.put("option", new OptionSubcommand(manager));
        subcommands.put("team", new TeamSubcommand(manager));
        subcommands.put("points", new PointsScaleSubcommand(manager));
        subcommands.put("season", new SeasonSubcommand(manager, leaderboardManager));
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

        // FIX: Removed the hardcoded team join interception here.
        // The proper invite handling is done in TeamSubcommand.handleJoin()
        // which correctly checks permissions and consumes invites.

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