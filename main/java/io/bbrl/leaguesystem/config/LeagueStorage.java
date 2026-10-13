package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.*;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class LeagueStorage {

    private final DatabaseCore core;
    private final DatabaseLeague leagueDb;
    private final DatabaseSeason seasonDb;
    private final DatabaseTeam teamDb;
    private final DatabaseRace raceDb;
    private final DatabaseChampionship championshipDb;
    private final DatabaseLeaderboard leaderboardDb;

    public LeagueStorage(JavaPlugin plugin) {
        this.core = new DatabaseCore(plugin.getDataFolder());
        this.leagueDb = new DatabaseLeague(core);
        this.seasonDb = new DatabaseSeason(core);
        this.teamDb = new DatabaseTeam(core);
        this.raceDb = new DatabaseRace(core);
        this.championshipDb = new DatabaseChampionship(core, teamDb, raceDb);
        this.leaderboardDb = new DatabaseLeaderboard(core);
    }

    public Collection<League> allLeagues() {
        List<League> leagues = leagueDb.allLeagues();
        for (League l : leagues) {
            if (l.getSeasons().isEmpty()) {
                Optional<League> reloaded = leagueDb.getLeague(l.getId());
                reloaded.ifPresent(reloadedLeague -> {
                    l.getSeasons().clear();
                    l.getSeasons().putAll(reloadedLeague.getSeasons());
                    l.setFinishedSeasons(reloadedLeague.getFinishedSeasons());
                });
            }
            // Load leaderboards
            l.setLeaderboards(loadLeaderboards(l));
            // Load point scales
            l.getConfig().getCustomScales().putAll(championshipDb.loadPointScales(l.getId()));
        }
        return leagues;
    }

    public Optional<League> getLeague(String id) {
        Optional<League> opt = leagueDb.getLeague(id);
        opt.ifPresent(l -> {
            l.setLeaderboards(loadLeaderboards(l));
            l.getConfig().getCustomScales().putAll(championshipDb.loadPointScales(l.getId()));
        });
        return opt;
    }

    public void saveLeague(League l) { leagueDb.saveLeague(l); }
    public void deleteLeague(String id) { leagueDb.deleteLeague(id); }
    public void renameLeague(String oldId, String newId, String newName) {
        leagueDb.renameLeague(oldId, newId, newName);
    }

    public void createSeason(League l, String key, String raceCount) {
        seasonDb.createSeason(l.getId(), key, raceCount);
        l.getSeasons().put(key, new Season(key));
    }

    public void deleteSeason(League l, String key) {
        seasonDb.deleteSeason(l.getId(), key);
        l.getSeasons().remove(key);
    }

    public void renameSeason(String leagueId, String oldKey, String newKey) {
        seasonDb.renameSeason(leagueId, oldKey, newKey);
    }

    public void setSeasonFinished(League l, String key, boolean finished) {
        seasonDb.setSeasonFinished(l.getId(), key, finished);
        if (finished) {
            l.finishSeason(key);
        } else {
            l.unfinishSeason(key);
        }
    }

    public boolean isSeasonFinished(League l, String key) {
        return seasonDb.isSeasonFinished(l.getId(), key);
    }

    public void setSeasonRaceCount(League l, String key, String raceCountDisplay) {
        seasonDb.setSeasonRaceCount(l.getId(), key, raceCountDisplay);
    }

    public Map<String, Team> loadTeams(League l) { return teamDb.loadTeams(l.getId()); }
    public void saveTeams(League l, Map<String, Team> teams) { teamDb.saveTeams(l.getId(), teams); }

    public void renameTeam(League l, String oldTeamId, String newTeamId, String newName) {
        teamDb.renameTeam(l.getId(), oldTeamId, newTeamId, newName);
    }

    public void saveRace(League l, String season, String race, Map<String, Object> ignored) {
        raceDb.saveRace(l.getId(), season, race);
    }

    public void deleteRace(League l, String season, String race) {
        raceDb.deleteRace(l.getId(), season, race);
    }

    public List<String> listRaces(League l, String season) {
        if (l == null || season == null) return List.of();
        return raceDb.listRaces(l.getId(), season);
    }

    /* Race number methods */
    public void setRaceNumber(League l, String season, String race, int raceNumber) {
        raceDb.setRaceNumber(l.getId(), season, race, raceNumber);
    }

    public int getRaceNumber(League l, String season, String race) {
        return raceDb.getRaceNumber(l.getId(), season, race);
    }

    /* Calendar methods */
    public void saveCalendarEntry(League l, String season, int raceNumber, String trackName, String raceName) {
        raceDb.saveCalendarEntry(l.getId(), season, raceNumber, trackName, raceName);
    }

    public void deleteCalendarEntry(League l, String season, int raceNumber) {
        raceDb.deleteCalendarEntry(l.getId(), season, raceNumber);
    }

    public Map<Integer, String> getCalendar(League l, String season) {
        return raceDb.getCalendar(l.getId(), season);
    }

    public String getCalendarRaceName(League l, String season, int raceNumber) {
        return raceDb.getCalendarRaceName(l.getId(), season, raceNumber);
    }

    /* Race finalization methods */
    public boolean isRaceFinished(League l, String season, String race) {
        return raceDb.isRaceFinished(l.getId(), season, race);
    }

    public void setRaceFinished(League l, String season, String race, boolean finished) {
        raceDb.setRaceFinished(l.getId(), season, race, finished);
    }

    public void saveRaceResult(League l, String season, String race, String uuid, int position, int points) {
        raceDb.saveRaceResult(l.getId(), season, race, uuid, position, points);
    }

    public void deleteRaceResult(League l, String season, String race, String uuid) {
        raceDb.deleteRaceResult(l.getId(), season, race, uuid);
    }

    public Map<String, Integer> getRaceResults(League l, String season, String race) {
        return raceDb.getRaceResults(l.getId(), season, race);
    }

    public Map<String, Integer> getRaceResultPositions(League l, String season, String race) {
        return raceDb.getRaceResultPositions(l.getId(), season, race);
    }

    /* NEW: Race result team tracking */
    public void saveRaceResultTeam(League l, String season, String race, String uuid, String teamId) {
        raceDb.saveRaceResultTeam(l.getId(), season, race, uuid, teamId);
    }

    public void deleteRaceResultTeam(League l, String season, String race, String uuid) {
        raceDb.deleteRaceResultTeam(l.getId(), season, race, uuid);
    }

    public Map<String, String> getRaceResultTeams(League l, String season, String race) {
        return raceDb.getRaceResultTeams(l.getId(), season, race);
    }

    public String getDriverTeamForRace(League l, String season, String race, String uuid) {
        return raceDb.getDriverTeamForRace(l.getId(), season, race, uuid);
    }

    public void saveRaceTeamResult(League l, String season, String race, String teamId, double points) {
        raceDb.saveRaceTeamResult(l.getId(), season, race, teamId, points);
    }

    public void deleteRaceTeamResult(League l, String season, String race, String teamId) {
        raceDb.deleteRaceTeamResult(l.getId(), season, race, teamId);
    }

    public Map<String, Double> getRaceTeamResults(League l, String season, String race) {
        return raceDb.getRaceTeamResults(l.getId(), season, race);
    }

    public void saveFastestLap(League l, String season, String race, String uuid, int points) {
        raceDb.saveFastestLap(l.getId(), season, race, uuid, points);
    }

    public void deleteFastestLap(League l, String season, String race) {
        raceDb.deleteFastestLap(l.getId(), season, race);
    }

    public Map<String, Object> getFastestLap(League l, String season, String race) {
        return raceDb.getFastestLap(l.getId(), season, race);
    }

    public void savePointSystem(League l, Map<String, Map<Integer, Integer>> scales) {
        championshipDb.savePointScales(l.getId(), scales);
    }

    public void deletePointSystem(League l, String scaleName) {
        championshipDb.deletePointScale(l.getId(), scaleName);
        l.getConfig().getCustomScales().remove(scaleName);
    }

    public Map<String, Map<Integer, Integer>> loadPointSystemData(League l) {
        return championshipDb.loadPointScales(l.getId());
    }

    public Map<String, Object> loadChampionship(League l, String season) {
        if (l == null || season == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("drivers", new HashMap<>());
            empty.put("teams", new HashMap<>());
            return empty;
        }

        championshipDb.rebuildChampionship(l.getId(), season);

        Map<String, Double> d = championshipDb.getDriverStandings(l.getId(), season);
        Map<String, Double> t = championshipDb.getTeamStandings(l.getId(), season);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("drivers", d);
        out.put("teams", t);
        return out;
    }

    public void saveChampionship(League l, String season, Map<String, Object> ignored) {
        if (l == null || season == null) return;
        championshipDb.rebuildChampionship(l.getId(), season);
    }

    public void addManualAdjustment(League l, String season, String uuid, double delta) {
        championshipDb.addManualAdjustment(l.getId(), season, uuid, delta);
    }

    public double getManualAdjustment(League l, String season, String uuid) {
        return championshipDb.getManualAdjustment(l.getId(), season, uuid);
    }

    /* Leaderboard methods */
    public Map<String, Leaderboard> loadLeaderboards(League l) {
        return leaderboardDb.loadLeaderboards(l.getId());
    }

    public void saveLeaderboards(League l, Map<String, Leaderboard> leaderboards) {
        leaderboardDb.saveLeaderboards(l.getId(), leaderboards);
    }

    public final File leaguesRoot = new File("");
}