package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
                }
                break;

            case 2: // Infection Manhunt
                if ("Runners".equals(playerTeam)) {
                    movePlayerToTeam(player, "Hunters", scoreboard);
                    Bukkit.broadcastMessage(player.getName() + " has been infected and joined the Hunters!");
                }
                break;

            case 3: // President Manhunt
                if ("Bodyguard".equals(playerTeam)) {
                    //player.setGameMode(GameMode.SPECTATOR);
                    //respawnPlayer(player, 60);
                } else if ("President".equals(playerTeam)) {
                    movePlayerToTeam(player, "Bodyguard", scoreboard);
                    //player.setGameMode(GameMode.SPECTATOR);
                    //respawnPlayer(player, 60);

                    // Check if there are any Presidents left
                    Team presidentTeam = scoreboard.getTeam("President");
                    if (presidentTeam != null && presidentTeam.getEntries().isEmpty()) {
                        Bukkit.broadcastMessage("The Hunters have won!");
                    }
                }
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