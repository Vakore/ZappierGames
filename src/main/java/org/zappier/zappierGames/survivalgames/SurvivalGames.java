package org.zappier.zappierGames.survivalgames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.zappier.zappierGames.ZappierGames;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SurvivalGames {

    public static int platformRadius = 25;
    public static int half = 128 + 64;
    public static int minClearY = 40;
    public static int maxClearY = 120;
    public static int flatY = 60;
    private static final Random random = new Random();
    public static int terrainType = random.nextInt(3); // 0: flat, 1: pit, 2: mountain
    public static double noiseFreq = 0.05;
    public static double noiseAmp = 4.0;




    static class TreeStruct {
        public int minH;
        public int maxH;
        public Material stem;
        public Material leaf;
        public TreeStruct(Material stem, Material leaf, int minH, int maxH) {
            this.stem = stem;
            this.leaf = leaf;
            this.minH = minH;
            this.maxH = maxH;
        }
    }

    static class MatList {
        public Material surface;
        public TreeStruct tree1;
        public MatList(Material surface, TreeStruct tree1) {
            this.surface = surface;
            this.tree1 = tree1;
        }
    }

    public static MatList[] matLists = {
            new MatList(Material.GRASS_BLOCK, new TreeStruct(Material.OAK_LOG, Material.OAK_LEAVES, 4, 6)),
            new MatList(Material.SAND, new TreeStruct(Material.CACTUS, null, 1, 4)),
            new MatList(Material.BLACK_CONCRETE_POWDER, new TreeStruct(Material.BASALT, Material.SMOOTH_BASALT, 6, 10)),
            new MatList(Material.PODZOL, new TreeStruct(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 5, 10)),
            new MatList(Material.GRASS_BLOCK, new TreeStruct(Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, 4, 12)),
            new MatList(Material.SNOW_BLOCK, new TreeStruct(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 5, 10)),
            new MatList(Material.RED_SAND, new TreeStruct(Material.ACACIA_LOG, Material.ACACIA_LEAVES, 3, 5))
    };

    public static void placeBlock(World world, int x, int y, int z, BlockData data) {
        x += 10000;
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData().matches(data)) { return; }
        block.setBlockData(data, false);
    }

    private static void clearArea(World world, int x2, int z2, int w, int d, int minY, int maxY) {
        BlockData data = Bukkit.createBlockData(Material.AIR);
        for (int x = x2; x <= x2 + w; x++) {
            for (int z = z2; z <= z2 + d; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBlock(world, x, y, z, data);
                }
            }
        }
    }

    private static void fillArea(World world, Material mat, int x2, int z2, int w, int d, int minY, int maxY) {
        BlockData data = Bukkit.createBlockData(mat);
        if (data instanceof Leaves) {
            ((Leaves) data).setPersistent(true);
        }
        for (int x = x2; x <= x2 + w; x++) {
            for (int z = z2; z <= z2 + d; z++) {
                for (int y = minY; y <= maxY; y++) {
                    placeBlock(world, x, y, z, data);
                }
            }
        }
    }

    public static void placeTree(World world, Material stem, Material leaf, int stemX, int stemY, int stemZ, int height) {
        if (stem == Material.CACTUS) {
            fillArea(world, stem, stemX, stemZ, 0, 0, stemY, stemY + height - 1);
            return;
        }

        // Stem
        fillArea(world, stem, stemX, stemZ, 0, 0, stemY, stemY + height - 1);

        if (leaf == null) return;

        if (stem == Material.OAK_LOG || stem == Material.JUNGLE_LOG || stem == Material.ACACIA_LOG) {
            // Oak-like: bushy with branches
            fillArea(world, leaf, stemX - 2, stemZ - 2, 4, 4, stemY + height - 4, stemY + height - 1);
            fillArea(world, leaf, stemX - 1, stemZ - 1, 2, 2, stemY + height - 1, stemY + height);
            // Add branches
            for (int b = 0; b < 3; b++) {
                int dirX = random.nextInt(3) - 1;
                int dirZ = random.nextInt(3) - 1;
                if (dirX == 0 && dirZ == 0) continue;
                int branchY = stemY + height - random.nextInt(4) - 2;
                fillArea(world, stem, stemX + dirX, stemZ + dirZ, 0, 0, branchY, branchY);
                fillArea(world, leaf, stemX + dirX - 1, stemZ + dirZ - 1, 2, 2, branchY, branchY + 1);
            }
        } else if (stem == Material.SPRUCE_LOG) {
            // Spruce: conical
            int layers = height / 2;
            for (int l = 0; l < layers; l++) {
                int rad = layers - l;
                int ly = stemY + height - 2 - l * 2;
                fillArea(world, leaf, stemX - rad, stemZ - rad, rad * 2, rad * 2, ly, ly);
            }
            fillArea(world, leaf, stemX, stemZ, 0, 0, stemY + height, stemY + height); // Top
        } else if (stem == Material.BASALT) {
            // Custom basalt "tree"
            fillArea(world, leaf, stemX - 1, stemZ - 1, 2, 2, stemY + height - 3, stemY + height);
        } else {
            // Default
            fillArea(world, leaf, stemX - 2, stemZ - 2, 4, 4, stemY + height - 2, stemY + height - 1);
            fillArea(world, leaf, stemX - 1, stemZ - 1, 2, 2, stemY + height - 1, stemY + height + 1);
        }
    }

    private static int getSurfaceY(int relX, int relZ, int flatY, int type, double freq, double amp) {
        double dist = Math.sqrt(relX * relX + relZ * relZ);
        double factor = Math.max(0, (dist - 30) / (128.0 - 30)); // Adjusted for larger half
        double shapeOffset = 0;
        if (type == 1) { // pit
            shapeOffset = factor * 20;
        } else if (type == 2) { // mountain
            shapeOffset = 20 * (1 - factor);
        }
        //crazy stuff here
        if (relX > 0 && relZ > 0) {
            int adder = Math.min(relX, relZ) / 4;
            if (adder > 55) {adder = 5;}
            amp *= (1.0 + adder);
        } else if (relX > 0 && relZ < 0) {
            double adder = 1.0;
            adder -= relX / 10.0;
            amp *= adder;
        }

        double noise = Math.sin(relX * freq) * amp + Math.cos(relZ * freq) * amp;
        noise *= factor;


        int maxY = 140;
        if (relX < 0 && relZ > 0 && relX < -half / 2 + 50 && relX > -half / 2 - 25 && relZ > half / 2 - 50 && relZ < half / 2 + 25) {
            noise = Math.abs(noise * noise);
            noise *= noise;
            maxY += 50;
        }

        int surfaceY = flatY + (int) shapeOffset + (int) noise;


        if (Math.abs(relX) > 128 || Math.abs(relZ) > 128) {
            int relXX = Math.max(Math.abs(relX), 129) - 128;
            int relZZ = Math.max(Math.abs(relZ), 129) - 128;

            surfaceY += relXX * relXX * relZZ * relZZ / 16;
            maxY += relXX * relXX * relZZ * relZZ / 16;
        }
        maxY = Math.min(maxY, 190);
        return Math.max(40, Math.min(maxY, surfaceY));
    }


    private static void placeMineshaft(World world, int hx, int hy, int hz) {
        BlockData plankData = Bukkit.createBlockData(Material.OAK_PLANKS);
        BlockData logData = Bukkit.createBlockData(Material.OAK_LOG);
        BlockData fenceData = Bukkit.createBlockData(Material.OAK_FENCE);
        BlockData airData = Bukkit.createBlockData(Material.AIR);
        BlockData chestData = Bukkit.createBlockData(Material.CHEST);
        BlockData lavaData = Bukkit.createBlockData(Material.LAVA);

        // Floor
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -50; dz <= 50; dz++) {
                placeBlock(world, hx + dx, hy, hz + dz, plankData);
                if (dz % 10 != 0) {
                    placeBlock(world, hx + dx, hy + 1, hz + dz, airData);
                    placeBlock(world, hx + dx, hy + 2, hz + dz, airData);
                    placeBlock(world, hx + dx, hy + 3, hz + dz, airData);
                } else {
                    if (dx == 0) {
                        placeBlock(world, hx + dx, hy + 1, hz + dz, airData);
                        placeBlock(world, hx + dx, hy + 2, hz + dz, airData);
                    } else {
                        placeBlock(world, hx + dx, hy + 1, hz + dz, fenceData);
                        placeBlock(world, hx + dx, hy + 2, hz + dz, fenceData);
                    }
                    placeBlock(world, hx + dx, hy + 3, hz + dz, plankData);
                }
            }
        }


        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -50; dz <= 0; dz++) {
                placeBlock(world, hx + dx, hy - 10, hz + dz, plankData);
                if (dz % 10 != 0) {
                    placeBlock(world, hx + dx, hy - 9, hz + dz, airData);
                    placeBlock(world, hx + dx, hy - 8, hz + dz, airData);
                    placeBlock(world, hx + dx, hy - 7, hz + dz, airData);
                } else {
                    if (dx == 0) {
                        placeBlock(world, hx + dx, hy - 9, hz + dz, airData);
                        placeBlock(world, hx + dx, hy - 8, hz + dz, airData);
                    } else {
                        placeBlock(world, hx + dx, hy - 9, hz + dz, fenceData);
                        placeBlock(world, hx + dx, hy - 8, hz + dz, fenceData);
                    }
                    placeBlock(world, hx + dx, hy - 7, hz + dz, plankData);
                }
            }
        }

        for (int k = -23; k < 10; k++) {
            for (int i = -7; i < 7; i++) {
                for (int j = -7; j < 7; j++) {
                    if (i*i + j*j < 7*7) {
                        if (k > -20 && (k != 0 && k != -10 || i*i + j*j < 5*5 || Math.random() > 0.8)) {
                            placeBlock(world, hx + i, hy + k, hz + j, airData);
                        } else if (k <= -20) {
                            placeBlock(world, hx + i, hy + k, hz + j, lavaData);
                        } else {
                            placeBlock(world, hx + i, hy + k, hz + j, plankData);
                        }
                    }
                }
            }
        }
        BlockData ladderData = Bukkit.createBlockData(Material.LADDER);
        BlockData chainData = Bukkit.createBlockData(Material.CHAIN);

        if (ladderData instanceof org.bukkit.block.data.Directional) {
            ((org.bukkit.block.data.Directional) ladderData).setFacing(org.bukkit.block.BlockFace.EAST);
        }

        for (int k = -9; k < 10; k++) {
            placeBlock(world, hx + -6, hy + k, hz + 0, ladderData);
        }

        placeBlock(world, hx + -2, hy + 9, hz + 0, chainData);
        placeBlock(world, hx + -2, hy + 8, hz + 0, chainData);
        placeBlock(world, hx + -2, hy + 7, hz + 0, plankData);
        placeBlock(world, hx + -2, hy + 7, hz + -1, plankData);
        placeBlock(world, hx + -2, hy + 7, hz + 1, plankData);
        placeBlock(world, hx + -2, hy + 8, hz + 1, chestData);

        Chest chestState = (Chest) world.getBlockAt(hx + -2 + 10000, hy + 8, hz + 1).getState();
        List<Material> lootMats = Arrays.asList(Material.IRON_SWORD, Material.APPLE, Material.BREAD, Material.IRON_INGOT, Material.LEATHER_CHESTPLATE);
        int numItems = 1 + random.nextInt(3);
        for (int i = 0; i < numItems; i++) {
            Material mat = lootMats.get(random.nextInt(lootMats.size()));
            int amount = 1 + random.nextInt(5);
            chestState.getBlockInventory().addItem(new ItemStack(mat, amount));
        }
    }

    private static void placeSmallHut(World world, int hx, int hy, int hz) {
        BlockData plankData = Bukkit.createBlockData(Material.OAK_PLANKS);
        BlockData logData = Bukkit.createBlockData(Material.OAK_LOG);
        BlockData airData = Bukkit.createBlockData(Material.AIR);
        BlockData chestData = Bukkit.createBlockData(Material.CHEST);

        // Floor
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                placeBlock(world, hx + dx, hy, hz + dz, plankData);
            }
        }

        // Walls
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dy == 3 || Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        placeBlock(world, hx + dx, hy + dy, hz + dz, plankData);
                    }
                }
            }
        }

        // Door
        placeBlock(world, hx, hy + 1, hz + 2, airData);
        placeBlock(world, hx, hy + 2, hz + 2, airData);

        // Chest
        placeBlock(world, hx - 1, hy + 1, hz, chestData);
        Chest chestState = (Chest) world.getBlockAt(hx - 1 + 10000, hy + 1, hz).getState();
        List<Material> lootMats = Arrays.asList(Material.IRON_SWORD, Material.APPLE, Material.BREAD, Material.IRON_INGOT, Material.LEATHER_CHESTPLATE);
        int numItems = 1 + random.nextInt(3);
        for (int i = 0; i < numItems; i++) {
            Material mat = lootMats.get(random.nextInt(lootMats.size()));
            int amount = 1 + random.nextInt(5);
            chestState.getBlockInventory().addItem(new ItemStack(mat, amount));
        }
    }

    // Treehouse platform
    private static void placeTreehouse(World world, int thx, int thy, int thz, MatList matList) {
        // Use theme-appropriate wood
        Material logMat = matList.tree1.stem;
        Material plankMat;
        Material fenceMat;

        // Derive plank and fence materials from log type
        if (logMat == Material.OAK_LOG) {
            plankMat = Material.OAK_PLANKS;
            fenceMat = Material.OAK_FENCE;
        } else if (logMat == Material.SPRUCE_LOG) {
            plankMat = Material.SPRUCE_PLANKS;
            fenceMat = Material.SPRUCE_FENCE;
        } else if (logMat == Material.JUNGLE_LOG) {
            plankMat = Material.JUNGLE_PLANKS;
            fenceMat = Material.JUNGLE_FENCE;
        } else if (logMat == Material.ACACIA_LOG) {
            plankMat = Material.ACACIA_PLANKS;
            fenceMat = Material.ACACIA_FENCE;
        } else if (logMat == Material.CACTUS) {
            // Special case for cactus - use oak
            logMat = Material.OAK_LOG;
            plankMat = Material.OAK_PLANKS;
            fenceMat = Material.OAK_FENCE;
        } else if (logMat == Material.BASALT) {
            // Special case for basalt - use dark oak
            logMat = Material.BLACKSTONE;
            plankMat = Material.POLISHED_BLACKSTONE_BRICKS;
            fenceMat = Material.POLISHED_BLACKSTONE_WALL;
        } else {
            plankMat = Material.OAK_PLANKS;
            fenceMat = Material.OAK_FENCE;
        }

        BlockData plankData = Bukkit.createBlockData(plankMat);
        BlockData logData = Bukkit.createBlockData(logMat);
        BlockData fenceData = Bukkit.createBlockData(fenceMat);
        BlockData chestData = Bukkit.createBlockData(Material.CHEST);
        BlockData ladderData = Bukkit.createBlockData(Material.LADDER);
        BlockData airData = Bukkit.createBlockData(Material.AIR);

        int treeHeight = 8 + random.nextInt(4);

        // Clear area below for ladder access
        for (int dy = -1; dy <= treeHeight + 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dy == -1) {
                        placeBlock(world, thx + dx, thy + dy, thz + dz, Bukkit.createBlockData(matList.surface));
                    } else {
                        placeBlock(world, thx + dx, thy + dy, thz + dz, airData);
                    }
                }
            }
        }

        // Support trees (4 corners)
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] corner : corners) {
            for (int dy = -2; dy < treeHeight; dy++) {
                placeBlock(world, thx + corner[0], thy + dy, thz + corner[1], logData);
            }
        }

        // Platform
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                placeBlock(world, thx + dx, thy + treeHeight, thz + dz, plankData);
            }
        }

        // Walls
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                    if (!((dx == 0 && dz == 4))) { // Leave entrance
                        placeBlock(world, thx + dx, thy + treeHeight + 1, thz + dz, fenceData);
                    }
                }
            }
        }

        // Roof
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                placeBlock(world, thx + dx, thy + treeHeight + 3, thz + dz, plankData);
            }
        }

        // Ladder up center (from ground level)
        for (int dy = 0; dy <= treeHeight; dy++) {
            placeBlock(world, thx, thy + dy, thz, ladderData);
        }

        // Chest
        placeBlock(world, thx + 2, thy + treeHeight + 1, thz, chestData);
        fillChest(world, thx + 2, thy + treeHeight + 1, thz);
    }

    // Survival Handbook House
    private static void placeSurvivalHandbookHouse(World world, int startX, int startY, int startZ) {
        Material cobbleMat = Material.COBBLESTONE;
        Material plankMat = Material.OAK_PLANKS;
        Material logMat = Material.OAK_LOG;
        Material glassMat = Material.GLASS;
        Material stairMat = Material.OAK_STAIRS;

        BlockData cobbleData = Bukkit.createBlockData(cobbleMat);
        BlockData plankData = Bukkit.createBlockData(plankMat);
        BlockData logData = Bukkit.createBlockData(logMat);
        BlockData glassData = Bukkit.createBlockData(glassMat);
        BlockData airData = Bukkit.createBlockData(Material.AIR);

        // Define the perimeter coordinates starting from back-left (startX, startZ)
        List<int[]> perimeter = new ArrayList<>();
        int x = startX;
        int z = startZ;

        // Build perimeter path: right 8, forward 4, right 8, back 4, right 8, forward 14,
        // left 8, forward 4, right 8, back 4, right 8, back 14

        // Right 8
        for (int i = 0; i < 8; i++) {
            perimeter.add(new int[]{x, z});
            x++;
        }
        // Forward 4
        for (int i = 0; i < 4; i++) {
            perimeter.add(new int[]{x, z});
            z++;
        }
        // Right 8
        for (int i = 0; i < 8; i++) {
            perimeter.add(new int[]{x, z});
            x++;
        }
        // Back 4
        for (int i = 0; i < 4; i++) {
            perimeter.add(new int[]{x, z});
            z--;
        }
        // Right 8
        for (int i = 0; i < 8; i++) {
            perimeter.add(new int[]{x, z});
            x++;
        }
        // Forward 14
        for (int i = 0; i < 14; i++) {
            perimeter.add(new int[]{x, z});
            z++;
        }
        // Left 8
        for (int i = 0; i < 8; i++) {
            perimeter.add(new int[]{x, z});
            x--;
        }
        // Forward 4
        for (int i = 0; i < 4; i++) {
            perimeter.add(new int[]{x, z});
            z++;
        }
        // left 8
        for (int i = 0; i < 8; i++) {
            perimeter.add(new int[]{x, z});
            x--;
        }
        // Back 4
        for (int i = 0; i < 4; i++) {
            perimeter.add(new int[]{x, z});
            z--;
        }
        // left 8
        for (int i = 0; i < 8; i++) {
            perimeter.add(new int[]{x, z});
            x--;
        }
        // Back 14 (close the loop)
        for (int i = 0; i < 14; i++) {
            perimeter.add(new int[]{x, z});
            z--;
        }

        // Find bounds for floor filling
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] coord : perimeter) {
            minX = Math.min(minX, coord[0]);
            maxX = Math.max(maxX, coord[0]);
            minZ = Math.min(minZ, coord[1]);
            maxZ = Math.max(maxZ, coord[1]);
        }

        // Step 2: Fill floor with oak planks
        for (int px = minX; px <= maxX; px++) {
            for (int pz = minZ; pz <= maxZ; pz++) {
                if (isInsidePerimeter(px, pz, perimeter)) {
                    placeBlock(world, px, startY, pz, plankData);
                }
            }
        }

        // Step 3: Build 3 blocks of cobblestone walls on perimeter
        for (int[] coord : perimeter) {
            for (int dy = 1; dy <= 3; dy++) {
                placeBlock(world, coord[0], startY + dy, coord[1], cobbleData);
            }
        }

        // Step 4: Oak planks on top of cobblestone + second floor
        for (int[] coord : perimeter) {
            placeBlock(world, coord[0], startY + 4, coord[1], plankData);
        }
        for (int px = minX; px <= maxX; px++) {
            for (int pz = minZ; pz <= maxZ; pz++) {
                if (isInsidePerimeter(px, pz, perimeter)) {
                    placeBlock(world, px, startY + 4, pz, plankData);
                }
            }
        }

        // Step 5: Find corners and build oak logs 4 blocks up with glass walls
        List<int[]> corners = findCorners(perimeter);

        // Place corner logs (4 blocks up from second floor)
        for (int[] corner : corners) {
            for (int dy = 0; dy < 4; dy++) {
                placeBlock(world, corner[0], startY + 5 + dy, corner[1], logData);
            }
        }

        // Place glass and log sandwich walls between corners
        for (int i = 0; i < perimeter.size(); i++) {
            int[] curr = perimeter.get(i);
            boolean isCorner = false;
            for (int[] corner : corners) {
                if (curr[0] == corner[0] && curr[1] == corner[1]) {
                    isCorner = true;
                    break;
                }
            }

            if (!isCorner) {
                // Pattern: glass, glass, log, glass, glass (4 blocks total)
                int blockIndex = getDistanceFromLastCorner(perimeter, i, corners) % 3;
                for (int dy = 0; dy < 4; dy++) {
                    if (blockIndex == 2) {
                        placeBlock(world, curr[0], startY + 5 + dy, curr[1], logData);
                    } else {
                        placeBlock(world, curr[0], startY + 5 + dy, curr[1], glassData);
                    }
                }
            }
        }

        // Step 6: Make third floor
        for (int px = minX; px <= maxX; px++) {
            for (int pz = minZ; pz <= maxZ; pz++) {
                if (isInsidePerimeter(px, pz, perimeter)) {
                    placeBlock(world, px, startY + 9, pz, plankData);
                }
            }
        }
    }

    private static boolean isInsidePerimeter(int x, int z, List<int[]> perimeter) {
        // Simple point-in-polygon check using ray casting
        int intersections = 0;
        for (int i = 0; i < perimeter.size(); i++) {
            int[] p1 = perimeter.get(i);
            int[] p2 = perimeter.get((i + 1) % perimeter.size());

            if ((p1[1] > z) != (p2[1] > z)) {
                double xIntersect = (p2[0] - p1[0]) * (z - p1[1]) / (double)(p2[1] - p1[1]) + p1[0];
                if (x < xIntersect) {
                    intersections++;
                }
            }
        }
        return (intersections % 2) == 1;
    }

    private static List<int[]> findCorners(List<int[]> perimeter) {
        List<int[]> corners = new ArrayList<>();
        for (int i = 0; i < perimeter.size(); i++) {
            int[] prev = perimeter.get((i - 1 + perimeter.size()) % perimeter.size());
            int[] curr = perimeter.get(i);
            int[] next = perimeter.get((i + 1) % perimeter.size());

            int dx1 = curr[0] - prev[0];
            int dz1 = curr[1] - prev[1];
            int dx2 = next[0] - curr[0];
            int dz2 = next[1] - curr[1];

            // Direction changed = corner
            if (dx1 != dx2 || dz1 != dz2) {
                corners.add(curr);
            }
        }
        return corners;
    }

    private static int getDistanceFromLastCorner(List<int[]> perimeter, int index, List<int[]> corners) {
        int distance = 0;
        for (int i = index - 1; i >= 0; i--) {
            boolean isCorner = false;
            for (int[] corner : corners) {
                if (perimeter.get(i)[0] == corner[0] && perimeter.get(i)[1] == corner[1]) {
                    isCorner = true;
                    break;
                }
            }
            if (isCorner) break;
            distance++;
        }
        return distance;
    }

    private static boolean isOnRoofEdge(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return x == minX || x == maxX || z == minZ || z == maxZ;
    }


    private static void placeFortCircle(World world, int thx, int thz, MatList matList) {
        int radiusInner = 14;
        int radiusOuter = 20;
        int maxHeightAboveSurface = 30;
        Material wallMaterial = Material.COBBLESTONE;
        Material fillMaterial = Material.OAK_PLANKS;

        int centerX = thx;
        int centerZ = thz;
        int centerSurfaceY = getSurfaceY(centerX, centerZ, flatY, terrainType, noiseFreq, noiseAmp);

        int topY = centerSurfaceY + maxHeightAboveSurface;

        List<Location> wallLocs = new ArrayList<>();

        double angleStep = Math.PI / (2 * Math.max(radiusOuter, 1) * 4); // ~1 block spacing

        for (double angle = 0; angle < 2 * Math.PI; angle += angleStep) {
            for (int r = radiusInner; r <= radiusOuter; r++) {
                int offsetX = (int) Math.round(r * Math.cos(angle));
                int offsetZ = (int) Math.round(r * Math.sin(angle));

                int blockX = centerX + offsetX;
                int blockZ = centerZ + offsetZ;

                int surfaceY = getSurfaceY(blockX, blockZ, flatY, terrainType, noiseFreq, noiseAmp);

                Material mat = (r == radiusInner || r == radiusOuter) ? wallMaterial : fillMaterial;

                for (int y = topY; y >= surfaceY; y--) {
                    Block block = world.getBlockAt(blockX, y, blockZ);
                    if (block.getType().isAir() || block.getType() == Material.SHORT_GRASS || block.getType() == Material.TALL_GRASS) {
                        placeBlock(world, blockX, y, blockZ, Bukkit.createBlockData(mat));

                        if (mat == wallMaterial) {
                            wallLocs.add(new Location(world, blockX, y, blockZ));
                        }
                    } else {
                        // Stop at terrain (don't dig into solid ground)
                        break;
                    }
                }
            }
        }
    }

    // Helper method to fill chests with loot
    private static void fillChest(World world, int x, int y, int z) {
        fillChest(world, x, y, z, false);
    }

    private static void fillChest(World world, int x, int y, int z, boolean premium) {
        Chest chestState = (Chest) world.getBlockAt(x + 10000, y, z).getState();
        List<Material> lootMats;

        if (premium) {
            lootMats = Arrays.asList(
                    Material.DIAMOND_SWORD, Material.IRON_CHESTPLATE, Material.IRON_HELMET,
                    Material.GOLDEN_APPLE, Material.BOW, Material.ARROW, Material.DIAMOND
            );
        } else {
            lootMats = Arrays.asList(
                    Material.IRON_SWORD, Material.APPLE, Material.BREAD,
                    Material.IRON_INGOT, Material.LEATHER_CHESTPLATE, Material.ARROW
            );
        }

        int numItems = premium ? 2 + random.nextInt(4) : 1 + random.nextInt(3);
        for (int i = 0; i < numItems; i++) {
            Material mat = lootMats.get(random.nextInt(lootMats.size()));
            int amount = mat == Material.ARROW ? 5 + random.nextInt(20) : 1 + random.nextInt(3);
            chestState.getBlockInventory().addItem(new ItemStack(mat, amount));
        }
    }



    private static abstract class GeneratorTask extends BukkitRunnable {
        protected Runnable onComplete;
        protected int batchSize = 5000;
        protected World world;
        protected ZappierGames plugin;

        public GeneratorTask(ZappierGames p, World w, Runnable oc, int bs) {
            plugin = p;
            world = w;
            onComplete = oc;
            batchSize = bs;
        }

        @Override
        public void run() {
            if (processBatch()) {
                cancel();
                if (onComplete != null) onComplete.run();
            }
        }

        abstract boolean processBatch();
    }

    private static class ClearGeneratorTask extends GeneratorTask {
        private int curX, curY, curZ;
        private int xStart, zStart, w, d, minY, maxY;
        private BlockData data;

        public ClearGeneratorTask(ZappierGames p, World w, Runnable oc, int x2, int z2, int ww, int dd, int mny, int mxy) {
            super(p, w, oc, 5000);
            xStart = x2;
            zStart = z2;
            w = w;
            ww = ww;
            d = dd;
            minY = mny;
            maxY = mxy;
            data = Bukkit.createBlockData(Material.AIR);
            curX = x2;
            curZ = z2;
            curY = mny;
        }

        boolean processBatch() {
            int count = 0;
            while (count < batchSize) {
                //placeBlock(world, curX, curY, curZ, data);
                curY++;
                if (curY > maxY) {
                    curY = minY;
                    curZ++;
                }
                if (curZ > zStart + d) {
                    curZ = zStart;
                    curX++;
                }
                if (curX > xStart + w) {
                    return true;
                }
                count++;
            }
            return false;
        }
    }

    private static class TerrainGeneratorTask extends GeneratorTask {
        private int curX, curZ;
        private int half, flatY, terrainType;
        private double noiseFreq, noiseAmp;
        private BlockData dirtData, surfaceData;

        public TerrainGeneratorTask(ZappierGames p, World w, Runnable oc, int hf, int fy, int tt, double nf, double na, BlockData dd, BlockData sd) {
            super(p, w, oc, 1000);
            half = hf;
            flatY = fy;
            terrainType = tt;
            noiseFreq = nf;
            noiseAmp = na;
            dirtData = dd;
            surfaceData = sd;
            curX = -half;
            curZ = -half;
        }

        boolean processBatch() {
            int count = 0;
            while (count < batchSize) {
                int sy = getSurfaceY(curX, curZ, flatY, terrainType, noiseFreq, noiseAmp);

                clearArea(world, curX, curZ, 0, 0, sy - 10, 200);
                for (int dy = 35; dy < sy; dy++) {
                    placeBlock(world, curX, dy, curZ, dirtData);
                }
                placeBlock(world, curX, sy, curZ, surfaceData);
                curZ++;
                if (curZ > half) {
                    curZ = -half;
                    curX++;
                }
                if (curX > half) {
                    return true;
                }
                count++;
            }
            return false;
        }
    }

    private static class FoliageGeneratorTask extends GeneratorTask {
        private int curX, curZ;
        private int half, flatY, terrainType;
        private double noiseFreq, noiseAmp;
        private MatList curMatList;

        public FoliageGeneratorTask(ZappierGames p, World w, Runnable oc, int hf, int fy, int tt, double nf, double na, MatList cml) {
            super(p, w, oc, 5000);
            half = hf;
            flatY = fy;
            terrainType = tt;
            noiseFreq = nf;
            noiseAmp = na;
            curMatList = cml;
            curX = -half;
            curZ = -half;
        }

        boolean processBatch() {
            int count = 0;
            List<Material> flowers = Arrays.asList(Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID, Material.ALLIUM);
            BlockData deadBushData = Bukkit.createBlockData(Material.DEAD_BUSH);
            BlockData grassData = Bukkit.createBlockData(Material.SHORT_GRASS);
            while (count < batchSize) {
                int sy = getSurfaceY(curX, curZ, flatY, terrainType, noiseFreq, noiseAmp);
                double dist = Math.sqrt(curX * curX + curZ * curZ);
                if (dist < 15) {
                    // Skip center
                } else if (curMatList.surface == Material.GRASS_BLOCK || curMatList.surface == Material.PODZOL) {
                    if (Math.random() < 0.1) {
                        BlockData folData = Math.random() < 0.6 ? grassData : Bukkit.createBlockData(flowers.get(random.nextInt(flowers.size())));
                        placeBlock(world, curX, sy + 1, curZ, folData);
                    }
                } else if (curMatList.surface == Material.SAND || curMatList.surface == Material.RED_SAND) {
                    if (Math.random() < 0.02) {
                        placeBlock(world, curX, sy + 1, curZ, deadBushData);
                    }
                }
                curZ++;
                if (curZ > half) {
                    curZ = -half;
                    curX++;
                }
                if (curX > half) {
                    return true;
                }
                count++;
            }
            return false;
        }
    }

    public static void start(World world, int borderSize) {
        ZappierGames.resetPlayers(true, false);
        world.getEntities().stream()
                .filter(e -> !(e instanceof Player))
                .forEach(org.bukkit.entity.Entity::remove);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(10000, 0);
        borderSize = 128*2;
        border.setSize(borderSize);
        MatList curMatList = matLists[random.nextInt(matLists.length)];

        half = 128 + 64;
        minClearY = 40;
        maxClearY = 120;
        flatY = 60;
        terrainType = random.nextInt(3); // 0: flat, 1: pit, 2: mountain
        noiseFreq = 0.05;
        noiseAmp = 4.0;

        BlockData dirtData = Bukkit.createBlockData(Material.DIRT);
        BlockData surfaceData = Bukkit.createBlockData(curMatList.surface);

        int liquidType = random.nextInt(4); // 0: none, 1: water, 2: lava, 3: smoky water
        int liquidSize = liquidType == 0 ? 0 : random.nextInt(2); // 0: small, 1: large
        boolean hasLilyPads = (liquidType == 1 || liquidType == 3) && liquidSize == 1 && random.nextBoolean();
        Material liquidMat = liquidType == 2 ? Material.LAVA : Material.WATER;

        platformRadius = 25 + random.nextInt(26); // 25-50
        int innerRadius = 4;
        int outerRadius = liquidSize == 0 ? 12 : platformRadius - random.nextInt(5) - 1;

        int centerTreeHeight = random.nextInt(curMatList.tree1.maxH - curMatList.tree1.minH + 1) + curMatList.tree1.minH;

        int numTrees = 80 + random.nextInt(240);
        int numPaths = 8 + random.nextInt(5);
        int numStructures = 5 + random.nextInt(5);
        int numChests = 10 + random.nextInt(10);

        ZappierGames pluginInstance = ZappierGames.instance; // Assuming this exists

        Runnable finalAction = () -> {
            // Center tree
            int centerY = getSurfaceY(0, 0, flatY, terrainType, noiseFreq, noiseAmp);
            placeTree(world, curMatList.tree1.stem, curMatList.tree1.leaf, 0, centerY, 0, centerTreeHeight);

            // Liquid (moat/lava)
            if (liquidType != 0) {
                for (int i = -outerRadius; i <= outerRadius; i++) {
                    for (int j = -outerRadius; j <= outerRadius; j++) {
                        int cc = i * i + j * j;
                        if (cc > innerRadius * innerRadius && cc <= outerRadius * outerRadius) {
                            int sy = getSurfaceY(i, j, flatY, terrainType, noiseFreq, noiseAmp);
                            if (liquidType == 3 && random.nextFloat() < 0.0f) {
                                BlockData cfData = Bukkit.createBlockData(Material.CAMPFIRE);
                                Campfire cf = (Campfire) cfData;
                                cf.setLit(true);
                                cf.setWaterlogged(true);
                                placeBlock(world, i, sy, j, cfData);
                            } else {
                                placeBlock(world, i, sy, j, Bukkit.createBlockData(liquidMat));
                            }
                        }
                    }
                }
            }

            // Lily pads if applicable
            BlockData lilyData = Bukkit.createBlockData(Material.LILY_PAD);
            if (hasLilyPads) {
                int numLilies = 100 + random.nextInt(100);
                for (int k = 0; k < numLilies; k++) {
                    int lx = -outerRadius + random.nextInt(outerRadius * 2 + 1);
                    int lz = -outerRadius + random.nextInt(outerRadius * 2 + 1);
                    int lcc = lx * lx + lz * lz;
                    if (lcc > innerRadius * innerRadius && lcc <= outerRadius * outerRadius) {
                        int sy = getSurfaceY(lx, lz, flatY, terrainType, noiseFreq, noiseAmp);
                        Block block = world.getBlockAt(lx + 10000, sy, lz);
                        if (block.getType() == Material.WATER) {
                            placeBlock(world, lx, sy + 1, lz, lilyData);
                        }
                    }
                }
            }

            // Spawn platforms
            BlockData slabData = Bukkit.createBlockData(Material.SMOOTH_STONE_SLAB);
            for (double i = 0.0; i < 360.0; i += 360.0 / 30.0) {
                int px = (int) (Math.cos(i * Math.PI / 180) * platformRadius);
                int pz = (int) (Math.sin(i * Math.PI / 180) * platformRadius);
                int py = getSurfaceY(px, pz, flatY, terrainType, noiseFreq, noiseAmp) + 1;
                placeBlock(world, px, py, pz, slabData);
            }

            // Additional trees
            for (int t = 0; t < numTrees; t++) {
                int tx = -half + random.nextInt(half * 2 + 1);
                int tz = -half + random.nextInt(half * 2 + 1);
                double dist = Math.sqrt(tx * tx + tz * tz);
                if (dist < platformRadius + 5 || dist > half - 5) continue;
                int ty = getSurfaceY(tx, tz, flatY, terrainType, noiseFreq, noiseAmp);
                int treeHeight = random.nextInt(curMatList.tree1.maxH - curMatList.tree1.minH + 1) + curMatList.tree1.minH;
                placeTree(world, curMatList.tree1.stem, curMatList.tree1.leaf, tx, ty, tz, treeHeight);
            }

            // Paths
            BlockData pathData = Bukkit.createBlockData(Material.DIRT_PATH);
            for (int p = 0; p < numPaths; p++) {
                double angle = Math.random() * 360;
                int pathLength = 100 + random.nextInt(50);
                for (int r = platformRadius + 10; r < pathLength; r += 2) {
                    int px = (int) (Math.cos(angle * Math.PI / 180) * r);
                    int pz = (int) (Math.sin(angle * Math.PI / 180) * r);
                    if (Math.abs(px) > half || Math.abs(pz) > half) break;
                    int py = getSurfaceY(px, pz, flatY, terrainType, noiseFreq, noiseAmp);
                    placeBlock(world, px, py, pz, pathData);
                    placeBlock(world, px + 1, py, pz, pathData);
                }
            }

// Place treehouses
            for (int i = 0; i < 4; i++) {
                int thx = -half + random.nextInt(half * 2 + 1);
                int thz = -half + random.nextInt(half * 2 + 1);
                double dist = Math.sqrt(thx * thx + thz * thz);
                if (dist < platformRadius + 15 || dist > half - 15) continue;
                int thy = getSurfaceY(thx, thz, flatY, terrainType, noiseFreq, noiseAmp);
                placeTreehouse(world, thx, thy, thz, curMatList);
            }

            placeFortCircle(world, -half / 2, -half / 2, curMatList);
            placeFortCircle(world, -half / 2 + 50, -half / 2, curMatList);


            placeMineshaft(world, -half / 2, 60, half / 2);
            placeSurvivalHandbookHouse(world, half / 3, 40, half / 3);

            // Scattered chests
            int numScatteredChests = 10 + random.nextInt(10);
            BlockData chestData = Bukkit.createBlockData(Material.CHEST);
            for (int c = 0; c < numScatteredChests; c++) {
                int cx = -half + random.nextInt(half * 2 + 1);
                int cz = -half + random.nextInt(half * 2 + 1);
                double dist = Math.sqrt(cx * cx + cz * cz);
                if (dist < platformRadius + 5 || dist > half - 5) continue;
                int cy = getSurfaceY(cx, cz, flatY, terrainType, noiseFreq, noiseAmp) + 1;
                placeBlock(world, cx, cy, cz, chestData);
                Chest chestState = (Chest) world.getBlockAt(cx + 10000, cy, cz).getState();
                List<Material> lootMats = Arrays.asList(Material.IRON_SWORD, Material.APPLE, Material.BREAD, Material.IRON_INGOT, Material.LEATHER_CHESTPLATE);
                int numItems = 1 + random.nextInt(3);
                for (int i = 0; i < numItems; i++) {
                    Material mat = lootMats.get(random.nextInt(lootMats.size()));
                    int amount = 1 + random.nextInt(5);
                    chestState.getBlockInventory().addItem(new ItemStack(mat, amount));
                }
            }

            // Teleport players
            Location tpLocation = new Location(world, 10000, centerY + 4, 0);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(tpLocation);
                p.sendMessage(Component.text("WARNING: Gamemode not made yet. WIP", NamedTextColor.DARK_RED));
            }
            ZappierGames.gameMode = 1000;
        };

        FoliageGeneratorTask foliageTask = new FoliageGeneratorTask(pluginInstance, world, finalAction, half, flatY, terrainType, noiseFreq, noiseAmp, curMatList);
        TerrainGeneratorTask terrainTask = new TerrainGeneratorTask(pluginInstance, world, () -> foliageTask.runTaskTimer(pluginInstance, 0, 1), half, flatY, terrainType, noiseFreq, noiseAmp, dirtData, surfaceData);
        ClearGeneratorTask clearTask = new ClearGeneratorTask(pluginInstance, world, () -> terrainTask.runTaskTimer(pluginInstance, 0, 1), -half, -half, half * 2, half * 2, minClearY, maxClearY);
        clearTask.runTaskTimer(pluginInstance, 0, 1);
    }
}