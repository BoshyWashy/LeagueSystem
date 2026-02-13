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
        String pri = l.getConfig().getPrimaryColor();
        String sec = l.getConfig().getSecondaryColor();

        sender.sendMessage(pri + "==============================");
        sender.sendMessage(sec + " " + l.getName());
        sender.sendMessage(pri + " League ID: [" + sec + l.getId() + pri + "]");
        sender.sendMessage(pri + "==============================");
        sender.sendMessage(pri + "Teams: " + sec + manager.getStorage().loadTeams(l).size());
        sender.sendMessage(pri + "Races: " + sec + manager.getStorage().listRaces(l, l.getCurrentSeason()).size());
        sender.sendMessage(pri + "Seasons: " + sec + l.getSeasons().size());
        sender.sendMessage(pri + "==============================");
        sender.sendMessage(pri + "Point system: " + sec + l.getConfig().getPointSystem());
        sender.sendMessage(pri + "Primary Color: " + sec + "■ " + pri + "(" + sec + l.getConfig().getPrimaryColor().replace("§", "&") + pri + ")");
        sender.sendMessage(pri + "Secondary Color: " + sec + "■ " + pri + "(" + sec + l.getConfig().getSecondaryColor().replace("§", "&") + pri + ")");
        sender.sendMessage(pri + "Standings [§l>§r" + pri + "]");
        sender.sendMessage(pri + "==============================");
    }
}