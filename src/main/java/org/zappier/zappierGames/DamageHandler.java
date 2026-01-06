//This file was made almost entirely by ChatGPT
package org.zappier.zappierGames;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.UUID;

public class DamageHandler implements Listener {

    private final HashSet<UUID> keepItemsPlayers = new HashSet<>(); // Tracks players who keep items on "death"

    // Toggle keep items for a player
    public void toggleKeepItems(Player player) {
        UUID playerId = player.getUniqueId();
        if (keepItemsPlayers.contains(playerId)) {
            keepItemsPlayers.remove(playerId);
            player.sendMessage("Item dropping on 'death' is now ENABLED.");
        } else {
            keepItemsPlayers.add(playerId);
            player.sendMessage("Item dropping on 'death' is now DISABLED.");
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

    private boolean areOnSameTeam(Player aTeam, Player bTeam) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String a = getPlayerTeam(aTeam, scoreboard);
        String b = getPlayerTeam(bTeam, scoreboard);
        if (a.equals("Runner_Suppliers")) {a = "Runners";}
        if (a.equals("Hunter_Suppliers")) {a = "Hunters";}
        if (a.equals("Bodyguard")) {a = "Runners";}
        if (a.equals("President")) {a = "Runners";}
        if (b.equals("Runner_Suppliers")) {b = "Runners";}
        if (b.equals("Hunter_Suppliers")) {b = "Hunters";}
        if (b.equals("Bodyguard")) {b = "Runners";}
        if (b.equals("President")) {b = "Runners";}

        if (a.equals(b)) {
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        //Friendly fire
        if (event instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();

            Player attacker = null;

            // Direct melee attack (ENTITY_ATTACK or ENTITY_SWEEP_ATTACK)
            if (damager instanceof Player) {
                attacker = (Player) damager;
            }
            // Projectile attack (arrows, tridents, etc.)
            else if (damager instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof Player shooter) {
                    attacker = shooter;
                }
            }

            if (attacker != null && attacker != player) { // player is the victim from earlier
                if (areOnSameTeam(player, attacker)) {
                    // Cancel melee or arrow damage between teammates
                    // Explosions (ENTITY_EXPLOSION, BLOCK_EXPLOSION) and other causes remain allowed
                    event.setCancelled(true);
                    // Optional: still apply knockback for melee (Minecraft does it automatically if not cancelled too early)
                    // For projectiles, knockback is handled by the projectile itself
                }
            }
        }

        if (ZappierGames.gameMode == 30) { // Biome Parkour
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                    event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                if (event.getDamageSource().getCausingEntity() instanceof Player) {
                    event.setDamage(0); // No damage
                    // Knockback is applied automatically by Minecraft
                }
            }
        }

        if (ZappierGames.gameMode != 10) {return;}
        if (event.getDamageSource().getCausingEntity() instanceof Creeper creeper) {
            //Bukkit.broadcastMessage("Creeper! " + event.getEntity().getName() + ", " + event.getDamageSource().getCausingEntity().getName());
            Player attacker = Bukkit.getPlayer(event.getDamageSource().getCausingEntity().getName());
            if (attacker != null && attacker.isOnline()) {
                event.setCancelled(true);
                ((Player)(event.getEntity())).damage(event.getDamage(), attacker);
            }
        }

    }
}
