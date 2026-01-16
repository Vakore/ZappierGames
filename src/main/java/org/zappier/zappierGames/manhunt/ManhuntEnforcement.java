package org.zappier.zappierGames.manhunt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.zappier.zappierGames.DamageHandler;
import org.zappier.zappierGames.ZappierGames;
import org.zappier.zappierGames.manhunt.Manhunt;
import org.bukkit.event.block.BlockPlaceEvent;

public class ManhuntEnforcement implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLavaPlace(PlayerBucketEmptyEvent event) {
        if (ZappierGames.gameMode > 5) {return;}
        if (Manhunt.netherLavaPvP <= 0) {
            Player player = event.getPlayer();

            if (event.getBucket() == Material.LAVA_BUCKET &&
                    player.getWorld().getEnvironment() == World.Environment.NETHER) {
                double radius = 5.0;
                for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (entity instanceof Player && !entity.equals(player) && !DamageHandler.areOnSameTeam(player, (Player)entity)) {
                        event.setCancelled(true);
                        player.sendMessage("§cNether Lava PvP is disabled");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmeltInteract(PlayerInteractEvent event) {
        if (ZappierGames.gameMode > 5) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // No Smelting Twist
        if (Manhunt.twists.getOrDefault("No Smelting", false)) {
            Material type = block.getType();
            if (type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cFurnaces are disabled for this twist!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (ZappierGames.gameMode > 5) return;

        // No Placing Blocks Twist
        if (Manhunt.twists.getOrDefault("No Placing Blocks", false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBuilding is strictly forbidden!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosiveInteract(PlayerInteractEvent event) {
        if (ZappierGames.gameMode > 5) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        World.Environment env = block.getWorld().getEnvironment();
        Material type = block.getType();
        ItemStack itemInHand = event.getItem();

        // 1. Bed Bombing Logic
        if (Manhunt.bedBombing <= 0) {
            if (type.name().contains("_BED") && (env == World.Environment.NETHER || env == World.Environment.THE_END)) {
                // Allow placing blocks on beds if sneaking
                if (player.isSneaking() && itemInHand != null && itemInHand.getType().isBlock()) {
                    return;
                }
                event.setCancelled(true);
                player.sendMessage("§cBed bombing is disabled!");
            }
        }

        if (Manhunt.anchorBombing <= 0) {
            if (type == Material.RESPAWN_ANCHOR && (env == World.Environment.NORMAL || env == World.Environment.THE_END)) {
                if (itemInHand != null && itemInHand.getType() == Material.GLOWSTONE) {
                    event.setCancelled(true);
                    player.sendMessage("§cAnchor bombing is disabled!");
                    return;
                }
            }
        }
    }
}