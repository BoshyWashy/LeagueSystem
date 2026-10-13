package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class SeasonTabCompleter implements TabSubcommand {

    private final LeagueManager manager;
    private final League league;
    private final CommandSender sender;

    public SeasonTabCompleter(LeagueManager manager, League league, CommandSender sender) {
        this.manager = manager;
        this.league = league;
        this.sender = sender;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        boolean canManage = !(sender instanceof Player) ||
                ((Player)sender).hasPermission("league.op") ||
                ((Player)sender).hasPermission("league.leagueowner." + league.getId());

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            if (canManage) {
                out.add("new");
            }
            out.addAll(league.getSeasons().keySet());
            return out.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        boolean isNew = "new".equalsIgnoreCase(args[0]);

        if (isNew) {
            if (!canManage) return List.of();
            if (args.length == 2) return List.of("<seasonName>");
            if (args.length == 3) return List.of("[raceCount|Unknown]");
            return List.of();
        }

        String seasonName = args[0];

        if (league.getSeason(seasonName) == null) {
            return List.of();
        }

        if (args.length == 2) {
            List<String> actions = new ArrayList<>();
            if (canManage) {
                actions.addAll(List.of("delete", "rename", "edit", "finish", "setcurrent", "race", "driver", "calendar"));
            }
            actions.add("standings");
            return actions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "delete" -> {
                return canManage ? List.of() : List.of();
            }
            case "rename" -> {
                if (!canManage) return List.of();
                if (args.length == 3) return List.of("<new-name>");
                return List.of();
            }
            case "finish" -> {
                if (!canManage) return List.of();
                if (args.length == 3) return List.of("undo");
                return List.of();
            }
            case "setcurrent" -> {
                return canManage ? List.of() : List.of();
            }
            case "edit" -> {
                if (!canManage) return List.of();
                if (args.length == 3) return List.of("raceCount");
                if (args.length >= 4) return List.of("<AmountOfRaces>");
                return List.of();
            }
            case "driver" -> {
                if (!canManage) return List.of();
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
                if (args.length == 4) {
                    return List.of("<page>");
                }
                return List.of();
            }
            case "calendar" -> {
                if (args.length == 3) {
                    List<String> calActions = new ArrayList<>(List.of("list"));
                    if (canManage) {
                        calActions.add("set");
                    }
                    return calActions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                String calAction = args[2].toLowerCase();
                if ("set".equals(calAction)) {
                    if (!canManage) return List.of();
                    if (args.length == 4) return List.of("<raceNumber>");
                    if (args.length >= 5) return List.of("<raceName>");
                }
                return List.of();
            }
            case "race" -> {
                if (!canManage && args.length > 3) {
                    // Allow viewing results without manage permission
                    String raceSub = args[2].toLowerCase();
                    if (!raceSub.equals("results") && !raceSub.equals("resultsteams")) {
                        return List.of();
                    }
                }

                if (args.length == 3) {
                    List<String> raceActions = new ArrayList<>();
                    if (canManage) {
                        raceActions.addAll(List.of("add", "delete", "edit", "finish"));
                    }
                    raceActions.addAll(List.of("results", "resultsTeams"));
                    return raceActions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }

                String raceSub = args[2].toLowerCase();

                switch (raceSub) {
                    case "add" -> {
                        if (!canManage) return List.of();
                        if (args.length == 4) return List.of("<raceName>");
                        if (args.length == 5) {
                            // Suggest race numbers from calendar
                            Map<Integer, String> calendar = manager.getStorage().getCalendar(league, seasonName);
                            return calendar.keySet().stream()
                                    .map(String::valueOf)
                                    .filter(s -> s.startsWith(args[4]))
                                    .toList();
                        }
                        return List.of();
                    }
                    case "delete", "results", "resultsteams" -> {
                        if (args.length == 4) {
                            return manager.getStorage().listRaces(league, seasonName).stream()
                                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                    .toList();
                        }
                        if (args.length == 5 && (raceSub.equals("results") || raceSub.equals("resultsteams"))) {
                            return List.of("<page>");
                        }
                        return List.of();
                    }
                    case "finish" -> {
                        if (!canManage) return List.of();
                        if (args.length == 4) {
                            return manager.getStorage().listRaces(league, seasonName).stream()
                                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                    .toList();
                        }
                        if (args.length == 5) {
                            return List.of("undo");
                        }
                        return List.of();
                    }
                    case "edit" -> {
                        if (!canManage) return List.of();
                        if (args.length == 4) {
                            return manager.getStorage().listRaces(league, seasonName).stream()
                                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                    .toList();
                        }
                        if (args.length == 5) {
                            return List.of("standings", "standings-team", "fastestlap", "driversTeam", "raceNumber").stream()
                                    .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                    .toList();
                        }
                        if (args.length == 6) {
                            String subSub = args[4].toLowerCase();
                            if (subSub.equals("standings")) {
                                // Collect all drivers from all races in this season
                                Set<String> allDrivers = new HashSet<>();
                                for (String raceName : manager.getStorage().listRaces(league, seasonName)) {
                                    for (String uuid : manager.getStorage().getRaceResults(league, seasonName, raceName).keySet()) {
                                        try {
                                            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                            if (name != null) allDrivers.add(name);
                                        } catch (Exception ignored) {}
                                    }
                                }
                                return allDrivers.stream()
                                        .filter(n -> n.toLowerCase().startsWith(args[5].toLowerCase()))
                                        .toList();
                            } else if (subSub.equals("standings-team")) {
                                return manager.getStorage().loadTeams(league).keySet().stream().toList();
                            } else if (subSub.equals("fastestlap")) {
                                List<String> suggestions = new ArrayList<>();
                                suggestions.add("clear");
                                suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                                        .map(p -> p.getName())
                                        .toList());
                                return suggestions.stream()
                                        .filter(n -> n.toLowerCase().startsWith(args[5].toLowerCase()))
                                        .toList();
                            } else if (subSub.equals("driversteam")) {
                                // Return all drivers with results in this race
                                if (args.length > 5) {
                                    String raceName = args[3];
                                    Map<String, Integer> results = manager.getStorage().getRaceResults(league, seasonName, raceName);
                                    List<String> driverNames = new ArrayList<>();
                                    for (String uuid : results.keySet()) {
                                        try {
                                            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                            if (name != null) driverNames.add(name);
                                        } catch (Exception ignored) {}
                                    }
                                    return driverNames.stream()
                                            .filter(n -> n.toLowerCase().startsWith(args[5].toLowerCase()))
                                            .toList();
                                }
                                return List.of();
                            } else if (subSub.equals("racenumber")) {
                                return List.of("<raceNumber>");
                            }
                        }
                        if (args.length == 7) {
                            String subSub = args[4].toLowerCase();
                            if (subSub.equals("standings-team")) {
                                return List.of("<+/-points>");
                            } else if (subSub.equals("standings")) {
                                return List.of("<position>");
                            } else if (subSub.equals("driversteam")) {
                                // Return all teams
                                List<String> teams = new ArrayList<>();
                                teams.add("none");
                                teams.addAll(manager.getStorage().loadTeams(league).keySet().stream().toList());
                                return teams.stream()
                                        .filter(t -> t.toLowerCase().startsWith(args[6].toLowerCase()))
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