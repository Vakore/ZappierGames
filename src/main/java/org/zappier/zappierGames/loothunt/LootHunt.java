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
        ZappierGames.resetPlayers(false);
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

            Bukkit.broadcast(Component.text(teamName + ": " + String.format("%.1f", totalScore), NamedTextColor.YELLOW));
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        // Generate HTML
        generateResultsHTML(teamItemCounts, teamPlayers, teamStorages);
    }

    private static Map<String, Integer> itemNameToId = null;

    private static int getItemIdByName(String name) {
        if (itemNameToId == null) {
            loadItemNameToIdMap();
        }
        return itemNameToId.getOrDefault(name, -1);
    }

    private static void loadItemNameToIdMap() {
        itemNameToId = new HashMap<>();
        try (InputStream is = LootHunt.class.getClassLoader().getResourceAsStream("items.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString()
                    .replaceAll("(?s)^\\s*mcData\\.items\\s*=\\s*", "")
                    .replace(";", "")
                    .trim();

            // Naive but effective for this format
            Pattern p = Pattern.compile("\"id\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher m = p.matcher(json);
            while (m.find()) {
                try {
                    int id = Integer.parseInt(m.group(1));
                    String name = m.group(2);
                    itemNameToId.put(name, id);
                } catch (NumberFormatException ignored) {}
            }

        } catch (Exception e) {
            ZappierGames.getInstance().getLogger().warning("Could not parse items.txt → " + e.getMessage());
        }
    }

    private static String getItemSpriteBase64() {
        try (InputStream is = LootHunt.class.getClassLoader().getResourceAsStream("itemIconsBase64.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            return reader.lines().collect(Collectors.joining(""));
        } catch (Exception e) {
            ZappierGames.getInstance().getLogger().warning("Failed to load itemIconsBase64.txt: " + e.getMessage());
            return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="; // tiny fallback 1x1
        }
    }

    private static void appendInventorySlot(StringBuilder sb, ItemStack item) {
        sb.append("<div class=\"slot\">");  // slot can stay 16px or 18px with padding

        if (item != null && item.getType() != Material.AIR) {
            String nameLower = item.getType().name().toLowerCase(Locale.ROOT);
            int numericId = getItemIdByName(nameLower);

            if (numericId >= 0 && numericId < 1296) {
                int col = numericId % 36;
                int row = numericId / 36;
                int offsetX = -col * 16;
                int offsetY = -row * 16;

                sb.append("<div class=\"item-sprite\" ")
                        .append("style=\"background-position: ").append(offsetX).append("px ").append(offsetY).append("px;\" ")
                        .append("title=\"").append(escapeHtml(nameLower)).append("\"></div>");

                if (item.getAmount() > 1) {
                    sb.append("<span style=\"position:absolute; bottom:0; right:1px; color:white; text-shadow:1px 1px #000; font-size:9px; font-weight:bold;\">")
                            .append(item.getAmount())
                            .append("</span>");
                }
            } else {
                sb.append("<div style=\"width:16px;height:16px;background:#333;color:#c66;font-size:9px;line-height:16px;text-align:center;\">?</div>");
            }
        }

        sb.append("</div>");
    }

    public static void generateResultsHTML(Map<String, Map<String, Double>> teamItemCounts,
                                           Map<String, List<PlayerResult>> teamPlayers,
                                           Map<String, Map<String, List<ItemEntry>>> teamStorages) {

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File htmlFile = new File(ZappierGames.getInstance().getDataFolder(),
                "loothunt-results-" + timestamp + ".html");

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>Loot Hunt Results - ").append(escapeHtml(timestamp)).append("</title>\n")
                .append("    <style>\n")
                .append("        .item-sprite {\n")
                .append("            width: 16px;\n")
                .append("            height: 16px;\n")
                .append("            background-image: url('").append(getItemSpriteBase64()).append("');\n")
                .append("            background-size: 576px 576px;\n")   // adjust if your sheet is different size
                .append("            image-rendering: pixelated;\n")
                .append("        }\n")
                .append("        body { font-family: Arial, sans-serif; background: #0f0f1a; color: #e0e0ff; margin: 0; padding: 20px; }\n")
                .append("        h1, h2, h3 { text-align: center; color: #ffd700; text-shadow: 0 0 10px #ffaa00; }\n")
                .append("        .team { background: #1a1a2e; border-radius: 10px; padding: 20px; margin: 20px auto; max-width: 1200px; box-shadow: 0 0 20px rgba(100,100,255,0.3); }\n")
                .append("        table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n")
                .append("        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #333366; }\n")
                .append("        th { background: #2a2a4a; }\n")
                .append("        tr:hover { background: #25253f; }\n")
                .append("        .skin-head { width: 80px; height: 80px; image-rendering: pixelated; vertical-align: middle; }\n")
                .append("        .skin-body { width: 120px; height: 180px; image-rendering: pixelated; vertical-align: middle; }\n")
                .append("        .score { font-weight: bold; color: #00ff88; font-size: 1.2em; }\n")
                .append("        .details { color: #aaaaff; font-size: 0.9em; }\n")
                .append("        .collection { cursor: help; color: #55ff55; }\n")
                .append("        .kills { color: #ff5555; }\n")
                .append("        .deaths { color: #ff7777; }\n")
                .append("        .inventory-table { margin-top: 10px; }\n")
                .append("        .item-row { font-size: 0.85em; }\n")
                .append("        .nested { margin-left: 20px; font-style: italic; }\n")
                .append("        .inventory-grid { display: grid; grid-template-columns: repeat(9, 32px); gap: 2px; background: #444; padding: 5px; border: 1px solid #666; }\n")
                .append("        .slot { position: relative; width: 32px; height: 32px; background: #888; }\n")
                .append("        .slot img { width: 32px; height: 32px; }\n")
                .append("        .slot span { position: absolute; bottom: 0; right: 0; color: white; text-shadow: 1px 1px black; font-size: 0.8em; }\n")
                .append("        .armor-slots { display: grid; grid-template-columns: 32px; gap: 2px; }\n")
                .append("        .offhand-slot { width: 32px; height: 32px; }\n")
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <h1>Loot Hunt Results</h1>\n")
                .append("    <p style=\"text-align:center\">Game finished at ").append(escapeHtml(timestamp)).append("</p>\n");

        List<Map.Entry<String, Map<String, Double>>> sortedTeams = teamItemCounts.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().values().stream().mapToDouble(Double::doubleValue).sum(),
                        a.getValue().values().stream().mapToDouble(Double::doubleValue).sum()
                ))
                .toList();

        for (var teamEntry : sortedTeams) {
            String teamName = teamEntry.getKey();
            Map<String, Double> items = teamEntry.getValue();
            double totalScore = calculateTotalScoreWithBonuses(items, teamName);

            sb.append("    <div class=\"team\">\n")
                    .append("        <h2>").append(escapeHtml(teamName))
                    .append(" – <span class=\"score\">").append(String.format("%.1f", totalScore)).append("</span></h2>\n");

            // Collections
            sb.append("        <h3>Collections</h3>\n");
            for (Collection coll : collections.values()) {
                long count = coll.items.stream().filter(items::containsKey).count();
                String status = (coll.type.equals("complete") && count >= coll.items.size())
                        ? "COMPLETE" : count + "/" + coll.items.size();
                int bonus = calculateCollectionBonus(coll, (int) count);

                sb.append("        <p class=\"collection\" title=\"")
                        .append(escapeHtml(buildCollectionTooltip(coll, items)))
                        .append("\">")
                        .append(escapeHtml(coll.name)).append(": ").append(status)
                        .append(" (+").append(bonus).append(" bonus)</p>\n");
            }

            // Players
            sb.append("        <h3>Players</h3>\n")
                    .append("        <table><tr><th>Player</th><th>Skin</th><th>Kills / Deaths</th><th>Personal Score</th><th>Inventory Visual</th><th>Inventory List</th></tr>\n");

            List<PlayerResult> players = teamPlayers.getOrDefault(teamName, new ArrayList<>());
            for (PlayerResult pr : players) {
                String headUrl = "https://visage.surgeplay.com/head/128/" + pr.uuid;
                String bodyUrl = "https://visage.surgeplay.com/full/384/" + pr.uuid + "?y=15&p=-18";

                sb.append("        <tr>")
                        .append("<td>").append(escapeHtml(pr.name)).append("</td>")
                        .append("<td><img class=\"skin-head\" src=\"").append(headUrl).append("\" alt=\"Head\"> ")
                        .append("<img class=\"skin-body\" src=\"").append(bodyUrl).append("\" alt=\"Body\"></td>")
                        .append("<td><span class=\"kills\">").append(pr.kills).append("</span> / ")
                        .append("<span class=\"deaths\">").append(pr.deaths).append("</span></td>")
                        .append("<td class=\"score\">").append(String.format("%.1f", pr.personalScore)).append("</td>")
                        .append("<td>");

                // Inventory visual - main inventory grid (9x3 upper + hotbar)
                sb.append("<div class=\"inventory-grid\">");
                for (int i = 9; i < 36; i++) { // Upper inventory
                    appendInventorySlot(sb, pr.inventoryContents[i]);
                }
                sb.append("</div>");

                sb.append("<div class=\"inventory-grid\" style=\"margin-top: 5px;\">"); // Hotbar
                for (int i = 0; i < 9; i++) {
                    appendInventorySlot(sb, pr.inventoryContents[i]);
                }
                sb.append("</div>");

                // Armor slots
                sb.append("<div class=\"armor-slots\" style=\"margin-top: 10px;\">");
                appendInventorySlot(sb, pr.inventoryContents[39]); // Helmet
                appendInventorySlot(sb, pr.inventoryContents[38]); // Chestplate
                appendInventorySlot(sb, pr.inventoryContents[37]); // Leggings
                appendInventorySlot(sb, pr.inventoryContents[36]); // Boots
                sb.append("</div>");

                // Offhand
                sb.append("<div class=\"offhand-slot\" style=\"margin-top: 5px;\">");
                appendInventorySlot(sb, pr.inventoryContents[40]);
                sb.append("</div>");

                sb.append("</td><td>");

                // Inventory list (alphabetical)
                sb.append("<table class=\"inventory-table\">");
                List<String> sortedPersonal = new ArrayList<>(pr.personalInventory.keySet());
                Collections.sort(sortedPersonal);
                for (String itemId : sortedPersonal) {
                    List<ItemEntry> entries = pr.personalInventory.get(itemId);
                    int totalQty = entries.stream().mapToInt(e -> e.quantity).sum();
                    double totalPts = entries.stream().mapToDouble(e -> e.points).sum();
                    String sources = entries.stream().map(e -> e.source).distinct().collect(Collectors.joining(", "));
                    sb.append("<tr class=\"item-row\"><td>").append(escapeHtml(itemId)).append("</td><td>x").append(totalQty)
                            .append("</td><td>").append(String.format("%.1f", totalPts)).append(" pts</td><td title=\"").append(escapeHtml(sources)).append("\">Sources</td></tr>");
                    // For nested, if source has >, it's nested
                }
                sb.append("</table></td></tr>\n");
            }
            sb.append("        </table>\n");

            // Team Storage (alphabetical, with sources)
            sb.append("        <h3>Team Infinibundle Storage</h3>\n")
                    .append("        <table><tr><th>Item</th><th>Quantity</th><th>Points</th><th>Sources</th></tr>\n");
            Map<String, List<ItemEntry>> storage = teamStorages.getOrDefault(teamName, new HashMap<>());
            List<String> sortedStorage = new ArrayList<>(storage.keySet());
            Collections.sort(sortedStorage);
            for (String itemId : sortedStorage) {
                List<ItemEntry> entries = storage.get(itemId);
                int totalQty = entries.stream().mapToInt(e -> e.quantity).sum();
                double totalPts = entries.stream().mapToDouble(e -> e.points).sum();
                String sources = entries.stream().map(e -> e.source).distinct().collect(Collectors.joining("<br>"));
                sb.append("        <tr><td>").append(escapeHtml(itemId))
                        .append("</td><td>x").append(totalQty)
                        .append("</td><td>").append(String.format("%.1f", totalPts))
                        .append("</td><td>").append(sources).append("</td></tr>\n");
            }
            sb.append("        </table>\n");

            sb.append("    </div>\n");
        }

        sb.append("</body>\n")
                .append("</html>\n");

        // Write file
        try (FileWriter writer = new FileWriter(htmlFile)) {
            writer.write(sb.toString());
            ZappierGames.getInstance().getLogger().info("Loot Hunt results saved to: " + htmlFile.getAbsolutePath());
        } catch (IOException e) {
            ZappierGames.getInstance().getLogger().severe("Failed to save results HTML: " + e.getMessage());
        }

        // Broadcast clickable link
        String serverIp = Bukkit.getIp();
        if (serverIp.isEmpty()) serverIp = "localhost";
        int webPort = 8081; // Configurable?
        String url = "http://" + serverIp + ":" + webPort + "/" + htmlFile.getName();

        Component msg = Component.text("Loot Hunt results generated! ", NamedTextColor.GREEN)
                .append(Component.text("Click to view detailed scoreboard", NamedTextColor.YELLOW)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.AQUA))));

        Bukkit.broadcast(msg);


        ZappierGames.getInstance().startResultsWebServer(htmlFile.getName());

        ZappierGames.getInstance().getLogger().info("Results available at: " + url);


    }

    private static double calculateTotalScoreWithBonuses(Map<String, Double> items, String teamName) {
        double base = items.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Collection coll : collections.values()) {
            int count = (int) coll.items.stream().filter(items::containsKey).count();
            base += calculateCollectionBonus(coll, count);
        }
        return base;
    }

    private static int calculateCollectionBonus(Collection coll, int count) {
        if ("progressive".equals(coll.type)) {
            if (!coll.progressiveScores.isEmpty() && count > 0) {
                return coll.progressiveScores.get(Math.min(count - 1, coll.progressiveScores.size() - 1));
            }
        } else if (count >= coll.items.size()) {
            return coll.completeBonus;
        }
        return 0;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String buildCollectionTooltip(Collection coll, Map<String, Double> items) {
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

    // Add this to ZappierGames.java onEnable()
    /*
    private HttpServer webServer;
    public void onEnable() {
        // ... existing code
        try {
            webServer = HttpServer.create(new InetSocketAddress(8080), 0);
            webServer.createContext("/", new ResultsHandler(this.getDataFolder()));
            webServer.setExecutor(null);
            webServer.start();
            getLogger().info("Web server started on port 8080");
        } catch (IOException e) {
            getLogger().severe("Failed to start web server: " + e.getMessage());
        }
    }

    public void onDisable() {
        if (webServer != null) {
            webServer.stop(0);
        }
    }

    static class ResultsHandler implements HttpHandler {
        private final File dataFolder;

        ResultsHandler(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/")) path = path.substring(1);
            File file = new File(dataFolder, path);
            if (file.exists() && file.getName().startsWith("loothunt-results-") && file.getName().endsWith(".html")) {
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, file.length());
                java.nio.file.Files.copy(file.toPath(), exchange.getResponseBody());
                exchange.getResponseBody().close();
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.getResponseBody().close();
            }
        }
    }
    */
}