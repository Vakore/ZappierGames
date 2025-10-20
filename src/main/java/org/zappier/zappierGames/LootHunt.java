package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LootHunt {
    public static double startTimer;
    public static String[] doorNames;
    public static int[] doorScores;
    public static String[] workStationNames;
    public static String[] dyeNames;
    public static Map<String, Double> itemValues = new HashMap<>();
    public static Map<String, Map<String, Double>> teamItemCounts = new HashMap<>();
    public static Map<String, Integer> playerKillCounts = new HashMap<>();
    public static Map<String, Integer> playerDeathCounts = new HashMap<>();
    private static int workstationBonus;
    private static int dyeBonus;
    private static int baseKillPoints;
    private static int baseDeathPoints;
    private static int pointsReductionFactor;
    private static int enchantmentPointsPerTier;
    private static Map<String, Integer> specialEnchantments = new HashMap<>();
    private static List<Map<String, Object>> customPearls = new ArrayList<>();
    private static Material[] shulkerColors;

    public static void loadConfig(FileConfiguration config) {
        ZappierGames plugin = ZappierGames.getInstance();

        // Load start timer
        startTimer = config.getDouble("start-timer", 240.0);

        // Load door names and scores
        List<String> doorNamesList = config.getStringList("door-names");
        if (doorNamesList.isEmpty()) {
            doorNamesList = List.of(
                    "OAK_DOOR", "SPRUCE_DOOR", "BIRCH_DOOR", "JUNGLE_DOOR", "ACACIA_DOOR",
                    "DARK_OAK_DOOR", "CRIMSON_DOOR", "WARPED_DOOR", "MANGROVE_DOOR",
                    "BAMBOO_DOOR", "IRON_DOOR", "CHERRY_DOOR", "COPPER_DOOR"
            );
            plugin.getLogger().warning("door-names not found in config.yml, using default values");
        }
        doorNames = doorNamesList.toArray(new String[0]);

        List<Integer> doorScoresList = config.getIntegerList("door-scores");
        if (doorScoresList.isEmpty()) {
            doorScoresList = List.of(1, 5, 10, 15, 25, 50, 100, 150, 250, 400, 700, 1000);
            plugin.getLogger().warning("door-scores not found in config.yml, using default values");
        }
        doorScores = doorScoresList.stream().mapToInt(Integer::intValue).toArray();

        // Load workstation names
        List<String> workStationNamesList = config.getStringList("workstation-names");
        if (workStationNamesList.isEmpty()) {
            workStationNamesList = List.of(
                    "BLAST_FURNACE", "SMOKER", "CARTOGRAPHY_TABLE", "BREWING_STAND",
                    "COMPOSTER", "BARREL", "FLETCHING_TABLE", "CAULDRON", "LECTERN",
                    "STONECUTTER", "LOOM", "SMITHING_TABLE", "GRINDSTONE"
            );
            plugin.getLogger().warning("workstation-names not found in config.yml, using default values");
        }
        workStationNames = workStationNamesList.toArray(new String[0]);

        // Load dye names
        List<String> dyeNamesList = config.getStringList("dye-names");
        if (dyeNamesList.isEmpty()) {
            dyeNamesList = List.of(
                    "WHITE_DYE", "LIGHT_GRAY_DYE", "GRAY_DYE", "BLACK_DYE", "BROWN_DYE",
                    "RED_DYE", "ORANGE_DYE", "YELLOW_DYE", "LIME_DYE", "GREEN_DYE",
                    "CYAN_DYE", "LIGHT_BLUE_DYE", "BLUE_DYE", "PURPLE_DYE", "MAGENTA_DYE",
                    "PINK_DYE"
            );
            plugin.getLogger().warning("dye-names not found in config.yml, using default values");
        }
        dyeNames = dyeNamesList.toArray(new String[0]);

        // Load shulker colors with default if not present
        List<String> shulkerColorNames = config.getStringList("shulker-colors");
        if (shulkerColorNames.isEmpty()) {
            shulkerColorNames = List.of(
                    "BLUE_SHULKER_BOX", "RED_SHULKER_BOX", "GREEN_SHULKER_BOX",
                    "YELLOW_SHULKER_BOX", "BLACK_SHULKER_BOX"
            );
            plugin.getLogger().warning("shulker-colors not found in config.yml, using default values");
        }
        List<Material> validShulkerColors = new ArrayList<>();
        for (String name : shulkerColorNames) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                validShulkerColors.add(material);
            } else {
                plugin.getLogger().warning("Invalid material in shulker-colors: " + name);
            }
        }
        shulkerColors = validShulkerColors.toArray(new Material[0]);

        // Load item values
        ConfigurationSection itemSection = config.getConfigurationSection("item-values");
        if (itemSection != null) {
            itemValues.clear();
            for (String key : itemSection.getKeys(false)) {
                itemValues.put(key, itemSection.getDouble(key));
            }
        } else {
            plugin.getLogger().warning("item-values not found in config.yml, no item scoring available");
        }

        // Load collection bonuses
        ConfigurationSection collectionSection = config.getConfigurationSection("collection-bonuses");
        if (collectionSection != null) {
            workstationBonus = collectionSection.getInt("workstations", 250);
            dyeBonus = collectionSection.getInt("dyes", 300);
        } else {
            workstationBonus = 250;
            dyeBonus = 300;
            plugin.getLogger().warning("collection-bonuses not found in config.yml, using defaults");
        }

        // Load PVP settings
        ConfigurationSection pvpSection = config.getConfigurationSection("pvp");
        if (pvpSection != null) {
            baseKillPoints = pvpSection.getInt("base-kill-points", 50);
            baseDeathPoints = pvpSection.getInt("base-death-points", 25);
            pointsReductionFactor = pvpSection.getInt("points-reduction-factor", 2);
        } else {
            baseKillPoints = 50;
            baseDeathPoints = 25;
            pointsReductionFactor = 2;
            plugin.getLogger().warning("pvp settings not found in config.yml, using defaults");
        }

        // Load enchantment settings
        ConfigurationSection enchantSection = config.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            enchantmentPointsPerTier = enchantSection.getInt("points-per-tier", 4);
            ConfigurationSection specialSection = enchantSection.getConfigurationSection("special-enchantments");
            if (specialSection != null) {
                specialEnchantments.clear();
                for (String key : specialSection.getKeys(false)) {
                    specialEnchantments.put(key, specialSection.getInt(key));
                }
            }
        } else {
            enchantmentPointsPerTier = 4;
            specialEnchantments.put("SWIFT_SNEAK", 8);
            specialEnchantments.put("MENDING", 8);
            specialEnchantments.put("FROST_WALKER", 8);
            plugin.getLogger().warning("enchantments not found in config.yml, using defaults");
        }

        // Load custom pearls
        customPearls.clear();
        List<Map<?, ?>> pearlList = config.getMapList("custom-pearls");
        if (pearlList.isEmpty()) {
            plugin.getLogger().warning("custom-pearls not found in config.yml, no custom pearls will be given");
        } else {
            for (Map<?, ?> pearl : pearlList) {
                Map<String, Object> pearlData = new HashMap<>();
                pearlData.put("sbitem", pearl.get("sbitem"));
                pearlData.put("custom-model-data", pearl.get("custom-model-data"));
                pearlData.put("display-name", pearl.get("display-name"));
                pearlData.put("amount", pearl.get("amount"));
                customPearls.add(pearlData);
            }
        }
    }

    public static void start(double duration) {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Keep inventory set to " + true + " across all dimensions");
        teamItemCounts.clear();
        playerKillCounts.clear();
        playerDeathCounts.clear();
        startTimer = duration * 60 * 20; // Convert minutes to ticks (20 ticks per second)
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.globalBossBar.setVisible(true);
        ZappierGames.globalBossBar.setStyle(BarStyle.SOLID);
        ZappierGames.globalBossBar.setColor(BarColor.YELLOW);
        ZappierGames.globalBossBar.setProgress(1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            ZappierGames.globalBossBar.addPlayer(p);
            p.getInventory().clear();
            giveStartingItems(p);
            p.sendTitle(ChatColor.GREEN + "Loot Hunt", ChatColor.GREEN + "Collect items, score points!");
            p.sendActionBar(ChatColor.GREEN + "Use /getscore <item> to find how much it's worth!");
            p.sendMessage(ChatColor.GREEN + "Use /getscore <item> to find how much it's worth!");
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExperienceLevelAndProgress(0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
        ZappierGames.gameMode = ZappierGames.LOOTHUNT;

        ZappierGames.timer = (int) Math.ceil(startTimer); // Initialize timer as ceiling of startTimer
    }

    public static void endGame() {
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.gameMode = -1;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.YELLOW + "Game Finished!", ChatColor.YELLOW + "");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            Map<String, Double> inventoryCounts = calculateInventoryCounts(p);

            String scoreDetails = inventoryCounts.entrySet().stream()
                    .map(e -> e.getKey() + ": " + String.format("%.1f", e.getValue()))
                    .collect(Collectors.joining(", "));
            //Bukkit.broadcastMessage(ChatColor.YELLOW + p.getName() + " - " + scoreDetails);

            String teamName = p.getScoreboard().getEntryTeam(p.getName()) != null
                    ? p.getScoreboard().getEntryTeam(p.getName()).getName()
                    : "(Solo) " + p.getName();

            Map<String, Double> disTeam = teamItemCounts.get(teamName);
            if (disTeam == null) {
                teamItemCounts.put(teamName, new HashMap<>());
                disTeam = teamItemCounts.get(teamName);
            }

            for (Map.Entry<String, Double> e : inventoryCounts.entrySet()) {
                disTeam.put(e.getKey(), disTeam.getOrDefault(e.getKey(), 0.0) + e.getValue());
            }

            p.getInventory().clear();
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");
        Bukkit.broadcastMessage(ChatColor.GREEN + "        RESULTS        ");
        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");

        for (Map.Entry<String, Map<String, Double>> d : teamItemCounts.entrySet()) {
            double disScore = 0;
            int doorsCount = 0;
            int workStationCount = 0;
            int dyeCount = 0;
            for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                String itemName = e.getKey();
                disScore += e.getValue();
                if (doorNames != null) {
                    for (String doorName : doorNames) {
                        if (doorName != null && doorName.equals(itemName)) {
                            doorsCount++;
                            break;
                        }
                    }
                }
                if (workStationNames != null) {
                    for (String workStationName : workStationNames) {
                        if (workStationName != null && workStationName.equals(itemName)) {
                            workStationCount++;
                            break;
                        }
                    }
                }
                if (dyeNames != null) {
                    for (String dyeName : dyeNames) {
                        if (dyeName != null && dyeName.equals(itemName)) {
                            dyeCount++;
                            break;
                        }
                    }
                }
            }

            if (workStationNames != null && workStationCount >= workStationNames.length) {
                disScore += workstationBonus;
            }

            if (dyeNames != null && dyeCount >= dyeNames.length) {
                disScore += dyeBonus;
            }

            if (doorScores != null && doorNames != null && doorsCount > 0) {
                if (doorsCount <= doorScores.length) {
                    disScore += doorScores[doorsCount - 1];
                } else {
                    disScore += doorScores[doorScores.length - 1];
                }
            }

            for (Map.Entry<String, Integer> e : playerKillCounts.entrySet()) {
                String killerName = e.getKey().toUpperCase();
                String killerTeam = Bukkit.getPlayer(killerName) != null && Bukkit.getPlayer(killerName).getScoreboard().getEntryTeam(killerName) != null
                        ? Bukkit.getPlayer(killerName).getScoreboard().getEntryTeam(killerName).getName()
                        : "(Solo) " + killerName;
                if (killerTeam.equals(d.getKey())) {
                    int killCount = e.getValue();
                    int oldKillCount = killCount;
                    int killValue = baseKillPoints;
                    int addScore = 0;

                    while (killCount > 0 && killValue > 1) {
                        addScore += killValue;
                        killCount--;
                        killValue /= pointsReductionFactor;
                    }
                    Bukkit.broadcastMessage(killerName + " got " + oldKillCount + " kills, earning " + addScore + " points for team " + d.getKey());
                    disScore += addScore;
                }
            }

            for (Map.Entry<String, Integer> e : playerDeathCounts.entrySet()) {
                String killedName = e.getKey().toUpperCase();
                String killedTeam = Bukkit.getPlayer(killedName) != null && Bukkit.getPlayer(killedName).getScoreboard().getEntryTeam(killedName) != null
                        ? Bukkit.getPlayer(killedName).getScoreboard().getEntryTeam(killedName).getName()
                        : "(Solo) " + killedName;
                if (killedTeam.equals(d.getKey())) {
                    int deathCount = e.getValue();
                    int oldDeathCount = deathCount;
                    int deathValue = baseDeathPoints;
                    int addScore = 0;

                    while (deathCount > 0 && deathValue > 1) {
                        addScore += deathValue;
                        deathCount--;
                        deathValue /= pointsReductionFactor;
                    }
                    Bukkit.broadcastMessage(killedName + " died " + oldDeathCount + " times, losing " + addScore + " points for team " + d.getKey());
                    disScore -= addScore;
                }
            }

            Bukkit.broadcastMessage(ChatColor.YELLOW + d.getKey() + ": " + String.format("%.1f", disScore));
            if (doorNames != null) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "  Doors: " + doorsCount + "/" + doorNames.length);
            } else {
                Bukkit.broadcastMessage(ChatColor.GRAY + "  Doors: " + doorsCount + "/0 (config missing)");
            }
            if (workStationNames != null) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "  Workstations: " + workStationCount + "/" + workStationNames.length);
            } else {
                Bukkit.broadcastMessage(ChatColor.GRAY + "  Workstations: " + workStationCount + "/0 (config missing)");
            }
            if (dyeNames != null) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "  Dyes: " + dyeCount + "/" + dyeNames.length);
            } else {
                Bukkit.broadcastMessage(ChatColor.GRAY + "  Dyes: " + dyeCount + "/0 (config missing)");
            }
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");

        teamItemCounts.clear();
    }

    public static Map<String, Double> calculateInventoryCounts(Player player) {
        Map<String, Double> scoreMap = new HashMap<>();

        for (ItemStack item : player.getInventory()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String itemId = item.getType().toString();
            double itemValue = getItemValue(itemId);
            int amount = item.getAmount();

            // Handle crafted armor, tools, weapons, clocks, and compasses
            if (isCraftedArmorOrTool(itemId)) {
                itemValue = getCraftingCost(itemId);
            }

            // Handle damaged items (half points for base value)
            if (item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
                itemValue /= 2.0;
            }

            // Handle enchantments
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                itemValue += getTotalEnchantmentPoints(item);
            }

            // Store total points for this item type
            scoreMap.put(itemId, scoreMap.getOrDefault(itemId, 0.0) + (itemValue * amount));

            if (item.getType().name().endsWith("SHULKER_BOX")) {
                player.sendMessage("Shulker box found");
                if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta meta) {
                    BlockState state = meta.getBlockState();
                    if (state instanceof ShulkerBox shulkerBox) {
                        for (ItemStack sItem : shulkerBox.getInventory().getContents()) {
                            if (sItem == null || sItem.getType() == Material.AIR) {
                                continue;
                            }
                            String sItemId = sItem.getType().toString();
                            double sItemValue = getItemValue(sItemId);
                            int sAmount = sItem.getAmount();

                            // Handle crafted armor, tools, weapons, clocks, and compasses in shulker box
                            if (isCraftedArmorOrTool(sItemId)) {
                                sItemValue = getCraftingCost(sItemId);
                            }

                            // Handle damaged items in shulker box
                            if (sItem.getItemMeta() instanceof Damageable sDamageable && sDamageable.hasDamage()) {
                                sItemValue /= 2.0;
                            }

                            // Handle enchantments in shulker box
                            if (sItem.hasItemMeta() && sItem.getItemMeta().hasEnchants()) {
                                sItemValue += getTotalEnchantmentPoints(sItem);
                            }

                            // Store total points for this item type
                            scoreMap.put(sItemId, scoreMap.getOrDefault(sItemId, 0.0) + (sItemValue * sAmount));
                        }
                    }
                }
            }
        }

        return scoreMap;
    }

    private static boolean isCraftedArmorOrTool(String itemId) {
        return (itemId.endsWith("_HELMET") || itemId.endsWith("_CHESTPLATE") ||
                itemId.endsWith("_LEGGINGS") || itemId.endsWith("_BOOTS") ||
                itemId.endsWith("_SWORD") || itemId.endsWith("_AXE") ||
                itemId.endsWith("_PICKAXE") || itemId.endsWith("_SHOVEL") ||
                itemId.endsWith("_HOE") || itemId.equals("CLOCK") || itemId.equals("COMPASS") ||
                itemId.equals("BOW") || itemId.equals("CROSSBOW") || itemId.equals("FISHING_ROD") ||
                itemId.equals("SHEARS") || itemId.equals("FLINT_AND_STEEL")) &&
                !itemId.equals("CHAIN_HELMET") && !itemId.equals("CHAIN_CHESTPLATE") &&
                !itemId.equals("CHAIN_LEGGINGS") && !itemId.equals("CHAIN_BOOTS") &&
                !itemId.equals("STONE_SWORD") && !itemId.equals("STONE_AXE") &&
                !itemId.equals("STONE_PICKAXE") && !itemId.equals("STONE_SHOVEL") &&
                !itemId.equals("STONE_HOE");
    }

    private static double getCraftingCost(String itemId) {
        // Define material costs based on Minecraft 1.21.3 crafting recipes
        Map<String, Integer> materialCosts = new HashMap<>();
        // Armor
        materialCosts.put("LEATHER_HELMET", 5); // 5 leather
        materialCosts.put("LEATHER_CHESTPLATE", 8); // 8 leather
        materialCosts.put("LEATHER_LEGGINGS", 7); // 7 leather
        materialCosts.put("LEATHER_BOOTS", 4); // 4 leather
        materialCosts.put("IRON_HELMET", 5); // 5 iron ingots
        materialCosts.put("IRON_CHESTPLATE", 8); // 8 iron ingots
        materialCosts.put("IRON_LEGGINGS", 7); // 7 iron ingots
        materialCosts.put("IRON_BOOTS", 4); // 4 iron ingots
        materialCosts.put("GOLDEN_HELMET", 5); // 5 gold ingots
        materialCosts.put("GOLDEN_CHESTPLATE", 8); // 8 gold ingots
        materialCosts.put("GOLDEN_LEGGINGS", 7); // 7 gold ingots
        materialCosts.put("GOLDEN_BOOTS", 4); // 4 gold ingots
        materialCosts.put("DIAMOND_HELMET", 5); // 5 diamonds
        materialCosts.put("DIAMOND_CHESTPLATE", 8); // 8 diamonds
        materialCosts.put("DIAMOND_LEGGINGS", 7); // 7 diamonds
        materialCosts.put("DIAMOND_BOOTS", 4); // 4 diamonds
        materialCosts.put("NETHERITE_HELMET", 4); // 4 netherite ingots (from smithing)
        materialCosts.put("NETHERITE_CHESTPLATE", 4); // 4 netherite ingots
        materialCosts.put("NETHERITE_LEGGINGS", 4); // 4 netherite ingots
        materialCosts.put("NETHERITE_BOOTS", 4); // 4 netherite ingots
        // Tools and Weapons
        materialCosts.put("WOODEN_SWORD", 2); // 2 planks
        materialCosts.put("WOODEN_AXE", 3); // 3 planks
        materialCosts.put("WOODEN_PICKAXE", 3); // 3 planks
        materialCosts.put("WOODEN_SHOVEL", 1); // 1 plank
        materialCosts.put("WOODEN_HOE", 2); // 2 planks
        materialCosts.put("IRON_SWORD", 2); // 2 iron ingots
        materialCosts.put("IRON_AXE", 3); // 3 iron ingots
        materialCosts.put("IRON_PICKAXE", 3); // 3 iron ingots
        materialCosts.put("IRON_SHOVEL", 1); // 1 iron ingot
        materialCosts.put("IRON_HOE", 2); // 2 iron ingots
        materialCosts.put("GOLDEN_SWORD", 2); // 2 gold ingots
        materialCosts.put("GOLDEN_AXE", 3); // 3 gold ingots
        materialCosts.put("GOLDEN_PICKAXE", 3); // 3 gold ingots
        materialCosts.put("GOLDEN_SHOVEL", 1); // 1 gold ingot
        materialCosts.put("GOLDEN_HOE", 2); // 2 gold ingots
        materialCosts.put("DIAMOND_SWORD", 2); // 2 diamonds
        materialCosts.put("DIAMOND_AXE", 3); // 3 diamonds
        materialCosts.put("DIAMOND_PICKAXE", 3); // 3 diamonds
        materialCosts.put("DIAMOND_SHOVEL", 1); // 1 diamond
        materialCosts.put("DIAMOND_HOE", 2); // 2 diamonds
        materialCosts.put("NETHERITE_SWORD", 4); // 4 netherite ingots (from smithing)
        materialCosts.put("NETHERITE_AXE", 4); // 4 netherite ingots
        materialCosts.put("NETHERITE_PICKAXE", 4); // 4 netherite ingots
        materialCosts.put("NETHERITE_SHOVEL", 4); // 4 netherite ingots
        materialCosts.put("NETHERITE_HOE", 4); // 4 netherite ingots
        materialCosts.put("BOW", 3); // 3 sticks (no clear material value, approximate)
        materialCosts.put("CROSSBOW", 3); // 3 sticks + 2 iron ingots (approximate)
        materialCosts.put("FISHING_ROD", 2); // 3 sticks (approximate)
        materialCosts.put("SHEARS", 2); // 2 iron ingots
        materialCosts.put("FLINT_AND_STEEL", 1); // 1 iron ingot
        materialCosts.put("CLOCK", 4); // 4 gold ingots
        materialCosts.put("COMPASS", 4); // 4 iron ingots

        int materialCount = materialCosts.getOrDefault(itemId, 0);
        // Convert material count to points based on material type
        if (itemId.startsWith("LEATHER_")) {
            return materialCount * getItemValue("LEATHER"); // Leather = 2
        } else if (itemId.startsWith("IRON_")) {
            return materialCount * getItemValue("IRON_INGOT"); // Iron ingot = 1
        } else if (itemId.startsWith("GOLDEN_")) {
            return materialCount * getItemValue("GOLD_INGOT"); // Gold ingot = 1
        } else if (itemId.startsWith("DIAMOND_")) {
            return materialCount * getItemValue("DIAMOND"); // Diamond = 10
        } else if (itemId.startsWith("NETHERITE_")) {
            return materialCount * getItemValue("NETHERITE_INGOT"); // Netherite ingot = 100
        } else if (itemId.equals("CLOCK")) {
            return materialCount * getItemValue("GOLD_INGOT"); // Gold ingot = 1
        } else if (itemId.equals("COMPASS")) {
            return materialCount * getItemValue("IRON_INGOT"); // Iron ingot = 1
        } else if (itemId.equals("SHEARS") || itemId.equals("FLINT_AND_STEEL")) {
            return materialCount * getItemValue("IRON_INGOT"); // Iron ingot = 1
        } else if (itemId.equals("BOW") || itemId.equals("CROSSBOW") || itemId.equals("FISHING_ROD")) {
            return materialCount; // Approximate, as sticks have no defined value
        } else if (itemId.startsWith("WOODEN_")) {
            return materialCount; // Approximate, as planks have no defined value
        }
        return 0.0;
    }

    public static void run() {
        if (ZappierGames.timer <= 0) {
            ZappierGames.gameMode = -1;
            LootHunt.endGame();
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            ZappierGames.globalBossBar.addPlayer(p);
        }

        // Calculate time from ticks
        double secondsTotal = ZappierGames.timer / 20.0;
        int hours = (int) (secondsTotal / 3600);
        int minutes = (int) ((secondsTotal % 3600) / 60);
        int seconds = (int) (secondsTotal % 60);

        ZappierGames.globalBossBar.setTitle(String.format("Time Left: %02d:%02d:%02d",
                hours, minutes, seconds));

        ZappierGames.globalBossBar.setProgress(ZappierGames.timer / startTimer);

        ZappierGames.timer--; // Decrement timer (integer, but startTimer is double)
    }

    public static void giveStartingItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_SHOVEL));
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE));

        // Add shulker boxes only if shulkerColors is not empty
        if (shulkerColors != null && shulkerColors.length > 0) {
            int pos = 8;
            for (Material shulker : shulkerColors) {
                player.getInventory().setItem(pos++, new ItemStack(shulker));
            }
        } else {
            ZappierGames.getInstance().getLogger().warning("No shulker boxes given to " + player.getName() + ": shulkerColors is empty or null");
        }

        // Add custom ender pearls
        /*for (Map<String, Object> pearl : customPearls) {
            int sbitem = ((Number) pearl.get("sbitem")).intValue();
            int customModelData = ((Number) pearl.get("custom-model-data")).intValue();
            String displayName = (String) pearl.get("display-name");
            int amount = ((Number) pearl.get("amount")).intValue();
            ItemStack pearlItem = CustomPearlsListener.createTestPearl(ZappierGames.getInstance(), sbitem, displayName, customModelData);
            if (pearlItem != null) {
                pearlItem.setAmount(amount);
                player.getInventory().addItem(pearlItem);
            } else {
                ZappierGames.getInstance().getLogger().warning("Failed to create custom pearl: " + displayName);
            }
        }*/
    }

    public static double getItemValue(String itemName) {
        return itemValues.getOrDefault(itemName, 0.0);
    }

    public static double getTotalEnchantmentPoints(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasEnchants()) {
            return 0;
        }
        double points = 0;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            String enchantName = entry.getKey().getKey().getKey().toUpperCase();
            int level = entry.getValue();
            int multiplier = specialEnchantments.getOrDefault(enchantName, enchantmentPointsPerTier);
            points += level * multiplier;
        }
        return points;
    }
}