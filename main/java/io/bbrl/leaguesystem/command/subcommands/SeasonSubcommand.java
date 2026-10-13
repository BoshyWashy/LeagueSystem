package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Season;
import io.bbrl.leaguesystem.model.Team;
import io.bbrl.leaguesystem.service.LeagueManager;
import io.bbrl.leaguesystem.service.PointCalculator;
import io.bbrl.leaguesystem.config.LeagueStorage;
import io.bbrl.leaguesystem.service.LeaderboardManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class SeasonSubcommand implements LeagueCommand.Subcommand {

    private final LeagueManager manager;
    private final LeagueStorage storage;
    private final LeaderboardManager leaderboardManager;

    public SeasonSubcommand(LeagueManager manager, LeaderboardManager leaderboardManager) {
        this.manager = manager;
        this.storage = (LeagueStorage) manager.getStorage();
        this.leaderboardManager = leaderboardManager;
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
            if (sender instanceof Player p) {
                if (!p.hasPermission("league.op") &&
                        !p.hasPermission("league.leagueowner." + leagueId)) {
                    sender.sendMessage("§cYou do not have permission to create seasons");
                    return;
                }
            }

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

            // Refresh leaderboards
            leaderboardManager.refreshAllLeaderboards(league);
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
            String pri = league.getConfig().getPrimaryColor();
            String sec = league.getConfig().getSecondaryColor();
            boolean isFinished = league.isSeasonFinished(action);

            // Count finished vs total races
            List<String> races = storage.listRaces(league, action);
            int finishedRaces = 0;
            for (String race : races) {
                if (storage.isRaceFinished(league, action, race)) {
                    finishedRaces++;
                }
            }

            // Show calendar
            Map<Integer, String> calendar = storage.getCalendar(league, action);

            sender.sendMessage(pri + "Season §l" + action + pri + "§a – " +
                    races.size() + " races registered, " + finishedRaces + " finished" + (isFinished ? pri + " [FINISHED]" : ""));
            sender.sendMessage(pri + "Current season: " + sec + (league.getCurrentSeason() != null ? league.getCurrentSeason() : "None"));

            if (!calendar.isEmpty()) {
                sender.sendMessage(pri + "Calendar:");
                for (Map.Entry<Integer, String> entry : calendar.entrySet()) {
                    String raceName = storage.getCalendarRaceName(league, action, entry.getKey());
                    String raceStatus = "";
                    if (raceName != null && !raceName.isEmpty()) {
                        boolean raceFinished = storage.isRaceFinished(league, action, raceName);
                        raceStatus = raceFinished ? " §a[FINISHED]" : " §e[UPCOMING]";
                    }
                    sender.sendMessage(pri + "  Race " + entry.getKey() + ": " + sec + entry.getValue() + raceStatus);
                }
            }
            return;
        }

        String sub = args[2].toLowerCase();

        switch (sub) {
            case "delete" -> handleDelete(sender, league, action);
            case "rename" -> handleRename(sender, league, action, args);
            case "edit" -> handleEdit(sender, league, action, args);
            case "finish" -> handleFinish(sender, league, action, args);
            case "setcurrent" -> handleSetCurrent(sender, league, action);
            case "race" -> handleRace(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "driver" -> handleDriver(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "standings" -> handleStandings(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "calendar" -> handleCalendar(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            default -> sender.sendMessage("§cUnknown sub-command. Use: delete, rename, edit, finish, setcurrent, race, driver, standings, calendar");
        }
    }

    private void handleDelete(CommandSender sender, League league, String seasonName) {
        if ("legacy".equalsIgnoreCase(seasonName)) {
            sender.sendMessage("§cCannot delete legacy season");
            return;
        }

        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") &&
                    !p.hasPermission("league.leagueowner." + league.getId())) {
                sender.sendMessage("§cYou do not have permission to delete seasons");
                return;
            }
        }

        manager.deleteSeason(league, seasonName);
        sender.sendMessage("§aDeleted season §l" + seasonName);

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    private void handleRename(CommandSender sender, League league, String oldSeasonName, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") &&
                    !p.hasPermission("league.leagueowner." + league.getId())) {
                sender.sendMessage("§cYou do not have permission to rename seasons");
                return;
            }
        }

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /league season <league> <season> rename <new-name>");
            return;
        }

        String newSeasonName = args[3];

        // Check if new name already exists
        if (league.getSeason(newSeasonName) != null) {
            sender.sendMessage("§cA season with that name already exists");
            return;
        }

        // Check if season is finished (cannot rename finished seasons to preserve data integrity)
        if (league.isSeasonFinished(oldSeasonName)) {
            sender.sendMessage("§cCannot rename a finished season");
            return;
        }

        storage.renameSeason(league.getId(), oldSeasonName, newSeasonName);

        // Update local cache
        Season season = league.getSeasons().remove(oldSeasonName);
        if (season != null) {
            league.getSeasons().put(newSeasonName, season);
        }

        // Update current season if this was the current one
        if (oldSeasonName.equals(league.getCurrentSeason())) {
            league.setCurrentSeason(newSeasonName);
            manager.saveLeague(league);
        }

        sender.sendMessage("§aRenamed season from §l" + oldSeasonName + "§a to §l" + newSeasonName);

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    private void handleFinish(CommandSender sender, League league, String seasonName, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") &&
                    !p.hasPermission("league.leagueowner." + league.getId())) {
                sender.sendMessage("§cYou do not have permission to finish seasons");
                return;
            }
        }

        if (args.length >= 4 && args[3].equalsIgnoreCase("undo")) {
            manager.unfinishSeason(league, seasonName);
            sender.sendMessage("§aUnfinished season §l" + seasonName + "§a. Results can now be modified.");
            return;
        }

        manager.finishSeason(league, seasonName);
        sender.sendMessage("§aSeason §l" + seasonName + "§a has been finished!");
        sender.sendMessage("§7No more races can be added or edited in this season.");
        sender.sendMessage("§7Use '/league season " + league.getId() + " " + seasonName + " finish undo' to unlock.");
    }

    private void handleSetCurrent(CommandSender sender, League league, String seasonName) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") &&
                    !p.hasPermission("league.leagueowner." + league.getId())) {
                sender.sendMessage("§cYou do not have permission to set current season");
                return;
            }
        }

        league.setCurrentSeason(seasonName);
        manager.saveLeague(league);
        sender.sendMessage("§aSet §l" + seasonName + "§a as the current season for " + league.getName());
    }

    private void handleEdit(CommandSender sender, League league, String season, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") &&
                    !p.hasPermission("league.leagueowner." + league.getId())) {
                sender.sendMessage("§cYou do not have permission to edit seasons");
                return;
            }
        }

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /league season <league> <season> edit raceCount <text>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        manager.setSeasonRaceCount(league, season, text);
        sender.sendMessage("§aSeason race-count display set to '" + text + "'");

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    /* ---------------------------------------------------------- */
    /*  Calendar management                                         */
    /* ---------------------------------------------------------- */
    private void handleCalendar(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league season <league> <season> calendar <set|list>");
            return;
        }

        String calAction = args[0].toLowerCase();

        switch (calAction) {
            case "set" -> {
                if (sender instanceof Player p) {
                    if (!p.hasPermission("league.op") &&
                            !p.hasPermission("league.leagueowner." + league.getId())) {
                        sender.sendMessage("§cYou do not have permission to edit the calendar");
                        return;
                    }
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /league season <league> <season> calendar set <raceNumber> <raceName>");
                    return;
                }
                int raceNumber;
                try {
                    raceNumber = Integer.parseInt(args[1]);
                    if (raceNumber < 1) {
                        sender.sendMessage("§cRace number must be positive");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cRace number must be an integer");
                    return;
                }
                String raceName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                // Check if a race with this name exists in the season for auto-linking
                List<String> races = storage.listRaces(league, season);
                String linkedRace = races.contains(raceName) ? raceName : null;

                storage.saveCalendarEntry(league, season, raceNumber, raceName, linkedRace);

                if (linkedRace != null) {
                    sender.sendMessage("§aSet Race " + raceNumber + " to '" + raceName + "' in calendar (auto-linked to race)");
                } else {
                    sender.sendMessage("§aSet Race " + raceNumber + " to '" + raceName + "' in calendar");
                    sender.sendMessage("§7Tip: Create a race with this name to auto-link it.");
                }
            }
            case "list" -> {
                Map<Integer, String> calendar = storage.getCalendar(league, season);
                if (calendar.isEmpty()) {
                    sender.sendMessage("§cNo calendar entries for this season");
                    return;
                }
                String pri = league.getConfig().getPrimaryColor();
                String sec = league.getConfig().getSecondaryColor();

                sender.sendMessage(pri + "========== §fSeason " + season + " Calendar " + pri + "==========");

                for (Map.Entry<Integer, String> entry : calendar.entrySet()) {
                    String linkedRaceName = storage.getCalendarRaceName(league, season, entry.getKey());
                    String raceStatus = "";
                    if (linkedRaceName != null && !linkedRaceName.isEmpty()) {
                        boolean raceFinished = storage.isRaceFinished(league, season, linkedRaceName);
                        // raceStatus = raceFinished ? pri + " [§aFINISHED§r" + pri + "]" : pri + " [§eUPCOMING§r" + pri + "]";
                    }
                    sender.sendMessage(pri + "Race " + entry.getKey() + ": " + sec + entry.getValue() + raceStatus);
                }

                sender.sendMessage(pri + "=====================================");
            }
            default -> sender.sendMessage("§cUnknown calendar action. Use: set, list");
        }
    }
    /* ---------------------------------------------------------- */
    /*  Race management - NEW finalization system                  */
    /* ---------------------------------------------------------- */
    private void handleRace(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league season <league> <season> race <add|delete|edit|finish|results|resultsteams> ...");
            return;
        }

        String action = args[0].toLowerCase();

        // Permission check for modifying races
        boolean canModify = true;
        if (sender instanceof Player p) {
            canModify = p.hasPermission("league.op") ||
                    p.hasPermission("league.leagueowner." + league.getId());
        }

        // Check if season is finished
        if (league.isSeasonFinished(season)) {
            if (!action.equals("results") && !action.equals("resultsteams") && !action.equals("calendar")) {
                sender.sendMessage("§cThis season is finished. No modifications can be made.");
                return;
            }
        }

        if (!canModify && !action.equals("results") && !action.equals("resultsteams")) {
            sender.sendMessage("§cYou do not have permission to modify races");
            return;
        }

        switch (action) {
            case "add" -> handleRaceAdd(sender, league, season, args);
            case "delete" -> handleRaceDelete(sender, league, season, args);
            case "edit" -> handleRaceEdit(sender, league, season, args);
            case "finish" -> handleRaceFinish(sender, league, season, args);
            case "results" -> handleRaceResults(sender, league, season, args);
            case "resultsteams" -> handleRaceResultsTeams(sender, league, season, args);
            default -> sender.sendMessage("§cUnknown race action. Use: add, delete, edit, finish, results, resultsteams");
        }
    }

    private void handleRaceAdd(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race add <name> [raceNumber]");
            return;
        }
        String raceName = args[1];
        storage.saveRace(league, season, raceName, null);

        // If raceNumber provided, auto-link to calendar
        if (args.length >= 3) {
            try {
                int raceNumber = Integer.parseInt(args[2]);
                Map<Integer, String> calendar = storage.getCalendar(league, season);
                String trackName = calendar.get(raceNumber);
                if (trackName != null) {
                    storage.saveCalendarEntry(league, season, raceNumber, trackName, raceName);
                    sender.sendMessage("§7Auto-linked to calendar Race " + raceNumber + ": " + trackName);
                }
            } catch (NumberFormatException ignored) {}
        }

        sender.sendMessage("§aRace §l" + raceName + "§a added to season " + season);

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    private void handleRaceDelete(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race delete <name>");
            return;
        }
        String raceName = args[1];

        // Check if race is finished
        if (storage.isRaceFinished(league, season, raceName)) {
            sender.sendMessage("§cThis race is finished. Unfinish it first before deleting.");
            sender.sendMessage("§cUse: /league season " + league.getId() + " " + season + " race finish " + raceName + " undo");
            return;
        }

        // Delete the race and all its results
        storage.deleteRace(league, season, raceName);

        // Force championship rebuild to remove points from this race
        storage.saveChampionship(league, season, null);

        sender.sendMessage("§aRace §l" + raceName + "§a removed from season " + season);
        sender.sendMessage("§7All points from this race have been removed from standings.");

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    /* ---------------------------------------------------------- */
    /*  Race edit - NEW system with finish command                 */
    /* ---------------------------------------------------------- */
    private void handleRaceEdit(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <name> standings|standings-team|fastestlap|driversTeam|raceNumber ...");
            return;
        }

        String raceName = args[1];
        String editType = args[2].toLowerCase();

        // Check if race is finished - some commands work on finished races, others don't
        boolean isFinished = storage.isRaceFinished(league, season, raceName);

        switch (editType) {
            case "standings" -> {
                if (isFinished) {
                    sender.sendMessage("§cThis race is finished. Cannot modify standings directly. Unfinish the race first or use 'driversTeam' to change team assignments.");
                    return;
                }
                handleRaceEditStandings(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            }
            case "standings-team" -> {
                if (isFinished) {
                    sender.sendMessage("§cThis race is finished. Cannot modify team standings directly.");
                    return;
                }
                handleRaceEditTeamStandings(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            }
            case "fastestlap" -> {
                if (isFinished) {
                    sender.sendMessage("§cThis race is finished. Cannot modify fastest lap directly.");
                    return;
                }
                handleRaceEditFastestLap(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            }
            case "driversteam" -> handleRaceEditDriverTeam(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            case "racenumber" -> handleRaceEditRaceNumber(sender, league, season, raceName, Arrays.copyOfRange(args, 3, args.length));
            default -> sender.sendMessage("§cUnknown edit type. Use: standings, standings-team, fastestlap, driversTeam, raceNumber");
        }
    }

    private void handleRaceEditRaceNumber(CommandSender sender, League league, String season, String raceName, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <race> raceNumber <integer>");
            return;
        }
        int raceNumber;
        try {
            raceNumber = Integer.parseInt(args[0]);
            if (raceNumber < 1) {
                sender.sendMessage("§cRace number must be positive");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cRace number must be an integer");
            return;
        }

        storage.setRaceNumber(league, season, raceName, raceNumber);

        // Auto-link to calendar if track exists
        Map<Integer, String> calendar = storage.getCalendar(league, season);
        String trackName = calendar.get(raceNumber);
        if (trackName != null) {
            storage.saveCalendarEntry(league, season, raceNumber, trackName, raceName);
            sender.sendMessage("§7Auto-linked to calendar: Race " + raceNumber + " " + trackName);
        }

        sender.sendMessage("§aSet race number for " + raceName + " to " + raceNumber);
    }

    private void handleRaceEditStandings(CommandSender sender, League league, String season, String raceName, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <race> standings <driver> <position|remove|dnf>");
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

            // Refresh leaderboards
            leaderboardManager.refreshAllLeaderboards(league);
            return;
        }

        boolean isDnf = action.equals("dnf") || action.equals("0");
        int position;
        if (isDnf) {
            position = 0;
        } else {
            try {
                position = Integer.parseInt(action);
                if (position < 0) {
                    sender.sendMessage("§cPosition must be a positive integer, 0, 'dnf', or 'remove'");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cPosition must be a positive integer, 0, 'dnf', or 'remove'");
                return;
            }
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

        if (isDnf) {
            sender.sendMessage("§aSet " + driverName + " as DNF (0 pts) for " + raceName);
        } else {
            sender.sendMessage("§aSet " + driverName + " to P" + position + " (" + points + " pts) for " + raceName);
        }

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
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

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    private void handleRaceEditFastestLap(CommandSender sender, League league, String season, String raceName, String[] args) {
        // If no args or "clear", remove fastest lap
        if (args.length < 1 || args[0].equalsIgnoreCase("clear")) {
            storage.deleteFastestLap(league, season, raceName);
            storage.saveChampionship(league, season, null);
            sender.sendMessage("§aCleared fastest lap from " + raceName);

            // Refresh leaderboards
            leaderboardManager.refreshAllLeaderboards(league);
            return;
        }

        String driverName = args[0];

        OfflinePlayer op = findPlayer(driverName);
        if (op == null) {
            sender.sendMessage("§cDriver not found: " + driverName);
            return;
        }

        String uuid = op.getUniqueId().toString();
        int points = league.getConfig().getFastestLapPoints();

        storage.saveFastestLap(league, season, raceName, uuid, points);
        storage.saveChampionship(league, season, null);

        sender.sendMessage("§aSet fastest lap for " + raceName + " to " + driverName + " (" + points + " pts)");

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    /* ---------------------------------------------------------- */
    /*  NEW: driversTeam command - assign team to driver for race  */
    /* ---------------------------------------------------------- */
    private void handleRaceEditDriverTeam(CommandSender sender, League league, String season, String raceName, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race edit <race> driversTeam <driver> <team>");
            sender.sendMessage("§cUse 'none' as team to clear assignment");
            return;
        }

        String driverName = args[0];
        String teamId = args[1].toLowerCase();

        OfflinePlayer op = findPlayer(driverName);
        if (op == null) {
            sender.sendMessage("§cDriver not found: " + driverName);
            return;
        }
        String uuid = op.getUniqueId().toString();

        // Check if driver has a result in this race
        Map<String, Integer> results = storage.getRaceResults(league, season, raceName);
        if (!results.containsKey(uuid)) {
            sender.sendMessage("§c" + driverName + " has no result in this race. Add them to standings first.");
            return;
        }

        if (teamId.equals("none")) {
            storage.deleteRaceResultTeam(league, season, raceName, uuid);
            sender.sendMessage("§aCleared team assignment for " + driverName + " in " + raceName);
        } else {
            Optional<Team> team = manager.getTeam(league, teamId);
            if (team.isEmpty()) {
                sender.sendMessage("§cTeam not found: " + teamId);
                sender.sendMessage("§cAvailable teams: " + String.join(", ", storage.loadTeams(league).keySet()));
                return;
            }

            storage.saveRaceResultTeam(league, season, raceName, uuid, teamId);
            sender.sendMessage("§aSet " + driverName + " as driving for " + team.get().getChatColor() + team.get().getName() +
                    " §ain " + raceName);
        }

        // Rebuild championship if race is finished
        if (storage.isRaceFinished(league, season, raceName)) {
            storage.saveChampionship(league, season, null);
            sender.sendMessage("§7Championship recalculated.");
        }

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    /* ---------------------------------------------------------- */
    /*  NEW: Race finish command                                   */
    /* ---------------------------------------------------------- */
    private void handleRaceFinish(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race finish <race> [undo]");
            return;
        }

        String raceName = args[1];
        boolean undo = args.length > 2 && args[2].equalsIgnoreCase("undo");

        // Check if race exists
        List<String> races = storage.listRaces(league, season);
        if (!races.contains(raceName)) {
            sender.sendMessage("§cRace not found: " + raceName);
            return;
        }

        if (undo) {
            // Unfinish the race
            storage.setRaceFinished(league, season, raceName, false);
            sender.sendMessage("§aRace §l" + raceName + "§a has been unfished.");
            sender.sendMessage("§7You can now edit standings and fastest lap directly.");
            return;
        }

        // Check if already finished
        if (storage.isRaceFinished(league, season, raceName)) {
            sender.sendMessage("§cRace is already finished. Use 'undo' to unfinish.");
            return;
        }

        // Get all race results
        Map<String, Integer> results = storage.getRaceResults(league, season, raceName);

        if (results.isEmpty()) {
            sender.sendMessage("§cNo results to finalize for " + raceName);
            return;
        }

        // For each driver, determine which team they were driving for
        Map<String, Team> teams = storage.loadTeams(league);
        int assignedTeams = 0;

        for (String uuid : results.keySet()) {
            // Check if team already assigned
            String existingTeam = storage.getDriverTeamForRace(league, season, raceName, uuid);
            if (existingTeam != null && !existingTeam.isEmpty()) {
                assignedTeams++;
                continue;
            }

            // Find which team this driver belongs to
            String teamId = null;
            for (Team team : teams.values()) {
                if (team.getMembers().contains(uuid) || team.getReserves().contains(uuid) || team.isOwner(uuid)) {
                    teamId = team.getId();
                    break;
                }
            }

            // Save the team association for this race
            if (teamId != null) {
                storage.saveRaceResultTeam(league, season, raceName, uuid, teamId);
                assignedTeams++;
            }
        }

        // Mark race as finished
        storage.setRaceFinished(league, season, raceName, true);

        // Rebuild championship
        storage.saveChampionship(league, season, null);

        sender.sendMessage("§aRace §l" + raceName + "§a has been finalized!");
        sender.sendMessage("§7" + results.size() + " driver results recorded, " + assignedTeams + " team assignments made.");
        sender.sendMessage("§7Championship standings have been updated.");
        sender.sendMessage("§7Use 'driversTeam' to change team assignments.");

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    private void handleRaceResults(CommandSender sender, League league, String season, String[] args) {
        String pri = league.getConfig().getPrimaryColor();
        String sec = league.getConfig().getSecondaryColor();

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race results <race> [page]");
            return;
        }
        String raceName = args[1];
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cPage must be a number");
                return;
            }
        }

        Map<String, Integer> results = storage.getRaceResults(league, season, raceName);
        if (results.isEmpty()) {
            sender.sendMessage("§cRace not found or no results for: " + raceName);
            return;
        }

        boolean isFinished = storage.isRaceFinished(league, season, raceName);
        Map<String, String> driverTeams = storage.getRaceResultTeams(league, season, raceName);

        // Show race number if set
        int raceNumber = storage.getRaceNumber(league, season, raceName);
        String raceNumStr = raceNumber > 0 ? " §7(#" + raceNumber + ")" : "";

        // Get positions to check for DNF
        Map<String, Integer> positions = storage.getRaceResultPositions(league, season, raceName);

        // Convert to list for pagination - results are already sorted with DNF at bottom
        List<Map.Entry<String, Integer>> resultList = new ArrayList<>(results.entrySet());
        int totalResults = resultList.size();
        int totalPages = (int) Math.ceil(totalResults / 10.0);
        if (page > totalPages) page = totalPages;
        if (totalPages == 0) totalPages = 1;

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, totalResults);

        sender.sendMessage(pri + "---- " + sec + "Results for " + raceName + raceNumStr + pri + " ----");
        sender.sendMessage(pri + "§o(Season: " + sec + "§o" + season + pri + "§o)" +
                (isFinished ? " §a[FINISHED]" : " §e[DRAFT]") + pri + " | Page " + page + "/" + totalPages);
        sender.sendMessage(pri + "======================================");

        int displayPos = 1;
        for (int i = start; i < end; i++) {
            Map.Entry<String, Integer> e = resultList.get(i);
            String playerName = getPlayerName(e.getKey());
            String teamId = driverTeams.get(e.getKey());
            String teamInfo;
            if (teamId != null && !teamId.isEmpty()) {
                Optional<Team> team = manager.getTeam(league, teamId);
                if (team.isPresent()) {
                    teamInfo = " " + team.get().getChatColor() + "[" + team.get().getName() + "]";
                } else {
                    teamInfo = " §7[N/A]";
                }
            } else {
                teamInfo = " §7[N/A]";
            }
            int pos = positions.getOrDefault(e.getKey(), 0);
            if (pos == 0) {
                sender.sendMessage("§cDNF. §7" + playerName + teamInfo + " §7- 0 pts");
            } else {
                sender.sendMessage(pri + "" + displayPos + ". " + sec + playerName + teamInfo + pri + " - " + e.getValue() + " pts");
                displayPos++;
            }
        }

        Map<String, Object> fastestLap = storage.getFastestLap(league, season, raceName);
        if (!fastestLap.isEmpty()) {
            String flUuid = (String) fastestLap.get("uuid");
            int flPoints = (int) fastestLap.get("points");
            String flName = getPlayerName(flUuid);
            sender.sendMessage(" ");
            sender.sendMessage(pri + "★ Fastest Lap: " + sec + flName + pri + " (+" + flPoints + " pts)");
        }

        sender.sendMessage(pri + "======================================");

        // Page navigation
        if (sender instanceof Player player) {
            if (totalPages > 1) {
                TextComponent nav = new TextComponent("");
                if (page > 1) {
                    TextComponent prev = new TextComponent(pri + "[§a§l<< Prev§r" + pri + "] ");
                    prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/league " + league.getId() + " season " + season + " race results " + raceName + " " + (page - 1)));
                    nav.addExtra(prev);
                }
                TextComponent pageInfo = new TextComponent(pri + "Page " + page + "/" + totalPages + " ");
                nav.addExtra(pageInfo);
                if (page < totalPages) {
                    TextComponent next = new TextComponent(pri + "[§a§lNext >>§r" + pri + "]");
                    next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/league " + league.getId() + " season " + season + " race results " + raceName + " " + (page + 1)));
                    nav.addExtra(next);
                }
                player.spigot().sendMessage(nav);
            }
        } else {
            if (totalPages > 1) {
                sender.sendMessage(pri + "Page " + page + "/" + totalPages +
                        (page > 1 ? " | Use page " + (page - 1) + " for previous" : "") +
                        (page < totalPages ? " | Use page " + (page + 1) + " for next" : ""));
            }
        }

        if (!isFinished) {
            sender.sendMessage("§eUse '/league " + league.getId() + " season " + season + " race finish " + raceName + "' to finalize");
        }
    }

    private void handleRaceResultsTeams(CommandSender sender, League league, String season, String[] args) {
        String pri = league.getConfig().getPrimaryColor();
        String sec = league.getConfig().getSecondaryColor();

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> race resultsteams <race> [page]");
            return;
        }
        String raceName = args[1];
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cPage must be a number");
                return;
            }
        }

        // Get race results and team assignments for this specific race
        Map<String, Integer> raceResults = storage.getRaceResults(league, season, raceName);
        if (raceResults.isEmpty()) {
            sender.sendMessage("§cNo results found for: " + raceName);
            return;
        }

        // Get team assignments for each driver in this race
        Map<String, String> driverTeams = storage.getRaceResultTeams(league, season, raceName);

        // Aggregate points by team
        Map<String, Double> teamPoints = new HashMap<>();
        Map<String, Team> teams = storage.loadTeams(league);

        for (Map.Entry<String, Integer> entry : raceResults.entrySet()) {
            String uuid = entry.getKey();
            int points = entry.getValue();

            // Find which team this driver was driving for in this race
            String teamId = driverTeams.get(uuid);

            // Fallback: if no specific team assignment, find their current team
            if (teamId == null || teamId.isEmpty()) {
                for (Team team : teams.values()) {
                    if (team.getMembers().contains(uuid) || team.getReserves().contains(uuid) || team.isOwner(uuid)) {
                        teamId = team.getId();
                        break;
                    }
                }
            }

            if (teamId != null && !teamId.isEmpty()) {
                teamPoints.merge(teamId, (double) points, Double::sum);
            }
        }

        // Add fastest lap points to the team
        Map<String, Object> fastestLap = storage.getFastestLap(league, season, raceName);
        if (!fastestLap.isEmpty()) {
            String flUuid = (String) fastestLap.get("uuid");
            int flPoints = (int) fastestLap.get("points");
            String flTeamId = driverTeams.get(flUuid);

            if (flTeamId == null || flTeamId.isEmpty()) {
                for (Team team : teams.values()) {
                    if (team.getMembers().contains(flUuid) || team.getReserves().contains(flUuid) || team.isOwner(flUuid)) {
                        flTeamId = team.getId();
                        break;
                    }
                }
            }

            if (flTeamId != null && !flTeamId.isEmpty()) {
                teamPoints.merge(flTeamId, (double) flPoints, Double::sum);
            }
        }

        if (teamPoints.isEmpty()) {
            sender.sendMessage("§cNo team results could be calculated for: " + raceName);
            sender.sendMessage("§7Make sure drivers have team assignments. Use 'driversTeam' to assign teams.");
            return;
        }

        // Sort teams by points descending
        List<Map.Entry<String, Double>> sortedTeams = teamPoints.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        int totalResults = sortedTeams.size();
        int totalPages = (int) Math.ceil(totalResults / 10.0);
        if (page > totalPages) page = totalPages;
        if (totalPages == 0) totalPages = 1;

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, totalResults);

        sender.sendMessage(pri + "---- " + sec + "Team Results for " + raceName + pri + " ----");
        sender.sendMessage(pri + "§o(Season: " + sec + "§o" + season + pri + "§o)" + pri + " | Page " + page + "/" + totalPages);
        sender.sendMessage(pri + "======================================");

        int pos = start + 1;
        for (int i = start; i < end; i++) {
            Map.Entry<String, Double> e = sortedTeams.get(i);
            String teamId = e.getKey();
            Optional<Team> teamOpt = manager.getTeam(league, teamId);
            String displayName = teamOpt.map(Team::getName).orElse(teamId);
            String teamColor = teamOpt.map(Team::getChatColor).orElse(sec);
            sender.sendMessage(pri + "" + pos + ". " + teamColor + displayName + pri + " - " + String.format("%.1f", e.getValue()) + " pts");
            pos++;
        }

        sender.sendMessage(pri + "======================================");

        // Page navigation
        if (sender instanceof Player player) {
            if (totalPages > 1) {
                TextComponent nav = new TextComponent("");
                if (page > 1) {
                    TextComponent prev = new TextComponent(pri + "[§a§l<< Prev§r" + pri + "] ");
                    prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/league " + league.getId() + " season " + season + " race resultsteams " + raceName + " " + (page - 1)));
                    nav.addExtra(prev);
                }
                TextComponent pageInfo = new TextComponent(pri + "Page " + page + "/" + totalPages + " ");
                nav.addExtra(pageInfo);
                if (page < totalPages) {
                    TextComponent next = new TextComponent(pri + "[§a§lNext >>§r" + pri + "]");
                    next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/league " + league.getId() + " season " + season + " race resultsteams " + raceName + " " + (page + 1)));
                    nav.addExtra(next);
                }
                player.spigot().sendMessage(nav);
            }
        } else {
            if (totalPages > 1) {
                sender.sendMessage(pri + "Page " + page + "/" + totalPages +
                        (page > 1 ? " | Use page " + (page - 1) + " for previous" : "") +
                        (page < totalPages ? " | Use page " + (page + 1) + " for next" : ""));
            }
        }
    }

    private void handleDriver(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <league> <season> driver <name> <points>");
            return;
        }

        if (sender instanceof Player p) {
            if (!p.hasPermission("league.op") &&
                    !p.hasPermission("league.leagueowner." + league.getId())) {
                sender.sendMessage("§cYou do not have permission to modify driver points");
                return;
            }
        }

        String name = args[0];
        String pointsStr = args[1];

        double adjustment;
        try {
            adjustment = Double.parseDouble(pointsStr);
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

        storage.addManualAdjustment(league, season, uuid, adjustment);
        storage.saveChampionship(league, season, null);

        Map<String, Object> champ = storage.loadChampionship(league, season);
        Map<String, Double> drivers = (Map<String, Double>) champ.getOrDefault("drivers", new HashMap<>());
        double newTotal = drivers.getOrDefault(uuid, 0.0);

        double totalAdjustment = storage.getManualAdjustment(league, season, uuid);
        String action = adjustment >= 0 ? "added +" : "subtracted ";
        sender.sendMessage("§a" + action + Math.abs(adjustment) + " points for " + name);
        sender.sendMessage("§7Total manual adjustment: " + (totalAdjustment >= 0 ? "+" : "") + String.format("%.1f", totalAdjustment));
        sender.sendMessage("§7New total: " + String.format("%.1f", newTotal) + " points");

        // Refresh leaderboards
        leaderboardManager.refreshAllLeaderboards(league);
    }

    private void handleStandings(CommandSender sender, League league, String season, String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "individual";
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage("§cPage must be a number");
                return;
            }
        }

        String pri = league.getConfig().getPrimaryColor();
        String sec = league.getConfig().getSecondaryColor();

        storage.saveChampionship(league, season, null);

        Map<String, Object> champ = storage.loadChampionship(league, season);
        Map<String, Double> drivers = (Map<String, Double>) champ.getOrDefault("drivers", new HashMap<>());
        Map<String, Double> teams = (Map<String, Double>) champ.getOrDefault("teams", new HashMap<>());

        switch (mode) {
            case "individual" -> {
                if (drivers.isEmpty()) {
                    sender.sendMessage("§cNo drivers in championship");
                    return;
                }

                List<Map.Entry<String, Double>> sorted = drivers.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .toList();

                int totalResults = sorted.size();
                int totalPages = (int) Math.ceil(totalResults / 10.0);
                if (page > totalPages) page = totalPages;
                if (totalPages == 0) totalPages = 1;

                int start = (page - 1) * 10;
                int end = Math.min(start + 10, totalResults);

                sender.sendMessage(pri + "-===- " + sec + "Driver Standings " + pri + "–" + sec + " " + season + pri + " -===-");
                sender.sendMessage(pri + "Page " + page + "/" + totalPages);
                sender.sendMessage(" ");

                int pos = start + 1;
                for (int i = start; i < end; i++) {
                    Map.Entry<String, Double> e = sorted.get(i);
                    String playerName = getPlayerName(e.getKey());
                    sender.sendMessage(pri + "" + pos + ". " + sec + playerName + pri + " -" + sec + " " + String.format("%.1f", e.getValue()) + pri + " pts");
                    pos++;
                }
                sender.sendMessage(" ");
                sender.sendMessage(pri + "======================================");

                // Page navigation
                if (sender instanceof Player player) {
                    if (totalPages > 1) {
                        TextComponent nav = new TextComponent("");
                        if (page > 1) {
                            TextComponent prev = new TextComponent(pri + "[§a§l<< Prev§r" + pri + "] ");
                            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/league " + league.getId() + " season " + season + " standings individual " + (page - 1)));
                            nav.addExtra(prev);
                        }
                        TextComponent pageInfo = new TextComponent(pri + "Page " + page + "/" + totalPages + " ");
                        nav.addExtra(pageInfo);
                        if (page < totalPages) {
                            TextComponent next = new TextComponent(pri + "[§a§lNext >>§r" + pri + "]");
                            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/league " + league.getId() + " season " + season + " standings individual " + (page + 1)));
                            nav.addExtra(next);
                        }
                        player.spigot().sendMessage(nav);
                    }

                    TextComponent teamStandings = new TextComponent(pri + "[Click to view Team Standings]");
                    teamStandings.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/league " + league.getId() + " season " + season + " standings teams"));
                    player.spigot().sendMessage(teamStandings);
                } else {
                    if (totalPages > 1) {
                        sender.sendMessage(pri + "Page " + page + "/" + totalPages +
                                (page > 1 ? " | Use page " + (page - 1) + " for previous" : "") +
                                (page < totalPages ? " | Use page " + (page + 1) + " for next" : ""));
                    }
                }
            }
            case "teams" -> {
                if (teams.isEmpty()) {
                    sender.sendMessage(pri + "No teams in championship :(");
                    return;
                }

                List<Map.Entry<String, Double>> sorted = teams.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .toList();

                int totalResults = sorted.size();
                int totalPages = (int) Math.ceil(totalResults / 10.0);
                if (page > totalPages) page = totalPages;
                if (totalPages == 0) totalPages = 1;

                int start = (page - 1) * 10;
                int end = Math.min(start + 10, totalResults);

                sender.sendMessage(pri + "-===- " + sec + "Team Standings " + pri + " –" + sec + " " + season + pri + " -===-");
                sender.sendMessage(pri + "Page " + page + "/" + totalPages);
                sender.sendMessage(" ");

                int pos = start + 1;
                for (int i = start; i < end; i++) {
                    Map.Entry<String, Double> e = sorted.get(i);
                    String teamId = e.getKey();
                    Optional<Team> teamOpt = manager.getTeam(league, teamId);
                    String displayName = teamOpt.map(Team::getName).orElse(teamId);
                    String teamColor = teamOpt.map(Team::getChatColor).orElse(sec);

                    sender.sendMessage(pri + "" + pos + ". " + teamColor + displayName + pri + "-" + sec + " " + String.format("%.1f", e.getValue()) + pri + " pts");
                    pos++;
                }
                sender.sendMessage(" ");
                sender.sendMessage(pri + "======================================");

                // Page navigation
                if (sender instanceof Player player) {
                    if (totalPages > 1) {
                        TextComponent nav = new TextComponent("");
                        if (page > 1) {
                            TextComponent prev = new TextComponent(pri + "[§a§l<< Prev§r" + pri + "] ");
                            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/league " + league.getId() + " season " + season + " standings teams " + (page - 1)));
                            nav.addExtra(prev);
                        }
                        TextComponent pageInfo = new TextComponent(pri + "Page " + page + "/" + totalPages + " ");
                        nav.addExtra(pageInfo);
                        if (page < totalPages) {
                            TextComponent next = new TextComponent(pri + "[§a§lNext >>§r" + pri + "]");
                            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/league " + league.getId() + " season " + season + " standings teams " + (page + 1)));
                            nav.addExtra(next);
                        }
                        player.spigot().sendMessage(nav);
                    }

                    TextComponent driverStandings = new TextComponent(pri + "[Click to view Driver Standings]");
                    driverStandings.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/league " + league.getId() + " season " + season + " standings individual"));
                    player.spigot().sendMessage(driverStandings);
                } else {
                    if (totalPages > 1) {
                        sender.sendMessage(pri + "Page " + page + "/" + totalPages +
                                (page > 1 ? " | Use page " + (page - 1) + " for previous" : "") +
                                (page < totalPages ? " | Use page " + (page + 1) + " for next" : ""));
                    }
                }
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