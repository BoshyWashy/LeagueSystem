package io.bbrl.leaguesystem.service;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import io.bbrl.leaguesystem.config.LeagueStorage;
import io.bbrl.leaguesystem.model.Leaderboard;
import io.bbrl.leaguesystem.model.LeaderboardType;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardManager {

    private final LeagueStorage storage;
    private final Map<String, Map<String, Hologram>> activeHolograms = new HashMap<>(); // leagueId -> (leaderboardId -> hologram)

    public LeaderboardManager(LeagueStorage storage) {
        this.storage = storage;
    }

    public void createLeaderboard(League league, String id, String name, LeaderboardType type, String seasonKey, String raceName, Location location, UUID creatorUuid) {
        Leaderboard leaderboard = new Leaderboard(id, name, type, seasonKey, raceName, location, creatorUuid);

        Map<String, Leaderboard> leaderboards = storage.loadLeaderboards(league);
        leaderboards.put(id, leaderboard);
        storage.saveLeaderboards(league, leaderboards);

        spawnHologram(league, leaderboard);
    }

    public void removeLeaderboard(League league, String id) {
        // Remove hologram from world
        despawnHologram(league.getId(), id);

        // Remove from storage
        Map<String, Leaderboard> leaderboards = storage.loadLeaderboards(league);
        leaderboards.remove(id);
        storage.saveLeaderboards(league, leaderboards);
    }

    public void editLeaderboard(League league, String id, LeaderboardType type, String seasonKey, String raceName) {
        Map<String, Leaderboard> leaderboards = storage.loadLeaderboards(league);
        Leaderboard leaderboard = leaderboards.get(id);
        if (leaderboard == null) return;

        leaderboard.setType(type);
        leaderboard.setSeasonKey(seasonKey);
        leaderboard.setRaceName(raceName);

        storage.saveLeaderboards(league, leaderboards);

        // Update the hologram
        despawnHologram(league.getId(), id);
        spawnHologram(league, leaderboard);
    }

    public void moveLeaderboard(Player player, League league, String id) {
        Map<String, Leaderboard> leaderboards = storage.loadLeaderboards(league);
        Leaderboard leaderboard = leaderboards.get(id);
        if (leaderboard == null) {
            player.sendMessage("§cLeaderboard not found: " + id);
            return;
        }

        Location newLoc = player.getLocation().clone();
        // Apply Y offset from config
        double yOffset = league.getConfig().getHologramYOffset();
        newLoc.setY(newLoc.getY() + yOffset);

        leaderboard.setLocation(newLoc);
        storage.saveLeaderboards(league, leaderboards);

        // Respawn at new location
        despawnHologram(league.getId(), id);
        spawnHologram(league, leaderboard);

        player.sendMessage("§aLeaderboard moved to your position!");
    }

    public void refreshLeaderboard(League league, String id) {
        Map<String, Leaderboard> leaderboards = storage.loadLeaderboards(league);
        Leaderboard leaderboard = leaderboards.get(id);
        if (leaderboard == null) return;

        despawnHologram(league.getId(), id);
        spawnHologram(league, leaderboard);
    }

    public void refreshAllLeaderboards(League league) {
        Map<String, Leaderboard> leaderboards = storage.loadLeaderboards(league);
        for (Leaderboard leaderboard : leaderboards.values()) {
            despawnHologram(league.getId(), leaderboard.getId());
            spawnHologram(league, leaderboard);
        }
    }

    public void spawnHologram(League league, Leaderboard leaderboard) {
        if (leaderboard.getLocation() == null) return;

        String hologramName = "league_" + league.getId() + "_" + leaderboard.getId();

        // Remove existing if present
        if (DHAPI.getHologram(hologramName) != null) {
            DHAPI.removeHologram(hologramName);
        }

        List<String> lines = buildHologramLines(league, leaderboard);

        try {
            Hologram hologram = DHAPI.createHologram(hologramName, leaderboard.getLocation(), false, lines);
            activeHolograms.computeIfAbsent(league.getId(), k -> new HashMap<>()).put(leaderboard.getId(), hologram);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to create hologram: " + e.getMessage());
        }
    }

    public void despawnHologram(String leagueId, String leaderboardId) {
        String hologramName = "league_" + leagueId + "_" + leaderboardId;

        if (DHAPI.getHologram(hologramName) != null) {
            DHAPI.removeHologram(hologramName);
        }

        Map<String, Hologram> leagueHolograms = activeHolograms.get(leagueId);
        if (leagueHolograms != null) {
            leagueHolograms.remove(leaderboardId);
        }
    }

    public void despawnAllLeagueHolograms(String leagueId) {
        Map<String, Hologram> leagueHolograms = activeHolograms.remove(leagueId);
        if (leagueHolograms != null) {
            for (String leaderboardId : leagueHolograms.keySet()) {
                String hologramName = "league_" + leagueId + "_" + leaderboardId;
                if (DHAPI.getHologram(hologramName) != null) {
                    DHAPI.removeHologram(hologramName);
                }
            }
        }
    }

    private List<String> buildHologramLines(League league, Leaderboard leaderboard) {
        List<String> lines = new ArrayList<>();
        String pri = league.getConfig().getPrimaryColor();
        String sec = league.getConfig().getSecondaryColor();

        // Header lines
        lines.add(pri + league.getName());

        String seasonLine = pri + "Season " + sec + leaderboard.getSeasonKey();
        if (leaderboard.isRaceResults() && leaderboard.getRaceName() != null) {
            seasonLine += pri + " -> Race " + sec + leaderboard.getRaceName();
        }
        lines.add(seasonLine);

        lines.add("&7&m----------------------------");

        // Content lines
        int maxEntries = league.getConfig().getHologramLeaderboardLength();

        switch (leaderboard.getType()) {
            case TEAMS_CHAMPIONSHIP -> {
                Map<String, Object> champ = storage.loadChampionship(league, leaderboard.getSeasonKey());
                @SuppressWarnings("unchecked")
                Map<String, Double> teams = (Map<String, Double>) champ.getOrDefault("teams", new HashMap<>());

                List<Map.Entry<String, Double>> sorted = teams.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(maxEntries)
                        .toList();

                int pos = 1;
                for (Map.Entry<String, Double> entry : sorted) {
                    String teamId = entry.getKey();
                    Optional<Team> teamOpt = getTeam(league, teamId);
                    String displayName = teamOpt.map(Team::getName).orElse(teamId);
                    String teamColor = teamOpt.map(Team::getChatColor).orElse(sec);

                    lines.add(pri + "P" + pos + ": " + teamColor + displayName + pri + " - " + sec + String.format("%.1f", entry.getValue()) + pri + " pts");
                    pos++;
                }

                // Fill empty slots
                while (pos <= maxEntries) {
                    lines.add(pri + "P" + pos + ": &7[Empty]");
                    pos++;
                }
            }

            case DRIVERS_CHAMPIONSHIP -> {
                Map<String, Object> champ = storage.loadChampionship(league, leaderboard.getSeasonKey());
                @SuppressWarnings("unchecked")
                Map<String, Double> drivers = (Map<String, Double>) champ.getOrDefault("drivers", new HashMap<>());

                List<Map.Entry<String, Double>> sorted = drivers.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(maxEntries)
                        .toList();

                int pos = 1;
                for (Map.Entry<String, Double> entry : sorted) {
                    String playerName = getPlayerName(entry.getKey());
                    lines.add(pri + "P" + pos + ": " + sec + playerName + pri + " - " + sec + String.format("%.1f", entry.getValue()) + pri + " pts");
                    pos++;
                }

                // Fill empty slots
                while (pos <= maxEntries) {
                    lines.add(pri + "P" + pos + ": &7[Empty]");
                    pos++;
                }
            }

            case RACE_RESULTS -> {
                if (leaderboard.getRaceName() != null) {
                    Map<String, Integer> results = storage.getRaceResults(league, leaderboard.getSeasonKey(), leaderboard.getRaceName());
                    Map<String, Integer> positions = storage.getRaceResultPositions(league, leaderboard.getSeasonKey(), leaderboard.getRaceName());

                    List<Map.Entry<String, Integer>> sorted = results.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .limit(maxEntries)
                            .toList();

                    int displayPos = 1;
                    for (Map.Entry<String, Integer> entry : sorted) {
                        String playerName = getPlayerName(entry.getKey());
                        int points = entry.getValue();
                        int pos = positions.getOrDefault(entry.getKey(), 0);
                        if (pos == 0) {
                            lines.add("&cDNF: &7" + playerName + " &7- 0 pts");
                        } else {
                            lines.add(pri + "P" + displayPos + ": " + sec + playerName + pri + " - " + sec + points + pri + " pts");
                            displayPos++;
                        }
                    }

                    // Fill empty slots
                    while (displayPos <= maxEntries) {
                        lines.add(pri + "P" + displayPos + ": &7[Empty]");
                        displayPos++;
                    }
                } else {
                    for (int i = 1; i <= maxEntries; i++) {
                        lines.add(pri + "P" + i + ": &7[No Race Selected]");
                    }
                }
            }
        }

        return lines;
    }

    private Optional<Team> getTeam(League league, String teamId) {
        return Optional.ofNullable(storage.loadTeams(league).get(teamId.toLowerCase()));
    }

    private String getPlayerName(String uuid) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
            return name != null ? name : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public Map<String, Leaderboard> getLeaderboards(League league) {
        return storage.loadLeaderboards(league);
    }

    public Optional<Leaderboard> getLeaderboard(League league, String id) {
        return Optional.ofNullable(storage.loadLeaderboards(league).get(id));
    }

    public boolean leaderboardExists(League league, String id) {
        return storage.loadLeaderboards(league).containsKey(id);
    }
}