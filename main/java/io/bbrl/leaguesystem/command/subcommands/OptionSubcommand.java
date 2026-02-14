package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

public class OptionSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public OptionSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    private static final Map<String, String> COLOR_MAP = Map.ofEntries(
            Map.entry("black", "§0"), Map.entry("dark_blue", "§1"),
            Map.entry("dark_green", "§2"), Map.entry("dark_aqua", "§3"),
            Map.entry("dark_red", "§4"), Map.entry("dark_purple", "§5"),
            Map.entry("gold", "§6"), Map.entry("gray", "§7"),
            Map.entry("dark_gray", "§8"), Map.entry("blue", "§9"),
            Map.entry("green", "§a"), Map.entry("aqua", "§b"),
            Map.entry("red", "§c"), Map.entry("light_purple", "§d"),
            Map.entry("yellow", "§e"), Map.entry("white", "§f")
    );

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league option <league> <setting> <value>");
            return;
        }
        String leagueId = args[0];
        String setting = args[1].toLowerCase();
        String value = args[2];

        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }
        League league = ol.get();

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can modify league options.");
            return;
        }
        Player p = (Player) sender;

        // Check permissions: league owner or operator
        boolean isLeagueOwner = p.hasPermission("league.leagueowner." + leagueId);
        boolean isOp = p.hasPermission("league.op");

        if (!isLeagueOwner && !isOp) {
            sender.sendMessage("§cYou lack permission. You need 'league.leagueowner." + leagueId + "' or 'league.op'");
            return;
        }

        switch (setting) {
            case "maxteamownership":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setMaxTeamOwnership(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet maxTeamOwnership to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "maxdriversperteam":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setMaxDriversPerTeam(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet maxDriversPerTeam to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "maxreservesperteam":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setMaxReservesPerTeam(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet maxReservesPerTeam to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "pointsystem":
                league.getConfig().setPointSystem(value.toLowerCase());
                manager.saveLeague(league);
                sender.sendMessage("§aSet pointSystem to " + value.toLowerCase());
                break;
            case "strikes":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setStrikeRaces(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet strike races to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "fastestlap":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setFastestLapPoints(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet fastest lap points to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "primarycolor":
                String primaryCode = COLOR_MAP.get(value.toLowerCase());
                if (primaryCode == null) {
                    sender.sendMessage("§cUnknown color. Use: " + String.join(", ", COLOR_MAP.keySet()));
                    return;
                }
                league.getConfig().setPrimaryColor(primaryCode);
                manager.saveLeague(league);
                sender.sendMessage("§aSet primary color to " + value);
                break;
            case "secondarycolor":
                String secondaryCode = COLOR_MAP.get(value.toLowerCase());
                if (secondaryCode == null) {
                    sender.sendMessage("§cUnknown color. Use: " + String.join(", ", COLOR_MAP.keySet()));
                    return;
                }
                league.getConfig().setSecondaryColor(secondaryCode);
                manager.saveLeague(league);
                sender.sendMessage("§aSet secondary color to " + value);
                break;
            case "allowanyonecreateteam":
                boolean allow = Boolean.parseBoolean(value);
                league.getConfig().setAllowAnyoneCreateTeam(allow);
                manager.saveLeague(league);
                sender.sendMessage("§aSet allowAnyoneCreateTeam to " + allow);
                sender.sendMessage(allow ? "§7Anyone can now create teams in this league" : "§7Only players with 'league.teamcreate." + leagueId + "' can create teams");
                break;
            case "renameid":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /league option <league> renameid <new-id>");
                    return;
                }
                String newId = args[2].toLowerCase().replaceAll("[^a-z0-9]", "");
                if (newId.isEmpty()) {
                    sender.sendMessage("§cInvalid ID");
                    return;
                }
                if (manager.getLeague(newId).isPresent()) {
                    sender.sendMessage("§cA league with that ID already exists");
                    return;
                }
                String oldId = league.getId();
                String currentName = league.getName();
                manager.renameLeague(oldId, newId, currentName);
                sender.sendMessage("§aRenamed league ID from '" + oldId + "' to '" + newId + "'");
                sender.sendMessage("§c§lNote: Players must now use /league " + newId + " for commands");
                break;
            case "renamedisplay":
                String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                league.setName(newName);
                manager.saveLeague(league);
                sender.sendMessage("§aRenamed league display name to '" + newName + "'");
                break;
            default:
                sender.sendMessage("§cUnknown setting: " + setting);
                sender.sendMessage("§cAvailable: maxteamownership, maxdriversperteam, maxreservesperteam, pointsystem, strikes, fastestlap, primarycolor, secondarycolor, allowanyonecreateteam, renameid, renamedisplay");
        }
    }
}