// src/main/java/org/zappier/zappierGames/TrackerInventory.java
package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class TrackerInventory implements InventoryHolder {

    private static final int ROWS = 6;                 // 54 slots
    private static final int SLOTS_PER_PAGE = 45;      // 9×5 = 45 (leave bottom row for navigation)
    private final Inventory inv;
    private final List<Player> allPlayers;
    private final int page;
    private final Player viewer;

    public TrackerInventory(Player viewer, int page) {
        this.viewer = viewer;
        this.page = Math.max(page, 0);
        this.allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        this.allPlayers.remove(viewer);                // don't show self

        this.inv = Bukkit.createInventory(this, ROWS * 9,
                Component.text("Tracker – Page " + (page + 1)));

        fillPage();
    }

    private void fillPage() {
        int start = page * SLOTS_PER_PAGE;
        int end   = Math.min(start + SLOTS_PER_PAGE, allPlayers.size());

        // ---- player heads -------------------------------------------------
        for (int i = start; i < end; i++) {
            Player target = allPlayers.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            // dimension name (short)
            String dim = switch (target.getWorld().getEnvironment()) {
                case NORMAL -> "Overworld";
                case NETHER -> "Nether";
                case THE_END -> "End";
                default -> target.getWorld().getName();
            };

            meta.displayName(Component.text(target.getName(), NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Dimension: ", NamedTextColor.GRAY)
                    .append(Component.text(dim, NamedTextColor.AQUA)));
            lore.add(Component.text("Click to track", NamedTextColor.GREEN));
            meta.lore(lore);
            meta.setOwningPlayer(target);
            head.setItemMeta(meta);

            inv.setItem(i - start, head);
        }

        // ---- navigation --------------------------------------------------
        ItemStack filler = createFiller();
        for (int i = SLOTS_PER_PAGE; i < ROWS * 9; i++) {
            inv.setItem(i, filler);
        }

        // previous page
        if (page > 0) {
            inv.setItem(45, createNavItem(Material.ARROW, "Previous Page"));
        }
        // next page
        if (end < allPlayers.size()) {
            inv.setItem(53, createNavItem(Material.ARROW, "Next Page"));
        }
        // close
        inv.setItem(49, createNavItem(Material.BARRIER, "Close"));
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(m -> m.displayName(Component.empty()));
        return item;
    }

    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> m.displayName(Component.text(name, NamedTextColor.GOLD)));
        return item;
    }

    @Override public Inventory getInventory() { return inv; }

    // ---------- static opener ----------
    public static void open(Player player, int page) {
        player.openInventory(new TrackerInventory(player, page).getInventory());
    }
}