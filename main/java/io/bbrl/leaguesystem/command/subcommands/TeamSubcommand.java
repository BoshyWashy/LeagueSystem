package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Team;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamSubcommand implements LeagueCommand.Subcommand {

    private final LeagueManager manager;

    public TeamSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    private static final Map<String, String> COLOUR_CODE_MAP = Map.ofEntries(
            Map.entry("black", "§0"), Map.entry("dark_blue", "§1"),
            Map.entry("dark_green", "§2"), Map.entry("dark_aqua", "§3"),
            Map.entry("dark_red", "§4"), Map.entry("dark_purple", "§5"),
            Map.entry("gold", "§6"), Map.entry("gray", "§7"),
            Map.entry("dark_gray", "§8"), Map.entry("blue", "§9"),
            Map.entry("green", "§a"), Map.entry("aqua", "§b"),
            Map.entry("red", "§c"), Map.entry("light_purple", "§d"),
            Map.entry("yellow", "§e"), Map.entry("white", "§f")
    );

    private static final Map<String, String> COLOUR_NAME_MAP = Map.ofEntries(
            Map.entry("§0", "black"), Map.entry("§1", "dark_blue"),
            Map.entry("§2", "dark_green"), Map.entry("§3", "dark_aqua"),
            Map.entry("§4", "dark_red"), Map.entry("§5", "dark_purple"),
            Map.entry("§6", "gold"), Map.entry("§7", "gray"),
            Map.entry("§8", "dark_gray"), Map.entry("§9", "blue"),
            Map.entry("§a", "green"), Map.entry("§b", "aqua"),
            Map.entry("§c", "red"), Map.entry("§d", "light_purple"),
            Map.entry("§e", "yellow"), Map.entry("§f", "white")
    );

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league <league> team <create|list|team-id> ...");
            return;
        }
        String leagueId = args[0].toLowerCase();
        String token = args[1].toLowerCase();

        League league = manager.getLeague(leagueId).orElse(null);
        if (league == null) {
            sender.sendMessage("§cLeague not found");
            return;
        }

        if (token.equals("create")) {
            handleCreate(sender, league, Arrays.copyOfRange(args, 2, args.length));
            return;
        }

        if (token.equals("list")) {
            handleList(sender, league);
            return;
        }

        String teamId = token;
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league <league> team <team-id> <invite|join|leave|info|option|delete|reorder|kick>");
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "invite" -> handleInvite(sender, league, teamId, Arrays.copyOfRange(args, 3, args.length));
            case "join" -> handleJoin(sender, league, teamId);
            case "leave" -> handleLeave(sender, league);
            case "info" -> handleInfo(sender, league, teamId);
            case "option" -> handleOption(sender, league, teamId, Arrays.copyOfRange(args, 3, args.length));
            case "delete" -> handleDelete(sender, league, teamId, Arrays.copyOfRange(args, 3, args.length));
            case "reorder" -> handleReorder(sender, league, teamId, Arrays.copyOfRange(args, 3, args.length));
            case "kick" -> handleKick(sender, league, teamId, Arrays.copyOfRange(args, 3, args.length));
            default -> sender.sendMessage("§cUnknown team action. Use: invite, join, leave, info, option, delete, reorder, kick");
        }
    }

    private void handleList(CommandSender sender, League league) {
        Map<String, Team> teams = manager.getStorage().loadTeams(league);
        if (teams.isEmpty()) {
            sender.sendMessage("§cNo teams exist in this league yet.");
            return;
        }

        sender.sendMessage(league.getConfig().getPrimaryColor() + "-- Teams in §f" + league.getName() + league.getConfig().getPrimaryColor() + " --");
        sender.sendMessage(league.getConfig().getPrimaryColor() + "=========================================");
        int count = 0;
        for (Team team : teams.values()) {
            String colorName = COLOUR_NAME_MAP.getOrDefault(team.getHexColor(), "white");
            String ownerName = getPlayerName(team.getOwnerUuid());
            sender.sendMessage((++count) + ". " + team.getChatColor() + team.getName() +
                    " §r§7(Owner: " + ownerName + ")");
        }
        sender.sendMessage(league.getConfig().getPrimaryColor() + "=========================================");
        sender.sendMessage(league.getConfig().getPrimaryColor() + "Total teams: §f" + count);
        sender.sendMessage(league.getConfig().getPrimaryColor() + "=========================================");
    }

    private void handleCreate(CommandSender sender, League league, String[] rest) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can create teams");
            return;
        }
        if (rest.length < 1) {
            sender.sendMessage("§cUsage: /league <league> team create <displayName> [colour]");
            return;
        }
        String displayName = rest[0];
        String colourWord = (rest.length > 1) ? rest[1].toLowerCase() : "white";
        String code = COLOUR_CODE_MAP.getOrDefault(colourWord, "§f");

        Player p = (Player) sender;
        String ownerUuid = p.getUniqueId().toString();
        if (!manager.canCreateTeam(league, ownerUuid)) {
            sender.sendMessage("§cYou cannot own more teams in this league");
            return;
        }

        String baseId = displayName.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (baseId.isEmpty()) baseId = "team";

        String teamId = baseId;
        int counter = 1;
        while (manager.getTeam(league, teamId).isPresent()) {
            teamId = baseId + counter;
            counter++;
            if (counter > 100) {
                sender.sendMessage("§cCould not generate unique team ID");
                return;
            }
        }

        Team t = manager.createTeam(league, teamId, displayName, code, ownerUuid);
        if (t == null) {
            sender.sendMessage("§cError creating team");
            return;
        }
        sender.sendMessage("§aTeam created: " + t.getChatColor() + t.getName());
        sender.sendMessage("§7Note: You are the owner but not automatically a driver. Use invite to add yourself.");
    }

    private void handleDelete(CommandSender sender, League league, String teamId, String[] rest) {
        if (rest.length != 1 || !rest[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§cUsage: /league <league> team <team> delete confirm");
            return;
        }
        Team team = manager.getTeam(league, teamId).orElse(null);
        if (team == null) {
            sender.sendMessage("§cTeam not found");
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        if (p == null || (!p.getUniqueId().toString().equals(team.getOwnerUuid()) && !p.hasPermission("bbrl.op"))) {
            sender.sendMessage("§cOnly the team owner or operator can delete this team");
            return;
        }

        Map<String, Team> teams = manager.getStorage().loadTeams(league);
        teams.remove(teamId);
        manager.getStorage().saveTeams(league, teams);
        sender.sendMessage("§aTeam §l" + team.getName() + "§a deleted.");
    }

    private void handleOption(CommandSender sender, League league, String teamId, String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage("§cUsage: /league <league> team <team> option <rename|colour> ...");
            return;
        }
        String sub = rest[0].toLowerCase();
        Team team = manager.getTeam(league, teamId).orElse(null);
        if (team == null) {
            sender.sendMessage("§cTeam not found");
            return;
        }
        Player p = (sender instanceof Player) ? (Player) sender : null;
        if (p == null || (!p.getUniqueId().toString().equals(team.getOwnerUuid()) && !p.hasPermission("bbrl." + league.getId() + ".manage"))) {
            sender.sendMessage("§cOnly the team owner or league manager can change team options");
            return;
        }

        switch (sub) {
            case "rename" -> {
                if (rest.length < 2) {
                    sender.sendMessage("§cUsage: /league <league> team <team> option rename <newName>");
                    return;
                }
                String newName = String.join(" ", java.util.Arrays.copyOfRange(rest, 1, rest.length));

                Map<String, Team> teams = manager.getStorage().loadTeams(league);
                boolean dup = teams.values().stream()
                        .anyMatch(t -> t.getName().equalsIgnoreCase(newName) && !t.getId().equals(teamId));
                if (dup) {
                    sender.sendMessage("§cA team with that name already exists in this league");
                    return;
                }

                team.setName(newName);
                teams.put(teamId, team);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aTeam renamed to '" + newName + "'");
            }
            case "colour", "color" -> {
                if (rest.length < 2) {
                    sender.sendMessage("§cUsage: /league <league> team <team> option colour <colour>");
                    return;
                }
                String colourWord = rest[1].toLowerCase();
                String code = COLOUR_CODE_MAP.get(colourWord);
                if (code == null) {
                    sender.sendMessage("§cUnknown colour. Choices: " + String.join(", ", COLOUR_CODE_MAP.keySet()));
                    return;
                }

                team.setHexColor(code);
                Map<String, Team> teams = manager.getStorage().loadTeams(league);
                teams.put(teamId, team);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aTeam colour updated to " + colourWord);
            }
            default -> sender.sendMessage("§cUnknown option. Use rename|colour");
        }
    }

    private void handleInvite(CommandSender sender, League league, String teamId, String[] rest) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can invite");
            return;
        }
        if (rest.length < 1) {
            sender.sendMessage("§cUsage: /league <league> team <team> invite <player> [main|reserve]");
            return;
        }
        String targetName = rest[0];
        boolean asReserve = false;
        if (rest.length > 1) {
            asReserve = rest[1].equalsIgnoreCase("reserve");
        }

        Team team = manager.getTeam(league, teamId).orElse(null);
        if (team == null) {
            sender.sendMessage("§cTeam not found");
            return;
        }
        Player p = (Player) sender;
        if (!p.getUniqueId().toString().equals(team.getOwnerUuid()) && !p.hasPermission("bbrl." + league.getId() + ".manage")) {
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

        if (manager.hasInvite(league, teamId, targetUuid)) {
            sender.sendMessage("§cPlayer already has a pending invite to this team");
            return;
        }

        manager.invitePlayer(league, team, targetUuid, target.getName(), asReserve);
        String type = asReserve ? "as reserve" : "as main driver";
        sender.sendMessage("§aInvited " + target.getName() + " " + type + " to team " + team.getName());

        if (target.isOnline()) {
            Player tp = target.getPlayer();
            String msg = "§6" + team.getName() + " has invited you to join their team in " + league.getName() + " " + type +
                    " §a§l[CLICK TO JOIN]§r §aUse: /league " + league.getId() + " team " + teamId + " join";
            tp.sendMessage(msg);
        }
    }

    private void handleJoin(CommandSender sender, League league, String teamId) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can join teams");
            return;
        }
        Player p = (Player) sender;
        String playerUuid = p.getUniqueId().toString();

        if (manager.isPlayerInLeagueTeam(league, playerUuid)) {
            sender.sendMessage("§cYou are already in a team in this league. Leave your current team first.");
            return;
        }

        if (!p.hasPermission("bbrl.op") && !manager.hasInvite(league, teamId, playerUuid)) {
            sender.sendMessage("§cUnfortunately you haven't been invited to this team yet :(");
            return;
        }

        manager.consumeInvite(league, teamId, playerUuid);
        manager.addPlayerToTeam(league, teamId, playerUuid);

        Optional<Team> joinedTeam = manager.getPlayerTeam(league, playerUuid);
        if (joinedTeam.isPresent() && joinedTeam.get().getId().equalsIgnoreCase(teamId)) {
            sender.sendMessage("§aJoined team " + joinedTeam.get().getChatColor() + joinedTeam.get().getName());
        } else {
            sender.sendMessage("§cFailed to join team. The team may be full.");
        }
    }

    private void handleLeave(CommandSender sender, League league) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can leave teams");
            return;
        }
        Player p = (Player) sender;
        String playerUuid = p.getUniqueId().toString();

        Optional<Team> currentTeam = manager.getPlayerTeam(league, playerUuid);
        if (currentTeam.isEmpty()) {
            sender.sendMessage("§cYou are not in a team");
            return;
        }

        String teamName = currentTeam.get().getName();
        boolean ok = manager.leaveTeam(league, playerUuid);

        if (ok) {
            sender.sendMessage("§cLeft team " + teamName);
            sender.sendMessage("§7You can rejoin if invited, or create your own team.");
        } else {
            sender.sendMessage("§cFailed to leave team.");
        }
    }

    private void handleKick(CommandSender sender, League league, String teamId, String[] rest) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can kick");
            return;
        }
        if (rest.length < 1) {
            sender.sendMessage("§cUsage: /league <league> team <team> kick <player>");
            return;
        }

        Team team = manager.getTeam(league, teamId).orElse(null);
        if (team == null) {
            sender.sendMessage("§cTeam not found");
            return;
        }

        Player p = (Player) sender;
        if (!p.getUniqueId().toString().equals(team.getOwnerUuid()) && !p.hasPermission("bbrl." + league.getId() + ".manage")) {
            sender.sendMessage("§cOnly the team owner or league manager can kick players");
            return;
        }

        String targetName = rest[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getName() == null) {
            sender.sendMessage("§cPlayer not found");
            return;
        }

        String targetUuid = target.getUniqueId().toString();

        // Allow kicking yourself (owner can kick themselves from driver position)
        if (targetUuid.equals(team.getOwnerUuid()) && !targetUuid.equals(p.getUniqueId().toString())) {
            sender.sendMessage("§cYou cannot kick the team owner");
            return;
        }

        if (!team.getMembers().contains(targetUuid) && !team.getReserves().contains(targetUuid)) {
            sender.sendMessage("§cThat player is not in this team");
            return;
        }

        manager.kickPlayerFromTeam(league, teamId, targetUuid);
        sender.sendMessage("§aKicked " + target.getName() + " from the team");

        if (target.isOnline()) {
            target.getPlayer().sendMessage("§cYou have been kicked from " + team.getName());
        }
    }

    private void handleReorder(CommandSender sender, League league, String teamId, String[] rest) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can reorder");
            return;
        }

        Team team = manager.getTeam(league, teamId).orElse(null);
        if (team == null) {
            sender.sendMessage("§cTeam not found");
            return;
        }

        Player p = (Player) sender;
        if (!p.getUniqueId().toString().equals(team.getOwnerUuid())) {
            sender.sendMessage("§cOnly the team owner can reorder the lineup");
            return;
        }

        if (rest.length < 1) {
            sender.sendMessage("§cUsage: /league <league> team <team> reorder <player1> [player2] [player3]...");
            sender.sendMessage("§7Specify players in the order you want them. Non-specified players stay at the end.");
            return;
        }

        List<String> newOrder = new ArrayList<>();
        for (String name : rest) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op.getUniqueId() != null) {
                newOrder.add(op.getUniqueId().toString());
            }
        }

        manager.reorderTeamMembers(league, teamId, newOrder);
        sender.sendMessage("§aTeam lineup reordered");
    }

    private void handleInfo(CommandSender sender, League league, String teamId) {
        Team t = manager.getTeam(league, teamId).orElse(null);
        if (t == null) {
            sender.sendMessage("§cTeam not found");
            return;
        }
        sendDecoratedInfo(sender, t, league);
    }

    private void sendDecoratedInfo(CommandSender sender, Team t, League league) {
        String teamColor = t.getChatColor();
        String colorName = COLOUR_NAME_MAP.getOrDefault(t.getHexColor(), "unknown");
        String bar = teamColor + "========= §f" + t.getName() + teamColor + " =========";

        sender.sendMessage(bar);

        String ownerName = getPlayerName(t.getOwnerUuid());

        sender.sendMessage(teamColor + "Team Owner: §f" + ownerName);
        sender.sendMessage(teamColor + "Colour: §f" + colorName + " (" + t.getHexColor().replace("§", "&") + ")");
        sender.sendMessage(teamColor + "==============================" );

        sender.sendMessage(teamColor + "Drivers:");
        for (String uuid : t.getMembers()) {
            String name = getPlayerName(uuid);
            sender.sendMessage(teamColor + "- §f" + name + teamColor + " [§fMain" + teamColor + "]");
        }
        for (String uuid : t.getReserves()) {
            String name = getPlayerName(uuid);
            sender.sendMessage(teamColor + "- §f" + name + teamColor + " [§fReserve" + teamColor + "]");
        }

        sender.sendMessage(teamColor + "==============================");
        sender.sendMessage(teamColor + "Standings:");
        // FIXED: Use double instead of int
        double pts = manager.getTeamPoints(league, t.getId());
        sender.sendMessage(teamColor + "- §fTeam Points: §f" + String.format("%.1f", pts));
        sender.sendMessage(teamColor + "==============================");
    }

    private String getPlayerName(String uuid) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
            return name != null ? name : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}