package org.zappier.zappierGames;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

public class Skybattle {
    private static final Logger LOGGER = Logger.getLogger("Skybattle");
    private static JavaPlugin plugin;

    public static double borderRadius = 100.0;
    public static int borderTime = 20;
    public static int POINTS = 64;
    // Precompute unit circle offsets (recomputed when points or radius changes)
    private static double[] cosCache = new double[POINTS];
    private static double[] sinCache = new double[POINTS];

    static {
        for (int i = 0; i < POINTS; i++) {
            double angle = 2 * Math.PI * i / POINTS;
            cosCache[i] = Math.cos(angle);
            sinCache[i] = Math.sin(angle);
        }
    }

/*
0 115.00 -105
-62 115.00 -85
-100 115.00 -32
-100 115.00 32
-62 115.00 85
0 114.00 105
62 114.00 85
100 114.00 32
100 115.00 -32
62 115.00 -84
 */
    //TODO: Make this all insertable inside of a file
    private static final int[][] POSSIBLE_SPAWNS = {
        {0, 115, -105},
        {-62, 115, -85},
        {-100, 115, -32},
        {-100, 115, 32},
        {-62, 115, 85},
        {0, 114, 105},
        {62, 114, 85},
        {100, 114, 32},
        {100, 115, -32},
        {62, 115, -84}
    };

    private static final Map<Integer, List<int[]>> LOOT_SPAWNS = new HashMap<>();
    static {
        LOOT_SPAWNS.put(1, new ArrayList<>());
        LOOT_SPAWNS.get(1).add(new int[]{-117, 235, -196});
        LOOT_SPAWNS.get(1).add(new int[]{-8, 235, -216});
        LOOT_SPAWNS.get(1).add(new int[]{102, 235, -196});
        LOOT_SPAWNS.get(1).add(new int[]{-62, 101, -175});
        LOOT_SPAWNS.get(1).add(new int[]{185, 235, -92});
        LOOT_SPAWNS.get(1).add(new int[]{-59, 101, -12});
        LOOT_SPAWNS.get(1).add(new int[]{185, 234, 67});
        LOOT_SPAWNS.get(1).add(new int[]{-24, 101, 85});
        LOOT_SPAWNS.get(1).add(new int[]{-13, 234, 185});
        LOOT_SPAWNS.get(1).add(new int[]{96, 234, 165});
        LOOT_SPAWNS.put(2, new ArrayList<>());
        LOOT_SPAWNS.get(2).add(new int[]{-24, 101, -276});
        LOOT_SPAWNS.get(2).add(new int[]{-11, 235, -219});
        LOOT_SPAWNS.get(2).add(new int[]{96, 235, -196});
        LOOT_SPAWNS.get(2).add(new int[]{-59, 101, -172});
        LOOT_SPAWNS.get(2).add(new int[]{188, 235, -95});
        LOOT_SPAWNS.get(2).add(new int[]{-62, 101, -15});
        LOOT_SPAWNS.get(2).add(new int[]{188, 234, 64});
        LOOT_SPAWNS.get(2).add(new int[]{-21, 101, 88});
        LOOT_SPAWNS.get(2).add(new int[]{-11, 234, 188});
        LOOT_SPAWNS.get(2).add(new int[]{99, 234, 168});
        LOOT_SPAWNS.put(3, new ArrayList<>());
        LOOT_SPAWNS.get(3).add(new int[]{-21, 101, -279});
        LOOT_SPAWNS.get(3).add(new int[]{-13, 235, -216});
        LOOT_SPAWNS.get(3).add(new int[]{99, 235, -199});
        LOOT_SPAWNS.get(3).add(new int[]{-59, 101, -178});
        LOOT_SPAWNS.get(3).add(new int[]{185, 235, -98});
        LOOT_SPAWNS.get(3).add(new int[]{-59, 101, -66});
        LOOT_SPAWNS.get(3).add(new int[]{185, 234, 13});
        LOOT_SPAWNS.get(3).add(new int[]{-117, 235, 165});
        LOOT_SPAWNS.get(3).add(new int[]{-8, 234, 185});
        LOOT_SPAWNS.get(3).add(new int[]{102, 234, 165});
    }

