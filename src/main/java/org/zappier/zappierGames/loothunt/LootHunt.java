package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
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
    // Point values for "special" item variants that share a Material with a common item but are
    // distinguished by rarity/display name (e.g. Ominous Banner vs. a plain banner). Keyed by the
    // synthetic ID returned from getSpecialItemId(), e.g. "OMINOUS_BANNER", "EXPLORER_MAP".
    public static Map<String, Double> specialItemValues = new HashMap<>();
    public static Map<String, Integer> playerKillCounts = new HashMap<>();
    public static Map<String, Integer> playerDeathCounts = new HashMap<>();
    private static int baseKillPoints;
    private static int baseDeathPoints;
    private static int pointsReductionFactor;
    private static int enchantmentPointsPerTier;
    private static Map<String, Integer> specialEnchantments = new HashMap<>();
    private static List<Map<String, Object>> customPearls = new ArrayList<>();
    public static boolean paused = false;
    private static boolean wasPausedLastTick = false;
    // Snapshot of each player's active potion effects at the moment pause was toggled on,
    // re-applied every tick while paused so effect durations don't tick down (freezing tick
    // manager alone doesn't stop potion effect duration from decrementing).
    private static final Map<UUID, List<org.bukkit.potion.PotionEffect>> pausedPotionEffects = new HashMap<>();
    public static Map<String, Integer> bundleSlots = new HashMap<>();
    public static Map<String, Integer> lastPages = new HashMap<>();

    // === Score history tracking ===
    public static class ScoreSnapshot {
        public final long tick;              // seconds elapsed since loothunt start (for graphing)
        public final double score;
        public final List<String> biomes;      // unique biomes visited since the previous snapshot
        public final List<String> structures;  // unique structures visited since the previous snapshot
        public final double x;               // player position at time of snapshot (for position chart)
        public final double z;
        public final String dimension;       // world name the player was in at time of snapshot

        public ScoreSnapshot(long tick, double score, List<String> biomes, List<String> structures, double x, double z, String dimension) {
            this.tick = tick;
            this.score = score;
            this.biomes = biomes != null ? biomes : Collections.emptyList();
            this.structures = structures != null ? structures : Collections.emptyList();
            this.x = x;
            this.z = z;
            this.dimension = dimension != null ? dimension : "unknown";
        }
    }

    // keyed by player name (matches playerKillCounts/playerDeathCounts convention)
    public static final Map<String, List<ScoreSnapshot>> scoreHistory = new HashMap<>();

    // Biomes/structures a player has visited since the last score snapshot was recorded. Sampled
    // every MICRO_SAMPLE_INTERVAL_TICKS and folded into (then cleared from) the next ScoreSnapshot.
    private static final Map<String, Set<String>> visitedBiomes = new HashMap<>();
    private static final Map<String, Set<String>> visitedStructures = new HashMap<>();

    private static int scoreHistoryTickCounter = 0;
    private static final int SNAPSHOT_INTERVAL_TICKS = 600; // 30 seconds @ 20 tps

    private static int microSampleTickCounter = 0;
    private static final int MICRO_SAMPLE_INTERVAL_TICKS = 100; // 5 seconds @ 20 tps

    public static class Collection {
        public String name;
        public String type; // "progressive" or "complete"
        public List<List<String>> itemGroups = new ArrayList<>();
        List<Integer> progressiveScores = new ArrayList<>();
        int completeBonus;
        // If true, this collection is a "quest" collection: it's hidden from the final
        // results (broadcast + HTML report) for a team unless that team fully completed it.
        public boolean quest = false;
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

    // === Spectator live scoreboard/tab display ===
    public static final Map<UUID, org.bukkit.scoreboard.Scoreboard> spectatorBoards = new HashMap<>();

    private static final int SPECTATOR_DISPLAY_INTERVAL_TICKS = 20; // 1 second
    private static final int SIDEBAR_MAX_ENTRIES = 5;
    private static int spectatorDisplayTickCounter = 0;
    public static double calculateTotalScore(Player targetPlayer) {
        Map<String, List<ItemEntry>> playerItems = calculateInventoryCounts(targetPlayer);
        if (playerItems == null || playerItems.isEmpty()) return 0.0;

        double totalScore = 0.0;
        for (List<ItemEntry> items : playerItems.values()) {
            for (ItemEntry item : items) {
                totalScore += item.points;
            }
        }
        // Collection bonuses (progressive/complete) count toward live score displays and the score graph too
        totalScore += calculateCollectionBonus(playerItems.keySet());
        return totalScore;
    }

    /**
     * Computes the total collection bonus (progressive tier or complete-set bonus) granted
     * by the given set of item IDs the player/team currently has at least one of.
     */
    public static double calculateCollectionBonus(Set<String> ownedItemIds) {
        double bonus = 0.0;
        for (Collection coll : collections.values()) {
            int unique = (int) coll.itemGroups.stream()
                    .filter(group -> group.stream().anyMatch(ownedItemIds::contains))
                    .count();

            if ("progressive".equals(coll.type)) {
                if (unique > 0 && !coll.progressiveScores.isEmpty()) {
                    bonus += coll.progressiveScores.get(Math.min(unique - 1, coll.progressiveScores.size() - 1));
                }
            } else if (unique >= coll.itemGroups.size()) {
                bonus += coll.completeBonus;
            }
        }
        return bonus;
    }

    /**
     * A collection counts as "complete" once every item group in it has been collected,
     * regardless of whether it's a progressive or complete-type collection. Used to decide
     * whether quest-flagged collections should be shown.
     */
    public static boolean isCollectionComplete(Collection coll, Set<String> ownedItemIds) {
        long unique = coll.itemGroups.stream()
                .filter(group -> group.stream().anyMatch(ownedItemIds::contains))
                .count();
        return unique >= coll.itemGroups.size();
    }

    public static String getPlayerBiome(Player player) {
        try {
            return player.getLocation().getBlock().getBiome().getKey().getKey();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static java.lang.reflect.Method getStructuresMethod = null;
    private static boolean getStructuresMethodChecked = false;

    public static String getPlayerStructure(Player player) {
        try {
            if (!getStructuresMethodChecked) {
                getStructuresMethodChecked = true;
                try {
                    getStructuresMethod = org.bukkit.World.class.getMethod(
                            "getStructures", int.class, int.class);
                } catch (NoSuchMethodException e) {
                    getStructuresMethod = null;
                }
            }
            if (getStructuresMethod == null) return null;

            org.bukkit.Location loc = player.getLocation();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;

            Object result = getStructuresMethod.invoke(player.getWorld(), chunkX, chunkZ);
            if (!(result instanceof java.util.Collection<?> structures) || structures.isEmpty()) {
                return null;
            }

            for (Object generatedStructure : structures) {
                // Only accept a structure whose bounding box actually contains the player,
                // not just one that clips the same chunk.
                Object boundingBox = generatedStructure.getClass().getMethod("getBoundingBox").invoke(generatedStructure);
                boolean contains = (boolean) boundingBox.getClass()
                        .getMethod("contains", double.class, double.class, double.class)
                        .invoke(boundingBox, loc.getX(), loc.getY(), loc.getZ());

                if (contains) {
                    Object structure = generatedStructure.getClass().getMethod("getStructure").invoke(generatedStructure);
                    Object key = structure.getClass().getMethod("getKey").invoke(structure);
                    Object keyStr = key.getClass().getMethod("getKey").invoke(key);
                    return String.valueOf(keyStr);
                }
            }

            return null; // structures exist in this chunk, but none actually contain the player
        } catch (Throwable t) {
            return null;
        }
    }


    public static void tickScoreHistory() {
        if (paused) return;

        microSampleTickCounter++;
        if (microSampleTickCounter >= MICRO_SAMPLE_INTERVAL_TICKS) {
            microSampleTickCounter = 0;
            sampleBiomesAndStructures();
        }

        scoreHistoryTickCounter++;
        if (scoreHistoryTickCounter < SNAPSHOT_INTERVAL_TICKS) return;
        scoreHistoryTickCounter = 0;
        recordScoreSnapshot();
    }

    /**
     * Runs every 5 seconds. Records the player's current biome/structure into a per-player set
     * (a set, so revisiting the same biome/structure within the window is a no-op) that gets
     * folded into the next score snapshot and then cleared.
     */
    private static void sampleBiomesAndStructures() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;

            String biome = getPlayerBiome(p);
            if (biome != null) {
                visitedBiomes.computeIfAbsent(p.getName(), k -> new LinkedHashSet<>()).add(biome);
            }
            String structure = getPlayerStructure(p);
            if (structure != null) {
                visitedStructures.computeIfAbsent(p.getName(), k -> new LinkedHashSet<>()).add(structure);
            }
        }
    }

    private static void recordScoreSnapshot() {
        // NOTE: ZappierGames.loothuntDuration is stored in *minutes* (it's the raw GUI selection),
        // while ZappierGames.timer counts down in *ticks*. Subtracting one from the other produced
        // a huge negative "elapsed time" on the score graph. LootHunt.startTimer is the tick-based
        // countdown start value set in start(), so it's the correct reference point here.
        long elapsedSeconds = Math.max(0L, (long) ((startTimer - ZappierGames.timer) / 20.0));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;

            double score = calculateTotalScore(p);

            // Fold in wherever the player is right now too, in case the window boundary lands
            // between micro-samples.
            String currentBiome = getPlayerBiome(p);
            if (currentBiome != null) {
                visitedBiomes.computeIfAbsent(p.getName(), k -> new LinkedHashSet<>()).add(currentBiome);
            }
            String currentStructure = getPlayerStructure(p);
            if (currentStructure != null) {
                visitedStructures.computeIfAbsent(p.getName(), k -> new LinkedHashSet<>()).add(currentStructure);
            }

            List<String> biomes = new ArrayList<>(visitedBiomes.getOrDefault(p.getName(), Collections.emptySet()));
            List<String> structures = new ArrayList<>(visitedStructures.getOrDefault(p.getName(), Collections.emptySet()));

            org.bukkit.Location loc = p.getLocation();
            scoreHistory.computeIfAbsent(p.getName(), k -> new ArrayList<>())
                    .add(new ScoreSnapshot(elapsedSeconds, score, biomes, structures, loc.getX(), loc.getZ(), p.getWorld().getName()));

            // Reset the accumulation window now that it's been folded into this snapshot
            visitedBiomes.remove(p.getName());
            visitedStructures.remove(p.getName());
        }
    }

    public static void loadConfig(FileConfiguration config) {
        ZappierGames plugin = ZappierGames.getInstance();
        startTimer = config.getDouble("start-timer", 240.0);

        List<String> shulkerColorNames = config.getStringList("shulker-colors");
        //Nah
        /*if (shulkerColorNames.isEmpty()) {
            shulkerColorNames = List.of("BLUE_SHULKER_BOX", "RED_SHULKER_BOX", "GREEN_SHULKER_BOX", "YELLOW_SHULKER_BOX", "BLACK_SHULKER_BOX");
            //plugin.getLogger().warning("shulker-colors not found in config.yml, using default values");
        }*/
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

        ConfigurationSection specialItemsSection = config.getConfigurationSection("special-items");
        if (specialItemsSection != null) {
            specialItemValues.clear();
            for (String key : specialItemsSection.getKeys(false)) {
                specialItemValues.put(key.toUpperCase(), specialItemsSection.getDouble(key));
            }
        } else {
            //plugin.getLogger().warning("special-items not found in config.yml, no special-item scoring available");
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
                coll.quest = collSec.getBoolean("quest", false);

                List<String> rawItems = collSec.getStringList("items");
                for (String raw : rawItems) {
                    String[] alts = raw.split("\\|");
                    List<String> group = new ArrayList<>();
                    for (String alt : alts) {
                        group.add(alt.trim().toUpperCase());
                    }
                    coll.itemGroups.add(group);
                }

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

    private static File seedHistoryFile;

    private static File getSeedHistoryFile() {
        if (seedHistoryFile == null) {
            seedHistoryFile = new File(ZappierGames.getInstance().getDataFolder(), "loothunt-seed-history.yml");
        }
        return seedHistoryFile;
    }

    /**
     * Warns everyone online if a loothunt has already been started on this world's seed before,
     * listing the durations/dates of the previous runs.
     */
    private static void warnIfSeedAlreadyPlayed() {
        long seed = Bukkit.getWorlds().getFirst().getSeed();
        String key = String.valueOf(seed);

        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(getSeedHistoryFile());
        List<Map<?, ?>> plays = cfg.getMapList(key);
        if (plays.isEmpty()) return;

        for (Map<?, ?> play : plays) {
            Object durObj = play.get("duration-minutes");
            Object dateObj = play.get("date");
            double dur = durObj instanceof Number ? ((Number) durObj).doubleValue() : 0;
            String durStr = (dur == Math.floor(dur)) ? String.valueOf((int) dur) : String.valueOf(dur);

            Bukkit.broadcast(Component.text(
                    "⚠ A " + durStr + " minute Loot Hunt has been played on this seed already" +
                            (dateObj != null ? " (" + dateObj + ")" : "") + "!",
                    NamedTextColor.GOLD));
        }
    }

    /** Records this run's duration under the current world seed for future warnings. */
    private static void recordSeedHistory(double durationMinutes) {
        long seed = Bukkit.getWorlds().getFirst().getSeed();
        String key = String.valueOf(seed);
        File file = getSeedHistoryFile();

        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> plays = new ArrayList<>(cfg.getMapList(key));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("duration-minutes", durationMinutes);
        entry.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        plays.add(entry);

        cfg.set(key, plays);
        try {
            cfg.save(file);
        } catch (IOException e) {
            ZappierGames.getInstance().getLogger().warning("Failed to save loothunt seed history: " + e.getMessage());
        }
    }

    public static void start(double duration) {
        warnIfSeedAlreadyPlayed();
        recordSeedHistory(duration);
        bundleSlots.clear();
        InfinibundleListener.clearAll();
        LootHunt.paused = false;
        wasPausedLastTick = false;
        pausedPotionEffects.clear();
        scoreHistory.clear();
        visitedBiomes.clear();
        visitedStructures.clear();
        scoreHistoryTickCounter = 0;
        microSampleTickCounter = 0;
        ZappierGames.resetPlayers(false, true);
        ZappierGames.noPvP = noPvP;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(0);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
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
            p.sendMessage(Component.text("Use /getinfinibundle if you lose it.", NamedTextColor.GREEN));
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setLevel(0);
            p.setExp(0.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);


            Iterator<Advancement> it = Bukkit.advancementIterator();
            while (it.hasNext()) {
                Advancement advancement = it.next();
                AdvancementProgress progress = p.getAdvancementProgress(advancement);

                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            LootHuntSpectatorListener.clearSpectatorDisplay(p);
        }
        if (noPvP) {
            playerKillCounts.clear();
        }
        ZappierGames.globalBossBar.removeAll();
        ZappierGames.gameMode = -1;

        ItemValueActionBarListener.clearTracking();

        // 1. Setup data structures
        Map<String, List<PlayerResult>> teamPlayers = new HashMap<>();
        Map<String, Map<String, Double>> teamItemCounts = new HashMap<>();
        Map<String, Map<String, List<ItemEntry>>> teamStorages = new HashMap<>();
        Set<String> teamsWithStorageProcessed = new HashSet<>();

        // 2. Process Players (Personal Inventories & Storage)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.YELLOW + "Game Finished!", "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            String teamName = p.getScoreboard().getEntryTeam(p.getName()) != null
                    ? p.getScoreboard().getEntryTeam(p.getName()).getName()
                    : "(Solo) " + p.getName();

            // Process personal inventory
            Map<String, List<ItemEntry>> personalInv = new HashMap<>();
            processContainer(personalInv, Arrays.asList(p.getInventory().getContents()), "Inventory");

            double personalScore = personalInv.values().stream()
                    .flatMap(List::stream)
                    .mapToDouble(e -> e.points)
                    .sum();

            // Create PlayerResult record
            PlayerResult pr = new PlayerResult();
            pr.name = p.getName();
            pr.uuid = p.getUniqueId().toString();
            pr.kills = playerKillCounts.getOrDefault(p.getName().toUpperCase(), 0);
            pr.deaths = playerDeathCounts.getOrDefault(p.getName().toUpperCase(), 0);
            pr.personalInventory = personalInv;
            pr.personalScore = personalScore;
            pr.inventoryContents = p.getInventory().getContents();

            teamPlayers.computeIfAbsent(teamName, k -> new ArrayList<>()).add(pr);

            // Process Shared Team Storage ONCE per team
            Map<String, Double> teamScores = teamItemCounts.computeIfAbsent(teamName, k -> new HashMap<>());

            if (!teamsWithStorageProcessed.contains(teamName)) {
                List<ItemStack> teamStorageItems = InfinibundleListener.getTeamStorage(teamName);
                Map<String, List<ItemEntry>> storageResults = new HashMap<>();
                processContainer(storageResults, teamStorageItems, "Team Storage");

                // Add storage points to team total
                for (Map.Entry<String, List<ItemEntry>> entry : storageResults.entrySet()) {
                    double totalPoints = entry.getValue().stream().mapToDouble(e -> e.points).sum();
                    teamScores.merge(entry.getKey(), totalPoints, Double::sum);
                }

                teamStorages.put(teamName, storageResults);
                teamsWithStorageProcessed.add(teamName);
            }
        }

        // 3. Add personal inventories + kills/deaths to team totals
        for (Map.Entry<String, List<PlayerResult>> entry : teamPlayers.entrySet()) {
            String teamName = entry.getKey();
            Map<String, Double> teamScores = teamItemCounts.get(teamName);

            for (PlayerResult pr : entry.getValue()) {
                // Add personal items to team score
                for (Map.Entry<String, List<ItemEntry>> invEntry : pr.personalInventory.entrySet()) {
                    double totalPoints = invEntry.getValue().stream().mapToDouble(e -> e.points).sum();
                    teamScores.merge(invEntry.getKey(), totalPoints, Double::sum);
                }

                // Kills Calculation
                int killCount = pr.kills;
                double killValue = baseKillPoints;
                double killScore = 0.0;
                while (killCount > 0 && killValue > 1) {
                    killScore += killValue;
                    killCount--;
                    killValue /= pointsReductionFactor;
                }
                teamScores.merge("kills", killScore, Double::sum);
                Bukkit.broadcast(Component.text(pr.name + " got " + pr.kills + " kills, earning " + String.format("%.1f", killScore) + " points for team " + teamName, NamedTextColor.YELLOW));

                // Deaths Calculation
                int deathCount = pr.deaths;
                double deathValue = baseDeathPoints;
                double deathScore = 0.0;
                while (deathCount > 0 && deathValue > 1) {
                    deathScore -= deathValue;
                    deathCount--;
                    deathValue /= pointsReductionFactor;
                }
                teamScores.merge("deaths", deathScore, Double::sum);
                Bukkit.broadcast(Component.text(pr.name + " got " + pr.deaths + " deaths, losing " + Math.abs(deathScore) + " points for team " + teamName, NamedTextColor.YELLOW));
            }
        }

        // 4. Final Broadcast & Collection Bonuses
        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("        RESULTS        ", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        for (Map.Entry<String, Map<String, Double>> teamEntry : teamItemCounts.entrySet()) {
            String teamName = teamEntry.getKey();
            Map<String, Double> items = teamEntry.getValue();

            double totalScore = items.values().stream().mapToDouble(Double::doubleValue).sum();
            List<Component> collectionLines = new ArrayList<>();

            for (Collection coll : collections.values()) {
                long uniqueCollected = coll.itemGroups.stream()
                        .filter(group -> group.stream().anyMatch(items::containsKey))
                        .count();
                boolean questHidden = coll.quest && uniqueCollected < coll.itemGroups.size();

                if ("progressive".equals(coll.type)) {
                    int count = (int) uniqueCollected;
                    int bonus = 0;
                    if (count > 0 && !coll.progressiveScores.isEmpty()) {
                        bonus = coll.progressiveScores.get(Math.min(count - 1, coll.progressiveScores.size() - 1));
                    }
                    totalScore += bonus;

                    if (questHidden) continue; // quest collection not yet completed by this team

                    Component hover = Component.text("Collected " + coll.name + ":", NamedTextColor.AQUA)
                            .append(Component.newline()).append(Component.newline());
                    for (List<String> group : coll.itemGroups) {
                        String rep = group.get(0);
                        boolean has = group.stream().anyMatch(items::containsKey);
                        hover = hover.append(Component.text((has ? "✓ " : "✗ ") + rep + (group.size() > 1 ? " (variants)" : ""), has ? NamedTextColor.GREEN : NamedTextColor.RED))
                                .append(Component.newline());
                    }

                    collectionLines.add(Component.text("  " + coll.name + ": " + uniqueCollected + "/" + coll.itemGroups.size(), NamedTextColor.GRAY)
                            .append(Component.text(" (+" + bonus + " bonus)", NamedTextColor.GREEN))
                            .hoverEvent(HoverEvent.showText(hover)));
                } else {
                    if (uniqueCollected >= coll.itemGroups.size()) {
                        totalScore += coll.completeBonus;
                        collectionLines.add(Component.text("  " + coll.name + ": COMPLETE (+" + coll.completeBonus + " bonus)", NamedTextColor.GREEN));
                    } else if (!questHidden) {
                        Component hover = Component.text("Collected " + coll.name + ":", NamedTextColor.AQUA)
                                .append(Component.newline()).append(Component.newline());
                        for (List<String> group : coll.itemGroups) {
                            String rep = group.get(0);
                            boolean has = group.stream().anyMatch(items::containsKey);
                            hover = hover.append(Component.text((has ? "✓ " : "✗ ") + rep + (group.size() > 1 ? " (variants)" : ""), has ? NamedTextColor.GREEN : NamedTextColor.RED))
                                    .append(Component.newline());
                        }
                        collectionLines.add(Component.text("  " + coll.name + ": " + uniqueCollected + "/" + coll.itemGroups.size(), NamedTextColor.GRAY)
                                .hoverEvent(HoverEvent.showText(hover)));
                    }
                    // questHidden && incomplete: contributes no bonus and is not shown, by design
                }
            }

            Bukkit.broadcast(Component.text(teamName + ": " + String.format("%.1f", totalScore), NamedTextColor.YELLOW));
            for (Component line : collectionLines) {
                Bukkit.broadcast(line);
            }
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        // 5. Generate final HTML report
        long seed = Bukkit.getWorlds().getFirst().getSeed();
        generateResultsHTML(teamItemCounts, teamPlayers, teamStorages, seed);
    }

    public static String buildCollectionTooltip(Collection coll, Map<String, Double> items) {
        StringBuilder tip = new StringBuilder(coll.name + ":\n\n");
        for (List<String> group : coll.itemGroups) {
            String rep = group.get(0);
            boolean has = group.stream().anyMatch(items::containsKey);
            tip.append(has ? "✓ " : "✗ ").append(rep).append(group.size() > 1 ? " (variants)" : "").append("\n");
        }
        return tip.toString();
    }

    private static void processContainer(Map<String, List<ItemEntry>> scoreMap, Iterable<ItemStack> items, String sourcePrefix) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            String itemId = item.getType().toString();
            double baseValue = itemValues.getOrDefault(itemId, 0.0);
            int amount = item.getAmount();

            // Special item variants (Ominous Banner, Explorer Map, etc.) - distinguished from
            // their base material by rarity/display name rather than a distinct Material
            String specialId = getSpecialItemId(item);
            if (specialId != null) {
                itemId = specialId;
                baseValue = specialItemValues.getOrDefault(specialId, 0.0);
            }

            // Potions
            if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType pt = potionMeta.getBasePotionType();
                    String prefix = item.getType() == Material.SPLASH_POTION ? "SPLASH_" :
                            item.getType() == Material.LINGERING_POTION ? "LINGERING_" : "";
                    String key = prefix + (pt != null ? pt.name() : "WATER");
                    baseValue = potionValues.getOrDefault(key, 0.0);
                    itemId = key; // Use the specific potion ID for scoring and collections
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
            final String effectiveItemId = itemId;   // ← capture this instead

            boolean isCollectionItem = collections.values().stream()
                    .anyMatch(c -> c.itemGroups.stream()
                            .anyMatch(g -> g.contains(effectiveItemId)));
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

        tickScoreHistory();
        tickSpectatorDisplay();

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
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                Bukkit.getServer().getServerTickManager().setFrozen(false);
            }
            if (wasPausedLastTick) {
                // Just unpaused: stop re-applying frozen effects and let them resume naturally
                pausedPotionEffects.clear();
            }
            wasPausedLastTick = false;
        } else {
            ZappierGames.globalBossBar.setColor(BarColor.RED);
            ZappierGames.globalBossBar.setTitle(String.format("(PAUSED) Time Left: %02d:%02d:%02d (PAUSED)", hours, minutes, seconds));
            ZappierGames.globalBossBar.setProgress(ZappierGames.timer / startTimer);
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                Bukkit.getServer().getServerTickManager().setFrozen(true);
            }
            freezePotionEffects();
            wasPausedLastTick = true;
        }
    }

    /**
     * The server tick freeze still lets potion effect durations decrement, so while paused we
     * capture each player's active effects the moment pause starts and re-apply that exact
     * snapshot every tick, holding duration/amplifier constant until unpause.
     */
    private static void freezePotionEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<org.bukkit.potion.PotionEffect> snapshot = pausedPotionEffects.get(p.getUniqueId());
            if (snapshot == null) {
                // First tick of the pause: capture current effects
                snapshot = new ArrayList<>(p.getActivePotionEffects());
                pausedPotionEffects.put(p.getUniqueId(), snapshot);
            }
            for (org.bukkit.potion.PotionEffect effect : snapshot) {
                p.addPotionEffect(effect, true);
            }
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
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("SHIFT + L-CLICK: put inventory inside", NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    Component.text("SHIFT + R-CLICK: toggle inventory slots", NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
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

    /**
     * Detects "special" item variants that share a Material with a common item but should be
     * scored differently - identified by item rarity/component data, since there's no dedicated
     * Material for them. Returns a synthetic item ID to use in place of the material name for
     * scoring/collections, or null if the item isn't a recognized special variant.
     *
     * Currently recognizes:
     *  - Ominous Banner: any *_BANNER with an elevated item rarity (vanilla banners are COMMON)
     *    and/or "ominous" in its display name.
     *  - Explorer Map: a FILLED_MAP whose "minecraft:item_name" component is a translatable text
     *    with key "filled_map.monument" (Ocean Explorer Map) or "filled_map.mansion" (Woodland
     *    Explorer Map) - the two explorer maps sold by a cartographer villager. Matching the raw
     *    translation key (rather than the localized display text) works regardless of the
     *    player's locale and regardless of whether the map's been renamed.
     */
    public static String getSpecialItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        Material type = item.getType();
        ItemMeta meta = item.getItemMeta();

        if (type.name().endsWith("_BANNER")) {
            String plainName = "";
            if (meta.hasDisplayName()) {
                try {
                    plainName = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).toLowerCase(Locale.ROOT);
                } catch (Throwable ignored) {
                    plainName = "";
                }
            }
            if (hasElevatedRarity(meta) || plainName.contains("ominous")) {
                return "OMINOUS_BANNER";
            }
        }

        if (type == Material.FILLED_MAP) {
            String translateKey = getItemNameTranslateKey(meta);
            if ("filled_map.monument".equals(translateKey) || "filled_map.mansion".equals(translateKey)) {
                return "EXPLORER_MAP";
            }
        }

        return null;
    }

    // Reflection-based check for ItemMeta#itemName()/hasItemName() (the 1.20.5+ "minecraft:item_name"
    // component) so this still compiles/runs against older API versions that don't expose it.
    private static java.lang.reflect.Method itemNameHasMethod = null;
    private static java.lang.reflect.Method itemNameGetMethod = null;
    private static boolean itemNameMethodsChecked = false;

    /**
     * Returns the translation key (e.g. "filled_map.monument") of the item's item_name component
     * if it's a TranslatableComponent, checking the item_name component first (falls back to
     * display name if item_name isn't available on this API version) and searching children too.
     */
    private static String getItemNameTranslateKey(ItemMeta meta) {
        try {
            Component comp = null;

            if (!itemNameMethodsChecked) {
                itemNameMethodsChecked = true;
                try {
                    itemNameHasMethod = meta.getClass().getMethod("hasItemName");
                    itemNameGetMethod = meta.getClass().getMethod("itemName");
                } catch (NoSuchMethodException e) {
                    itemNameHasMethod = null;
                    itemNameGetMethod = null;
                }
            }
            if (itemNameHasMethod != null && itemNameGetMethod != null) {
                boolean has = (boolean) itemNameHasMethod.invoke(meta);
                if (has) {
                    comp = (Component) itemNameGetMethod.invoke(meta);
                }
            }
            if (comp == null && meta.hasDisplayName()) {
                comp = meta.displayName();
            }
            return findTranslateKey(comp);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String findTranslateKey(Component comp) {
        if (comp == null) return null;
        if (comp instanceof net.kyori.adventure.text.TranslatableComponent tc) {
            return tc.key();
        }
        for (Component child : comp.children()) {
            String key = findTranslateKey(child);
            if (key != null) return key;
        }
        return null;
    }

    // Reflection-based check for ItemMeta#getRarity() so this still compiles/runs against older
    // API versions that don't expose item rarity (mirrors the getStructures() pattern above).
    private static java.lang.reflect.Method rarityHasRarityMethod = null;
    private static java.lang.reflect.Method rarityGetRarityMethod = null;
    private static boolean rarityMethodsChecked = false;

    private static boolean hasElevatedRarity(ItemMeta meta) {
        try {
            if (!rarityMethodsChecked) {
                rarityMethodsChecked = true;
                try {
                    rarityHasRarityMethod = meta.getClass().getMethod("hasRarity");
                    rarityGetRarityMethod = meta.getClass().getMethod("getRarity");
                } catch (NoSuchMethodException e) {
                    rarityHasRarityMethod = null;
                    rarityGetRarityMethod = null;
                }
            }
            if (rarityHasRarityMethod == null || rarityGetRarityMethod == null) return false;

            boolean has = (boolean) rarityHasRarityMethod.invoke(meta);
            if (!has) return false;
            Object rarity = rarityGetRarityMethod.invoke(meta);
            return rarity != null && !"COMMON".equals(rarity.toString());
        } catch (Throwable t) {
            return false;
        }
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


    private static void tickSpectatorDisplay() {
        if (paused) return;
        spectatorDisplayTickCounter++;
        if (spectatorDisplayTickCounter < SPECTATOR_DISPLAY_INTERVAL_TICKS) return;
        spectatorDisplayTickCounter = 0;
        updateSpectatorDisplays();
    }

    private static void updateSpectatorDisplays() {
        List<Player> spectators = new ArrayList<>();
        List<Map.Entry<Player, Double>> liveScores = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR) {
                spectators.add(p);
            } else if (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) {
                liveScores.add(new AbstractMap.SimpleEntry<>(p, calculateTotalScore(p)));
            }
        }

        if (spectators.isEmpty()) return;

        liveScores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Component tabFooter = buildTabFooter(liveScores);
        Component tabHeader = Component.text("=== Loot Hunt ===", NamedTextColor.GOLD);

        for (Player spec : spectators) {
            applySidebar(spec, liveScores);
            spec.sendPlayerListHeaderAndFooter(tabHeader, tabFooter);
        }
    }

    private static void applySidebar(Player spectator, List<Map.Entry<Player, Double>> liveScores) {
        org.bukkit.scoreboard.Scoreboard board = spectatorBoards.computeIfAbsent(spectator.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());

        org.bukkit.scoreboard.Objective obj = board.getObjective("loothunt_sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("loothunt_sidebar", org.bukkit.scoreboard.Criteria.DUMMY,
                    Component.text("Loot Hunt - Top 5", NamedTextColor.GOLD));
            obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        }

        Set<String> previousEntries = new HashSet<>(board.getEntries());
        Set<String> keptEntries = new HashSet<>();

        int limit = Math.min(SIDEBAR_MAX_ENTRIES, liveScores.size());
        for (int i = 0; i < limit; i++) {
            Player p = liveScores.get(i).getKey();
            double score = liveScores.get(i).getValue();
            String entryName = p.getName();
            keptEntries.add(entryName);
            obj.getScore(entryName).setScore((int) Math.round(score));
        }

        // Drop entries that fell out of the top 5 (or players no longer in survival/adventure)
        for (String entry : previousEntries) {
            if (!keptEntries.contains(entry)) {
                obj.getScore(entry).resetScore();
            }
        }

        if (spectator.getScoreboard() != board) {
            spectator.setScoreboard(board);
        }
    }

    private static Component buildTabFooter(List<Map.Entry<Player, Double>> liveScores) {
        if (liveScores.isEmpty()) {
            return Component.text("No active survival/adventure players", NamedTextColor.GRAY);
        }

        Component footer = Component.text("Live Scores", NamedTextColor.GOLD, TextDecoration.BOLD);
        for (int i = 0; i < liveScores.size(); i++) {
            Player p = liveScores.get(i).getKey();
            double score = liveScores.get(i).getValue();
            footer = footer.append(Component.newline())
                    .append(Component.text((i + 1) + ". ", NamedTextColor.GRAY))
                    .append(Component.text(p.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.format("%.1f", score), NamedTextColor.GREEN));
        }
        return footer;
    }

}