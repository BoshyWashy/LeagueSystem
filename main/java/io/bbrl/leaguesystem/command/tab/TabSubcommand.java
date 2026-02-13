package io.bbrl.leaguesystem.command.tab;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface TabSubcommand {
    List<String> onTabComplete(CommandSender sender, String[] args);
}