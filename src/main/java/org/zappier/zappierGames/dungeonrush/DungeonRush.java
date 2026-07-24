package org.zappier.zappierGames.dungeonrush;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;
import org.zappier.zappierGames.ZappierGames;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DungeonRush {
    private static JavaPlugin plugin;
    public static void placeBlock(World world, int x, int y, int z, BlockData data) {
        x -= 10000;
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

    public static String[] roomSegments = {
            "dungeon_corner_2",
            "dungeon_corner_4",
            "dungeon_intersection_2",
            "dungeon_intersection_4",
            "dungeon_path_2",
            "dungeon_path_3",
            "dungeon_path_4",
            "dungeon_room_1",
            "dungeon_room_3",
            "dungeon_room_6",
            "dungeon_room_8",
            "dungeon_room_9",
            "dungeon_split_2",
            "dungeon_split_4",
            "floor1_lava1",
            "floor1_stairway1",
            "floor1_start1"
    };
    public enum Face { NORTH, SOUTH, EAST, WEST }

    public record Exit(Face face, int segmentIndex, int yIndex) {}

    public static class RoomInfo {
        public final String name;
        public final int width, height, depth;
        public final List<ItemStack> frameItems;
        public final Set<Exit> exits;

        public RoomInfo(String name, int w, int h, int d, List<ItemStack> items, Set<Exit> exits) {
            this.name = name;
            this.width = w;
            this.height = h;
            this.depth = d;
            this.frameItems = items;
            this.exits = exits;
        }
    }

    public static StructureManager sm;
    public static Map<String, RoomInfo> roomDataMap = new HashMap<>();
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        sm = plugin.getServer().getStructureManager();
        for (String roomName : roomSegments) {
            String resourcePath = "dungeonrush/" + roomName + ".nbt";

            try (InputStream is = plugin.getResource(resourcePath)) {
                if (is == null) {
                    plugin.getLogger().warning("Could not find: " + resourcePath);
                    continue;
                }

                // 1. Create a temporary file so StructureManager can read it
                java.io.File tempFile = java.io.File.createTempFile(roomName, ".nbt");
                tempFile.deleteOnExit(); // Cleanup in case of a crash

                // 2. Copy the resource bytes into the temp file
                java.nio.file.Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // 3. Use loadStructure(File) which is more widely supported
                Structure structure = sm.loadStructure(tempFile);

                if (structure != null) {
                    BlockVector size = structure.getSize();
                    int gridW = size.getBlockX() / 16;
                    int gridH = size.getBlockY() / 8;
                    int gridD = size.getBlockZ() / 16;

                    Set<Exit> exitsFound = new HashSet<>();
                    List<ItemStack> otherItems = new ArrayList<>();

                    for (org.bukkit.entity.Entity entity : structure.getEntities()) {
                        if (entity instanceof ItemFrame frame) {
                            ItemStack item = frame.getItem();
                            if (item == null || item.getType() == Material.AIR) continue;

                            // If it's a torch, it's an exit marker
                            if (item.getType() == Material.TORCH) {
                                int x = entity.getLocation().getBlockX();
                                int y = entity.getLocation().getBlockY(); // Get Y coordinate
                                int z = entity.getLocation().getBlockZ();

                                int yIdx = y / 8; // Determine which "floor" the exit is on (0, 1, 2, etc.)

                                if (x == 0) {
                                    exitsFound.add(new Exit(Face.WEST, z / 16, yIdx));
                                } else if (x == size.getBlockX() - 1) {
                                    exitsFound.add(new Exit(Face.EAST, z / 16, yIdx));
                                } else if (z == 0) {
                                    exitsFound.add(new Exit(Face.NORTH, x / 16, yIdx));
                                } else if (z == size.getBlockZ() - 1) {
                                    exitsFound.add(new Exit(Face.SOUTH, x / 16, yIdx));
                                }
                            } else {
                                // It's a normal item (loot, room type tag, etc)
                                otherItems.add(item.clone());
                            }
                        }
                    }

                    roomDataMap.put(roomName, new RoomInfo(roomName, gridW, gridH, gridD, otherItems, exitsFound));

                    plugin.getLogger().info(String.format("Loaded %s: %dx%dx%d with %d exits.",
                            roomName, gridW, gridH, gridD, exitsFound.size()));
                }

                // 4. Clean up the temp file immediately
                tempFile.delete();

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load " + roomName);
                e.printStackTrace();
            }
        }
    }

    private static void loadAndPlace(World world, String nbtName,
                                     int x, int y, int z,
                                     StructureManager sm) throws Exception {

        InputStream nbtStream = plugin.getResource("dungeonrush/" + nbtName);
        if (nbtStream == null) {
            throw new IllegalStateException("Could not find " + nbtName + " in resources/dungeonrush/");
        }

        Path tempFile = Files.createTempFile(nbtName.replace(".nbt", ""), ".nbt");
        Files.copy(nbtStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        nbtStream.close();

        Structure struct = sm.loadStructure(tempFile.toFile());
        if (struct == null) {
            Bukkit.broadcastMessage("fail");
            throw new IllegalStateException("Failed to load structure from NBT file.");
        }

        struct.place(new Location(world, x - 10000, y, z), true,
                StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());

        //LOGGER.info("Placed " + nbtName + " at " + x + "," + y + "," + z);

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ex) {
            //LOGGER.warning("Could not delete temp file: " + ex.getMessage());
        }
    }


    public static void start(World world) {
        Location tpLocation = new Location(world, -10000 + 8*16, 100 + 4 + 80, 0+8*16);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(tpLocation);
            p.sendMessage(Component.text("WARNING: Gamemode not made yet. WIP", NamedTextColor.DARK_RED));
        }
        world.getEntities().stream()
                .filter(e -> !(e instanceof org.bukkit.entity.Player))
                .forEach(org.bukkit.entity.Entity::remove);
        ZappierGames.gameMode = 2000;

        final int boundX = 16;
        final int boundY = 16;
        final int boundZ = 16;
        final int offsetY = 0;
        int[][][] level = new int[boundX][32][boundZ];
        for (int i = 0; i < boundX; i++) {
            for (int j = 0; j < 32; j++) {
                for (int k = 0; k < boundZ; k++) {
                    level[i][j][k] = 0;
                }
            }
        }
        fillArea(world, Material.AIR, 0, 0, boundX*16, boundZ*16, 0, 256);
        int attempts = 0;
        int[] levelStart = {8, 20, 8};
        int[] levelEnd = {10, 21, 12};
        spawnBlazeBoss(new Location(world, levelEnd[0] * 16 + 8, levelEnd[1] * 8 + 4, levelEnd[2] * 16 + 8));

        level[levelStart[0]][levelStart[1]][levelStart[2]] = 1;
        level[levelEnd[0]][levelEnd[1]][levelEnd[2]] = 1;

        fillArea(world, Material.OAK_PLANKS, levelStart[0] * 16, levelStart[2] * 16, 15, 15, levelStart[1] * 8, levelStart[1] * 8 + 7);
        fillArea(world, Material.AIR, levelStart[0] * 16 + 1, levelStart[2] * 16 + 1, 15 - 2, 15 - 2, levelStart[1] * 8 + 1, levelStart[1] * 8 + 7 - 1);
        fillArea(world, Material.NETHER_BRICKS, levelEnd[0] * 16, levelEnd[2] * 16, 15, 15, levelEnd[1] * 8, levelEnd[1] * 8 + 7);
        fillArea(world, Material.AIR, levelEnd[0] * 16 + 1, levelEnd[2] * 16 + 1, 15 - 2, 15 - 2, levelEnd[1] * 8 + 1, levelEnd[1] * 8 + 7 - 1);
        int roomId = 2;
        while (attempts < 50 && (levelStart[0] != levelEnd[0] || levelStart[1] != levelEnd[1] || levelStart[2] != levelEnd[2])) {
             if (attempts > 0 && levelStart[1] != levelEnd[1]) {
                levelStart[1] -= Math.signum(levelStart[1] - levelEnd[1]);
                roomId--;
            } else if (levelStart[0] != levelEnd[0]) {
                levelStart[0] -= Math.signum(levelStart[0] - levelEnd[0]);
            } else if (levelStart[2] != levelEnd[2]) {
                levelStart[2] -= Math.signum(levelStart[2] - levelEnd[2]);
            }
            if (levelStart[0] == levelEnd[0] && levelStart[1] == levelEnd[1] && levelStart[2] == levelEnd[2]) {
                break;
            }
            roomId++;
            level[levelStart[0]][levelStart[1]][levelStart[2]] = roomId;
            attempts++;
        }

        HashMap<Integer, ArrayList<Integer>> neighborRooms = new HashMap<>();

        //neighborRooms.put(-1, ); new ArrayList<Integer>().add() //add starter room and end room

        for (int i = 0; i < level.length; i++) {
            for (int j = 0; j < level[i].length; j++) {
                for (int k = 0; k < level[i][j].length; k++) {
                    int id = level[i][j][k];
                    if (id <= 1) {continue;}
                    int castX = i;
                    int sizeX = 0;

                    while (castX < level.length && level[castX][j][k] == id) {
                        castX++;
                        sizeX++;
                    }
                    int castY = j;
                    int sizeY = 0;
                    while (castY < level[i].length && level[i][castY][k] == id) {
                        castY++;
                        sizeY++;
                    }
                    int castZ = k;
                    int sizeZ = 0;
                    while (castZ < level[i][j].length && level[i][j][castZ] == id) {
                        castZ++;
                        sizeZ++;
                    }

                    for (int a = i; a < i+sizeX; a++) {
                        for (int b = j; b < j+sizeY; b++) {
                            for (int c = k; c < k+sizeZ; c++) {//absolute cinema
                                level[a][b][c] = -id;
                            }
                        }
                    }
                    neighborRooms.put(id, new ArrayList<>());
                    neighborRooms.get(id).add(i);
                    neighborRooms.get(id).add(j);
                    neighborRooms.get(id).add(k);
                    neighborRooms.get(id).add(sizeX);
                    neighborRooms.get(id).add(sizeY);
                    neighborRooms.get(id).add(sizeZ);
                    /*try {
                        int[] offset = {i*16,j*8,k*16};
                        String roomName = "";
                        ArrayList<Integer> roomCandidates = new ArrayList<>();
                        for (int l = 0; l < roomSegments.length; l++) {
                            if (roomDataMap.get(roomSegments[l]).width == sizeX &&
                                    roomDataMap.get(roomSegments[l]).height == sizeY &&
                                    roomDataMap.get(roomSegments[l]).depth == sizeZ) {
                                roomCandidates.add(l);
                            }
                        }

                        int roomIndex = (int)(Math.random() * roomCandidates.size());
                        Bukkit.broadcastMessage("" + roomCandidates.size());
                        roomName = roomSegments[roomCandidates.get(roomIndex)];
                        loadAndPlace(world, roomName + ".nbt", offset[0], offset[1], offset[2], sm);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }*/
                }
            }
        }


        for (int i = 0; i < 100; i++) {
            ArrayList<Integer> rn = neighborRooms.get(i);
            if (rn == null) {continue;}
            int[] pos1 = {rn.get(0),rn.get(1),rn.get(2)};
            int[] size1 = {rn.get(3),rn.get(4),rn.get(5)};
            for (int j = i + 1; j < 100; j++) {
                //if (i == j) {continue;}
                ArrayList<Integer> rm = neighborRooms.get(j);
                if (rm == null) {continue;}
                int[] pos2 = {rm.get(0),rm.get(1),rm.get(2)};
                int[] size2 = {rn.get(3),rn.get(4),rn.get(5)};
                int xAligned = 1;
                int zAligned = 1;
                if (pos2[0] + size2[0] < pos1[0] || pos2[0] > pos1[0] + size1[0]) {
                    xAligned = 0;
                } else if (pos2[2] + size2[2] < pos1[2] || pos2[2] > pos1[2] + size1[2]) {
                    zAligned = 0;
                }
                if (xAligned + zAligned < 2) {
                    continue;
                }
                xAligned = 1;
                zAligned = 1;
                if (pos2[0] + size2[0] - 1 < pos1[0] || pos2[0] > pos1[0] + size1[0] - 1) {
                    xAligned = 0;
                } else if (pos2[2] + size2[2] - 1 < pos1[2] || pos2[2] > pos1[2] + size1[2] - 1) {
                    zAligned = 0;
                }
                if (xAligned + zAligned < 1) {
                    continue;
                }

                //Bukkit.broadcastMessage("Neighboring Rooms: " + i + ", " + j);
                rn.add(j);
                rm.add(i);
            }
        }

        for (int i = 0; i < 100; i++) {
            ArrayList<Integer> rn = neighborRooms.get(i);
            if (rn == null) {continue;}
            /*for (int j = 6; j < rn.size(); j++) {
                Bukkit.broadcastMessage("Room neighbors: " + i + ", " + rn.get(j));
            }*/

            try {
                int[] offset = {rn.get(0)*16,rn.get(1)*8,rn.get(2)*16};
                String roomName = "";
                ArrayList<Integer> roomCandidates = new ArrayList<>();
                for (int l = 0; l < roomSegments.length; l++) {
                    RoomInfo curData = roomDataMap.get(roomSegments[l]);
                    if (curData.width == rn.get(3) &&
                            curData.height == rn.get(4) &&
                            curData.depth == rn.get(5)) {
                        //public record Exit(Face face, int segmentIndex, int yIndex) {}
                        if (curData.height == 1 && !curData.exits.contains(new Exit(Face.SOUTH, curData.width - 1, 0))) {
                        } else {
                            roomCandidates.add(l);
                        }
                    }
                }

                int roomIndex = (int)(Math.random() * roomCandidates.size());
                Bukkit.broadcastMessage("" + roomCandidates.size());
                roomName = roomSegments[roomCandidates.get(roomIndex)];
                loadAndPlace(world, roomName + ".nbt", offset[0], offset[1], offset[2], sm);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        while (attempts < 0 && (levelStart[0] != levelEnd[0] || levelStart[1] != levelEnd[1] || levelStart[2] != levelEnd[2])) {
            double dir = Math.random();
            int[] adder = {0, 0, 0};
            if (dir < 0.2) {
                adder[0]--;
            } else if (dir < 0.4) {
                adder[0]++;
            } else if (dir < 0.6) {
                adder[2]--;
            } else if (dir < 0.8) {
                adder[2]++;
            } else if (dir < 0.9) {
                adder[1]--;
            } else {
                adder[1]++;
            }
            levelStart[0] += adder[0];
            levelStart[1] += adder[1];
            levelStart[2] += adder[2];
            if (levelStart[0] < 0) {levelStart[0] = 0;}
            if (levelStart[0] >= boundX) {levelStart[0] = boundX - 1;}
            if (levelStart[1] < 0) {levelStart[0] = 0;}
            if (levelStart[1] >= 32) {levelStart[0] = 32 - 1;}
            if (levelStart[2] < 0) {levelStart[2] = 0;}
            if (levelStart[2] >= boundZ) {levelStart[2] = boundZ - 1;}
            if (level[levelStart[0]][levelStart[1]][levelStart[2]] == 0) {
                int[] offset = {levelStart[0]*16,levelStart[1]*8,levelStart[2]*16};
                //fillArea(world, Material.STONE_BRICKS, offset[0], offset[2], 15, 15, offset[1], offset[1] + 7);
                //fillArea(world, Material.AIR, offset[0] + 1, offset[2] + 1, 15 - 2, 15 - 2, offset[1] + 1, offset[1] + 7 - 1);
                try {
                    String roomName = "";
                    int roomIndex = (int)(Math.random() * roomSegments.length);
                    roomName = roomSegments[roomIndex];
                    loadAndPlace(world, roomName + ".nbt", offset[0], offset[1], offset[2], sm);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                level[levelStart[0]][levelStart[1]][levelStart[2]] = 1;
            }
            attempts++;
        }
    }

    public static void run(World world) {
        for (int i = 0; i < bosses.size(); i++) {
            if (bosses.get(i).type.equals("Conflagration")) {
                runConflagration(bosses.get(i));
            }
        }
    }




    public static class BossEntity {
        public Entity entity;
        public String type;
        public ArrayList<Integer> data;
    }
    private static final List<BossEntity> bosses = new ArrayList<>();
    private static void spawnBlazeBoss(Location location) {
        Bukkit.broadcastMessage("???");
        location.add(-10000, 0, 0);
        World world = location.getWorld();
        if (world == null) return;

        Blaze blaze = world.spawn(location, Blaze.class);
        blaze.setCustomName("Conflagration");
        blaze.getAttribute(Attribute.MAX_HEALTH).setBaseValue(400);
        blaze.setHealth(400);

        blaze.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_AXE));

        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        boots.addUnsafeEnchantment(Enchantment.PROTECTION, 10);
        blaze.getEquipment().setBoots(boots);

        ItemStack chestplate = new ItemStack(Material.ITEM_FRAME, 1);
        ItemMeta meta = chestplate.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Blazing Eye");
            meta.setCustomModelData(1150006);
            chestplate.setItemMeta(meta);
        }
        blaze.getEquipment().setChestplate(chestplate);

        BossEntity bossEntity = new BossEntity();
        bossEntity.entity = blaze;
        bossEntity.type = "Conflagration";
        bossEntity.data = new ArrayList<>();
        bossEntity.data.add(0);
        bossEntity.data.add(0);
        bossEntity.data.add(0);

        bosses.add(bossEntity);
        Bukkit.broadcastMessage("" + location.toString());
    }

    public static void runConflagration(BossEntity bossEntity) {
        bossEntity.entity.setVelocity(new Vector(0.0, 0.0, 0.0));
        int attackType = bossEntity.data.get(0);
        int attackTimer1 = bossEntity.data.get(1);
        int attackTimer2 = bossEntity.data.get(2);

        attackTimer1++;
        if (attackType == 0) {
            if (attackTimer1 < 40) {
                bossEntity.entity.setVelocity(new Vector(0.0, 0.1, 0.0));
                if (attackTimer1 % 5 == 0) {
                    float pitch = 1.5f;
                    pitch += (attackTimer1 / 40.0f) * 0.5f;
                    bossEntity.entity.getLocation().getWorld().playSound(bossEntity.entity.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.0f, pitch);
                    bossEntity.entity.getLocation().getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, bossEntity.entity.getLocation(), 1, 0, 0.01, 0, 0.01);
                }
            } else if (attackTimer1 < 80) {
                bossEntity.entity.setVelocity(new Vector(0.0, -10.0, 0.0));
                if (bossEntity.entity.isOnGround()) {
                    attackTimer1 = 80;
                    bossEntity.entity.getLocation().getWorld().playSound(bossEntity.entity.getLocation(), Sound.ENTITY_BLAZE_HURT, 1.0f, 0.5f);
                    List<Player> targets = getNearbyPlayers(bossEntity.entity, 10);
                    for (Player p : targets) {
                        if (!p.isOnGround()) {continue;}
                        p.setVelocity(new Vector(0.0, 10.0, 0.0));
                        p.damage(18.0, bossEntity.entity);
                    }
                }
            }
        }

        if (attackTimer1 > 120) {
            attackTimer1 = 0;
            attackType++;
            if (attackType > 1) {
                attackType = 0;
            }
            Bukkit.broadcastMessage("Attack Type: " + attackType);
        }

        bossEntity.data.set(0, attackType);
        bossEntity.data.set(1, attackTimer1);
        bossEntity.data.set(2, attackTimer2);
    }

    private static List<Player> getNearbyPlayers(Blaze blaze, double radius) {
        List<Player> players = new ArrayList<>();
        for (Entity e : blaze.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p &&
                    !p.getGameMode().equals(GameMode.CREATIVE) &&
                    !p.getGameMode().equals(GameMode.SPECTATOR)) {
                players.add(p);
            }
        }
        return players;
    }

    private static List<Player> getNearbyPlayers(Entity entity, double radius) {
        List<Player> players = new ArrayList<>();
        for (Entity e : entity.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p &&
                    !p.getGameMode().equals(GameMode.CREATIVE) &&
                    !p.getGameMode().equals(GameMode.SPECTATOR)) {
                players.add(p);
            }
        }
        return players;
    }
}
