package org.zappier.zappierGames.manhunt;

import org.bukkit.*;
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
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) {
            return;
        }

        // Feature is enabled → allow lava everywhere
        if (Manhunt.netherLavaPvP > 0) {
            return;
        }

        Player placer = event.getPlayer();

        if (event.getBucket() != Material.LAVA_BUCKET) return;
        if (placer.getWorld().getEnvironment() != World.Environment.NETHER) return;

        // This is the block where the lava will actually be placed
        Block targetBlock = event.getBlockClicked().getRelative(event.getBlockFace());

        // Now check players near the *placement location*, not near the placer
        double radius = 5.0;
        Location checkCenter = targetBlock.getLocation().add(0.5, 0.5, 0.5); // center of the block for fair distance

        for (Entity entity : targetBlock.getWorld().getNearbyEntities(checkCenter, radius, radius, radius)) {
            if (!(entity instanceof Player)) continue;

            Player target = (Player) entity;
            if (target.equals(placer)) continue;

            // Skip teammates
            if (DamageHandler.areOnSameTeam(placer, target)) continue;

            // Only cancel if BOTH are in survival/adventure
            GameMode placerMode = placer.getGameMode();
            GameMode targetMode = target.getGameMode();

            if ((placerMode == GameMode.SURVIVAL || placerMode == GameMode.ADVENTURE) &&
                    (targetMode == GameMode.SURVIVAL  || targetMode == GameMode.ADVENTURE)) {

                event.setCancelled(true);
                placer.sendMessage("§cNether Lava PvP is disabled");
                // Optional: target.sendMessage("§cLava placement near you was blocked (PvP disabled)");
                return; // No need to check further players
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmeltInteract(PlayerInteractEvent event) {
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) return;
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
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) return;

        // No Placing Blocks Twist
        if (Manhunt.twists.getOrDefault("No Placing Blocks", false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBuilding is strictly forbidden!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosiveInteract(PlayerInteractEvent event) {
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        World.Environment env = block.getWorld().getEnvironment();
        Material type = block.getType();
        ItemStack itemInHand = event.getItem();

        // Always disable charging respawn anchors (glowstone right-click), everywhere
        if (type == Material.RESPAWN_ANCHOR && itemInHand != null && itemInHand.getType() == Material.GLOWSTONE) {
            event.setCancelled(true);
            player.sendMessage("§cRespawn anchor charging is disabled!");
            return;
        }

        // Bed bombing logic (only in Nether / End)
        if (type.name().contains("_BED") && (env == World.Environment.NETHER || env == World.Environment.THE_END)) {

            // Allow placing blocks on beds if sneaking
            if (player.isSneaking() && itemInHand != null && itemInHand.getType().isBlock()) {
                return;
            }

            // Never allow bed usage if neverBedBomb > 0
            if (Manhunt.neverBedBomb > 0) {
                event.setCancelled(true);
                player.sendMessage("§cBed bombing is permanently disabled!");
                return;
            }

            if (Manhunt.bedBombing > 0) {
                return;
            }

            // Otherwise: check for nearby enemies around the BED location
            double radius = 7.0;
            Location checkCenter = block.getLocation().add(0.5, 0.5, 0.5); // center of bed for fair checking

            for (Entity entity : block.getWorld().getNearbyEntities(checkCenter, radius, radius, radius)) {
                if (!(entity instanceof Player)) continue;

                Player target = (Player) entity;
                if (target.equals(player)) continue;
                if (DamageHandler.areOnSameTeam(player, target)) continue;

                // Only cancel if BOTH are in survival/adventure
                GameMode playerMode = player.getGameMode();
                GameMode targetMode = target.getGameMode();

                if ((playerMode == GameMode.SURVIVAL || playerMode == GameMode.ADVENTURE) &&
                        (targetMode == GameMode.SURVIVAL || targetMode == GameMode.ADVENTURE)) {

                    event.setCancelled(true);
                    player.sendMessage("§cBed bombing near enemies is disabled!");
                    return; // Stop after first valid enemy found
                }
            }
        }
    }
}