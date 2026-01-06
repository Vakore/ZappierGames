package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.zappier.zappierGames.ZappierGames;

import java.util.*;

public class LootHunt {
    public static double startTimer;
    public static String[] doorNames;
    public static int[] doorScores;
    public static String[] workStationNames;
    public static String[] dyeNames;
    public static String[] bucketNames;
    public static String[] soupNames;
    public static String[] brewingIngredientNames;
    public static int[] brewingIngredientScores;
    private static int bucketBonus;
    private static int soupBonus;
    public static Map<String, Double> itemValues = new HashMap<>();
    public static Map<String, Double> potionValues = new HashMap<>(); // Field for potion values
    public static Map<String, Map<String, List<ItemEntry>>> playerItemCounts = new HashMap<>();
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
    public static boolean paused = false;

    public static class ItemEntry {
        String itemId;
        public int quantity;
        public double points;
        String source; // "Inventory" or "Shulker Box"

        public ItemEntry(String itemId, int quantity, double points, String source) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.points = points;
            this.source = source;
        }

        @Override
        public String toString() {
            return "{itemId=" + itemId + ", quantity=" + quantity + ", points=" + points + ", source=" + source + "}";
        }
    }

    public static void loadConfig(FileConfiguration config) {
        ZappierGames plugin = ZappierGames.getInstance();
        startTimer = config.getDouble("start-timer", 240.0);

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

        List<String> bucketNamesList = config.getStringList("bucket-names");
        if (bucketNamesList.isEmpty()) {
            bucketNamesList = List.of("BUCKET", "WATER_BUCKET", "LAVA_BUCKET", "MILK_BUCKET",
                    "COD_BUCKET", "SALMON_BUCKET", "PUFFERFISH_BUCKET", "TROPICAL_FISH_BUCKET",
                    "AXOLOTL_BUCKET", "POWDER_SNOW_BUCKET");
            plugin.getLogger().warning("bucket-names not found in config.yml, using defaults");
        }
        bucketNames = bucketNamesList.toArray(new String[0]);

        List<String> soupNamesList = config.getStringList("soup-names");
        if (soupNamesList.isEmpty()) {
            soupNamesList = List.of("MUSHROOM_STEW", "RABBIT_STEW", "SUSPICIOUS_STEW", "BEETROOT_SOUP");
            plugin.getLogger().warning("soup-names not found in config.yml, using defaults");
        }
        soupNames = soupNamesList.toArray(new String[0]);

        List<String> brewingNamesList = config.getStringList("brewing-ingredient-names");
        if (brewingNamesList.isEmpty()) {
            brewingNamesList = List.of("REDSTONE", "STONE", "SUGAR", "GUNPOWDER", "SPIDER_EYE",
                    "GOLDEN_CARROT", "BREEZE_ROD", "FERMENTED_SPIDER_EYE", "COBWEB", "GHAST_TEAR",
                    "BLAZE_POWDER", "NETHER_WART", "GLOWSTONE_DUST", "MAGMA_CREAM",
                    "GLISTERING_MELON_SLICE", "PUFFERFISH", "RABBITS_FOOT", "SLIME_BLOCK",
                    "DRAGONS_BREATH", "PHANTOM_MEMBRANE");
            plugin.getLogger().warning("brewing-ingredient-names not found in config.yml, using defaults");
        }
        brewingIngredientNames = brewingNamesList.toArray(new String[0]);

        List<Integer> brewingScoresList = config.getIntegerList("brewing-ingredient-scores");
        if (brewingScoresList.isEmpty()) {
            brewingScoresList = List.of(5,10,15,20,30,40,50,60,75,100,150,225,300,400,600,825,1000,1500,2000,3000);
            plugin.getLogger().warning("brewing-ingredient-scores not found in config.yml, using defaults");
        }
        brewingIngredientScores = brewingScoresList.stream().mapToInt(Integer::intValue).toArray();



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

        ConfigurationSection itemSection = config.getConfigurationSection("item-values");
        if (itemSection != null) {
            itemValues.clear();
            for (String key : itemSection.getKeys(false)) {
                itemValues.put(key, itemSection.getDouble(key));
            }
        } else {
            plugin.getLogger().warning("item-values not found in config.yml, no item scoring available");
        }

        // Load potion values
        ConfigurationSection potionSection = config.getConfigurationSection("potion-values");
        if (potionSection != null) {
            potionValues.clear();
            for (String key : potionSection.getKeys(false)) {
                potionValues.put(key.toUpperCase(), potionSection.getDouble(key));
            }
        } else {
            // Default potion values
            potionValues.put("WATER", 0.0);
            potionValues.put("THICK", 0.0);
            potionValues.put("MUNDANE", 0.0);
            potionValues.put("AWKWARD", 0.0);
            potionValues.put("REGENERATION", 20.0);
            potionValues.put("LONG_REGENERATION", 20.0);
            potionValues.put("STRONG_REGENERATION", 25.0);
            potionValues.put("SWIFTNESS", 5.0);
            potionValues.put("LONG_SWIFTNESS", 5.0);
            potionValues.put("STRONG_SWIFTNESS", 7.0);
            potionValues.put("FIRE_RESISTANCE", 15.0);
            potionValues.put("LONG_FIRE_RESISTANCE", 15.0);
            potionValues.put("POISON", 5.0);
            potionValues.put("LONG_POISON", 5.0);
            potionValues.put("STRONG_POISON", 7.0);
            potionValues.put("HEALING", 15.0);
            potionValues.put("STRONG_HEALING", 20.0);
            potionValues.put("NIGHT_VISION", 5.0);
            potionValues.put("LONG_NIGHT_VISION", 5.0);
            potionValues.put("WEAKNESS", 5.0);
            potionValues.put("LONG_WEAKNESS", 5.0);
            potionValues.put("STRENGTH", 5.0);
            potionValues.put("LONG_STRENGTH", 5.0);
            potionValues.put("STRONG_STRENGTH", 7.0);
            potionValues.put("SLOWNESS", 5.0);
            potionValues.put("LONG_SLOWNESS", 5.0);
            potionValues.put("STRONG_SLOWNESS", 7.0);
            potionValues.put("LEAPING", 5.0);
            potionValues.put("LONG_LEAPING", 5.0);
            potionValues.put("STRONG_LEAPING", 7.0);
            potionValues.put("HARMING", 5.0);
            potionValues.put("STRONG_HARMING", 7.0);
            potionValues.put("WATER_BREATHING", 10.0);
            potionValues.put("LONG_WATER_BREATHING", 10.0);
            potionValues.put("INVISIBILITY", 5.0);
            potionValues.put("LONG_INVISIBILITY", 5.0);
            potionValues.put("SLOW_FALLING", 5.0);
            potionValues.put("LONG_SLOW_FALLING", 5.0);
            potionValues.put("LUCK", 5.0);
            potionValues.put("TURTLE_MASTER", 5.0);
            potionValues.put("LONG_TURTLE_MASTER", 5.0);
            potionValues.put("STRONG_TURTLE_MASTER", 7.0);
            potionValues.put("WIND_CHARGED", 5.0);
            potionValues.put("WEAVING", 5.0);
            potionValues.put("OOZING", 5.0);
            potionValues.put("INFESTED", 5.0);
            potionValues.put("SPLASH_WATER", 0.0);
            potionValues.put("SPLASH_THICK", 0.0);
            potionValues.put("SPLASH_MUNDANE", 0.0);
            potionValues.put("SPLASH_AWKWARD", 0.0);
            potionValues.put("SPLASH_REGENERATION", 25.0);
            potionValues.put("SPLASH_LONG_REGENERATION", 25.0);
            potionValues.put("SPLASH_STRONG_REGENERATION", 30.0);
            potionValues.put("SPLASH_SWIFTNESS", 5.0);
            potionValues.put("SPLASH_LONG_SWIFTNESS", 5.0);
            potionValues.put("SPLASH_STRONG_SWIFTNESS", 7.0);
            potionValues.put("SPLASH_FIRE_RESISTANCE", 5.0);
            potionValues.put("SPLASH_LONG_FIRE_RESISTANCE", 5.0);
            potionValues.put("SPLASH_POISON", 5.0);
            potionValues.put("SPLASH_LONG_POISON", 5.0);
            potionValues.put("SPLASH_STRONG_POISON", 7.0);
            potionValues.put("SPLASH_HEALING", 5.0);
            potionValues.put("SPLASH_STRONG_HEALING", 7.0);
            potionValues.put("SPLASH_NIGHT_VISION", 5.0);
            potionValues.put("SPLASH_LONG_NIGHT_VISION", 5.0);
            potionValues.put("SPLASH_WEAKNESS", 5.0);
            potionValues.put("SPLASH_LONG_WEAKNESS", 5.0);
            potionValues.put("SPLASH_STRENGTH", 5.0);
            potionValues.put("SPLASH_LONG_STRENGTH", 5.0);
            potionValues.put("SPLASH_STRONG_STRENGTH", 7.0);
            potionValues.put("SPLASH_SLOWNESS", 5.0);
            potionValues.put("SPLASH_LONG_SLOWNESS", 5.0);
            potionValues.put("SPLASH_STRONG_SLOWNESS", 7.0);
            potionValues.put("SPLASH_LEAPING", 5.0);
            potionValues.put("SPLASH_LONG_LEAPING", 5.0);
            potionValues.put("SPLASH_STRONG_LEAPING", 7.0);
            potionValues.put("SPLASH_HARMING", 10.0);
            potionValues.put("SPLASH_STRONG_HARMING", 12.0);
            potionValues.put("SPLASH_WATER_BREATHING", 5.0);
            potionValues.put("SPLASH_LONG_WATER_BREATHING", 5.0);
            potionValues.put("SPLASH_INVISIBILITY", 5.0);
            potionValues.put("SPLASH_LONG_INVISIBILITY", 5.0);
            potionValues.put("SPLASH_SLOW_FALLING", 5.0);
            potionValues.put("SPLASH_LONG_SLOW_FALLING", 5.0);
            potionValues.put("SPLASH_LUCK", 5.0);
            potionValues.put("SPLASH_TURTLE_MASTER", 5.0);
            potionValues.put("SPLASH_LONG_TURTLE_MASTER", 5.0);
            potionValues.put("SPLASH_STRONG_TURTLE_MASTER", 7.0);
            potionValues.put("SPLASH_WIND_CHARGED", 5.0);
            potionValues.put("SPLASH_WEAVING", 5.0);
            potionValues.put("SPLASH_OOZING", 5.0);
            potionValues.put("SPLASH_INFESTED", 5.0);
            potionValues.put("LINGERING_WATER", 0.0);
            potionValues.put("LINGERING_THICK", 0.0);
            potionValues.put("LINGERING_MUNDANE", 0.0);
            potionValues.put("LINGERING_AWKWARD", 0.0);
            potionValues.put("LINGERING_REGENERATION", 2.0);
            potionValues.put("LINGERING_LONG_REGENERATION", 2.0);
            potionValues.put("LINGERING_STRONG_REGENERATION", 3.0);
            potionValues.put("LINGERING_SWIFTNESS", 2.0);
            potionValues.put("LINGERING_LONG_SWIFTNESS", 2.0);
            potionValues.put("LINGERING_STRONG_SWIFTNESS", 3.0);
            potionValues.put("LINGERING_FIRE_RESISTANCE", 2.0);
            potionValues.put("LINGERING_LONG_FIRE_RESISTANCE", 2.0);
            potionValues.put("LINGERING_POISON", 2.0);
            potionValues.put("LINGERING_LONG_POISON", 2.0);
            potionValues.put("LINGERING_STRONG_POISON", 3.0);
            potionValues.put("LINGERING_HEALING", 2.0);
            potionValues.put("LINGERING_STRONG_HEALING", 3.0);
            potionValues.put("LINGERING_NIGHT_VISION", 2.0);
            potionValues.put("LINGERING_LONG_NIGHT_VISION", 2.0);
            potionValues.put("LINGERING_WEAKNESS", 2.0);
            potionValues.put("LINGERING_LONG_WEAKNESS", 2.0);
            potionValues.put("LINGERING_STRENGTH", 2.0);
            potionValues.put("LINGERING_LONG_STRENGTH", 2.0);
            potionValues.put("LINGERING_STRONG_STRENGTH", 3.0);
            potionValues.put("LINGERING_SLOWNESS", 2.0);
            potionValues.put("LINGERING_LONG_SLOWNESS", 2.0);
            potionValues.put("LINGERING_STRONG_SLOWNESS", 3.0);
            potionValues.put("LINGERING_LEAPING", 2.0);
            potionValues.put("LINGERING_LONG_LEAPING", 2.0);
            potionValues.put("LINGERING_STRONG_LEAPING", 3.0);
            potionValues.put("LINGERING_HARMING", 2.0);
            potionValues.put("LINGERING_STRONG_HARMING", 3.0);
            potionValues.put("LINGERING_WATER_BREATHING", 10.0);
            potionValues.put("LINGERING_LONG_WATER_BREATHING", 10.0);
            potionValues.put("LINGERING_INVISIBILITY", 2.0);
            potionValues.put("LINGERING_LONG_INVISIBILITY", 2.0);
            potionValues.put("LINGERING_SLOW_FALLING", 2.0);
            potionValues.put("LINGERING_LONG_SLOW_FALLING", 2.0);
            potionValues.put("LINGERING_LUCK", 2.0);
            potionValues.put("LINGERING_TURTLE_MASTER", 2.0);
            potionValues.put("LINGERING_LONG_TURTLE_MASTER", 2.0);
            potionValues.put("LINGERING_STRONG_TURTLE_MASTER", 3.0);
            potionValues.put("LINGERING_WIND_CHARGED", 2.0);
            potionValues.put("LINGERING_WEAVING", 2.0);
            potionValues.put("LINGERING_OOZING", 2.0);
            potionValues.put("LINGERING_INFESTED", 2.0);
            plugin.getLogger().warning("potion-values not found in config.yml, using default values");
        }

        ConfigurationSection collectionSection = config.getConfigurationSection("collection-bonuses");
        if (collectionSection != null) {
            workstationBonus = collectionSection.getInt("workstations", 250);
            dyeBonus = collectionSection.getInt("dyes", 300);
            bucketBonus = collectionSection.getInt("buckets", 500);
            soupBonus = collectionSection.getInt("soups", 150);
        } else {
            workstationBonus = 250;
            dyeBonus = 300;
            bucketBonus = 500;
            soupBonus = 150;
            plugin.getLogger().warning("collection-bonuses not found in config.yml, using defaults");
        }

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
        InfinibundleListener.clearAll();
        LootHunt.paused = false;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(0);
        }
        Bukkit.broadcast(Component.text("Keep inventory set to true across all dimensions", NamedTextColor.YELLOW));
        playerItemCounts.clear();
        playerKillCounts.clear();
        playerDeathCounts.clear();
        startTimer = duration * 60 * 20;
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.globalBossBar.setVisible(true);
        ZappierGames.globalBossBar.setStyle(BarStyle.SOLID);
        ZappierGames.globalBossBar.setColor(BarColor.YELLOW);
        ZappierGames.globalBossBar.setProgress(1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.clearActivePotionEffects();
            p.setCollidable(true);
            ZappierGames.globalBossBar.addPlayer(p);
            p.getInventory().clear();
            giveStartingItems(p);
            p.sendTitle(ChatColor.GREEN + "Loot Hunt", ChatColor.GREEN + "Collect items, score points!", 10, 70, 20);
            p.sendActionBar(Component.text("Use /getscore <item> to find how much it's worth!", NamedTextColor.GREEN));
            p.sendMessage(Component.text("Use /getscore <item> to find how much it's worth!", NamedTextColor.GREEN));
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setLevel(0);
            p.setExp(0.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
        ZappierGames.gameMode = ZappierGames.LOOTHUNT;
        ZappierGames.timer = (int) Math.ceil(startTimer);
    }

    public static void endGame() {
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.gameMode = -1;

        Map<String, Map<String, Double>> teamItemCounts = new HashMap<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.YELLOW + "Game Finished!", "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            Map<String, List<ItemEntry>> inventoryCounts = calculateInventoryCounts(p);
            Map<String, List<ItemEntry>> mergedCounts = new HashMap<>();
            for (Map.Entry<String, List<ItemEntry>> entry : inventoryCounts.entrySet()) {
                String itemId = entry.getKey();
                List<ItemEntry> entries = entry.getValue();
                int totalQuantity = 0;
                double totalPoints = 0;
                for (ItemEntry itemEntry : entries) {
                    totalQuantity += itemEntry.quantity;
                    totalPoints += itemEntry.points;
                }
                mergedCounts.put(itemId, List.of(new ItemEntry(itemId, totalQuantity, totalPoints, "merged")));
            }
            inventoryCounts = mergedCounts;

            String teamName = p.getScoreboard().getEntryTeam(p.getName()) != null
                    ? p.getScoreboard().getEntryTeam(p.getName()).getName()
                    : "(Solo) " + p.getName();

            playerItemCounts.put(p.getName().toUpperCase(), inventoryCounts);

            Map<String, Double> teamScores = teamItemCounts.computeIfAbsent(teamName, k -> new HashMap<>());
            for (Map.Entry<String, List<ItemEntry>> entry : inventoryCounts.entrySet()) {
                double totalPoints = entry.getValue().stream().mapToDouble(e -> e.points).sum();
                teamScores.merge(entry.getKey(), totalPoints, Double::sum);
            }


            int killCount = playerKillCounts.getOrDefault(p.getName().toUpperCase(), 0);
            int oldKillCount = killCount;
            int killValue = baseKillPoints;
            int addScore = 0;
            while (killCount > 0 && killValue > 1) {
                addScore += killValue;
                killCount--;
                killValue /= pointsReductionFactor;
            }
            Bukkit.broadcast(Component.text(p.getName() + " got " + oldKillCount + " kills, earning " + addScore + " points for team " + teamName, NamedTextColor.YELLOW));
            teamItemCounts.get(teamName).put("kills", teamItemCounts.get(teamName).getOrDefault("kills", 0.0) + addScore);

            int deathCount = playerDeathCounts.getOrDefault(p.getName().toUpperCase(), 0);
            int oldDeathCount = deathCount;
            int deathValue = baseDeathPoints;
            addScore = 0;
            while (deathCount > 0 && deathValue > 1) {
                addScore -= deathValue;
                deathCount--;
                deathValue /= pointsReductionFactor;
            }
            Bukkit.broadcast(Component.text(p.getName() + " got " + oldDeathCount + " deaths, losing " + Math.abs(addScore) + " points for team " + teamName, NamedTextColor.YELLOW));
            teamItemCounts.get(teamName).put("deaths", teamItemCounts.get(teamName).getOrDefault("deaths", 0.0) + addScore);
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("        RESULTS        ", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        for (Map.Entry<String, Map<String, Double>> d : teamItemCounts.entrySet()) {
            double disScore = 0;
            int doorsCount = 0;
            //Map<String, Integer> doorsCollected = new HashMap<>();
            int workStationCount = 0;
            int dyeCount = 0;
            int bucketCount = 0;
            int soupCount = 0;
            int brewingCount = 0;
            for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                String itemName = e.getKey();
                disScore += e.getValue();
                if (doorNames != null) {
                    for (String doorName : doorNames) {
                        if (doorName != null && doorName.equals(itemName)) {
                            doorsCount++;
                            //doorsCollected.put(doorName, 1);
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
                if (bucketNames != null) {
                    for (String bucketName : bucketNames) {
                        if (bucketName.equals(itemName)) {
                            bucketCount++;
                            break;
                        }
                    }
                }
                if (soupNames != null) {
                    for (String soupName : soupNames) {
                        if (soupName.equals(itemName)) {
                            soupCount++;
                            break;
                        }
                    }
                }
                if (brewingIngredientNames != null) {
                    for (String ingName : brewingIngredientNames) {
                        if (ingName.equals(itemName)) {
                            brewingCount++;
                            break;
                        }
                    }
                }
            }

            if (workStationNames != null && workStationCount >= workStationNames.length) {
                disScore += workstationBonus;
                ZappierGames.getInstance().getLogger().info("Applied workstation bonus (" + workstationBonus + ") for team " + d.getKey());
            }

            if (dyeNames != null && dyeCount >= dyeNames.length) {
                disScore += dyeBonus;
                ZappierGames.getInstance().getLogger().info("Applied dye bonus (" + dyeBonus + ") for team " + d.getKey());
            }

            if (doorScores != null && doorNames != null && doorsCount > 0) {
                int doorBonus = doorsCount <= doorScores.length ? doorScores[doorsCount - 1] : doorScores[doorScores.length - 1];
                disScore += doorBonus;
                ZappierGames.getInstance().getLogger().info("Applied door bonus (" + doorBonus + ") for team " + d.getKey() + " with " + doorsCount + " doors");
            }

            if (bucketNames != null && bucketCount >= bucketNames.length) {
                disScore += bucketBonus;
            }
            if (soupNames != null && soupCount >= soupNames.length) {
                disScore += soupBonus;
            }
            if (brewingIngredientScores != null && brewingIngredientNames != null && brewingCount > 0) {
                int brewBonus = brewingCount <= brewingIngredientScores.length
                        ? brewingIngredientScores[brewingCount - 1]
                        : brewingIngredientScores[brewingIngredientScores.length - 1];
                disScore += brewBonus;
            }

            Bukkit.broadcast(Component.text(d.getKey() + ": " + String.format("%.1f", disScore), NamedTextColor.YELLOW));
            if (doorNames != null) {
                Map<String, Integer> doorsCollected = new HashMap<>();

                // Recount doors properly with support for multiples per type
                for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                    String itemName = e.getKey();
                    for (String doorName : doorNames) {
                        if (doorName != null && doorName.equals(itemName)) {
                            doorsCollected.merge(doorName, 1, Integer::sum);
                            break;
                        }
                    }
                }

                final Component[] doorHover = {Component.text("Collected doors:", NamedTextColor.GREEN)
                        .append(Component.newline())
                        .append(Component.newline())};

                Arrays.stream(doorNames)
                        .filter(name -> name != null)
                        .sorted()
                        .forEach(doorName -> {
                            int count = doorsCollected.getOrDefault(doorName, 0);
                            NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                            String prefix = count > 0 ? "✓ " : "✗ ";
                            doorHover[0] = doorHover[0].append(Component.text(prefix + doorName, color))
                                    .append(Component.newline());
                        });

                Component doorText = Component.text("  Doors: " + doorsCount + "/" + doorNames.length, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(doorHover[0]));

                Bukkit.broadcast(doorText);

            } else {
                Bukkit.broadcast(Component.text("  Doors: " + doorsCount + "/0 (config missing)", NamedTextColor.GRAY));
            }

// === WORKSTATIONS ===
            if (workStationNames != null) {
                Map<String, Integer> workstationsCollected = new HashMap<>();

                // Re-count workstations properly with counts (in case multiples allowed)
                for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                    String itemName = e.getKey();
                    for (String wsName : workStationNames) {
                        if (wsName.equals(itemName)) {
                            workstationsCollected.merge(wsName, 1, Integer::sum);
                        }
                    }
                }

                final Component[] wsHover = {Component.text("Collected workstations:", NamedTextColor.AQUA)
                        .append(Component.newline())
                        .append(Component.newline())};

                Arrays.stream(workStationNames)
                        .sorted()
                        .forEach(wsName -> {
                            int count = workstationsCollected.getOrDefault(wsName, 0);
                            NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                            String prefix = count > 0 ? "✓ " : "✗ ";
                            wsHover[0] = wsHover[0].append(Component.text(prefix + wsName, color))
                                    .append(Component.newline());
                        });

                Component wsText = Component.text("  Workstations: " + workStationCount + "/" + workStationNames.length, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(wsHover[0]));

                Bukkit.broadcast(wsText);

            } else {
                Bukkit.broadcast(Component.text("  Workstations: " + workStationCount + "/0 (config missing)", NamedTextColor.GRAY));
            }

// === DYES ===
            if (dyeNames != null) {
                Map<String, Integer> dyesCollected = new HashMap<>();

                // Count dyes (multiples possible)
                for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                    String itemName = e.getKey();
                    for (String dyeName : dyeNames) {
                        if (dyeName.equals(itemName)) {
                            dyesCollected.merge(dyeName, 1, Integer::sum);
                        }
                    }
                }

                final Component[] dyeHover = {Component.text("Collected dyes:", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.newline())
                        .append(Component.newline())};

                Arrays.stream(dyeNames)
                        .sorted()
                        .forEach(dyeName -> {
                            int count = dyesCollected.getOrDefault(dyeName, 0);
                            NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                            String prefix = count > 0 ? "✓ " : "✗ ";
                            dyeHover[0] = dyeHover[0].append(Component.text(prefix + dyeName, color))
                                    .append(Component.newline());
                        });

                Component dyeText = Component.text("  Dyes: " + dyeCount + "/" + dyeNames.length, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(dyeHover[0]));

                Bukkit.broadcast(dyeText);

            } else {
                Bukkit.broadcast(Component.text("  Dyes: " + dyeCount + "/0 (config missing)", NamedTextColor.GRAY));
            }

            // === BUCKETS ===
            if (bucketNames != null) {
                Map<String, Integer> bucketsCollected = new HashMap<>();
                for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                    String itemName = e.getKey();
                    for (String bName : bucketNames) {
                        if (bName.equals(itemName)) {
                            bucketsCollected.merge(bName, 1, Integer::sum);
                        }
                    }
                }

                final Component[] bucketHover = {Component.text("Collected buckets:", NamedTextColor.AQUA)
                        .append(Component.newline())
                        .append(Component.newline())};

                Arrays.stream(bucketNames)
                        .sorted()
                        .forEach(bName -> {
                            int count = bucketsCollected.getOrDefault(bName, 0);
                            NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                            String prefix = count > 0 ? "✓ " : "✗ ";
                            bucketHover[0] = bucketHover[0].append(Component.text(prefix + bName, color))
                                    .append(Component.newline());
                        });

                Component bucketText = Component.text("  Buckets: " + bucketCount + "/" + bucketNames.length, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(bucketHover[0]));

                Bukkit.broadcast(bucketText);
            } else {
                Bukkit.broadcast(Component.text("  Buckets: " + bucketCount + "/0 (config missing)", NamedTextColor.GRAY));
            }

// === SOUPS ===
            if (soupNames != null) {
                Map<String, Integer> soupsCollected = new HashMap<>();
                for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                    String itemName = e.getKey();
                    for (String sName : soupNames) {
                        if (sName.equals(itemName)) {
                            soupsCollected.merge(sName, 1, Integer::sum);
                        }
                    }
                }

                final Component[] soupHover = {Component.text("Collected soups:", NamedTextColor.GOLD)
                        .append(Component.newline())
                        .append(Component.newline())};

                Arrays.stream(soupNames)
                        .sorted()
                        .forEach(sName -> {
                            int count = soupsCollected.getOrDefault(sName, 0);
                            NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                            String prefix = count > 0 ? "✓ " : "✗ ";
                            soupHover[0] = soupHover[0].append(Component.text(prefix + sName, color))
                                    .append(Component.newline());
                        });

                Component soupText = Component.text("  Soups: " + soupCount + "/" + soupNames.length, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(soupHover[0]));

                Bukkit.broadcast(soupText);
            } else {
                Bukkit.broadcast(Component.text("  Soups: " + soupCount + "/0 (config missing)", NamedTextColor.GRAY));
            }

// === BREWING INGREDIENTS ===
            if (brewingIngredientNames != null) {
                Map<String, Integer> brewingCollected = new HashMap<>();
                for (Map.Entry<String, Double> e : d.getValue().entrySet()) {
                    String itemName = e.getKey();
                    for (String ingName : brewingIngredientNames) {
                        if (ingName.equals(itemName)) {
                            brewingCollected.merge(ingName, 1, Integer::sum);
                        }
                    }
                }

                final Component[] brewingHover = {Component.text("Collected brewing ingredients:", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.newline())
                        .append(Component.newline())};

                Arrays.stream(brewingIngredientNames)
                        .sorted()
                        .forEach(ingName -> {
                            int count = brewingCollected.getOrDefault(ingName, 0);
                            NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
                            String prefix = count > 0 ? "✓ " : "✗ ";
                            brewingHover[0] = brewingHover[0].append(Component.text(prefix + ingName, color))
                                    .append(Component.newline());
                        });

                Component brewingText = Component.text("  Brewing Ingredients: " + brewingCount + "/" + brewingIngredientNames.length, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(brewingHover[0]));

                Bukkit.broadcast(brewingText);
            } else {
                Bukkit.broadcast(Component.text("  Brewing Ingredients: " + brewingCount + "/0 (config missing)", NamedTextColor.GRAY));
            }
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));
    }

    public static Map<String, List<ItemEntry>> calculateInventoryCounts(Player player) {
        Map<String, List<ItemEntry>> scoreMap = new HashMap<>();
        ZappierGames.getInstance().getLogger().info("Processing inventory for " + player.getName());

        // Process main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String itemId = item.getType().toString();
            double itemValue = getItemValue(itemId);
            int amount = item.getAmount();

            // Handle potions
            if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType potionType = potionMeta.getBasePotionType();
                    String effectName;
                    if (potionType == null) {
                        effectName = "NONE";
                    } else {
                        // Map PotionType to effect name for scoring
                        switch (potionType) {
                            case WATER:
                                effectName = "WATER";
                                break;
                            case THICK:
                                effectName = "THICK";
                                break;
                            case MUNDANE:
                                effectName = "MUNDANE";
                                break;
                            case AWKWARD:
                                effectName = "AWKWARD";
                                break;
                            case REGENERATION:
                                effectName = "REGENERATION";
                                break;
                            case LONG_REGENERATION:
                                effectName = "LONG_REGENERATION";
                                break;
                            case STRONG_REGENERATION:
                                effectName = "STRONG_REGENERATION";
                                break;
                            case SWIFTNESS:
                                effectName = "SWIFTNESS";
                                break;
                            case LONG_SWIFTNESS:
                                effectName = "LONG_SWIFTNESS";
                                break;
                            case STRONG_SWIFTNESS:
                                effectName = "STRONG_SWIFTNESS";
                                break;
                            case FIRE_RESISTANCE:
                                effectName = "FIRE_RESISTANCE";
                                break;
                            case LONG_FIRE_RESISTANCE:
                                effectName = "LONG_FIRE_RESISTANCE";
                                break;
                            case POISON:
                                effectName = "POISON";
                                break;
                            case LONG_POISON:
                                effectName = "LONG_POISON";
                                break;
                            case STRONG_POISON:
                                effectName = "STRONG_POISON";
                                break;
                            case HEALING:
                                effectName = "HEALING";
                                break;
                            case STRONG_HEALING:
                                effectName = "STRONG_HEALING";
                                break;
                            case NIGHT_VISION:
                                effectName = "NIGHT_VISION";
                                break;
                            case LONG_NIGHT_VISION:
                                effectName = "LONG_NIGHT_VISION";
                                break;
                            case WEAKNESS:
                                effectName = "WEAKNESS";
                                break;
                            case LONG_WEAKNESS:
                                effectName = "LONG_WEAKNESS";
                                break;
                            case STRENGTH:
                                effectName = "STRENGTH";
                                break;
                            case LONG_STRENGTH:
                                effectName = "LONG_STRENGTH";
                                break;
                            case STRONG_STRENGTH:
                                effectName = "STRONG_STRENGTH";
                                break;
                            case SLOWNESS:
                                effectName = "SLOWNESS";
                                break;
                            case LONG_SLOWNESS:
                                effectName = "LONG_SLOWNESS";
                                break;
                            case STRONG_SLOWNESS:
                                effectName = "STRONG_SLOWNESS";
                                break;
                            case LEAPING:
                                effectName = "LEAPING";
                                break;
                            case LONG_LEAPING:
                                effectName = "LONG_LEAPING";
                                break;
                            case STRONG_LEAPING:
                                effectName = "STRONG_LEAPING";
                                break;
                            case HARMING:
                                effectName = "HARMING";
                                break;
                            case STRONG_HARMING:
                                effectName = "STRONG_HARMING";
                                break;
                            case WATER_BREATHING:
                                effectName = "WATER_BREATHING";
                                break;
                            case LONG_WATER_BREATHING:
                                effectName = "LONG_WATER_BREATHING";
                                break;
                            case INVISIBILITY:
                                effectName = "INVISIBILITY";
                                break;
                            case LONG_INVISIBILITY:
                                effectName = "LONG_INVISIBILITY";
                                break;
                            case SLOW_FALLING:
                                effectName = "SLOW_FALLING";
                                break;
                            case LONG_SLOW_FALLING:
                                effectName = "LONG_SLOW_FALLING";
                                break;
                            case LUCK:
                                effectName = "LUCK";
                                break;
                            case TURTLE_MASTER:
                                effectName = "TURTLE_MASTER";
                                break;
                            case LONG_TURTLE_MASTER:
                                effectName = "LONG_TURTLE_MASTER";
                                break;
                            case STRONG_TURTLE_MASTER:
                                effectName = "STRONG_TURTLE_MASTER";
                                break;
                            case WIND_CHARGED:
                                effectName = "WIND_CHARGED";
                                break;
                            case WEAVING:
                                effectName = "WEAVING";
                                break;
                            case OOZING:
                                effectName = "OOZING";
                                break;
                            case INFESTED:
                                effectName = "INFESTED";
                                break;
                            default:
                                effectName = "NONE";
                                break;
                        }
                    }
                    if (item.getType() == Material.POTION) {
                        itemValue = potionValues.getOrDefault(effectName, 0.0);
                    } else if (item.getType() == Material.SPLASH_POTION) {
                        itemValue = potionValues.getOrDefault("SPLASH_" + effectName, 0.0);
                    } else if (item.getType() == Material.LINGERING_POTION) {
                        itemValue = potionValues.getOrDefault("LINGERING_" + effectName, 0.0);
                    }
                    ZappierGames.getInstance().getLogger().info("Potion " + itemId + " with effect " + effectName + ": value=" + itemValue);
                } else {
                    itemValue = 0.0; // No PotionMeta, treat as invalid
                }
            }

            // Handle crafted armor, tools, etc.
            if (isCraftedArmorOrTool(itemId)) {
                itemValue = getCraftingCost(itemId);
            }

            // Handle enchantments
            ZappierGames.getInstance().getLogger().info("Item name: " + item.getType().name());
            ZappierGames.getInstance().getLogger().info("Item bools: " + item.hasItemMeta() + " : " + item.getItemMeta().toString());
            ZappierGames.getInstance().getLogger().info("Enchs: " + (item.getType() == Material.ENCHANTED_BOOK ? ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants().toString() : item.getEnchantments().toString()));
            if (item.hasItemMeta() && (item.getItemMeta().hasEnchants() || (item.getType() == Material.ENCHANTED_BOOK && !((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants().isEmpty()))) {
                itemValue += getTotalEnchantmentPoints(item);
            }

            // Handle doors, workstations, and dyes
            boolean isCollectionItem = false;
            if (Arrays.asList(doorNames).contains(itemId) || Arrays.asList(workStationNames).contains(itemId) || Arrays.asList(dyeNames).contains(itemId)) {
                isCollectionItem = true;
                if (itemValue == 0.0) {
                    itemValue = 0.001; // Default value for collection items with no config value
                }
            }

            ZappierGames.getInstance().getLogger().info("Main inventory item " + itemId + ": quantity=" + amount + ", value=" + itemValue + (isCollectionItem ? ", collection item" : ""));
            scoreMap.computeIfAbsent(itemId, k -> new ArrayList<>())
                    .add(new ItemEntry(itemId, amount, itemValue * amount, "Inventory"));

            // Process shulker box contents
            if (item.getType() == Material.SHULKER_BOX || item.getType().name().endsWith("_SHULKER_BOX")) {
                ZappierGames.getInstance().getLogger().info("Found shulker box " + itemId + " for " + player.getName());
                if (!item.hasItemMeta()) {
                    ZappierGames.getInstance().getLogger().warning("Shulker box " + itemId + " for " + player.getName() + " has no item meta");
                    continue;
                }
                if (!(item.getItemMeta() instanceof BlockStateMeta meta)) {
                    ZappierGames.getInstance().getLogger().warning("Shulker box " + itemId + " for " + player.getName() + " has invalid meta (not BlockStateMeta)");
                    continue;
                }
                BlockState state = meta.getBlockState();
                if (!(state instanceof ShulkerBox shulkerBox)) {
                    ZappierGames.getInstance().getLogger().warning("Shulker box " + itemId + " for " + player.getName() + " is not a valid ShulkerBox");
                    continue;
                }

                ZappierGames.getInstance().getLogger().info("Processing shulker box inventory for " + itemId);

                int itemCount = 0;
                for (ItemStack sItem : shulkerBox.getInventory().getContents()) {
                    if (sItem == null || sItem.getType() == Material.AIR) {
                        continue;
                    }
                    itemCount++;
                    String sItemId = sItem.getType().toString();
                    double sItemValue = getItemValue(sItemId);
                    int sAmount = sItem.getAmount();

                    // Handle potions in shulker box
                    if (sItem.getType() == Material.POTION || sItem.getType() == Material.SPLASH_POTION || sItem.getType() == Material.LINGERING_POTION) {
                        if (sItem.hasItemMeta() && sItem.getItemMeta() instanceof PotionMeta potionMeta) {
                            PotionType potionType = potionMeta.getBasePotionType();
                            String effectName;
                            if (potionType == null) {
                                effectName = "NONE";
                            } else {
                                // Map PotionType to effect name for scoring
                                switch (potionType) {
                                    case WATER:
                                        effectName = "WATER";
                                        break;
                                    case THICK:
                                        effectName = "THICK";
                                        break;
                                    case MUNDANE:
                                        effectName = "MUNDANE";
                                        break;
                                    case AWKWARD:
                                        effectName = "AWKWARD";
                                        break;
                                    case REGENERATION:
                                        effectName = "REGENERATION";
                                        break;
                                    case LONG_REGENERATION:
                                        effectName = "LONG_REGENERATION";
                                        break;
                                    case STRONG_REGENERATION:
                                        effectName = "STRONG_REGENERATION";
                                        break;
                                    case SWIFTNESS:
                                        effectName = "SWIFTNESS";
                                        break;
                                    case LONG_SWIFTNESS:
                                        effectName = "LONG_SWIFTNESS";
                                        break;
                                    case STRONG_SWIFTNESS:
                                        effectName = "STRONG_SWIFTNESS";
                                        break;
                                    case FIRE_RESISTANCE:
                                        effectName = "FIRE_RESISTANCE";
                                        break;
                                    case LONG_FIRE_RESISTANCE:
                                        effectName = "LONG_FIRE_RESISTANCE";
                                        break;
                                    case POISON:
                                        effectName = "POISON";
                                        break;
                                    case LONG_POISON:
                                        effectName = "LONG_POISON";
                                        break;
                                    case STRONG_POISON:
                                        effectName = "STRONG_POISON";
                                        break;
                                    case HEALING:
                                        effectName = "HEALING";
                                        break;
                                    case STRONG_HEALING:
                                        effectName = "STRONG_HEALING";
                                        break;
                                    case NIGHT_VISION:
                                        effectName = "NIGHT_VISION";
                                        break;
                                    case LONG_NIGHT_VISION:
                                        effectName = "LONG_NIGHT_VISION";
                                        break;
                                    case WEAKNESS:
                                        effectName = "WEAKNESS";
                                        break;
                                    case LONG_WEAKNESS:
                                        effectName = "LONG_WEAKNESS";
                                        break;
                                    case STRENGTH:
                                        effectName = "STRENGTH";
                                        break;
                                    case LONG_STRENGTH:
                                        effectName = "LONG_STRENGTH";
                                        break;
                                    case STRONG_STRENGTH:
                                        effectName = "STRONG_STRENGTH";
                                        break;
                                    case SLOWNESS:
                                        effectName = "SLOWNESS";
                                        break;
                                    case LONG_SLOWNESS:
                                        effectName = "LONG_SLOWNESS";
                                        break;
                                    case STRONG_SLOWNESS:
                                        effectName = "STRONG_SLOWNESS";
                                        break;
                                    case LEAPING:
                                        effectName = "LEAPING";
                                        break;
                                    case LONG_LEAPING:
                                        effectName = "LONG_LEAPING";
                                        break;
                                    case STRONG_LEAPING:
                                        effectName = "STRONG_LEAPING";
                                        break;
                                    case HARMING:
                                        effectName = "HARMING";
                                        break;
                                    case STRONG_HARMING:
                                        effectName = "STRONG_HARMING";
                                        break;
                                    case WATER_BREATHING:
                                        effectName = "WATER_BREATHING";
                                        break;
                                    case LONG_WATER_BREATHING:
                                        effectName = "LONG_WATER_BREATHING";
                                        break;
                                    case INVISIBILITY:
                                        effectName = "INVISIBILITY";
                                        break;
                                    case LONG_INVISIBILITY:
                                        effectName = "LONG_INVISIBILITY";
                                        break;
                                    case SLOW_FALLING:
                                        effectName = "SLOW_FALLING";
                                        break;
                                    case LONG_SLOW_FALLING:
                                        effectName = "LONG_SLOW_FALLING";
                                        break;
                                    case LUCK:
                                        effectName = "LUCK";
                                        break;
                                    case TURTLE_MASTER:
                                        effectName = "TURTLE_MASTER";
                                        break;
                                    case LONG_TURTLE_MASTER:
                                        effectName = "LONG_TURTLE_MASTER";
                                        break;
                                    case STRONG_TURTLE_MASTER:
                                        effectName = "STRONG_TURTLE_MASTER";
                                        break;
                                    case WIND_CHARGED:
                                        effectName = "WIND_CHARGED";
                                        break;
                                    case WEAVING:
                                        effectName = "WEAVING";
                                        break;
                                    case OOZING:
                                        effectName = "OOZING";
                                        break;
                                    case INFESTED:
                                        effectName = "INFESTED";
                                        break;
                                    default:
                                        effectName = "NONE";
                                        break;
                                }
                            }
                            if (sItem.getType() == Material.POTION) {
                                sItemValue = potionValues.getOrDefault(effectName, 0.0);
                            } else if (sItem.getType() == Material.SPLASH_POTION) {
                                sItemValue = potionValues.getOrDefault("SPLASH_" + effectName, 0.0);
                            } else if (sItem.getType() == Material.LINGERING_POTION) {
                                sItemValue = potionValues.getOrDefault("LINGERING_" + effectName, 0.0);
                            }
                            ZappierGames.getInstance().getLogger().info("Shulker box potion " + sItemId + " with effect " + effectName + ": value=" + sItemValue);
                        } else {
                            sItemValue = 0.0; // No PotionMeta, treat as invalid
                        }
                    }

                    // Handle crafted armor, tools, etc.
                    if (isCraftedArmorOrTool(sItemId)) {
                        sItemValue = getCraftingCost(sItemId);
                    }

                    // Handle enchantments
                    if (sItem.hasItemMeta() && (sItem.getItemMeta().hasEnchants() || (sItem.getType() == Material.ENCHANTED_BOOK && !((EnchantmentStorageMeta) sItem.getItemMeta()).getStoredEnchants().isEmpty()))) {
                        sItemValue += getTotalEnchantmentPoints(sItem);
                    }

                    // Handle doors, workstations, and dyes
                    boolean isShulkerCollectionItem = false;
                    if (Arrays.asList(doorNames).contains(sItemId) || Arrays.asList(workStationNames).contains(sItemId) || Arrays.asList(dyeNames).contains(sItemId)) {
                        isShulkerCollectionItem = true;
                        if (sItemValue == 0.0) {
                            sItemValue = 0.001; // Default value for collection items with no config value
                        }
                    }

                    ZappierGames.getInstance().getLogger().info("Shulker box item " + sItemId + ": quantity=" + sAmount + ", value=" + sItemValue + (isShulkerCollectionItem ? ", collection item" : ""));
                    scoreMap.computeIfAbsent(sItemId, k -> new ArrayList<>())
                            .add(new ItemEntry(sItemId, sAmount, sItemValue * sAmount, "Shulker Box"));
                }

                ZappierGames.getInstance().getLogger().info("Found " + itemCount + " items in shulker box " + itemId);
                meta.setBlockState(shulkerBox);
                item.setItemMeta(meta);
            }
        }

        ZappierGames.getInstance().getLogger().info("Final score map for " + player.getName() + ": " + scoreMap);
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
                !itemId.equals("CHAINMAIL_HELMET") && !itemId.equals("CHAINMAIL_CHESTPLATE") &&
                !itemId.equals("CHAINMAIL_LEGGINGS") && !itemId.equals("CHAINMAIL_BOOTS") &&
                !itemId.equals("STONE_SWORD") && !itemId.equals("STONE_AXE") &&
                !itemId.equals("STONE_PICKAXE") && !itemId.equals("STONE_SHOVEL") &&
                !itemId.equals("STONE_HOE");
    }

    private static double getCraftingCost(String itemId) {
        Map<String, Integer> materialCosts = new HashMap<>();
        materialCosts.put("LEATHER_HELMET", 5);
        materialCosts.put("LEATHER_CHESTPLATE", 8);
        materialCosts.put("LEATHER_LEGGINGS", 7);
        materialCosts.put("LEATHER_BOOTS", 4);
        materialCosts.put("IRON_HELMET", 5);
        materialCosts.put("IRON_CHESTPLATE", 8);
        materialCosts.put("IRON_LEGGINGS", 7);
        materialCosts.put("IRON_BOOTS", 4);
        materialCosts.put("GOLDEN_HELMET", 5);
        materialCosts.put("GOLDEN_CHESTPLATE", 8);
        materialCosts.put("GOLDEN_LEGGINGS", 7);
        materialCosts.put("GOLDEN_BOOTS", 4);
        materialCosts.put("DIAMOND_HELMET", 5);
        materialCosts.put("DIAMOND_CHESTPLATE", 8);
        materialCosts.put("DIAMOND_LEGGINGS", 7);
        materialCosts.put("DIAMOND_BOOTS", 4);
        materialCosts.put("NETHERITE_HELMET", 1);
        materialCosts.put("NETHERITE_CHESTPLATE", 1);
        materialCosts.put("NETHERITE_LEGGINGS", 1);
        materialCosts.put("NETHERITE_BOOTS", 1);
        materialCosts.put("WOODEN_SWORD", 2);
        materialCosts.put("WOODEN_AXE", 3);
        materialCosts.put("WOODEN_PICKAXE", 3);
        materialCosts.put("WOODEN_SHOVEL", 1);
        materialCosts.put("WOODEN_HOE", 2);
        materialCosts.put("IRON_SWORD", 2);
        materialCosts.put("IRON_AXE", 3);
        materialCosts.put("IRON_PICKAXE", 3);
        materialCosts.put("IRON_SHOVEL", 1);
        materialCosts.put("IRON_HOE", 2);
        materialCosts.put("GOLDEN_SWORD", 2);
        materialCosts.put("GOLDEN_AXE", 3);
        materialCosts.put("GOLDEN_PICKAXE", 3);
        materialCosts.put("GOLDEN_SHOVEL", 1);
        materialCosts.put("GOLDEN_HOE", 2);
        materialCosts.put("DIAMOND_SWORD", 2);
        materialCosts.put("DIAMOND_AXE", 3);
        materialCosts.put("DIAMOND_PICKAXE", 3);
        materialCosts.put("DIAMOND_SHOVEL", 1);
        materialCosts.put("DIAMOND_HOE", 2);
        materialCosts.put("NETHERITE_SWORD", 1);
        materialCosts.put("NETHERITE_AXE", 1);
        materialCosts.put("NETHERITE_PICKAXE", 1);
        materialCosts.put("NETHERITE_SHOVEL", 1);
        materialCosts.put("NETHERITE_HOE", 1);
        materialCosts.put("BOW", 3);
        materialCosts.put("CROSSBOW", 3);
        materialCosts.put("FISHING_ROD", 2);
        materialCosts.put("SHEARS", 2);
        materialCosts.put("FLINT_AND_STEEL", 1);
        materialCosts.put("CLOCK", 4);
        materialCosts.put("COMPASS", 4);

        int materialCount = materialCosts.getOrDefault(itemId, 0);
        if (itemId.startsWith("LEATHER_")) {
            return materialCount * getItemValue("LEATHER");
        } else if (itemId.startsWith("IRON_")) {
            return materialCount * getItemValue("IRON_INGOT");
        } else if (itemId.startsWith("GOLDEN_")) {
            return materialCount * getItemValue("GOLD_INGOT");
        } else if (itemId.startsWith("DIAMOND_")) {
            return materialCount * getItemValue("DIAMOND");
        } else if (itemId.startsWith("NETHERITE_")) {
            return materialCount * getItemValue("NETHERITE_INGOT");
        } else if (itemId.equals("CLOCK")) {
            return materialCount * getItemValue("GOLD_INGOT");
        } else if (itemId.equals("COMPASS")) {
            return materialCount * getItemValue("IRON_INGOT");
        } else if (itemId.equals("SHEARS") || itemId.equals("FLINT_AND_STEEL")) {
            return materialCount * getItemValue("IRON_INGOT");
        } else if (itemId.equals("BOW") || itemId.equals("CROSSBOW") || itemId.equals("FISHING_ROD")) {
            return materialCount * getItemValue("STRING");
        } else if (itemId.startsWith("WOODEN_")) {
            return materialCount * getItemValue("OAK_PLANKS");
        }
        return 0.0;
    }

    public static void run() {
        if (ZappierGames.timer <= 0) {
            ZappierGames.gameMode = -1;
            endGame();
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            ZappierGames.globalBossBar.addPlayer(p);
        }

        double secondsTotal = ZappierGames.timer / 20.0;
        int hours = (int) (secondsTotal / 3600);
        int minutes = (int) ((secondsTotal % 3600) / 60);
        int seconds = (int) (secondsTotal % 60);

        if (!LootHunt.paused) {
            ZappierGames.globalBossBar.setColor(BarColor.YELLOW);
            ZappierGames.globalBossBar.setTitle(String.format("Time Left: %02d:%02d:%02d", hours, minutes, seconds));
            ZappierGames.globalBossBar.setProgress(ZappierGames.timer / startTimer);
            ZappierGames.timer--;
        } else {
            ZappierGames.globalBossBar.setColor(BarColor.RED);
            ZappierGames.globalBossBar.setTitle(String.format("(PAUSED) Time Left: %02d:%02d:%02d (PAUSED)", hours, minutes, seconds));
            ZappierGames.globalBossBar.setProgress(ZappierGames.timer / startTimer);
        }
    }

    public static void giveStartingItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_SHOVEL));
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE));

        //Infinibundle
        // Inside giveStartingItems(Player player) or wherever you give the super-bundle

        String teamName = player.getScoreboard().getEntryTeam(player.getName()) != null
                ? player.getScoreboard().getEntryTeam(player.getName()).getName()
                : "(Solo) " + player.getName();

        ChatColor teamChatColor = getTeamColor(teamName);
        NamedTextColor teamTextColor = chatColorToAdventure(teamChatColor);

        // Determine the base Material for the bundle (colored if possible, fallback to normal BUNDLE)
        Material bundleMaterial = Material.BUNDLE; // Default
        String lowerTeam = teamName.toLowerCase(Locale.ENGLISH);
        if (lowerTeam.contains("black")) bundleMaterial = Material.BLACK_BUNDLE;
        else if (lowerTeam.contains("red")) bundleMaterial = Material.RED_BUNDLE;
        else if (lowerTeam.contains("green")) bundleMaterial = Material.GREEN_BUNDLE;
        else if (lowerTeam.contains("brown")) bundleMaterial = Material.BROWN_BUNDLE;
        else if (lowerTeam.contains("blue")) bundleMaterial = Material.BLUE_BUNDLE;
        else if (lowerTeam.contains("purple")) bundleMaterial = Material.PURPLE_BUNDLE;
        else if (lowerTeam.contains("cyan")) bundleMaterial = Material.CYAN_BUNDLE;
        else if (lowerTeam.contains("light_gray")) bundleMaterial = Material.LIGHT_GRAY_BUNDLE;
        else if (lowerTeam.contains("gray")) bundleMaterial = Material.GRAY_BUNDLE;
        else if (lowerTeam.contains("pink")) bundleMaterial = Material.PINK_BUNDLE;
        else if (lowerTeam.contains("lime")) bundleMaterial = Material.LIME_BUNDLE;
        else if (lowerTeam.contains("yellow")) bundleMaterial = Material.YELLOW_BUNDLE;
        else if (lowerTeam.contains("light_blue")) bundleMaterial = Material.LIGHT_BLUE_BUNDLE;
        else if (lowerTeam.contains("magenta")) bundleMaterial = Material.MAGENTA_BUNDLE;
        else if (lowerTeam.contains("orange")) bundleMaterial = Material.ORANGE_BUNDLE;
        else if (lowerTeam.contains("white")) bundleMaterial = Material.WHITE_BUNDLE;
