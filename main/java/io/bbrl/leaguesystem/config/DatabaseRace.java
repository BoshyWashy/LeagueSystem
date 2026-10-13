package io.bbrl.leaguesystem.config;

import java.sql.*;
import java.util.*;

/**
 * Race, race results, race team results, and fastest lap operations.
 * NEW: Added race finalization support, per-race driver-team tracking,
 * race numbers, and season calendar support.
 */
public final class DatabaseRace {

    private final DatabaseCore core;

    public DatabaseRace(DatabaseCore core) {
        this.core = core;
    }

    /* ---------------------------------------------------------- */
    /*  races & results                                            */
    /* ---------------------------------------------------------- */
    public void saveRace(String leagueId, String season, String raceName) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "INSERT INTO races(league_id,season_key,race_name,is_finished) VALUES (?,?,?,0) ON CONFLICT DO NOTHING")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteRace(String leagueId, String season, String raceName) {
        Connection conn = core.getConnection();
        try {
            conn.setAutoCommit(false);

            // Delete race results first
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM race_results WHERE league_id=? AND season_key=? AND race_name=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                ps.setString(3, raceName);
                ps.executeUpdate();
            }

            // Delete race results team associations
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM race_results_team WHERE league_id=? AND season_key=? AND race_name=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                ps.setString(3, raceName);
                ps.executeUpdate();
            }

            // Delete race team results
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM race_team_results WHERE league_id=? AND season_key=? AND race_name=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                ps.setString(3, raceName);
                ps.executeUpdate();
            }

            // Delete fastest lap
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM fastest_laps WHERE league_id=? AND season_key=? AND race_name=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                ps.setString(3, raceName);
                ps.executeUpdate();
            }

            // Delete calendar entry for this race
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM calendar WHERE league_id=? AND season_key=? AND race_name=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                ps.setString(3, raceName);
                ps.executeUpdate();
            }

            // Delete the race itself
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM races WHERE league_id=? AND season_key=? AND race_name=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                ps.setString(3, raceName);
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

    public List<String> listRaces(String leagueId, String season) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT race_name FROM races WHERE league_id=? AND season_key=? ORDER BY race_number, race_name")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    /* ---------------------------------------------------------- */
    /*  race number                                                */
    /* ---------------------------------------------------------- */
    public void setRaceNumber(String leagueId, String season, String raceName, int raceNumber) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "UPDATE races SET race_number=? WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setInt(1, raceNumber);
            ps.setString(2, leagueId);
            ps.setString(3, season);
            ps.setString(4, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getRaceNumber(String leagueId, String season, String raceName) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT race_number FROM races WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("race_number");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /* ---------------------------------------------------------- */
    /*  calendar                                                   */
    /* ---------------------------------------------------------- */
    public void saveCalendarEntry(String leagueId, String season, int raceNumber, String trackName, String raceName) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO calendar(league_id,season_key,race_number,track_name,race_name)
                VALUES (?,?,?,?,?)
                ON CONFLICT(league_id,season_key,race_number)
                DO UPDATE SET track_name=excluded.track_name, race_name=excluded.race_name
                """)) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setInt(3, raceNumber);
            ps.setString(4, trackName);
            ps.setString(5, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteCalendarEntry(String leagueId, String season, int raceNumber) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM calendar WHERE league_id=? AND season_key=? AND race_number=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setInt(3, raceNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<Integer, String> getCalendar(String leagueId, String season) {
        Map<Integer, String> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT race_number, track_name FROM calendar WHERE league_id=? AND season_key=? ORDER BY race_number")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("race_number"), rs.getString("track_name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public String getCalendarRaceName(String leagueId, String season, int raceNumber) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT race_name FROM calendar WHERE league_id=? AND season_key=? AND race_number=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setInt(3, raceNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("race_name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* ---------------------------------------------------------- */
    /*  race finalization                                          */
    /* ---------------------------------------------------------- */
    public boolean isRaceFinished(String leagueId, String season, String raceName) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT is_finished FROM races WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
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

    public void setRaceFinished(String leagueId, String season, String raceName, boolean finished) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "UPDATE races SET is_finished=? WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setInt(1, finished ? 1 : 0);
            ps.setString(2, leagueId);
            ps.setString(3, season);
            ps.setString(4, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* ---------------------------------------------------------- */
    /*  race results                                               */
    /* ---------------------------------------------------------- */
    public void saveRaceResult(String leagueId, String season, String raceName, String uuid, int position, int points) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO race_results(league_id,season_key,race_name,uuid,position,points)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(league_id,season_key,race_name,uuid)
                DO UPDATE SET position=excluded.position, points=excluded.points
                """)) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, uuid);
            ps.setInt(5, position);
            ps.setInt(6, points);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteRaceResult(String leagueId, String season, String raceName, String uuid) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM race_results WHERE league_id=? AND season_key=? AND race_name=? AND uuid=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Integer> getRaceResults(String leagueId, String season, String raceName) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT uuid,points FROM race_results WHERE league_id=? AND season_key=? AND race_name=? ORDER BY CASE WHEN position = 0 THEN 999999 ELSE position END")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /* ---------------------------------------------------------- */
    /*  race results team (which team each driver drove for)         */
    /* ---------------------------------------------------------- */
    /* ---------------------------------------------------------- */
    /*  race result positions (for DNF display)                    */
    /* ---------------------------------------------------------- */
    public Map<String, Integer> getRaceResultPositions(String leagueId, String season, String raceName) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT uuid, position FROM race_results WHERE league_id=? AND season_key=? AND race_name=? ORDER BY CASE WHEN position = 0 THEN 999999 ELSE position END")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public void saveRaceResultTeam(String leagueId, String season, String raceName, String uuid, String teamId) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO race_results_team(league_id,season_key,race_name,uuid,team_id)
                VALUES (?,?,?,?,?)
                ON CONFLICT(league_id,season_key,race_name,uuid)
                DO UPDATE SET team_id=excluded.team_id
                """)) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, uuid);
            ps.setString(5, teamId != null ? teamId.toLowerCase() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteRaceResultTeam(String leagueId, String season, String raceName, String uuid) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM race_results_team WHERE league_id=? AND season_key=? AND race_name=? AND uuid=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, String> getRaceResultTeams(String leagueId, String season, String raceName) {
        Map<String, String> map = new HashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT uuid, team_id FROM race_results_team WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString("uuid"), rs.getString("team_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public String getDriverTeamForRace(String leagueId, String season, String raceName, String uuid) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT team_id FROM race_results_team WHERE league_id=? AND season_key=? AND race_name=? AND uuid=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("team_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* ---------------------------------------------------------- */
    /*  race team results                                          */
    /* ---------------------------------------------------------- */
    public void saveRaceTeamResult(String leagueId, String season, String raceName, String teamId, double points) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO race_team_results(league_id,season_key,race_name,team_id,points)
                VALUES (?,?,?,?,?)
                ON CONFLICT(league_id,season_key,race_name,team_id)
                DO UPDATE SET points=excluded.points
                """)) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, teamId);
            ps.setDouble(5, points);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteRaceTeamResult(String leagueId, String season, String raceName, String teamId) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM race_team_results WHERE league_id=? AND season_key=? AND race_name=? AND team_id=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Double> getRaceTeamResults(String leagueId, String season, String raceName) {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT team_id,points FROM race_team_results WHERE league_id=? AND season_key=? AND race_name=? ORDER BY points DESC")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString(1), rs.getDouble(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /* ---------------------------------------------------------- */
    /*  fastest lap                                                */
    /* ---------------------------------------------------------- */
    public void saveFastestLap(String leagueId, String season, String raceName, String uuid, int points) {
        try (PreparedStatement ps = core.getConnection().prepareStatement("""
                INSERT INTO fastest_laps(league_id,season_key,race_name,uuid,points)
                VALUES (?,?,?,?,?)
                ON CONFLICT(league_id,season_key,race_name)
                DO UPDATE SET uuid=excluded.uuid, points=excluded.points
                """)) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.setString(4, uuid);
            ps.setInt(5, points);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteFastestLap(String leagueId, String season, String raceName) {
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "DELETE FROM fastest_laps WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Object> getFastestLap(String leagueId, String season, String raceName) {
        Map<String, Object> result = new HashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT uuid, points FROM fastest_laps WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("uuid", rs.getString("uuid"));
                    result.put("points", rs.getInt("points"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
}