package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.*;
import io.bbrl.leaguesystem.util.YamlUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SINGLE SOURCE OF TRUTH – every mutation is written to disk immediately.
 */
public final class LeagueStorage {

    private final JavaPlugin plugin;
    public final File leaguesRoot;
    private final File indexFile;
    private final Map<String, League> cache = new HashMap<>();

    public LeagueStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.leaguesRoot = new File(plugin.getDataFolder(), "leagues");
        this.indexFile = new File(plugin.getDataFolder(), "leagues.yml");
        ensureFolders();
        loadAll();
    }

    public void ensureFolders() {
        if (!leaguesRoot.exists()) leaguesRoot.mkdirs();
    }

    /* ---------- load all leagues (done once on enable) ---------- */
    public void loadAll() {
        cache.clear();
        if (!indexFile.exists()) {
            YamlUtil.saveYaml(indexFile, Map.of("leagues", List.of()));
            return;
        }
        List<String> leagues = YamlUtil.getList(indexFile, "leagues");
        if (leagues == null) leagues = List.of();
        for (String id : leagues) {
            File leagueDir = new File(leaguesRoot, id);
            File leagueFile = new File(leagueDir, "league.yml");
            if (!leagueFile.exists()) continue;
            League l = YamlUtil.loadObject(leagueFile, League.class);
            if (l != null) {
                l.migrateOldData();
                loadSeasons(l);
                cache.put(id.toLowerCase(), l);
            }
        }
    }

    /* actually populate seasons from disk */
    private void loadSeasons(League league) {
        File seasonsDir = new File(leaguesRoot, league.getId() + "/seasons");
        if (!seasonsDir.exists()) return;
        File[] dirs = seasonsDir.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            String seasonKey = dir.getName();
            league.getOrCreateSeason(seasonKey);
        }
    }

    public Collection<League> allLeagues() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public Optional<League> getLeague(String id) {
        return Optional.ofNullable(cache.get(id.toLowerCase()));
    }

    /* ---------- league-level save ---------- */
    public void saveLeague(League league) {
        File leagueDir = new File(leaguesRoot, league.getId());
        leagueDir.mkdirs();
        File leagueFile = new File(leagueDir, "league.yml");

        /* build the exact YAML structure you want */
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("league-id", league.getId());
        root.put("league-display-name", league.getName());
        root.put("league-owner", league.getConfig().getOwner());

        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("maxdriversperteam", league.getConfig().getMaxDriversPerTeam());
        opts.put("maxreservesperteam", league.getConfig().getMaxReservesPerTeam());
        opts.put("maxteamownership", league.getConfig().getMaxTeamOwnership());
        root.put("options", opts);

        YamlUtil.saveYaml(leagueFile, root);

        /* keep cache & index in sync */
        cache.put(league.getId().toLowerCase(), league);
        List<String> ids = cache.values().stream().map(League::getId).toList();
        YamlUtil.saveYaml(indexFile, Map.of("leagues", ids));
    }

    public void deleteLeague(String id) {
        cache.remove(id.toLowerCase());
        File leagueDir = new File(leaguesRoot, id);
        if (leagueDir.exists()) deleteRecursively(leagueDir);
        List<String> ids = cache.values().stream().map(League::getId).toList();
        YamlUtil.saveYaml(indexFile, Map.of("leagues", ids));
    }

    /* ---------- teams (global roster) ---------- */
    public void saveTeams(League league, Map<String, Team> teams) {
        File leagueDir = new File(leaguesRoot, league.getId());
        leagueDir.mkdirs();
        File teamsFile = new File(leagueDir, "teams.yml");
        YamlUtil.saveObject(teamsFile, teams);
    }

    public Map<String, Team> loadTeams(League league) {
        File teamsFile = new File(leaguesRoot, league.getId() + "/teams.yml");
        if (!teamsFile.exists()) return new LinkedHashMap<>();
        return Optional.ofNullable(YamlUtil.loadObject(teamsFile, Map.class)).orElse(new LinkedHashMap<>());
    }

    /* ---------- season-level ---------- */
    public void saveRace(League league, String season, String race, Map<String, Object> data) {
        File raceFile = new File(leaguesRoot, league.getId() + "/seasons/" + season + "/" + race + ".yml");
        raceFile.getParentFile().mkdirs();
        YamlUtil.saveObject(raceFile, data);
    }

    public Map<String, Object> loadRace(League league, String season, String race) {
        File raceFile = new File(leaguesRoot, league.getId() + "/seasons/" + season + "/" + race + ".yml");
        if (!raceFile.exists()) return new LinkedHashMap<>();
        return Optional.ofNullable(YamlUtil.loadObject(raceFile, Map.class)).orElse(new LinkedHashMap<>());
    }

    public void saveChampionship(League league, String season, Map<String, Object> data) {
        File champFile = new File(leaguesRoot, league.getId() + "/seasons/" + season + "/championship.yml");
        champFile.getParentFile().mkdirs();
        YamlUtil.saveObject(champFile, data);
    }

    public Map<String, Object> loadChampionship(League league, String season) {
        File champFile = new File(leaguesRoot, league.getId() + "/seasons/" + season + "/championship.yml");
        if (!champFile.exists()) {
            Map<String, Object> blank = new LinkedHashMap<>();
            blank.put("drivers", new LinkedHashMap<String, Integer>());
            blank.put("teams", new LinkedHashMap<String, Integer>());
            return blank;
        }
        return Optional.ofNullable(YamlUtil.loadObject(champFile, Map.class)).orElse(new LinkedHashMap<>());
    }

    /* ---------- tiny helper ---------- */
    public void saveSeasonRaceCount(League league, String season, String display) {
        Map<String, Object> champ = new LinkedHashMap<>(loadChampionship(league, season));
        champ.put("raceCountDisplay", display);
        saveChampionship(league, season, champ);
    }

    /* helper */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : Objects.requireNonNull(file.listFiles())) deleteRecursively(child);
        }
        file.delete();
    }
}