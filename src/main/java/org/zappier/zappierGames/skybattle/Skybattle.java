package org.zappier.zappierGames.skybattle;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.enchantments.Enchantment;
import org.zappier.zappierGames.ZappierGames;

public class Skybattle {
    private static final Logger LOGGER = Logger.getLogger("Skybattle");
    private static JavaPlugin plugin;

    public static double borderRadius = 100.0;
    public static int borderTime = 20;
    public static int POINTS = 64;
    public static int startTime = 0;
    private static double[] cosCache = new double[POINTS];
    private static double[] sinCache = new double[POINTS];
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    public static int[] TWISTS = {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};

    static {
        for (int i = 0; i < POINTS; i++) {
            double angle = 2 * Math.PI * i / POINTS;
            cosCache[i] = Math.cos(angle);
            sinCache[i] = Math.sin(angle);
        }
    }

    private static final int[][] POSSIBLE_SPAWNS = {
            {0, 115, -105}, {-62, 115, -85}, {-100, 115, -32}, {-100, 115, 32},
            {-62, 115, 85}, {0, 114, 105}, {62, 114, 85}, {100, 114, 32},
            {100, 115, -32}, {62, 115, -84}
    };

    private static final int[][] LOOT_SPAWNS = {
            {0, 114, 108, 0}, {-3, 114, 105, 1}, {3, 114, 105, 2},
            {62, 114, 88, 0}, {59, 114, 85, 1}, {65, 114, 85, 2},
            {103, 114, 32, 0}, {100, 114, 35, 1}, {100, 114, 29, 2},
            {103, 115, -32, 0}, {100, 115, -29, 1}, {100, 115, -35, 2},
            {62, 115, -88, 0}, {65, 115, -85, 1}, {59, 115, -85, 2},
            {0, 115, -108, 0}, {3, 115, -105, 1}, {-3, 115, -105, 2},
            {-62, 115, -88, 0}, {-59, 115, -85, 1}, {-65, 115, -85, 2},
            {-103, 115, -32, 0}, {-100, 115, -35, 1}, {-100, 115, -29, 2},
            {-103, 115, 32, 0}, {-100, 115, 29, 1}, {-100, 115, 35, 2},
            {-62, 115, 88, 0}, {-65, 115, 85, 1}, {-59, 115, 85, 2},
            {1, 120, 74, 3}, {25, 120, 70, 3}, {50, 120, 53, 3},
            {66, 120, 30, 3}, {73, 120, 0, 3}, {69, 120, -25, 3},
            {59, 120, -44, 3}, {44, 120, -59, 3}, {24, 120, -69, 3},
            {0, 120, -74, 3}, {-23, 120, -70, 3}, {-44, 120, -60, 3},
            {-58, 120, -47, 3}, {-68, 120, -28, 3}, {-72, 120, -5, 3},
            {-70, 120, 21, 3}, {-59, 120, 43, 3}, {-44, 120, 59, 3},
            {-23, 120, 69, 3}, {0, 117, 53, 4}, {31, 117, 43, 4},
            {50, 117, 16, 4}, {50, 117, -16, 4}, {31, 117, -43, 4},
            {0, 117, -53, 4}, {-31, 117, -43, 4}, {-50, 117, -16, 4},
            {-50, 117, 16, 4}, {-31, 117, 43, 4}, {0, 122, 36, 5},
            {36, 122, -1, 5}, {0, 122, -36, 5}, {-36, 122, 0, 5},
            {15, 133, 21, 6}, {15, 133, -21, 6}, {-15, 133, -21, 6},
            {-15, 133, 21, 6}, {-9, 132, 9, 7}, {10, 132, 10, 7},
            {10, 132, -10, 7}, {-9, 132, -9, 7}, {-1, 131, 0, 8},
            {1, 131, 0, 8}, {0, 147, 0, 9}, {0, 127, -1, 10}
    };

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void placeBlock(World world, int x, int y, int z, BlockData data) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData().matches(data)) { return; }
        block.setBlockData(data, false);
    }

    public static void start(World world, int borderSize) {
        if (plugin == null) {
            LOGGER.severe("Skybattle plugin instance not initialized! Call Skybattle.init(plugin) in onEnable.");
            return;
        }
        world.getEntities().stream()
                .filter(e -> !(e instanceof org.bukkit.entity.Player))
                .forEach(org.bukkit.entity.Entity::remove);

        startTime = 20 * 15;
        borderTime = 20;
        borderRadius = 135.0;
        ZappierGames.gameMode = 10;

        clearArea(world, -120, -120, 240, 240, 148, 180);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);

        try (Reader reader = new InputStreamReader(Skybattle.class.getResourceAsStream("/skybattle_maps/skybattle_map.txt"))) {
            if (reader == null) {
                LOGGER.severe("skybattle_maps/skybattle_map.txt not found in resources!");
                return;
            }

            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, List<List<Object>>>>() {}.getType();
            Map<String, List<List<Object>>> columns = gson.fromJson(reader, mapType);

            LOGGER.info("Loading Skybattle map: " + columns.size() + " columns.");
            for (Map.Entry<String, List<List<Object>>> columnEntry : columns.entrySet()) {
                String[] parts = columnEntry.getKey().split(",");
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                List<List<Object>> columnData = columnEntry.getValue();

                for (List<Object> entry : columnData) {
                    int count = ((Number) entry.get(0)).intValue();
                    int yStart = ((Number) entry.get(1)).intValue();
                    String materialStr = (String) entry.get(2);
                    Map<String, String> properties = entry.size() > 3 ? (Map<String, String>) entry.get(3) : null;

                    StringBuilder dataStr = new StringBuilder(materialStr);
                    if (properties != null && !properties.isEmpty()) {
                        dataStr.append("[").append(String.join(",", properties.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new))).append("]");
                    }

                    BlockData data = Bukkit.createBlockData(dataStr.toString());
                    for (int i = 0; i < count; i++) {
                        placeBlock(world, x, yStart + i, z, data);
                    }
                }
            }

            LOGGER.info("Skybattle map loaded successfully!");

            // Clear all concrete
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                p.clearActivePotionEffects();
                p.setCollidable(true);

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

            List<int[]> shuffledSpawns = new ArrayList<>(Arrays.asList(POSSIBLE_SPAWNS));
            Collections.shuffle(shuffledSpawns);

            Map<Integer, Location> teamSpawns = new HashMap<>();
            for (int team = 1; team <= 10; team++) {
                int[] pos = shuffledSpawns.get(team - 1);
                teamSpawns.put(team, new Location(world, pos[0] + 0.5, pos[1], pos[2] + 0.5));

                for (int x1 = -2; x1 <= 2; x1++) {
                    for (int z1 = -2; z1 <= 2; z1++) {
                        for (int y1 = -3; y1 <= 3; y1++) {
                            if (Math.abs(x1) == 2 || Math.abs(y1) == 3 || Math.abs(z1) == 2) {
                                placeBlock(world, pos[0] + x1, 11 + pos[1] + y1, pos[2] + z1, Bukkit.createBlockData(Material.BARRIER));
                            } else {
                                placeBlock(world, pos[0] + x1, 11 + pos[1] + y1, pos[2] + z1, Bukkit.createBlockData(Material.AIR));
                            }
                        }
                    }
                }
            }

            Material[] teamWools = {
                    null, Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL,
                    Material.GREEN_WOOL, Material.ORANGE_WOOL, Material.WHITE_WOOL,
                    Material.LIGHT_BLUE_WOOL, Material.MAGENTA_WOOL, Material.PURPLE_WOOL,
                    Material.YELLOW_WOOL
            };

            String[] teamNames = {
                    null, "RED", "BLUE", "GREEN", "DARK_GREEN", "GOLD",
                    "WHITE", "AQUA", "LIGHT_PURPLE", "DARK_PURPLE", "YELLOW"
            };

            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            for (int team = 1; team <= 10; team++) {
                Location spawn = teamSpawns.get(team);
                Material teamWool = teamWools[team];
                if (scoreboard.getTeam(teamNames[team]) != null) {
                    scoreboard.getTeam(teamNames[team]).setAllowFriendlyFire(true);
                }
                int spawnX = spawn.getBlockX();
                int spawnY = spawn.getBlockY();
                int spawnZ = spawn.getBlockZ();
                for (int dx = -8; dx <= 8; dx++) {
                    for (int dy = -9; dy <= -1; dy++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            Block block = world.getBlockAt(spawnX + dx, spawnY + dy, spawnZ + dz);
                            if (block.getType() == Material.WHITE_WOOL) {
                                block.setType(teamWool);
                            }
                        }
                    }
                }
            }

            try (Reader lootReader = new InputStreamReader(Skybattle.class.getResourceAsStream("/skybattle_maps/skybattle_loot.txt"))) {
                if (lootReader == null) {
                    LOGGER.severe("skybattle_maps/skybattle_loot.txt not found in resources!");
                    return;
                }

                String[][] tierKeys = {
                        {"spawn1_1", "spawn1_2", "spawn1_3"}, {"spawn2_1", "spawn2_2", "spawn2_3"},
                        {"spawn3_1", "spawn3_2", "spawn3_3"}, {"ring1_1", "ring1_2", "ring1_3", "ring1_4", "ring1_5", "ring1_6", "ring1_7", "ring1_8", "ring1_9", "ring1_10"},
                        {"ring2_1", "ring2_2", "ring2_3"}, {"ring4_1", "ring4_2", "ring4_3"},
                        {"ring3_1", "ring3_2", "ring3_3"}, {"center1_1", "center1_2", "center1_3"},
                        {"center2_1", "center2_2", "center2_3"}, {"center3_1", "center3_2", "center3_3"},
                        {"center3_1", "center3_2", "center3_3"}
                };

                int[] tierVals = {0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0};
                for (int i = 0; i < tierVals.length; i++) {
                    if (tierVals[i] == -1) continue;
                    tierVals[i] = (int) (Math.random() * tierKeys[i].length);
                }

                Type lootType = new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();
                Map<String, List<Map<String, Object>>> lootData = gson.fromJson(lootReader, lootType);

                for (int i = 0; i < LOOT_SPAWNS.length; i++) {
                    String tierKey = tierVals[LOOT_SPAWNS[i][3]] == -1 ?
                            tierKeys[LOOT_SPAWNS[i][3]][(int) (Math.random() * tierKeys[LOOT_SPAWNS[i][3]].length)] :
                            tierKeys[LOOT_SPAWNS[i][3]][tierVals[LOOT_SPAWNS[i][3]]];
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
                                Map<String, Object> tag = (Map<String, Object>) itemData.get("tag");

                                Material material = Material.matchMaterial(itemId);
                                if (material == null) {
                                    LOGGER.warning("Invalid material ID: " + itemId + " at tier " + tierKey);
                                    continue;
                                }

                                ItemStack item = new ItemStack(material, count);
                                ItemMeta meta = item.getItemMeta();
                                if (meta != null && tag != null) {
                                    Map<String, String> display = (Map<String, String>) tag.get("display");
                                    if (display != null) {
                                        Object nameObj = display.get("Name");
                                        if (nameObj != null) {
                                            try {
                                                Component nameComponent;

                                                if (nameObj instanceof String) {
                                                    // Case 1: Escaped JSON string
                                                    String nameJson = (String) nameObj;
                                                    String unescapedJson = nameJson.replace("\\\"", "\"");
                                                    nameComponent = GsonComponentSerializer.gson().deserialize(unescapedJson);
                                                } else if (nameObj instanceof Map) {
                                                    // Case 2: Direct JSON object (Gson parsed it)
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> nameMap = (Map<String, Object>) nameObj;
                                                    String jsonString = gson.toJson(nameMap);
                                                    nameComponent = GsonComponentSerializer.gson().deserialize(jsonString);
                                                } else {
                                                    throw new IllegalArgumentException("Unsupported Name type: " + nameObj.getClass());
                                                }

                                                meta.displayName(nameComponent);
                                            } catch (Exception e) {
                                                LOGGER.warning("Failed to parse JSON name: " + nameObj + " - " + e.getMessage());
                                                // Fallback: plain text
                                                if (nameObj instanceof String) {
                                                    meta.setDisplayName((String) nameObj);
                                                }
                                            }
                                        }
                                    }
                                    if (tag.containsKey("CustomModelData")) {
                                        meta.setCustomModelData(((Number) tag.get("CustomModelData")).intValue());
                                    }
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
                                    List<Map<String, Object>> effects = (List<Map<String, Object>>) tag.get("Effects");
                                    if (effects != null && material == Material.SUSPICIOUS_STEW) {
                                        LOGGER.warning("Suspicious Stew effects not supported: " + effects);
                                    }
                                    item.setItemMeta(meta);
                                    if (tag.containsKey("Damage")) {
                                        // Handle damage if needed
                                        item.setDurability(((Number) tag.get("Damage")).shortValue());
                                    }
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

            PotionEffect slowEffect = new PotionEffect(PotionEffectType.SLOW_FALLING, 5 * 20, 1);
            PotionEffect invincibleEffect = new PotionEffect(PotionEffectType.RESISTANCE, 20 * 17, 9);
            for (Player p : Bukkit.getOnlinePlayers()) {
                int team = 0;
                if (p.getScoreboard().getEntryTeam(p.getName()) != null) {
                    for (int i = 1; i <= 10; i++) {
                        if (p.getScoreboard().getEntryTeam(p.getName()).getName().equals(teamNames[i])) {
                            team = i;
                        }
                    }
                }
                Location spawn = teamSpawns.get(team);
                if (team == 0) {
                    p.teleport(new Location(world, 0.5, 150, 0.5));
                    continue;
                } else {
                    p.teleport(spawn.add(0.0, 11.0, 0.0));
                    p.setGameMode(GameMode.SURVIVAL);
                    p.addPotionEffect(slowEffect);
                    p.addPotionEffect(invincibleEffect);
                }

                // Team-specific armor and concrete
                String teamName = teamNames[team].toUpperCase();
                int color = -1;
                Material trimMaterial = null;
                Material concrete = null;
                switch (teamName) {
                    case "RED": color = 16711680; trimMaterial = Material.REDSTONE; concrete = Material.RED_CONCRETE; break;
                    case "BLUE": color = 255; trimMaterial = Material.LAPIS_LAZULI; concrete = Material.BLUE_CONCRETE; break;
                    case "GREEN": color = 65280; trimMaterial = Material.EMERALD; concrete = Material.LIME_CONCRETE; break;
                    case "DARK_GREEN": color = 34816; trimMaterial = Material.EMERALD; concrete = Material.GREEN_CONCRETE; break;
                    case "GOLD": color = 16746496; trimMaterial = Material.GOLD_INGOT; concrete = Material.ORANGE_CONCRETE; break;
                    case "WHITE": color = 16777215; trimMaterial = Material.QUARTZ; concrete = Material.WHITE_CONCRETE; break;
                    case "AQUA": color = 7829503; trimMaterial = Material.DIAMOND; concrete = Material.LIGHT_BLUE_CONCRETE; break;
                    case "LIGHT_PURPLE": color = 16075236; trimMaterial = Material.AMETHYST_SHARD; concrete = Material.MAGENTA_CONCRETE; break;
                    case "DARK_PURPLE": color = 10881943; trimMaterial = Material.AMETHYST_SHARD; concrete = Material.PURPLE_CONCRETE; break;
                    case "YELLOW": color = 16776960; trimMaterial = Material.GOLD_INGOT; concrete = Material.YELLOW_CONCRETE; break;
                    default: continue;
                }

                p.getInventory().clear();


                // Load saved slots
                File slotsFile = new File(plugin.getDataFolder(), "skybattle_slots.json");
                Map<String, int[]> savedSlots = new HashMap<>();
                gson = new Gson();
                if (slotsFile.exists()) {
                    try (FileReader reader2 = new FileReader(slotsFile)) {
                        Type slotsType = new TypeToken<Map<String, int[]>>() {}.getType();
                        savedSlots = gson.fromJson(reader2, slotsType);
                    } catch (IOException e) {
                        LOGGER.warning("Failed to load skybattle_slots.json: " + e.getMessage());
                    }
                }
                int[] slots = savedSlots.getOrDefault(p.getUniqueId().toString(), new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1});

                // Equip concrete
                ItemStack concreteStack = new ItemStack(concrete, 64);
                if (slots[0] == 0 || slots[0] == 41) p.getInventory().setItemInOffHand(concreteStack);
                else if (slots[0] >= 1 && slots[0] <= 36) p.getInventory().setItem(slots[0] - 1, concreteStack);
                else p.getInventory().addItem(concreteStack);

                // Equip stone sword
                ItemStack sword = new ItemStack(Material.STONE_SWORD);
                if (slots[1] >= 1 && slots[1] <= 36) p.getInventory().setItem(slots[1] - 1, sword);
                else p.getInventory().addItem(sword);

                // Equip bow
                ItemStack bow = new ItemStack(Material.BOW);
                if (slots[2] >= 1 && slots[2] <= 36) p.getInventory().setItem(slots[2] - 1, bow);
                else p.getInventory().addItem(bow);

                // Equip iron pickaxe
                ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
                pickaxe.addEnchantment(Enchantment.EFFICIENCY, 3);
                if (slots[3] >= 1 && slots[3] <= 36) p.getInventory().setItem(slots[3] - 1, pickaxe);
                else p.getInventory().addItem(pickaxe);

                // Equip cooked beef
                ItemStack beef = new ItemStack(Material.COOKED_BEEF, 8);
                if (slots[4] >= 1 && slots[4] <= 36) p.getInventory().setItem(slots[4] - 1, beef);
                else p.getInventory().addItem(beef);

                // Equip arrows
                ItemStack arrows = new ItemStack(Material.ARROW, 2);
                if (slots[5] >= 1 && slots[5] <= 36) p.getInventory().setItem(slots[5] - 1, arrows);
                else p.getInventory().addItem(arrows);

                // Equip leather boots
                ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
                LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
                bootsMeta.setColor(Color.fromRGB(color));
                boots.setItemMeta(bootsMeta);
                if (slots[6] >= 37 && slots[6] <= 39) p.getInventory().setBoots(boots);
                else p.getInventory().setBoots(boots);

                // Equip leather leggings
                ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                LeatherArmorMeta leggingsMeta = (LeatherArmorMeta) leggings.getItemMeta();
                leggingsMeta.setColor(Color.fromRGB(color));
                leggings.setItemMeta(leggingsMeta);
                if (slots[7] >= 37 && slots[7] <= 39) p.getInventory().setLeggings(leggings);
                else p.getInventory().setLeggings(leggings);

                // Equip iron chestplate
                ItemStack chestplate = new ItemStack(Material.IRON_CHESTPLATE);
                ArmorMeta chestplateMeta = (ArmorMeta) chestplate.getItemMeta();
                TrimPattern trimPattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft("sentry"));
                TrimMaterial trimMat = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(trimMaterial.getKey().getKey()));
                if (trimPattern != null && trimMat != null) {
                    chestplateMeta.setTrim(new ArmorTrim(trimMat, trimPattern));
                }
                chestplate.setItemMeta(chestplateMeta);
                if (slots[8] >= 37 && slots[8] <= 39) p.getInventory().setChestplate(chestplate);
                else p.getInventory().setChestplate(chestplate);

                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setSaturation(20.0f);
                p.setExperienceLevelAndProgress(0);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                p.sendTitle(ChatColor.BLUE + "Skybattle", ChatColor.BLUE + "Fight to survive!", 10, 70, 20);
                Audience audience = Bukkit.getServer();

                // The name of the custom yellow color is likely "yellow"
                // but tellraw's 'yeloow' seems like a typo, so we'll use a standard
                // color for consistency (e.g., GOLD) or a Hex color (e.g., #FFFF00).
                // Since you wrote "yeloow" and in-game yellow is GOLD in Adventure, we'll use a hex color for an accurate bright yellow.

                // Define the content of the message using MiniMessage format
                String messageContent = """
        <green>---------------------------------</green>
        <green>---------------------------------</green>
        <white></white>
        <red><b>MAP - Birthday Party</b></red>
        <#FFFF00>MADE BY - Slushly_, Moyaii,\nGamingUni456, DodieIsShining,\nOli_hockey14, and Hexatope.</#FFFF00>
        <white></white>
        <green>---------------------------------</green>
        <green>---------------------------------</green>
        """;

                // Parse the MiniMessage string into a Component
                Component message = miniMessage.deserialize(messageContent);

                // Send the complete component message to all players
                audience.sendMessage(message);
                p.updateInventory();
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to load Skybattle map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void run(World world) {
        if (startTime > 0) {
            startTime--;
            if ((startTime / 20) <= 10 && (double)startTime / 20.0 == Math.round(startTime / 20.0)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    float pitch = ((float)startTime) / 20.0f;
                    pitch = 10.0f - pitch;
                    pitch /= 20.0f;
                    pitch += 0.5f;
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.PLAYERS, 1.0f, pitch);
                    p.sendTitle(ChatColor.RED + "" + (startTime / 20), ChatColor.RED + "Get Ready!");
                }
            }
        } else if (startTime == 0) {
            world.setGameRule(GameRule.DO_TILE_DROPS, false);
            world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setDifficulty(Difficulty.HARD);
            world.setTime(6000);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            for (int i = 0; i < POSSIBLE_SPAWNS.length; i++) {
                int[] pos = POSSIBLE_SPAWNS[i];
                clearArea(world, pos[0] - 2, pos[2] - 2, 5, 5, pos[1] + 8, pos[1] + 16);
            }
            startTime--;

            Map<String, int[]> savedSlots = new HashMap<>();
            File slotsFile = new File(plugin.getDataFolder(), "skybattle_slots.json");
            Gson gson = new Gson();
            if (slotsFile.exists()) {
                try (FileReader reader = new FileReader(slotsFile)) {
                    Type slotsType = new TypeToken<Map<String, int[]>>() {}.getType();
                    savedSlots = gson.fromJson(reader, slotsType);
                } catch (IOException e) {
                    LOGGER.warning("Failed to load skybattle_slots.json: " + e.getMessage());
                }
            }

            for (Player p : world.getPlayers()) {
                int[] slots = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1};
                PlayerInventory inv = p.getInventory();
                ItemStack[] offhand = new ItemStack[]{inv.getItemInOffHand()};
                ItemStack[] mainInventory = inv.getContents();
                ItemStack[] armor = inv.getArmorContents();
                ItemStack[] combined = new ItemStack[1 + mainInventory.length + armor.length];

                combined[0] = offhand[0];
                System.arraycopy(mainInventory, 0, combined, 1, mainInventory.length);
                System.arraycopy(armor, 0, combined, 1 + mainInventory.length, armor.length);

                for (int i = 0; i < combined.length; i++) {
                    if (combined[i] == null) continue;
                    String slotStr = combined[i].getType().toString();
                    if (slotStr.contains("CONCRETE")) slots[0] = i;
                    else if (slotStr.contains("STONE_SWORD")) slots[1] = i;
                    else if (slotStr.contains("BOW")) slots[2] = i;
                    else if (slotStr.contains("IRON_PICKAXE")) slots[3] = i;
                    else if (slotStr.contains("COOKED_BEEF")) slots[4] = i;
                    else if (slotStr.contains("ARROW")) slots[5] = i;
                    else if (slotStr.contains("LEATHER_BOOTS")) slots[6] = i;
                    else if (slotStr.contains("LEATHER_LEGGINGS")) slots[7] = i;
                    else if (slotStr.contains("IRON_CHESTPLATE")) slots[8] = i;
                }

                // Save slots for this player
                savedSlots.put(p.getUniqueId().toString(), slots);
            }



            try (FileWriter writer = new FileWriter(slotsFile)) {
                gson.toJson(savedSlots, writer);
            } catch (IOException e) {
                LOGGER.warning("Failed to save skybattle_slots.json: " + e.getMessage());
            }
        }

        if (borderTime % 10 == 0) {
            for (Player p : world.getPlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL) {
                    continue;
                }

                Team team = p.getScoreboard().getEntryTeam(p.getName());
                if (team == null) {
                    continue;
                }


                PlayerInventory inv = p.getInventory();
                ItemStack[] mainInventory = inv.getContents();
                ItemStack[] armor = inv.getArmorContents();
                ItemStack[] combined = new ItemStack[1 + mainInventory.length + armor.length];
                System.arraycopy(mainInventory, 0, combined, 1, mainInventory.length);
                System.arraycopy(armor, 0, combined, 1 + mainInventory.length, armor.length);

                String teamName = team.getName().toUpperCase();
                Material concrete = null;
                switch (teamName) {
                    case "RED":
                        concrete = Material.RED_CONCRETE;
                        break;
                    case "BLUE":
                        concrete = Material.BLUE_CONCRETE;
                        break;
                    case "GREEN":
                        concrete = Material.LIME_CONCRETE;
                        break;
                    case "DARK_GREEN":
                        concrete = Material.GREEN_CONCRETE;
                        break;
                    case "GOLD":
                        concrete = Material.ORANGE_CONCRETE;
                        break;
                    case "WHITE":
                        concrete = Material.WHITE_CONCRETE;
                        break;
                    case "AQUA":
                        concrete = Material.LIGHT_BLUE_CONCRETE;
                        break;
                    case "LIGHT_PURPLE":
                        concrete = Material.MAGENTA_CONCRETE;
                        break;
                    case "DARK_PURPLE":
                        concrete = Material.PURPLE_CONCRETE;
                        break;
                    case "YELLOW":
                        concrete = Material.YELLOW_CONCRETE;
                        break;
                    default:
                        plugin.getLogger().warning("Unknown team color for player " + p.getName() + ": " + teamName);
                        continue;
                }

                ItemStack largestStack = null;
                int maxAmount = 0;
                int largestStackSlot = -1;
                int totalAmount = 0;

                ItemStack held = p.getItemOnCursor();
                if (held != null && held.getType() == concrete) {
                    totalAmount += held.getAmount();
                    if (held.getAmount() > maxAmount) {
                        maxAmount = held.getAmount();
                        largestStack = held;
                        largestStackSlot = -2;
                    }
                }

                for (int i = 0; i < combined.length; i++) {
                    ItemStack stack = combined[i];
                    if (stack != null && stack.getType() == concrete) {
                        totalAmount += stack.getAmount();
                        if (stack.getAmount() > maxAmount) {
                            maxAmount = stack.getAmount();
                            largestStack = stack;
                            largestStackSlot = i;
                        }
                    }
                }

                if (largestStack != null && totalAmount < 64) {
                    largestStack.setAmount(maxAmount + (64 - totalAmount));
                    if (largestStackSlot == 0) inv.setItemInOffHand(largestStack);
                    else if (largestStackSlot == -2) p.setItemOnCursor(largestStack);
                    else if (largestStackSlot >= 1 && largestStackSlot <= 36)
                        inv.setItem(largestStackSlot - 1, largestStack);
                    else if (largestStackSlot >= 37) inv.setArmorContents(armor);
                    p.updateInventory();
                } else if (largestStack == null) {
                    ItemStack newStack = new ItemStack(concrete, 1);
                    if (largestStackSlot == 0) {
                        inv.setItemInOffHand(newStack);
                    } else if (largestStackSlot >= 1 && largestStackSlot <= 36) {
                        inv.setItem(largestStackSlot - 1, newStack);
                    } else {
                        inv.addItem(newStack);
                    }
                    p.updateInventory();
                }
            }
        }


        double Y_STEP = 5.0;
        borderTime--;
        DamageSource voidSource = DamageSource.builder(DamageType.OUT_OF_WORLD).build();
        if (borderTime % 10 == 0) {
            for (Player player : world.getPlayers()) {
                GameMode gm = player.getGameMode();
                if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && player.getLocation().getY() < 85) {
                    player.damage(6.0, voidSource);
                }
            }
        }

        if (borderTime < 0) {
            borderTime = 20;
            if (startTime != -100) {borderRadius -= 0.5;}
            if (borderRadius < 0) borderRadius = 0;

            Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 20, 20), 1.8f);
            for (Player player : world.getPlayers()) {
                if (startTime == -100) {continue;}
                Location center = player.getLocation();
                double startY = center.getY() - 8.0;
                double endY = center.getY() + 8.0;
                GameMode gm = player.getGameMode();

                if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) &&
                        (Math.sqrt((center.getX() - 0.5) * (center.getX() - 0.5) + (center.getZ() - 0.5) * (center.getZ() - 0.5)) >= borderRadius)) {
                    player.damage(5.0, voidSource);
                } else if (center.getY() < 0 && (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE)) {
                    player.teleport(new Location(world, 0, 150, 0));
                }

                for (double prty = startY; prty <= endY; prty += Y_STEP) {
                    double x = center.x();
                    double z = center.z();
                    int sampleRate = borderRadius < 1 ? 32 : borderRadius < 20 ? 8 : borderRadius < 30 ? 4 : borderRadius < 50 ? 2 : 1;
                    for (int i = 0; i < POINTS; i += sampleRate) {
                        double prtx = 0.5 + cosCache[i] * borderRadius;
                        double prtz = 0.5 + sinCache[i] * borderRadius;
                        if (Math.abs(prtx - x) + Math.abs(prtz - z) > 24) continue;
                        player.spawnParticle(Particle.DUST, new Location(world, prtx, prty, prtz), 1, 0.0, 0.0, 0.0, 0.0, redDust);
                    }
                }
            }
        }


        // Check for alive teams
        // Check for alive teams
        Set<String> aliveTeams = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL) {
                String teamName = getPlayerTeam(p); // You'll need to implement this method
                if (teamName != null) {
                    aliveTeams.add(teamName);
                }
            }
        }

        if (aliveTeams.size() == 1 && startTime != -100) {
            startTime = -100;
            borderRadius = 200;

            String winningTeam = aliveTeams.iterator().next();
            String coloredTeamName = getColoredTeamName(winningTeam); // Get colorized team name

            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
                p.sendTitle("Game Finished!", coloredTeamName + " wins!");
                if (p.getGameMode() == GameMode.SURVIVAL) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
            }
        } else if (aliveTeams.isEmpty() && startTime != -100) {
            startTime = -100;

            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                p.sendTitle("Game Finished!", "It's a tie!");
            }
        }
    }

    private static void clearArea(World world, int x2, int z2, int w, int d, int minY, int maxY) {
        for (int x = x2; x <= x2 + w; x++) {
            for (int z = z2; z <= z2 + d; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBlock(world, x, y, z, Bukkit.createBlockData(Material.AIR));
                }
            }
        }
    }
    private static String getPlayerTeam(Player p) {
        Scoreboard board = p.getScoreboard();
        for (Team team : board.getTeams()) {
            if (team.hasEntry(p.getName())) {
                return team.getName();
            }
        }
        return null;
    }

    private static String getColoredTeamName(String teamName) {
        return switch (teamName.toLowerCase()) {
            case "red" -> "§cRED";
            case "blue" -> "§9BLUE";
            case "green" -> "§aGREEN";
            case "yellow" -> "§eYELLOW";
            case "light_purple" -> "§dPINK";
            case "orange" -> "§6ORANGE";
            case "gray", "grey" -> "§7GRAY";
            case "aqua", "cyan" -> "§bAQUA";
            case "white" -> "§fWHITE";
            case "black" -> "§0BLACK";
            case "dark_red" -> "§4DARK RED";
            case "dark_blue" -> "§1DARK BLUE";
            case "dark_green" -> "§2GREEN";
            case "dark_purple", "dark_magenta" -> "§5PURPLE";
            case "gold" -> "§6GOLD";
            case "dark_gray" -> "§8DARK GRAY";
            default -> "§f" + teamName; // Default to white
        };
    }
}