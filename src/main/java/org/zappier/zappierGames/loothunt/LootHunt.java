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
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.zappier.zappierGames.ZappierGames;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.zappier.zappierGames.loothunt.LootHuntScorePage.generateResultsHTML;

public class LootHunt {
    public static boolean noPvP = false;
    public static double startTimer;
    private static Material[] shulkerColors;
    public static Map<String, Double> itemValues = new HashMap<>();
    public static Map<String, Double> potionValues = new HashMap<>();
    public static Map<String, Integer> playerKillCounts = new HashMap<>();
    public static Map<String, Integer> playerDeathCounts = new HashMap<>();
    private static int baseKillPoints;
    private static int baseDeathPoints;
    private static int pointsReductionFactor;
    private static int enchantmentPointsPerTier;
    private static Map<String, Integer> specialEnchantments = new HashMap<>();
    private static List<Map<String, Object>> customPearls = new ArrayList<>();
    public static boolean paused = false;

    public static class Collection {
        public String name;
        public String type; // "progressive" or "complete"
        public List<String> items = new ArrayList<>();
        List<Integer> progressiveScores = new ArrayList<>();
        int completeBonus;
    }

    public static Map<String, Collection> collections = new HashMap<>();

    public static class ItemEntry {
        String itemId;
        public int quantity;
        public double points;
        String source;

