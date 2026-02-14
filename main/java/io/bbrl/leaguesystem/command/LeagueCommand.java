package io.bbrl.leaguesystem.command;

import io.bbrl.leaguesystem.command.subcommands.*;
import io.bbrl.leaguesystem.command.tab.*;
import io.bbrl.leaguesystem.service.HologramService;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class LeagueCommand implements CommandExecutor, TabCompleter {

    private final LeagueManager manager;
    private final HologramService hologramService;

    public LeagueCommand(LeagueManager manager, HologramService hologramService) {
        this.manager = manager;
        this.hologramService = hologramService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check for base command
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.use") && !p.hasPermission("league.op")) {
                sender.sendMessage("§cYou do not have permission to perform this command.");
                return true;
            }
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("create")) {
            String[] rest = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
            new CreateSubcommand(manager).execute(sender, rest);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league <create|league> ...");
            sender.sendMessage("§cUse /league create <id> [name] to create a new league");
            sender.sendMessage("§cUse /league <league-id> <subcommand> for league management");
            return true;
        }

        String leagueId = args[0].toLowerCase();
        Optional<io.bbrl.leaguesystem.model.League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return true;
        }

        String subKey = args[1].toLowerCase();
        String[] rest = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        if (subKey.equals("delete")) {
            if (rest.length != 1 || !rest[0].equalsIgnoreCase("confirm")) {
                sender.sendMessage("§cUsage: /league " + leagueId + " delete confirm");
                sender.sendMessage("§c§lWARNING: This will delete the entire league and all its data!");
                return true;
            }

            if (sender instanceof Player p) {
                // Only league owner or op can delete leagues
                if (!p.hasPermission("league.op") && !p.hasPermission("league.leagueowner." + leagueId)) {
                    sender.sendMessage("§cYou don't have permission to delete this league. You need 'league.leagueowner." + leagueId + "' or 'league.op'");
                    return true;
                }
            }

            manager.deleteLeague(leagueId);
            sender.sendMessage("§aLeague §l" + leagueId + "§a and all its data deleted.");
            return true;
        }

        Subcommand sc = switch (subKey) {
            case "info"   -> new InfoSubcommand(manager);
            case "option" -> new OptionSubcommand(manager);
            case "team"   -> new TeamSubcommand(manager);
            case "points" -> new PointsScaleSubcommand(manager);
            case "season" -> new SeasonSubcommand(manager);
            case "setspawn", "spawn" -> new SpawnSubcommand(manager);
            default -> null;
        };

        if (sc == null) {
            sender.sendMessage("§cUnknown subcommand. Available: info, option, team, points, season, setspawn, spawn, delete");
            return true;
        }

        String[] subArgs;
        if (subKey.equals("setspawn") || subKey.equals("spawn")) {
            subArgs = new String[2];
            subArgs[0] = leagueId;
            subArgs[1] = subKey;
        } else {
            subArgs = new String[rest.length + 1];
            subArgs[0] = leagueId;
            System.arraycopy(rest, 0, subArgs, 1, rest.length);
        }

        try {
            sc.execute(sender, subArgs);
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred: " + e.getMessage());
            Bukkit.getLogger().warning("Error executing league command: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();

        // Check base permission for tab complete
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.use") && !p.hasPermission("league.op")) {
                return List.of();
            }
        }

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            // Only show create if they have permission
            if (!(sender instanceof Player) ||
                    ((Player)sender).hasPermission("league.op") ||
                    ((Player)sender).hasPermission("league.createleague")) {
                out.add("create");
            }
            manager.allLeagues().forEach(l -> out.add(l.getId()));
            return out.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        String firstArg = args[0].toLowerCase();

        if (firstArg.equals("create")) {
            if (args.length == 2) return List.of("<id>");
            if (args.length == 3) return List.of("[display name]");
            return List.of();
        }

        Optional<io.bbrl.leaguesystem.model.League> ol = manager.getLeague(firstArg);
        if (ol.isEmpty()) return List.of();

        io.bbrl.leaguesystem.model.League league = ol.get();

        if (args.length == 2) {
            List<String> out = new ArrayList<>(List.of("info", "option", "team", "points", "season", "spawn"));
            // Only show delete and setspawn if they have permission
            if (!(sender instanceof Player) ||
                    ((Player)sender).hasPermission("league.op") ||
                    ((Player)sender).hasPermission("league.leagueowner." + firstArg)) {
                out.add("delete");
                out.add("setspawn");
            }
            return out.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        String subKey = args[1].toLowerCase();
        String[] rest = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        TabSubcommand tab = switch (subKey) {
            case "info"   -> new InfoTabCompleter();
            case "option" -> new OptionTabCompleter(manager);
            case "team"   -> new TeamTabCompleter(manager, league);
            case "points" -> new PointsScaleTabCompleter(manager, league);
            case "season" -> new SeasonTabCompleter(manager, league);
            case "setspawn", "spawn" -> null;
            default -> null;
        };

        if (tab == null) return List.of();

        return tab.onTabComplete(sender, rest);
    }

    public interface Subcommand {
        void execute(CommandSender sender, String[] args);
    }
}