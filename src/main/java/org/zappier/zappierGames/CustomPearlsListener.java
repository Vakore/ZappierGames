package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CustomPearlsListener implements Listener {
    private final NamespacedKey sbitemKey;
    private final JavaPlugin plugin;

    public CustomPearlsListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.sbitemKey = new NamespacedKey(plugin, "sbitem");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("CustomPearlsListener registered for Minecraft 1.21.3!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process main hand to prevent double triggering
        if (event.getHand() != EquipmentSlot.HAND) {
            //plugin.getLogger().info("Ignoring offhand or other slot interaction");
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        //plugin.getLogger().info("Interact event triggered by " + player.getName() + " with item: " + (item != null ? item.getType() : "null"));

        if (item == null || item.getType() != Material.ENDER_PEARL) {
            //plugin.getLogger().info("Item is null or not an ender pearl: " + (item == null ? "null" : item.getType()));
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            //plugin.getLogger().info("Action is not right-click: " + event.getAction());
            return;
        }

        // Check if the right-click is on an interactable block
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && isInteractableBlock(block.getType())) {
                //plugin.getLogger().info("Right-click on interactable block: " + block.getType() + ", allowing normal interaction");
                return; // Allow normal block interaction (e.g., opening chest)
            }
        }

        // Check for cooldown
        if (player.hasCooldown(Material.ENDER_PEARL)) {
            //plugin.getLogger().info("Player " + player.getName() + " is on ender pearl cooldown");
            event.setCancelled(true);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            //plugin.getLogger().info("Item has no metadata");
            return;
        }

        Integer sbitem = null;
        if (meta.getPersistentDataContainer().has(sbitemKey, PersistentDataType.INTEGER)) {
            sbitem = meta.getPersistentDataContainer().get(sbitemKey, PersistentDataType.INTEGER);
            //plugin.getLogger().info("Found sbitem tag with value: " + sbitem);
        } else {
            //plugin.getLogger().info("No sbitem tag found in PersistentDataContainer");
            if (meta.hasCustomModelData()) {
                int customModelData = meta.getCustomModelData();
                //plugin.getLogger().info("CustomModelData found: " + customModelData);
                switch (customModelData) {
                    case 1160001:
                        sbitem = 1; // Levitation
                        break;
                    case 1160002:
                        sbitem = 2; // Speed (3)
                        break;
                    case 1160020:
                        sbitem = 20; // Speed (20)
                        break;
                    case 1160003:
                        sbitem = 3; // Regeneration
                        break;
                    case 1160004:
                        sbitem = 4; // Jump Boost
                        break;
                }
            }
        }

        if (sbitem == null) {
            //plugin.getLogger().info("No valid sbitem identifier found");
            return;
        }

        PotionEffect effect = null;
        String effectName = "";
        switch (sbitem) {
            case 1:
                effect = new PotionEffect(PotionEffectType.LEVITATION, 3 * 20, 4); // 3 seconds, amplifier 4 (level 5)
                effectName = "Levitation";
                //plugin.getLogger().info("Applying Levitation effect");
                break;
            case 2:
                effect = new PotionEffect(PotionEffectType.SPEED, 3 * 20, 3); // 3 seconds, amplifier 3 (level 4)
                effectName = "Speed (3)";
                //plugin.getLogger().info("Applying Speed (3) effect");
                break;
            case 20:
                effect = new PotionEffect(PotionEffectType.SPEED, 3 * 20, 20); // 3 seconds, amplifier 20 (level 21)
                effectName = "Speed (20)";
                //plugin.getLogger().info("Applying Speed (20) effect");
                break;
            case 3:
                effect = new PotionEffect(PotionEffectType.REGENERATION, 4 * 20, 2); // 4 seconds, amplifier 2 (level 3)
                effectName = "Regeneration";
                //plugin.getLogger().info("Applying Regeneration effect");
                break;
            case 4:
                effect = new PotionEffect(PotionEffectType.JUMP_BOOST, 7 * 20, 6); // 7 seconds, amplifier 6 (level 7)
                effectName = "Jump Boost";
                //plugin.getLogger().info("Applying Jump Boost effect");
                break;
            default:
                //plugin.getLogger().info("Unknown sbitem value: " + sbitem);
                return;
        }

        // Apply the effect
        player.addPotionEffect(effect);
        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_THROWN, 1.0f, 0.5f);
        //player.sendMessage(Component.text("Applied " + effectName + " effect (sbitem: " + sbitem + ")"));

        // Consume the item
        item.setAmount(item.getAmount() - 1);
        //plugin.getLogger().info("Consumed pearl, new amount: " + item.getAmount());

        // Apply vanilla ender pearl cooldown (20 ticks = 1 second)
        player.setCooldown(Material.ENDER_PEARL, 20);
        //plugin.getLogger().info("Applied ender pearl cooldown to " + player.getName());

        // Cancel the event to prevent the pearl from being thrown
        event.setCancelled(true);
        //plugin.getLogger().info("Event cancelled to prevent pearl throw");
    }

    // Check if a block is interactable (e.g., chest, crafting table)
    private boolean isInteractableBlock(Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case CRAFTING_TABLE:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case ANVIL:
            case CHIPPED_ANVIL:
            case DAMAGED_ANVIL:
            case ENCHANTING_TABLE:
            case BREWING_STAND:
            case BARREL:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case LECTERN:
            case LOOM:
            case CARTOGRAPHY_TABLE:
            case GRINDSTONE:
            case SMITHING_TABLE:
            case ENDER_CHEST:
            case SHULKER_BOX:
            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
                return true;
            default:
                return material.isInteractable(); // Fallback to Bukkit's isInteractable for other blocks
        }
    }

    // Utility method to create a test pearl
    public static ItemStack createTestPearl(JavaPlugin plugin, int sbitem, String displayName, int customModelData) {
        ItemStack pearl = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = pearl.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "sbitem"), PersistentDataType.INTEGER, sbitem);
        meta.setCustomModelData(customModelData);
        meta.displayName(Component.text(displayName));
        pearl.setItemMeta(meta);
        return pearl;
    }
}