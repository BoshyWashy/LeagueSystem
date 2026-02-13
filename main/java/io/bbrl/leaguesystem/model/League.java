package io.bbrl.leaguesystem.model;

import java.util.*;
import io.bbrl.leaguesystem.service.LeagueManager;   // added

/**
 * Mutable seasons map – getOrCreateSeason writes into it.
 * All other fields remain as before.
 */
public class League {

    private String id;
    private String name;
    private LeagueConfig config;

    /* seasons: key is ANY string (e.g. "2024-spring") */
    private final Map<String, Season> seasons = new LinkedHashMap<>();
    private String currentSeason = null; // null = none selected

    /* invite map – InviteEntry objects */
    private final Map<String, Set<LeagueManager.InviteEntry>> teamInvites = new HashMap<>();

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

    /* season access – writable */
    public Map<String, Season> getSeasons() { return seasons; }
    public String getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(String currentSeason) { this.currentSeason = currentSeason; }
    public Season getSeason(String key) { return seasons.get(key); }
    public Season getOrCreateSeason(String key) {
        return seasons.computeIfAbsent(key, Season::new);
    }

    /* invite access – writable */
    public Map<String, Set<LeagueManager.InviteEntry>> getTeamInvites() { return teamInvites; }
}