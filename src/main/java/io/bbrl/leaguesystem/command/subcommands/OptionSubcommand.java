package io.bbrl.leaguesystem.command.subcommands;
import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Optional;
public class OptionSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public OptionSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league option <league> <setting> <value>");
            return;
        }
        String leagueId = args[0];
        String setting = args[1].toLowerCase();
        String value = args[2];

        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }
        League league = ol.get();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can modify league options.");
            return;
        }
        Player p = (Player) sender;
        String perm = "bbrl." + leagueId + ".manage";
        if (!p.hasPermission(perm)) {
            sender.sendMessage("§cYou lack permission: " + perm);
            return;
        }

        switch (setting) {
            case "maxteamownership":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setMaxTeamOwnership(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet maxTeamOwnership to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "maxdriversperteam":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setMaxDriversPerTeam(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet maxDriversPerTeam to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "maxreservesperteam":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setMaxReservesPerTeam(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet maxReservesPerTeam to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            case "pointsystem":
                league.getConfig().setPointSystem(value.toLowerCase());
                manager.saveLeague(league);
                sender.sendMessage("§aSet pointSystem to " + value.toLowerCase());
                break;
            case "strikes":
                try {
                    int v = Integer.parseInt(value);
                    league.getConfig().setStrikeRaces(v);
                    manager.saveLeague(league);
                    sender.sendMessage("§aSet strike races to " + v);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cValue must be a number");
                }
                break;
            default:
                sender.sendMessage("§cUnknown setting: " + setting);
        }
    }
}