package org.zappier.zappierGames.biomeparkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.zappier.zappierGames.ZappierGames;

import java.util.*;

/**
 * Biome Parkour gamemode:
 * - Border moves constantly in +Z direction
 * - Speed starts at 0.25 blocks/tick, increases by 0.25 every 45 seconds
 * - Two modes: LIVES (respawn with life loss) and POINTS (score-based)
 * - Points mode: +1 pt/tick safe, -1 pt/tick in water after 10 ticks grace (boats protect)
 * - End conditions:
 *   - Lives: all players out of lives → most lives wins
 *   - Points: speed reaches 6.0 blocks/tick → highest score wins
 */
public class BiomeParkour {

    public enum Mode {
        LIVES,
        POINTS
    }

    // Configurable / runtime values
    public static Mode currentMode = Mode.POINTS;
    public static double borderX = 0.0;
    public static double borderZ = 0.0;
    public static int borderSize = 100;

    public static double currentSpeed = 0.05;
    public static final double SPEED_START = 0.05;
    public static final double SPEED_STEP = 0.05;
    public static final int SPEEDUP_INTERVAL_TICKS = 45 * 20; // 45 seconds

    public static int ticksElapsed = 0;
    public static boolean gameRunning = false;

    // Lives mode data
    public static final int STARTING_LIVES = 3;
    public static Map<UUID, Integer> lives = new HashMap<>();
    public static Map<UUID, Long> eliminationTimestamps = new HashMap<>(); // for tie-breaking

    // Points mode data
    public static Map<UUID, Double> points = new HashMap<>();
    public static Map<UUID, Integer> waterTicksIn = new HashMap<>();

    public static final double POINTS_PER_TICK_SAFE = 1.0;
    public static final double POINTS_PENALTY_PER_TICK = -1.0;
    public static final int WATER_GRACE_PERIOD_TICKS = 10;
    public static final double POINTS_RESPAWN_PENALTY = -100.0;

    // End trigger for points mode
    public static final double POINTS_MODE_END_SPEED = 0.5;

    private static final Random random = new Random();

    /**
     * Start a new Biome Parkour game
     */
    public static void start(Mode mode, double centerX, double centerZ, int size) {
        currentMode = mode;
        borderX = centerX;
        borderZ = centerZ;
        borderSize = Math.max(10, size); // reasonable minimum

        currentSpeed = SPEED_START;
        ticksElapsed = 0;
        gameRunning = true;

        lives.clear();
        points.clear();
        waterTicksIn.clear();
        eliminationTimestamps.clear();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Biome Parkour starting! Mode: " + mode.name());
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Border moving NORTH at " + String.format("%.2f", currentSpeed) + " blocks/tick");

        World world = Bukkit.getWorlds().get(0); // adjust if using custom world
        world.getWorldBorder().setCenter(borderX, borderZ);
        world.getWorldBorder().setSize(borderSize);
        world.setTime(6000);
        world.setStorm(false);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            resetAndTeleportPlayer(p);

            if (currentMode == Mode.LIVES) {
                lives.put(p.getUniqueId(), STARTING_LIVES);
                p.sendMessage(ChatColor.GREEN + "You have " + STARTING_LIVES + " lives!");
            } else {
                points.put(p.getUniqueId(), 0.0);
                waterTicksIn.put(p.getUniqueId(), 0);
                p.sendMessage(ChatColor.GREEN + "Points mode – survive and score!");
            }
        }

