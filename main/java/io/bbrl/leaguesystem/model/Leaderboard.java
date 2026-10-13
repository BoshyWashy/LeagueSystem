package io.bbrl.leaguesystem.model;

import org.bukkit.Location;

import java.util.UUID;

public class Leaderboard {
    private String id;
    private String name;
    private LeaderboardType type;
    private String seasonKey;
    private String raceName; // Only used for RACE_RESULTS type
    private Location location;
    private UUID creatorUuid;

    public Leaderboard() {}

    public Leaderboard(String id, String name, LeaderboardType type, String seasonKey, String raceName, Location location, UUID creatorUuid) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.seasonKey = seasonKey;
        this.raceName = raceName;
        this.location = location;
        this.creatorUuid = creatorUuid;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LeaderboardType getType() { return type; }
    public void setType(LeaderboardType type) { this.type = type; }

    public String getSeasonKey() { return seasonKey; }
    public void setSeasonKey(String seasonKey) { this.seasonKey = seasonKey; }

    public String getRaceName() { return raceName; }
    public void setRaceName(String raceName) { this.raceName = raceName; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public UUID getCreatorUuid() { return creatorUuid; }
    public void setCreatorUuid(UUID creatorUuid) { this.creatorUuid = creatorUuid; }

    public boolean isRaceResults() {
        return type == LeaderboardType.RACE_RESULTS;
    }
}