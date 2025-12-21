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

import java.io.File;
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
            sender.sendMessage("§cUsage: /league season <league> <new|season-name>");
            return;
        }
        String leagueId = args[0];
        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found");
            return;
        }
        League league = ol.get();
        String action = args[1];

        /* ---- create new season ---- */
        if (action.equalsIgnoreCase("new")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /league season <league> new <seasonName> [raceCount]");
                return;
            }
            String seasonName = args[2];
            String raceCount  = args.length > 3 ? args[3] : "Unknown";

            /* 1.  season directory */
            File seasonDir = new File(storage.leaguesRoot, leagueId + "/seasons/" + seasonName);
            seasonDir.mkdirs();

            /* 2.  championship.yml with empty lists */
            Map<String, Object> champ = new LinkedHashMap<>();
            champ.put("DriversChampionship", new ArrayList<>());
            champ.put("TeamsChampionship",   new ArrayList<>());
            champ.put("raceCountDisplay",    raceCount);
            File champFile = new File(seasonDir, "championship.yml");
            io.bbrl.leaguesystem.util.YamlUtil.saveYaml(champFile, champ);

            /* 3.  teams.yml empty */
            Map<String, Object> teamsRoot = new LinkedHashMap<>();
            teamsRoot.put("Teams", new LinkedHashMap<>());
            File teamsFile = new File(seasonDir, "teams.yml");
            io.bbrl.leaguesystem.util.YamlUtil.saveYaml(teamsFile, teamsRoot);

            /* 4.  register season in league object */
            league.getOrCreateSeason(seasonName);
            league.setCurrentSeason(seasonName);
            storage.saveLeague(league);

            sender.sendMessage("§aCreated season §l" + seasonName + "§a (" + raceCount + " races)");
            return;
        }

        /* ---- work on existing season ---- */
        Season season = league.getSeason(action);
        if (season == null) {
            sender.sendMessage("§cSeason '" + action + "' does not exist");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§aSeason §l" + action + "§a – " +
                    listRaces(league, action).size() + " races");
            return;
        }
        String sub = args[2].toLowerCase();

        switch (sub) {
            case "delete" -> handleDelete(sender, league, action);
            case "edit"   -> handleEdit(sender, league, season, args);
            case "race"   -> handleRace(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "driver" -> handleDriver(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            case "standings" -> handleStandings(sender, league, action, Arrays.copyOfRange(args, 3, args.length));
            default -> sender.sendMessage("§cUnknown sub-command");
        }
    }

    /* ---------- delete ---------- */
    private void handleDelete(CommandSender sender, League league, String seasonName) {
        if ("legacy".equals(seasonName)) {
            sender.sendMessage("§cCannot delete legacy season");
            return;
        }
        manager.deleteSeason(league, seasonName);
        sender.sendMessage("§aDeleted season §l" + seasonName);
    }

    /* ---------- edit ---------- */
    private void handleEdit(CommandSender sender, League league, Season season, String[] args) {
        if (args.length < 4 || !args[3].equalsIgnoreCase("raceCount")) {
            sender.sendMessage("§cUsage: /league season <le> <name> edit raceCount <text>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        manager.setSeasonRaceCount(league, season.getKey(), text);
        sender.sendMessage("§aSeason race-count display set to '" + text + "'");
    }

    /* ---------- race branch ---------- */
    private void handleRace(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league season <le> <sn> race <add|delete|edit|results>");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> handleRaceAdd(sender, league, season, args);
            case "delete" -> handleRaceDelete(sender, league, season, args);
            case "edit" -> handleRaceEdit(sender, league, season, args);
            case "results" -> handleRaceResults(sender, league, season, args);
            default -> sender.sendMessage("§cUnknown race action");
        }
    }

    private void handleRaceAdd(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league season <le> <sn> race add <name> [raceNumber]");
            return;
        }
        String raceName = args[2];
        if (storage.loadRace(league, season, raceName).containsKey("drivers")) {
            sender.sendMessage("§cRace already exists");
            return;
        }

        /* 1.  races sub-folder */
        File racesDir = new File(storage.leaguesRoot, league.getId() + "/seasons/" + season + "/races");
        racesDir.mkdirs();

        /* 2.  snapshot teams at race-creation time */
        Map<String, Team> teams = storage.loadTeams(league);
        Map<String, Object> teamsSection = new LinkedHashMap<>();
        for (Team t : teams.values()) {
            Map<String, String> line = new LinkedHashMap<>();
            int idx = 1;
            for (String uuid : t.getMembers()) {
                line.put("Main-" + idx++, Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName());
            }
            idx = 1;
            for (String uuid : t.getReserves()) {
                line.put("Reserve-" + idx++, Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName());
            }
            teamsSection.put(t.getName(), line);
        }

        /* 3.  build race file */
        Map<String, Object> raceRoot = new LinkedHashMap<>();
        raceRoot.put("Results",  new ArrayList<>());
        raceRoot.put("Teams",    teamsSection);
        raceRoot.put("TeamResults", new ArrayList<>());

        File raceFile = new File(racesDir, raceName + ".yml");
        io.bbrl.leaguesystem.util.YamlUtil.saveYaml(raceFile, raceRoot);

        sender.sendMessage("§aRace §l" + raceName + "§a added.");
    }

    private void handleRaceDelete(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league season <le> <sn> race delete <name>");
            return;
        }
        String raceName = args[2];
        File raceFile = new File(getSeasonDir(league, season), "races/" + raceName + ".yml");
        if (!raceFile.exists()) {
            sender.sendMessage("§cRace not found");
            return;
        }
        raceFile.delete();
        rebuildChampionship(league, season);
        sender.sendMessage("§aRace §l" + raceName + "§a removed.");
    }

    private void handleRaceEdit(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 4 || !args[3].equalsIgnoreCase("standings")) {
            sender.sendMessage("§cUsage: /league season <le> <sn> race edit <name> standings <driver> <position>");
            return;
        }
        if (args.length < 6) {
            sender.sendMessage("§cProvide driver name and position (0 = DNS)");
            return;
        }
        String raceName = args[2];
        File raceFile = new File(getSeasonDir(league, season), "races/" + raceName + ".yml");
        if (!raceFile.exists()) {
            sender.sendMessage("§cRace not found – create it first");
            return;
        }
        Map<String, Object> raceData = io.bbrl.leaguesystem.util.YamlUtil.loadObject(raceFile, Map.class);

        String driverName = args[4];
        int position;
        try {
            position = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPosition must be integer (0 = DNS)");
            return;
        }
        OfflinePlayer op = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(driverName))
                .findFirst()
                .orElse(null);
        if (op == null) {
            sender.sendMessage("§cDriver name not found");
            return;
        }
        String uuid = op.getUniqueId().toString();

        /* update Results list */
        List<Map<String, Object>> results = (List<Map<String, Object>>) raceData.getOrDefault("Results", new ArrayList<>());
        results.removeIf(m -> driverName.equalsIgnoreCase((String) m.get("username")));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("username", driverName);
        entry.put("position", position);
        entry.put("points", PointCalculator.getPointsFor(league, position));
        results.add(entry);
        raceData.put("Results", results);

        /* rebuild TeamResults: sum per team, mains first, reserves only if mains missing */
        Map<String, Team> teams = storage.loadTeams(league);
        Map<String, Integer> teamPoints = new LinkedHashMap<>();
        for (Team t : teams.values()) {
            int sum = 0;
            boolean useReserves = t.getMembers().stream().noneMatch(uuid::equals);
            List<String> pool = useReserves ? t.getReserves() : t.getMembers();
            for (String member : pool) {
                for (Map<String, Object> r : results) {
                    if (member.equals(Bukkit.getOfflinePlayer(UUID.fromString((String) r.get("uuid"))).getUniqueId().toString())) {
                        sum += (Integer) r.get("points");
                    }
                }
            }
            if (sum > 0) teamPoints.put(t.getName(), sum);
        }
        List<Map<String, Object>> tr = new ArrayList<>();
        teamPoints.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teamname", e.getKey());
                    m.put("points", e.getValue());
                    tr.add(m);
                });
        raceData.put("TeamResults", tr);

        io.bbrl.leaguesystem.util.YamlUtil.saveYaml(raceFile, raceData);
        rebuildChampionship(league, season);
        sender.sendMessage("§aSet " + driverName + " to P" + position + " for " + raceName);
    }

    private void handleRaceResults(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league season <le> <sn> race results <race>");
            return;
        }
        String raceName = args[2];
        File raceFile = new File(getSeasonDir(league, season), "races/" + raceName + ".yml");
        if (!raceFile.exists()) {
            sender.sendMessage("§cRace not found or no results");
            return;
        }
        Map<String, Object> race = io.bbrl.leaguesystem.util.YamlUtil.loadObject(raceFile, Map.class);
        sender.sendMessage("§aResults for §l" + raceName);
        List<Map<String, Object>> results = (List<Map<String, Object>>) race.getOrDefault("Results", List.of());
        results.stream()
                .sorted(Comparator.comparingInt(m -> (Integer) m.get("position")))
                .forEach(m -> sender.sendMessage(
                        m.get("position") + ". " + m.get("username") + "  –  " + m.get("points") + " pts"));
    }

    /* ---------- driver +/- points ---------- */
    private void handleDriver(CommandSender sender, League league, String season, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /league season <le> <sn> driver <name> +50|-50");
            return;
        }
        String name = args[1];
        String deltaStr = args[2];
        boolean add = deltaStr.startsWith("+");
        double delta;
        try {
            delta = Double.parseDouble(deltaStr.substring(1));
            if (!add) delta = -delta;
        } catch (Exception e) {
            sender.sendMessage("§cDelta must be +number or -number (decimals allowed)");
            return;
        }
        OfflinePlayer op = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (op == null) {
            sender.sendMessage("§cDriver not found");
            return;
        }
        /* load championship, adjust, save */
        File champFile = new File(getSeasonDir(league, season), "championship.yml");
        Map<String, Object> champ = io.bbrl.leaguesystem.util.YamlUtil.loadObject(champFile, Map.class);
        List<Map<String, Object>> drivers = (List<Map<String, Object>>) champ.getOrDefault("DriversChampionship", new ArrayList<>());
        drivers.removeIf(m -> name.equalsIgnoreCase((String) m.get("username")));
        int old = 0;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("username", name);
        entry.put("points", (int) Math.round(old + delta));
        drivers.add(entry);
        champ.put("DriversChampionship", drivers);
        io.bbrl.leaguesystem.util.YamlUtil.saveYaml(champFile, champ);
        sender.sendMessage("§aAdjusted " + name + " by " + (add ? "+" : "") + delta);
    }

    /* ---------- standings ---------- */
    private void handleStandings(CommandSender sender, League league, String season, String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "individual";
        File champFile = new File(getSeasonDir(league, season), "championship.yml");
        Map<String, Object> champ = io.bbrl.leaguesystem.util.YamlUtil.loadObject(champFile, Map.class);
        switch (mode) {
            case "individual" -> {
                sender.sendMessage("§aIndividual standings – season " + season);
                List<Map<String, Object>> drivers = (List<Map<String, Object>>) champ.getOrDefault("DriversChampionship", List.of());
                drivers.stream()
                        .sorted((a, b) -> Integer.compare((Integer) b.get("points"), (Integer) a.get("points")))
                        .limit(10)
                        .forEach(m -> sender.sendMessage(m.get("username") + " - " + m.get("points")));
            }
            case "teams" -> {
                sender.sendMessage("§aTeam standings – season " + season);
                List<Map<String, Object>> teams = (List<Map<String, Object>>) champ.getOrDefault("TeamsChampionship", List.of());
                teams.stream()
                        .sorted((a, b) -> Integer.compare((Integer) b.get("points"), (Integer) a.get("points")))
                        .limit(10)
                        .forEach(m -> sender.sendMessage(m.get("teamname") + " - " + m.get("points")));
            }
            default -> sender.sendMessage("§cMode must be individual or teams");
        }
    }

    /* ---------- helpers ---------- */
    private File getSeasonDir(League league, String season) {
        return new File(storage.leaguesRoot, league.getId() + "/seasons/" + season);
    }

    private List<String> listRaces(League league, String season) {
        File dir = new File(getSeasonDir(league, season), "races");
        if (!dir.exists()) return List.of();
        String[] names = dir.list((d, name) -> name.endsWith(".yml"));
        if (names == null) return List.of();
        return Arrays.stream(names).map(n -> n.replace(".yml", "")).toList();
    }

    /* rebuild championship after any race change or driver adjustment */
    private void rebuildChampionship(League league, String season) {
        Map<String, Integer> driverTotals = new LinkedHashMap<>();
        Map<String, Integer> teamTotals   = new LinkedHashMap<>();
        Map<String, Team> teamMap = storage.loadTeams(league);

        for (String race : listRaces(league, season)) {
            File rf = new File(getSeasonDir(league, season), "races/" + race + ".yml");
            Map<String, Object> data = io.bbrl.leaguesystem.util.YamlUtil.loadObject(rf, Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) data.getOrDefault("Results", List.of());
            for (Map<String, Object> r : results) {
                String uuid = Bukkit.getOfflinePlayer((String) r.get("username")).getUniqueId().toString();
                int pts = (Integer) r.get("points");
                driverTotals.merge(uuid, pts, Integer::sum);
            }
        }
        /* build team totals */
        driverTotals.forEach((uuid, pts) -> {
            teamMap.values().stream()
                    .filter(t -> t.getMembers().contains(uuid))
                    .findFirst()
                    .ifPresent(t -> teamTotals.merge(t.getId(), pts, Integer::sum));
        });

        /* write championship.yml in the requested list style */
        File champFile = new File(getSeasonDir(league, season), "championship.yml");
        Map<String, Object> champ = io.bbrl.leaguesystem.util.YamlUtil.loadObject(champFile, Map.class);

        List<Map<String, Object>> driversList = new ArrayList<>();
        driverTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", Bukkit.getOfflinePlayer(UUID.fromString(e.getKey())).getName());
                    m.put("points", e.getValue());
                    driversList.add(m);
                });
        champ.put("DriversChampionship", driversList);

        List<Map<String, Object>> teamsList = new ArrayList<>();
        teamTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teamname", e.getKey());
                    m.put("points", e.getValue());
                    teamsList.add(m);
                });
        champ.put("TeamsChampionship", teamsList);

        io.bbrl.leaguesystem.util.YamlUtil.saveYaml(champFile, champ);
    }
}