package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.structure.Mirror;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import javax.naming.Name;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ParkourRace {
    private static final Logger LOGGER = Logger.getLogger("ParkourRace");
    private static JavaPlugin plugin;

    private static final PotionEffect slowFallEffect = new PotionEffect(PotionEffectType.SLOW_FALLING, 5, 1, true, false, false);
    private static final PotionEffect invincibleEffect = new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 10, true, false, false);
    private static List<String> checkpointNames = new ArrayList<>();
    private static Map<String, Location> playerCheckpoints = new HashMap<>();
    public static int startTime = -1;
    private static int loopTimer = 0;
    private static int loopTimer2 = 0;
    private static Location genPos;

    private static int mapEaterX = 0;

    record Offset(int dx, int dy, int dz) {}
    record LoadAction(String nbtName, Offset structOffset, Offset redstoneOffset) {}
    record FillAction(int x1, int y1, int z1, int x2, int y2, int z2, Material material, BlockData data) {}
    record ParkourSegment(String displayName, LoadAction[] loads, FillAction[] fills) {}

    private static class SegDifficulty {
        String name;
        int cap;
        List<String> segments;

        public SegDifficulty(String str, int num) {
            this.name = str;
            cap = num;
            segments = new ArrayList<>();
        }
    }
    private static final List<ParkourSegment> SEGMENTS = new ArrayList<>();
    private static List<SegDifficulty> difficultyMap = new ArrayList<>();

    private static void loadSegmentsFromFile() {
        difficultyMap.clear();

        InputStream is = ParkourRace.class.getClassLoader().getResourceAsStream("parkourrace/segmentLoads.txt");
        if (is == null) {
            LOGGER.severe("segmentLoads.txt NOT FOUND in resources/parkourrace/!");
            return;
        }

        LOGGER.info("Found segmentLoads.txt – STARTING PARSE");

        try (Scanner scanner = new Scanner(is)) {
            int lineNum = 0;
            while (scanner.hasNextLine()) {
                String rawLine = scanner.nextLine();
                String line = rawLine.trim();
                lineNum++;

                if (line.isEmpty() || line.startsWith("#")) {
                    LOGGER.info("  [Line " + lineNum + "] Skipped: " + rawLine);
                    continue;
                } else if (line.startsWith("@")) {
                    LOGGER.info("  [Line " + lineNum + "] DIFFICULTY: " + line.substring(1) + ", Skipped: " + rawLine);
                    String name = line.substring(1).split(" ")[0];
                    int num = Integer.parseInt(line.substring(1).split(" ")[1]);
                    difficultyMap.add(new SegDifficulty(name, num));
                    continue;
                }

                LOGGER.info("  [Line " + lineNum + "] Raw: " + rawLine);

                // === SPLIT ON " ... fills: " TO GET ALL FILL SECTIONS ===
                String[] fillSplits = line.split(" \\.\\.\\. fills: ");
                String mainPart = fillSplits[0];
                List<String> fillParts = new ArrayList<>();
                for (int i = 1; i < fillSplits.length; i++) {
                    fillParts.add(fillSplits[i]);
                }

                String[] parts = mainPart.split("\\|", -1);
                LOGGER.info("    → Split main into " + parts.length + " parts");

                if (parts.length < 4) {
                    LOGGER.warning("    Invalid: too few main parts");
                    continue;
                }

                String nbtName = parts[0].trim();
                String displayName = parts[1].trim();
                Offset struct = parseOffset(parts[2]);
                Offset redstone = parseOffset(parts[3]);

                LOGGER.info("    → Main: " + nbtName + " | '" + displayName + "' | struct=" + struct + " | redstone=" + redstone);

                List<LoadAction> loads = new ArrayList<>();
                loads.add(new LoadAction(nbtName, struct, redstone));

                // === EXTRA LOADS: from index 4 to end of mainPart ===
                for (int i = 4; i + 2 < parts.length; i += 3) {
                    String eNbt = parts[i].trim();
                    if (eNbt.isEmpty()) continue;
                    Offset eStruct = parseOffset(parts[i + 1]);
                    Offset eRed = parseOffset(parts[i + 2]);
                    loads.add(new LoadAction(eNbt, eStruct, eRed));
                    LOGGER.info("      + Extra: " + eNbt + " @ " + eStruct + " | redstone @ " + eRed);
                }

                // === MULTIPLE FILL ACTIONS ===
                List<FillAction> fills = new ArrayList<>();
                for (String fillPart : fillParts) {
                    if (fillPart.trim().isEmpty()) continue;
                    String[] fillRaw = fillPart.split("\\|", -1);
                    LOGGER.info("    → Fill raw: " + Arrays.toString(fillRaw));
                    if (fillRaw.length >= 4) {
                        int[] from = parseIntArray(fillRaw[0]);
                        int[] to = parseIntArray(fillRaw[1]);
                        Material mat = Material.matchMaterial(fillRaw[2].trim());
                        BlockData data = Bukkit.createBlockData(fillRaw[3].trim());

                        if (from.length == 3 && to.length == 3 && mat != null && data != null) {
                            fills.add(new FillAction(from[0], from[1], from[2], to[0], to[1], to[2], mat, data));
                            LOGGER.info("      + Fill: " + mat + " from " + Arrays.toString(from) + " to " + Arrays.toString(to));
                        } else {
                            LOGGER.warning("      Fill parse failed");
                        }
                    }
                }

                difficultyMap.getLast().segments.add(displayName);
                SEGMENTS.add(new ParkourSegment(displayName, loads.toArray(new LoadAction[0]), fills.isEmpty() ? null : fills.toArray(new FillAction[0])));
                LOGGER.info("    → SEGMENT LOADED: " + displayName + " | " + loads.size() + " loads | fills=" + fills.size());
            }
        } catch (Exception e) {
            LOGGER.severe("PARSE ERROR: " + e.getMessage());
            e.printStackTrace();
        }

        LOGGER.info("PARSE COMPLETE – Loaded " + SEGMENTS.size() + " segments");
    }

    private static Offset parseOffset(String str) {
        String[] p = str.split(",");
        return new Offset(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    private static int[] parseIntArray(String str) {
        String[] p = str.split(",");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }
    /*
    private static final String[] EASY_SEGMENTS = {
            "easysegment1.nbt",   // Easy 1
            "easysegment2.nbt",   // Easy 2
            "easysegment3.nbt",   // Low Logs
            "easysegment4.nbt",   // Switch Chain
            "easysegment5.nbt",   // Switches
            "easysegment6.nbt",   // Dispenser
            "easysegment7.nbt",   // Snowier Snow
            "easysegment8.nbt",   // Zappier's Challenge
            "easysegment9.nbt",   // Cactus Jumps
            "easysegment10.nbt",  // Purple Collab
            "easysegment11.nbt",  // Zappier's Bastion
            "easysegment12.nbt",  // Easy 12
            "easysegment13.nbt",  // Easy 13
            "easysegment14.nbt",  // Vakore's Slime Bounce
            "easysegment15.nbt",  // Not so Paneful
            "easysegment16.nbt",  // Easy 16
            "easysegment17.nbt",  // Loonac's Honey
            "easysegment18a.nbt", // Dodie's Puzzle (part a – the datapack loads 18a + 18b)
            // 18b is loaded separately, so we only need the .nbt name here
            "easysegment19.nbt",  // Piston Temple  (moved from easy → medium in the datapack)
            "easysegment20.nbt",  // Temptation
            "easysegment21.nbt",  // Sub-Zero Speeds
            "easysegment22.nbt"   // Blackstone Bounces
    };

    // ----------------------------------------------------------------------
    // MEDIUM segments (14 total – the ones that appear in selectstructure_medium)
    // ----------------------------------------------------------------------
    private static final String[] MEDIUM_SEGMENTS = {
            "mediumsegment1.nbt",   // Quick Switches
            "mediumsegment2.nbt",   // Swinging Side to Side
            "mediumsegment3.nbt",   // Door Neo (Not the first jump)
            "mediumsegment4.nbt",   // Vakore's Slime Fall
            "mediumsegment5.nbt",   // Cobble Switches
            "mediumsegment6.nbt",   // Powdered Snow
            "mediumsegment7.nbt",   // Neo Tutorial
            "mediumsegment8.nbt",   // GamingUni456's Challenge
            "mediumsegment9.nbt",   // Zappier's Honey
            "mediumsegment10.nbt",  // Soul Fire Pillars (rnd100 in hard, but also listed in medium)
            "mediumsegment12.nbt",  // Superbri's Ladders
            "mediumsegment13.nbt",  // Jungle Gym
            "mediumsegment14.nbt",  // Become Slime
            "easysegment19.nbt"     // Piston Temple – the datapack moved it to medium
    };

    // ----------------------------------------------------------------------
    // HARD segments (14 total – the ones that appear in selectstructure_hard)
    // ----------------------------------------------------------------------
    private static final String[] HARD_SEGMENTS = {
            "hardsegment1.nbt",   // Quick Cobble Switches
            "hardsegment2a.nbt",  // Superbri212's Slime Fall (part a)
            // part b is loaded separately
            "hardsegment3.nbt",   // Dobule Door Neo
            "hardsegment4.nbt",   // Moderate Pane
            "hardsegment5.nbt",   // Paneful Ascent
            "hardsegment6.nbt",   // Disappearing Neos
            "hardsegment7.nbt",   // Snowy Slime
            "hardsegment8.nbt",   // Burning Panes
            "hardsegment9.nbt",   // Door Prison
            "hardsegment10.nbt",  // Loonac's Snowy Course
            "hardsegment11.nbt",  // Chorus of Dodie
            "hardsegment12.nbt",  // Superbri212 but sideways
            "hardsegment13.nbt",  // Fall Up
            "hardsegment14.nbt"   // Slime Neos
    };

    // ----------------------------------------------------------------------
    // BEDROCK segments (5 total – the ones that appear in selectstructure_bedrock)
    // ----------------------------------------------------------------------
    private static final String[] BEDROCK_SEGMENTS = {
            "bedrocksegment1.nbt", // Double Double Disappearing Neo
            "bedrocksegment2.nbt", // Triple Neo
            "bedrocksegment3.nbt", // Pains of Pane
            "bedrocksegment4.nbt", // The Maze
            "bedrocksegment5.nbt"  // Dodie's Head Neo
    };
     */

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        loadSegmentsFromFile();
    }

    public static void start(World world) {
        startTime = 1100;
        if (plugin == null) {
            LOGGER.severe("ParkourRace plugin instance not initialized! Call ParkourRace.init(plugin) in onEnable.");
            return;
        }
        mapEaterX = 0;
        genPos = new Location(world, 0, 100, -10000);
        playerCheckpoints.clear();
        checkpointNames.clear();
        world.getEntities().stream()
                .filter(e -> !(e instanceof org.bukkit.entity.Player))
                .forEach(org.bukkit.entity.Entity::remove);
        ZappierGames.gameMode = 20;

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, -10000);
        border.setSize(2000000);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(invincibleEffect);
            p.teleport(new Location(world, 0.5, 100, -9999.5, -90.0f, 0.0f));
            p.setGameMode(GameMode.ADVENTURE);
        }

        world.setDifficulty(Difficulty.PEACEFUL);
        startTime = 1100;
    }


    public static double dist3d(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt((x2 - x1)*(x2 - x1) + (y2 - y1)*(y2 - y1) + (z2 - z1)*(z2 - z1));
    };


    private static int currentDifficulty = 0;
    private static int currentCap = 0;
    public static void run(World world) {
        if (startTime == 1000) {
            BlockData air = Bukkit.createBlockData(Material.AIR);
            for (int i = -2; i < 200; i++) {
                for (int k = -10; k < 10; k++) {
                    for (int j = -10; j < 50; j++) {
                        Block block = world.getBlockAt(i, 100 + j, -10000 + k);
                        if (block.getBlockData().matches(air)) { continue; }
                        block.setBlockData(air, false);
                    }
                }
            }

            BlockData grass = Bukkit.createBlockData(Material.GRASS_BLOCK);
            BlockData dirt = Bukkit.createBlockData(Material.DIRT);
            BlockData gold_block = Bukkit.createBlockData(Material.GOLD_BLOCK);
            for (int i = -2; i <= 2; i++) {
                for (int k = -2; k <= 2; k++) {
                    for (int j = -2; j < 0; j++) {
                        Block block = world.getBlockAt(i, 100 + j, -10000 + k);
                        //if (block.getBlockData().matches(data)) { return; }
                        if (j == -1) {
                            block.setBlockData(grass, false);
                        } else {
                            block.setBlockData(dirt, false);
                        }

                        if (i == 0 && k == 0 && j == -1) {
                            block.setBlockData(gold_block, false);
                        }
                    }
                }
            }
            startTime = 80;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(new Location(world, 0, 101, -10000));
                p.setAllowFlight(false);
            }

        }
        if (startTime > 1000) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setAllowFlight(true);
                p.teleport(new Location(world, mapEaterX, 140, -10000));
            }
            fillNoUpdate(world, Material.AIR, mapEaterX - 17, 90, -10000 - 48, mapEaterX + 12, 140, -10000 + 48);
            mapEaterX += 11;
            startTime--;

            world.getEntities().stream()
                    .filter(e -> (!(e instanceof org.bukkit.entity.Player)))
                    .forEach(org.bukkit.entity.Entity::remove);
            return;
        }
        if (startTime > 0) {
            if (startTime >= 4 && startTime % 3 == 0) {
                if (SEGMENTS.isEmpty()) {
                    LOGGER.warning("No segments loaded! Cannot place structure.");
                    return;
                }


                if (currentDifficulty >= difficultyMap.size()) {
                    startTime = 1;
                } else {
                    startTime += 3;
                    //int segIndex = (int) (Math.random() * SEGMENTS.size());
                    int segIndex = 0;
                    String leStr = difficultyMap.get(currentDifficulty).segments.get(currentCap);
                        for (ParkourSegment s: SEGMENTS) {
                            if (s.displayName().equals(leStr)) {
                                segIndex = SEGMENTS.indexOf(s);
                                LOGGER.info("Set segIndex to " + segIndex + ", " + s.displayName + " : " + leStr);
                            }
                        }
                    LOGGER.info("Seg index of " + difficultyMap.get(currentDifficulty).segments.get(currentCap) + ", " + currentDifficulty + ", " + currentCap + " : " + segIndex + " - " + SEGMENTS.get(segIndex).displayName);
                    try {
                        placeStructure(world, genPos.getBlockX(), genPos.getBlockY() - 1, genPos.getBlockZ(), segIndex);
                    } catch (Exception e) {
                        LOGGER.severe("Failed to place structure: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                currentCap++;
                if (currentCap >= difficultyMap.get(currentDifficulty).cap) {
                    currentDifficulty++;
                    currentCap = 0;
                }
            }
            startTime--;
            if (startTime == 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.teleport(new Location(world, 0, 101, -10000));
                }
                startTime--;
            }
        }

        world.getEntitiesByClass(Arrow.class).stream()
                .filter(arrow -> arrow.isOnGround() || arrow.isInBlock()) // inGround check
                .forEach(Arrow::remove);
        world.getEntitiesByClass(Arrow.class).forEach(arrow -> arrow.setPierceLevel((byte) 20));

        loopTimer++;
        loopTimer %= 80;
        loopTimer2++;
        loopTimer2 %= 60;

        List<Entity> markers = world.getEntities().stream().filter(entity -> entity.getType() == EntityType.MARKER).collect(Collectors.toList());
        if (loopTimer % 40 == 19 || loopTimer % 40 == 39) {
            for (Entity m : markers) {
                int x = (int) Math.floor(m.getX());
                int y = (int) Math.floor(m.getY());
                int z = (int) Math.floor(m.getZ());

                // ---- orange 3*1*3 (cobble1b) ----
                if (m.getScoreboardTags().contains("cobble1b")) {
                    if (loopTimer % 40 == 19) {
                        fill(m.getWorld(), Material.ORANGE_WOOL,
                                x, y - 1, z, x, y + 1, z);
                    } else { // 39
                        breakEffect(m.getWorld(), x, y, z, Material.ORANGE_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x, y - 1, z, x, y + 1, z);
                    }
                }

                // ---- orange 1*3*1 (cobble1a) ----
                if (m.getScoreboardTags().contains("cobble1a")) {
                    if (loopTimer % 40 == 19) {
                        fill(m.getWorld(), Material.ORANGE_WOOL,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    } else { // 39
                        breakEffect(m.getWorld(), x, y, z, Material.ORANGE_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    }
                }

                // ---- blue 3*1*3 (cobble2b) ----
                if (m.getScoreboardTags().contains("cobble2b")) {
                    if (loopTimer % 40 == 39) {
                        fill(m.getWorld(), Material.BLUE_WOOL,
                                x, y - 1, z, x, y + 1, z);
                    } else { // 19
                        breakEffect(m.getWorld(), x, y, z, Material.BLUE_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x, y - 1, z, x, y + 1, z);
                    }
                }

                // ---- blue 1*3*1 (cobble2a) ----
                if (m.getScoreboardTags().contains("cobble2a")) {
                    if (loopTimer % 40 == 39) {
                        fill(m.getWorld(), Material.BLUE_WOOL,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    } else { // 19
                        breakEffect(m.getWorld(), x, y, z, Material.BLUE_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    }
                }

                // ---- orange glass pane (glass1b) ----
                if (m.getScoreboardTags().contains("glass1b")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    if (loopTimer % 40 == 19) {
                        b.setType(Material.ORANGE_STAINED_GLASS_PANE, true);
                    } else { // 39
                        breakEffect(m.getWorld(), x, y, z, Material.ORANGE_STAINED_GLASS_PANE, Sound.BLOCK_GLASS_BREAK);
                        b.setType(Material.AIR, false);
                    }
                }

                // ---- blue glass pane (glass2b) ----
                if (m.getScoreboardTags().contains("glass2b")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    if (loopTimer % 40 == 39) {
                        b.setType(Material.BLUE_STAINED_GLASS_PANE, true);
                    } else { // 19
                        breakEffect(m.getWorld(), x, y, z, Material.BLUE_STAINED_GLASS_PANE, Sound.BLOCK_GLASS_BREAK);
                        b.setType(Material.AIR, false);
                    }
                }

                // ---- redstone A / B ----
                if (m.getScoreboardTags().contains("redstone1a")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    b.setType(loopTimer % 40 == 19 ? Material.REDSTONE_BLOCK : Material.AIR, true);
                }
                if (m.getScoreboardTags().contains("redstone2a")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    b.setType(loopTimer % 40 == 39 ? Material.REDSTONE_BLOCK : Material.AIR, true);
                }
            }
        }

        if (loopTimer == 39 || loopTimer == 79) {
            for (Entity m : markers) {
                int x = (int) Math.floor(m.getX());
                int y = (int) Math.floor(m.getY());
                int z = (int) Math.floor(m.getZ());

                // ---- red 1*3*1 (cobble2d) ----
                if (m.getScoreboardTags().contains("cobble2d")) {
                    if (loopTimer == 39) {
                        fill(m.getWorld(), Material.RED_WOOL,
                                x, y - 1, z, x, y + 1, z);
                    } else { // 79
                        breakEffect(m.getWorld(), x, y, z, Material.RED_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x, y - 1, z, x, y + 1, z);
                    }
                }

                // ---- light-blue 1*3*1 (cobble1d) ----
                if (m.getScoreboardTags().contains("cobble1d")) {
                    if (loopTimer == 79) {
                        fill(m.getWorld(), Material.LIGHT_BLUE_WOOL,
                                x, y - 1, z, x, y + 1, z);
                    } else { // 39
                        breakEffect(m.getWorld(), x, y, z, Material.LIGHT_BLUE_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x, y - 1, z, x, y + 1, z);
                    }
                }

                // ---- red 3*1*3 (cobble2c) ----
                if (m.getScoreboardTags().contains("cobble2c")) {
                    if (loopTimer == 39) {
                        fill(m.getWorld(), Material.RED_WOOL,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    } else { // 79
                        breakEffect(m.getWorld(), x, y, z, Material.RED_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    }
                }

                // ---- light-blue 3*1*3 (cobble1c) ----
                if (m.getScoreboardTags().contains("cobble1c")) {
                    if (loopTimer == 79) {
                        fill(m.getWorld(), Material.LIGHT_BLUE_WOOL,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    } else { // 39
                        breakEffect(m.getWorld(), x, y, z, Material.LIGHT_BLUE_WOOL, Sound.BLOCK_WOOL_BREAK);
                        fill(m.getWorld(), Material.AIR,
                                x - 1, y, z - 1, x + 1, y, z + 1);
                    }
                }
            }
        }

        if (loopTimer2 == 19 || loopTimer2 == 39 || loopTimer2 == 59) {
            for (Entity m : markers) {
                int x = (int) Math.floor(m.getX());
                int y = (int) Math.floor(m.getY());
                int z = (int) Math.floor(m.getZ());

                // ---- 3×1×1 slime (slime1b / slime2b / slime3b) ----
                if (m.getScoreboardTags().contains("slime1b")) {
                    Material mat = (loopTimer2 == 19) ? Material.SLIME_BLOCK : Material.AIR;
                    fill(m.getWorld(), mat, x - 1, y, z, x + 1, y, z);
                }
                if (m.getScoreboardTags().contains("slime2b")) {
                    Material mat = (loopTimer2 == 39) ? Material.SLIME_BLOCK : Material.AIR;
                    fill(m.getWorld(), mat, x - 1, y, z, x + 1, y, z);
                }
                if (m.getScoreboardTags().contains("slime3b")) {
                    Material mat = (loopTimer2 == 59) ? Material.SLIME_BLOCK : Material.AIR;
                    fill(m.getWorld(), mat, x - 1, y, z, x + 1, y, z);
                }

                // ---- 1×1×1 slime (slime1c / slime2c / slime3c) ----
                if (m.getScoreboardTags().contains("slime1c")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    b.setType((loopTimer2 == 19) ? Material.SLIME_BLOCK : Material.AIR, false);
                }
                if (m.getScoreboardTags().contains("slime2c")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    b.setType((loopTimer2 == 39) ? Material.SLIME_BLOCK : Material.AIR, false);
                }
                if (m.getScoreboardTags().contains("slime3c")) {
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    b.setType((loopTimer2 == 59) ? Material.SLIME_BLOCK : Material.AIR, false);
                }
            }
        }

        if (loopTimer == 79) {
            for (Entity m : markers) {
                if (m.getScoreboardTags().contains("dispenserrefill")) {
                    int x = (int) Math.floor(m.getX());
                    int y = (int) Math.floor(m.getY());
                    int z = (int) Math.floor(m.getZ());
                    Block b = m.getWorld().getBlockAt(x, y, z);
                    if (b.getState() instanceof Dispenser) {
                        Dispenser disp = (Dispenser) b.getState();
                        Inventory inv = disp.getInventory();
                        inv.setItem(5, new ItemStack(Material.ARROW, 32));
                    }
                }
            }
        }


        for (Player p : world.getPlayers()) {
            if (playerCheckpoints.get(p.getName()) == null) {
                playerCheckpoints.put(p.getName(), new Location(world, 0.5, 100, -9999.5, -90.0f, 0.0f));
            }
            Location checkpoint = playerCheckpoints.get(p.getName());
            if (p.getGameMode() == GameMode.ADVENTURE && p.getLocation().getY() < 95) {
                p.setFallDistance(0);
                p.addPotionEffect(slowFallEffect);
                p.teleport(checkpoint);
            }

            if (p.isOnGround() && startTime < 0) {
                for (Entity marker : markers) {
                    if (marker.getX() <= checkpoint.getX() || dist3d(p.getX(), p.getY(), p.getZ(), marker.getX(), marker.getY(), marker.getZ()) > 3) {
                        continue;
                    } else {
                        Set<String> myTags = getVanillaEntityTags(marker);
                        for (String str : myTags) {
                            if (str.equals("pr_checkpoint")) {
                                //Bukkit.broadcastMessage("New checkpoint!");
                                p.sendTitle("", marker.getName());
                                p.playSound(p.getLocation(), "minecraft:custom.killget", 1.0f, 1.0f);
                                Location newLocation = marker.getLocation();
                                newLocation.setYaw(-90.0f);
                                playerCheckpoints.put(p.getName(), newLocation);
                                p.spawnParticle(
                                        Particle.GLOW,
                                        newLocation.add(0, 1, 0),
                                        30,2.0,2.0, 2.0, 1.0f
                                );
                                newLocation.add(0,-1,0);
                            }
                        }
                    }
                }
                /*for (int i = 0; i < checkpoints.size(); i += 3) {
                    Integer currentIndex = playerCheckpointIndices.get(p.getName());
                    if (currentIndex != null && currentIndex == i) {
                        continue;
                    }
                    int cx = 0;
                    int cy = 101;
                    int cz = -10000;
                    if (dist3d(p.getX(), p.getY(), p.getZ(), cx + 0.5, cy, cz + 0.5) < 5.0) {
                        playerCheckpoints.get(p.getName()).setX(cx + 0.5);
                        playerCheckpoints.get(p.getName()).setY(cy);
                        playerCheckpoints.get(p.getName()).setZ(cz + 0.5);
                        p.playSound(p.getLocation(), "minecraft:custom.killget", 1.0f, 1.0f);
                        p.sendTitle(checkpointNames.get(i / 3), "");
                    }
                }*/
            }
        }

    }

    private static void placeStructure(World world, int x, int y, int z, int segIndex) throws Exception {
        if (segIndex < 0 || segIndex >= SEGMENTS.size()) {
            throw new IllegalArgumentException("Invalid segment index: " + segIndex);
        }

        ParkourSegment seg = SEGMENTS.get(segIndex);
        StructureManager sm = plugin.getServer().getStructureManager();

        // 1. Update marker name
        List<Entity> someMarkers = world.getEntities().stream()
                .filter(e -> e.getType() == EntityType.MARKER)
                .collect(Collectors.toList());

        for (Entity marker : someMarkers) {
            if (marker.getX() != genPos.getX()) continue;
            if (getVanillaEntityTags(marker).contains("pr_checkpoint")) {
                marker.customName(Component.text(ChatColor.YELLOW + seg.displayName));
            }
        }

        // 2. Place all structures
        for (LoadAction load : seg.loads) {
            int sx = x + load.structOffset.dx;
            int sy = y + load.structOffset.dy;
            int sz = z + load.structOffset.dz;
            loadAndPlace(world, load.nbtName, sx, sy, sz, sm);
        }

        // 3. Apply ALL fill actions
        if (seg.fills != null) {
            for (FillAction f : seg.fills) {
                for (int fx = Math.min(f.x1, f.x2); fx <= Math.max(f.x1, f.x2); fx++) {
                    for (int fy = Math.min(f.y1, f.y2); fy <= Math.max(f.y1, f.y2); fy++) {
                        for (int fz = Math.min(f.z1, f.z2); fz <= Math.max(f.z1, f.z2); fz++) {
                            world.getBlockAt(x + fx, y + fy, z + fz).setBlockData(f.data, true);
                        }
                    }
                }
            }
        }

        // 4. Update genPos from last pr_checkpoint
        List<Entity> markers = world.getEntities().stream()
                .filter(e -> e.getType() == EntityType.MARKER)
                .collect(Collectors.toList());

        for (Entity marker : markers) {
            if (marker.getX() <= genPos.getX()) continue;
            if (getVanillaEntityTags(marker).contains("pr_checkpoint")) {
                genPos = marker.getLocation();
            }
        }

        // 5. Remove old entities
        world.getEntities().stream()
                .filter(e -> !(e instanceof Player) && e.getX() > genPos.getX())
                .forEach(Entity::remove);

        // 6. Teleport players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(genPos);
        }
    }

    /* -------------------------------------------------------------
     * Helper – loads a single .nbt and places it (extracted from your
     * original code to avoid duplication)
     * ------------------------------------------------------------- */
    private static void loadAndPlace(World world, String nbtName,
                                     int x, int y, int z,
                                     StructureManager sm) throws Exception {

        InputStream nbtStream = plugin.getResource("parkourrace/" + nbtName);
        if (nbtStream == null) {
            throw new IllegalStateException("Could not find " + nbtName + " in resources/parkourrace/");
        }

        Path tempFile = Files.createTempFile(nbtName.replace(".nbt", ""), ".nbt");
        Files.copy(nbtStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        nbtStream.close();

        Structure struct = sm.loadStructure(tempFile.toFile());
        if (struct == null) {
            throw new IllegalStateException("Failed to load structure from NBT file.");
        }

        struct.place(new Location(world, x, y, z), true,
                StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());

        LOGGER.info("Placed " + nbtName + " at " + x + "," + y + "," + z);


        // 7. Clean up structure blocks (original cleanup logic)
        BlockData structBlock = Bukkit.createBlockData(Material.STRUCTURE_BLOCK);
        for (int i = x - 10; i < x + 10; i++) {
            for (int k = z - 2; k < z + 4; k++) {
                for (int j = y - 5; j <= y + 5; j++) {
                    Block b = world.getBlockAt(i, j, k);
                    if (b.getBlockData().matches(structBlock)) {
                        fill(world, Material.AIR, i, j, k, i, j, k);
                    }
                }
            }
        }

        try { Files.deleteIfExists(tempFile); }
        catch (IOException ex) { LOGGER.warning("Could not delete temp file: " + ex.getMessage()); }
    }

    public static Set<String> getVanillaEntityTags(Entity entity) {
        // This method retrieves the actual vanilla entity tags (scoreboard tags)
        return entity.getScoreboardTags();
    }

    private static void fill(World world, Material mat,
                      int x1, int y1, int z1,
                      int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.getBlockAt(x, y, z).setType(mat, true);
                }
            }
        }
    }

    private static void fillNoUpdate(World world, Material mat,
                             int x1, int y1, int z1,
                             int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.getBlockAt(x, y, z).setType(mat, false);
                }
            }
        }
    }

    public static void placeBlock(World world, int x, int y, int z, BlockData data) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData().matches(data)) { return; }
        block.setBlockData(data, false);
    }

    private static void breakEffect(World world, int x, int y, int z, Material wool, Sound sound) {
        Location center = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        world.spawnParticle(Particle.BLOCK, center, 40,
                0.3, 0.3, 0.3, Bukkit.createBlockData(wool));
        world.playSound(center, sound, 1.0f, 1.0f);
    }

}

