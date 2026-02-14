package io.bbrl.leaguesystem.config;

import io.bbrl.leaguesystem.model.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class LeagueStorage {

    private final Database db;

    public LeagueStorage(JavaPlugin plugin) {
        this.db = new Database(plugin.getDataFolder());
    }

    public Collection<League> allLeagues() {
        List<League> leagues = db.allLeagues();
        for (League l : leagues) {
            if (l.getSeasons().isEmpty()) {
                Optional<League> reloaded = db.getLeague(l.getId());
                reloaded.ifPresent(reloadedLeague -> {
                    l.getSeasons().clear();
                    l.getSeasons().putAll(reloadedLeague.getSeasons());
                });
            }
        }
        return leagues;
    }

    public Optional<League> getLeague(String id) {
        return db.getLeague(id);
    }

    public void saveLeague(League l) { db.saveLeague(l); }
    public void deleteLeague(String id) { db.deleteLeague(id); }
    public void renameLeague(String oldId, String newId, String newName) {
        db.renameLeague(oldId, newId, newName);
    }

    public void createSeason(League l, String key, String raceCount) {
        db.createSeason(l.getId(), key, raceCount);
        l.getSeasons().put(key, new Season(key));
    }

    public void deleteSeason(League l, String key) {
        db.deleteSeason(l.getId(), key);
        l.getSeasons().remove(key);
    }

    public void setSeasonRaceCount(League l, String key, String display) {
        db.setSeasonRaceCount(l.getId(), key, display);
    }

    public Map<String, Team> loadTeams(League l) { return db.loadTeams(l.getId()); }
    public void saveTeams(League l, Map<String, Team> teams) { db.saveTeams(l.getId(), teams); }

    public void renameTeam(League l, String oldTeamId, String newTeamId, String newName) {
        db.renameTeam(l.getId(), oldTeamId, newTeamId, newName);
    }

    public void saveRace(League l, String season, String race, Map<String, Object> ignored) {
        db.saveRace(l.getId(), season, race);
    }

    public void deleteRace(League l, String season, String race) {
        db.deleteRace(l.getId(), season, race);
    }

    public List<String> listRaces(League l, String season) {
        if (l == null || season == null) return List.of();
        return db.listRaces(l.getId(), season);
    }

    public void saveRaceResult(League l, String season, String race, String uuid, int position, int points) {
        db.saveRaceResult(l.getId(), season, race, uuid, position, points);
    }

    public void deleteRaceResult(League l, String season, String race, String uuid) {
        db.deleteRaceResult(l.getId(), season, race, uuid);
    }

    public void saveRaceTeamResult(League l, String season, String race, String teamId, double points) {
        db.saveRaceTeamResult(l.getId(), season, race, teamId, points);
    }

    public Map<String, Integer> getRaceResults(League l, String season, String race) {
        return db.getRaceResults(l.getId(), season, race);
    }

    public void saveFastestLap(League l, String season, String race, String uuid, int points) {
        db.saveFastestLap(l.getId(), season, race, uuid, points);
    }

    public void deleteFastestLap(League l, String season, String race) {
        db.deleteFastestLap(l.getId(), season, race);
    }

    public Map<String, Object> getFastestLap(League l, String season, String race) {
        return db.getFastestLap(l.getId(), season, race);
    }

    public void savePointSystem(League l, Map<String, Map<Integer, Integer>> scales) {
        db.savePointScales(l.getId(), scales);
    }

    public void deletePointSystem(League l, String scaleName) {
        db.deletePointScale(l.getId(), scaleName);
        l.getConfig().getCustomScales().remove(scaleName);
    }

    public Map<String, Map<Integer, Integer>> loadPointSystemData(League l) {
        return db.loadPointScales(l.getId());
    }

    public Map<String, Object> loadChampionship(League l, String season) {
        if (l == null || season == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("drivers", new HashMap<>());
            empty.put("teams", new HashMap<>());
            return empty;
        }

        db.rebuildChampionship(l.getId(), season);

        Map<String, Double> d = db.getDriverStandings(l.getId(), season);
        Map<String, Double> t = db.getTeamStandings(l.getId(), season);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("drivers", d);
        out.put("teams", t);
        return out;
    }

    public void saveChampionship(League l, String season, Map<String, Object> ignored) {
        if (l == null || season == null) return;
        db.rebuildChampionship(l.getId(), season);
    }

    public void addManualAdjustment(League l, String season, String uuid, double delta) {
        db.addManualAdjustment(l.getId(), season, uuid, delta);
    }

    public double getManualAdjustment(League l, String season, String uuid) {
        return db.getManualAdjustment(l.getId(), season, uuid);
    }

    public final File leaguesRoot = new File("");
}