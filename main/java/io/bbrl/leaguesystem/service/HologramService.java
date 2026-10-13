package io.bbrl.leaguesystem.service;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.config.LeagueStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * DecentHolograms integration for league leaderboards.
 * Manages holographic displays of standings and results.
 */
public class HologramService {
    private final JavaPlugin plugin;
    private final LeagueManager manager;
    private final LeagueStorage storage;
    private final LeaderboardManager leaderboardManager;

    public HologramService(JavaPlugin plugin, LeagueManager manager, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = manager.getStorage();
        this.leaderboardManager = leaderboardManager;
    }

    public void updateLeagueHolograms(League league) {
        if (!league.getConfig().isHologramsEnabled()) return;

        // Refresh all leaderboards for this league
        leaderboardManager.refreshAllLeaderboards(league);
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
}