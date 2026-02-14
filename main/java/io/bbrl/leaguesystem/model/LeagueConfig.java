package io.bbrl.leaguesystem.model;

import java.util.*;

public class LeagueConfig {
    private int maxTeamOwnership = 1;
    private int maxDriversPerTeam = 3;
    private int maxReservesPerTeam = 1;
    private String pointSystem = "f1";
    private boolean hologramsEnabled = true;
    private int hologramRefreshSeconds = 30;
    private int strikeRaces = 0;
    private final Map<String, Map<Integer, Integer>> customScales = new HashMap<>();

    /* League colors */
    private String primaryColor = "§6";  // Default gold
    private String secondaryColor = "§f"; // Default white

    /* Team creation settings */
    private boolean allowAnyoneCreateTeam = true; // Default: anyone can create teams

    /* Fastest lap points */
    private int fastestLapPoints = 1;

    /* current season – String key (e.g. "2024-spring") */
    private String currentSeason = null;

    /* Spawn location - stored with exact decimals */
    private String spawnWorld = null;
    private double spawnX = 0.0;
    private double spawnY = 0.0;
    private double spawnZ = 0.0;
    private float spawnYaw = 0.0f;
    private float spawnPitch = 0.0f;
    private boolean spawnSet = false;

    /* TRANSIENT: only used while saving league – not persisted as JSON/YAML itself */
    private transient String owner = "unknown | unknown";

    public LeagueConfig() {
        customScales.put("f1", Map.ofEntries(
                Map.entry(1,25), Map.entry(2,18), Map.entry(3,15), Map.entry(4,12), Map.entry(5,10),
                Map.entry(6,8), Map.entry(7,6), Map.entry(8,4), Map.entry(9,2), Map.entry(10,1)
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

    /* League colors */
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String v) { this.primaryColor = v; }
    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String v) { this.secondaryColor = v; }

    /* Team creation permission */
    public boolean isAllowAnyoneCreateTeam() { return allowAnyoneCreateTeam; }
    public void setAllowAnyoneCreateTeam(boolean v) { this.allowAnyoneCreateTeam = v; }

    public int getFastestLapPoints() { return fastestLapPoints; }
    public void setFastestLapPoints(int v) { this.fastestLapPoints = v; }

    /* current season */
    public String getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(String currentSeason) { this.currentSeason = currentSeason; }

    /* Spawn location getters/setters */
    public String getSpawnWorld() { return spawnWorld; }
    public void setSpawnWorld(String spawnWorld) { this.spawnWorld = spawnWorld; }
    public double getSpawnX() { return spawnX; }
    public void setSpawnX(double spawnX) { this.spawnX = spawnX; }
    public double getSpawnY() { return spawnY; }
    public void setSpawnY(double spawnY) { this.spawnY = spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public void setSpawnZ(double spawnZ) { this.spawnZ = spawnZ; }
    public float getSpawnYaw() { return spawnYaw; }
    public void setSpawnYaw(float spawnYaw) { this.spawnYaw = spawnYaw; }
    public float getSpawnPitch() { return spawnPitch; }
    public void setSpawnPitch(float spawnPitch) { this.spawnPitch = spawnPitch; }
    public boolean isSpawnSet() { return spawnSet; }
    public void setSpawnSet(boolean spawnSet) { this.spawnSet = spawnSet; }

    /* TRANSIENT owner */
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}