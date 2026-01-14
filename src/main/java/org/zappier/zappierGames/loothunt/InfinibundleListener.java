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

import java.util.*;

public class InfinibundleListener implements Listener {

    private static final int CUSTOM_MODEL_DATA = 900009;
    private static final int SLOTS_PER_PAGE = 45;

    private static final Map<String, List<ItemStack>> teamStorages = new HashMap<>();
    private static final Map<String, Player> viewingPlayer = new HashMap<>();
    private static final Map<String, List<ItemStack>> depositBuffers = new HashMap<>();

    // Tracks players currently jumping between pages to prevent double-saving
    private static final Set<UUID> switchingPages = new HashSet<>();

    public static List<ItemStack> getTeamStorage(String teamName) {
        return teamStorages.computeIfAbsent(teamName, k -> new ArrayList<>());
    }

    public static void clearAll() {
        teamStorages.clear();
        viewingPlayer.clear();
        depositBuffers.clear();
        switchingPages.clear();
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

        // Open to the last page with items
        List<ItemStack> storage = getTeamStorage(team);
        int lastOccupiedPage = Math.max(0, (storage.size() - 1) / SLOTS_PER_PAGE);
        openTeamInventory(player, team, lastOccupiedPage);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryView view = event.getView();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean involvesBundle = isInfinibundle(current) || isInfinibundle(cursor);

        // Deposit into bundle logic
        if (involvesBundle) {
            if (event.getClickedInventory() == null) {
                event.setCancelled(true);
                return;
            }
            if (cursor != null && cursor.getType() != Material.AIR && isInfinibundle(current)) {
                event.setCancelled(true);
                String team = getTeamName(player);
                if (viewingPlayer.containsKey(team)) {
                    mergeIntoStorage(depositBuffers.computeIfAbsent(team, k -> new ArrayList<>()), cursor.clone());
                } else {
                    mergeIntoStorage(getTeamStorage(team), cursor.clone());
                }
                player.setItemOnCursor(null);
                return;
            }
            if (isInfinibundle(cursor) && isInfinibundle(current)) {
                event.setCancelled(true);
                return;
            }
        }

        Component titleComp = view.title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        if (!title.contains(" Team Inventory")) return;

        if (event.getClickedInventory() == view.getBottomInventory() && involvesBundle) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() != view.getTopInventory()) return;
        event.setCancelled(true);

        int slot = event.getSlot();

        // === NAVIGATION LOGIC (NO FLICKER) ===
        if (slot == 45 || slot == 53) {
            if (current == null || current.getType() != Material.ARROW) return;

            String team = title.split(" Team Inventory")[0];
            int currentPage = Integer.parseInt(title.split("Page ")[1]) - 1;
            int newPage = slot == 45 ? currentPage - 1 : currentPage + 1;

            // 1. Save current page items manually
            savePage(player, event.getInventory(), title);

            // 2. Set flag to ignore the next InventoryCloseEvent
            switchingPages.add(player.getUniqueId());

            // 3. Open new page immediately (replaces current inventory)
            openTeamInventory(player, team, newPage);

            // 4. Clean up flag
            switchingPages.remove(player.getUniqueId());
            return;
        }

        if (slot >= SLOTS_PER_PAGE) return;

        // Item manipulation (Shift, Number keys, Clicks)
        handleItemInteractions(event, player, slot, current, cursor);
    }

    private void handleItemInteractions(InventoryClickEvent event, Player player, int slot, ItemStack current, ItemStack cursor) {
        if (event.isShiftClick()) {
            if (current == null || current.getType() == Material.AIR) return;
            ItemStack toMove = current.clone();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toMove);
            event.getInventory().setItem(slot, leftover.isEmpty() ? null : leftover.get(0));
        } else if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            player.getInventory().setItem(hotbarSlot, current != null ? current.clone() : null);
            event.getInventory().setItem(slot, hotbarItem != null ? hotbarItem.clone() : null);
        } else {
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.setItemOnCursor(current != null ? current.clone() : null);
                event.getInventory().setItem(slot, null);
            } else {
                if (current == null || current.getType() == Material.AIR) {
                    event.getInventory().setItem(slot, cursor.clone());
                    player.setItemOnCursor(null);
                } else if (current.isSimilar(cursor)) {
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
                    player.setItemOnCursor(current.clone());
                    event.getInventory().setItem(slot, cursor.clone());
                }
            }
        }
    }

    private void openTeamInventory(Player player, String team, int page) {
        List<ItemStack> storage = getTeamStorage(team);
        int maxPage = (storage.size() / SLOTS_PER_PAGE) + 1;

        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(team + " Team Inventory - Page " + (page + 1)));

        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, storage.size());

        if (start < storage.size()) {
            for (int i = start; i < end; i++) {
                inv.setItem(i - start, storage.get(i).clone());
            }
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = SLOTS_PER_PAGE; i < 54; i++) inv.setItem(i, filler);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", NamedTextColor.GREEN));
            prev.setItemMeta(pm);
            inv.setItem(45, prev);
        }

        if (page < maxPage) {
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
        if (switchingPages.contains(player.getUniqueId())) return; // Skip saving if just changing pages

        Component titleComp = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        if (!title.contains(" Team Inventory")) return;

        String team = title.split(" Team Inventory")[0];
        viewingPlayer.remove(team);

        savePage(player, event.getInventory(), title);
    }

    /**
     * Extracted logic to save items from the current inventory view into the team storage list.
     */
    private void savePage(Player player, Inventory inv, String title) {
        String team = title.split(" Team Inventory")[0];
        List<ItemStack> storage = getTeamStorage(team);

        int page = Integer.parseInt(title.split("Page ")[1]) - 1;
        int start = page * SLOTS_PER_PAGE;

        List<ItemStack> pageItems = new ArrayList<>();
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                pageItems.add(item.clone());
            }
        }

        int oldEnd = Math.min(start + SLOTS_PER_PAGE, storage.size());
        if (oldEnd > start) {
            storage.subList(start, oldEnd).clear();
        }

        if (start > storage.size()) {
            storage.addAll(pageItems);
        } else {
            storage.addAll(start, pageItems);
        }

        if (depositBuffers.containsKey(team)) {
            storage.addAll(depositBuffers.remove(team));
        }

        compressStorage(storage);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        switchingPages.remove(player.getUniqueId());
        String team = getTeamName(player);
        if (viewingPlayer.getOrDefault(team, null) == player) {
            viewingPlayer.remove(team);
        }
    }

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
        if (item.getAmount() > 0) storage.add(item);
    }

    private void compressStorage(List<ItemStack> storage) {
        List<ItemStack> compressed = new ArrayList<>();
        for (ItemStack item : storage) mergeIntoStorage(compressed, item.clone());
        storage.clear();
        storage.addAll(compressed);
    }
}