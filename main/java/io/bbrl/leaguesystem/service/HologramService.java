package io.bbrl.leaguesystem.service;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.config.LeagueStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Lightweight DecentHolograms integration stub.
 * Reads standings straight from DB – no files, no caches.
 */
public class HologramService {
    private final JavaPlugin plugin;
    private final LeagueManager manager;
    private final LeagueStorage storage;

    public HologramService(JavaPlugin plugin, LeagueManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = manager.getStorage();
    }

    public void updateLeagueHolograms(League league) {
        if (!league.getConfig().isHologramsEnabled()) return;

        // Fresh from DB
        Map<String, Double> drivers = (Map<String, Double>) storage.loadChampionship(league, league.getCurrentSeason()).get("drivers");
        Map<String, Double> teams   = (Map<String, Double>) storage.loadChampionship(league, league.getCurrentSeason()).get("teams");

        // TODO: feed drivers + teams into DecentHolograms API
    }
}