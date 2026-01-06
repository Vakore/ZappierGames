package org.zappier.zappierGames.skybattle;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.Component;

public class CreeperSpawnListener implements Listener {

    @EventHandler
    public void onPlayerUseSpawnEgg(PlayerInteractEvent event) {
        // 1. Check if the action is right-clicking a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        // 2. Check if the player is holding a Creeper Spawn Egg
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.CREEPER_SPAWN_EGG) {
            return;
        }

        // Cancel the event so the game doesn't spawn a "normal" creeper
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Consume one egg if not in creative mode
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        // 3. Spawn the Creeper at the clicked location (offset by face)
        Creeper creeper = (Creeper) event.getClickedBlock().getWorld().spawnEntity(
                event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5),
                EntityType.CREEPER
        );

        // 4. Apply customizations
        setupCreeper(creeper, player);
    }

    private void setupCreeper(Creeper creeper, Player player) {
        // Set nameplate
        Component creeperName = Component.text(player.getName() + "'s Creeper");
        creeper.customName(creeperName);
        creeper.setCustomNameVisible(true);

        // Add to player's team
        Team playerTeam = player.getScoreboard().getEntryTeam(player.getName());
        if (playerTeam != null) {
            playerTeam.addEntry(creeper.getUniqueId().toString());
        }
    }
}
