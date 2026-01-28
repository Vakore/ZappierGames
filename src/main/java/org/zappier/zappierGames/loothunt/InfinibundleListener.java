package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.ObjectUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InfinibundleListener implements Listener {

    private static final int CUSTOM_MODEL_DATA = 900009;
    private static final int SLOTS_PER_PAGE = 45;

    public static final Map<String, List<ItemStack>> teamStorages = new HashMap<>();
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


        if (event.getPlayer().isSneaking()) {
            int pInfData = LootHunt.bundleSlots.getOrDefault(player.getName().toUpperCase(), 0);
            if (pInfData == 0) {
                LootHunt.bundleSlots.put(player.getName().toUpperCase(), 0b111);
                pInfData = 0b111;
            }
            pInfData++;
            pInfData = pInfData & 0b111;
            if (pInfData == 0) {
                pInfData = 0b001;
            }
            LootHunt.bundleSlots.put(player.getName().toUpperCase(), pInfData);
            //□■
            String shiftL[] = {"□", "□", "□"};
            if ((pInfData & 0b001) > 0) {shiftL[0] = "■";}
            if ((pInfData & 0b010) > 0) {shiftL[1] = "■";}
            if ((pInfData & 0b100) > 0) {shiftL[2] = "■";}
            player.sendActionBar(ChatColor.GREEN + "SHIFT-L Slots: " + shiftL[0] + shiftL[1] + shiftL[2]);
            return;
        }

        if (viewingPlayer.containsKey(team)) {
            player.sendMessage(Component.text("Team inventory is already in use!", NamedTextColor.RED));
            return;
        }

        // Open to the last page with items
        List<ItemStack> storage = getTeamStorage(team);
        int lastOccupiedPage = Math.max(0, (storage.size() - 1) / SLOTS_PER_PAGE);

        int lastPageToGo = LootHunt.lastPages.getOrDefault(player.getName().toLowerCase(), -1);
        if (lastPageToGo == -1 || lastPageToGo > lastOccupiedPage) {
            lastPageToGo = lastOccupiedPage;
            LootHunt.lastPages.put(player.getName().toLowerCase(), lastPageToGo);
        }
        openTeamInventory(player, team, lastPageToGo, false);
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
        if (!title.contains(" Team Inventory") && !title.contains(" Team Collections")) return;

        if (event.getClickedInventory() == view.getBottomInventory() && involvesBundle) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() != view.getTopInventory()) return;
        event.setCancelled(true);

        int slot = event.getSlot();

        // === NAVIGATION AND SWITCH LOGIC (NO FLICKER) ===
        if (slot == 45 || slot == 53 || slot == 49) {
            if (current == null) return;
            if (slot == 45 && current.getType() != Material.ARROW) return;
            if (slot == 53 && current.getType() != Material.ARROW) return;
            if (slot == 49 && current.getType() != Material.BARRIER) return;

            String team = title.split(" Team ")[0];
            int currentPage = Integer.parseInt(title.split("Page ")[1]) - 1;
            boolean isCollections = title.contains("Collections");

            // 1. Save current page items manually if in storage mode
            if (!isCollections) {
                savePage(player, event.getInventory(), title);
            }

            // 2. Set flag to ignore the next InventoryCloseEvent
            switchingPages.add(player.getUniqueId());

            // 3. Determine new page and mode
            int newPage;
            boolean newIsCollections;
            if (slot == 49) {
                newPage = 0;
                newIsCollections = !isCollections;
            } else {
                newPage = slot == 45 ? currentPage - 1 : currentPage + 1;
                newIsCollections = isCollections;
            }

            // 4. Open new page immediately (replaces current inventory)
            openTeamInventory(player, team, newPage, newIsCollections);

            // 5. Clean up flag
            switchingPages.remove(player.getUniqueId());
            return;
        }

        if (title.contains("Collections")) return; // No item interactions in collections view

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

    private void openTeamInventory(Player player, String team, int page, boolean isCollections) {
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(team + " Team " + (isCollections ? "Collections" : "Inventory") + " - Page " + (page + 1)));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = SLOTS_PER_PAGE; i < 54; i++) inv.setItem(i, filler);

        int maxPage = 0;
        List<ItemStack> displayItems = null; // only used in collections mode

        if (isCollections) {
            // Compute collected items
            Set<String> collected = getTeamCollectedItems(team);

            // Build display items
            displayItems = new ArrayList<>();
            for (LootHunt.Collection coll : LootHunt.collections.values()) {
                int startOfColl = displayItems.size();

                // Header
                ItemStack header = new ItemStack(Material.WRITABLE_BOOK);
                ItemMeta hm = header.getItemMeta();
                hm.displayName(Component.text(coll.name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                long collectedCount = coll.itemGroups.stream()
                        .filter(group -> group.stream().anyMatch(collected::contains))
                        .count();
                hm.lore(List.of(
                        Component.text("Type: " + capitalize(coll.type), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Progress: " + collectedCount + "/" + coll.itemGroups.size(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                ));
                header.setItemMeta(hm);
                displayItems.add(header);

                // Collect item displays
                List<ItemStack> collItems = new ArrayList<>();
                for (List<String> group : coll.itemGroups) {
                    String repId = group.get(0);
                    Material repMat = Material.getMaterial(repId);
                    boolean isPotionKey = false;

                    if (repMat == null) {
                        Pattern potionPattern = Pattern.compile("^(SPLASH_|LINGERING_)?(.+)$");
                        Matcher m = potionPattern.matcher(repId);
                        if (m.matches()) {
                            String prefix = m.group(1) != null ? m.group(1) : "";
                            String typeStr = m.group(2);
                            try {
                                PotionType.valueOf(typeStr);
                                repMat = prefix.startsWith("SPLASH") ? Material.SPLASH_POTION :
                                        prefix.startsWith("LING") ? Material.LINGERING_POTION : Material.POTION;
                                isPotionKey = true;
                            } catch (IllegalArgumentException ignored) {}
                        }
                        if (repMat == null) repMat = Material.BARRIER;
                    }

                    boolean has = group.stream().anyMatch(collected::contains);

                    ItemStack disp;
                    String dispName = capitalize(repId.replace("_", " ").toLowerCase());
                    if (group.size() > 1) dispName += " (variants)";

                    if (has) {
                        disp = new ItemStack(Material.BARRIER);
                        ItemMeta dm = disp.getItemMeta();
                        dm.displayName(Component.text(dispName, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                        dm.lore(List.of(Component.text("COLLECTED", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
                        disp.setItemMeta(dm);
                    } else {
                        disp = new ItemStack(repMat, 1);
                        if (isPotionKey && disp.getType().name().contains("POTION")) {
                            PotionMeta pm = (PotionMeta) disp.getItemMeta();
                            String typeStr = repId.replaceFirst("^(SPLASH_|LINGERING_)", "");
                            PotionType pt = PotionType.valueOf(typeStr);
                            pm.setBasePotionType(pt);
                            disp.setItemMeta(pm);
                        }
                        ItemMeta dm = disp.getItemMeta();
                        dm.displayName(Component.text(dispName, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("Not Collected", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                        if (group.size() > 1) {
                            lore.add(Component.text("Any of: " + String.join(", ", group), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                        }
                        dm.lore(lore);
                        disp.setItemMeta(dm);
                    }
                    collItems.add(disp);
                }

                // Add collItems in groups of 8, with null placeholder for col0 in subsequent "rows"
                for (int g = 0; g < collItems.size(); g += 8) {
                    if (g > 0) {
                        displayItems.add(null); // Placeholder for reserved first column (empty slot)
                    }
                    int end = Math.min(g + 8, collItems.size());
                    for (int j = g; j < end; j++) {
                        displayItems.add(collItems.get(j));
                    }
                }

                // Pad to next row for next collection
                int currentSize = displayItems.size();
                int remainder = currentSize % 9;
                if (remainder != 0) {
                    int toAdd = 9 - remainder;
                    for (int f = 0; f < toAdd; f++) {
                        displayItems.add(null);
                    }
                }
            }

            maxPage = displayItems.isEmpty() ? 0 : (displayItems.size() - 1) / SLOTS_PER_PAGE;

        } else {
            List<ItemStack> storage = getTeamStorage(team);
            maxPage = storage.isEmpty() ? 0 : (storage.size() / SLOTS_PER_PAGE) + 1;
        }

        // Clamp page
        if (page > maxPage) page = maxPage;

        // Fill items
        if (isCollections && displayItems != null) {
            int start = page * SLOTS_PER_PAGE;
            int end = Math.min(start + SLOTS_PER_PAGE, displayItems.size());
            for (int i = start; i < end; i++) {
                ItemStack dispItem = displayItems.get(i);
                if (dispItem != null) {
                    inv.setItem(i - start, dispItem.clone());
                }
            }
        } else if (!isCollections) {
            List<ItemStack> storage = getTeamStorage(team);
            int start = page * SLOTS_PER_PAGE;
            int end = Math.min(start + SLOTS_PER_PAGE, storage.size());
            if (start < storage.size()) {
                for (int i = start; i < end; i++) {
                    inv.setItem(i - start, storage.get(i).clone());
                }
            }
        }

        // Navigation buttons
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", NamedTextColor.GREEN));
            prev.setItemMeta(pm);
            inv.setItem(45, prev);
        }

        // Next button: use the pre-calculated maxPage
        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.displayName(Component.text("Next Page", NamedTextColor.GREEN));
            next.setItemMeta(nm);
            inv.setItem(53, next);
        }

        // Switch view button
        ItemStack switchView = new ItemStack(Material.BARRIER);
        ItemMeta sm = switchView.getItemMeta();
        sm.displayName(Component.text("Switch to " + (isCollections ? "Storage" : "Collections"), NamedTextColor.GREEN));
        switchView.setItemMeta(sm);
        inv.setItem(49, switchView);

        player.openInventory(inv);
        viewingPlayer.put(team, player);
    }

    private Set<String> getTeamCollectedItems(String team) {
        Set<String> collected = new HashSet<>();

        // Process team storage
        processContainerForIds(collected, getTeamStorage(team));

        // Process team players' inventories
        org.bukkit.scoreboard.Team sbTeam = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(team);
        if (sbTeam != null) {
            for (String entry : sbTeam.getEntries()) {
                Player p = Bukkit.getPlayer(entry);
                if (p != null && p.isOnline()) {
                    processContainerForIds(collected, Arrays.asList(p.getInventory().getContents()));
                }
            }
        } else {
            // Solo player
            String playerName = team.replace("(Solo) ", "");
            Player p = Bukkit.getPlayer(playerName);
            if (p != null && p.isOnline()) {
                processContainerForIds(collected, Arrays.asList(p.getInventory().getContents()));
            }
        }

        return collected;
    }

    private void processContainerForIds(Set<String> ids, Iterable<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;

            String itemId = item.getType().toString();

            // Handle potions
            if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta potionMeta) {
                    PotionType pt = potionMeta.getBasePotionType();
                    String prefix = "";
                    if (item.getType() == Material.SPLASH_POTION) prefix = "SPLASH_";
                    else if (item.getType() == Material.LINGERING_POTION) prefix = "LINGERING_";
                    itemId = prefix + (pt != null ? pt.name() : "WATER");
                }
            }

            ids.add(itemId);

            // Recurse into shulker boxes
            if (item.getType().name().endsWith("_SHULKER_BOX")) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
                    BlockState bs = bsm.getBlockState();
                    if (bs instanceof ShulkerBox shulker) {
                        processContainerForIds(ids, Arrays.asList(shulker.getInventory().getContents()));
                    }
                }
            }

            // Recurse into bundles
            if (item.getType() == Material.BUNDLE) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    processContainerForIds(ids, bundleMeta.getItems());
                }
            }
        }
    }

    private String capitalize(String str) {
        return Arrays.stream(str.split(" "))
                .map(word -> word.isEmpty() ? "" : word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (switchingPages.contains(player.getUniqueId())) return; // Skip saving if just changing pages

        Component titleComp = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        if (!title.contains(" Team Inventory") && !title.contains(" Team Collections")) return;

        String team = title.split(" Team ")[0];
        viewingPlayer.remove(team);

        if (title.contains("Collections")) return; // No saving for collections

        savePage(player, event.getInventory(), title);
    }

    /**
     * Extracted logic to save items from the current inventory view into the team storage list.
     */
    private void savePage(Player player, Inventory inv, String title) {
        String team = title.split(" Team ")[0];
        List<ItemStack> storage = getTeamStorage(team);

        int page = Integer.parseInt(title.split("Page ")[1]) - 1;
        LootHunt.lastPages.put(player.getName().toLowerCase(), page);
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



    @EventHandler
    public void onQuickDepositShiftLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;

        ItemStack held = event.getItem();
        if (held == null || !isInfinibundle(held)) return;

        event.setCancelled(true);

        Player p = event.getPlayer();
        String team = getTeamName(p);

        if (viewingPlayer.containsKey(team) && viewingPlayer.get(team) != p) {
            p.sendMessage(Component.text("Team storage is currently in use by another player!", NamedTextColor.RED));
            return;
        }

        // ──────────────────────────────────────────────
        // Decide target — same logic as normal deposit (cursor → bundle)
        // ──────────────────────────────────────────────
        final List<ItemStack> target;
        final boolean usingBuffer = viewingPlayer.containsKey(team);

        if (usingBuffer) {
            target = depositBuffers.computeIfAbsent(team, k -> new ArrayList<>());
        } else {
            target = getTeamStorage(team);
        }

        int count = 0;

        int pInfData = LootHunt.bundleSlots.getOrDefault(p.getName().toUpperCase(), 0);
        if (pInfData == 0) {
            LootHunt.bundleSlots.put(p.getName().toUpperCase(), 0b111);
            pInfData = 0b111;
        }
        for (int i = 9; i <= 35; i++) {
            if ((pInfData & (1 << (int)((i / 9) - 1))) == 0) {
                continue;
            }
            ItemStack item = p.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;

            mergeIntoStorage(target, item.clone());
            p.getInventory().setItem(i, null);
            count++;
        }

        if (count == 0) {
            p.sendActionBar(Component.text("Nothing to deposit", NamedTextColor.GRAY));
            return;
        }

        // Only compress when it makes sense
        compressStorage(target);

        // Feedback
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.9f, 1.15f);
        p.sendActionBar(Component.text()
                .append(Component.text("Deposited ", NamedTextColor.GREEN))
                .append(Component.text(count, NamedTextColor.YELLOW))
                .append(Component.text(" stacks → Team Storage", NamedTextColor.GREEN)));

        // If someone is viewing (including possibly self), they will see update on next page change / reopen
        // But when no one views → change is immediately visible on next open (which is what you want)
    }
}