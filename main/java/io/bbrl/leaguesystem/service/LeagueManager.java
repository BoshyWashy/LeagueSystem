package io.bbrl.leaguesystem.service;

import io.bbrl.leaguesystem.config.LeagueStorage;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Season;
import io.bbrl.leaguesystem.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class LeagueManager {

    private static LeagueManager INSTANCE;
    private final LeagueStorage storage;

    public LeagueManager(LeagueStorage storage) {
        this.storage = storage;
        INSTANCE = this;
    }
    public static LeagueManager getInstance() { return INSTANCE; }

    public LeagueStorage getStorage() { return storage; }

    public Collection<League> allLeagues() { return storage.allLeagues(); }
    public Optional<League> getLeague(String id) { return storage.getLeague(id); }

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

    public void renameLeague(String oldId, String newId, String newName) {
        storage.renameLeague(oldId, newId, newName);
    }

    public Season getSeason(League league, String key) {
        Optional<League> refreshed = getLeague(league.getId());
        if (refreshed.isPresent()) {
            return refreshed.get().getSeason(key);
        }
        return league.getSeason(key);
    }

    public Season getCurrentSeason(League league) {
        String key = league.getCurrentSeason();
        if (key == null) return null;
        return getSeason(league, key);
    }

    public void createSeason(League league, String key, String raceCountDisplay) {
        storage.createSeason(league, key, raceCountDisplay);

        Optional<League> refreshed = getLeague(league.getId());
        if (refreshed.isPresent()) {
            League fresh = refreshed.get();
            league.getSeasons().clear();
            league.getSeasons().putAll(fresh.getSeasons());
        }

        league.setCurrentSeason(key);
        storage.saveLeague(league);
    }

    public void deleteSeason(League league, String key) {
        if ("legacy".equalsIgnoreCase(key)) return;
        storage.deleteSeason(league, key);

        league.getSeasons().remove(key);

        if (Objects.equals(league.getCurrentSeason(), key)) {
            league.setCurrentSeason(null);
        }
        storage.saveLeague(league);
    }

    public void setSeasonRaceCount(League league, String key, String raceCountDisplay) {
        storage.setSeasonRaceCount(league, key, raceCountDisplay);
    }

    public Optional<Team> getTeam(League league, String teamId) {
        if (league == null) return Optional.empty();
        return Optional.ofNullable(storage.loadTeams(league).get(teamId.toLowerCase()));
    }

    public boolean isPlayerInLeagueTeam(League league, String playerUuid) {
        return storage.loadTeams(league).values().stream()
                .anyMatch(t -> t.getMembers().contains(playerUuid) || t.getReserves().contains(playerUuid) || t.isOwner(playerUuid));
    }

    public Optional<Team> getPlayerTeam(League league, String playerUuid) {
        return storage.loadTeams(league).values().stream()
                .filter(t -> t.getMembers().contains(playerUuid) || t.getReserves().contains(playerUuid) || t.isOwner(playerUuid))
                .findFirst();
    }

    public boolean canCreateTeam(League league, String ownerUuid) {
        long alreadyOwns = storage.loadTeams(league).values().stream()
                .filter(t -> t.isOwner(ownerUuid)).count();
        return alreadyOwns < league.getConfig().getMaxTeamOwnership();
    }

    public Team createTeam(League league, String teamId, String name, String hex, String ownerUuid) {
        if (!canCreateTeam(league, ownerUuid)) return null;
        Map<String, Team> teams = storage.loadTeams(league);

        if (teams.containsKey(teamId.toLowerCase())) {
            return null;
        }

        Team t = new Team(teamId.toLowerCase(), name, hex, ownerUuid);
        teams.put(teamId.toLowerCase(), t);
        storage.saveTeams(league, teams);
        return t;
    }

    public void renameTeam(League league, String oldTeamId, String newTeamId, String newName) {
        Map<String, Team> teams = storage.loadTeams(league);
        Team team = teams.get(oldTeamId.toLowerCase());
        if (team == null) return;

        if (teams.containsKey(newTeamId.toLowerCase()) && !oldTeamId.equalsIgnoreCase(newTeamId)) {
            return; // New ID already exists
        }

        storage.renameTeam(league, oldTeamId, newTeamId, newName);
    }

    public boolean hasInvite(League league, String teamId, String playerUuid) {
        return league.getTeamInvites().getOrDefault(teamId.toLowerCase(), Collections.emptySet())
                .stream().anyMatch(entry -> entry.uuid.equals(playerUuid));
    }

    public void invitePlayer(League league, Team team, String targetUuid, String targetName, boolean asReserve) {
        invitePlayer(league, team, targetUuid, targetName, asReserve, false);
    }

    public void invitePlayer(League league, Team team, String targetUuid, String targetName, boolean asReserve, boolean asOwner) {
        league.getTeamInvites().computeIfAbsent(team.getId(), k -> new HashSet<>())
                .add(new InviteEntry(targetUuid, targetName, asReserve, asOwner));
        storage.saveLeague(league);
    }

    public boolean consumeInvite(League league, String teamId, String playerUuid) {
        Set<InviteEntry> set = league.getTeamInvites().get(teamId.toLowerCase());
        if (set == null) return false;
        boolean removed = set.removeIf(e -> e.uuid.equals(playerUuid));
        if (removed) storage.saveLeague(league);
        return removed;
    }

    public Optional<InviteEntry> getInvite(League league, String teamId, String playerUuid) {
        return league.getTeamInvites().getOrDefault(teamId.toLowerCase(), Collections.emptySet())
                .stream().filter(e -> e.uuid.equals(playerUuid)).findFirst();
    }

    public void deliverOfflineInvites(Player player) {
        String uuid = player.getUniqueId().toString();
        for (League league : allLeagues()) {
            for (Map.Entry<String, Set<InviteEntry>> e : league.getTeamInvites().entrySet()) {
                for (InviteEntry ie : e.getValue()) {
                    if (ie.uuid.equals(uuid)) {
                        Optional<Team> ot = getTeam(league, e.getKey());
                        if (ot.isPresent()) {
                            String type;
                            if (ie.asOwner) {
                                type = "as co-owner";
                            } else {
                                type = ie.asReserve ? "as reserve" : "as main driver";
                            }
                            player.sendMessage("§6" + ot.get().getName() +
                                    " has invited you to join their team in " + league.getName() + " " + type +
                                    " §a§l/league " + league.getId() + " team " + e.getKey() + " join");
                        }
                    }
                }
            }
        }
    }

    public void addPlayerToTeam(League league, String teamId, String playerUuid) {
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(teamId.toLowerCase());
        if (live == null) return;

        if (live.getMembers().contains(playerUuid) || live.getReserves().contains(playerUuid) || live.isOwner(playerUuid)) {
            return;
        }

        for (Team t : teams.values()) {
            if (t.getMembers().contains(playerUuid) || t.getReserves().contains(playerUuid) || t.isOwner(playerUuid)) {
                return;
            }
        }

        Optional<InviteEntry> invite = getInvite(league, teamId, playerUuid);
        boolean asReserve = invite.map(i -> i.asReserve).orElse(false);
        boolean asOwner = invite.map(i -> i.asOwner).orElse(false);

        if (asOwner) {
            live.addOwner(playerUuid);
        } else if (!asReserve && live.getMembers().size() < league.getConfig().getMaxDriversPerTeam()) {
            live.getMembers().add(playerUuid);
        } else if (live.getReserves().size() < league.getConfig().getMaxReservesPerTeam()) {
            live.getReserves().add(playerUuid);
        } else {
            return;
        }

        storage.saveTeams(league, teams);
    }

    public boolean leaveTeam(League league, String playerUuid) {
        return leaveTeam(league, playerUuid, false);
    }

    public boolean leaveTeam(League league, String playerUuid, boolean asOwner) {
        Optional<Team> ot = getPlayerTeam(league, playerUuid);
        if (!ot.isPresent()) return false;

        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(ot.get().getId());
        if (live == null) return false;

        if (asOwner) {
            // Can only leave as owner if not the main owner
            if (live.isMainOwner(playerUuid)) {
                return false; // Main owner cannot leave as owner
            }
            live.removeOwner(playerUuid);
        } else {
            live.getMembers().remove(playerUuid);
            live.getReserves().remove(playerUuid);
        }

        storage.saveTeams(league, teams);
        return true;
    }

    public void kickPlayerFromTeam(League league, String teamId, String playerUuid) {
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(teamId.toLowerCase());
        if (live == null) return;

        live.getMembers().remove(playerUuid);
        live.getReserves().remove(playerUuid);
        live.removeOwner(playerUuid);

        storage.saveTeams(league, teams);
    }

    public void transferMainOwnership(League league, String teamId, String newMainOwnerUuid) {
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(teamId.toLowerCase());
        if (live == null) return;

        if (!live.isOwner(newMainOwnerUuid)) {
            return; // New owner must already be an owner
        }

        live.transferMainOwnership(newMainOwnerUuid);
        storage.saveTeams(league, teams);
    }

    public void reorderTeamMembers(League league, String teamId, List<String> newOrder) {
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(teamId.toLowerCase());
        if (live == null) return;

        List<String> reordered = new ArrayList<>();
        for (String uuid : newOrder) {
            if (live.getMembers().contains(uuid)) {
                reordered.add(uuid);
            }
        }
        for (String uuid : live.getMembers()) {
            if (!reordered.contains(uuid)) {
                reordered.add(uuid);
            }
        }

        live.setMembers(reordered);
        storage.saveTeams(league, teams);
    }

    public void reorderTeamMember(League league, String teamId, String playerUuid, boolean asReserve, int slotIndex) {
        Map<String, Team> teams = storage.loadTeams(league);
        Team live = teams.get(teamId.toLowerCase());
        if (live == null) return;

        live.getMembers().remove(playerUuid);
        live.getReserves().remove(playerUuid);

        if (asReserve) {
            if (slotIndex < 0) slotIndex = 0;
            if (slotIndex > live.getReserves().size()) slotIndex = live.getReserves().size();
            live.getReserves().add(slotIndex, playerUuid);
        } else {
            if (slotIndex < 0) slotIndex = 0;
            if (slotIndex > live.getMembers().size()) slotIndex = live.getMembers().size();
            live.getMembers().add(slotIndex, playerUuid);
        }

        storage.saveTeams(league, teams);
    }

    public void saveLeague(League league) { storage.saveLeague(league); }

    public double getPlayerPoints(League league, String playerUuid) {
        Map<String, Object> champ = storage.loadChampionship(league, league.getCurrentSeason());
        Map<String, Double> drivers = (Map<String, Double>) champ.getOrDefault("drivers", Map.of());
        return drivers.getOrDefault(playerUuid, 0.0);
    }

    public double getTeamPoints(League league, String teamId) {
        Map<String, Object> champ = storage.loadChampionship(league, league.getCurrentSeason());
        Map<String, Double> teams = (Map<String, Double>) champ.getOrDefault("teams", Map.of());
        return teams.getOrDefault(teamId.toLowerCase(), 0.0);
    }

    public LinkedHashMap<String, Double> getDriverStandings(League league) {
        Map<String, Object> champ = storage.loadChampionship(league, league.getCurrentSeason());
        Map<String, Double> drivers = (Map<String, Double>) champ.getOrDefault("drivers", Map.of());
        Map<String, Double> tmp = new LinkedHashMap<>(drivers);
        return tmp.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }

    public static class InviteEntry {
        public final String uuid;
        public final String name;
        public final boolean asReserve;
        public final boolean asOwner;

        public InviteEntry(String uuid, String name, boolean asReserve) {
            this(uuid, name, asReserve, false);
        }

        public InviteEntry(String uuid, String name, boolean asReserve, boolean asOwner) {
            this.uuid = uuid;
            this.name = name;
            this.asReserve = asReserve;
            this.asOwner = asOwner;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof InviteEntry e && e.uuid.equals(uuid);
        }

        @Override
        public int hashCode() { return uuid.hashCode(); }
    }
}