        ZappierGames.gameMode = 30; // assuming this is your identifier for Biome Parkour
    }

    public static void stop() {
        if (!gameRunning) return;
        gameRunning = false;
        Bukkit.broadcastMessage(ChatColor.RED + "Biome Parkour has been manually stopped.");
        cleanup();
    }

    private static void cleanup() {
        lives.clear();
        points.clear();
        waterTicksIn.clear();
        eliminationTimestamps.clear();
        currentSpeed = SPEED_START;
        ticksElapsed = 0;
    }

    /**
     * Main game tick – call this every server tick (from scheduler)
     */
    public static void run() {
        if (!gameRunning) return;

        ticksElapsed++;

        // Speed increase
        if (ticksElapsed % SPEEDUP_INTERVAL_TICKS == 0 && ticksElapsed > 0) {
            currentSpeed += SPEED_STEP;
            Bukkit.broadcastMessage(ChatColor.GOLD + "Border speed increased to "
                    + String.format("%.2f", currentSpeed) + " blocks/tick!");

            // Points mode auto-end check
            if (currentMode == Mode.POINTS && currentSpeed >= POINTS_MODE_END_SPEED) {
                endGamePoints();
                return;
            }
        }

        // Move border (always +Z)
        borderZ += currentSpeed;

        // Update world border
        World world = Bukkit.getWorlds().get(0); // ← customize if needed
        world.getWorldBorder().setCenter(borderX, borderZ);
        world.getWorldBorder().setSize(borderSize);

        int alivePlayers = 0;
        Player lastAlive = null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;

            alivePlayers++;
            lastAlive = p;

            updatePlayerScoreAndStatus(p);

            // Action bar
            String actionBarText = getActionBarForPlayer(p);
            p.sendActionBar(Component.text(actionBarText, NamedTextColor.GOLD));
        }

        // Lives mode end condition
        if (currentMode == Mode.LIVES && alivePlayers <= 0) {
            endGameLives();
        }
    }

    private static void updatePlayerScoreAndStatus(Player p) {
        UUID uuid = p.getUniqueId();
        Location loc = p.getLocation();

        if (currentMode == Mode.POINTS) {
            boolean isInWater = loc.getBlock().isLiquid();
            boolean isInBoat = p.getVehicle() instanceof Boat;

            int currentWaterTicks = waterTicksIn.getOrDefault(uuid, 0);

            if (isInWater && !isInBoat) {
                currentWaterTicks++;
                waterTicksIn.put(uuid, currentWaterTicks);

                if (currentWaterTicks > WATER_GRACE_PERIOD_TICKS) {
                    addPoints(uuid, POINTS_PENALTY_PER_TICK);
                }
            } else {
                // Reset water counter and give safe tick point
                waterTicksIn.put(uuid, 0);
                addPoints(uuid, POINTS_PER_TICK_SAFE);
            }
        }
        // Lives mode: no per-tick scoring
    }

    private static void addPoints(UUID uuid, double amount) {
        double current = points.getOrDefault(uuid, 0.0);
        points.put(uuid, current + amount);
    }

    private static String getActionBarForPlayer(Player p) {
        UUID uuid = p.getUniqueId();
        if (currentMode == Mode.LIVES) {
            int livesLeft = lives.getOrDefault(uuid, 0);
            return ChatColor.RED + "Lives: " + livesLeft
                    + ChatColor.GRAY + "  |  Speed: " + String.format("%.2f", currentSpeed);
        } else {
            double score = points.getOrDefault(uuid, 0.0);
            return ChatColor.YELLOW + "Points: " + String.format("%.1f", score)
                    + ChatColor.GRAY + "  |  Speed: " + String.format("%.2f", currentSpeed);
        }
    }

    public static void onPlayerDeath(Player p) {
        if (!gameRunning) return;

        UUID uuid = p.getUniqueId();

        if (currentMode == Mode.LIVES) {
            int remaining = lives.getOrDefault(uuid, 1) - 1;
            lives.put(uuid, remaining);

            if (remaining <= 0) {
                p.sendMessage(ChatColor.RED + "You are out of lives! Game over for you.");
                p.setGameMode(GameMode.SPECTATOR);
                eliminationTimestamps.put(uuid, System.currentTimeMillis());
            } else {
                p.sendMessage(ChatColor.YELLOW + "You died! " + remaining + " lives remaining.");
                respawnPlayer(p);
            }
        } else { // POINTS mode
            double newScore = points.getOrDefault(uuid, 0.0) + POINTS_RESPAWN_PENALTY;
            points.put(uuid, newScore);
            p.sendMessage(ChatColor.RED + "Respawn penalty: -100 points");
            respawnPlayer(p);
        }
    }

    private static void respawnPlayer(Player p) {
        Location center = new Location(p.getWorld(), borderX, 100.0, borderZ);
        p.teleport(center);
        resetAndTeleportPlayer(p);
    }

    private static void resetAndTeleportPlayer(Player p) {
        p.getInventory().clear();
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20.0f);
        p.setFireTicks(0);
        p.clearActivePotionEffects();

        giveStartingKit(p);

        // Optional: small random offset so players don't stack
        double offsetX = (random.nextDouble() - 0.5) * 6;
        double offsetZ = (random.nextDouble() - 0.5) * 6;
        p.teleport(p.getLocation().add(offsetX, 0, offsetZ));
    }

    /**
     * Easy to override / customize the starting kit
     */
    public static void giveStartingKit(Player p) {
        p.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 64));
        p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET, 2));
        p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16));
        p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE, 1));
        p.getInventory().addItem(new ItemStack(Material.IRON_AXE, 1));

        // Example alternative kit (uncomment to use):
        // ItemStack kbStick = new ItemStack(Material.STICK);
        // ItemMeta meta = kbStick.getItemMeta();
        // meta.displayName(Component.text("Knockback Stick", NamedTextColor.RED));
        // kbStick.setItemMeta(meta);
        // kbStick.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.KNOCKBACK, 10);
        // p.getInventory().addItem(kbStick);
    }

    private static void endGameLives() {
        gameRunning = false;

        // Sort: most lives first, then earliest elimination time on tie
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(lives.entrySet());
        ranking.sort((a, b) -> {
            int livesDiff = b.getValue().compareTo(a.getValue());
            if (livesDiff != 0) return livesDiff;
            long timeA = eliminationTimestamps.getOrDefault(a.getKey(), Long.MAX_VALUE);
            long timeB = eliminationTimestamps.getOrDefault(b.getKey(), Long.MAX_VALUE);
            return Long.compare(timeA, timeB); // earlier elimination loses tie
        });

        Bukkit.broadcastMessage(ChatColor.GOLD + "§lBiome Parkour – Lives Mode – Game Over!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Final ranking (most lives survives):");

        int place = 1;
        for (var entry : ranking) {
            Player pl = Bukkit.getPlayer(entry.getKey());
            String name = (pl != null) ? pl.getName() : entry.getKey().toString().substring(0, 8) + "...";
            Bukkit.broadcastMessage(ChatColor.WHITE + "" + place + ". " + ChatColor.YELLOW + name
                    + ChatColor.GRAY + " - " + ChatColor.RED + entry.getValue() + " lives");
            place++;
        }

        cleanup();
    }

    private static void endGamePoints() {
        gameRunning = false;

        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(points.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Bukkit.broadcastMessage(ChatColor.GOLD + "§lBiome Parkour – Points Mode – Game Over!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Border speed reached " + POINTS_MODE_END_SPEED + " blocks/tick");

        int place = 1;
        for (var entry : ranking) {
            Player pl = Bukkit.getPlayer(entry.getKey());
            String name = (pl != null) ? pl.getName() : entry.getKey().toString().substring(0, 8) + "...";
            Bukkit.broadcastMessage(ChatColor.WHITE + "" + place + ". " + ChatColor.YELLOW + name
                    + ChatColor.GRAY + " - " + ChatColor.GREEN + String.format("%.1f", entry.getValue()) + " points");
            place++;
        }

        cleanup();
    }

    // Optional: call when player quits during game
    public static void onPlayerQuit(Player p) {
        if (!gameRunning) return;
        UUID uuid = p.getUniqueId();

        if (currentMode == Mode.LIVES) {
            lives.remove(uuid);
            eliminationTimestamps.remove(uuid);
        } else {
            points.remove(uuid);
            waterTicksIn.remove(uuid);
        }

        // Optional: check if game should end
        int alive = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() != GameMode.SPECTATOR) alive++;
        }
        if (alive <= 0 && currentMode == Mode.LIVES) {
            endGameLives();
        }
    }
}