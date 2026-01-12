package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.zappier.zappierGames.biomeparkour.BiomeParkour;
import org.zappier.zappierGames.loothunt.LootHunt;
import org.zappier.zappierGames.manhunt.Manhunt;

import java.util.UUID;

public class PlayerDeathListener implements Listener {

    private final JavaPlugin plugin;

    public PlayerDeathListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static String[] teamList = {"Runners", "Hunters", "Runner_Suppliers", "Hunter_Suppliers", "President", "Bodyguard", "Spectator"};

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        int gameMode = ZappierGames.gameMode; // Retrieve the game mode

        // Check the player's current team
        String playerTeam = getPlayerTeam(player, scoreboard);

        switch (gameMode) {
            case 0: // LootHunt
                //Bukkit.broadcastMessage(player.getName() + " has died and will lose points!");
                LootHunt.playerDeathCounts.put(player.getName().toUpperCase(), LootHunt.playerDeathCounts.getOrDefault(player.getName().toUpperCase(), 0) + 1);
                break;

            case 1: // Manhunt
                if ("Runners".equals(playerTeam)) {
                    movePlayerToTeam(player, "Spectator", scoreboard);
                    Bukkit.broadcastMessage(player.getName() + " has been eliminated as a Runner!");
                    player.setGameMode(GameMode.SPECTATOR);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                    }
                }
                break;

