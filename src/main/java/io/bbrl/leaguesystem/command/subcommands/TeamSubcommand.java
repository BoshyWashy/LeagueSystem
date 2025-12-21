package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Team;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;

public class TeamSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public TeamSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league team <create|invite|join|leave|info|leaderboard> ...");
            return;
        }
        String action = args[0].toLowerCase();
        switch (action) {
            case "create":
                handleCreate(sender, args);
                break;
            case "invite":
                handleInvite(sender, args);
                break;
            case "join":
                handleJoin(sender, args);
                break;
            case "leave":
                handleLeave(sender, args);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "leaderboard":
                handleLeaderboard(sender, args);
                break;
            default:
                sender.sendMessage("§cUnknown team action");
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can create teams");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /league team create <league> <teamId> <colorHex>");
            return;
        }
        String leagueId = args[1];
        String teamId = args[2].toLowerCase();
        String hex = args[3];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        Player p = (Player) sender;
        String ownerUuid = p.getUniqueId().toString();
        if (!manager.canCreateTeam(league, ownerUuid)) {
            sender.sendMessage("§cYou cannot own more teams in this league");
            return;
        }
        Team t = manager.createTeam(league, teamId, teamId, hex, ownerUuid);
        if (t == null) {
            sender.sendMessage("§cError creating team");
            return;
        }
        sender.sendMessage("§aTeam created: " + t.getName());
    }

    private void handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can invite");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /league team invite <league> <teamId> <player> [reserve]");
            return;
        }
        String leagueId = args[1];
        String teamId = args[2].toLowerCase();
        String targetName = args[3];
        boolean asReserve = args.length > 4 && args[4].equalsIgnoreCase("reserve");
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        Optional<Team> ot = manager.getTeam(league, teamId);
        if (ot.isEmpty()) {
            sender.sendMessage("§cTeam not found");
            return;
        }
        Team team = ot.get();
        Player p = (Player) sender;
        if (!p.getUniqueId().toString().equals(team.getOwnerUuid()) && !p.hasPermission("bbrl." + leagueId + ".manage")) {
            sender.sendMessage("§cOnly the team owner or league manager can invite");
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getName() == null) {
            sender.sendMessage("§cPlayer not found");
            return;
        }
        String targetUuid = target.getUniqueId().toString();
        if (manager.isPlayerInLeagueTeam(league, targetUuid)) {
            sender.sendMessage("§cPlayer already in a team for this league");
            return;
        }
        manager.invitePlayer(league, team, targetUuid);
        sender.sendMessage("§aInvited " + target.getName() + " §ato team " + team.getName());

        if (target.isOnline()) {
            ((Player) target.getPlayer()).sendMessage("§6" + team.getName() + " has invited you to join their team in " + league.getName()
                    + " §a§l/league team join " + teamId);
        } else {
            league.getTeamInvites().computeIfAbsent(team.getId(), k -> new HashSet<>()).add(targetUuid);
            manager.saveLeague(league);
        }
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can join teams");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league team join <league> <teamId>");
            return;
        }
        String leagueId = args[1];
        String teamId = args[2].toLowerCase();
        Player p = (Player) sender;
        String uuid = p.getUniqueId().toString();

        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        Optional<Team> ot = manager.getTeam(league, teamId);
        if (ot.isEmpty()) {
            sender.sendMessage("§cTeam not found");
            return;
        }
        Team team = ot.get();

        if (manager.isPlayerInLeagueTeam(league, uuid)) {
            sender.sendMessage("§cYou are already in a team");
            return;
        }
        if (!manager.consumeInvite(league, teamId, uuid)) {
            sender.sendMessage("§cSorry, but you haven't been invited to this team.");
            return;
        }

        if (team.getMembers().size() < league.getConfig().getMaxDriversPerTeam()) {
            team.getMembers().add(uuid);
        } else if (team.getReserves().size() < league.getConfig().getMaxReservesPerTeam()) {
            team.getReserves().add(uuid);
        } else {
            sender.sendMessage("§cTeam is full");
            return;
        }
        manager.saveLeague(league);
        sender.sendMessage("§aJoined team " + team.getName());
    }

    private void handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can leave teams");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league team leave <league>");
            return;
        }
        String leagueId = args[1];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        Player p = (Player) sender;
        boolean ok = manager.leaveTeam(ol.get(), p.getUniqueId().toString());
        if (!ok) sender.sendMessage("§cYou are not in a team");
        else sender.sendMessage("§cLeft team");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league team info <league> [teamId]");
            return;
        }
        String leagueId = args[1];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        if (args.length == 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify teamId");
                return;
            }
            Player p = (Player) sender;
            Optional<Team> ot = manager.getPlayerTeam(league, p.getUniqueId().toString());
            if (ot.isEmpty()) {
                sender.sendMessage("§cYou are not in a team for this league");
                return;
            }
            Team t = ot.get();
            sender.sendMessage("§aTeam Name: " + t.getName() + " Colour: " + t.getHexColor());
            sender.sendMessage("§aOwner: " + Bukkit.getOfflinePlayer(UUID.fromString(t.getOwnerUuid())).getName());
            sender.sendMessage("§aMains:");
            for (String m : t.getMembers())
                sender.sendMessage("- " + Bukkit.getOfflinePlayer(UUID.fromString(m)).getName());
            sender.sendMessage("§aReserves:");
            for (String r : t.getReserves())
                sender.sendMessage("- " + Bukkit.getOfflinePlayer(UUID.fromString(r)).getName());
        } else {
            String teamId = args[2];
            Optional<Team> ot = manager.getTeam(league, teamId);
            if (ot.isEmpty()) {
                sender.sendMessage("§cTeam not found");
                return;
            }
            Team t = ot.get();
            sender.sendMessage("§aTeam Name: " + t.getName() + " Color: " + t.getHexColor());
            sender.sendMessage("§aOwner: " + Bukkit.getOfflinePlayer(UUID.fromString(t.getOwnerUuid())).getName());
            sender.sendMessage("§aMains:");
            for (String m : t.getMembers())
                sender.sendMessage("- " + Bukkit.getOfflinePlayer(UUID.fromString(m)).getName());
            sender.sendMessage("§aReserves:");
            for (String r : t.getReserves())
                sender.sendMessage("- " + Bukkit.getOfflinePlayer(UUID.fromString(r)).getName());
        }
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league team leaderboard <league>");
            return;
        }
        String leagueId = args[1];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        league.getTeamPoints().entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .forEach(e -> sender.sendMessage(e.getKey() + " - " + e.getValue()));
    }
}