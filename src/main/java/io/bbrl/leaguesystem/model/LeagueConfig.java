package io.bbrl.leaguesystem.model;

import java.util.*;

public class LeagueConfig {
    private int maxTeamOwnership = 1;
    private int maxDriversPerTeam = 3;
    private int maxReservesPerTeam = 1;
    private String pointSystem = "standard";
    private boolean hologramsEnabled = true;
    private int hologramRefreshSeconds = 30;
    private int strikeRaces = 0;
    private Map<String, Map<Integer, Integer>> customScales = new HashMap<>();

    /* current season – String key (e.g. "2024-spring") */
    private String currentSeason = null;

    /* TRANSIENT: only used while saving league.yml – not persisted as JSON/YAML itself */
    private transient String owner = "unknown | unknown";

    public LeagueConfig() {
        customScales.put("standard", Map.ofEntries(
                Map.entry(1,32),Map.entry(2,24),Map.entry(3,18),Map.entry(4,14),Map.entry(5,12),
                Map.entry(6,10),Map.entry(7,9),Map.entry(8,8),Map.entry(9,7),Map.entry(10,6),
                Map.entry(11,5),Map.entry(12,4),Map.entry(13,3),Map.entry(14,2),Map.entry(15,1)
        ));
        customScales.put("f1-sprint", Map.ofEntries(
                Map.entry(1,8),Map.entry(2,7),Map.entry(3,6),Map.entry(4,5),Map.entry(5,4),
                Map.entry(6,3),Map.entry(7,2),Map.entry(8,1)
        ));
    }

    /* original getters/setters */
    public int getMaxTeamOwnership() { return maxTeamOwnership; }
    public void setMaxTeamOwnership(int v) { this.maxTeamOwnership = v; }
    public int getMaxDriversPerTeam() { return maxDriversPerTeam; }
    public void setMaxDriversPerTeam(int v) { this.maxDriversPerTeam = v; }
    public int getMaxReservesPerTeam() { return maxReservesPerTeam; }
    public void setMaxReservesPerTeam(int v) { this.maxReservesPerTeam = v; }
    public String getPointSystem() { return pointSystem; }
    public void setPointSystem(String v) { this.pointSystem = v; }
    public boolean isHologramsEnabled() { return hologramsEnabled; }
    public void setHologramsEnabled(boolean v) { this.hologramsEnabled = v; }
    public int getHologramRefreshSeconds() { return hologramRefreshSeconds; }
    public void setHologramRefreshSeconds(int v) { this.hologramRefreshSeconds = v; }
    public int getStrikeRaces() { return strikeRaces; }
    public void setStrikeRaces(int v) { this.strikeRaces = v; }
    public Map<String, Map<Integer, Integer>> getCustomScales() { return customScales; }

    /* current season */
    public String getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(String currentSeason) { this.currentSeason = currentSeason; }

    /* TRANSIENT owner */
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}