package io.bbrl.leaguesystem.service;

import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight DecentHolograms integration stub.
 * Replace with DecentHolograms API calls to create/update holograms.
 */
public class HologramService {
    private final JavaPlugin plugin;
    private final LeagueManager manager;
    private final Map<String, Object> hologramHandles = new HashMap<>();

    public HologramService(JavaPlugin plugin, LeagueManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void updateLeagueHolograms(League league) {
        if (!league.getConfig().isHologramsEnabled()) return;
        // Example: prepare top 10 players and teams
        List<Map.Entry<String,Integer>> players = league.getPlayerPoints().entrySet().stream()
                .sorted((a,b)->b.getValue().compareTo(a.getValue()))
                .limit(10)
                .collect(Collectors.toList());

        List<Map.Entry<String,Integer>> teams = league.getTeamPoints().entrySet().stream()
                .sorted((a,b)->b.getValue().compareTo(a.getValue()))
                .limit(10)
                .collect(Collectors.toList());

        // TODO: call DecentHolograms API to update hologram at configured location(s)
    }
}
