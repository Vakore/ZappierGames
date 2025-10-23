package org.zappier.zappierGames;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.Component;
import java.util.Comparator;

public class CreeperSpawnListener implements Listener {

    @EventHandler
    public void onCreeperSpawn(CreatureSpawnEvent event) {
        // 1. Check if the entity is a Creeper and spawned by a spawn egg
        if (event.getEntityType() != EntityType.CREEPER ||
                event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        Creeper creeper = (Creeper) event.getEntity();
        Player player = getPlacer(creeper); // Custom method to find the placer (see step 3)

        if (player == null) {
            return; // Couldn't determine the placer
        }

        // 2. Set Creeper's custom name
        Component creeperName = Component.text(player.getName()); // You may want to add color or style
        creeper.customName(creeperName);
        creeper.setCustomNameVisible(true); // Make the nameplate visible

        // 3. Add Creeper to the player's team
        Team playerTeam = player.getScoreboard().getEntryTeam(player.getName());
        if (playerTeam != null) {
            // Entities are added to teams by their UUID string
            playerTeam.addEntry(creeper.getUniqueId().toString());
        }
    }

    // Helper method to find the closest player to the spawned creeper's location
    private Player getPlacer(Creeper creeper) {
        return creeper.getNearbyEntities(3, 3, 3).stream() // Check within a 3-block radius
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                // Find the closest player to minimize false positives
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(creeper.getLocation())))
                .orElse(null);
    }
}
