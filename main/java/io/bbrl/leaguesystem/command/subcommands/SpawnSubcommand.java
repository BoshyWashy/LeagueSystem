package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.model.League;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class SpawnSubcommand implements LeagueCommand.Subcommand {
    private final LeagueManager manager;

    public SpawnSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /league <league> setspawn|spawn");
            return;
        }
        String leagueId = args[0].toLowerCase();
        String action = args[1].toLowerCase();

        Optional<League> ol = manager.getLeague(leagueId);
        if (ol.isEmpty()) {
            sender.sendMessage("§cLeague not found: " + leagueId);
            return;
        }
        League league = ol.get();

        switch (action) {
            case "setspawn" -> handleSetSpawn(sender, league);
            case "spawn" -> handleSpawn(sender, league);
            default -> sender.sendMessage("§cUnknown action. Use: setspawn, spawn");
        }
    }

    private void handleSetSpawn(CommandSender sender, League league) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can set spawn locations");
            return;
        }

        boolean isLeagueOwner = p.hasPermission("league.leagueowner." + league.getId());
        boolean isOp = p.hasPermission("league.op");

        if (!isLeagueOwner && !isOp) {
            sender.sendMessage("§cYou need permission 'league.leagueowner." + league.getId() + "' or 'league.op' to set spawn");
            return;
        }

        Location loc = p.getLocation();

        league.getConfig().setSpawnWorld(loc.getWorld().getName());
        league.getConfig().setSpawnX(loc.getX());
        league.getConfig().setSpawnY(loc.getY());
        league.getConfig().setSpawnZ(loc.getZ());
        league.getConfig().setSpawnYaw(loc.getYaw());
        league.getConfig().setSpawnPitch(loc.getPitch());
        league.getConfig().setSpawnSet(true);

        manager.saveLeague(league);

        sender.sendMessage("§aSpawn location set for league §f" + league.getName());
        sender.sendMessage("§7Location: §f" + String.format("%.2f", loc.getX()) + ", " +
                String.format("%.2f", loc.getY()) + ", " +
                String.format("%.2f", loc.getZ()) +
                " §7in §f" + loc.getWorld().getName());
        sender.sendMessage("§7Direction: §fYaw=" + String.format("%.1f", loc.getYaw()) +
                "§7, §fPitch=" + String.format("%.1f", loc.getPitch()));
    }

    private void handleSpawn(CommandSender sender, League league) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can teleport to spawn");
            return;
        }

        if (!p.hasPermission("league.use") && !p.hasPermission("league.op")) {
            sender.sendMessage("§cYou need permission 'league.use' to teleport to league spawn");
            return;
        }

        if (!league.getConfig().isSpawnSet()) {
            sender.sendMessage("§cNo spawn location set for this league");
            sender.sendMessage("§cA league owner must set it first with: /league " + league.getId() + " setspawn");
            return;
        }

        World world = Bukkit.getWorld(league.getConfig().getSpawnWorld());
        if (world == null) {
            sender.sendMessage("§cSpawn world is not available: " + league.getConfig().getSpawnWorld());
            return;
        }

        Location spawnLoc = new Location(
                world,
                league.getConfig().getSpawnX(),
                league.getConfig().getSpawnY(),
                league.getConfig().getSpawnZ(),
                league.getConfig().getSpawnYaw(),
                league.getConfig().getSpawnPitch()
        );

        p.teleport(spawnLoc);
        sender.sendMessage("§aTeleported to §f" + league.getName() + " §aspawn!");
    }
}