package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        sender.sendMessage(pri + "| " + "Teams: " + sec + manager.getStorage().loadTeams(l).size() + pri + " | " + "Races: " + sec + manager.getStorage().listRaces(l, l.getCurrentSeason()).size() + pri + " | " + "Seasons: " + sec + l.getSeasons().size() + pri + " |");
        sender.sendMessage(pri + "==============================");
        sender.sendMessage(pri + "Point system: " + sec + l.getConfig().getPointSystem());
        sender.sendMessage(pri + "Primary: " + "(" + sec + l.getConfig().getPrimaryColor().replace("§", "&") + pri + ")" + sec + " | " + pri + "Secondary: " + "(" + sec + l.getConfig().getSecondaryColor().replace("§", "&") + pri + ")");

        if (sender instanceof Player player) {
            TextComponent standingsMsg = new TextComponent(pri + "Standings [§l>§r" + pri + "] (Click to view)");
            standingsMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/league " + id + " season " + l.getCurrentSeason() + " standings individual"));
            player.spigot().sendMessage(standingsMsg);
        } else {
            sender.sendMessage(pri + "Standings [§l>§r" + pri + "]");
        }

        sender.sendMessage(pri + "==============================");

    }
}