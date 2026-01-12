package org.zappier.zappierGames.biomeparkour;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.zappier.zappierGames.ZappierGames;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BiomeParkour {
    public static double borderX = 0;
    public static double borderZ = 0;
    public static int borderSize = 100;          // Configurable border size
    public static double baseBorderSpeed = 0.1;    // Starting speed
    public static double speedIncreaseRate = 0.005; // How much speed increases per tick
    public static double currentBorderSpeed = 0.1;
    public static int direction = 1;              // 1 = +Z, -1 = -Z, will also use X axis
    public static boolean useXAxis = false;       // false = Z axis, true = X axis
    public static int axisChangeIntervalMin = 400; // Min ticks (~20s) before possible axis/direction change
    public static int axisChangeIntervalMax = 800; // Max ticks (~40s)
    public static int ticksUntilAxisChange = 600;
    public static boolean respawnLosePoints = true; // GUI option: respawn but lose 100 points
    public static int maxGameMinutes = 30;         // Configurable game duration
    public static boolean gameRunning = false;
    public static Map<String, Double> playerScores = new HashMap<>();
    public static Map<UUID, Integer> playerLives = new HashMap<>();
    public static int startingLives = 3;

    private static final Random random = new Random();

    public static void start(int size, double centerX, double centerZ, double speed, double speedInc, boolean losePointsOnRespawn, int minutes) {
        Bukkit.broadcastMessage(ChatColor.GREEN + "Biome Parkour starting!");
        ZappierGames.gameMode = 30;
        ZappierGames.globalBossBar.setVisible(false);

        borderSize = size;
        borderX = centerX;
        borderZ = centerZ;
        baseBorderSpeed = speed;
        currentBorderSpeed = speed;
        speedIncreaseRate = speedInc;
        respawnLosePoints = losePointsOnRespawn;
        maxGameMinutes = minutes;

        playerScores.clear();
        playerLives.clear();

        gameRunning = true;
        useXAxis = false;
        direction = 1;
        scheduleNextAxisChange();

        ZappierGames.resetPlayers(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.clearActivePotionEffects();
            p.setCollidable(true);
            p.sendMessage(ChatColor.GREEN + "SURVIVE THE MOVING BORDER!");
            p.sendTitle(ChatColor.RED + "RUN!", ChatColor.YELLOW + "The border is coming...", 10, 70, 20);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExperienceLevelAndProgress(0);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

            p.getWorld().setTime(6000); // Eternal day for visibility
            p.getWorld().getWorldBorder().setSize(borderSize);
            p.getWorld().getWorldBorder().setCenter(borderX, borderZ);
            p.getWorld().setDifficulty(Difficulty.NORMAL);

            // Kit
            p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
            for (int i = 0; i < 9; i++) {
                p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET, 1));
            }
            p.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            p.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            p.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            p.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            for (int i = 0; i < 9; i++) {
                p.getInventory().addItem(new ItemStack(Material.OAK_LOG, 64));
            }

            playerLives.put(p.getUniqueId(), startingLives);
            playerScores.put(p.getName().toUpperCase(), 0.0);
        }
    }

    public static void run() {
        if (!gameRunning) return;

        // Gradually increase speed
        //currentBorderSpeed += speedIncreaseRate;//This clanker made this mega fast lol

        // Move border
        if (useXAxis) {
            borderX += direction * currentBorderSpeed;
        } else {
            borderZ += direction * currentBorderSpeed;
        }

        // Random axis/direction change
        ticksUntilAxisChange--;
        if (ticksUntilAxisChange <= 0) {
            changeAxisAndDirection();
            scheduleNextAxisChange();
        }

        int alivePlayers = 0;
        Player lastPlayer = null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getWorld().getWorldBorder().setCenter(borderX, borderZ);
            p.getWorld().getWorldBorder().setSize(borderSize);
            if (p.getGameMode() != GameMode.SURVIVAL || p.getHealth() <= 0.0) continue;

            alivePlayers++;
            lastPlayer = p;

            p.setFoodLevel(19);

            Location centerLoc = new Location(p.getWorld(), borderX, 100.0, borderZ);
            p.setRespawnLocation(centerLoc);

            String scoreKey = p.getName().toUpperCase();
            double score = playerScores.getOrDefault(scoreKey, 0.0) + 0.05;
            if (p.getLocation().getBlock().getType() == Material.WATER || p.getLocation().getBlock().getType() == Material.LAVA) {
                score -= 0.25;
            }
            playerScores.put(scoreKey, score);

            p.sendActionBar(ChatColor.GOLD + "Score: " + String.format("%.1f", score) +
                    ChatColor.WHITE + " | Lives: " + ChatColor.RED + playerLives.get(p.getUniqueId()));
        }

        // End game check
        if (alivePlayers <= 0) {
            gameRunning = false;
            if (alivePlayers == 1) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "=== " + lastPlayer.getName() + " wins Biome Parkour! ===");
                lastPlayer.playSound(lastPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                Bukkit.broadcastMessage(ChatColor.RED + "No players remaining! Game ended.");
            }
            currentBorderSpeed = 0;
        }
    }

    private static void changeAxisAndDirection() {
        useXAxis = !useXAxis; // Toggle axis
        if (random.nextBoolean()) {
            direction *= -1; // 50% chance to reverse direction on same axis
        }
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "The border has changed direction!");
    }

    private static void scheduleNextAxisChange() {
        ticksUntilAxisChange = axisChangeIntervalMin + random.nextInt(axisChangeIntervalMax - axisChangeIntervalMin + 1);
    }

    public static void stop() {
        gameRunning = false;
        currentBorderSpeed = baseBorderSpeed;
    }
}