        public ItemEntry(String itemId, int quantity, double points, String source) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.points = points;
            this.source = source;
        }
    }

    public static void loadConfig(FileConfiguration config) {
        ZappierGames plugin = ZappierGames.getInstance();
        startTimer = config.getDouble("start-timer", 240.0);

        List<String> shulkerColorNames = config.getStringList("shulker-colors");
        if (shulkerColorNames.isEmpty()) {
            shulkerColorNames = List.of("BLUE_SHULKER_BOX", "RED_SHULKER_BOX", "GREEN_SHULKER_BOX", "YELLOW_SHULKER_BOX", "BLACK_SHULKER_BOX");
            //plugin.getLogger().warning("shulker-colors not found in config.yml, using default values");
        }
        List<Material> validShulkerColors = new ArrayList<>();
        for (String name : shulkerColorNames) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                validShulkerColors.add(material);
            } else {
                // plugin.getLogger().warning("Invalid material in shulker-colors: " + name);
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
            //plugin.getLogger().warning("item-values not found in config.yml, no item scoring available");
        }

        ConfigurationSection potionSection = config.getConfigurationSection("potion-values");
        if (potionSection != null) {
            potionValues.clear();
            for (String key : potionSection.getKeys(false)) {
                potionValues.put(key.toUpperCase(), potionSection.getDouble(key));
            }
        } else {
            // Keep your existing default potion values here if desired
            //plugin.getLogger().warning("potion-values not found in config.yml, using defaults from code (or zero)");
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
        }

        ConfigurationSection enchantSection = config.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            enchantmentPointsPerTier = enchantSection.getInt("points-per-tier", 4);
            ConfigurationSection specialSection = enchantSection.getConfigurationSection("special-enchantments");
            if (specialSection != null) {
                specialEnchantments.clear();
                for (String key : specialSection.getKeys(false)) {
                    specialEnchantments.put(key.toUpperCase(), specialSection.getInt(key));
                }
            }
        } else {
            enchantmentPointsPerTier = 4;
            specialEnchantments.put("MENDING", 15);
            specialEnchantments.put("FROST_WALKER", 15);
            specialEnchantments.put("WIND_BURST", 100);
        }

        customPearls.clear();
        List<Map<?, ?>> pearlList = config.getMapList("custom-pearls");
        if (!pearlList.isEmpty()) {
            for (Map<?, ?> pearl : pearlList) {
                Map<String, Object> pearlData = new HashMap<>();
                pearlData.put("sbitem", pearl.get("sbitem"));
                pearlData.put("custom-model-data", pearl.get("custom-model-data"));
                pearlData.put("display-name", pearl.get("display-name"));
                pearlData.put("amount", pearl.get("amount"));
                customPearls.add(pearlData);
            }
        }

        // Load collections
        ConfigurationSection collectionsSection = config.getConfigurationSection("collections");
        if (collectionsSection != null) {
            collections.clear();
            for (String key : collectionsSection.getKeys(false)) {
                ConfigurationSection collSec = collectionsSection.getConfigurationSection(key);
                if (collSec == null) continue;

                Collection coll = new Collection();
                coll.name = collSec.getString("name", key);
                coll.type = collSec.getString("type", "complete").toLowerCase();
                coll.items = collSec.getStringList("items");

                if ("progressive".equals(coll.type)) {
                    coll.progressiveScores = collSec.getIntegerList("scores");
                } else {
                    coll.completeBonus = collSec.getInt("bonus", 0);
                }

                collections.put(key, coll);
            }
        } else {
            //plugin.getLogger().warning("collections section not found in config.yml");
        }
    }

    public static void start(double duration) {
        InfinibundleListener.clearAll();
        LootHunt.paused = false;
        ZappierGames.resetPlayers(false, true);
        ZappierGames.noPvP = noPvP;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(0);
        }
        Bukkit.broadcast(Component.text("Keep inventory set to true across all dimensions", NamedTextColor.YELLOW));
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

    public static class PlayerResult {
        String name;
        String uuid;
        int kills;
        int deaths;
        Map<String, List<ItemEntry>> personalInventory = new HashMap<>();
        double personalScore;
        ItemStack[] inventoryContents; // Full inventory for visual display
    }

    public static void endGame() {
        if (noPvP) {playerKillCounts.clear();}
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.gameMode = -1;

        Map<String, Map<String, Double>> teamItemCounts = new HashMap<>();
        Map<String, List<PlayerResult>> teamPlayers = new HashMap<>();
        Map<String, Map<String, List<ItemEntry>>> teamStorages = new HashMap<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.YELLOW + "Game Finished!", "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            String teamName = p.getScoreboard().getEntryTeam(p.getName()) != null
                    ? p.getScoreboard().getEntryTeam(p.getName()).getName()
                    : "(Solo) " + p.getName();

            // Personal inventory
            Map<String, List<ItemEntry>> personalInv = new HashMap<>();
            processContainer(personalInv, Arrays.asList(p.getInventory().getContents()), "Inventory");

            // Team storage
            List<ItemStack> teamStorageItems = InfinibundleListener.getTeamStorage(teamName);
            Map<String, List<ItemEntry>> teamStorage = new HashMap<>();
            processContainer(teamStorage, teamStorageItems, "Team Storage");

            // Combined for team counts
            Map<String, List<ItemEntry>> combined = new HashMap<>(personalInv);
            teamStorage.forEach((k, v) -> combined.merge(k, v, (a, b) -> {
                a.addAll(b);
                return a;
            }));

            Map<String, Double> teamScores = teamItemCounts.computeIfAbsent(teamName, k -> new HashMap<>());
            for (Map.Entry<String, List<ItemEntry>> entry : combined.entrySet()) {
                double totalPoints = entry.getValue().stream().mapToDouble(e -> e.points).sum();
                teamScores.merge(entry.getKey(), totalPoints, Double::sum);
            }

            // Kills
            int killCount = playerKillCounts.getOrDefault(p.getName().toUpperCase(), 0);
            int oldKillCount = killCount;
            double killValue = baseKillPoints;
            double addScore = 0.0;
            while (killCount > 0 && killValue > 1) {
                addScore += killValue;
                killCount--;
                killValue /= pointsReductionFactor;
            }
            Bukkit.broadcast(Component.text(p.getName() + " got " + oldKillCount + " kills, earning " + addScore + " points for team " + teamName, NamedTextColor.YELLOW));
            teamScores.put("kills", teamScores.getOrDefault("kills", 0.0) + addScore);

            // Deaths
            int deathCount = playerDeathCounts.getOrDefault(p.getName().toUpperCase(), 0);
            int oldDeathCount = deathCount;
            double deathValue = baseDeathPoints;
            addScore = 0.0;
            while (deathCount > 0 && deathValue > 1) {
                addScore -= deathValue;
                deathCount--;
                deathValue /= pointsReductionFactor;
            }
            Bukkit.broadcast(Component.text(p.getName() + " got " + oldDeathCount + " deaths, losing " + Math.abs(addScore) + " points for team " + teamName, NamedTextColor.YELLOW));
            teamScores.put("deaths", teamScores.getOrDefault("deaths", 0.0) + addScore);

            // Store player result
            PlayerResult pr = new PlayerResult();
            pr.name = p.getName();
            pr.uuid = p.getUniqueId().toString();
            pr.kills = oldKillCount;
            pr.deaths = oldDeathCount;
            pr.personalInventory = personalInv;
            pr.personalScore = personalInv.values().stream().flatMap(List::stream).mapToDouble(e -> e.points).sum();
            pr.inventoryContents = p.getInventory().getContents(); // For visual

            teamPlayers.computeIfAbsent(teamName, k -> new ArrayList<>()).add(pr);
            teamStorages.put(teamName, teamStorage);
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("        RESULTS        ", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        for (Map.Entry<String, Map<String, Double>> teamEntry : teamItemCounts.entrySet()) {
            String teamName = teamEntry.getKey();
            Map<String, Double> items = teamEntry.getValue();
            double totalScore = items.values().stream().mapToDouble(Double::doubleValue).sum();

            Bukkit.broadcast(Component.text(teamName + ": " + String.format("%.1f", totalScore), NamedTextColor.YELLOW));
            // Collection bonuses
            for (Collection coll : collections.values()) {
                long uniqueCollected = coll.items.stream()
                        .filter(items::containsKey)
                        .count();

                if ("progressive".equals(coll.type)) {
                    int count = (int) uniqueCollected;
                    if (!coll.progressiveScores.isEmpty()) {
                        int bonus = 0;
                        if (count > 0) {
                            bonus = coll.progressiveScores.get(Math.min(count - 1, coll.progressiveScores.size() - 1));
                        }
                        totalScore += bonus;

                        Component hover = Component.text("Collected " + coll.name + ":", NamedTextColor.AQUA)
                                .append(Component.newline()).append(Component.newline());
                        for (String item : coll.items) {
                            boolean has = items.containsKey(item);
                            hover = hover.append(Component.text((has ? "✓ " : "✗ ") + item, has ? NamedTextColor.GREEN : NamedTextColor.RED))
                                    .append(Component.newline());
                        }

                        Bukkit.broadcast(Component.text("  " + coll.name + ": " + uniqueCollected + "/" + coll.items.size(), NamedTextColor.GRAY)
                                .append(Component.text(" (+" + bonus + " bonus)", NamedTextColor.GREEN))
                                .hoverEvent(HoverEvent.showText(hover)));
                    }
                } else { // complete
                    if (uniqueCollected >= coll.items.size()) {
                        totalScore += coll.completeBonus;
                        Bukkit.broadcast(Component.text("  " + coll.name + ": COMPLETE (+" + coll.completeBonus + " bonus)", NamedTextColor.GREEN));
                    } else {
                        Component hover = Component.text("Collected " + coll.name + ":", NamedTextColor.AQUA)
                                .append(Component.newline()).append(Component.newline());
                        for (String item : coll.items) {
                            boolean has = items.containsKey(item);
                            hover = hover.append(Component.text((has ? "✓ " : "✗ ") + item, has ? NamedTextColor.GREEN : NamedTextColor.RED))
                                    .append(Component.newline());
                        }

                        Bukkit.broadcast(Component.text("  " + coll.name + ": " + uniqueCollected + "/" + coll.items.size(), NamedTextColor.GRAY)
                                .hoverEvent(HoverEvent.showText(hover)));
                    }
                }
            }
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        // Generate HTML
        generateResultsHTML(teamItemCounts, teamPlayers, teamStorages);
    }

    public static String buildCollectionTooltip(Collection coll, Map<String, Double> items) {
        StringBuilder tip = new StringBuilder(coll.name + ":\n\n");
        for (String item : coll.items) {
            boolean has = items.containsKey(item);
            tip.append(has ? "✓ " : "✗ ").append(item).append("\n");
        }
        return tip.toString();
    }

    private static void processContainer(Map<String, List<ItemEntry>> scoreMap, Iterable<ItemStack> items, String sourcePrefix) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            String itemId = item.getType().toString();
            double baseValue = itemValues.getOrDefault(itemId, 0.0);
            int amount = item.getAmount();

            // Potions
            if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType pt = potionMeta.getBasePotionType();
                    String prefix = item.getType() == Material.SPLASH_POTION ? "SPLASH_" :
                            item.getType() == Material.LINGERING_POTION ? "LINGERING_" : "";
                    String key = prefix + (pt != null ? pt.name() : "WATER");
                    baseValue = potionValues.getOrDefault(key, 0.0);
                }
            }

            // Enchantments
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasEnchants() || (item.getType() == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants())) {
                    baseValue += getTotalEnchantmentPoints(item);
                }
            }

            // Tiny default for collection items
            boolean isCollectionItem = collections.values().stream().anyMatch(c -> c.items.contains(itemId));
            if (isCollectionItem && baseValue == 0.0) {
                baseValue = 0.001;
            }

            double points = baseValue * amount;
            scoreMap.computeIfAbsent(itemId, k -> new ArrayList<>())
                    .add(new ItemEntry(itemId, amount, points, sourcePrefix));

            // Recurse into shulker boxes
            if (item.getType().name().endsWith("_SHULKER_BOX")) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
                    BlockState bs = bsm.getBlockState();
                    if (bs instanceof ShulkerBox shulker) {
                        processContainer(scoreMap, Arrays.asList(shulker.getInventory().getContents()), sourcePrefix + " > Shulker");
                    }
                }
            }

            // Recurse into bundles
            if (item.getType() == Material.BUNDLE) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    processContainer(scoreMap, bundleMeta.getItems(), sourcePrefix + " > Bundle");
                }
            }
        }
    }

    public static Map<String, List<ItemEntry>> calculateInventoryCounts(Player player) {
        Map<String, List<ItemEntry>> scoreMap = new HashMap<>();

        // Player inventory
        processContainer(scoreMap, Arrays.asList(player.getInventory().getContents()), "Inventory");

        // Team infinibundle storage
        String teamName = player.getScoreboard().getEntryTeam(player.getName()) != null
                ? player.getScoreboard().getEntryTeam(player.getName()).getName()
                : "(Solo) " + player.getName();

        List<ItemStack> teamStorage = InfinibundleListener.getTeamStorage(teamName);
        processContainer(scoreMap, teamStorage, "Team Storage");

        return scoreMap;
    }

    public static double getTotalEnchantmentPoints(ItemStack item) {
        if (!item.hasItemMeta()) return 0.0;

        Map<Enchantment, Integer> enchants = item.getType() == Material.ENCHANTED_BOOK
                ? ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants()
                : item.getEnchantments();

        double points = 0;
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            String name = e.getKey().getKey().getKey().toUpperCase();
            int level = e.getValue();
            int multiplier = specialEnchantments.getOrDefault(name, enchantmentPointsPerTier);
            points += level * multiplier;
        }
        return points;
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