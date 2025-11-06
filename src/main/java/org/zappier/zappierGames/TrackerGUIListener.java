// src/main/java/org/zappier/zappierGames/TrackerGUIListener.java
package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import net.kyori.adventure.text.Component;

public class TrackerGUIListener implements Listener {

    private final ZappierGames plugin;

    public TrackerGUIListener(ZappierGames plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof TrackerInventory)) return;
        if (e.getClickedInventory() == null) return; // clicking outside inventory
        if (e.getClickedInventory().getHolder() == null) return;
        if (!(e.getClickedInventory().getHolder() instanceof TrackerInventory)) {
            e.setCancelled(true); // prevent taking items from GUI
            return;
        }


        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = e.getRawSlot();

        // ---- navigation -------------------------------------------------
        if (slot == 45) { // previous
            int currentPage = getCurrentPage(e.getView().title());
            if (currentPage > 1) {
                TrackerInventory.open(p, currentPage - 2); // page is 0-indexed
            }
            return;
        }
        if (slot == 53) { // next
            int currentPage = getCurrentPage(e.getView().title());
            TrackerInventory.open(p, currentPage); // next page number
            return;
        }
        if (slot == 49) { // close
            p.closeInventory();
            return;
        }

        // ---- player head (slots 0-44) -----------------------------------
        if (slot >= 0 && slot < 45 && clicked.getType() == org.bukkit.Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta == null) return;

            PlayerProfile profile = meta.getOwnerProfile();
            if (profile == null) return;

            // Get player by exact name from profile
            Player target = Bukkit.getPlayerExact(profile.getName());
            if (target == null || !target.isOnline()) {
                p.sendMessage(ChatColor.RED + "That player is no longer online.");
                return;
            }

            // Set tracking (same as /trackplayer command)
            plugin.trackingPairs.put(p.getName(), target.getName());
            p.sendMessage(ChatColor.GREEN + "Now tracking " + target.getName() + "!");

            if (ZappierGames.shoutHunterTarget > 0) {
                Bukkit.broadcastMessage(ChatColor.RED + p.getName() + " is tracking " + target.getName() + "!");
            }

            p.closeInventory();
        }
    }

    private int getCurrentPage(Component title) {
        String titleStr = title.toString();
        // Extract page number from "Tracker – Page X"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Page\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(titleStr);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1)) - 1; // Convert to 0-indexed
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}