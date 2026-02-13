package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

import java.util.List;

public class OptionTabCompleter implements TabSubcommand {
    private final LeagueManager manager;

    public OptionTabCompleter(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // /league <league> option <setting> <value>
        if (args.length == 1) {
            return List.of(
                            "maxteamownership", "maxdriversperteam", "maxreservesperteam",
                            "pointsystem", "strikes", "primarycolor", "secondarycolor",
                            "renameid", "renamedisplay"
                    ).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            String setting = args[0].toLowerCase();
            return switch (setting) {
                case "pointsystem" -> List.of("f1", "custom");
                case "primarycolor", "secondarycolor" -> List.of(
                        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                        "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
                );
                case "renameid" -> List.of("<new-id>");
                case "renamedisplay" -> List.of("<new-display-name>");
                default -> List.of("<value>");
            };
        }
        return List.of();
    }
}