package io.bbrl.leaguesystem.service;

import io.bbrl.leaguesystem.config.LeagueStorage;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Season;
import io.bbrl.leaguesystem.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Every write goes straight to disk via LeagueStorage.
 * No in-memory maps are kept here.
 */
public class LeagueManager {

    private final LeagueStorage storage;

    public LeagueManager(LeagueStorage storage) {
        this.storage = storage;
    }

    public LeagueStorage getStorage() {
        return storage;
    }

    public Collection<League> allLeagues() {
        return storage.allLeagues();
    }

    public Optional<League> getLeague(String id) {
        return storage.getLeague(id);
    }

    public boolean createLeague(String id, String name, String ownerUuid) {
        if (storage.getLeague(id).isPresent()) return false;
        League l = new League(id, name, new io.bbrl.leaguesystem.model.LeagueConfig());
        storage.saveLeague(l);
        return true;
    }

    public boolean deleteLeague(String id) {
        if (!storage.getLeague(id).isPresent()) return false;
        storage.deleteLeague(id);
        return true;
    }

    /* -------------------------------------------------- */
    /*  SEASON HELPERS  (String season keys)               */
    /* -------------------------------------------------- */
    public Season getSeason(League league, String key) {
        return league.getSeason(key);
    }

    public Season getCurrentSeason(League league) {
        String key = league.getCurrentSeason();
        return key == null ? null : league.getSeason(key);
    }

    public void createSeason(League league, String key, String raceCountDisplay) {
        // force-create the season folder by writing an empty championship file
        storage.saveSeasonRaceCount(league, key, raceCountDisplay);
        league.setCurrentSeason(key);
        storage.saveLeague(league);
    }

    public void deleteSeason(League league, String key) {
        if ("legacy".equals(key)) return;
        // remove directory
        File seasonDir = new File(storage.leaguesRoot, league.getId() + "/" + key);
        if (seasonDir.exists()) deleteRecursively(seasonDir);
        // update league
        if (Objects.equals(league.getCurrentSeason(), key)) league.setCurrentSeason(null);
        storage.saveLeague(league);
    }

    public void setSeasonRaceCount(League league, String key, String raceCountDisplay) {
        storage.saveSeasonRaceCount(league, key, raceCountDisplay);
    }

    /* -------------------------------------------------- */
    /*  TEAMS  (global roster)                             */
    /* -------------------------------------------------- */
    public Optional<Team> getTeam(League league, String teamId) {
        if (league == null) return Optional.empty();
        return Optional.ofNullable(storage.loadTeams(league).get(teamId.toLowerCase()));
    }

    public boolean isPlayerInLeagueTeam(League league, String playerUuid) {
        return storage.loadTeams(league).values().stream()
                .anyMatch(t -> t.getMembers().contains(playerUuid) || t.getReserves().contains(playerUuid));
    }

    public Optional<Team> getPlayerTeam(League league, String playerUuid) {
        return storage.loadTeams(league).values().stream()
                .filter(t -> t.getMembers().contains(playerUuid) || t.getReserves().contains(playerUuid))
                .findFirst();
    }

    public boolean canCreateTeam(League league, String ownerUuid) {
        long alreadyOwns = storage.loadTeams(league).values().stream()
                .filter(t -> ownerUuid.equals(t.getOwnerUuid())).count();
        return alreadyOwns < league.getConfig().getMaxTeamOwnership();
    }

    public Team createTeam(League league, String teamId, String name, String hex, String ownerUuid) {
        if (!canCreateTeam(league, ownerUuid)) return null;
        Map<String, Team> teams = storage.loadTeams(league);
        Team t = new Team(teamId.toLowerCase(), name, hex, ownerUuid);
        teams.put(teamId.toLowerCase(), t);
        storage.saveTeams(league, teams);          // <-- critical: writes teams.yml
        return t;
    }

    public boolean inviteToTeam(League league, Team team, String playerUuid, boolean asReserve) {
        if (isPlayerInLeagueTeam(league, playerUuid)) return false;
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(team.getId());
        if (live == null) return false;
        if (asReserve) {
            if (live.getReserves().size() >= league.getConfig().getMaxReservesPerTeam()) return false;
            live.getReserves().add(playerUuid);
        } else {
            if (live.getMembers().size() >= league.getConfig().getMaxDriversPerTeam()) return false;
            live.getMembers().add(playerUuid);
        }
        storage.saveTeams(league, teams);
        return true;
    }

