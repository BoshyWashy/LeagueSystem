package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Core database connection and schema management.
 * Provides the shared Connection and schema/migration logic.
 */
public final class DatabaseCore {

    private final Connection conn;

    public DatabaseCore(File dataFolder) {
        File db = new File(dataFolder, "leagues.db");
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            createSchema();
            runMigrations();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open SQLite database", e);
        }
    }

    public Connection getConnection() {
        return conn;
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
                  description   TEXT DEFAULT '',
                  max_team_own  INTEGER DEFAULT 1,
                  max_drivers   INTEGER DEFAULT 3,
                  max_reserves  INTEGER DEFAULT 1,
                  point_system  TEXT    DEFAULT 'f1',
                  holograms     INTEGER DEFAULT 1,
                  holo_refresh  INTEGER DEFAULT 30,
                  strike_races  INTEGER DEFAULT 0,
                  current_season TEXT,
                  primary_color TEXT DEFAULT '§6',
                  secondary_color TEXT DEFAULT '§f',
                  allow_anyone_create_team INTEGER DEFAULT 1,
                  fastest_lap_points INTEGER DEFAULT 1,
                  spawn_world   TEXT,
                  spawn_x       REAL DEFAULT 0.0,
                  spawn_y       REAL DEFAULT 0.0,
                  spawn_z       REAL DEFAULT 0.0,
                  spawn_yaw     REAL DEFAULT 0.0,
                  spawn_pitch   REAL DEFAULT 0.0,
                  spawn_set     INTEGER DEFAULT 0,
                  holo_leaderboard_length INTEGER DEFAULT 10,
                  holo_y_offset REAL DEFAULT 0.0
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS seasons(
                  league_id  TEXT,
                  season_key TEXT,
                  race_count TEXT,
                  is_finished INTEGER DEFAULT 0,
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
                CREATE TABLE IF NOT EXISTS team_owners(
                  league_id  TEXT,
                  team_id    TEXT,
                  uuid       TEXT,
                  is_main_owner INTEGER DEFAULT 0,
                  PRIMARY KEY (league_id, team_id, uuid),
                  FOREIGN KEY (league_id, team_id) REFERENCES teams(league_id, team_id) ON DELETE CASCADE
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
                  is_finished INTEGER DEFAULT 0,
                  race_number INTEGER DEFAULT 0,
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

            // NEW: Track which team each driver was driving for in each race
            st.execute("""
                CREATE TABLE IF NOT EXISTS race_results_team(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  uuid       TEXT,
                  team_id    TEXT,
                  PRIMARY KEY (league_id, season_key, race_name, uuid),
                  FOREIGN KEY (league_id, season_key, race_name) REFERENCES races(league_id, season_key, race_name) ON DELETE CASCADE,
                  FOREIGN KEY (league_id, team_id) REFERENCES teams(league_id, team_id) ON DELETE CASCADE
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
                CREATE TABLE IF NOT EXISTS fastest_laps(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  uuid       TEXT,
                  points     INTEGER,
                  PRIMARY KEY (league_id, season_key, race_name),
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

            st.execute("""
                CREATE TABLE IF NOT EXISTS manual_adjustments(
                  league_id  TEXT,
                  season_key TEXT,
                  uuid       TEXT,
                  adjustment REAL,
                  PRIMARY KEY (league_id, season_key, uuid),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS leaderboards(
                  id          TEXT,
                  league_id   TEXT,
                  name        TEXT,
                  type        TEXT,
                  season_key  TEXT,
                  race_name   TEXT,
                  world       TEXT,
                  x           REAL,
                  y           REAL,
                  z           REAL,
                  yaw         REAL,
                  pitch       REAL,
                  creator_uuid TEXT,
                  PRIMARY KEY (id, league_id),
                  FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE
                );
                """);

            // NEW: Calendar table for season race calendars
            st.execute("""
                CREATE TABLE IF NOT EXISTS calendar(
                  league_id  TEXT,
                  season_key TEXT,
                  race_number INTEGER,
                  track_name TEXT,
                  race_name  TEXT,
                  PRIMARY KEY (league_id, season_key, race_number),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);
        }
    }

    /* ---------------------------------------------------------- */
    /*  migrations – add new columns to existing tables             */
    /* ---------------------------------------------------------- */
    private void runMigrations() throws SQLException {
        // Migration 1: Add league color columns
        try {
            conn.createStatement().execute("SELECT primary_color FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN primary_color TEXT DEFAULT '§6'");
                System.out.println("[LeagueSystem] Migration: Added primary_color column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        try {
            conn.createStatement().execute("SELECT secondary_color FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN secondary_color TEXT DEFAULT '§f'");
                System.out.println("[LeagueSystem] Migration: Added secondary_color column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 2: Add allow_anyone_create_team column
        try {
            conn.createStatement().execute("SELECT allow_anyone_create_team FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN allow_anyone_create_team INTEGER DEFAULT 1");
                System.out.println("[LeagueSystem] Migration: Added allow_anyone_create_team column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 3: Add sort_order to team_members if not exists
        try {
            conn.createStatement().execute("SELECT sort_order FROM team_members LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE team_members ADD COLUMN sort_order INTEGER DEFAULT 0");
                System.out.println("[LeagueSystem] Migration: Added sort_order column to team_members table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 4: Create race_team_results table if not exists
        try {
            conn.createStatement().execute("""
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
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration 5: Add spawn location columns
        String[] spawnColumns = {"spawn_world", "spawn_x", "spawn_y", "spawn_z", "spawn_yaw", "spawn_pitch", "spawn_set"};
        for (String col : spawnColumns) {
            try {
                conn.createStatement().execute("SELECT " + col + " FROM leagues LIMIT 1");
            } catch (SQLException e) {
                try {
                    String dataType = col.equals("spawn_world") ? "TEXT" : (col.equals("spawn_set") ? "INTEGER DEFAULT 0" : "REAL DEFAULT 0.0");
                    conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN " + col + " " + dataType);
                    System.out.println("[LeagueSystem] Migration: Added " + col + " column to leagues table");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }

        // Migration 6: Create manual_adjustments table if not exists
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS manual_adjustments(
                  league_id  TEXT,
                  season_key TEXT,
                  uuid       TEXT,
                  adjustment REAL,
                  PRIMARY KEY (league_id, season_key, uuid),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);
            System.out.println("[LeagueSystem] Migration: Created manual_adjustments table");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration 7: Add fastest_lap_points column
        try {
            conn.createStatement().execute("SELECT fastest_lap_points FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN fastest_lap_points INTEGER DEFAULT 1");
                System.out.println("[LeagueSystem] Migration: Added fastest_lap_points column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 8: Create fastest_laps table
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS fastest_laps(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  uuid       TEXT,
                  points     INTEGER,
                  PRIMARY KEY (league_id, season_key, race_name),
                  FOREIGN KEY (league_id, season_key, race_name) REFERENCES races(league_id, season_key, race_name) ON DELETE CASCADE
                );
                """);
            System.out.println("[LeagueSystem] Migration: Created fastest_laps table");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration 9: Create team_owners table
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS team_owners(
                  league_id  TEXT,
                  team_id    TEXT,
                  uuid       TEXT,
                  is_main_owner INTEGER DEFAULT 0,
                  PRIMARY KEY (league_id, team_id, uuid),
                  FOREIGN KEY (league_id, team_id) REFERENCES teams(league_id, team_id) ON DELETE CASCADE
                );
                """);
            System.out.println("[LeagueSystem] Migration: Created team_owners table");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration 10: Add hologram leaderboard config columns
        try {
            conn.createStatement().execute("SELECT holo_leaderboard_length FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN holo_leaderboard_length INTEGER DEFAULT 10");
                System.out.println("[LeagueSystem] Migration: Added holo_leaderboard_length column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        try {
            conn.createStatement().execute("SELECT holo_y_offset FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN holo_y_offset REAL DEFAULT 0.0");
                System.out.println("[LeagueSystem] Migration: Added holo_y_offset column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 11: Create leaderboards table
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS leaderboards(
                  id          TEXT,
                  league_id   TEXT,
                  name        TEXT,
                  type        TEXT,
                  season_key  TEXT,
                  race_name   TEXT,
                  world       TEXT,
                  x           REAL,
                  y           REAL,
                  z           REAL,
                  yaw         REAL,
                  pitch       REAL,
                  creator_uuid TEXT,
                  PRIMARY KEY (id, league_id),
                  FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE
                );
                """);
            System.out.println("[LeagueSystem] Migration: Created leaderboards table");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration 12: Add description column to leagues
        try {
            conn.createStatement().execute("SELECT description FROM leagues LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE leagues ADD COLUMN description TEXT DEFAULT ''");
                System.out.println("[LeagueSystem] Migration: Added description column to leagues table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 13: Add is_finished column to seasons
        try {
            conn.createStatement().execute("SELECT is_finished FROM seasons LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE seasons ADD COLUMN is_finished INTEGER DEFAULT 0");
                System.out.println("[LeagueSystem] Migration: Added is_finished column to seasons table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 14: Add is_finished column to races (NEW for race finalization)
        try {
            conn.createStatement().execute("SELECT is_finished FROM races LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE races ADD COLUMN is_finished INTEGER DEFAULT 0");
                System.out.println("[LeagueSystem] Migration: Added is_finished column to races table");

                // Finalize all existing races since they were already "saved" in the old system
                conn.createStatement().execute("UPDATE races SET is_finished = 1");
                System.out.println("[LeagueSystem] Migration: All existing races marked as finished");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 15: Create race_results_team table (NEW for tracking driver teams per race)
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS race_results_team(
                  league_id  TEXT,
                  season_key TEXT,
                  race_name  TEXT,
                  uuid       TEXT,
                  team_id    TEXT,
                  PRIMARY KEY (league_id, season_key, race_name, uuid),
                  FOREIGN KEY (league_id, season_key, race_name) REFERENCES races(league_id, season_key, race_name) ON DELETE CASCADE,
                  FOREIGN KEY (league_id, team_id) REFERENCES teams(league_id, team_id) ON DELETE CASCADE
                );
                """);
            System.out.println("[LeagueSystem] Migration: Created race_results_team table");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration 16: Recreate race_results_team without CASCADE on team_id
        try {
            conn.createStatement().execute("PRAGMA foreign_keys = OFF");

            boolean tableExists = false;
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "race_results_team", null)) {
                tableExists = rs.next();
            }

            if (tableExists) {
                List<Map<String, Object>> backup = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM race_results_team");
                     ResultSet data = ps.executeQuery()) {
                    ResultSetMetaData meta = data.getMetaData();
                    int cols = meta.getColumnCount();
                    while (data.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(meta.getColumnName(i), data.getObject(i));
                        }
                        backup.add(row);
                    }
                }

                conn.createStatement().execute("DROP TABLE race_results_team");
                conn.createStatement().execute("""
                    CREATE TABLE race_results_team(
                      league_id  TEXT,
                      season_key TEXT,
                      race_name  TEXT,
                      uuid       TEXT,
                      team_id    TEXT,
                      PRIMARY KEY (league_id, season_key, race_name, uuid),
                      FOREIGN KEY (league_id, season_key, race_name) REFERENCES races(league_id, season_key, race_name) ON DELETE CASCADE
                    );
                    """);

                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT OR IGNORE INTO race_results_team(league_id, season_key, race_name, uuid, team_id) VALUES (?,?,?,?,?)")) {
                    for (Map<String, Object> row : backup) {
                        ins.setString(1, (String) row.get("league_id"));
                        ins.setString(2, (String) row.get("season_key"));
                        ins.setString(3, (String) row.get("race_name"));
                        ins.setString(4, (String) row.get("uuid"));
                        ins.setString(5, (String) row.get("team_id"));
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                System.out.println("[LeagueSystem] Migration 16: Recreated race_results_team without team_id CASCADE to preserve historical results");
            }

            conn.createStatement().execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.createStatement().execute("PRAGMA foreign_keys = ON"); } catch (SQLException ignored) {}
        }

        // Migration 17: Add race_number column to races table
        try {
            conn.createStatement().execute("SELECT race_number FROM races LIMIT 1");
        } catch (SQLException e) {
            try {
                conn.createStatement().execute("ALTER TABLE races ADD COLUMN race_number INTEGER DEFAULT 0");
                System.out.println("[LeagueSystem] Migration: Added race_number column to races table");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        // Migration 18: Create calendar table
        try {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS calendar(
                  league_id  TEXT,
                  season_key TEXT,
                  race_number INTEGER,
                  track_name TEXT,
                  race_name  TEXT,
                  PRIMARY KEY (league_id, season_key, race_number),
                  FOREIGN KEY (league_id, season_key) REFERENCES seasons(league_id, season_key) ON DELETE CASCADE
                );
                """);
            System.out.println("[LeagueSystem] Migration: Created calendar table");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* ---------------------------------------------------------- */
    /*  helpers                                                    */
    /* ---------------------------------------------------------- */
    League buildLeague(ResultSet rs) throws SQLException {
        League l = new League();
        l.setId(rs.getString("id"));
        l.setName(rs.getString("name"));

        try {
            l.setDescription(rs.getString("description"));
        } catch (SQLException e) {
            l.setDescription("");
        }

        LeagueConfig c = new LeagueConfig();
        c.setMaxTeamOwnership(rs.getInt("max_team_own"));
        c.setMaxDriversPerTeam(rs.getInt("max_drivers"));
        c.setMaxReservesPerTeam(rs.getInt("max_reserves"));
        c.setPointSystem(rs.getString("point_system"));
        c.setHologramsEnabled(rs.getInt("holograms") == 1);
        c.setHologramRefreshSeconds(rs.getInt("holo_refresh"));
        c.setStrikeRaces(rs.getInt("strike_races"));

        try {
            c.setPrimaryColor(rs.getString("primary_color"));
        } catch (SQLException e) {
            c.setPrimaryColor("§6");
        }
        try {
            c.setSecondaryColor(rs.getString("secondary_color"));
        } catch (SQLException e) {
            c.setSecondaryColor("§f");
        }
        try {
            c.setAllowAnyoneCreateTeam(rs.getInt("allow_anyone_create_team") == 1);
        } catch (SQLException e) {
            c.setAllowAnyoneCreateTeam(true);
        }
        try {
            c.setFastestLapPoints(rs.getInt("fastest_lap_points"));
        } catch (SQLException e) {
            c.setFastestLapPoints(1);
        }

        try {
            c.setSpawnWorld(rs.getString("spawn_world"));
            c.setSpawnX(rs.getDouble("spawn_x"));
            c.setSpawnY(rs.getDouble("spawn_y"));
            c.setSpawnZ(rs.getDouble("spawn_z"));
            c.setSpawnYaw(rs.getFloat("spawn_yaw"));
            c.setSpawnPitch(rs.getFloat("spawn_pitch"));
            c.setSpawnSet(rs.getInt("spawn_set") == 1);
        } catch (SQLException e) {
            c.setSpawnSet(false);
        }

        try {
            c.setHologramLeaderboardLength(rs.getInt("holo_leaderboard_length"));
        } catch (SQLException e) {
            c.setHologramLeaderboardLength(10);
        }

        try {
            c.setHologramYOffset(rs.getDouble("holo_y_offset"));
        } catch (SQLException e) {
            c.setHologramYOffset(0.0);
        }

        l.setConfig(c);
        l.setCurrentSeason(rs.getString("current_season"));
        return l;
    }
}