    // Initialize the plugin instance (call from ZappierGames.onEnable)
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void placeBlock(World world, int x, int y, int z, BlockData data) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData().matches(data)) { return; }
        block.setBlockData(data, false); // false prevents physics/item drops
    }

    public static void start(World world, int borderSize) {
        if (plugin == null) {
            LOGGER.severe("Skybattle plugin instance not initialized! Call Skybattle.init(plugin) in onEnable.");
            return;
        }
        world.getEntities().stream()
                .filter(e -> !(e instanceof org.bukkit.entity.Player))
                .forEach(org.bukkit.entity.Entity::remove);


        borderTime = 20;
        borderRadius = 150.0;
        ZappierGames.gameMode = 10;

        clearArea(world, 120, 148, 180); // Clear ~240x240 area

        // Set world border
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);

        // Load the Skybattle map from skybattle_map.txt
        try (Reader reader = new InputStreamReader(Skybattle.class.getResourceAsStream("/skybattle_map.txt"))) {
            if (reader == null) {
                LOGGER.severe("skybattle_map.txt not found in resources! Ensure it's bundled in the JAR.");
                return;
            }

            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, List<List<Object>>>>() {}.getType();
            Map<String, List<List<Object>>> columns = gson.fromJson(reader, mapType);

            LOGGER.info("Loading Skybattle map: " + columns.size() + " columns.");

            // Process all columns synchronously
            for (Map.Entry<String, List<List<Object>>> columnEntry : columns.entrySet()) {
                String coord = columnEntry.getKey();
                String[] parts = coord.split(",");
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                List<List<Object>> columnData = columnEntry.getValue();

                for (List<Object> entry : columnData) {
                    int count = ((Number) entry.get(0)).intValue();
                    int yStart = ((Number) entry.get(1)).intValue();
                    String materialStr = (String) entry.get(2);
                    @SuppressWarnings("unchecked")
                    Map<String, String> properties = entry.size() > 3 ? (Map<String, String>) entry.get(3) : null;

                    StringBuilder dataStr = new StringBuilder(materialStr);
                    if (properties != null && !properties.isEmpty()) {
                        dataStr.append("[");
                        String[] props = properties.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .toArray(String[]::new);
                        dataStr.append(String.join(",", props));
                        dataStr.append("]");
                    }

                    BlockData data = Bukkit.createBlockData(dataStr.toString());
                    for (int i = 0; i < count; i++) {
                        placeBlock(world, x, yStart + i, z, data);
                    }
                }
            }

            LOGGER.info("Skybattle map loaded successfully!");

            // Clear all concrete from players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.getInventory().remove(Material.BLACK_CONCRETE);
                p.getInventory().remove(Material.BLUE_CONCRETE);
                p.getInventory().remove(Material.BROWN_CONCRETE);
                p.getInventory().remove(Material.CYAN_CONCRETE);
                p.getInventory().remove(Material.GRAY_CONCRETE);
                p.getInventory().remove(Material.GREEN_CONCRETE);
                p.getInventory().remove(Material.LIGHT_BLUE_CONCRETE);
                p.getInventory().remove(Material.LIGHT_GRAY_CONCRETE);
                p.getInventory().remove(Material.LIME_CONCRETE);
                p.getInventory().remove(Material.MAGENTA_CONCRETE);
                p.getInventory().remove(Material.ORANGE_CONCRETE);
                p.getInventory().remove(Material.PINK_CONCRETE);
                p.getInventory().remove(Material.PURPLE_CONCRETE);
                p.getInventory().remove(Material.RED_CONCRETE);
                p.getInventory().remove(Material.WHITE_CONCRETE);
                p.getInventory().remove(Material.YELLOW_CONCRETE);
            }

            // Randomize spawn locations
            List<int[]> shuffledSpawns = new ArrayList<>(Arrays.asList(POSSIBLE_SPAWNS));
            Collections.shuffle(shuffledSpawns);

            // Assign spawns to teams 1-10
            Map<Integer, Location> teamSpawns = new HashMap<>();
            for (int team = 1; team <= 10; team++) {
                int[] pos = shuffledSpawns.get(team - 1);
                teamSpawns.put(team, new Location(world, pos[0] + 0.5, pos[1], pos[2] + 0.5));
            }

            // Define team wool colors
            Material[] teamWools = {
                    null,  // Team 0 unused
                    Material.RED_WOOL,       // Team 1
                    Material.BLUE_WOOL,      // Team 2
                    Material.LIME_WOOL,      // Team 3
                    Material.GREEN_WOOL,     // Team 4
                    Material.ORANGE_WOOL,    // Team 5
                    Material.WHITE_WOOL,     // Team 6
                    Material.LIGHT_BLUE_WOOL,// Team 7
                    Material.MAGENTA_WOOL,   // Team 8
                    Material.PURPLE_WOOL,    // Team 9
                    Material.YELLOW_WOOL     // Team 10
            };

            String[] teamNames = {
                    null,  // Team 0 unused
                    "RED",       // Team 1
                    "DARK_BLUE",      // Team 2
                    "GREEN",      // Team 3
                    "DARK_GREEN",     // Team 4
                    "GOLD",    // Team 5
                    "WHITE",     // Team 6
                    "AQUA",// Team 7
                    "LIGHT_PURPLE",   // Team 8
                    "DARK_PURPLE",    // Team 9
                    "YELLOW"     // Team 10
            };

            // Replace wool for each team island
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            for (int team = 1; team <= 10; team++) {
                Location spawn = teamSpawns.get(team);
                Material teamWool = teamWools[team];
                int spawnX = spawn.getBlockX();
                int spawnY = spawn.getBlockY();
                int spawnZ = spawn.getBlockZ();
                for (int dx = -8; dx <= 8; dx++) {
                    for (int dy = -9; dy <= -1; dy++) {  // ~-9 to ~-1 to avoid replacing at ~ level
                        for (int dz = -8; dz <= 8; dz++) {
                            Block block = world.getBlockAt(spawnX + dx, spawnY + dy, spawnZ + dz);
                            if (block.getType() == Material.WHITE_WOOL) {
                                block.setType(teamWool);
                            }
                        }
                    }
                }
            }

            // Fill spawn chests with items based on tier (1: golden apple, 2: iron sword, 3: iron boots)
            Material[] tierItems = {
                    null,               // Tier 0 unused
                    Material.GOLDEN_APPLE,  // Tier 1
                    Material.IRON_SWORD,    // Tier 2
                    Material.IRON_BOOTS     // Tier 3
            };

            for (Map.Entry<Integer, List<int[]>> entry : LOOT_SPAWNS.entrySet()) {
                int tier = entry.getKey();
                Material item = tierItems[tier];
                if (item == null) continue;

                for (int[] pos : entry.getValue()) {
                    Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
                    if (block.getType() == Material.CHEST) {
                        Chest chest = (Chest) block.getState();
                        chest.getBlockInventory().addItem(new ItemStack(item));
                    }
                }
            }

            // Teleport players after map is loaded
            for (Player p : Bukkit.getOnlinePlayers()) {
                int team = 0;
                if (LootHunt.playerTeams.get(p.getName().toUpperCase()) != null) {
                    Bukkit.broadcastMessage(LootHunt.playerTeams.get(p.getName().toUpperCase()));
                    for (int i = 1; i <= 10; i++) {
                        Bukkit.broadcastMessage(teamNames[i]);
                        if (LootHunt.playerTeams.get(p.getName().toUpperCase()).equals(teamNames[i])) {
                            team = i;
                        }
                    }
                }
                Location spawn = teamSpawns.get(team);
                if (team == 0) {
                    p.teleport(new Location(world, 0.5, 150, 0.5));
                } else {
                    p.teleport(spawn);
                }
                p.getInventory().clear();
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setSaturation(20.0f);
                p.setExperienceLevelAndProgress(0);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                p.sendTitle(ChatColor.BLUE + "Skybattle", ChatColor.BLUE + "Fight to survive!", 10, 70, 20);
                p.sendMessage(ChatColor.GREEN + "Skybattle started! Survive on the platform!");
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to load Skybattle map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void run(World world) {
        double Y_STEP = 5.0;    // vertical spacing between circles
        borderTime--;
        if (borderTime < 0) {
            borderTime = 20;
            borderRadius -= 0.5;
            if (borderRadius < 0) {
                borderRadius = 0;
            }

            // Loop through all players in the world
            Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 20, 20), 1.8f);
            DamageSource voidSource = DamageSource.builder(DamageType.OUT_OF_WORLD).build();
            // For each player in world, draw a column centered on the player's position:
            for (Player player : world.getPlayers()) {
                Location center = player.getLocation();
                double startY = center.getY() - 8.0; // 10 below
                double endY   = center.getY() + 8.0; // 20 above
                GameMode gm = player.getGameMode();

                if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && Math.sqrt((center.getX() - 0.5) * (center.getX() - 0.5) + (center.getZ() - 0.5) * (center.getZ() - 0.5)) >= borderRadius) {
                    player.damage(4.0, voidSource);
                }

                // iterate vertical layers first, then points (keeps cos/sin precomputed)
                for (double prty = startY; prty <= endY; prty += Y_STEP) {
                    double x = center.x();
                    double z = center.z();

                    int sampleRate = 1;
                    if (borderRadius < 1) {
                        sampleRate = 32;
                    } else if (borderRadius < 20) {
                        sampleRate = 8;
                    } else if (borderRadius < 30) {
                        sampleRate = 4;
                    } else if (borderRadius < 50) {
                        sampleRate = 2;
                    }
                    for (int i = 0; i < POINTS; i += sampleRate) {
                        //if ()
                        double prtx = 0.5 + cosCache[i] * borderRadius;
                        double prtz = 0.5 + sinCache[i] * borderRadius;
                        if (Math.abs(prtx - x) + Math.abs(prtz - z) > 24) {
                            continue;
                        }

                        // Use the overload that passes data (DustOptions).
                        // count = 1, offsets = 0, extra = 0, data = redDust
                        // Player.spawnParticle has overload: spawnParticle(Particle, Location, int count, double offX, offY, offZ, double extra, T data)
                        player.spawnParticle(Particle.DUST, new Location(world, prtx, prty, prtz), 1, 0.0, 0.0, 0.0, 0.0, redDust);
                    }
                }
            }
        }
    }



    private static void clearArea(World world, int width, int minY, int maxY) {
        // Clear a rectangular area (~240x240)
        for (int x = -width; x <= width; x++) {
            for (int z = -width; z <= width; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBlock(world, x, y, z, Bukkit.createBlockData(Material.AIR));
                }
            }
        }
    }
}