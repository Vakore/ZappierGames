package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player; // Not strictly needed here, but fine to keep
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent; // The official Bukkit Event
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.inventory.ItemStack;
import org.zappier.zappierGames.manhunt.Manhunt;

// Renamed the class to avoid conflict
public class PlayerSpawnListener implements Listener {
    private final JavaPlugin plugin;

    public PlayerSpawnListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        String playerTeam = getPlayerTeam(player, scoreboard);
        if (ZappierGames.gameMode <= 5 && ZappierGames.gameMode > 0) {
             Manhunt.giveKit(player);
        }

        if (ZappierGames.gameMode == 10) {
            World skybattleWorld = Bukkit.getWorld("skybattle_world");
            if (skybattleWorld != null) {
                event.setRespawnLocation(new Location(skybattleWorld, 0.5, 150, 0.5));
            }
        }
    }

    private String getPlayerTeam(Player player, Scoreboard scoreboard) {
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                return team.getName();
            }
        }
        return null;
    }
}