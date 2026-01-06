package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.zappier.zappierGames.ZappierGames;

import java.util.*;

public class InfinibundleListener implements Listener {

    private static final int CUSTOM_MODEL_DATA = 900009;
    private static final int SLOTS_PER_PAGE = 45; // 5 rows x 9 columns

    // teamName -> list of all items in team storage (unlimited)
    private static final Map<String, List<ItemStack>> teamStorages = new HashMap<>();

    // teamName -> player currently viewing it (to prevent concurrent access)
    private static final Map<String, Player> viewingPlayer = new HashMap<>();

    public static List<ItemStack> getTeamStorage(String teamName) {
        return teamStorages.computeIfAbsent(teamName, k -> new ArrayList<>());
    }

    public static void clearAll() {
        teamStorages.clear();
        viewingPlayer.clear();
    }

    private boolean isInfinibundle(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA;
    }

    private String getTeamName(Player player) {
        return player.getScoreboard().getEntryTeam(player.getName()) != null
                ? player.getScoreboard().getEntryTeam(player.getName()).getName()
                : "(Solo) " + player.getName();
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!event.hasItem() || !event.getAction().toString().contains("RIGHT_CLICK")) return;

        ItemStack hand = event.getItem();
        if (!isInfinibundle(hand)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        String team = getTeamName(player);

        if (viewingPlayer.containsKey(team)) {
            player.sendMessage(Component.text("Team inventory is already in use!", NamedTextColor.RED));
            return;
        }

        openTeamInventory(player, team, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryView view = event.getView();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean involvesBundle = isInfinibundle(current) || isInfinibundle(cursor);

        // General bundle handling (deposit, prevent nesting, etc.)
        if (involvesBundle) {
            if (event.getClickedInventory() == null) {
                event.setCancelled(true);
                return;
            }

            // Deposit: clicking on bundle with item on cursor
            if (cursor != null && cursor.getType() != Material.AIR && isInfinibundle(current)) {
                event.setCancelled(true);
                String team = getTeamName(player);
                List<ItemStack> storage = getTeamStorage(team);

                storage.add(cursor.clone());
                player.setItemOnCursor(null);
                player.sendMessage(Component.text("Item deposited into team inventory!", NamedTextColor.GREEN));
                return;
            }

            // Prevent putting bundle inside another bundle
            if (isInfinibundle(cursor) && isInfinibundle(current)) {
                event.setCancelled(true);
                return;
            }

            // Other bundle movements (including number key and shift) are allowed here
        }

        // Handle team inventory GUI
        Component titleComp = view.title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        if (!title.contains(" Team Inventory")) return;

        // Issue 6: Prevent moving the infinibundle while teamchest is open
        if (event.getClickedInventory() == view.getBottomInventory() && involvesBundle) {
            event.setCancelled(true);
            return;
        }

        // Allow normal interactions in player's inventory (bottom)
        if (event.getClickedInventory() != view.getTopInventory()) return;

        // Clicked in top inventory: fully control
        event.setCancelled(true);

        int slot = event.getSlot();

        // Navigation arrows
        if (slot == 45 || slot == 53) {
            if (current == null || current.getType() != Material.ARROW) return;

            String team = title.split(" Team Inventory")[0];
            int currentPage = Integer.parseInt(title.split("Page ")[1]) - 1;
            int newPage = slot == 45 ? currentPage - 1 : currentPage + 1;

            player.closeInventory();
            new BukkitRunnable() {
                @Override
                public void run() {
                    openTeamInventory(player, team, newPage);
                }
            }.runTaskLater(ZappierGames.getInstance(), 1L);
            return;
        }

        // Prevent interaction with filler panes or control row
        if (slot >= SLOTS_PER_PAGE) return;

        // Handle shift-click: move to player inventory
        if (event.isShiftClick()) {
            if (current == null || current.getType() == Material.AIR) return;

            ItemStack toMove = current.clone();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toMove);
            if (leftover.isEmpty()) {
                event.getInventory().setItem(slot, null);
            } else {
                event.getInventory().setItem(slot, leftover.get(0));
            }
            return;
        }

        // Handle number key: swap with hotbar
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            ItemStack slotItem = event.getInventory().getItem(slot);

            player.getInventory().setItem(hotbarSlot, slotItem != null ? slotItem.clone() : null);
            event.getInventory().setItem(slot, hotbarItem != null ? hotbarItem.clone() : null);
            return;
        }

        // Normal click: swap with cursor
        if (cursor.getType() == Material.AIR) {
            // Taking item
            player.setItemOnCursor(current != null ? current.clone() : null);
            event.getInventory().setItem(slot, null);
        } else {
            // Placing item
            event.getInventory().setItem(slot, cursor.clone());
            player.setItemOnCursor(null);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Component titleComp = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);

        ItemStack cursor = event.getCursor();
        if (isInfinibundle(cursor)) {
            event.setCancelled(true);
            return;
        }

        for (int raw : event.getRawSlots()) {
            ItemStack item = event.getView().getItem(raw);
            if (isInfinibundle(item)) {
                event.setCancelled(true);
                return;
            }
        }

        if (!title.contains(" Team Inventory")) return;

        // In team inventory: prevent drag on control row
        for (int raw : event.getRawSlots()) {
            if (raw < event.getView().getTopInventory().getSize() && raw >= SLOTS_PER_PAGE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void openTeamInventory(Player player, String team, int page) {
        List<ItemStack> storage = getTeamStorage(team);
        int maxPage = Math.max(0, (storage.size() - 1) / SLOTS_PER_PAGE);

        if (page < 0) page = 0;
        if (page > maxPage + 1) page = maxPage + 1;

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(team + " Team Inventory - Page " + (page + 1)));

        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, storage.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, storage.get(i).clone());
        }

        // Fill control row with gray panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);

        for (int i = SLOTS_PER_PAGE; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Navigation arrows (overwrite fillers)
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", NamedTextColor.GREEN));
            prev.setItemMeta(pm);
            inv.setItem(45, prev);
        }

        boolean showNext = page < maxPage || (end - start == SLOTS_PER_PAGE);
        if (showNext) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.displayName(Component.text("Next Page", NamedTextColor.GREEN));
            next.setItemMeta(nm);
            inv.setItem(53, next);
        }

        player.openInventory(inv);
        viewingPlayer.put(team, player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Component titleComp = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        if (!title.contains(" Team Inventory")) return;

        String team = title.split(" Team Inventory")[0];
        if (viewingPlayer.getOrDefault(team, null) == player) {
            viewingPlayer.remove(team);
        }

        Inventory inv = event.getInventory();
        List<ItemStack> storage = getTeamStorage(team);

        int page = Integer.parseInt(title.split("Page ")[1]) - 1;
        int start = page * SLOTS_PER_PAGE;

        // Collect items from content slots (0-44)
        List<ItemStack> newPageItems = new ArrayList<>();
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                newPageItems.add(item.clone());
            }
        }

        // Remove old page range
        int oldEnd = Math.min(start + SLOTS_PER_PAGE, storage.size());
        if (oldEnd > start) {
            storage.subList(start, oldEnd).clear();
        }

        // Insert new items
        storage.addAll(start, newPageItems);

        // Remove nulls
        storage.removeIf(Objects::isNull);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String team = getTeamName(player);
        if (viewingPlayer.getOrDefault(team, null) == player) {
            viewingPlayer.remove(team);
        }
    }

    public static Set<String> getTeamStorageKeys() {
        return teamStorages.keySet();
    }
}