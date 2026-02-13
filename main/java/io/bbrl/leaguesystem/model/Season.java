package io.bbrl.leaguesystem.model;

import java.util.*;

/**
 * Immutable data-transfer object.
 * All mutating operations are handled directly by Database.
 */
public class Season {
    private final String key;

    /* per-season data – read-only views */
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<String, Map<String, Integer>> raceResults = new HashMap<>();
    private final Map<String, Integer> playerPoints = new HashMap<>();
    private final Map<String, Integer> teamPoints = new HashMap<>();

    public Season(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public Map<String, Team> getTeams() { return Collections.unmodifiableMap(teams); }
    public Map<String, Map<String, Integer>> getRaceResults() { return Collections.unmodifiableMap(raceResults); }
    public Map<String, Integer> getPlayerPoints() { return Collections.unmodifiableMap(playerPoints); }
    public Map<String, Integer> getTeamPoints() { return Collections.unmodifiableMap(teamPoints); }
}