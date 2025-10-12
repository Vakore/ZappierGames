package org.zappier.zappierGames;

import org.bukkit.*;
import org.bukkit.entity.Player;

public class Skybattle {
    public static void start(World world, int borderSize) {
        // Set world border
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);

        // Create a simple 10x10 stone platform at y=100
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.getBlockAt(x, 100, z).setType(Material.STONE);
            }
        }

        // Teleport all players to the platform
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(new Location(world, 0.5, 101, 0.5));
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExperienceLevelAndProgress(0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            p.sendTitle(ChatColor.BLUE + "Skybattle", ChatColor.BLUE + "Fight to survive!", 10, 70, 20);
            p.sendMessage(ChatColor.GREEN + "Skybattle started! Survive on the platform!");
        }
    }
}