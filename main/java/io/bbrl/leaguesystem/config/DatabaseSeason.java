package io.bbrl.leaguesystem.config;

import java.sql.*;

/**
 * Season CRUD operations.
 */
public final class DatabaseSeason {

    private final DatabaseCore core;

    public DatabaseSeason(DatabaseCore core) {
        this.core = core;
    }

    /* ---------------------------------------------------------- */
    /*  seasons                                                    */
    /* ---------------------------------------------------------- */
    public void createSeason(String leagueId, String key, String raceCount) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "INSERT INTO seasons(league_id,season_key,race_count,is_finished) VALUES (?,?,?,0) ON CONFLICT DO NOTHING")) {
            ps.setString(1, leagueId);
            ps.setString(2, key);
            ps.setString(3, raceCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteSeason(String leagueId, String key) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM seasons WHERE league_id=? AND season_key=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void renameSeason(String leagueId, String oldKey, String newKey) {
        Connection conn = core.getConnection();
        try {
            conn.setAutoCommit(false);

            // Update seasons table
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seasons SET season_key=? WHERE league_id=? AND season_key=?")) {
                ps.setString(1, newKey);
                ps.setString(2, leagueId);
                ps.setString(3, oldKey);
                ps.executeUpdate();
            }

            // Update all related tables
            String[] tables = {"races", "race_results", "race_team_results", "fastest_laps",
                    "championship", "team_championship", "manual_adjustments"};
            for (String table : tables) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table + " SET season_key=? WHERE league_id=? AND season_key=?")) {
                    ps.setString(1, newKey);
                    ps.setString(2, leagueId);
                    ps.setString(3, oldKey);
                    ps.executeUpdate();
                }
            }

            // Update leaderboards that reference this season
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE leaderboards SET season_key=? WHERE league_id=? AND season_key=?")) {
                ps.setString(1, newKey);
                ps.setString(2, leagueId);
                ps.setString(3, oldKey);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void setSeasonFinished(String leagueId, String key, boolean finished) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "UPDATE seasons SET is_finished=? WHERE league_id=? AND season_key=?")) {
            ps.setInt(1, finished ? 1 : 0);
            ps.setString(2, leagueId);
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isSeasonFinished(String leagueId, String key) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT is_finished FROM seasons WHERE league_id=? AND season_key=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_finished") == 1;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setSeasonRaceCount(String leagueId, String key, String display) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "UPDATE seasons SET race_count=? WHERE league_id=? AND season_key=?")) {
            ps.setString(1, display);
            ps.setString(2, leagueId);
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
