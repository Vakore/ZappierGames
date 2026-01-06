package org.zappier.zappierGames.loothunt;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

        // Check for special categories
        boolean isDoor = LootHunt.doorNames != null && Arrays.asList(LootHunt.doorNames).contains(itemName);
        boolean isWorkstation = LootHunt.workStationNames != null && Arrays.asList(LootHunt.workStationNames).contains(itemName);
        boolean isDye = LootHunt.dyeNames != null && Arrays.asList(LootHunt.dyeNames).contains(itemName);
        boolean isTool = itemName.equals("STONE_SWORD") || itemName.equals("STONE_AXE") ||
                itemName.equals("STONE_PICKAXE") || itemName.equals("STONE_SHOVEL") ||
                itemName.equals("STONE_HOE");
        boolean isCraftedArmorTool = isCraftedArmorOrTool(itemName);
        boolean isMusicDisc = itemName.startsWith("MUSIC_DISC_");

        // Build the message based on item value and category
        if (value <= 0 && !isDoor && !isWorkstation && !isDye && !isTool && !isCraftedArmorTool && !isMusicDisc) {
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
                message.append(value > 0 || isDoor || isWorkstation || isDye || isCraftedArmorTool || isMusicDisc ? " + " : " ")
                        .append(String.format("%.1f", enchantPoints))
                        .append(" for enchantments");
            }
        }

        // Add special category information
        if (isDoor) {
            message.append(value > 0 || isWorkstation || isDye || isCraftedArmorTool || isMusicDisc ? " and " : " ")
                    .append("counts toward the door collection (");
            if (LootHunt.doorScores != null) {
                message.append("1st door: 1, 2nd: 5, 3rd: 10, ..., up to 1000 points");
            } else {
                message.append("config missing");
            }
            message.append(")");
        }
        if (isWorkstation) {
            message.append(value > 0 || isDoor || isDye || isCraftedArmorTool || isMusicDisc ? " and " : " ")
                    .append("counts toward the workstation collection (");
            if (LootHunt.workStationNames != null) {
                message.append("250-point bonus for all ").append(LootHunt.workStationNames.length).append(" types");
            } else {
                message.append("config missing");
            }
            message.append(")");
        }
        if (isDye) {
            message.append(value > 0 || isDoor || isWorkstation || isCraftedArmorTool || isMusicDisc ? " and " : " ")
                    .append("counts toward the dye collection (");
            if (LootHunt.dyeNames != null) {
                message.append("300-point bonus for all ").append(LootHunt.dyeNames.length).append(" types");
            } else {
                message.append("config missing");
            }
            message.append(")");
        }
        if (isTool) {
            message.append(value > 0 || isDoor || isWorkstation || isDye || isCraftedArmorTool || isMusicDisc ? " and " : " ")
                    .append("is a starting tool with no special scoring unless enchanted");
        }
        if (isCraftedArmorTool) {
            message.append(value > 0 || isDoor || isWorkstation || isDye || isTool || isMusicDisc ? " and " : " ")
                    .append("has points based on its base material crafting cost");
        }
        if (isMusicDisc) {
            message.append(value > 0 || isDoor || isWorkstation || isDye || isTool || isCraftedArmorTool ? " and " : " ")
                    .append("is a music disc (values: Cat/13: 15, Pigstep: 30, Otherside: 50, 5/Relic: 100, others: 50)");
        }

        message.append(".");
        player.sendMessage(message.toString());

        // Brief description of scoring rules
        player.sendMessage(ChatColor.GRAY + "Scoring: Items have base values (halved if damaged). Enchantments add 4 points per tier (8 for Swift Sneak, Mending, Frost Walker). " +
                "Doors add points per unique type (1, 5, 10, ..., 1000). Workstations grant 250 points for all types. Dyes grant 300 points for all 16 types. " +
                "Crafted armor/tools score based on material cost. Kills add 50 points, deaths subtract 25, reducing per occurrence. Starting tools have no value unless enchanted.");
        return true;
    }

    private boolean isCraftedArmorOrTool(String itemName) {
        // Check for armor, tools, weapons, clock, compass (excluding chain armor, which has explicit values)
        return itemName.endsWith("_HELMET") || itemName.endsWith("_CHESTPLATE") ||
                itemName.endsWith("_LEGGINGS") || itemName.endsWith("_BOOTS") ||
                itemName.endsWith("_SWORD") || itemName.endsWith("_AXE") ||
                itemName.endsWith("_PICKAXE") || itemName.endsWith("_SHOVEL") ||
                itemName.endsWith("_HOE") || itemName.equals("CLOCK") || itemName.equals("COMPASS") ||
                itemName.equals("BOW") || itemName.equals("CROSSBOW") || itemName.equals("FISHING_ROD") ||
                itemName.equals("SHEARS") || itemName.equals("FLINT_AND_STEEL") ||
                !itemName.equals("CHAIN_HELMET") && !itemName.equals("CHAIN_CHESTPLATE") &&
                        !itemName.equals("CHAIN_LEGGINGS") && !itemName.equals("CHAIN_BOOTS") &&
                        !itemName.equals("STONE_SWORD") && !itemName.equals("STONE_AXE") &&
                        !itemName.equals("STONE_PICKAXE") && !itemName.equals("STONE_SHOVEL") &&
                        !itemName.equals("STONE_HOE");
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