    public boolean leaveTeam(League league, String playerUuid) {
        Optional<Team> ot = getPlayerTeam(league, playerUuid);
        if (!ot.isPresent()) return false;
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(ot.get().getId());
        if (live == null) return false;
        live.getMembers().remove(playerUuid);
        live.getReserves().remove(playerUuid);
        if (playerUuid.equals(live.getOwnerUuid())) {
            if (!live.getMembers().isEmpty()) live.setOwnerUuid(live.getMembers().get(0));
            else teams.remove(live.getId());
        }
        storage.saveTeams(league, teams);
        return true;
    }

    public void saveLeague(League league) {
        storage.saveLeague(league);
    }

    /* -------------------------------------------------- */
    /*  POINTS  (from championship file)                  */
    /* -------------------------------------------------- */
    public int getPlayerPoints(League league, String playerUuid) {
        Map<String, Object> champ = storage.loadChampionship(league, league.getCurrentSeason());
        Map<String, Integer> drivers = (Map<String, Integer>) champ.getOrDefault("drivers", Map.of());
        return drivers.getOrDefault(playerUuid, 0);
    }

    public int getTeamPoints(League league, String teamId) {
        Map<String, Object> champ = storage.loadChampionship(league, league.getCurrentSeason());
        Map<String, Integer> teams = (Map<String, Integer>) champ.getOrDefault("teams", Map.of());
        return teams.getOrDefault(teamId.toLowerCase(), 0);
    }

    public void addPlayerPoints(League league, String playerUuid, int points) {
        // championship is updated externally after races
    }

    /* -------------------------------------------------- */
    /*  INVITES  (league-wide)                             */
    /* -------------------------------------------------- */
    public void invitePlayer(League league, Team team, String uuid) {
        league.getTeamInvites().computeIfAbsent(team.getId(), k -> new HashSet<>()).add(uuid);
        storage.saveLeague(league);
    }

    public boolean consumeInvite(League league, String teamId, String uuid) {
        Set<String> set = league.getTeamInvites().get(teamId);
        if (set == null || !set.remove(uuid)) return false;
        storage.saveLeague(league);
        return true;
    }

    /* -------------------------------------------------- */
    /*  STRIKE-ADJUSTED TOTALS  (from championship)        */
    /* -------------------------------------------------- */
    public int getPlayerPointsPostStrikes(League league, String uuid) {
        int strikes = league.getConfig().getStrikeRaces();
        if (strikes <= 0) return getPlayerPoints(league, uuid);
        // collect all race points for this driver
        List<Integer> races = new ArrayList<>();
        File seasonDir = new File(storage.leaguesRoot, league.getId() + "/" + league.getCurrentSeason());
        if (!seasonDir.exists()) return 0;
        String[] raceFiles = seasonDir.list((d, name) -> name.endsWith(".yml") && !name.equals("championship.yml"));
        if (raceFiles == null) return 0;
        for (String rf : raceFiles) {
            String race = rf.replace(".yml", "");
            Map<String, Object> raceData = storage.loadRace(league, league.getCurrentSeason(), race);
            Map<String, Integer> pts = (Map<String, Integer>) raceData.getOrDefault("pointsAwarded", Map.of());
            Integer p = pts.get(uuid);
            if (p != null) races.add(p);
        }
        if (races.isEmpty()) return 0;
        races.sort(Integer::compareTo);
        int drop = Math.min(strikes, races.size());
        return races.subList(drop, races.size()).stream().mapToInt(i -> i).sum();
    }

    public LinkedHashMap<String, Integer> getDriverStandings(League league) {
        Map<String, Object> champ = storage.loadChampionship(league, league.getCurrentSeason());
        Map<String, Integer> drivers = (Map<String, Integer>) champ.getOrDefault("drivers", Map.of());
        Map<String, Integer> tmp = new LinkedHashMap<>(drivers);
        return tmp.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }

    /* helper */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : Objects.requireNonNull(file.listFiles())) deleteRecursively(child);
        }
        file.delete();
    }
}