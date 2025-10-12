//This file was made almost entirely by ChatGPT
package org.zappier.zappierGames;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.UUID;

public class DamageHandler implements Listener {

    private final HashSet<UUID> keepItemsPlayers = new HashSet<>(); // Tracks players who keep items on "death"

    // Toggle keep items for a player
    public void toggleKeepItems(Player player) {
        UUID playerId = player.getUniqueId();
        if (keepItemsPlayers.contains(playerId)) {
            keepItemsPlayers.remove(playerId);
            player.sendMessage("Item dropping on 'death' is now ENABLED.");
        } else {
            keepItemsPlayers.add(playerId);
            player.sendMessage("Item dropping on 'death' is now DISABLED.");
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Check if damage would "kill" the player
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) return; // Player survives

        // Cancel the "death"
        event.setCancelled(true);

        // Handle item dropping
        if (!keepItemsPlayers.contains(player.getUniqueId())) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            player.getInventory().clear();
        }

        // Transition the player to spectator mode
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("You have been defeated and are now in spectator mode.");

        // Optional: Broadcast a message to the server
        Bukkit.broadcastMessage(player.getName() + " has fallen and entered spectator mode.");
        respawnPlayer(player);
    }

    public void respawnPlayer(Player player) {
        // Respawn the player manually
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.teleport(player.getWorld().getSpawnLocation()); // Teleport to spawn or a set location
            player.setGameMode(GameMode.SURVIVAL); // Reset game mode
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.setExperienceLevelAndProgress(0);
            player.sendMessage("You have respawned!");
        }
    }
}
