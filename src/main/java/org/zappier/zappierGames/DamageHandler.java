//This file was made almost entirely by ChatGPT
package org.zappier.zappierGames;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
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
        if (ZappierGames.gameMode != 10) {return;}
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getDamageSource().getCausingEntity() instanceof Creeper creeper) {
            //Bukkit.broadcastMessage("Creeper! " + event.getEntity().getName() + ", " + event.getDamageSource().getCausingEntity().getName());
            Player attacker = Bukkit.getPlayer(event.getDamageSource().getCausingEntity().getName());
            if (attacker != null && attacker.isOnline()) {
                event.setCancelled(true);
                ((Player)(event.getEntity())).damage(event.getDamage(), attacker);
            }
        }

    }
}
