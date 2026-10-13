package io.bbrl.leaguesystem;

import io.bbrl.leaguesystem.command.LeagueAdminCommand;
import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.config.LeagueStorage;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.HologramService;
import io.bbrl.leaguesystem.service.LeaderboardManager;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {
    private static Main instance;
    private LeagueStorage storage;
    private LeagueManager leagueManager;
    private LeaderboardManager leaderboardManager;
    private HologramService hologramService;

    // Global config values
    private String defaultPointSystem;
    private boolean enableHolograms;
    private int hologramRefreshSeconds;
    private int maxLeaguesPerPlayer;
    private String defaultTeamColor;
    private String defaultLeaguePrimaryColor;
    private String defaultLeagueSecondaryColor;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadGlobalConfig();

        storage = new LeagueStorage(this);
        leagueManager = new LeagueManager(storage);
        leaderboardManager = new LeaderboardManager(storage);
        hologramService = new HologramService(this, leagueManager, leaderboardManager);

        LeagueCommand leagueCommand = new LeagueCommand(leagueManager, hologramService, leaderboardManager);
        getCommand("league").setExecutor(leagueCommand);
        getCommand("league").setTabCompleter(leagueCommand);

        // Register leagueadmin command
        LeagueAdminCommand adminCommand = new LeagueAdminCommand(this);
        getCommand("leagueadmin").setExecutor(adminCommand);
        getCommand("leagueadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(this, this);

        // Spawn all holograms on startup
        getServer().getScheduler().runTaskLater(this, () -> {
            for (League league : leagueManager.allLeagues()) {
                leaderboardManager.refreshAllLeaderboards(league);
            }
        }, 40L); // 2 seconds after startup

        getLogger().info("LeagueSystem enabled");
    }

    public void loadGlobalConfig() {
        FileConfiguration config = getConfig();
        defaultPointSystem = config.getString("default-point-system", "f1");
        enableHolograms = config.getBoolean("enable-holograms", true);
        hologramRefreshSeconds = config.getInt("hologram-refresh-seconds", 30);
        maxLeaguesPerPlayer = config.getInt("max-leagues-per-player", 5);
        defaultTeamColor = config.getString("default-team-color", "#FFFFFF");
        defaultLeaguePrimaryColor = config.getString("default-league-primary-color", "§6");
        defaultLeagueSecondaryColor = config.getString("default-league-secondary-color", "§f");

        getLogger().info("Config loaded: pointSystem=" + defaultPointSystem +
                ", holograms=" + enableHolograms +
                ", refresh=" + hologramRefreshSeconds +
                ", maxLeagues=" + maxLeaguesPerPlayer +
                ", defaultColor=" + defaultTeamColor +
                ", leaguePrimary=" + defaultLeaguePrimaryColor +
                ", leagueSecondary=" + defaultLeagueSecondaryColor);
    }

    /* deliver offline invites 5 seconds after join */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        getServer().getScheduler().runTaskLater(this, () -> {
            leagueManager.deliverOfflineInvites(e.getPlayer());
        }, 100L); // 5 seconds (20 ticks = 1 second)
    }

    public static Main getInstance() {
        return instance;
    }

    public LeagueManager getLeagueManager() { return leagueManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public LeagueStorage getStorage() { return storage; }

    /* Accessors for global config */
    public String getDefaultPointSystem() { return defaultPointSystem; }
    public boolean isHologramsEnabled() { return enableHolograms; }
    public int getHologramRefreshSeconds() { return hologramRefreshSeconds; }
    public int getMaxLeaguesPerPlayer() { return maxLeaguesPerPlayer; }
    public String getDefaultTeamColor() { return defaultTeamColor; }
    public String getDefaultLeaguePrimaryColor() { return defaultLeaguePrimaryColor; }
    public String getDefaultLeagueSecondaryColor() { return defaultLeagueSecondaryColor; }

    @Override
    public void onDisable() {
        // Despawn all holograms on shutdown
        if (leaderboardManager != null) {
            for (League league : leagueManager.allLeagues()) {
                leaderboardManager.despawnAllLeagueHolograms(league.getId());
            }
        }
        getLogger().info("LeagueSystem disabled");
    }
}