package org.zappier.zappierGames;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.zappier.zappierGames.skybattle.Skybattle;

public class AutoTNTListener implements Listener {

    private final JavaPlugin plugin;
    private final String worldName; // the world where TNT should auto-prime

    public AutoTNTListener(JavaPlugin plugin, String worldName) {
        this.plugin = plugin;
        this.worldName = worldName;
    }

    @EventHandler
    public void onTntPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Only trigger in the target world
        if (!world.getName().equalsIgnoreCase(worldName)) return;

        if (event.getBlockPlaced().getType() == Material.TNT) {
            event.setCancelled(true); // stop the normal placement

            Location loc = event.getBlockPlaced().getLocation().add(0.5, 0, 0.5);

            // Spawn primed TNT instantly
            TNTPrimed tnt = (TNTPrimed) world.spawnEntity(loc, EntityType.TNT);
            tnt.setFuseTicks((Skybattle.TWISTS[0] == 1) ? 40 : 80); // default 4 seconds; set lower if you want instant detonation
            tnt.setSource(player); // sets player as cause

            // Optional: remove one TNT from the player's hand
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                player.getInventory().getItemInMainHand().setAmount(
                        player.getInventory().getItemInMainHand().getAmount() - 1
                );
            }
        }
    }
}
