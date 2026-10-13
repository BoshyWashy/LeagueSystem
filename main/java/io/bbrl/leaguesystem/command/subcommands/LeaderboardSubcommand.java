package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.Leaderboard;
import io.bbrl.leaguesystem.model.LeaderboardType;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeaderboardManager;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class LeaderboardSubcommand implements LeagueCommand.Subcommand {

    private final LeagueManager manager;
    private final LeaderboardManager leaderboardManager;

    public LeaderboardSubcommand(LeagueManager manager, LeaderboardManager leaderboardManager) {
        this.manager = manager;
        this.leaderboardManager = leaderboardManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league <league> leaderboard <add|remove|edit|move|refresh> ...");
            return;
        }

        String leagueId = args[0];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }
        League league = ol.get();

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> handleAdd(sender, league, args);
            case "remove" -> handleRemove(sender, league, args);
            case "edit" -> handleEdit(sender, league, args);
            case "move" -> handleMove(sender, league, args);
            case "refresh" -> handleRefresh(sender, league, args);
            default -> sender.sendMessage("§cUnknown action. Use: add, remove, edit, move, refresh");
        }
    }

    private void handleAdd(CommandSender sender, League league, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can create leaderboards");
            return;
        }

        // Check permission
        boolean isLeagueOwner = p.hasPermission("league.leagueowner." + league.getId());
        boolean isOp = p.hasPermission("league.op");

        if (!isLeagueOwner && !isOp) {
            sender.sendMessage("§cYou do not have permission to create leaderboards");
            return;
        }

        if (args.length < 5) {
            sender.sendMessage("§cUsage: /league <league> leaderboard add <name> <type> <season> [race]");
            sender.sendMessage("§cTypes: teamsChampionship, driversChampionship, raceResults");
            sender.sendMessage("§cSeason: Use 'currentSeason' for the active season");
            return;
        }

        String name = args[2];
        String typeStr = args[3].toLowerCase();
        String seasonKey = args[4];
        String raceName = args.length > 5 ? args[5] : null;

        // Handle currentSeason keyword
        if (seasonKey.equalsIgnoreCase("currentSeason")) {
            if (league.getCurrentSeason() == null) {
                sender.sendMessage("§cNo current season is set for this league");
                return;
            }
            seasonKey = league.getCurrentSeason();
        }

        // Validate season exists
        if (league.getSeason(seasonKey) == null) {
            sender.sendMessage("§cSeason not found: " + seasonKey);
            sender.sendMessage("§cAvailable: " + String.join(", ", league.getSeasons().keySet()));
            return;
        }

        LeaderboardType type;
        try {
            type = switch (typeStr) {
                case "teamschampionship" -> LeaderboardType.TEAMS_CHAMPIONSHIP;
                case "driverschampionship" -> LeaderboardType.DRIVERS_CHAMPIONSHIP;
                case "raceresults" -> LeaderboardType.RACE_RESULTS;
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid type. Use: teamsChampionship, driversChampionship, raceResults");
            return;
        }

        // Validate race exists for race results
        if (type == LeaderboardType.RACE_RESULTS) {
            if (raceName == null) {
                sender.sendMessage("§cRace name required for raceResults type");
                return;
            }
            if (!manager.getStorage().listRaces(league, seasonKey).contains(raceName)) {
                sender.sendMessage("§cRace not found: " + raceName);
                return;
            }
        }

        // Generate ID from name (sanitized)
        String id = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (id.isEmpty()) id = "leaderboard";

        // Ensure unique ID
        String baseId = id;
        int counter = 1;
        while (leaderboardManager.leaderboardExists(league, id)) {
            id = baseId + counter;
            counter++;
            if (counter > 100) {
                sender.sendMessage("§cCould not generate unique leaderboard ID");
                return;
            }
        }

        // Get location with Y offset
        Location loc = p.getLocation().clone();
        double yOffset = league.getConfig().getHologramYOffset();
        loc.setY(loc.getY() + yOffset);

        leaderboardManager.createLeaderboard(league, id, name, type, seasonKey, raceName, loc, p.getUniqueId());

        sender.sendMessage("§aLeaderboard '" + name + "' created!");
        sender.sendMessage("§7Type: " + typeStr + ", Season: " + seasonKey + (raceName != null ? ", Race: " + raceName : ""));
    }

    private void handleRemove(CommandSender sender, League league, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league <league> leaderboard remove <name>");
            return;
        }

        String name = args[2];
        String id = findLeaderboardIdByName(league, name);

        if (id == null) {
            sender.sendMessage("§cLeaderboard not found: " + name);
            return;
        }

        Optional<Player> p = sender instanceof Player ? Optional.of((Player) sender) : Optional.empty();
        boolean isLeagueOwner = p.map(player -> player.hasPermission("league.leagueowner." + league.getId())).orElse(false);
        boolean isOp = p.map(player -> player.hasPermission("league.op")).orElse(false);

        // Check ownership
        if (p.isPresent() && !isLeagueOwner && !isOp) {
            var lb = leaderboardManager.getLeaderboard(league, id);
            if (lb.isPresent() && !lb.get().getCreatorUuid().equals(p.get().getUniqueId())) {
                sender.sendMessage("§cYou can only remove leaderboards you created");
                return;
            }
        }

        leaderboardManager.removeLeaderboard(league, id);
        sender.sendMessage("§aLeaderboard '" + name + "' removed!");
    }

    private void handleEdit(CommandSender sender, League league, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can edit leaderboards");
            return;
        }

        boolean isLeagueOwner = p.hasPermission("league.leagueowner." + league.getId());
        boolean isOp = p.hasPermission("league.op");

        if (!isLeagueOwner && !isOp) {
            sender.sendMessage("§cYou do not have permission to edit leaderboards");
            return;
        }

        if (args.length < 6) {
            sender.sendMessage("§cUsage: /league <league> leaderboard edit <name> <type> <season> [race]");
            sender.sendMessage("§cTypes: teamsChampionship, driversChampionship, raceResults");
            sender.sendMessage("§cSeason: Use 'currentSeason' for the active season");
            return;
        }

        String name = args[2];
        String id = findLeaderboardIdByName(league, name);

        if (id == null) {
            sender.sendMessage("§cLeaderboard not found: " + name);
            return;
        }

        String typeStr = args[3].toLowerCase();
        String seasonKey = args[4];
        String raceName = args.length > 5 ? args[5] : null;

        // Handle currentSeason keyword
        if (seasonKey.equalsIgnoreCase("currentSeason")) {
            if (league.getCurrentSeason() == null) {
                sender.sendMessage("§cNo current season is set for this league");
                return;
            }
            seasonKey = league.getCurrentSeason();
        }

        // Validate season exists
        if (league.getSeason(seasonKey) == null) {
            sender.sendMessage("§cSeason not found: " + seasonKey);
            return;
        }

        LeaderboardType type;
        try {
            type = switch (typeStr) {
                case "teamschampionship" -> LeaderboardType.TEAMS_CHAMPIONSHIP;
                case "driverschampionship" -> LeaderboardType.DRIVERS_CHAMPIONSHIP;
                case "raceresults" -> LeaderboardType.RACE_RESULTS;
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid type. Use: teamsChampionship, driversChampionship, raceResults");
            return;
        }

        // Validate race exists for race results
        if (type == LeaderboardType.RACE_RESULTS && raceName != null) {
            if (!manager.getStorage().listRaces(league, seasonKey).contains(raceName)) {
                sender.sendMessage("§cRace not found: " + raceName);
                return;
            }
        }

        leaderboardManager.editLeaderboard(league, id, type, seasonKey, raceName);
        sender.sendMessage("§aLeaderboard '" + name + "' updated!");
    }

    private void handleMove(CommandSender sender, League league, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can move leaderboards");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league <league> leaderboard move <name>");
            return;
        }

        String name = args[2];
        String id = findLeaderboardIdByName(league, name);

        if (id == null) {
            sender.sendMessage("§cLeaderboard not found: " + name);
            return;
        }

        boolean isLeagueOwner = p.hasPermission("league.leagueowner." + league.getId());
        boolean isOp = p.hasPermission("league.op");

        if (!isLeagueOwner && !isOp) {
            // Check ownership
            var lb = leaderboardManager.getLeaderboard(league, id);
            if (lb.isEmpty() || !lb.get().getCreatorUuid().equals(p.getUniqueId())) {
                sender.sendMessage("§cYou can only move leaderboards you created");
                return;
            }
        }

        leaderboardManager.moveLeaderboard(p, league, id);
    }

    private void handleRefresh(CommandSender sender, League league, String[] args) {
        boolean isLeagueOwner = sender instanceof Player p && p.hasPermission("league.leagueowner." + league.getId());
        boolean isOp = sender instanceof Player p && p.hasPermission("league.op");

        if (args.length < 3) {
            // Refresh all leaderboards
            if (!isLeagueOwner && !isOp) {
                sender.sendMessage("§cYou do not have permission to refresh all leaderboards");
                return;
            }
            leaderboardManager.refreshAllLeaderboards(league);
            sender.sendMessage("§aAll leaderboards refreshed!");
        } else {
            String name = args[2];

            if (name.equalsIgnoreCase("all")) {
                if (!isLeagueOwner && !isOp) {
                    sender.sendMessage("§cYou do not have permission to refresh all leaderboards");
                    return;
                }
                leaderboardManager.refreshAllLeaderboards(league);
                sender.sendMessage("§aAll leaderboards refreshed!");
                return;
            }

            String id = findLeaderboardIdByName(league, name);

            if (id == null) {
                sender.sendMessage("§cLeaderboard not found: " + name);
                return;
            }

            // Check ownership for single refresh
            if (sender instanceof Player p && !isLeagueOwner && !isOp) {
                var lb = leaderboardManager.getLeaderboard(league, id);
                if (lb.isEmpty() || !lb.get().getCreatorUuid().equals(p.getUniqueId())) {
                    sender.sendMessage("§cYou can only refresh leaderboards you created");
                    return;
                }
            }

            leaderboardManager.refreshLeaderboard(league, id);
            sender.sendMessage("§aLeaderboard '" + name + "' refreshed!");
        }
    }

    /**
     * Find a leaderboard ID by its display name (case-insensitive)
     */
    private String findLeaderboardIdByName(League league, String name) {
        for (Leaderboard lb : leaderboardManager.getLeaderboards(league).values()) {
            if (lb.getName().equalsIgnoreCase(name)) {
                return lb.getId();
            }
        }
        // Also try matching by ID directly (for backwards compatibility)
        if (leaderboardManager.leaderboardExists(league, name.toLowerCase().replaceAll("[^a-z0-9]", ""))) {
            return name.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        return null;
    }
}