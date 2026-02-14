package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.*;

public class SeasonTabCompleter implements TabSubcommand {

    private final LeagueManager manager;
    private final League league;

    public SeasonTabCompleter(LeagueManager manager, League league) {
        this.manager = manager;
        this.league = league;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("new");
            out.addAll(league.getSeasons().keySet());
            return out.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        boolean isNew = "new".equalsIgnoreCase(args[0]);

        if (isNew) {
            if (args.length == 2) return List.of("<seasonName>");
            if (args.length == 3) return List.of("[raceCount|Unknown]");
            return List.of();
        }

        String seasonName = args[0];

        if (league.getSeason(seasonName) == null) {
            return List.of();
        }

        if (args.length == 2) {
            return List.of("delete", "edit", "race", "driver", "standings").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "delete" -> {
                return List.of();
            }
            case "edit" -> {
                if (args.length == 3) return List.of("raceCount");
                if (args.length >= 4) return List.of("<AmountOfRaces>");
                return List.of();
            }
            case "driver" -> {
                if (args.length == 3) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(p -> p.getName())
                            .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                if (args.length == 4) return List.of("<points>");
                return List.of();
            }
            case "standings" -> {
                if (args.length == 3) {
                    return List.of("individual", "teams").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                return List.of();
            }
            case "race" -> {
                if (args.length == 3) {
                    return List.of("add", "delete", "edit", "results").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }

                String raceSub = args[2].toLowerCase();

                switch (raceSub) {
                    case "add" -> {
                        if (args.length == 4) return List.of("<raceName>");
                        return List.of();
                    }
                    case "delete", "results" -> {
                        if (args.length == 4) {
                            return manager.getStorage().listRaces(league, seasonName);
                        }
                        return List.of();
                    }
                    case "edit" -> {
                        if (args.length == 4) {
                            return manager.getStorage().listRaces(league, seasonName);
                        }
                        if (args.length == 5) {
                            return List.of("standings", "standings-team", "fastestlap");
                        }
                        if (args.length == 6) {
                            String subSub = args[4].toLowerCase();
                            if (subSub.equals("standings")) {
                                return Bukkit.getOnlinePlayers().stream()
                                        .map(p -> p.getName())
                                        .filter(n -> n.toLowerCase().startsWith(args[5].toLowerCase()))
                                        .toList();
                            } else if (subSub.equals("standings-team")) {
                                return manager.getStorage().loadTeams(league).keySet().stream().toList();
                            } else if (subSub.equals("fastestlap")) {
                                // For fastestlap, suggest players and "clear"
                                List<String> suggestions = new ArrayList<>();
                                suggestions.add("clear");
                                suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                                        .map(p -> p.getName())
                                        .toList());
                                return suggestions.stream()
                                        .filter(n -> n.toLowerCase().startsWith(args[5].toLowerCase()))
                                        .toList();
                            }
                        }
                        return List.of();
                    }
                    default -> {
                        return List.of();
                    }
                }
            }
            default -> {
                return List.of();
            }
        }
    }
}