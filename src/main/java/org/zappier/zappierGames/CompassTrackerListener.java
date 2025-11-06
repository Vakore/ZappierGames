// src/main/java/org/zappier/zappierGames/CompassTrackerListener.java
package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.ChatColor;

public class CompassTrackerListener implements Listener {

    private final ZappierGames plugin;

    public CompassTrackerListener(ZappierGames plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onCompassClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;

        // Must be the cursed tracker compass
        if (!item.getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.VANISHING_CURSE)) return;

        e.setCancelled(true);                // prevent normal lodestone behaviour
        TrackerInventory.open(p, 0);        // open first page
    }
}