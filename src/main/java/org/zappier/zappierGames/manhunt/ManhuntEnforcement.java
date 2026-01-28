package org.zappier.zappierGames.manhunt;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.zappier.zappierGames.DamageHandler;
import org.zappier.zappierGames.ZappierGames;
import org.zappier.zappierGames.manhunt.Manhunt;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.zappier.zappierGames.skybattle.Skybattle.getPlayerTeam;

public class ManhuntEnforcement implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) return;
        if (!Manhunt.twists.getOrDefault("Hands Full", false)) return;

        if (player.getGameMode() != GameMode.SURVIVAL &&
                player.getGameMode() != GameMode.ADVENTURE) return;

        // Only care about the player's own inventory
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        int slot = event.getSlot();

        if (slot >= 9 && slot <= 35) {
            event.setCancelled(true);
            player.sendActionBar("§cHands Full: you can't manage your inventory right now!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) return;
        if (!Manhunt.twists.getOrDefault("Hands Full", false)) return;

        if (player.getGameMode() != GameMode.SURVIVAL &&
                player.getGameMode() != GameMode.ADVENTURE) return;

        InventoryView view = event.getView();

        // Cancel if ANY dragged slot touches the main inventory (9–35)
        for (int slot : event.getRawSlots()) {
            if (slot >= 9 && slot <= 35) {
                event.setCancelled(true);
                player.sendActionBar("§cHands Full: dragging items is disabled!");
                return;
            }
        }
    }

    private static final Random random = new Random();

    // List of hostile mobs that can spawn (no boss entities)
    private static final List<EntityType> HOSTILE_MOBS = Arrays.asList(
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.CREEPER,
            EntityType.ENDERMAN,
            EntityType.BLAZE,
            EntityType.WITCH,
            EntityType.SILVERFISH,
            EntityType.GUARDIAN,
            EntityType.DROWNED,
            EntityType.HUSK,
            EntityType.STRAY,
            EntityType.PHANTOM,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER,
            //EntityType.RAVAGER, //actually nah
            EntityType.VEX,
            EntityType.HOGLIN,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.ZOGLIN,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.MAGMA_CUBE,
            EntityType.SLIME,
            EntityType.WITHER_SKELETON,
            EntityType.GHAST,
            EntityType.ENDERMITE,
            EntityType.SHULKER
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) {
            return;
        }

        if (!Manhunt.twists.getOrDefault("Mob Mayhem", false)) {
            return;
        }

        // Check if a player damaged another player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();

        // Only spawn mobs if victim is in survival/adventure mode
        if (victim.getGameMode() != GameMode.SURVIVAL && victim.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        // Spawn a random hostile mob at the victim's location
        EntityType mobType = HOSTILE_MOBS.get(random.nextInt(HOSTILE_MOBS.size()));
        Location spawnLoc = victim.getLocation();

        try {
            Entity spawnedMob = victim.getWorld().spawnEntity(spawnLoc, mobType);

            // Play a sound effect
            victim.playSound(spawnLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 0.7f);

        } catch (IllegalArgumentException e) {
            // If mob type can't spawn in this dimension, try a different one
            Bukkit.getLogger().warning("Failed to spawn " + mobType + " for Mob Mayhem twist");
        }
    }

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
                return; // No need to check further players
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCobwebPlace(BlockPlaceEvent event) {
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) {
            return;
        }

        // Feature is enabled -> allow cobwebs everywhere in nether
        if (Manhunt.netherCobwebPvP > 0) {
            return;
        }

        Player placer = event.getPlayer();

        // Only care about cobweb being placed
        if (event.getBlockPlaced().getType() != Material.COBWEB) {
            return;
        }

        // Only in the Nether
        if (placer.getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }

        // This is the block where the cobweb is actually being placed
        Block targetBlock = event.getBlockPlaced();

        // Check players near the placement location
        double radius = 5.0;
        Location checkCenter = targetBlock.getLocation().add(0.5, 0.5, 0.5); // center of the block

        for (Entity entity : targetBlock.getWorld().getNearbyEntities(checkCenter, radius, radius, radius)) {
            if (!(entity instanceof Player)) continue;

            Player target = (Player) entity;
            if (target.equals(placer)) continue;

            // Skip teammates
            if (DamageHandler.areOnSameTeam(placer, target)) continue;

            // Only cancel if BOTH are in survival/adventure mode
            GameMode placerMode = placer.getGameMode();
            GameMode targetMode = target.getGameMode();

            if ((placerMode == GameMode.SURVIVAL || placerMode == GameMode.ADVENTURE) &&
                    (targetMode == GameMode.SURVIVAL  || targetMode == GameMode.ADVENTURE)) {

                event.setCancelled(true);
                placer.sendMessage("§cNether Cobweb PvP is disabled");
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
        if (Manhunt.anchorBombing <= 0 && (env != World.Environment.NETHER) && type == Material.RESPAWN_ANCHOR && itemInHand != null && itemInHand.getType() == Material.GLOWSTONE) {
            event.setCancelled(true);
            player.sendMessage("§cRespawn anchor charging outside the nether is disabled!");
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


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) {
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        if (!Manhunt.twists.getOrDefault("Picky Eaters", false)) {
            return;
        }

        if (Manhunt.allowedFoods.isEmpty() || Manhunt.allowedFoods.size() != 5) {
            return;
        }

        Material consumedType = event.getItem().getType();

        if (!consumedType.isEdible()) {
            return;
        }

        if (!Manhunt.allowedFoods.contains(consumedType)) {
            event.setCancelled(true);

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
            player.sendActionBar(ChatColor.RED + "Picky Eaters: that food is not allowed!");
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!Manhunt.twists.get("Side Quest")) return;

        Player player = event.getPlayer();
        String team = getPlayerTeam(player);
        if (!team.equals("Runners") && !team.equals("Runner_Suppliers") && !team.equals("Bodyguard") && !team.equals("President")) {
            return; // only runners count
        }

        String advancementId = event.getAdvancement().getKey().toString(); // e.g. "minecraft:adventure/trade"
        //Bukkit.broadcastMessage("ADVANCEMENT: " + advancementId);
        if (Manhunt.sideQuestAdvancementIds.contains(advancementId) &&
                !Manhunt.completedSideQuests.contains(advancementId)) {

            Manhunt.completedSideQuests.add(advancementId);

            String friendly = Manhunt.sideQuestAdvancementDisplays.get(
                    Manhunt.sideQuestAdvancementIds.indexOf(advancementId)
            );
            Bukkit.broadcastMessage(ChatColor.GREEN + "Side Quest progress: " + ChatColor.YELLOW + friendly +
                    ChatColor.GREEN + " completed! (" +
                    Manhunt.completedSideQuests.size() + "/" + Manhunt.sideQuestAdvancementIds.size() + ")");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

            // Check if all are done → runners win
            if (Manhunt.completedSideQuests.size() >= Manhunt.sideQuestAdvancementIds.size()) {
                Bukkit.broadcastMessage(ChatColor.GREEN + "§lSIDE QUEST COMPLETE!");
                // → trigger win condition for runners here
                // e.g. ZappierGames.endGame("Runners", "completed side quests");
                // or call your existing win method
            }
        }
    }
}