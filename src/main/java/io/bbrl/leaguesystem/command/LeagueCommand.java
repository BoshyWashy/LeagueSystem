package io.bbrl.leaguesystem.command;

import io.bbrl.leaguesystem.command.subcommands.*;
import io.bbrl.leaguesystem.command.tab.*;
import io.bbrl.leaguesystem.service.HologramService;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class LeagueCommand implements CommandExecutor, TabCompleter {

    private final Map<String, Subcommand> subcommands = new HashMap<>();
    private final Map<String, TabSubcommand> tabSubcommands = new HashMap<>();

    public LeagueCommand(LeagueManager manager, HologramService hologramService) {
        subcommands.put("create", new CreateSubcommand(manager));
        subcommands.put("info", new InfoSubcommand(manager));
        subcommands.put("option", new OptionSubcommand(manager));
        subcommands.put("team", new TeamSubcommand(manager));
        // standings removed from root – now inside season
        subcommands.put("points", new PointsScaleSubcommand(manager));
        subcommands.put("season", new SeasonSubcommand(manager));

        tabSubcommands.put("create", new CreateTabCompleter());
        tabSubcommands.put("info", new InfoTabCompleter());
        tabSubcommands.put("option", new OptionTabCompleter(manager));
        tabSubcommands.put("team", new TeamTabCompleter(manager));
        // standings tab removed from root
        tabSubcommands.put("points", new PointsScaleTabCompleter(manager));
        tabSubcommands.put("season", new SeasonTabCompleter(manager));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /league <subcommand>");
            return true;
        }
        String key = args[0].toLowerCase();
        Subcommand sc = subcommands.get(key);
        if (sc == null) {
            sender.sendMessage("§cUnknown subcommand.");
            return true;
        }
        String[] rest = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        sc.execute(sender, rest);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return subcommands.keySet().stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        String sub = args[0].toLowerCase();
        TabSubcommand tab = tabSubcommands.get(sub);
        if (tab != null) {
            return tab.onTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    public interface Subcommand {
        void execute(CommandSender sender, String[] args);
    }
}