// Add more mappings if you have other team colors

        ItemStack infinibundle = new ItemStack(bundleMaterial);

        ItemMeta meta = infinibundle.getItemMeta();
        if (meta != null) {
            // Name with team color
            meta.displayName(Component.text("Infinibundle", teamTextColor).decoration(TextDecoration.ITALIC, false));

            // Lore
            meta.lore(List.of(
                    Component.text("R-CLICK: open team inventory", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("L-CLICK (cursor): put item inside", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.setCustomModelData(900009);

            infinibundle.setItemMeta(meta);
        }

        player.getInventory().addItem(infinibundle);
        //Infinibundle

        if (shulkerColors != null && shulkerColors.length > 0) {
            int pos = 8;
            for (Material shulker : shulkerColors) {
                ZappierGames.getInstance().getLogger().info("Giving shulker box " + shulker + " to " + player.getName());
                player.getInventory().setItem(pos++, new ItemStack(shulker));
            }
        } else {
            ZappierGames.getInstance().getLogger().warning("No shulker boxes given to " + player.getName() + ": shulkerColors is empty or null");
        }

        /*
        for (Map<String, Object> pearl : customPearls) {
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
        }
        */
    }

    public static double getItemValue(String itemName) {
        Material material = Material.getMaterial(itemName);
        if (material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) {
            return 0.0; // Potion scoring handled in calculateInventoryCounts
        }
        return itemValues.getOrDefault(itemName, 0.0);
    }

    public static double getTotalEnchantmentPoints(ItemStack item) {
        ZappierGames.getInstance().getLogger().info("Getting ench info...");
        if (!item.hasItemMeta() || (!item.getItemMeta().hasEnchants() && !(item.getType() == Material.ENCHANTED_BOOK && !((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants().isEmpty()))) {
            return 0;
        }
        double points = 0;
        Map<Enchantment, Integer> enchantments = item.getType() == Material.ENCHANTED_BOOK ? ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants() : item.getEnchantments();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            String enchantName = entry.getKey().getKey().getKey().toUpperCase();
            int level = entry.getValue();
            int multiplier = specialEnchantments.getOrDefault(enchantName, enchantmentPointsPerTier);
            points += level * multiplier;
        }
        ZappierGames.getInstance().getLogger().info("Points: " + points);
        return points;
    }

    private static ChatColor getTeamColor(String teamName) {
        String lower = teamName.toLowerCase(Locale.ENGLISH);
        if (lower.contains("red")) return ChatColor.RED;
        if (lower.contains("blue")) return ChatColor.BLUE;
        if (lower.contains("green")) return ChatColor.GREEN;
        if (lower.contains("yellow")) return ChatColor.YELLOW;
        if (lower.contains("black")) return ChatColor.BLACK;
        if (lower.contains("purple") || lower.contains("magenta")) return ChatColor.LIGHT_PURPLE;
        if (lower.contains("cyan") || lower.contains("aqua")) return ChatColor.AQUA;
        if (lower.contains("orange")) return ChatColor.GOLD;
        if (lower.contains("pink")) return ChatColor.LIGHT_PURPLE; // Closest match
        if (lower.contains("lime")) return ChatColor.GREEN; // Closest match
        if (lower.contains("gray") || lower.contains("grey")) {
            if (lower.contains("light") || lower.contains("silver")) return ChatColor.GRAY;
            return ChatColor.DARK_GRAY;
        }
        if (lower.contains("white")) return ChatColor.WHITE;
        if (lower.contains("brown")) return ChatColor.DARK_RED; // Closest warm brown tone
        if (lower.contains("light blue")) return ChatColor.AQUA;
        // Add more custom mappings here if you have specific team names
        return ChatColor.WHITE; // Default fallback
    }

    private static NamedTextColor chatColorToAdventure(ChatColor chatColor) {
        return switch (chatColor) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE; // Fallback for BOLD, ITALIC, etc.
        };
    }
}