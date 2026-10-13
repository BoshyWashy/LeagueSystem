package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.model.Leaderboard;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeaderboardManager;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LeaderboardTabCompleter implements TabSubcommand {

    private final LeagueManager manager;
    private final LeaderboardManager leaderboardManager;
    private final League league;
    private final CommandSender sender;

    public LeaderboardTabCompleter(LeagueManager manager, LeaderboardManager leaderboardManager, League league, CommandSender sender) {
        this.manager = manager;
        this.leaderboardManager = leaderboardManager;
        this.league = league;
        this.sender = sender;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        boolean canManage = !(sender instanceof Player) ||
                ((Player)sender).hasPermission("league.op") ||
                ((Player)sender).hasPermission("league.leagueowner." + league.getId());

        // /league <league> leaderboard <action> ...

        if (args.length == 1) {
            List<String> actions = new ArrayList<>();
            if (canManage) {
                actions.addAll(List.of("add", "remove", "edit", "move"));
            }
            actions.add("refresh");
            return actions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add" -> {
                if (!canManage) return List.of();
                if (args.length == 2) {
                    return List.of("<customName>");
                }
                if (args.length == 3) {
                    return List.of("teamsChampionship", "driversChampionship", "raceResults").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                if (args.length == 4) {
                    // Season list - include currentSeason option
                    List<String> seasons = new ArrayList<>();
                    seasons.add("currentSeason");
                    seasons.addAll(league.getSeasons().keySet());
                    return seasons.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .toList();
                }
                if (args.length == 5) {
                    // Check if type is raceResults
                    if (args[2].equalsIgnoreCase("raceResults")) {
                        String season = args[3];
                        // Handle currentSeason keyword
                        if (season.equalsIgnoreCase("currentSeason")) {
                            season = league.getCurrentSeason();
                        }
                        if (season != null) {
                            return manager.getStorage().listRaces(league, season).stream()
                                    .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                    .toList();
                        }
                    }
                }
                return List.of();
            }

            case "remove", "move" -> {
                if (!canManage) return List.of();
                if (args.length == 2) {
                    // Show leaderboard names (not IDs)
                    return leaderboardManager.getLeaderboards(league).values().stream()
                            .map(Leaderboard::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                return List.of();
            }

            case "edit" -> {
                if (!canManage) return List.of();
                if (args.length == 2) {
                    // Show leaderboard names (not IDs)
                    return leaderboardManager.getLeaderboards(league).values().stream()
                            .map(Leaderboard::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                if (args.length == 3) {
                    return List.of("teamsChampionship", "driversChampionship", "raceResults").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                if (args.length == 4) {
                    // Season list - include currentSeason option
                    List<String> seasons = new ArrayList<>();
                    seasons.add("currentSeason");
                    seasons.addAll(league.getSeasons().keySet());
                    return seasons.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .toList();
                }
                if (args.length == 5) {
                    if (args[2].equalsIgnoreCase("raceResults")) {
                        String season = args[3];
                        // Handle currentSeason keyword
                        if (season.equalsIgnoreCase("currentSeason")) {
                            season = league.getCurrentSeason();
                        }
                        if (season != null) {
                            return manager.getStorage().listRaces(league, season).stream()
                                    .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                    .toList();
                        }
                    }
                }
                return List.of();
            }

            case "refresh" -> {
                if (args.length == 2) {
                    List<String> options = new ArrayList<>();
                    if (canManage) {
                        options.add("all");
                    }
                    // Show leaderboard names (not IDs)
                    options.addAll(leaderboardManager.getLeaderboards(league).values().stream()
                            .map(Leaderboard::getName)
                            .toList());
                    return options.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                return List.of();
            }

            default -> {
                return List.of();
            }
        }
    }
}