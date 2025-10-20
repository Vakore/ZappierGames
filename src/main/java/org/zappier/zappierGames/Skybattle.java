package org.zappier.zappierGames;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import org.bukkit.enchantments.Enchantment;

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

    private static final int[][] LOOT_SPAWNS = {
            {0, 114, 108, 0},
            {-3, 114, 105, 1},
            {3, 114, 105, 2},
            {62, 114, 88, 0},
            {59, 114, 85, 1},
            {65, 114, 85, 2},
            {103, 114, 32, 0},
            {100, 114, 35, 1},
            {100, 114, 29, 2},
            {103, 115, -32, 0},
            {100, 115, -29, 1},
            {100, 115, -35, 2},
            {62, 115, -88, 0},
            {65, 115, -85, 1},
            {59, 115, -85, 2},
            {0, 115, -108, 0},
            {3, 115, -105, 1},
            {-3, 115, -105, 2},
            {-62, 115, -88, 0},
            {-59, 115, -85, 1},
            {-65, 115, -85, 2},
            {-103, 115, -32, 0},
            {-100, 115, -35, 1},
            {-100, 115, -29, 2},
            {-103, 115, 32, 0},
            {-100, 115, 29, 1},
            {-100, 115, 35, 2},
            {-62, 115, 88, 0},
            {-65, 115, 85, 1},
            {-59, 115, 85, 2},
            //Candles
            {1, 120, 74, 3},
            {25, 120, 70, 3},
            {50, 120, 53, 3},
            {66, 120, 30, 3},
            {73, 120, 0, 3},
            {69, 120, -25, 3},
            {59, 120, -44, 3},
            {44, 120, -59, 3},
            {24, 120, -69, 3},
            {0, 120, -74, 3},
            {-23, 120, -70, 3},
            {-44, 120, -60, 3},
            {-58, 120, -47, 3},
            {-68, 120, -28, 3},
            {-72, 120, -5, 3},
            {-70, 120, 21, 3},
            {-59, 120, 43, 3},
            {-44, 120, 59, 3},
            {-23, 120, 69, 3},
            //cookies
            {0, 117, 53, 4},
            {31, 117, 43, 4},
            {50, 117, 16, 4},
            {50, 117, -16, 4},
            {31, 117, -43, 4},
            {0, 117, -53, 4},
            {-31, 117, -43, 4},
            {-50, 117, -16, 4},
            {-50, 117, 16, 4},
            {-31, 117, 43, 4},
            //cake1
            {0, 122, 36, 5},
            {36, 122, -1, 5},
            {0, 122, -36, 5},
            {-36, 122, 0, 5},
            //cake2
            {15, 133, 21, 6},
            {15, 133, -21, 6},
            {-15, 133, -21, 6},
            {-15, 133, 21, 6},
            //cake inside
            {-9, 132, 9, 7},
            {10, 132, 10, 7},
            {10, 132, -10, 7},
            {-9, 132, -9, 7},
            //cherry
            {-1, 131, 0, 8},
            {1, 131, 0, 8},
            //topmid
            {0, 147, 0, 9},
            //in cherry
            {0, 127, -1, 10}

    };

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

            // Load loot from skybattle_loot.txt
            try (Reader lootReader = new InputStreamReader(Skybattle.class.getResourceAsStream("/skybattle_loot.txt"))) {
                if (lootReader == null) {
                    LOGGER.severe("skybattle_loot.txt not found in resources! Ensure it's bundled in the JAR.");
                    return;
                }

                // Define tier mapping for loot spawns
                String[][] tierKeys = {
                        {"spawn1_1", "spawn1_2", "spawn1_3"},
                        {"spawn2_1", "spawn2_2", "spawn2_3"},
                        {"spawn3_1", "spawn3_2", "spawn3_3"},
                        {"ring1_1", "ring1_2", "ring1_3", "ring1_4", "ring1_5", "ring1_6", "ring1_7", "ring1_8", "ring1_9", "ring1_10"},
                        {"ring2_1", "ring2_2", "ring2_3"},
                        {"ring4_1", "ring4_2", "ring4_3"},
                        {"ring3_1", "ring3_2", "ring3_3"},
                        {"center1_1", "center1_2", "center1_3"},
                        {"center2_1", "center2_2", "center2_3"},
                        {"center3_1", "center3_2", "center3_3"},
                        {"center3_1", "center3_2", "center3_3"}
                };

                int[] tierVals = {
                     0,
                     0,
                     0,
                     -1,
                     0,
                     0,
                     0,
                     0,
                     0,
                     0,
                     0
                };

                for (int i = 0; i < tierVals.length; i++) {
                    if (tierVals[i] == -1) {continue;}
                    tierVals[i] = (int) (Math.random() * tierKeys[i].length);
                }

                Type lootType = new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();
                Map<String, List<Map<String, Object>>> lootData = gson.fromJson(lootReader, lootType);

                // Fill spawn chests with items based on tier from skybattle_loot.txt
                for (int i = 0; i < LOOT_SPAWNS.length; i++) {
                    String tierKey = "";
                    if (tierVals[LOOT_SPAWNS[i][3]] == -1) {
                        tierKey = tierKeys[LOOT_SPAWNS[i][3]][(int) (Math.random() * tierKeys[LOOT_SPAWNS[i][3]].length)];
                    } else {
                        tierKey = tierKeys[LOOT_SPAWNS[i][3]][tierVals[LOOT_SPAWNS[i][3]]];
                    }
                    List<Map<String, Object>> items = lootData.get(tierKey);

                    Block block = world.getBlockAt(LOOT_SPAWNS[i][0], LOOT_SPAWNS[i][1], LOOT_SPAWNS[i][2]);
                    if (block.getType() == Material.CHEST) {
                        Chest chest = (Chest) block.getState();
                        chest.getBlockInventory().clear();

                        if (items != null) {
                            for (Map<String, Object> itemData : items) {
                                int slot = ((Number) itemData.get("Slot")).intValue();
                                String itemId = (String) itemData.get("id");
                                int count = ((Number) itemData.get("Count")).intValue();
                                @SuppressWarnings("unchecked")
                                Map<String, Object> tag = (Map<String, Object>) itemData.get("tag");

                                Material material = Material.matchMaterial(itemId);
                                if (material == null) {
                                    LOGGER.warning("Invalid material ID: " + itemId + " at tier " + tierKey);
                                    continue;
                                }

                                ItemStack item = new ItemStack(material, count);
                                ItemMeta meta = item.getItemMeta();
                                if (meta != null && tag != null) {
                                    // Handle display name
                                    @SuppressWarnings("unchecked")
                                    Map<String, String> display = (Map<String, String>) tag.get("display");
                                    if (display != null) {
                                        String nameJson = display.get("Name");
                                        if (nameJson != null) {
                                            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameJson));
                                        }
                                    }

                                    // Handle CustomModelData
                                    if (tag.containsKey("CustomModelData")) {
                                        meta.setCustomModelData(((Number) tag.get("CustomModelData")).intValue());
                                    }

                                    // Handle Enchantments
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> enchantments = (List<Map<String, Object>>) tag.get("Enchantments");
                                    if (enchantments != null) {
                                        for (Map<String, Object> ench : enchantments) {
                                            String enchId = (String) ench.get("id");
                                            int level = ((Number) ench.get("lvl")).intValue();
                                            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchId));
                                            if (enchantment != null) {
                                                meta.addEnchant(enchantment, level, true);
                                            } else {
                                                LOGGER.warning("Invalid enchantment ID: " + enchId + " for item " + itemId);
                                            }
                                        }
                                    }

                                    // Handle Damage (for items like shields)
                                    if (tag.containsKey("Damage")) {
                                        //meta.set (((Number) tag.get("Damage")).intValue());
                                    }

                                    // Handle Effects for suspicious stew
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> effects = (List<Map<String, Object>>) tag.get("Effects");
                                    if (effects != null && material == Material.SUSPICIOUS_STEW) {
                                        for (Map<String, Object> effect : effects) {
                                            String effectId = (String) effect.get("EffectId");
                                            int duration = ((Number) effect.get("EffectDuration")).intValue();
                                            // Note: Bukkit API does not directly support setting Suspicious Stew effects via ItemMeta
                                            // You may need a custom solution or skip this for now
                                            LOGGER.warning("Suspicious Stew effects not supported in this version: " + effectId);
                                        }
                                    }

                                    item.setItemMeta(meta);
                                }

                                chest.getBlockInventory().setItem(slot, item);
                            }
                        } else {
                            LOGGER.warning("No items found for tier: " + tierKey);
                        }
                    } else {
                        Bukkit.broadcastMessage("POSITION AT " + LOOT_SPAWNS[i][0] + ", " + LOOT_SPAWNS[i][1] + ", " + LOOT_SPAWNS[i][2] + " IS NOT A CHEST!");
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load Skybattle loot: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // Teleport players after map is loaded
            for (Player p : Bukkit.getOnlinePlayers()) {
                int team = 0;
                if (p.getScoreboard().getEntryTeam(p.getName()).getName() != null) {
                    for (int i = 1; i <= 10; i++) {
                        if (p.getScoreboard().getEntryTeam(p.getName()).getName().equals(teamNames[i])) {
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
            for (Player player : world.getPlayers()) {
                Location center = player.getLocation();
                double startY = center.getY() - 8.0; // 10 below
                double endY   = center.getY() + 8.0; // 20 above
                GameMode gm = player.getGameMode();

                if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && Math.sqrt((center.getX() - 0.5) * (center.getX() - 0.5) + (center.getZ() - 0.5) * (center.getZ() - 0.5)) >= borderRadius) {
                    player.damage(4.0, voidSource);
                }

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
                        double prtx = 0.5 + cosCache[i] * borderRadius;
                        double prtz = 0.5 + sinCache[i] * borderRadius;
                        if (Math.abs(prtx - x) + Math.abs(prtz - z) > 24) {
                            continue;
                        }

                        player.spawnParticle(Particle.DUST, new Location(world, prtx, prty, prtz), 1, 0.0, 0.0, 0.0, 0.0, redDust);
                    }
                }
            }
        }
    }

    private static void clearArea(World world, int width, int minY, int maxY) {
        for (int x = -width; x <= width; x++) {
            for (int z = -width; z <= width; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBlock(world, x, y, z, Bukkit.createBlockData(Material.AIR));
                }
            }
        }
    }
}