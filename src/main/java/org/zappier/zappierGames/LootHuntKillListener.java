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
        if (killed == null) {return;}
        Player killer = killed.getKiller();
        if (killer == null) {return;}

        if (!(killed.getKiller() instanceof Player)) {} //return;

        /*Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String killedTeam = scoreboard.getEntryTeam(killed.getName()).getName();
        Team killerTeam = scoreboard.getEntryTeam(killer.getName()).getName();*/
        String killedTeam = LootHunt.playerTeams.getOrDefault(killed.getName().toUpperCase(), "_");
        String killerTeam = LootHunt.playerTeams.getOrDefault(killer.getName().toUpperCase(), "-");

        if (killedTeam != null && killerTeam != null && killedTeam.equals(killerTeam)) {
            //no reward
            //Bukkit.broadcastMessage("Give this man no points");
        } else {
            //give points to killer
            LootHunt.playerKillCounts.put(killer.getName().toUpperCase(), LootHunt.playerKillCounts.getOrDefault(killer.getName().toUpperCase(), 0) + 1);
            //Bukkit.broadcastMessage("Give this man points");
            //killer.setNoDamageTicks(0);
        }
    }
}
