package org.zappier.zappierGames;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LootHunt {
    public static int[] doorScores = {0,1,5,10,15,25,50,100,150,200,250,400,700,1000,1000,1000,1000,1000};
    public static String[] doorNames = {
            "OAK_DOOR",
            "SPRUCE_DOOR",
            "BIRCH_DOOR",
            "JUNGLE_DOOR",
            "ACACIA_DOOR",
            "DARK_OAK_DOOR",
            "CRIMSON_DOOR",
            "WARPED_DOOR",
            "MANGROVE_DOOR",
            "BAMBOO_DOOR",
            "IRON_DOOR",
            "CHERRY_DOOR",
            "COPPER_DOOR"};
    public static String[] workStationNames = {
            "BLAST_FURNACE",
            "SMOKER",
            "CARTOGRAPHY_TABLE",
            "BREWING_STAND",
            "COMPOSTER",
            "BARREL",
            "FLETCHING_TABLE",
            "CAULDRON",
            "LECTERN",
            "STONECUTTER",
            "LOOM",
            "SMITHING_TABLE",
            "GRINDSTONE"
    };
    public static String[] dyeNames = {
            "WHITE_DYE",
            "LIGHT_GRAY_DYE",
            "GRAY_DYE",
            "BLACK_DYE",
            "BROWN_DYE",
            "RED_DYE",
            "ORANGE_DYE",
            "YELLOW_DYE",
            "LIME_DYE",
            "GREEN_DYE",
            "CYAN_DYE",
            "LIGHT_BLUE_DYE",
            "BLUE_DYE",
            "PURPLE_DYE",
            "MAGENTA_DYE",
            "PINK_DYE"
    };

    public static int startTimer = 120*2;
    public static Map<String, String> playerTeams = new HashMap<String, String>();
    public static Map<String, Double> itemValues;
    public static Map<String, Map<String, Integer>> teamItemCounts = new HashMap<String, Map<String, Integer>>();
    public static Map<String, Integer> playerKillCounts = new HashMap<String, Integer>();
    public static Map<String, Integer> playerDeathCounts = new HashMap<String, Integer>();


    public static void start(double duration) {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Keep inventory set to " + true + " across all dimensions");
        teamItemCounts.clear();
        playerKillCounts.clear();
        playerDeathCounts.clear();
        startTimer = (int)(duration * 60 * 2);
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.globalBossBar.setVisible(true);
        ZappierGames.globalBossBar.setStyle(BarStyle.SOLID);
        ZappierGames.globalBossBar.setColor(BarColor.YELLOW);
        ZappierGames.globalBossBar.setProgress(1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            ZappierGames.globalBossBar.addPlayer(p);
            p.getInventory().clear();
            LootHunt.giveStartingItems(p);
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

        ZappierGames.timer = startTimer;
    }

    public static void endGame() {
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.gameMode = -1;


        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.YELLOW + "Game Finished!", ChatColor.YELLOW + "");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            Map<String, Integer> inventoryCounts = calculateInventoryCounts(p);


            String scoreDetails = inventoryCounts.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
            //Bukkit.broadcastMessage(ChatColor.YELLOW + p.getName() + " - " + scoreDetails);

            //double disScore = 0;
            String teamName = playerTeams.getOrDefault(p.getName().toUpperCase(), "(Solo) " + p.getName());

            Map<String, Integer> disTeam = teamItemCounts.get(teamName);
            if (disTeam == null) {
                teamItemCounts.put(teamName, new HashMap<String, Integer>());
                disTeam = teamItemCounts.get(teamName);
            }

            for (Map.Entry e : inventoryCounts.entrySet()) {
                disTeam.put((String)e.getKey(), disTeam.getOrDefault(e.getKey(), 0) + ((int)e.getValue()));
            }

            p.getInventory().clear();
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");
        Bukkit.broadcastMessage(ChatColor.GREEN + "        RESULTS        ");
        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");

        for (Map.Entry d : teamItemCounts.entrySet()) {
            int disScore = 0;
            int doorsCount = 0;
            int workStationCount = 0;
            int dyeCount = 0;
            for (Map.Entry e : ((HashMap<String, Integer>)d.getValue()).entrySet()) {
                String itemName = (String)e.getKey();
                disScore += ((int)e.getValue()) * getItemValue(itemName);
                for (int i = 0; i < doorNames.length; i++) {
                    if (doorNames[i].equals(itemName)) {
                        i = doorNames.length;
                        doorsCount++;
                    }
                }
                for (int i = 0; i < workStationNames.length; i++) {
                    if (workStationNames[i].equals(itemName)) {
                        i = workStationNames.length;
                        workStationCount++;
                    }
                }
                for (int i = 0; i < dyeNames.length; i++) {
                    if (dyeNames[i].equals(itemName)) {
                        i = dyeNames.length;
                        dyeCount++;
                    }
                }
            }

            if (workStationCount >= workStationNames.length) {
                disScore += 250;
            }

            if (dyeCount >= dyeNames.length) {
                disScore += 300;
            }

            if (doorsCount <= doorScores.length) {
                disScore += doorScores[doorsCount];
            } else {
                disScore += 1001;
            }

            for (Map.Entry e : playerKillCounts.entrySet()) {
                String killerName = ((String)e.getKey()).toUpperCase();
                if (playerTeams.getOrDefault(killerName, "(Solo) " + killerName).equals((String)d.getKey())) {
                    int killCount = (int)e.getValue();
                    int oldKillCount = killCount;
                    int killValue = 50;
                    int addScore = 0;

                    //The kill value is divided by two for each additional kill,
                    //as to reward killing without rewarding murderous rampages.
                    while (killCount > 0 && killValue > 1) {
                        addScore += killValue;
                        killCount--;
                        killValue /= 2;
                    }
                    Bukkit.broadcastMessage(killerName + " got " + oldKillCount + " kills, earning " + addScore + " points for team " + ((String)d.getKey()));
                    disScore += addScore;
                }
            }

            for (Map.Entry e : playerDeathCounts.entrySet()) {
                String killedName = ((String)e.getKey()).toUpperCase();
                if (playerTeams.getOrDefault(killedName, "(Solo) " + killedName).equals((String)d.getKey())) {
                    int deathCount = (int)e.getValue();
                    int oldDeathCount = deathCount;
                    int deathValue = 25;
                    int addScore = 0;

                    //The kill value is divided by two for each additional kill,
                    //as to reward killing without rewarding murderous rampages.
                    while (deathCount > 0 && deathValue > 1) {
                        addScore += deathValue;
                        deathCount--;
                        deathValue /= 2;
                    }
                    Bukkit.broadcastMessage(killedName + " died " + oldDeathCount + " times, losing " + addScore + " points for team " + ((String)d.getKey()));
                    disScore -= addScore;
                }
            }

            Bukkit.broadcastMessage(ChatColor.YELLOW + ((String)d.getKey()) + ": " + disScore);
            Bukkit.broadcastMessage(ChatColor.GRAY + "  Doors: " + doorsCount + "/" + doorNames.length);
            Bukkit.broadcastMessage(ChatColor.GRAY + "  Workstations: " + workStationCount + "/" + workStationNames.length);
            Bukkit.broadcastMessage(ChatColor.GRAY + "  Dyes: " + dyeCount + "/" + dyeNames.length);
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");

        teamItemCounts.clear();
    }

    public static Map<String, Integer> calculateInventoryCounts(Player player) {
        Map<String, Integer> scoreMap = new HashMap<>();

        for (ItemStack item : player.getInventory()) {
            if (item == null || item.getType() == Material.AIR) {continue;}
            String itemId = item.getType().toString();
            if (item.getType().name().endsWith("SHULKER_BOX")) {
                player.sendMessage("Shulker box found");
                if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta meta) {
                    BlockState state = meta.getBlockState();
                    if (state instanceof ShulkerBox shulkerBox) {
                        List<ItemStack> items = new ArrayList<>();
                        for (ItemStack sItem : shulkerBox.getInventory().getContents()) {
                            if (sItem == null || sItem.getType() == Material.AIR) {continue;}
                            String sItemId = sItem.getType().toString();
                            scoreMap.put(sItemId, scoreMap.getOrDefault(sItemId, 0) + sItem.getAmount());
                            player.sendMessage(sItemId);
                        }
                    }
                }
                //scoreMap.put(itemId, scoreMap.getOrDefault(itemId, 0) + item.getAmount());
            }
            scoreMap.put(itemId, scoreMap.getOrDefault(itemId, 0) + item.getAmount());
        }

        return scoreMap;
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

        int hours = ZappierGames.timer / 7200;
        int minutes = ((ZappierGames.timer % 7200) / (60*2));
        int seconds = (ZappierGames.timer / 2) % (60);
        ZappierGames.globalBossBar.setTitle("Time Left: " +
                (hours < 10 ? "0" : "") + hours + ":" +
                (minutes < 10 ? "0" : "") + minutes + ":" +
                (seconds < 10 ? "0" : "") + seconds
        );

        ZappierGames.globalBossBar.setProgress( ((double)ZappierGames.timer / (double)(startTimer)) );

        ZappierGames.timer--;
    }

    public static void giveStartingItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_SHOVEL));
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE));

        Material[] shulkerColors = {
                Material.BLUE_SHULKER_BOX,
                Material.RED_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX, Material.BLACK_SHULKER_BOX
        };

        int pos = 8;
        for (Material shulker : shulkerColors) {
            player.getInventory().setItem(pos++, new ItemStack(shulker));
        }
    }

    public static double getItemValue(String itemName) {
        return itemValues.getOrDefault(itemName, 0.0); // Default to 0 if not specified
    }

    public static int getTotalEnchantments(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasEnchants()) {
            return 0;
        }
        int totalEnchantments = item.getEnchantments().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return totalEnchantments;
    }
}
