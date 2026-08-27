package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
    public static String gameVersion = "";
    public static String loothuntSeason = "";
    private static Material[] shulkerColors;
    public static Map<String, Double> itemValues = new HashMap<>();
    public static Map<String, Double> potionValues = new HashMap<>();
    // Tipped arrows (Material.TIPPED_ARROW) use PotionMeta just like potions, but score
    // separately via this map (keyed "ARROW_" + potion type, e.g. "ARROW_STRENGTH") rather than
    // sharing potionValues, since a tipped arrow and a drinkable potion aren't worth the same.
    public static Map<String, Double> arrowValues = new HashMap<>();
    // Point values for "special" item variants that share a Material with a common item but are
    // distinguished by rarity/display name (e.g. Ominous Banner vs. a plain banner). Keyed by the
    // synthetic ID returned from getSpecialItemId(), e.g. "OMINOUS_BANNER", "EXPLORER_MAP".
    public static Map<String, Double> specialItemValues = new HashMap<>();
    public static Map<String, Integer> playerKillCounts = new HashMap<>();
    public static Map<String, Integer> playerDeathCounts = new HashMap<>();
    private static int baseKillPoints;
    private static int baseDeathPoints;
    private static int pointsReductionFactor;

    /** Diminishing-return kill bonus: each successive kill is worth less (halved by default). */
    private static double calculateKillScore(int killCount) {
        double killValue = baseKillPoints;
        double score = 0.0;
        while (killCount > 0 && killValue > 1) {
            score += killValue;
            killCount--;
            killValue /= pointsReductionFactor;
        }
        return score;
    }

    /** Diminishing-return death penalty (negative): each successive death costs less. */
    private static double calculateDeathScore(int deathCount) {
        double deathValue = baseDeathPoints;
        double score = 0.0;
        while (deathCount > 0 && deathValue > 1) {
            score -= deathValue;
            deathCount--;
            deathValue /= pointsReductionFactor;
        }
        return score;
    }
    private static int enchantmentPointsPerTier;
    private static Map<String, Integer> specialEnchantments = new HashMap<>();

    // === Enchantment collection scoring ===
    // Grants a one-time bonus per unique enchantment (optionally per unique level) currently
    // present on any item a team owns - like a collection, but auto-derived from enchantments
    // rather than a manually configured item list. Recomputed from current holdings each time
    // (not a permanent unlock), consistent with how item collections already work.
    private static boolean enchantmentCollectionEnabled = false;
    private static boolean excludeEnchantedBooksFromCollection = false;
    private static boolean countUniqueEnchantmentLevels = false;
    private static final Map<String, Double> enchantmentCollectionBonuses = new HashMap<>();

    /**
     * Scans the given items for enchantments and returns the total one-time bonus earned: each
     * unique enchantment (or unique enchantment+level, if count-unique-levels is on) present on
     * at least one item only counts once, no matter how many items carry it.
     */
    public static double calculateEnchantmentCollectionBonus(Iterable<ItemStack> items) {
        if (!enchantmentCollectionEnabled) return 0.0;
        return scanEnchantmentCollectionBonus(items, new HashSet<>());
    }

    private static double scanEnchantmentCollectionBonus(Iterable<ItemStack> items, Set<String> seen) {
        double bonus = 0.0;

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) continue;

            boolean isBook = item.getType() == Material.ENCHANTED_BOOK;
            ItemMeta meta = item.getItemMeta();

            if (!(isBook && excludeEnchantedBooksFromCollection)) {
                Map<Enchantment, Integer> enchants = (isBook && meta instanceof EnchantmentStorageMeta esm)
                        ? esm.getStoredEnchants()
                        : item.getEnchantments();

                for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                    String name = e.getKey().getKey().getKey().toUpperCase();
                    int level = e.getValue();
                    String key = countUniqueEnchantmentLevels ? (name + "_" + level) : name;

                    if (seen.add(key)) {
                        bonus += enchantmentCollectionBonuses.getOrDefault(name, 0.0);
                    }
                }
            }

            // Recurse into shulker boxes and bundles, same as regular item scoring, sharing the
            // same "seen" set so nested items don't re-grant a bonus already counted above.
            if (item.getType().name().endsWith("_SHULKER_BOX") && meta instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
                BlockState bs = bsm.getBlockState();
                if (bs instanceof ShulkerBox shulker) {
                    bonus += scanEnchantmentCollectionBonus(Arrays.asList(shulker.getInventory().getContents()), seen);
                }
            }
            if (isBundle(item.getType()) && meta instanceof BundleMeta bundleMeta) {
                bonus += scanEnchantmentCollectionBonus(bundleMeta.getItems(), seen);
            }
        }

        return bonus;
    }
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

    /** A team's aggregate score at a point in time - used for the final score history graph so
     * it reflects one true line per team instead of one (double-counting, storage-inflated) line
     * per player. Also carries the merged biome/structure/dimension context from all online
     * teammates at that tick, for the graph tooltip. */
    public static class TeamScoreSnapshot {
        public final long tick;
        public final double score;
        public final List<String> biomes;
        public final List<String> structures;
        public final List<String> dimensions;

        public TeamScoreSnapshot(long tick, double score, List<String> biomes, List<String> structures, List<String> dimensions) {
            this.tick = tick;
            this.score = score;
            this.biomes = biomes != null ? biomes : Collections.emptyList();
            this.structures = structures != null ? structures : Collections.emptyList();
            this.dimensions = dimensions != null ? dimensions : Collections.emptyList();
        }
    }

    public static final Map<String, List<TeamScoreSnapshot>> teamScoreHistory = new HashMap<>();

    // Chunk-level biome samples collected as chunks load during the game (dimension name ->
    // packed chunk coords -> biome key). The end-of-game world map used to sample biomes live
    // from whatever chunks happened to still be loaded, which was almost none of the explored
    // area by the time the game ended - this builds up full coverage incrementally instead.
    public static final Map<String, Map<Long, String>> visitedChunkBiomes = new HashMap<>();

    public static long packChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Called from LootHuntChunkListener whenever a chunk loads during an active loothunt. */
    public static void recordChunkBiome(org.bukkit.Chunk chunk) {
        if (ZappierGames.gameMode != ZappierGames.LOOTHUNT) return;
        sampleAndStoreChunkBiome(chunk);
    }

    /**
     * Samples every currently-loaded chunk's biome across all worlds. ChunkLoadEvent only fires
     * for chunks that load *after* the listener starts caring, so chunks that were already loaded
     * when the game started (e.g. the spawn chunks) would otherwise never get recorded. Called
     * once from start().
     */
    public static void prepopulateLoadedChunkBiomes() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                sampleAndStoreChunkBiome(chunk);
            }
        }
    }

    private static void sampleAndStoreChunkBiome(org.bukkit.Chunk chunk) {
        try {
            org.bukkit.World world = chunk.getWorld();
            int blockX = (chunk.getX() << 4) + 8;
            int blockZ = (chunk.getZ() << 4) + 8;
            int blockY = world.getHighestBlockYAt(blockX, blockZ);
            String biomeKey = world.getBlockAt(blockX, blockY, blockZ).getBiome().getKey().getKey();
            visitedChunkBiomes.computeIfAbsent(world.getName(), k -> new HashMap<>())
                    .put(packChunkKey(chunk.getX(), chunk.getZ()), biomeKey);
        } catch (Throwable ignored) {
            // Biome lookup can fail in edge cases (e.g. mid-generation chunks) - just skip the sample
        }
    }

    // Biomes/structures a player has visited since the last score snapshot was recorded. Sampled
    // every MICRO_SAMPLE_INTERVAL_TICKS and folded into (then cleared from) the next ScoreSnapshot.
    private static final Map<String, Set<String>> visitedBiomes = new HashMap<>();
    private static final Map<String, Set<String>> visitedStructures = new HashMap<>();

    private static int scoreHistoryTickCounter = 0;
    private static final int SNAPSHOT_INTERVAL_TICKS = 600; // 30 seconds @ 20 tps

    // Counts unpaused ticks since game start, purely for graph timestamps. Deliberately NOT
    // derived from ZappierGames.timer (the countdown) - that can be adjusted independently
    // (pause/unpause edge cases, admin time changes, etc.), which was causing recorded times to
    // drift off clean 30-second boundaries (e.g. showing as "1.483 minutes" instead of "1.5").
    // This counter only ever increments in lockstep with scoreHistoryTickCounter, so a snapshot
    // is always recorded at an exact multiple of 30 seconds since start.
    private static long totalUnpausedTicks = 0;

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

    /**
     * Returns every collection in display order: non-quest collections first (alphabetical by
     * name), then quest collections (also alphabetical). Used everywhere collections are shown
     * to players (end-game broadcast, HTML report, bundle GUI) for a consistent ordering. Note
     * this doesn't filter by completion - callers still decide whether to hide an incomplete
     * quest collection based on that team's/player's own progress.
     */
    public static List<Collection> getSortedCollections() {
        List<Collection> sorted = new ArrayList<>(collections.values());
        sorted.sort(Comparator.<Collection>comparingInt(c -> c.quest ? 1 : 0)
                .thenComparing(c -> c.name, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

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

        totalUnpausedTicks++;

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
        // Deliberately NOT derived from ZappierGames.timer (see totalUnpausedTicks comment above) -
        // this always lands on an exact multiple of 30 seconds since totalUnpausedTicks only ever
        // increments alongside scoreHistoryTickCounter, which resets every 600 ticks.
        long elapsedSeconds = totalUnpausedTicks / 20;

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

        // Team-level aggregate score (personal items of all online members + team storage once,
        // not once per member + collection/enchantment bonuses + kills/deaths) - this is what the
        // final score history graph shows, since per-player lines double-count shared team storage.
        List<Player> livePlayers = (List<Player>) Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                .toList();

        // Merge biome/structure/dimension context from every online teammate's snapshot just
        // recorded above, so the team graph's tooltip can show where the team was, not just score.
        Map<String, Set<String>> teamBiomes = new HashMap<>();
        Map<String, Set<String>> teamStructures = new HashMap<>();
        Map<String, Set<String>> teamDimensions = new HashMap<>();
        for (Player p : livePlayers) {
            String team = getPlayerTeamName(p);
            List<ScoreSnapshot> hist = scoreHistory.get(p.getName());
            if (hist == null || hist.isEmpty()) continue;
            ScoreSnapshot last = hist.get(hist.size() - 1); // the snapshot just added above for this tick
            teamBiomes.computeIfAbsent(team, k -> new LinkedHashSet<>()).addAll(last.biomes);
            teamStructures.computeIfAbsent(team, k -> new LinkedHashSet<>()).addAll(last.structures);
            teamDimensions.computeIfAbsent(team, k -> new LinkedHashSet<>()).add(last.dimension);
        }

        for (Map.Entry<String, Double> entry : calculateLiveTeamScores(livePlayers).entrySet()) {
            String team = entry.getKey();
            List<String> biomes = new ArrayList<>(teamBiomes.getOrDefault(team, Collections.emptySet()));
            List<String> structures = new ArrayList<>(teamStructures.getOrDefault(team, Collections.emptySet()));
            List<String> dimensions = new ArrayList<>(teamDimensions.getOrDefault(team, Collections.emptySet()));
            teamScoreHistory.computeIfAbsent(team, k -> new ArrayList<>())
                    .add(new TeamScoreSnapshot(elapsedSeconds, entry.getValue(), biomes, structures, dimensions));
        }
    }

    public static void loadConfig(FileConfiguration config) {
        ZappierGames plugin = ZappierGames.getInstance();
        startTimer = config.getDouble("start-timer", 240.0);
        gameVersion = config.getString("game-version", "");
        loothuntSeason = config.getString("loothunt-season", "");

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

        ConfigurationSection arrowSection = config.getConfigurationSection("arrow-values");
        if (arrowSection != null) {
            arrowValues.clear();
            for (String key : arrowSection.getKeys(false)) {
                arrowValues.put(key.toUpperCase(), arrowSection.getDouble(key));
            }
        } else {
            //plugin.getLogger().warning("arrow-values not found in config.yml, tipped arrows will score 0");
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

        ConfigurationSection enchantCollectionSection = config.getConfigurationSection("enchantment-collection");
        enchantmentCollectionBonuses.clear();
        if (enchantCollectionSection != null) {
            enchantmentCollectionEnabled = enchantCollectionSection.getBoolean("enabled", false);
            excludeEnchantedBooksFromCollection = enchantCollectionSection.getBoolean("exclude-enchanted-books", false);
            countUniqueEnchantmentLevels = enchantCollectionSection.getBoolean("count-unique-levels", false);
            ConfigurationSection bonusSection = enchantCollectionSection.getConfigurationSection("bonuses");
            if (bonusSection != null) {
                for (String key : bonusSection.getKeys(false)) {
                    enchantmentCollectionBonuses.put(key.toUpperCase(), bonusSection.getDouble(key));
                }
            }
        } else {
            enchantmentCollectionEnabled = false;
            excludeEnchantedBooksFromCollection = false;
            countUniqueEnchantmentLevels = false;
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
        teamScoreHistory.clear();
        visitedChunkBiomes.clear();
        visitedBiomes.clear();
        visitedStructures.clear();
        scoreHistoryTickCounter = 0;
        microSampleTickCounter = 0;
        totalUnpausedTicks = 0;
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
        prepopulateLoadedChunkBiomes();
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
                double killScore = calculateKillScore(pr.kills);
                teamScores.merge("kills", killScore, Double::sum);
                Bukkit.broadcast(Component.text(pr.name + " got " + pr.kills + " kills, earning " + String.format("%.1f", killScore) + " points for team " + teamName, NamedTextColor.YELLOW));

                // Deaths Calculation
                double deathScore = calculateDeathScore(pr.deaths);
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

            for (Collection coll : getSortedCollections()) {
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

            if (enchantmentCollectionEnabled) {
                List<ItemStack> allTeamItems = new ArrayList<>();
                for (PlayerResult pr : teamPlayers.getOrDefault(teamName, Collections.emptyList())) {
                    if (pr.inventoryContents != null) {
                        allTeamItems.addAll(Arrays.asList(pr.inventoryContents));
                    }
                }
                allTeamItems.addAll(InfinibundleListener.getTeamStorage(teamName));

                double enchantBonus = calculateEnchantmentCollectionBonus(allTeamItems);
                if (enchantBonus > 0) {
                    totalScore += enchantBonus;
                    collectionLines.add(Component.text("  Enchantment Collection: +" + String.format("%.1f", enchantBonus) + " bonus", NamedTextColor.LIGHT_PURPLE));
                }
            }

            Bukkit.broadcast(Component.text(teamName + ": " + String.format("%.1f", totalScore), NamedTextColor.YELLOW));
            for (Component line : collectionLines) {
                Bukkit.broadcast(line);
            }
        }

        Bukkit.broadcast(Component.text("=======================", NamedTextColor.GREEN));

        // 5. Generate final HTML report, with the item breakdown embedded as a download button
        long seed = Bukkit.getWorlds().getFirst().getSeed();
        String csvContent = buildItemBreakdownCsv(teamPlayers);
        generateResultsHTML(teamItemCounts, teamPlayers, teamStorages, seed, csvContent);

        // Now safe to clear - the report has already consumed this game's chunk biome data
        visitedChunkBiomes.clear();
    }

    /**
     * Builds a per-team item breakdown (name/count/item points/enchantment points, plus each
     * player's death penalty) as CSV text - embedded as a download button on the results page
     * rather than written to a separate folder, so it's available from wherever the results link
     * is shared. Opens directly in Excel or Google Sheets - not a true .xlsx (this plugin doesn't
     * currently depend on a library like Apache POI to write that format), but the same data.
     */
    private static String buildItemBreakdownCsv(Map<String, List<PlayerResult>> teamPlayers) {
        StringBuilder csv = new StringBuilder();
        List<String> teamNames = new ArrayList<>(teamPlayers.keySet());
        Collections.sort(teamNames, String.CASE_INSENSITIVE_ORDER);

        for (String teamName : teamNames) {
            List<PlayerResult> players = teamPlayers.get(teamName);

            csv.append(csvField(teamName)).append("\n");
            csv.append(csvField(players.stream().map(p -> p.name).collect(Collectors.joining(", ")))).append("\n");
            csv.append("Item Name,Count,Item Points,Enchantment Points\n");

            // itemId -> [quantity, itemPoints (no enchant), enchantPoints]
            Map<String, double[]> breakdown = new TreeMap<>();
            for (PlayerResult pr : players) {
                if (pr.inventoryContents != null) {
                    accumulateItemBreakdown(breakdown, Arrays.asList(pr.inventoryContents));
                }
            }
            accumulateItemBreakdown(breakdown, InfinibundleListener.getTeamStorage(teamName));

            for (Map.Entry<String, double[]> entry : breakdown.entrySet()) {
                double[] v = entry.getValue();
                if (v[0] <= 0) continue;
                csv.append(csvField(entry.getKey())).append(",")
                        .append((int) v[0]).append(",")
                        .append(String.format("%.2f", v[1])).append(",")
                        .append(String.format("%.2f", v[2])).append("\n");
            }

            csv.append("\nPlayer,Deaths,Death Penalty\n");
            for (PlayerResult pr : players) {
                double deathPenalty = calculateDeathScore(pr.deaths);
                csv.append(csvField(pr.name)).append(",").append(pr.deaths).append(",")
                        .append(String.format("%.2f", deathPenalty)).append("\n");
            }

            csv.append("\n\n");
        }

        return csv.toString();
    }

    /**
     * Same item resolution as processContainer (special items, potions, enchantments) but keeps
     * base item points and enchantment points separate instead of merging them, and aggregates
     * by itemId across every item passed in rather than returning a per-source list.
     */
    private static void accumulateItemBreakdown(Map<String, double[]> breakdown, Iterable<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (isInfinibundle(item)) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    accumulateItemBreakdown(breakdown, bundleMeta.getItems());
                }
                continue;
            }

            String itemId = item.getType().toString();
            double baseValue = itemValues.getOrDefault(itemId, 0.0);
            int amount = item.getAmount();

            String specialId = getSpecialItemId(item);
            if (specialId != null) {
                itemId = specialId;
                baseValue = specialItemValues.getOrDefault(specialId, 0.0);
            }

            if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType pt = potionMeta.getBasePotionType();
                    String prefix = item.getType() == Material.SPLASH_POTION ? "SPLASH_" :
                            item.getType() == Material.LINGERING_POTION ? "LINGERING_" : "";
                    String key = prefix + (pt != null ? pt.name() : "WATER");
                    baseValue = potionValues.getOrDefault(key, 0.0);
                    itemId = key;
                }
            }

            if (item.getType() == Material.TIPPED_ARROW) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType pt = potionMeta.getBasePotionType();
                    String key = "ARROW_" + (pt != null ? pt.name() : "WATER");
                    baseValue = arrowValues.getOrDefault(key, 0.0);
                    itemId = key;
                }
            }


            double enchantValue = 0.0;
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasEnchants() || (item.getType() == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants())) {
                    enchantValue = getTotalEnchantmentPoints(item);
                }
            }

            double[] agg = breakdown.computeIfAbsent(itemId, k -> new double[3]);
            agg[0] += amount;
            agg[1] += baseValue * amount;
            agg[2] += enchantValue * amount;

            if (item.getType().name().endsWith("_SHULKER_BOX")) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
                    BlockState bs = bsm.getBlockState();
                    if (bs instanceof ShulkerBox shulker) {
                        accumulateItemBreakdown(breakdown, Arrays.asList(shulker.getInventory().getContents()));
                    }
                }
            }
            if (isBundle(item.getType())) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    accumulateItemBreakdown(breakdown, bundleMeta.getItems());
                }
            }
        }
    }

    private static String csvField(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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

            // The Infinibundle itself never counts as an item (not even at 0 points - it just
            // doesn't appear at all), regardless of item-values config. But if real items ever
            // end up in its own physical bundle contents (separate from the plugin's virtual team
            // storage, which is tracked elsewhere), those are genuinely in the player's inventory
            // and should still score - so recurse into it, then skip straight to the next item.
            if (isInfinibundle(item)) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    processContainer(scoreMap, bundleMeta.getItems(), sourcePrefix + " > Bundle");
                }
                continue;
            }

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

            // Tipped arrows - score separately from potions (see arrowValues)
            if (item.getType() == Material.TIPPED_ARROW) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType pt = potionMeta.getBasePotionType();
                    String key = "ARROW_" + (pt != null ? pt.name() : "WATER");
                    baseValue = arrowValues.getOrDefault(key, 0.0);
                    itemId = key; // Use the specific arrow ID for scoring and collections
                }
            }

            // Enchantments
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasEnchants() || (item.getType() == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants())) {
                    baseValue += getTotalEnchantmentPoints(item);
                }
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

            // Recurse into bundles (any color). The Infinibundle is already handled/skipped above.
            if (isBundle(item.getType())) {
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
    /** True for any bundle variant - the plain BUNDLE material or any colored *_BUNDLE. */
    public static boolean isBundle(Material type) {
        return type == Material.BUNDLE || type.name().endsWith("_BUNDLE");
    }

    /**
     * True if this is the special per-team storage "Infinibundle" (identified by its unique
     * custom model data, 900009, set wherever it's created), as opposed to an ordinary bundle a
     * player found. Its contents mirror the team's storage, which is already tracked separately
     * via InfinibundleListener.getTeamStorage() - so it must never contribute its own point value
     * nor be recursed into, or team storage would get double-counted.
     */
    public static boolean isInfinibundle(ItemStack item) {
        if (item == null || !isBundle(item.getType()) || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 900009;
    }

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

        if (type == Material.OMINOUS_BOTTLE) {
            // Bukkit amplifier is 0-indexed; in-game the bottle shows tier as roman numeral I-V (1-5).
            // The amplifier component is likely only actually attached to the item's data for tier
            // 2+ (tier 1/amplifier 0 being the implicit default), so a null/failed detection here
            // means "no elevated tier detected" - i.e. tier 1 - not "not an ominous bottle at all".
            Integer amplifier = getOminousBottleAmplifier(meta);
            int tier = (amplifier != null) ? (amplifier + 1) : 1;
            return "OMINOUS_BOTTLE_TIER_" + tier;
        }

        return null;
    }

    // Reflection-based check for the Ominous Bottle's amplifier (its "tier"), so config can score
    // each tier separately. Tries a couple of plausible method names since this is a fairly new
    // (1.21 Trial Chambers) API and naming isn't consistent across Paper versions; falls back to
    // the flat OMINOUS_BOTTLE item-value if neither is found.
    private static java.lang.reflect.Method ominousBottleGetMethod = null;
    private static boolean ominousBottleMethodChecked = false;

    private static Integer getOminousBottleAmplifier(ItemMeta meta) {
        try {
            if (!ominousBottleMethodChecked) {
                ominousBottleMethodChecked = true;
                for (String methodName : new String[]{"getAmplifier", "getOminousBottleAmplifier"}) {
                    try {
                        ominousBottleGetMethod = meta.getClass().getMethod(methodName);
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // try the next candidate name
                    }
                }
            }
            if (ominousBottleGetMethod == null) return null;
            Object result = ominousBottleGetMethod.invoke(meta);
            return result instanceof Integer ? (Integer) result : null;
        } catch (Throwable t) {
            return null;
        }
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
        if (material == Material.TIPPED_ARROW) {
            return 0.0; // Tipped arrow scoring handled in calculateInventoryCounts
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
        List<Player> livePlayers = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR) {
                spectators.add(p);
            } else if (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) {
                livePlayers.add(p);
            }
        }

        if (spectators.isEmpty()) return;

        List<Map.Entry<String, Double>> teamScores = new ArrayList<>(calculateLiveTeamScores(livePlayers).entrySet());
        teamScores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Component tabFooter = buildTabFooter(teamScores);
        Component tabHeader = Component.text("=== Loot Hunt ===", NamedTextColor.GOLD);

        for (Player spec : spectators) {
            applySidebar(spec, teamScores);
            spec.sendPlayerListHeaderAndFooter(tabHeader, tabFooter);
        }
    }

    /**
     * Computes each team's live score from currently-online team members: personal item points
     * (per member) + team storage points (once per team, not once per member - counting it per
     * member was the source of the old double-counting bug) + the regular collection bonus +
     * kills/deaths (with the same diminishing-return formula used in final scoring).
     */
    private static Map<String, Double> calculateLiveTeamScores(List<Player> livePlayers) {
        Map<String, List<Player>> teamMembers = new LinkedHashMap<>();
        for (Player p : livePlayers) {
            teamMembers.computeIfAbsent(getPlayerTeamName(p), k -> new ArrayList<>()).add(p);
        }

        Map<String, Double> teamScores = new LinkedHashMap<>();
        for (Map.Entry<String, List<Player>> entry : teamMembers.entrySet()) {
            String teamName = entry.getKey();
            List<Player> members = entry.getValue();

            Set<String> ownedItemIds = new HashSet<>();
            double score = 0.0;

            // Personal inventories - once per member
            List<ItemStack> allTeamItems = new ArrayList<>();
            for (Player p : members) {
                Map<String, List<ItemEntry>> personal = new HashMap<>();
                List<ItemStack> contents = Arrays.asList(p.getInventory().getContents());
                processContainer(personal, contents, "Inventory");
                for (Map.Entry<String, List<ItemEntry>> e : personal.entrySet()) {
                    ownedItemIds.add(e.getKey());
                    score += e.getValue().stream().mapToDouble(en -> en.points).sum();
                }
                allTeamItems.addAll(contents);
            }

            // Team storage - once per team, not once per member
            List<ItemStack> teamStorage = InfinibundleListener.getTeamStorage(teamName);
            Map<String, List<ItemEntry>> storage = new HashMap<>();
            processContainer(storage, teamStorage, "Team Storage");
            for (Map.Entry<String, List<ItemEntry>> e : storage.entrySet()) {
                ownedItemIds.add(e.getKey());
                score += e.getValue().stream().mapToDouble(en -> en.points).sum();
            }
            allTeamItems.addAll(teamStorage);

            score += calculateCollectionBonus(ownedItemIds);
            score += calculateEnchantmentCollectionBonus(allTeamItems);

            for (Player p : members) {
                score += calculateKillScore(playerKillCounts.getOrDefault(p.getName().toUpperCase(), 0));
                score += calculateDeathScore(playerDeathCounts.getOrDefault(p.getName().toUpperCase(), 0));
            }

            teamScores.put(teamName, score);
        }

        return teamScores;
    }

    private static String getPlayerTeamName(Player p) {
        org.bukkit.scoreboard.Team t = p.getScoreboard().getEntryTeam(p.getName());
        return t != null ? t.getName() : "(Solo) " + p.getName();
    }

    /** Looks up a registered team's color (set via /loothunt jointeam), or null if none/solo. */
    private static TextColor getTeamColor2(String teamName) {
        try {
            org.bukkit.scoreboard.Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
            return team != null ? team.color() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Scoreboard sidebar entries are plain strings with no Adventure Component support, so team
     * color is baked in via legacy '§' color codes (which the client still renders correctly for
     * fake score-holder names) rather than left white.
     */
    private static String coloredSidebarEntry(String teamName) {
        TextColor color = getTeamColor2(teamName);
        Component comp = Component.text(teamName, color != null ? color : NamedTextColor.WHITE);
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(comp);
        return legacy.length() > 40 ? legacy.substring(0, 40) : legacy;
    }

    private static void applySidebar(Player spectator, List<Map.Entry<String, Double>> teamScores) {
        org.bukkit.scoreboard.Scoreboard board = spectatorBoards.computeIfAbsent(spectator.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());

        org.bukkit.scoreboard.Objective obj = board.getObjective("loothunt_sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("loothunt_sidebar", org.bukkit.scoreboard.Criteria.DUMMY,
                    Component.text("Loothunt - Teams", NamedTextColor.GOLD));
            obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        }

        Set<String> previousEntries = new HashSet<>(board.getEntries());
        Set<String> keptEntries = new HashSet<>();

        int limit = Math.min(SIDEBAR_MAX_ENTRIES, teamScores.size());
        for (int i = 0; i < limit; i++) {
            String teamName = teamScores.get(i).getKey();
            double score = teamScores.get(i).getValue();
            String entryName = coloredSidebarEntry(teamName);
            keptEntries.add(entryName);
            obj.getScore(entryName).setScore((int) Math.round(score));
        }

        // Drop entries that fell out of the top N (or teams no longer active)
        for (String entry : previousEntries) {
            if (!keptEntries.contains(entry)) {
                obj.getScore(entry).resetScore();
            }
        }

        if (spectator.getScoreboard() != board) {
            spectator.setScoreboard(board);
        }
    }

    private static Component buildTabFooter(List<Map.Entry<String, Double>> teamScores) {
        if (teamScores.isEmpty()) {
            return Component.text("No active survival/adventure players", NamedTextColor.GRAY);
        }

        Component footer = Component.text("Live Team Scores", NamedTextColor.GOLD, TextDecoration.BOLD);
        for (int i = 0; i < teamScores.size(); i++) {
            String teamName = teamScores.get(i).getKey();
            double score = teamScores.get(i).getValue();
            TextColor color = getTeamColor2(teamName);
            footer = footer.append(Component.newline())
                    .append(Component.text((i + 1) + ". ", NamedTextColor.GRAY))
                    .append(Component.text(teamName, color != null ? color : NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.format("%.1f", score), NamedTextColor.GREEN));
        }
        return footer;
    }

}