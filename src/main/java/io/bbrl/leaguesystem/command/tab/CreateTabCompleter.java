package io.bbrl.leaguesystem.command.tab;

import org.bukkit.command.CommandSender;

import java.util.List;

public class CreateTabCompleter implements TabSubcommand {
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("<id>");
        if (args.length == 2) return List.of("[display name]");
        return List.of();
    }
}