package io.bbrl.leaguesystem.command.tab;

import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

import java.util.List;

public class StandingsTabCompleter implements TabSubcommand {
    private final LeagueManager manager;
    public StandingsTabCompleter(LeagueManager manager) { this.manager = manager; }

    /* this class is now ONLY used inside the season branch */
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // season standings:  /league season <league> <season> standings <individual|teams>
        if (args.length == 1) return List.of("individual", "teams");
        return List.of();
    }
}