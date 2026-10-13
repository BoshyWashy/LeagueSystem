package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.Leaderboard;
import io.bbrl.leaguesystem.model.LeaderboardType;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.util.*;

/**
 * Leaderboard CRUD operations.
 */
public final class DatabaseLeaderboard {

    private final DatabaseCore core;

    public DatabaseLeaderboard(DatabaseCore core) {
        this.core = core;
    }

    /* ---------------------------------------------------------- */
    /*  leaderboards                                               */
    /* ---------------------------------------------------------- */
    public Map<String, Leaderboard> loadLeaderboards(String leagueId) {
        Map<String, Leaderboard> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement("SELECT * FROM leaderboards WHERE league_id=?")) {
            ps.setString(1, leagueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String worldName = rs.getString("world");
                    Location loc = null;
                    if (worldName != null) {
                        org.bukkit.World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            loc = new Location(world, rs.getDouble("x"), rs.getDouble("y"),
                                    rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                        }
                    }

                    LeaderboardType type;
                    try {
                        type = LeaderboardType.valueOf(rs.getString("type"));
                    } catch (Exception e) {
                        continue; // Skip invalid types
                    }

                    String raceName = rs.getString("race_name");

                    Leaderboard lb = new Leaderboard(
                            rs.getString("id"),
                            rs.getString("name"),
                            type,
                            rs.getString("season_key"),
                            raceName,
                            loc,
                            UUID.fromString(rs.getString("creator_uuid"))
                    );
                    map.put(lb.getId(), lb);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public void saveLeaderboards(String leagueId, Map<String, Leaderboard> leaderboards) {
        try {
            Connection conn = core.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM leaderboards WHERE league_id=?")) {
                del.setString(1, leagueId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO leaderboards(id,league_id,name,type,season_key,race_name,world,x,y,z,yaw,pitch,creator_uuid) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (Leaderboard lb : leaderboards.values()) {
                    ins.setString(1, lb.getId());
                    ins.setString(2, leagueId);
                    ins.setString(3, lb.getName());
                    ins.setString(4, lb.getType().name());
                    ins.setString(5, lb.getSeasonKey());
                    ins.setString(6, lb.getRaceName());
                    if (lb.getLocation() != null) {
                        ins.setString(7, lb.getLocation().getWorld().getName());
                        ins.setDouble(8, lb.getLocation().getX());
                        ins.setDouble(9, lb.getLocation().getY());
                        ins.setDouble(10, lb.getLocation().getZ());
                        ins.setFloat(11, lb.getLocation().getYaw());
                        ins.setFloat(12, lb.getLocation().getPitch());
                    } else {
                        ins.setNull(7, Types.VARCHAR);
                        ins.setDouble(8, 0);
                        ins.setDouble(9, 0);
                        ins.setDouble(10, 0);
                        ins.setFloat(11, 0);
                        ins.setFloat(12, 0);
                    }
                    ins.setString(13, lb.getCreatorUuid().toString());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            try { core.getConnection().rollback(); } catch (SQLException ignored) {}
        } finally {
            try { core.getConnection().setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }
}
