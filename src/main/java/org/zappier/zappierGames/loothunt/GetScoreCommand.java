package org.zappier.zappierGames.loothunt;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.stream.Collectors;

public class GetScoreCommand implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        ItemStack item;
        String itemName;

        // Check if an argument is provided
        if (args.length == 0) {
            // Get the item in the player's main hand
            item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "You must be holding an item or specify an item name!");
                return true;
            }
            itemName = item.getType().name();
        } else {
            // Validate the provided item name
            itemName = args[0].toUpperCase();
            Material material = Material.getMaterial(itemName);
            if (material == null) {
                player.sendMessage(ChatColor.RED + itemName + " is not a valid item!");
                return true;
            }
            item = new ItemStack(material);
        }

        // Get the base item value
        double value = LootHunt.getItemValue(itemName);
        StringBuilder message = new StringBuilder(ChatColor.GREEN + itemName);

        // Check which collections this item belongs to
        List<LootHunt.Collection> itemCollections = new ArrayList<>();
        for (LootHunt.Collection coll : LootHunt.collections.values()) {
            if (coll.items.contains(itemName)) {
                itemCollections.add(coll);
            }
        }

        // Check for special item types
        boolean isTool = itemName.equals("STONE_SWORD") || itemName.equals("STONE_AXE") ||
                itemName.equals("STONE_PICKAXE") || itemName.equals("STONE_SHOVEL") ||
                itemName.equals("STONE_HOE");

        // Build the message based on item value and category
        if (value <= 0 && itemCollections.isEmpty() && !isTool) {
            player.sendMessage(message.append(" has no value or special scoring.").toString());
            return true;
        }

        if (value > 0) {
            message.append(" is worth ").append(String.format("%.1f", value));
        } else {
            message.append(" has no base value");
        }

        // Check for damaged item (only if checking held item)
        if (args.length == 0 && item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            value /= 2.0;
            message.append(value > 0 ? " (" : " (")
                    .append(String.format("%.1f", value))
                    .append(" when damaged)");
        }

        // Check for enchantments (only if checking held item)
        if (args.length == 0 && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            double enchantPoints = LootHunt.getTotalEnchantmentPoints(item);
            if (enchantPoints > 0) {
                value += enchantPoints;
                message.append(value > 0 || !itemCollections.isEmpty() ? " + " : " ")
                        .append(String.format("%.1f", enchantPoints))
                        .append(" for enchantments");
            }
        }

        // Add collection information
        if (!itemCollections.isEmpty()) {
            for (LootHunt.Collection coll : itemCollections) {
                message.append(value > 0 || itemCollections.indexOf(coll) > 0 ? " and " : " ")
                        .append("counts toward the ").append(coll.name).append(" collection (");

                if ("progressive".equals(coll.type)) {
                    if (!coll.progressiveScores.isEmpty()) {
                        message.append("progressive scores: ");
                        int displayCount = Math.min(3, coll.progressiveScores.size());
                        for (int i = 0; i < displayCount; i++) {
                            if (i > 0) message.append(", ");
                            message.append((i + 1)).append(": ").append(coll.progressiveScores.get(i));
                        }
                        if (coll.progressiveScores.size() > 3) {
                            message.append(", ..., max: ").append(coll.progressiveScores.get(coll.progressiveScores.size() - 1));
                        }
                    } else {
                        message.append("config missing");
                    }
                } else { // complete
                    message.append(coll.completeBonus).append("-point bonus for all ")
                            .append(coll.items.size()).append(" types");
                }
                message.append(")");
            }
        }

        // Add special type information
        if (isTool) {
            message.append(value > 0 || !itemCollections.isEmpty() ? " and " : " ")
                    .append("is a starting tool with no special scoring unless enchanted");
        }

        message.append(".");
        player.sendMessage(message.toString());

        // Brief description of scoring rules
        player.sendMessage(ChatColor.GRAY + "Scoring: Items have base values (halved if damaged). Enchantments add points per tier. " +
                "Collections grant bonuses (progressive or complete-set). " +
                "Kills add points, deaths subtract points, both reducing per occurrence. " +
                "Starting tools have no value unless enchanted.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(Material.values())
                    .map(Material::name)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}