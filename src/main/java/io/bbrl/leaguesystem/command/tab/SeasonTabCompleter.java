package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.*;

public class SeasonTabCompleter implements TabSubcommand {

    private final LeagueManager manager;

    public SeasonTabCompleter(LeagueManager manager) { this.manager = manager; }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return manager.allLeagues().stream().map(l -> l.getId()).toList();
        if (args.length == 2) { // <league>  ->  new | existing season names
            List<String> out = new ArrayList<>();
            out.add("new");
            manager.getLeague(args[0]).ifPresent(league ->
                    league.getSeasons().keySet().forEach(out::add));
            return out;
        }
        // we now know args[1] is either "new" or a season name
        boolean isNew = "new".equalsIgnoreCase(args[1]);
        if (isNew) {
            if (args.length == 3) return List.of("<seasonName>");
            if (args.length == 4) return List.of("[raceCount|Unknown]");
            return List.of();
        }
        // existing season branch
        if (args.length == 3) return List.of("delete", "edit", "race", "driver", "standings");
        String sub = args[2].toLowerCase();
        switch (sub) {
            case "delete", "edit" -> {
                if (sub.equals("edit") && args.length == 4) return List.of("raceCount");
                if (sub.equals("edit") && args.length >= 5) return List.of("<freeText>");
                return List.of();
            }
            case "driver" -> {
                if (args.length == 4) return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).toList();
                if (args.length == 5) return List.of("+10", "-5");
                return List.of();
            }
            case "standings" -> {
                if (args.length == 4) return List.of("individual", "teams");
                return List.of();
            }
            case "race" -> {
                if (args.length == 4) return List.of("add", "delete", "edit", "results", "reload");
                if (args.length == 5) {
                    String racSub = args[4].toLowerCase();
                    switch (racSub) {
                        case "add":
                            if (args.length == 5) return List.of("<raceName>");
                            if (args.length == 6) return List.of("[raceNumber]");
                            return List.of();
                        case "delete", "results", "reload":
                            return listRacesNames(args[0], args[1]);
                        case "edit":
                            if (args.length == 5) return listRacesNames(args[0], args[1]);
                            if (args.length == 6) return List.of("standings");
                            if (args.length == 7) return Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).toList();
                            if (args.length == 8) return List.of("<position|0>");
                            return List.of();
                        default:
                            return List.of();
                    }
                }
                return List.of();
            }
            default -> List.of();
        }
        return List.of();
    }

    /* helper to list races for tab */
    private List<String> listRacesNames(String leagueId, String seasonName) {
        return manager.getLeague(leagueId)
                .map(l -> {
                    File seasonDir = new File(manager.getStorage().leaguesRoot, l.getId() + "/seasons/" + seasonName + "/races");
                    if (!seasonDir.exists()) return List.<String>of();
                    String[] names = seasonDir.list((d, name) -> name.endsWith(".yml"));
                    if (names == null) return List.<String>of();
                    return Arrays.stream(names).map(n -> n.replace(".yml", "")).toList();
                })
                .orElse(List.of());
    }
}