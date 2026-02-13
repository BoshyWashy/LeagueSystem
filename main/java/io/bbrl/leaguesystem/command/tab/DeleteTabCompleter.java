package io.bbrl.leaguesystem.command.tab;

import org.bukkit.command.CommandSender;
import io.bbrl.leaguesystem.command.tab.*;   // added

import java.util.List;

public class DeleteTabCompleter implements TabSubcommand {
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) return List.of("confirm");
        return List.of();
    }
}