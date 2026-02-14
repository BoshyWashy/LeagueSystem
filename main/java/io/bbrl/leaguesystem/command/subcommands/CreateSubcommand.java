package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.LeagueConfig;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public CreateSubcommand(LeagueManager manager) { this.manager = manager; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league create <id> [display name]");
            return;
        }

        // Check permission for creating leagues
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") && !p.hasPermission("league.createleague")) {
                sender.sendMessage("§cYou need permission 'league.createleague' or 'league.op' to create leagues");
                return;
            }
        }

        String id = args[0].toLowerCase();
        String name = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : id;

        /* owner string:  username | uuid  (uuid always present) */
        String owner;
        if (sender instanceof Player p) {
            owner = p.getName() + " | " + p.getUniqueId();
        } else {
            owner = "Console | console";
        }

        /* build league object */
        LeagueConfig config = new LeagueConfig();
        config.setOwner(owner);
        League league = new League(id, name, config);

        /* let storage write BOTH index and league.yml */
        if (manager.getStorage().getLeague(id).isPresent()) {
            sender.sendMessage("§cLeague already exists");
            return;
        }
        manager.getStorage().saveLeague(league);

        // Grant the creator league owner permission automatically
        if (sender instanceof Player p) {
            sender.sendMessage("§aYou have been granted league owner permissions for this league");
            sender.sendMessage("§7Permission: league.leagueowner." + id);
        }

        sender.sendMessage("§aLeague " + name + " created with id " + id);
    }
}