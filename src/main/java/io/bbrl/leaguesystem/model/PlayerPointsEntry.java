package io.bbrl.leaguesystem.model;

public class PlayerPointsEntry {
    private String playerUuid;
    private int points;

    public PlayerPointsEntry() {}
    public PlayerPointsEntry(String playerUuid, int points) {
        this.playerUuid = playerUuid;
        this.points = points;
    }

    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
}
