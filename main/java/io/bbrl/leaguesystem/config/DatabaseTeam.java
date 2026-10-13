package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.Team;

import java.sql.*;
import java.util.*;

/**
 * Team CRUD operations.
 */
public final class DatabaseTeam {

    private final DatabaseCore core;

    public DatabaseTeam(DatabaseCore core) {
        this.core = core;
    }

    /* ---------------------------------------------------------- */
    /*  teams                                                      */
    /* ---------------------------------------------------------- */
    public Map<String, Team> loadTeams(String leagueId) {
        Map<String, Team> map = new LinkedHashMap<>();
        try (PreparedStatement ps = core.getConnection().prepareStatement("SELECT * FROM teams WHERE league_id=?")) {
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
        // Load owners from team_owners table - prevent duplicates
        try (PreparedStatement ps = core.getConnection().prepareStatement(
                "SELECT * FROM team_owners WHERE league_id=? ORDER BY is_main_owner DESC")) {
            ps.setString(1, leagueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Team t = map.get(rs.getString("team_id"));
                    if (t == null) continue;
                    String uuid = rs.getString("uuid");
                    boolean isMain = rs.getInt("is_main_owner") == 1;

                    // Only add if not already in list (prevent duplicates)
                    if (!t.getOwners().contains(uuid)) {
                        if (isMain) {
                            // Main owner goes first
                            t.getOwners().add(0, uuid);
                            t.setOwnerUuid(uuid);
                        } else {
                            t.getOwners().add(uuid);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // members with sort order
        try (PreparedStatement ps = core.getConnection().prepareStatement(
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
        Connection conn = core.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement delM = conn.prepareStatement("DELETE FROM team_members WHERE league_id=?")) {
                delM.setString(1, leagueId);
                delM.executeUpdate();
            }
            try (PreparedStatement delO = conn.prepareStatement("DELETE FROM team_owners WHERE league_id=?")) {
                delO.setString(1, leagueId);
                delO.executeUpdate();
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
            // Save owners - use unique list to prevent duplicates
            try (PreparedStatement insO = conn.prepareStatement(
                    "INSERT INTO team_owners(league_id,team_id,uuid,is_main_owner) VALUES (?,?,?,?)")) {
                for (Team t : teams.values()) {
                    Set<String> seen = new HashSet<>();
                    for (String uuid : t.getOwners()) {
                        if (seen.add(uuid)) { // Only process if not seen before
                            boolean isMain = uuid.equals(t.getOwnerUuid());
                            insO.setString(1, leagueId);
                            insO.setString(2, t.getId());
                            insO.setString(3, uuid);
                            insO.setInt(4, isMain ? 1 : 0);
                            insO.addBatch();
                        }
                    }
                }
                insO.executeBatch();
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

    public void renameTeam(String leagueId, String oldTeamId, String newTeamId, String newName) {
        Connection conn = core.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE teams SET team_id=?, name=? WHERE league_id=? AND team_id=?")) {
                ps.setString(1, newTeamId.toLowerCase());
                ps.setString(2, newName);
                ps.setString(3, leagueId);
                ps.setString(4, oldTeamId.toLowerCase());
                ps.executeUpdate();
            }
            String[] tables = {"team_owners", "team_members", "race_team_results", "team_championship"};
            for (String table : tables) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + table + " SET team_id=? WHERE league_id=? AND team_id=?")) {
                    ps.setString(1, newTeamId.toLowerCase());
                    ps.setString(2, leagueId);
                    ps.setString(3, oldTeamId.toLowerCase());
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
