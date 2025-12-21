package io.bbrl.leaguesystem;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.config.LeagueStorage;
import io.bbrl.leaguesystem.model.League;        // FIXED import
import io.bbrl.leaguesystem.service.HologramService;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    private static Main instance;
    private LeagueStorage storage;
    private LeagueManager leagueManager;
    private HologramService hologramService;

    // Global config values
    private String defaultPointSystem;
    private boolean enableHolograms;
    private int hologramRefreshSeconds;
    private int maxLeaguesPerPlayer;
    private String defaultTeamColor;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadGlobalConfig();

        storage = new LeagueStorage(this);
        storage.ensureFolders();
        storage.loadAll();
        leagueManager = new LeagueManager(storage);
        hologramService = new HologramService(this, leagueManager);

        LeagueCommand leagueCommand = new LeagueCommand(leagueManager, hologramService);
        getCommand("league").setExecutor(leagueCommand);
        getCommand("league").setTabCompleter(leagueCommand);

        /* one-time migration for old leagues */
        storage.allLeagues().forEach(League::migrateOldData);

        getLogger().info("LeagueSystem enabled");
    }

    private void loadGlobalConfig() {
        FileConfiguration config = getConfig();
        defaultPointSystem = config.getString("default-point-system", "standard");
        enableHolograms = config.getBoolean("enable-holograms", true);
        hologramRefreshSeconds = config.getInt("hologram-refresh-seconds", 30);
        maxLeaguesPerPlayer = config.getInt("max-leagues-per-player", 5);
        defaultTeamColor = config.getString("default-team-color", "#FFFFFF");

        getLogger().info("Config loaded: pointSystem=" + defaultPointSystem +
                ", holograms=" + enableHolograms +
                ", refresh=" + hologramRefreshSeconds +
                ", maxLeagues=" + maxLeaguesPerPlayer +
                ", defaultColor=" + defaultTeamColor);
    }

    public static Main getInstance() {
        return instance;
    }

    public LeagueManager getLeagueManager() { return leagueManager; }
    public LeagueStorage getStorage() { return storage; }

    /* Accessors for global config */
    public String getDefaultPointSystem() { return defaultPointSystem; }
    public boolean isHologramsEnabled() { return enableHolograms; }
    public int getHologramRefreshSeconds() { return hologramRefreshSeconds; }
    public int getMaxLeaguesPerPlayer() { return maxLeaguesPerPlayer; }
    public String getDefaultTeamColor() { return defaultTeamColor; }
}