package io.bbrl.leaguesystem.command.tab;

import org.bukkit.command.CommandSender;

import java.util.List;

public class InfoTabCompleter implements TabSubcommand {
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // /league <league> info - no additional arguments needed
        return List.of();
    }
}