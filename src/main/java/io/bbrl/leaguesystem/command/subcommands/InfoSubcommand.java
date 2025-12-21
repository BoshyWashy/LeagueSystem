package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class InfoSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public InfoSubcommand(LeagueManager manager) { this.manager = manager; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league info <league>");
            return;
        }
        String id = args[0];
        Optional<League> ol = manager.getLeague(id);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + id);
            return;
        }
        League l = ol.get();
        sender.sendMessage("§a---§r " + l.getName() + " §r(" + l.getId() + ") §a---");
        sender.sendMessage("§aTeams: " + l.getTeams().size());
        sender.sendMessage("§aRaces: " + l.getRaceResults().size());
        sender.sendMessage("§aPoint system: " + l.getConfig().getPointSystem());
        sender.sendMessage("§aStandings (click)");
    }
}
