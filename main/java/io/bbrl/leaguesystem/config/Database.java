package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * SINGLE SOURCE OF TRUTH – every mutation is written to the DB immediately.
 * No caches, no maps, no "save" methods – reads are always fresh SQL.
 */
public final class Database {

    private final Connection conn;

    public Database(File dataFolder) {
        File db = new File(dataFolder, "leagues.db");
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            createSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open SQLite database", e);
        }
    }

    /* ---------------------------------------------------------- */
    /*  schema – run once                                           */
    /* ---------------------------------------------------------- */
    private void createSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS leagues(
                  id            TEXT PRIMARY KEY,
                  name          TEXT NOT NULL,
                  max_team_own  INTEGER DEFAULT 1,
                  max_drivers   INTEGER DEFAULT 3,
                  max_reserves  INTEGER DEFAULT 1,
                  point_system  TEXT    DEFAULT 'f1',
                  holograms     INTEGER DEFAULT 1,
                  holo_refresh  INTEGER DEFAULT 30,
                  strike_races  INTEGER DEFAULT 0,
                  current_season TEXT,
                  primary_color TEXT DEFAULT '§6',
                  secondary_color TEXT DEFAULT '§f'
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS seasons(
                  league_id  TEXT,
                  season_key TEXT,
                  race_count TEXT,
                  PRIMARY KEY (league_id, season_key),
                  FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS teams(
                  league_id  TEXT,
                  team_id    TEXT,
                  name       TEXT,
                  hex_color  TEXT,
                  owner_uuid TEXT,
                  PRIMARY KEY (league_id, team_id),
                  FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS team_members(
                  league_id  TEXT,
                  team_id    TEXT,
                  uuid       TEXT,
                  is_reserve INTEGER,
                  sort_order INTEGER DEFAULT 0,
                  PRIMARY KEY (league_id, team_id, uuid),
                  FOREIGN KEY (league_id, team_id) REFERENCES teams(league_id, team_id) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS races(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  PRIMARY KEY (league_id, season_key, race_name),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS race_results(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  uuid       TEXT,
                  position   INTEGER,
                  points     INTEGER,
                  PRIMARY KEY (league_id, season_key, race_name, uuid),
                  FOREIGN KEY (league_id, season_key, race_name) REFERENCES races(league_id, season_key, race_name) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS race_team_results(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  team_id    TEXT,
                  points     REAL,
                  PRIMARY KEY (league_id, season_key, race_name, team_id),
                  FOREIGN KEY (league_id, season_key, race_name) REFERENCES races(league_id, season_key, race_name) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS championship(
                  league_id  TEXT,
                  season_key TEXT,
                  uuid       TEXT,
                  points     REAL,
                  PRIMARY KEY (league_id, season_key, uuid),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS team_championship(
                  league_id  TEXT,
                  season_key TEXT,
                  team_id    TEXT,
                  points     REAL,
                  PRIMARY KEY (league_id, season_key, team_id),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS point_scales(
                  league_id TEXT,
                  scale     TEXT,
                  position  INTEGER,
                  points    INTEGER,
                  PRIMARY KEY (league_id, scale, position),
                  FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE
                );
                """);
        }
    }

    /* ---------------------------------------------------------- */
    /*  league                                                     */
    /* ---------------------------------------------------------- */
    public List<League> allLeagues() {
        List<League> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM leagues");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                League l = buildLeague(rs);
                loadSeasonsIntoLeague(l);
                out.add(l);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    public Optional<League> getLeague(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM leagues WHERE id=?")) {
            ps.setString(1, id.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    League l = buildLeague(rs);
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
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT season_key, race_count FROM seasons WHERE league_id=?")) {
            ps.setString(1, l.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("season_key");
                    Season season = new Season(key);
                    l.getSeasons().put(key, season);
                }
            }
        }
    }

    public void saveLeague(League l) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO leagues(id,name,max_team_own,max_drivers,max_reserves,point_system,holograms,holo_refresh,strike_races,current_season,primary_color,secondary_color)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                  name=excluded.name,
                  max_team_own=excluded.max_team_own,
                  max_drivers=excluded.max_drivers,
                  max_reserves=excluded.max_reserves,
                  point_system=excluded.point_system,
                  holograms=excluded.holograms,
                  holo_refresh=excluded.holo_refresh,
                  strike_races=excluded.strike_races,
                  current_season=excluded.current_season,
                  primary_color=excluded.primary_color,
                  secondary_color=excluded.secondary_color
                """)) {
            ps.setString(1, l.getId());
            ps.setString(2, l.getName());
            LeagueConfig c = l.getConfig();
            ps.setInt(3, c.getMaxTeamOwnership());
            ps.setInt(4, c.getMaxDriversPerTeam());
            ps.setInt(5, c.getMaxReservesPerTeam());
            ps.setString(6, c.getPointSystem());
            ps.setInt(7, c.isHologramsEnabled() ? 1 : 0);
            ps.setInt(8, c.getHologramRefreshSeconds());
            ps.setInt(9, c.getStrikeRaces());
            ps.setString(10, l.getCurrentSeason());
            ps.setString(11, c.getPrimaryColor());
            ps.setString(12, c.getSecondaryColor());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteLeague(String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM leagues WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void renameLeague(String oldId, String newId, String newName) {
        try {
            conn.setAutoCommit(false);
            // Update leagues table
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE leagues SET id=?, name=? WHERE id=?")) {
                ps.setString(1, newId.toLowerCase());
                ps.setString(2, newName);
                ps.setString(3, oldId.toLowerCase());
                ps.executeUpdate();
            }
            // Update all related tables
            String[] tables = {"seasons", "teams", "team_members", "races", "race_results",
                    "race_team_results", "championship", "team_championship", "point_scales"};
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

    /* ---------------------------------------------------------- */
    /*  seasons                                                    */
    /* ---------------------------------------------------------- */
    public void createSeason(String leagueId, String key, String raceCount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO seasons(league_id,season_key,race_count) VALUES (?,?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, leagueId);
            ps.setString(2, key);
            ps.setString(3, raceCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteSeason(String leagueId, String key) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM seasons WHERE league_id=? AND season_key=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setSeasonRaceCount(String leagueId, String key, String display) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE seasons SET race_count=? WHERE league_id=? AND season_key=?")) {
            ps.setString(1, display);
            ps.setString(2, leagueId);
            ps.setString(3, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* ---------------------------------------------------------- */
    /*  teams                                                      */
    /* ---------------------------------------------------------- */
    public Map<String, Team> loadTeams(String leagueId) {
        Map<String, Team> map = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM teams WHERE league_id=?")) {
            ps.setString(1, leagueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Team t = new Team(rs.getString("team_id"), rs.getString("name"),
                            rs.getString("hex_color"), rs.getString("owner_uuid"));
                    map.put(t.getId(), t);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // members with sort order
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM team_members WHERE league_id=? ORDER BY sort_order")) {
            ps.setString(1, leagueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Team t = map.get(rs.getString("team_id"));
                    if (t == null) continue;
                    String uuid = rs.getString("uuid");
                    if (rs.getInt("is_reserve") == 1) t.getReserves().add(uuid);
                    else t.getMembers().add(uuid);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public void saveTeams(String leagueId, Map<String, Team> teams) {
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement delM = conn.prepareStatement("DELETE FROM team_members WHERE league_id=?")) {
                delM.setString(1, leagueId);
                delM.executeUpdate();
            }
            try (PreparedStatement delT = conn.prepareStatement("DELETE FROM teams WHERE league_id=?")) {
                delT.setString(1, leagueId);
                delT.executeUpdate();
            }
            try (PreparedStatement insT = conn.prepareStatement(
                    "INSERT INTO teams(league_id,team_id,name,hex_color,owner_uuid) VALUES (?,?,?,?,?)")) {
                for (Team t : teams.values()) {
                    insT.setString(1, leagueId);
                    insT.setString(2, t.getId());
                    insT.setString(3, t.getName());
                    insT.setString(4, t.getHexColor());
                    insT.setString(5, t.getOwnerUuid());
                    insT.addBatch();
                }
                insT.executeBatch();
            }
            Set<String> seen = new HashSet<>();
            int sortOrder = 0;
            try (PreparedStatement insM = conn.prepareStatement(
                    "INSERT INTO team_members(league_id,team_id,uuid,is_reserve,sort_order) VALUES (?,?,?,?,?)")) {
                for (Team t : teams.values()) {
                    sortOrder = 0;
                    for (String u : t.getMembers()) {
                        String key = t.getId() + ":" + u;
                        if (seen.add(key)) {
                            insM.setString(1, leagueId);
                            insM.setString(2, t.getId());
                            insM.setString(3, u);
                            insM.setInt(4, 0);
                            insM.setInt(5, sortOrder++);
                            insM.addBatch();
                        }
                    }
                    for (String u : t.getReserves()) {
                        String key = t.getId() + ":" + u;
                        if (seen.add(key)) {
                            insM.setString(1, leagueId);
                            insM.setString(2, t.getId());
                            insM.setString(3, u);
                            insM.setInt(4, 1);
                            insM.setInt(5, sortOrder++);
                            insM.addBatch();
                        }
                    }
                }
                insM.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /* ---------------------------------------------------------- */
    /*  races & results                                            */
    /* ---------------------------------------------------------- */
    public void saveRace(String leagueId, String season, String raceName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO races(league_id,season_key,race_name) VALUES (?,?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteRace(String leagueId, String season, String raceName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM races WHERE league_id=? AND season_key=? AND race_name=?")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            ps.setString(3, raceName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> listRaces(String leagueId, String season) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT race_name FROM races WHERE league_id=? AND season_key=?")) {
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

    public void saveRaceResult(String leagueId, String season, String raceName, String uuid, int position, int points) {
        try (PreparedStatement ps = conn.prepareStatement("""
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
        try (PreparedStatement ps = conn.prepareStatement(
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

    public void saveRaceTeamResult(String leagueId, String season, String raceName, String teamId, double points) {
        try (PreparedStatement ps = conn.prepareStatement("""
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

    public Map<String, Integer> getRaceResults(String leagueId, String season, String raceName) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid,points FROM race_results WHERE league_id=? AND season_key=? AND race_name=? ORDER BY position")) {
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
    /*  championship                                               */
    /* ---------------------------------------------------------- */
    public void rebuildChampionship(String leagueId, String season) {
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
            /* driver totals */
            Map<String, Double> driverTotals = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, SUM(points) FROM race_results WHERE league_id=? AND season_key=? GROUP BY uuid")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) driverTotals.put(rs.getString(1), rs.getDouble(2));
                }
            }
            /* Add team results to driver totals */
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT team_id, points FROM race_team_results WHERE league_id=? AND season_key=?")) {
                ps.setString(1, leagueId);
                ps.setString(2, season);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String teamId = rs.getString("team_id");
                        double points = rs.getDouble("points");
                        // Distribute to team members or add to team championship directly
                        // For now, add to team championship
                    }
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO championship(league_id,season_key,uuid,points) VALUES (?,?,?,?)")) {
                for (Map.Entry<String, Double> e : driverTotals.entrySet()) {
                    if (e.getValue() > 0) { // Only add if has points
                        ins.setString(1, leagueId);
                        ins.setString(2, season);
                        ins.setString(3, e.getKey());
                        ins.setDouble(4, e.getValue());
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            /* team totals */
            Map<String, Team> teams = loadTeams(leagueId);
            Map<String, Double> teamTotals = new HashMap<>();
            for (Map.Entry<String, Double> e : driverTotals.entrySet()) {
                for (Team t : teams.values()) {
                    if (t.getMembers().contains(e.getKey())) {
                        teamTotals.merge(t.getId(), e.getValue(), Double::sum);
                        break;
                    }
                }
            }
            /* Add race_team_results to team totals */
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
                    if (e.getValue() > 0) { // Only add if has points
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
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid,points FROM championship WHERE league_id=? AND season_key=? ORDER BY points DESC")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double points = rs.getDouble(2);
                    if (points > 0) map.put(rs.getString(1), points);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public Map<String, Double> getTeamStandings(String leagueId, String season) {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT team_id,points FROM team_championship WHERE league_id=? AND season_key=? ORDER BY points DESC")) {
            ps.setString(1, leagueId);
            ps.setString(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double points = rs.getDouble(2);
                    if (points > 0) map.put(rs.getString(1), points);
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
            try { conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void deletePointScale(String leagueId, String scaleName) {
        try (PreparedStatement ps = conn.prepareStatement(
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
        try (PreparedStatement ps = conn.prepareStatement(
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

    /* ---------------------------------------------------------- */
    /*  helpers                                                    */
    /* ---------------------------------------------------------- */
    private League buildLeague(ResultSet rs) throws SQLException {
        League l = new League();
        l.setId(rs.getString("id"));
        l.setName(rs.getString("name"));
        LeagueConfig c = new LeagueConfig();
        c.setMaxTeamOwnership(rs.getInt("max_team_own"));
        c.setMaxDriversPerTeam(rs.getInt("max_drivers"));
        c.setMaxReservesPerTeam(rs.getInt("max_reserves"));
        c.setPointSystem(rs.getString("point_system"));
        c.setHologramsEnabled(rs.getInt("holograms") == 1);
        c.setHologramRefreshSeconds(rs.getInt("holo_refresh"));
        c.setStrikeRaces(rs.getInt("strike_races"));
        c.setPrimaryColor(rs.getString("primary_color"));
        c.setSecondaryColor(rs.getString("secondary_color"));
        l.setConfig(c);
        l.setCurrentSeason(rs.getString("current_season"));
        c.getCustomScales().putAll(loadPointScales(l.getId()));
        return l;
    }
}