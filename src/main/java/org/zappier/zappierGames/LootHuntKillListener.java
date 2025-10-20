package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class LootHuntKillListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        if (killed == null) {
            return;
        }
        Player killer = killed.getKiller();
        if (killer == null || !(killer instanceof Player)) {
            return;
        }

        String killedTeam = killed.getScoreboard().getEntryTeam(killed.getName()) != null
                ? killed.getScoreboard().getEntryTeam(killed.getName()).getName()
                : null;
        String killerTeam = killer.getScoreboard().getEntryTeam(killer.getName()) != null
                ? killer.getScoreboard().getEntryTeam(killer.getName()).getName()
                : null;

        if (killedTeam != null && killerTeam != null && killedTeam.equals(killerTeam)) {
            // No reward for killing a teammate
            // Bukkit.broadcastMessage("Give this man no points");
        } else {
            // Give points to killer
            LootHunt.playerKillCounts.put(killer.getName().toUpperCase(),
                    LootHunt.playerKillCounts.getOrDefault(killer.getName().toUpperCase(), 0) + 1);
            // Bukkit.broadcastMessage("Give this man points");
            // killer.setNoDamageTicks(0);
        }
    }
}