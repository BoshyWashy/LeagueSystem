package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Team;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.*;

public class TeamTabCompleter implements TabSubcommand {

    private final LeagueManager manager;
    private final League league;

    public TeamTabCompleter(LeagueManager manager, League league) {
        this.manager = manager;
        this.league = league;
    }

    private static final List<String> COLOURS = List.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("create");
            out.add("list");

            Map<String, io.bbrl.leaguesystem.model.Team> teams = manager.getStorage().loadTeams(league);
            out.addAll(teams.keySet());

            return out.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        String token = args[0].toLowerCase();

        if (token.equals("create")) {
            if (args.length == 2) return List.of("<Name>");
            if (args.length == 3) {
                return COLOURS.stream()
                        .filter(c -> c.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
            return List.of();
        }

        if (token.equals("list")) {
            return List.of();
        }

        String teamId = token;

        if (manager.getTeam(league, teamId).isEmpty()) {
            return List.of();
        }

        if (args.length == 2) {
            return List.of("invite", "join", "leave", "info", "option", "delete", "reorder", "kick", "transferownership").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "invite" -> {
                if (args.length == 3) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(p -> p.getName())
                            .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                if (args.length == 4) return List.of("main", "reserve", "co-owner");
                return List.of();
            }
            case "leave" -> {
                if (args.length == 3) return List.of("owner");
                return List.of();
            }
            case "option" -> {
                if (args.length == 3) {
                    return List.of("rename", "renameid", "colour").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                if (args.length == 4) {
                    String sub = args[2].toLowerCase();
                    if (sub.equals("rename")) return List.of("<newName>");
                    if (sub.equals("renameid")) return List.of("<new-id>");
                    if (sub.equals("colour")) {
                        return COLOURS.stream()
                                .filter(c -> c.toLowerCase().startsWith(args[3].toLowerCase()))
                                .toList();
                    }
                }
                return List.of();
            }
            case "delete" -> {
                if (args.length == 3) return List.of("confirm");
                return List.of();
            }
            case "reorder" -> {
                if (args.length == 3) {
                    List<String> positions = new ArrayList<>();
                    int maxMain = league.getConfig().getMaxDriversPerTeam();
                    int maxReserve = league.getConfig().getMaxReservesPerTeam();

                    for (int i = 1; i <= maxMain; i++) {
                        positions.add("Main-" + i);
                    }
                    for (int i = 1; i <= maxReserve; i++) {
                        positions.add("Reserve-" + i);
                    }
                    return positions.stream()
                            .filter(p -> p.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                if (args.length == 4) {
                    return manager.getTeam(league, teamId)
                            .map(t -> {
                                List<String> names = new ArrayList<>();
                                for (String uuid : t.getMembers()) {
                                    if (!uuid.equals(t.getOwnerUuid())) {
                                        String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                        if (name != null) names.add(name);
                                    }
                                }
                                for (String uuid : t.getReserves()) {
                                    if (!uuid.equals(t.getOwnerUuid())) {
                                        String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                        if (name != null) names.add(name);
                                    }
                                }
                                return names;
                            })
                            .orElse(List.of());
                }
                return List.of();
            }
            case "kick" -> {
                if (args.length == 3) {
                    return manager.getTeam(league, teamId)
                            .map(t -> {
                                List<String> names = new ArrayList<>();
                                for (String uuid : t.getMembers()) {
                                    String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                    if (name != null) names.add(name);
                                }
                                for (String uuid : t.getReserves()) {
                                    String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                    if (name != null) names.add(name);
                                }
                                for (String uuid : t.getOwners()) {
                                    if (!uuid.equals(t.getOwnerUuid())) {
                                        String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                        if (name != null && !names.contains(name)) names.add(name);
                                    }
                                }
                                return names.stream()
                                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                                        .toList();
                            })
                            .orElse(List.of());
                }
                return List.of();
            }
            case "transferownership" -> {
                if (args.length == 3) {
                    return manager.getTeam(league, teamId)
                            .map(t -> {
                                List<String> names = new ArrayList<>();
                                for (String uuid : t.getOwners()) {
                                    if (!uuid.equals(t.getOwnerUuid())) {
                                        String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                                        if (name != null) names.add(name);
                                    }
                                }
                                return names.stream()
                                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                                        .toList();
                            })
                            .orElse(List.of());
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }
}