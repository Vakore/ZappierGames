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

    // teamName -> buffer for deposits while viewing
    private static final Map<String, List<ItemStack>> depositBuffers = new HashMap<>();

    public static List<ItemStack> getTeamStorage(String teamName) {
        return teamStorages.computeIfAbsent(teamName, k -> new ArrayList<>());
    }

    public static void clearAll() {
        teamStorages.clear();
        viewingPlayer.clear();
        depositBuffers.clear();
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

        // General bundle handling
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

                ItemStack toDeposit = cursor.clone();

                if (viewingPlayer.containsKey(team)) {
                    List<ItemStack> buffer = depositBuffers.computeIfAbsent(team, k -> new ArrayList<>());
                    mergeIntoStorage(buffer, toDeposit);
                } else {
                    mergeIntoStorage(storage, toDeposit);
                }

                player.setItemOnCursor(null);
                player.sendMessage(Component.text("Item deposited into team inventory!", NamedTextColor.GREEN));
                return;
            }

            // Prevent nesting bundles
            if (isInfinibundle(cursor) && isInfinibundle(current)) {
                event.setCancelled(true);
                return;
            }
        }

        // Handle team inventory GUI
        Component titleComp = view.title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        if (!title.contains(" Team Inventory")) return;

        // Prevent moving the infinibundle while teamchest is open
        if (event.getClickedInventory() == view.getBottomInventory() && involvesBundle) {
            event.setCancelled(true);
            return;
        }

        // Allow normal interactions in player's inventory
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

        // === FIXED: Normal left/right click with cursor ===
        if (cursor == null || cursor.getType() == Material.AIR) {
            // Taking item from team inventory
            player.setItemOnCursor(current != null ? current.clone() : null);
            event.getInventory().setItem(slot, null);
        } else {
            // Placing item into team inventory
            if (current == null || current.getType() == Material.AIR) {
                // Empty slot: place entire cursor stack
                event.getInventory().setItem(slot, cursor.clone());
                player.setItemOnCursor(null);
            } else if (current.isSimilar(cursor)) {
                // Same type: add as much as possible
                int maxStack = current.getMaxStackSize();
                int total = current.getAmount() + cursor.getAmount();
                int toLeaveInSlot = Math.min(maxStack, total);
                int remainder = total - maxStack;

                current.setAmount(toLeaveInSlot);
                event.getInventory().setItem(slot, current);

                if (remainder > 0) {
                    ItemStack leftover = cursor.clone();
                    leftover.setAmount(remainder);
                    player.setItemOnCursor(leftover);
                } else {
                    player.setItemOnCursor(null);
                }
            } else {
                // Different types: swap
                player.setItemOnCursor(current.clone());
                event.getInventory().setItem(slot, cursor.clone());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Component titleComp = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);

        ItemStack cursor = event.getOldCursor();
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
        if (page > maxPage) page = maxPage; // Don't allow beyond last full page

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(team + " Team Inventory - Page " + (page + 1)));

        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, storage.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, storage.get(i).clone());
        }

        // Filler panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);

        for (int i = SLOTS_PER_PAGE; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Navigation arrows
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", NamedTextColor.GREEN));
            prev.setItemMeta(pm);
            inv.setItem(45, prev);
        }

        if (end == start + SLOTS_PER_PAGE || page < maxPage) {
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

        // Collect items from this page
        List<ItemStack> pageItems = new ArrayList<>();
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                pageItems.add(item.clone());
            }
        }

        // Remove old items in this page range
        int oldEnd = Math.min(start + SLOTS_PER_PAGE, storage.size());
        if (oldEnd > start) {
            storage.subList(start, oldEnd).clear();
        }

        // Insert updated page items
        storage.addAll(start, pageItems);

        // Add buffered deposits if any
        if (depositBuffers.containsKey(team)) {
            List<ItemStack> buffer = depositBuffers.remove(team);
            storage.addAll(buffer);
        }

        // Compress entire storage to merge stacks
        compressStorage(storage);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String team = getTeamName(player);
        if (viewingPlayer.getOrDefault(team, null) == player) {
            viewingPlayer.remove(team);
        }
    }

    // === Helper: merge item into storage (used on deposit) ===
    private void mergeIntoStorage(List<ItemStack> storage, ItemStack item) {
        for (ItemStack existing : storage) {
            if (existing.isSimilar(item)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space > 0) {
                    int add = Math.min(space, item.getAmount());
                    existing.setAmount(existing.getAmount() + add);
                    item.setAmount(item.getAmount() - add);
                    if (item.getAmount() <= 0) return;
                }
            }
        }
        // If any left, add new stack
        if (item.getAmount() > 0) {
            storage.add(item);
        }
    }

    // === Helper: compress entire storage list by merging similar items ===
    private void compressStorage(List<ItemStack> storage) {
        List<ItemStack> compressed = new ArrayList<>();
        for (ItemStack item : storage) {
            mergeIntoStorage(compressed, item.clone());
        }
        storage.clear();
        storage.addAll(compressed);
    }

    public static Set<String> getTeamStorageKeys() {
        return teamStorages.keySet();
    }
}