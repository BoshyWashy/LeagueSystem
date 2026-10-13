package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.LeagueConfig;
import io.bbrl.leaguesystem.model.Season;

import java.sql.*;
import java.util.*;

/**
 * League CRUD operations.
 */
public final class DatabaseLeague {

    private final DatabaseCore core;

    public DatabaseLeague(DatabaseCore core) {
        this.core = core;
    }

    /* ---------------------------------------------------------- */
    /*  league                                                     */
    /* ---------------------------------------------------------- */
    public List<League> allLeagues() {
        List<League> out = new ArrayList<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement("SELECT * FROM leagues");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                League l = core.buildLeague(rs);
                loadSeasonsIntoLeague(l);
                out.add(l);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    public Optional<League> getLeague(String id) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("SELECT * FROM leagues WHERE id=?")) {
            ps.setString(1, id.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    League l = core.buildLeague(rs);
                    loadSeasonsIntoLeague(l);
                    return Optional.of(l);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private void loadSeasonsIntoLeague(League l) throws SQLException {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT season_key, race_count, is_finished FROM seasons WHERE league_id=?")) {
            ps.setString(1, l.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("season_key");
                    Season season = new Season(key);
                    l.getSeasons().put(key, season);
                    if (rs.getInt("is_finished") == 1) {
                        l.finishSeason(key);
                    }
                }
            }
        }
    }

    public void saveLeague(League l) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO leagues(id,name,description,max_team_own,max_drivers,max_reserves,point_system,holograms,holo_refresh,strike_races,current_season,primary_color,secondary_color,allow_anyone_create_team,fastest_lap_points,spawn_world,spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch,spawn_set,holo_leaderboard_length,holo_y_offset)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                  name=excluded.name,
                  description=excluded.description,
                  max_team_own=excluded.max_team_own,
                  max_drivers=excluded.max_drivers,
                  max_reserves=excluded.max_reserves,
                  point_system=excluded.point_system,
                  holograms=excluded.holograms,
                  holo_refresh=excluded.holo_refresh,
                  strike_races=excluded.strike_races,
                  current_season=excluded.current_season,
                  primary_color=excluded.primary_color,
                  secondary_color=excluded.secondary_color,
                  allow_anyone_create_team=excluded.allow_anyone_create_team,
                  fastest_lap_points=excluded.fastest_lap_points,
                  spawn_world=excluded.spawn_world,
                  spawn_x=excluded.spawn_x,
                  spawn_y=excluded.spawn_y,
                  spawn_z=excluded.spawn_z,
                  spawn_yaw=excluded.spawn_yaw,
                  spawn_pitch=excluded.spawn_pitch,
                  spawn_set=excluded.spawn_set,
                  holo_leaderboard_length=excluded.holo_leaderboard_length,
                  holo_y_offset=excluded.holo_y_offset
                """)) {
            ps.setString(1, l.getId());
            ps.setString(2, l.getName());
            ps.setString(3, l.getDescription());
            LeagueConfig c = l.getConfig();
            ps.setInt(4, c.getMaxTeamOwnership());
            ps.setInt(5, c.getMaxDriversPerTeam());
            ps.setInt(6, c.getMaxReservesPerTeam());
            ps.setString(7, c.getPointSystem());
            ps.setInt(8, c.isHologramsEnabled() ? 1 : 0);
            ps.setInt(9, c.getHologramRefreshSeconds());
            ps.setInt(10, c.getStrikeRaces());
            ps.setString(11, l.getCurrentSeason());
            ps.setString(12, c.getPrimaryColor());
            ps.setString(13, c.getSecondaryColor());
            ps.setInt(14, c.isAllowAnyoneCreateTeam() ? 1 : 0);
            ps.setInt(15, c.getFastestLapPoints());
            ps.setString(16, c.getSpawnWorld());
            ps.setDouble(17, c.getSpawnX());
            ps.setDouble(18, c.getSpawnY());
            ps.setDouble(19, c.getSpawnZ());
            ps.setFloat(20, c.getSpawnYaw());
            ps.setFloat(21, c.getSpawnPitch());
            ps.setInt(22, c.isSpawnSet() ? 1 : 0);
            ps.setInt(23, c.getHologramLeaderboardLength());
            ps.setDouble(24, c.getHologramYOffset());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteLeague(String id) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("DELETE FROM leagues WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void renameLeague(String oldId, String newId, String newName) {
        Connection conn = core.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE leagues SET id=?, name=? WHERE id=?")) {
                ps.setString(1, newId.toLowerCase());
                ps.setString(2, newName);
                ps.setString(3, oldId.toLowerCase());
                ps.executeUpdate();
            }
            String[] tables = {"seasons", "teams", "team_owners", "team_members", "races", "race_results",
                    "race_team_results", "fastest_laps", "championship", "team_championship", "point_scales", "manual_adjustments", "leaderboards"};
            for (String table : tables) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table + " SET league_id=? WHERE league_id=?")) {
                    ps.setString(1, newId.toLowerCase());
                    ps.setString(2, oldId.toLowerCase());
                    ps.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }
}