            case 2: // Infection Manhunt
                if ("Runners".equals(playerTeam)) {
                    int runnerCount = 0;
                    movePlayerToTeam(player, "Hunters", scoreboard);
                    Bukkit.broadcastMessage(player.getName() + " has been infected and joined the Hunters!");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (getPlayerTeam(p, scoreboard).equals("Runners")) {
                            runnerCount++;
                        }
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                    }
                    if (runnerCount == 0) {
                        Bukkit.broadcastMessage("Game over! The hunters win!");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendTitle("Hunters Win!", "All Runners Died!");
                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                        }
                    }
                }
                break;

            case 3: // President Manhunt
                if ("Bodyguard".equals(playerTeam) && Manhunt.bodyguardRespawn <= 0) {
                    movePlayerToTeam(player, "Spectator", scoreboard);
                    Bukkit.broadcastMessage(player.getName() + " has been eliminated as a Bodyguard!");
                    player.setGameMode(GameMode.SPECTATOR);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                    }
                } else if ("President".equals(playerTeam)) {
                    movePlayerToTeam(player, "Bodyguard", scoreboard);
                    int presidentCount = 0;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (getPlayerTeam(p, scoreboard).equals("President")) {
                            presidentCount++;
                            if (Manhunt.presidentDeathLink > 0) {
                                presidentCount--;
                                p.setGameMode(GameMode.SPECTATOR);
                                movePlayerToTeam(p, "Spectator", scoreboard);
                            }
                        }
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                    }
                    if (presidentCount > 0) {
                        Bukkit.broadcastMessage("President " + player.getName() + " has died and became a bodyguard!");
                    } else {
                        Bukkit.broadcastMessage("President " + player.getName() + " is dead! Hunters win!");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendTitle("Hunters Win!", "The President is Dead!");
                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                        }
                    }
                }
                break;

            case 10:
                player.getInventory().remove(Material.BLACK_CONCRETE);
                player.getInventory().remove(Material.BLUE_CONCRETE);
                player.getInventory().remove(Material.BROWN_CONCRETE);
                player.getInventory().remove(Material.CYAN_CONCRETE);
                player.getInventory().remove(Material.GRAY_CONCRETE);
                player.getInventory().remove(Material.GREEN_CONCRETE);
                player.getInventory().remove(Material.LIGHT_BLUE_CONCRETE);
                player.getInventory().remove(Material.LIGHT_GRAY_CONCRETE);
                player.getInventory().remove(Material.LIME_CONCRETE);
                player.getInventory().remove(Material.MAGENTA_CONCRETE);
                player.getInventory().remove(Material.ORANGE_CONCRETE);
                player.getInventory().remove(Material.PINK_CONCRETE);
                player.getInventory().remove(Material.PURPLE_CONCRETE);
                player.getInventory().remove(Material.RED_CONCRETE);
                player.getInventory().remove(Material.WHITE_CONCRETE);
                player.getInventory().remove(Material.YELLOW_CONCRETE);
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                player.getInventory().clear();
                player.setGameMode(GameMode.SPECTATOR);
                player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1.0f, 0.8f);
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(5.0f);
                player.setExperienceLevelAndProgress(0);
                if (player.getY() < 80) {
                    player.teleport(new Location(player.getWorld(), 0, 150, 0));
                }


                Team diedTeam = player.getScoreboard().getEntryTeam(player.getName());
                Component diedPrefixComponent = Component.empty();
                if (diedTeam != null) {
                    diedPrefixComponent = diedTeam.prefix();
                }

                if (player.getKiller() == null) {
                    Entity killer = event.getDamageSource().getCausingEntity();
                    if (killer != null) {

                        Team killerTeam = getTeamOfEntity(killer);
                        Component killerPrefixComponent = Component.empty();
                        if (killerTeam != null) {
                            killerPrefixComponent = killerTeam.prefix();
                        }
                        Bukkit.broadcast(diedPrefixComponent.append(player.displayName()).append(Component.text(" died to ")).append(killerPrefixComponent.append(Component.text(killer.getName() + ""))));
                    } else {
                        Bukkit.broadcast(diedPrefixComponent.append(player.displayName()).append(Component.text(" died of natural causes").color(null)));
                    }
                } else {
                    Team killerTeam = player.getScoreboard().getEntryTeam(player.getKiller().getName());
                    Component killerPrefixComponent = Component.empty();
                    if (killerTeam != null) {
                        killerPrefixComponent = killerTeam.prefix();
                    }
                    Bukkit.broadcast(diedPrefixComponent.append(player.displayName()).append(Component.text(" was killed by ").color(null)).append(killerPrefixComponent.append(player.getKiller().displayName())).append(Component.text("").color(null)));
                }
                //Bukkit.broadcastMessage("DEBUG: GIVE ALL SURVIVING PLAYERS +3 POINTS!");

                event.setCancelled(true);
                break;
            case 30: // Biome Parkour
                UUID uuid = player.getUniqueId();
                int lives = BiomeParkour.playerLives.getOrDefault(uuid, 0) - 1;
                BiomeParkour.playerLives.put(uuid, lives);

                if (lives <= 0) {
                    player.setGameMode(GameMode.SPECTATOR);
                    Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " is out of lives and is now spectating!");
                    // Check win condition will be handled in BiomeParkour.run()
                } else {
                    // Respawn with penalty
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.spigot().respawn();
                            player.teleport(player.getWorld().getSpawnLocation());

                            if (BiomeParkour.respawnLosePoints) {
                                String key = player.getName().toUpperCase();
                                double score = BiomeParkour.playerScores.getOrDefault(key, 0.0) - 100.0;
                                BiomeParkour.playerScores.put(key, Math.max(0, score));
                                player.sendMessage(ChatColor.RED + "You lost 100 points for dying!");
                            }
                            player.sendMessage(ChatColor.YELLOW + "Lives remaining: " + ChatColor.RED + lives);
                        }
                    }.runTaskLater(plugin, 20L); // 1 second delay
                }
                event.setKeepInventory(true);
                event.getDrops().clear();
                break;
        }
    }

    // Helper method to get the team of a player
    private String getPlayerTeam(Player player, Scoreboard scoreboard) {
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                return team.getName();
            }
        }
        return null;
    }

    // Helper method to move a player to a different team
    private void movePlayerToTeam(Player player, String teamName, Scoreboard scoreboard) {
        // Remove from current team
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }

        // Add to new team
        Team newTeam = scoreboard.getTeam(teamName);
        if (newTeam != null) {
            newTeam.addEntry(player.getName());
        }
    }

    public static Team getTeamOfEntity(Entity entity) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(entity.getUniqueId().toString())) {
                return team;
            }
        }
        return null; // Player not found in any team
    }

    // Helper method to respawn the player after a delay
    private void respawnPlayer(Player player, int delaySeconds) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.SURVIVAL); // Change to survival after delay
                player.teleport(player.getWorld().getSpawnLocation()); // Optional: teleport to spawn
                player.sendMessage("You have been respawned!");
            }
        }.runTaskLater(plugin, delaySeconds * 20L); // Delay in ticks (20 ticks = 1 second)
    }
}