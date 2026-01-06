package org.zappier.zappierGames.manhunt;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.zappier.zappierGames.ZappierGames;

public class Manhunt {
    public static void start(int borderSize, int centerX, int centerZ) {
        if (ZappierGames.gameMode < 1 || ZappierGames.gameMode > 10) {
            ZappierGames.gameMode = 1;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode unset, defaulting to standard.");
        }

        ZappierGames.globalBossBar.setVisible(false);

        for (World world : Bukkit.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            if (world.isBedWorks()) {
                border.setCenter(centerX, centerZ);
                border.setSize(borderSize);
            } else {
                border.setCenter(0, 0);
                border.setSize(30000000);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.clearActivePotionEffects();
            p.setCollidable(true);

            switch (ZappierGames.gameMode) {
                case 1:
                    p.sendTitle(ChatColor.RED + "Manhunt", ChatColor.RED + "Beat the game, or die trying.");
                    break;
                case 2:
                    p.sendTitle(ChatColor.GREEN + "Manhunt", ChatColor.GREEN + "Beat the game, don't get infected!");
                    break;
                case 3:
                    p.sendTitle(ChatColor.BLUE + "Manhunt", ChatColor.BLUE + "Protect the president!");
                    break;
            }

            p.sendActionBar(ChatColor.GREEN + "Use /gc to get a tracker, and /trp <player> to select target.");
            p.sendMessage(ChatColor.GREEN + "Use /gc to get a tracker, and /trp <player> to select target.");
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExperienceLevelAndProgress(0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            p.getWorld().setTime(0);
        }
    }
}
