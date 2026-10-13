package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.Team;

import java.sql.*;
import java.util.*;

/**
 * Championship rebuild, manual adjustments, and point scales operations.
 * Updated to support per-race team tracking - points stay with the team
 * the driver was driving for at the time of each race.
 */
public final class DatabaseChampionship {

    private final DatabaseCore core;
    private final DatabaseTeam teamDb;
    private final DatabaseRace raceDb;

    public DatabaseChampionship(DatabaseCore core, DatabaseTeam teamDb, DatabaseRace raceDb) {
        this.core = core;
        this.teamDb = teamDb;
        this.raceDb = raceDb;
    }

    /* ---------------------------------------------------------- */
    /*  manual adjustments                                         */
    /* ---------------------------------------------------------- */
    public void addManualAdjustment(String leagueId, String season, String uuid, double delta) {
        double current = getManualAdjustment(leagueId, season, uuid);
        double newTotal = current + delta;

        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO manual_adjustments(league_id,season_key,uuid,adjustment)
                VALUES (?,?,?,?)
                ON CONFLICT(league_id,season_key,uuid)
                DO UPDATE SET adjustment=excluded.adjustment
                """)) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, uuid);
            ps.setDouble(4, newTotal);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public double getManualAdjustment(String leagueId, String season, String uuid) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT adjustment FROM manual_adjustments WHERE league_id=? AND season_key=? AND uuid=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public Map<String, Double> getAllManualAdjustments(String leagueId, String season) {
        Map<String, Double> map = new HashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT uuid, adjustment FROM manual_adjustments WHERE league_id=? AND season_key=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getDouble(2));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /* ---------------------------------------------------------- */
    /*  championship                                               */
    /* ---------------------------------------------------------- */
    public void rebuildChampionship(String leagueId, String season) {
        Connection conn = core.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement delD = conn.prepareStatement(
                    "DELETE FROM championship WHERE league_id=? AND season_key=?");
                 PreparedStatement delT = conn.prepareStatement(
                         "DELETE FROM team_championship WHERE league_id=? AND season_key=?")) {
                delD.setString(1, leagueId);
                delD.setString(2, season);
                delD.executeUpdate();
                delT.setString(1, leagueId);
                delT.setString(2, season);
                delT.executeUpdate();
            }

            /* driver totals - aggregate all race results */
            Map<String, Double> driverTotals = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, SUM(points) FROM race_results WHERE league_id=? AND season_key=? GROUP BY uuid")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) driverTotals.put(rs.getString(1), rs.getDouble(2));
                }
            }

            /* ADD fastest lap points */
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, points FROM fastest_laps WHERE league_id=? AND season_key=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuid = rs.getString("uuid");
                        int points = rs.getInt("points");
                        driverTotals.merge(uuid, (double) points, Double::sum);
                    }
                }
            }

            /* ADD manual adjustments to driver totals */
            Map<String, Double> adjustments = getAllManualAdjustments(leagueId, season);
            for (Map.Entry<String, Double> adj : adjustments.entrySet()) {
                driverTotals.merge(adj.getKey(), adj.getValue(), Double::sum);
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO championship(league_id,season_key,uuid,points) VALUES (?,?,?,?)")) {
                for (Map.Entry<String, Double> e : driverTotals.entrySet()) {
                    if (e.getValue() != 0) {
                        ins.setString(1, leagueId);
                        ins.setString(2, season);
                        ins.setString(3, e.getKey());
                        ins.setDouble(4, e.getValue());
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }

            /*
             * NEW: Team totals using per-race team tracking.
             * For each race result, we look up which team the driver was driving for
             * in THAT specific race (from race_results_team table).
             * If no specific team is recorded, we fall back to their current team.
             * This ensures points stay with the team they were earned for.
             */
            Map<String, Double> teamTotals = new HashMap<>();
            Map<String, Team> teams = teamDb.loadTeams(leagueId);

            // Get all finished races for this season
            List<String> races = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT race_name FROM races WHERE league_id=? AND season_key=? AND is_finished=1")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) races.add(rs.getString(1));
                }
            }

            // For each finished race, aggregate points by the team each driver was driving for
            for (String raceName : races) {
                // Get race results with their team associations
                Map<String, String> driverTeams = raceDb.getRaceResultTeams(leagueId, season, raceName);
                Map<String, Integer> raceResults = raceDb.getRaceResults(leagueId, season, raceName);

                for (Map.Entry<String, Integer> result : raceResults.entrySet()) {
                    String uuid = result.getKey();
                    int points = result.getValue();

                    // Look up which team this driver was driving for in this race
                    String teamId = driverTeams.get(uuid);

                    // If no specific team recorded, try to find their current team
                    if (teamId == null || teamId.isEmpty()) {
                        for (Team t : teams.values()) {
                            if (t.getMembers().contains(uuid) || t.getReserves().contains(uuid) || t.isOwner(uuid)) {
                                teamId = t.getId();
                                break;
                            }
                        }
                    }

                    if (teamId != null && !teamId.isEmpty()) {
                        teamTotals.merge(teamId, (double) points, Double::sum);
                    }
                }

                // Add fastest lap points to the team the driver was driving for in this race
                Map<String, Object> fastestLap = raceDb.getFastestLap(leagueId, season, raceName);
                if (!fastestLap.isEmpty()) {
                    String flUuid = (String) fastestLap.get("uuid");
                    int flPoints = (int) fastestLap.get("points");
                    String flTeamId = driverTeams.get(flUuid);

                    if (flTeamId == null || flTeamId.isEmpty()) {
                        for (Team t : teams.values()) {
                            if (t.getMembers().contains(flUuid) || t.getReserves().contains(flUuid) || t.isOwner(flUuid)) {
                                flTeamId = t.getId();
                                break;
                            }
                        }
                    }

                    if (flTeamId != null && !flTeamId.isEmpty()) {
                        teamTotals.merge(flTeamId, (double) flPoints, Double::sum);
                    }
                }
            }

            /* Add manual adjustments to team totals (adjustments follow the driver, not team) */
            // Manual adjustments are driver-specific and don't transfer to teams

            /* Add race_team_results (direct team points from team standings races) */
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT team_id, SUM(points) as total FROM race_team_results WHERE league_id=? AND season_key=? GROUP BY team_id")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String teamId = rs.getString("team_id");
                        double points = rs.getDouble("total");
                        teamTotals.merge(teamId, points, Double::sum);
                    }
                }
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO team_championship(league_id,season_key,team_id,points) VALUES (?,?,?,?)")) {
                for (Map.Entry<String, Double> e : teamTotals.entrySet()) {
                    if (e.getValue() != 0) {
                        ins.setString(1, leagueId);
                        ins.setString(2, season);
                        ins.setString(3, e.getKey());
                        ins.setDouble(4, e.getValue());
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public Map<String, Double> getDriverStandings(String leagueId, String season) {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT uuid,points FROM championship WHERE league_id=? AND season_key=? ORDER BY points DESC")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double points = rs.getDouble(2);
                    if (points != 0) map.put(rs.getString(1), points);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public Map<String, Double> getTeamStandings(String leagueId, String season) {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT team_id,points FROM team_championship WHERE league_id=? AND season_key=? ORDER BY points DESC")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double points = rs.getDouble(2);
                    if (points != 0) map.put(rs.getString(1), points);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /* ---------------------------------------------------------- */
    /*  point scales                                               */
    /* ---------------------------------------------------------- */
    public void savePointScales(String leagueId, Map<String, Map<Integer, Integer>> scales) {
        try {
            Connection conn = core.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM point_scales WHERE league_id=?")) {
                del.setString(1, leagueId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO point_scales(league_id,scale,position,points) VALUES (?,?,?,?)")) {
                for (Map.Entry<String, Map<Integer, Integer>> e : scales.entrySet()) {
                    for (Map.Entry<Integer, Integer> p : e.getValue().entrySet()) {
                        ins.setString(1, leagueId);
                        ins.setString(2, e.getKey());
                        ins.setInt(3, p.getKey());
                        ins.setInt(4, p.getValue());
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException ex) {
            ex.printStackTrace();
            try { core.getConnection().rollback(); } catch (SQLException ignored) {}
        } finally {
            try { core.getConnection().setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void deletePointScale(String leagueId, String scaleName) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM point_scales WHERE league_id=? AND scale=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, scaleName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Map<Integer, Integer>> loadPointScales(String leagueId) {
        Map<String, Map<Integer, Integer>> out = new HashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT scale,position,points FROM point_scales WHERE league_id=?")) {
            ps.setString(1, leagueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getString(1), k -> new HashMap<>())
                            .put(rs.getInt(2), rs.getInt(3));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }
}