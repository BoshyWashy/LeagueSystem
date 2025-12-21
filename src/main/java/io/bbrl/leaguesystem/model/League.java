package io.bbrl.leaguesystem.model;

import java.util.*;

/**
 * Immutable data-transfer object.
 * All mutating operations are handled directly by LeagueStorage.
 */
public class League {

    private String id;
    private String name;
    private LeagueConfig config;

    /* seasons: key is ANY string (e.g. "2024-spring") */
    private Map<String, Season> seasons = new LinkedHashMap<>();
    private String currentSeason = null; // null = none selected

    /* legacy maps – only used for first-time migration */
    private Map<String, Team> teams = new HashMap<>();
    private Map<String, Map<String, Integer>> raceResults = new HashMap<>();
    private Map<String, Integer> playerPoints = new HashMap<>();
    private Map<String, Integer> teamPoints = new HashMap<>();
    private Map<String, Set<String>> teamInvites = new HashMap<>();

    public League() {}
    public League(String id, String name, LeagueConfig config) {
        this.id = id;
        this.name = name;
        this.config = config;
    }

    /* ---------- getters ---------- */
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LeagueConfig getConfig() { return config; }
    public void setConfig(LeagueConfig config) { this.config = config; }

    /* season access – read-only */
    public Map<String, Season> getSeasons() { return Collections.unmodifiableMap(seasons); }
    public String getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(String currentSeason) { this.currentSeason = currentSeason; }
    public Season getSeason(String key) { return seasons.get(key); }
    public Season getOrCreateSeason(String key) {
        return seasons.computeIfAbsent(key, Season::new);
    }

    /* legacy accessors – read-only */
    public Map<String, Team> getTeams() { return Collections.unmodifiableMap(teams); }
    public Map<String, Map<String, Integer>> getRaceResults() { return Collections.unmodifiableMap(raceResults); }
    public Map<String, Integer> getPlayerPoints() { return Collections.unmodifiableMap(playerPoints); }
    public Map<String, Integer> getTeamPoints() { return Collections.unmodifiableMap(teamPoints); }
    public Map<String, Set<String>> getTeamInvites() { return Collections.unmodifiableMap(teamInvites); }

    /* one-time migration: move legacy data into season named "legacy" */
    public void migrateOldData() {
        if (!seasons.isEmpty()) return; // already migrated
        if (teams.isEmpty() && raceResults.isEmpty()) return; // nothing to migrate
        Season legacy = getOrCreateSeason("legacy");
        legacy.getTeams().putAll(teams);
        legacy.getRaceResults().putAll(raceResults);
        legacy.getPlayerPoints().putAll(playerPoints);
        legacy.getTeamPoints().putAll(teamPoints);
        currentSeason = "legacy";
    }
}