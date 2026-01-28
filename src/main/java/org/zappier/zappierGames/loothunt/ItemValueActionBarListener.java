package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.zappier.zappierGames.ZappierGames;

import java.util.HashMap;
import java.util.Map;

public class ItemValueActionBarListener implements Listener {

    // Track last held item per player to avoid duplicate messages
    public static final Map<String, Material> lastHeldItem = new HashMap<>();

    @EventHandler
    public static void onItemHeld(PlayerItemHeldEvent event) {
        // Only show during Loot Hunt game
        if (ZappierGames.gameMode != ZappierGames.LOOTHUNT) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        // Clear if switching to empty slot
        if (newItem == null || newItem.getType() == Material.AIR || newItem.getType().toString().toLowerCase().contains("bundle")) {
            lastHeldItem.remove(player.getName());
            if (newItem != null && newItem.getType() != Material.AIR) {
                int pInfData = LootHunt.bundleSlots.getOrDefault(player.getName().toUpperCase(), 0);
                if (pInfData == 0) {
                    LootHunt.bundleSlots.put(player.getName().toUpperCase(), 0b111);
                    pInfData = 0b111;
                }
                LootHunt.bundleSlots.put(player.getName().toUpperCase(), pInfData);
                //□■
                String shiftL[] = {"□", "□", "□"};
                if ((pInfData & 0b001) > 0) {shiftL[0] = "■";}
                if ((pInfData & 0b010) > 0) {shiftL[1] = "■";}
                if ((pInfData & 0b100) > 0) {shiftL[2] = "■";}
                player.sendActionBar(ChatColor.GREEN + "SHIFT-L Slots: " + shiftL[0] + shiftL[1] + shiftL[2]);
            }
            return;
        }

        // Prevent spam for same item type
        Material newMaterial = newItem.getType();
        Material lastMaterial = lastHeldItem.get(player.getName());
        if (newMaterial.equals(lastMaterial)) {
            return;
        }
        lastHeldItem.put(player.getName(), newMaterial);

        String itemId = newItem.getType().toString();
        String itemName = formatItemName(newItem);

        // Determine collection status
        boolean isCollectionItem = LootHunt.collections.values().stream()
                .anyMatch(c -> c.itemGroups.stream()
                        .anyMatch(group -> group.contains(itemId)));

        Component star = Component.text(
                isCollectionItem ? "⭐ " : "☆ ",
                isCollectionItem ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY
        );

        double baseValue = getBaseItemValue(newItem);
        double enchantValue = getEnchantmentValue(newItem);
        double totalPerItem = baseValue + enchantValue;

        // Zero-value case
        if (totalPerItem <= 0) {
            Component message = star
                    .append(Component.text(itemName, NamedTextColor.GRAY))
                    .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("0 pts", NamedTextColor.RED));

            player.sendActionBar(message);
            return;
        }

        double totalValue = totalPerItem * newItem.getAmount();

        Component message = star
                .append(Component.text(itemName, NamedTextColor.YELLOW));

        // Enchantment contribution on the left
        if (enchantValue > 0) {
            message = message.append(Component.text("  📖 ", NamedTextColor.DARK_PURPLE))
                    .append(Component.text("+" + String.format("%.1f", enchantValue),
                            NamedTextColor.LIGHT_PURPLE));
        }

        message = message.append(Component.text(" : ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f", totalPerItem),
                                NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(" pts/item", NamedTextColor.GRAY));

        if (newItem.getAmount() > 1) {
            message = message.append(Component.text(" (", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.format("%.1f", totalValue),
                            NamedTextColor.AQUA))
                    .append(Component.text(" total)", NamedTextColor.DARK_GRAY));
        }

        player.sendActionBar(message);
    }

    private static double getBaseItemValue(ItemStack item) {
        String itemId = item.getType().toString();
        double baseValue = LootHunt.itemValues.getOrDefault(itemId, 0.0);

        // Handle potions
        if (item.getType() == Material.POTION ||
                item.getType() == Material.SPLASH_POTION ||
                item.getType() == Material.LINGERING_POTION) {

            if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                PotionType pt = potionMeta.getBasePotionType();
                String prefix =
                        item.getType() == Material.SPLASH_POTION ? "SPLASH_" :
                                item.getType() == Material.LINGERING_POTION ? "LINGERING_" : "";
                String key = prefix + (pt != null ? pt.name() : "WATER");
                baseValue = LootHunt.potionValues.getOrDefault(key, 0.0);
            }
        }

        // Tiny default for collection items
        boolean isCollectionItem = LootHunt.collections.values().stream()
                .anyMatch(c -> c.itemGroups.stream()
                        .anyMatch(group -> group.contains(itemId)));
        if (isCollectionItem && baseValue == 0.0) {
            baseValue = 0.001;
        }

        return baseValue;
    }

    private static double getEnchantmentValue(ItemStack item) {
        if (!item.hasItemMeta()) {
            return 0.0;
        }
        return LootHunt.getTotalEnchantmentPoints(item);
    }

    private static String formatItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName().toString();
        }

        String name = item.getType().toString();
        String[] parts = name.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();

        for (String part : parts) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }

        return formatted.toString();
    }

    /**
     * Call this when the game ends to clear tracking
     */
    public static void clearTracking() {
        lastHeldItem.clear();
    }
}
