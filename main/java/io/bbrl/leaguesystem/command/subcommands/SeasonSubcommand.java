package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Season;
import io.bbrl.leaguesystem.model.Team;
import io.bbrl.leaguesystem.service.LeagueManager;
import io.bbrl.leaguesystem.service.PointCalculator;
import io.bbrl.leaguesystem.config.LeagueStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class SeasonSubcommand implements LeagueCommand.Subcommand {

    private final LeagueManager manager;
    private final LeagueStorage storage;

    public SeasonSubcommand(LeagueManager manager) {
        this.manager = manager;
        this.storage = (LeagueStorage) manager.getStorage();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <new|season-name> [subcommand]");
            return;
        }
        String leagueId = args[0];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }
        League league = ol.get();
        String action = args[1];

        if (action.equalsIgnoreCase("new")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /league season <league> new <seasonName> [raceCount]");
                return;
            }
            String seasonName = args[2];
            String raceCount = args.length > 3 ? args[3] : "Unknown";

            storage.createSeason(league, seasonName, raceCount);
            league.setCurrentSeason(seasonName);
            storage.saveLeague(league);

            Optional<League> refreshed = manager.getLeague(leagueId);
            if (refreshed.isPresent()) {
                League fresh = refreshed.get();
                league.getSeasons().clear();
                league.getSeasons().putAll(fresh.getSeasons());
            }

            sender.sendMessage("§aCreated season §l" + seasonName + "§a (" + raceCount + " races)");
            sender.sendMessage("§aSet as current season for " + league.getName());
            return;
        }

        Season season = league.getSeason(action);
        if (season == null) {
            Optional<League> reloaded = manager.getLeague(leagueId);
            if (reloaded.isPresent()) {
                league = reloaded.get();
                season = league.getSeason(action);
            }

            if (season == null) {
                sender.sendMessage("§cSeason '" + action + "' does not exist");
                sender.sendMessage("§cAvailable seasons: " + String.join(", ", league.getSeasons().keySet()));
                return;
            }
        }

        if (args.length < 3) {
            sender.sendMessage("§aSeason §l" + action + "§a – " +
                    storage.listRaces(league, action).size() + " races recorded");
            sender.sendMessage("§aCurrent season: " + (league.getCurrentSeason() != null ? league.getCurrentSeason() : "None"));
            return;
        }

        String sub = args[2].toLowerCase();

        switch (sub) {
            case "delete" -> handleDelete(sender, league, action);
            case "edit" -> handleEdit(sender, league, season, args);
            case "race" -> handleRace(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "driver" -> handleDriver(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "standings" -> handleStandings(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            default -> sender.sendMessage("§cUnknown sub-command. Use: delete, edit, race, driver, standings");
        }
    }

    private void handleDelete(CommandSender sender, League league, String seasonName) {
        if ("legacy".equalsIgnoreCase(seasonName)) {
            sender.sendMessage("§cCannot delete legacy season");
            return;
        }

        if (sender instanceof Player p) {
            if (!p.hasPermission("bbrl." + league.getId() + ".manage") && !p.hasPermission("bbrl.op")) {
                sender.sendMessage("§cYou don't have permission to delete seasons");
                return;
            }
        }

        manager.deleteSeason(league, seasonName);
        sender.sendMessage("§aDeleted season §l" + seasonName);
    }

    private void handleEdit(CommandSender sender, League league, Season season, String[] args) {
        if (args.length < 4 || !args[3].equalsIgnoreCase("raceCount")) {
            sender.sendMessage("§cUsage: /league season <league> <season> edit raceCount <text>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        manager.setSeasonRaceCount(league, season.getKey(), text);
        sender.sendMessage("§aSeason race-count display set to '" + text + "'");
    }

    private void handleRace(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league season <league> <season> race <add|delete|edit|results>");
            return;
        }

        if (sender instanceof Player p) {
            if (!p.hasPermission("bbrl." + league.getId() + ".manage") && !p.hasPermission("bbrl.op")) {
                sender.sendMessage("§cYou don't have permission to modify races");
                return;
            }
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "add" -> handleRaceAdd(sender, league, season, args);
            case "delete" -> handleRaceDelete(sender, league, season, args);
            case "edit" -> handleRaceEdit(sender, league, season, args);
            case "results" -> handleRaceResults(sender, league, season, args);
            default -> sender.sendMessage("§cUnknown race action. Use: add, delete, edit, results");
        }
    }

    private void handleRaceAdd(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race add <name>");
            return;
        }
        String raceName = args[1];
        storage.saveRace(league, season, raceName, null);
        sender.sendMessage("§aRace §l" + raceName + "§a added to season " + season);
    }

    private void handleRaceDelete(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race delete <name>");
            return;
        }
        String raceName = args[1];
        storage.deleteRace(league, season, raceName);
        storage.saveChampionship(league, season, null);
        sender.sendMessage("§aRace §l" + raceName + "§a removed from season " + season);
    }

    private void handleRaceEdit(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <name> standings|standings-team ...");
            return;
        }

        String raceName = args[1];
        String editType = args[2].toLowerCase();

        switch (editType) {
            case "standings" -> handleRaceEditStandings(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            case "standings-team" -> handleRaceEditTeamStandings(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            default -> sender.sendMessage("§cUnknown edit type. Use: standings or standings-team");
        }
    }

    private void handleRaceEditStandings(CommandSender sender, League league, String season, String raceName, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <race> standings <driver> <position|remove>");
            return;
        }

        String driverName = args[0];
        String action = args[1].toLowerCase();

        if (action.equals("remove")) {
            OfflinePlayer op = findPlayer(driverName);
            if (op == null) {
                sender.sendMessage("§cDriver not found: " + driverName);
                return;
            }
            storage.deleteRaceResult(league, season, raceName, op.getUniqueId().toString());
            storage.saveChampionship(league, season, null);
            sender.sendMessage("§aRemoved " + driverName + " from " + raceName);
            return;
        }

        int position;
        try {
            position = Integer.parseInt(action);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPosition must be integer or 'remove'");
            return;
        }

        OfflinePlayer op = findPlayer(driverName);
        if (op == null) {
            sender.sendMessage("§cDriver not found: " + driverName);
            return;
        }

        String uuid = op.getUniqueId().toString();
        int points = PointCalculator.getPointsFor(league, position);

        storage.saveRaceResult(league, season, raceName, uuid, position, points);
        storage.saveChampionship(league, season, null);

        sender.sendMessage("§aSet " + driverName + " to P" + position + " (" + points + " pts) for " + raceName);
    }

    private void handleRaceEditTeamStandings(CommandSender sender, League league, String season, String raceName, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <race> standings-team <team> <points>");
            return;
        }

        String teamId = args[0].toLowerCase();
        String pointsStr = args[1];

        double points;
        try {
            points = Double.parseDouble(pointsStr);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPoints must be a number");
            return;
        }

        Optional<Team> team = manager.getTeam(league, teamId);
        if (team.isEmpty()) {
            sender.sendMessage("§cTeam not found: " + teamId);
            return;
        }

        storage.saveRaceTeamResult(league, season, raceName, teamId, points);
        storage.saveChampionship(league, season, null);

        String action = points >= 0 ? "added" : "removed";
        sender.sendMessage("§a" + action + " " + Math.abs(points) + " points for " + team.get().getName() + " in " + raceName);
    }

    private void handleRaceResults(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race results <race>");
            return;
        }
        String raceName = args[1];

        Map<String, Integer> results = storage.getRaceResults(league, season, raceName);
        if (results.isEmpty()) {
            sender.sendMessage("§cRace not found or no results for: " + raceName);
            return;
        }

        sender.sendMessage("§7---- §aResults for §f" + raceName + "§7 ----");
        sender.sendMessage("§7§o(Season: §7§o" + season + "§7§o)");
        sender.sendMessage("§a================================");
        int pos = 1;
        for (Map.Entry<String, Integer> e : results.entrySet()) {
            String playerName = getPlayerName(e.getKey());
            sender.sendMessage("§a" + pos++ + ". §f" + playerName + " §a- " + e.getValue() + " pts");
        }
    }

    private void handleDriver(CommandSender sender, League league, String season, String[] args) {
        // FIXED: Proper argument parsing
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> driver <name> <points>");
            return;
        }

        if (sender instanceof Player p) {
            if (!p.hasPermission("bbrl." + league.getId() + ".manage") && !p.hasPermission("bbrl.op")) {
                sender.sendMessage("§cYou don't have permission to modify driver points");
                return;
            }
        }

        String name = args[0];
        String pointsStr = args[1];

        double points;
        try {
            points = Double.parseDouble(pointsStr);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPoints must be a number (positive to add, negative to remove)");
            return;
        }

        OfflinePlayer op = findPlayer(name);
        if (op == null) {
            sender.sendMessage("§cDriver not found: " + name);
            return;
        }

        String uuid = op.getUniqueId().toString();

        // Add manual adjustment as a race result with position 0
        storage.saveRaceResult(league, season, "__manual__", uuid, 0, (int) points);
        storage.saveChampionship(league, season, null);

        String action = points >= 0 ? "added" : "removed";
        sender.sendMessage("§a" + action + " " + Math.abs(points) + " points for " + name);
    }

    private void handleStandings(CommandSender sender, League league, String season, String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "individual";

        storage.saveChampionship(league, season, null);

        Map<String, Object> champ = storage.loadChampionship(league, season);
        Map<String, Double> drivers = (Map<String, Double>) champ.getOrDefault("drivers", new HashMap<>());
        Map<String, Double> teams = (Map<String, Double>) champ.getOrDefault("teams", new HashMap<>());

        switch (mode) {
            case "individual" -> {
                sender.sendMessage("§a-===- §fDriver Standings §a–§f " + season + "§a -===-");
                sender.sendMessage(" ");
                if (drivers.isEmpty()) {
                    sender.sendMessage("§cNo drivers in championship");
                    return;
                }
                int pos = 1;
                for (Map.Entry<String, Double> e : drivers.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(20)
                        .toList()) {
                    String playerName = getPlayerName(e.getKey());
                    sender.sendMessage("§a" + pos + ". §f" + playerName + " §a-§f " + String.format("%.1f", e.getValue()) + "§a pts");
                    pos++;
                }
                sender.sendMessage(" ");
                sender.sendMessage("§a======================================");
            }
            case "teams" -> {
                sender.sendMessage("§a-===- §fTeam Standings §a–§f " + season + "§a -===-");
                sender.sendMessage(" ");
                if (teams.isEmpty()) {
                    sender.sendMessage("§cNo teams in championship");
                    return;
                }
                int pos = 1;
                for (Map.Entry<String, Double> e : teams.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(20)
                        .toList()) {
                    String teamId = e.getKey();
                    Optional<Team> teamOpt = manager.getTeam(league, teamId);
                    String displayName = teamOpt.map(Team::getName).orElse(teamId);
                    String color = teamOpt.map(Team::getChatColor).orElse("§f");

                    sender.sendMessage("§a" + pos + ". " + color + displayName + " §a-§f " + String.format("%.1f", e.getValue()) + "§a pts");
                    pos++;
                }
                sender.sendMessage(" ");
                sender.sendMessage("§a======================================");
            }
            default -> sender.sendMessage("§cMode must be 'individual' or 'teams'");
        }
    }

    private OfflinePlayer findPlayer(String name) {
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
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