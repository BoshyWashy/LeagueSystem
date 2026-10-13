package io.bbrl.leaguesystem.model;

import java.util.*;
import io.bbrl.leaguesystem.service.LeagueManager;

/**
 * Mutable seasons map – getOrCreateSeason writes into it.
 * All other fields remain as before.
 */
public class League {

    private String id;
    private String name;
    private String description = "";  // League description
    private LeagueConfig config;

    /* seasons: key is ANY string (e.g. "2024-spring") */
    private final Map<String, Season> seasons = new LinkedHashMap<>();
    private String currentSeason = null; // null = none selected
    private final Set<String> finishedSeasons = new HashSet<>(); // Seasons that are finished (locked)

    /* invite map – InviteEntry objects */
    private final Map<String, Set<LeagueManager.InviteEntry>> teamInvites = new HashMap<>();

    /* leaderboards - transient, loaded from DB */
    private transient Map<String, Leaderboard> leaderboards = new HashMap<>();

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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    /* finished seasons */
    public Set<String> getFinishedSeasons() { return finishedSeasons; }
    public void setFinishedSeasons(Set<String> finishedSeasons) {
        this.finishedSeasons.clear();
        this.finishedSeasons.addAll(finishedSeasons);
    }
    public boolean isSeasonFinished(String seasonKey) {
        return finishedSeasons.contains(seasonKey);
    }
    public void finishSeason(String seasonKey) {
        finishedSeasons.add(seasonKey);
    }
    public void unfinishSeason(String seasonKey) {
        finishedSeasons.remove(seasonKey);
    }

    /* invite access – writable */
    public Map<String, Set<LeagueManager.InviteEntry>> getTeamInvites() { return teamInvites; }

    /* leaderboards access */
    public Map<String, Leaderboard> getLeaderboards() {
        if (leaderboards == null) leaderboards = new HashMap<>();
        return leaderboards;
    }
    public void setLeaderboards(Map<String, Leaderboard> leaderboards) { this.leaderboards = leaderboards; }
}