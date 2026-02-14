package io.bbrl.leaguesystem.command;

import io.bbrl.leaguesystem.Main;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Team;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public class LeagueAdminCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final LeagueManager manager;

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

    public LeagueAdminCommand(Main plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLeagueManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") && !p.hasPermission("league.admin")) {
                sender.sendMessage("§cYou need permission 'league.admin' or 'league.op' to use this command");
                return true;
            }
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        FileConfiguration config = plugin.getConfig();

        switch (action) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aConfiguration reloaded successfully!");
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /leagueadmin set <key> <value>");
                    sender.sendMessage("§cAvailable keys: default-point-system, enable-holograms, hologram-refresh-seconds, max-leagues-per-player, default-team-color, default-league-primary-color, default-league-secondary-color, log-level");
                    return true;
                }
                String key = args[1];
                String value = args[2];

                switch (key.toLowerCase()) {
                    case "default-point-system" -> {
                        config.set("default-point-system", value);
                        sender.sendMessage("§aSet default-point-system to: " + value);
                    }
                    case "enable-holograms" -> {
                        boolean bool = Boolean.parseBoolean(value);
                        config.set("enable-holograms", bool);
                        sender.sendMessage("§aSet enable-holograms to: " + bool);
                    }
                    case "hologram-refresh-seconds" -> {
                        try {
                            int num = Integer.parseInt(value);
                            config.set("hologram-refresh-seconds", num);
                            sender.sendMessage("§aSet hologram-refresh-seconds to: " + num);
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§cValue must be a number");
                            return true;
                        }
                    }
                    case "max-leagues-per-player" -> {
                        try {
                            int num = Integer.parseInt(value);
                            config.set("max-leagues-per-player", num);
                            sender.sendMessage("§aSet max-leagues-per-player to: " + num);
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§cValue must be a number");
                            return true;
                        }
                    }
                    case "default-team-color" -> {
                        config.set("default-team-color", value);
                        sender.sendMessage("§aSet default-team-color to: " + value);
                    }
                    case "default-league-primary-color" -> {
                        config.set("default-league-primary-color", value);
                        sender.sendMessage("§aSet default-league-primary-color to: " + value);
                    }
                    case "default-league-secondary-color" -> {
                        config.set("default-league-secondary-color", value);
                        sender.sendMessage("§aSet default-league-secondary-color to: " + value);
                    }
                    case "log-level" -> {
                        config.set("log-level", value.toLowerCase());
                        sender.sendMessage("§aSet log-level to: " + value.toLowerCase());
                    }
                    default -> {
                        sender.sendMessage("§cUnknown configuration key: " + key);
                        sender.sendMessage("§cAvailable: default-point-system, enable-holograms, hologram-refresh-seconds, max-leagues-per-player, default-team-color, default-league-primary-color, default-league-secondary-color, log-level");
                        return true;
                    }
                }
                plugin.saveConfig();
                sender.sendMessage("§aConfiguration saved! Changes will take effect immediately.");
            }
            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /leagueadmin get <key>");
                    return true;
                }
                String key = args[1];
                Object value = config.get(key);
                if (value != null) {
                    sender.sendMessage("§a" + key + " = " + value);
                } else {
                    sender.sendMessage("§cKey not found: " + key);
                }
            }
            case "info", "status" -> {
                sender.sendMessage("§6========== LeagueSystem Admin Info ==========");
                sender.sendMessage("§aDefault Point System: §f" + config.getString("default-point-system", "f1"));
                sender.sendMessage("§aHolograms Enabled: §f" + config.getBoolean("enable-holograms", true));
                sender.sendMessage("§aHologram Refresh: §f" + config.getInt("hologram-refresh-seconds", 30) + " seconds");
                sender.sendMessage("§aMax Leagues Per Player: §f" + config.getInt("max-leagues-per-player", 5));
                sender.sendMessage("§aDefault Team Color: §f" + config.getString("default-team-color", "#FFFFFF"));
                sender.sendMessage("§aDefault League Primary: §f" + config.getString("default-league-primary-color", "§6"));
                sender.sendMessage("§aDefault League Secondary: §f" + config.getString("default-league-secondary-color", "§f"));
                sender.sendMessage("§aLog Level: §f" + config.getString("log-level", "info"));
                sender.sendMessage("§6============================================");
            }
            case "edit-team" -> handleEditTeam(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleEditTeam(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /leagueadmin edit-team <league> <team> <action> [args...]");
            sender.sendMessage("§cActions: setOwner, setDriver, rename, renameID, colour, delete");
            return;
        }

        String leagueId = args[1].toLowerCase();
        String teamId = args[2].toLowerCase();
        String action = args[3].toLowerCase();

        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }
        League league = ol.get();

        Optional<Team> ot = manager.getTeam(league, teamId);
        if (ot.isEmpty()) {
            sender.sendMessage("§cTeam not found: " + teamId);
            return;
        }
        Team team = ot.get();

        if (sender instanceof Player p) {
            boolean isLeagueOwner = p.hasPermission("league.leagueowner." + leagueId);
            boolean isOp = p.hasPermission("league.op");

            if (!isLeagueOwner && !isOp) {
                sender.sendMessage("§cYou need permission 'league.leagueowner." + leagueId + "' or 'league.op' to edit teams");
                return;
            }
        }

        Map<String, Team> teams = manager.getStorage().loadTeams(league);

        switch (action) {
            case "setowner" -> {
                // Clear existing owners first
                team.getOwners().clear();

                // Check if using "clear" to make team owner-less
                if (args.length >= 5 && args[4].equalsIgnoreCase("clear")) {
                    team.setOwnerUuid("");
                    teams.put(team.getId(), team);
                    manager.getStorage().saveTeams(league, teams);
                    sender.sendMessage("§aCleared all owners from team. Team is now owner-less.");
                    return;
                }

                // Add new owners from command arguments
                for (int i = 4; i < args.length; i++) {
                    String playerName = args[i];

                    // Skip if it's a placeholder hint
                    if (playerName.equalsIgnoreCase("main-owner") || playerName.equalsIgnoreCase("co-owner") || playerName.equalsIgnoreCase("clear")) {
                        continue;
                    }

                    OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                    if (target.getName() == null) {
                        try {
                            UUID.fromString(playerName);
                            team.addOwner(playerName);
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage("§cPlayer not found: " + playerName);
                        }
                    } else {
                        team.addOwner(target.getUniqueId().toString());
                    }
                }

                // First one becomes main owner if list not empty
                if (!team.getOwners().isEmpty()) {
                    team.setOwnerUuid(team.getOwners().get(0));
                } else {
                    team.setOwnerUuid("");
                }

                teams.put(team.getId(), team);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aUpdated team owners. Total owners: " + team.getOwners().size());
            }
            case "setdriver" -> {
                if (args.length < 6) {
                    sender.sendMessage("§cUsage: /leagueadmin edit-team <league> <team> setDriver <main-1|main-2|...|reserve-1|...> <player>");
                    return;
                }

                String position = args[4].toLowerCase();
                String playerName = args[5];

                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                String uuid;
                if (target.getName() == null) {
                    try {
                        UUID.fromString(playerName);
                        uuid = playerName;
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage("§cPlayer not found: " + playerName);
                        return;
                    }
                } else {
                    uuid = target.getUniqueId().toString();
                }

                team.getMembers().remove(uuid);
                team.getReserves().remove(uuid);

                if (position.startsWith("main-")) {
                    try {
                        int slot = Integer.parseInt(position.substring(5)) - 1;
                        int maxMain = league.getConfig().getMaxDriversPerTeam();
                        if (slot < 0 || slot >= maxMain) {
                            sender.sendMessage("§cInvalid main slot. Max main drivers: " + maxMain);
                            return;
                        }
                        while (team.getMembers().size() <= slot) {
                            team.getMembers().add(null);
                        }
                        team.getMembers().set(slot, uuid);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid position format");
                        return;
                    }
                } else if (position.startsWith("reserve-")) {
                    try {
                        int slot = Integer.parseInt(position.substring(8)) - 1;
                        int maxReserve = league.getConfig().getMaxReservesPerTeam();
                        if (slot < 0 || slot >= maxReserve) {
                            sender.sendMessage("§cInvalid reserve slot. Max reserves: " + maxReserve);
                            return;
                        }
                        while (team.getReserves().size() <= slot) {
                            team.getReserves().add(null);
                        }
                        team.getReserves().set(slot, uuid);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid position format");
                        return;
                    }
                } else {
                    sender.sendMessage("§cInvalid position. Use main-1, main-2, etc. or reserve-1, reserve-2, etc.");
                    return;
                }

                teams.put(team.getId(), team);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aSet " + playerName + " to " + position);
            }
            case "rename" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /leagueadmin edit-team <league> <team> rename <new-name>");
                    return;
                }
                String newName = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

                boolean dup = teams.values().stream()
                        .anyMatch(t -> t.getName().equalsIgnoreCase(newName) && !t.getId().equals(teamId));
                if (dup) {
                    sender.sendMessage("§cA team with that name already exists in this league");
                    return;
                }

                team.setName(newName);
                teams.put(team.getId(), team);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aRenamed team to '" + newName + "'");
            }
            case "renameid" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /leagueadmin edit-team <league> <team> renameID <new-id>");
                    return;
                }
                String newId = args[4].toLowerCase().replaceAll("[^a-z0-9]", "");
                if (newId.isEmpty()) {
                    sender.sendMessage("§cInvalid ID");
                    return;
                }
                if (teams.containsKey(newId) && !newId.equalsIgnoreCase(teamId)) {
                    sender.sendMessage("§cA team with that ID already exists");
                    return;
                }

                manager.renameTeam(league, teamId, newId, team.getName());
                sender.sendMessage("§aRenamed team ID from '" + teamId + "' to '" + newId + "'");
            }
            case "colour", "color" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /leagueadmin edit-team <league> <team> colour <colour>");
                    sender.sendMessage("§cAvailable: " + String.join(", ", COLOUR_CODE_MAP.keySet()));
                    return;
                }
                String colourWord = args[4].toLowerCase();
                String code = COLOUR_CODE_MAP.get(colourWord);
                if (code == null) {
                    sender.sendMessage("§cUnknown colour. Choices: " + String.join(", ", COLOUR_CODE_MAP.keySet()));
                    return;
                }

                team.setHexColor(code);
                teams.put(team.getId(), team);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aTeam colour updated to " + colourWord);
            }
            case "delete" -> {
                if (args.length < 5 || !args[4].equalsIgnoreCase("confirm")) {
                    sender.sendMessage("§cUsage: /leagueadmin edit-team <league> <team> delete confirm");
                    sender.sendMessage("§c§lWARNING: This will permanently delete the team!");
                    return;
                }

                teams.remove(teamId);
                manager.getStorage().saveTeams(league, teams);
                sender.sendMessage("§aTeam '" + team.getName() + "' deleted.");
            }
            default -> {
                sender.sendMessage("§cUnknown action: " + action);
                sender.sendMessage("§cAvailable: setOwner, setDriver, rename, renameID, colour, delete");
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== LeagueAdmin Commands ==========");
        sender.sendMessage("§a/leagueadmin reload §7- Reload configuration from file");
        sender.sendMessage("§a/leagueadmin set <key> <value> §7- Set a configuration value");
        sender.sendMessage("§a/leagueadmin get <key> §7- Get a configuration value");
        sender.sendMessage("§a/leagueadmin info §7- Show current configuration");
        sender.sendMessage("§a/leagueadmin edit-team <league> <team> <action> §7- Edit team properties");
        sender.sendMessage("§6=========================================");
        sender.sendMessage("§7Available config keys:");
        sender.sendMessage("§7- default-point-system, enable-holograms");
        sender.sendMessage("§7- hologram-refresh-seconds, max-leagues-per-player");
        sender.sendMessage("§7- default-team-color, default-league-primary-color");
        sender.sendMessage("§7- default-league-secondary-color, log-level");
        sender.sendMessage("§7Team edit actions: setOwner, setDriver, rename, renameID, colour, delete");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") && !p.hasPermission("league.admin")) {
                return List.of();
            }
        }

        if (args.length == 1) {
            return List.of("reload", "set", "get", "info", "edit-team").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("set") || action.equals("get")) {
                return List.of(
                                "default-point-system",
                                "enable-holograms",
                                "hologram-refresh-seconds",
                                "max-leagues-per-player",
                                "default-team-color",
                                "default-league-primary-color",
                                "default-league-secondary-color",
                                "log-level"
                        ).stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            } else if (action.equals("edit-team")) {
                return manager.allLeagues().stream()
                        .map(League::getId)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("edit-team")) {
            String leagueId = args[1].toLowerCase();
            Optional<League> ol = manager.getLeague(leagueId);
            if (ol.isPresent()) {
                return manager.getStorage().loadTeams(ol.get()).keySet().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("edit-team")) {
            return List.of("setOwner", "setDriver", "rename", "renameID", "colour", "delete").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .toList();
        }

        if (args.length >= 5 && args[0].equalsIgnoreCase("edit-team")) {
            String action = args[3].toLowerCase();
            String leagueId = args[1].toLowerCase();
            Optional<League> ol = manager.getLeague(leagueId);

            switch (action) {
                case "setowner" -> {
                    // First argument position shows "clear" and "main-owner"
                    // Subsequent positions show "co-owner"
                    List<String> suggestions = new ArrayList<>();
                    if (args.length == 5) {
                        suggestions.add("clear");
                        suggestions.add("main-owner");
                    } else if (args.length > 5) {
                        suggestions.add("co-owner");
                    }
                    return suggestions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .toList();
                }
                case "setdriver" -> {
                    if (args.length == 5 && ol.isPresent()) {
                        League league = ol.get();
                        List<String> positions = new ArrayList<>();
                        for (int i = 1; i <= league.getConfig().getMaxDriversPerTeam(); i++) {
                            positions.add("main-" + i);
                        }
                        for (int i = 1; i <= league.getConfig().getMaxReservesPerTeam(); i++) {
                            positions.add("reserve-" + i);
                        }
                        return positions.stream()
                                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                .toList();
                    }
                    if (args.length == 6) {
                        return Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(s -> s.toLowerCase().startsWith(args[5].toLowerCase()))
                                .toList();
                    }
                }
                case "colour", "color" -> {
                    if (args.length == 5) {
                        return COLOUR_CODE_MAP.keySet().stream()
                                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                .toList();
                    }
                }
                case "delete" -> {
                    if (args.length == 5) {
                        return List.of("confirm").stream()
                                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                                .toList();
                    }
                }
            }
        }

        return List.of();
    }
}