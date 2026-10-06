package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.zappier.zappierGames.ZappierGames;
import org.zappier.zappierGames.lootrun.Lootrun;
import org.zappier.zappierGames.lootrun.LootrunShop;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InfinibundleListener implements Listener {

    private static InfinibundleListener instance;

    public InfinibundleListener() {
        instance = this;
    }

    /** Force-closes anyone's open Infinibundle/Collections/Quests/Shop GUI right now. Used when
     * LootHunt is about to end - this routes through the normal InventoryCloseEvent handling, so
     * whatever a player had sitting in an open Inventory tab (as the primary viewer) actually
     * gets saved via savePage() instead of just vanishing when the game ends out from under them. */
    public static void forceCloseAllOpenGuis() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView view = p.getOpenInventory();
            if (view.getTopInventory().getSize() != 54) continue;
            String title = PlainTextComponentSerializer.plainText().serialize(view.title());
            if (titleIsMode(title, "Inventory") || titleIsMode(title, "Collections")
                    || titleIsMode(title, "Quests") || titleIsMode(title, "Shop")) {
                p.closeInventory();
            }
        }
    }

    /** Used by /shop to jump straight to the Shop tab without going through the Infinibundle item. */
    public static void openShopDirect(Player player) {
        if (instance == null) return;
        instance.openTeamInventory(player, 0, ViewMode.SHOP);
    }

    private enum ViewMode {
        INVENTORY("Inventory"), COLLECTIONS("Collections"), QUESTS("Quests"), SHOP("Shop");

        final String label;
        ViewMode(String label) { this.label = label; }

        static ViewMode fromTitle(String title) {
            if (title.contains("Collections")) return COLLECTIONS;
            if (title.contains("Quests")) return QUESTS;
            if (title.contains("Shop")) return SHOP;
            return INVENTORY;
        }
    }

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

    // ===== Priority ordering (infinibundle-priority-order in config.yml) =====
    //
    // NOT a "give these items" feature - this sorts whatever's ALREADY in the infinibundle so
    // strategic items (tools, food, buckets, etc.) float to the front/earliest pages, instead of
    // needing to page through everything to find them. Items matching an earlier group in the
    // config list sort before items matching a later one; items matching no group keep their
    // existing relative order after all of the matched ones (stable sort).

    private static boolean priorityOrderEnabled = false;
    private static final List<PriorityGroup> priorityGroups = new ArrayList<>();

    private static class PriorityGroup {
        final Set<Material> materials = new HashSet<>();
        String enchantmentName; // raw config string, e.g. "SILK_TOUCH" - resolved lazily below
        boolean edible;

        private org.bukkit.enchantments.Enchantment resolvedEnchantment;
        private boolean enchantmentResolveAttempted = false;

        boolean matches(ItemStack item) {
            if (item == null) return false;
            if (edible && item.getType().isEdible()) return true;
            if (enchantmentName != null) {
                if (!enchantmentResolveAttempted) {
                    enchantmentResolveAttempted = true;
                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(enchantmentName.toLowerCase());
                    resolvedEnchantment = org.bukkit.Registry.ENCHANTMENT.get(key);
                    if (resolvedEnchantment == null) {
                        Bukkit.getLogger().warning("infinibundle-priority-order: unknown enchantment '" + enchantmentName + "'.");
                    }
                }
                if (resolvedEnchantment != null && item.containsEnchantment(resolvedEnchantment)) return true;
            }
            return materials.contains(item.getType());
        }
    }

    /** Loads infinibundle-priority-order from config.yml. The order groups appear in that list is
     * the sort priority - this reads them with getMapList(), which preserves file order. */
    public static void loadPriorityOrder(org.bukkit.configuration.file.FileConfiguration config) {
        priorityGroups.clear();
        priorityOrderEnabled = config.getBoolean("infinibundle-priority-order.enabled", false);

        for (Map<?, ?> entry : config.getMapList("infinibundle-priority-order.groups")) {
            try {
                PriorityGroup group = new PriorityGroup();

                Object materialsRaw = entry.get("materials");
                if (materialsRaw instanceof List<?> list) {
                    for (Object m : list) {
                        try {
                            group.materials.add(Material.valueOf(String.valueOf(m).toUpperCase()));
                        } catch (IllegalArgumentException ex) {
                            Bukkit.getLogger().warning("infinibundle-priority-order: unknown material '" + m + "' - skipped.");
                        }
                    }
                }

                Object enchantRaw = entry.get("enchantment");
                if (enchantRaw != null) {
                    group.enchantmentName = String.valueOf(enchantRaw);
                }

                group.edible = Boolean.TRUE.equals(entry.get("edible"));

                if (!group.materials.isEmpty() || group.enchantmentName != null || group.edible) {
                    priorityGroups.add(group);
                }
            } catch (Exception ex) {
                Bukkit.getLogger().warning("infinibundle-priority-order: bad entry " + entry + " (" + ex.getMessage() + ") - skipped.");
            }
        }

        if (priorityOrderEnabled) {
            Bukkit.getLogger().info("infinibundle-priority-order: loaded " + priorityGroups.size() + " group(s), in priority order: "
                    + priorityGroups.stream().map(g -> g.enchantmentName != null ? "enchantment:" + g.enchantmentName
                    : g.edible ? "edible" : "materials:" + g.materials).collect(Collectors.joining(" > ")));
        }
    }

    /** Sorts this storage list in place so items matching an earlier priority group come first.
     * Safe/cheap to call every time the Inventory tab is displayed - a no-op if the feature is
     * off or nothing in the list matches any group. */
    public static void applyPriorityOrder(List<ItemStack> storage) {
        if (!priorityOrderEnabled || priorityGroups.isEmpty() || storage.size() < 2) return;
        storage.sort(Comparator.comparingInt(InfinibundleListener::priorityRank));
    }

    private static int priorityRank(ItemStack item) {
        for (int i = 0; i < priorityGroups.size(); i++) {
            if (priorityGroups.get(i).matches(item)) return i;
        }
        return Integer.MAX_VALUE;
    }

    private boolean isInfinibundle(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA;
    }

    private String getTeamName(Player player) {
        return getStorageKey(player);
    }

    /** The storage key for this player's infinibundle - their own name during Lootrun (each
     * player gets an individual infinibundle/collection score, so runners/hunters can't use a
     * shared bundle to pass items across distance), their real team otherwise. Public so
     * LootHunt#giveInfinibundle can seed the right storage with starting items. */
    public static String getStorageKey(Player player) {
        if (ZappierGames.gameMode == ZappierGames.LOOTRUN) {
            return player.getName();
        }
        return getRealTeamName(player);
    }

    /** Always the player's actual scoreboard team, regardless of game mode - used anywhere that
     * needs the real team (Shop balance/purchases), never the Lootrun-individualized storage key
     * that getTeamName() returns above. */
    private static String getRealTeamName(Player player) {
        // Main scoreboard explicitly - see the comment on LootHunt#getPlayerTeamName for why
        // player.getScoreboard() isn't safe here (it can be swapped to an isolated per-viewer
        // board by the live score display feature).
        org.bukkit.scoreboard.Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
        return team != null ? team.getName() : "(Solo) " + player.getName();
    }

    /**
     * True if this player holds the read-write "lock" on a team's storage (the first person to
     * open the Inventory tab, until they close it). Anyone else who opens the Inventory tab while
     * it's held sees a placeholder instead of the real contents/pagination, so two people can
     * never edit the same underlying list at once. Bukkit only ever runs one event handler at a
     * time on the main thread, so claiming the lock via Map#putIfAbsent inside a single
     * synchronous event handler (see openTeamInventory) is atomic - there's no real race even
     * when two players click to open in the same tick.
     */
    private boolean isPrimaryViewer(String team, Player player) {
        return viewingPlayer.get(team) == player;
    }

    /**
     * In Lootrun, every player has their own individual infinibundle (see getTeamName()), so the
     * GUI titles say "<Name>'s Inventory" instead of "<Team> Team Inventory" - this is the one
     * place that difference lives, so every bit of code that builds or parses a GUI title goes
     * through these two helpers instead of hardcoding " Team ".
     */
    private static String titleSeparator() {
        return ZappierGames.gameMode == ZappierGames.LOOTRUN ? "'s " : " Team ";
    }

    private static boolean titleIsMode(String title, String modeLabel) {
        return title.contains(modeLabel);
    }

    private static String extractTeamFromTitle(String title) {
        int idx = title.indexOf(titleSeparator());
        return idx >= 0 ? title.substring(0, idx) : title;
    }

    private static String fmtBalance(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.1f", d);
    }

    /** Extracts just the page number after "Page " - stops at the first non-digit so trailing
     * text (like the Shop's " | Balance: X" suffix) doesn't break the parse. */
    private static int extractPageFromTitle(String title) {
        String after = title.split("Page ")[1];
        int end = 0;
        while (end < after.length() && Character.isDigit(after.charAt(end))) end++;
        return Integer.parseInt(after.substring(0, end));
    }

    // ===== Click rules for views that aren't an editable storage page =====
    //
    // The locked "someone else is in it" placeholder (and the Collections / Quests / Shop tabs)
    // aren't real storage for the person looking at them: anything put into the GUI's own slots
    // would just vanish when it closes. But that must NOT stop them using their own inventory, or
    // the tab buttons. So the rule is narrow - block only what moves items into/out of the GUI.

    /** The bottom-row navigation and tab buttons (see openTeamInventory). */
    static boolean isNavOrModeSlot(int slot) {
        return slot == 45 || slot == 46 || slot == 48 || slot == 49 || slot == 50
                || slot == 51 || slot == 52 || slot == 53;
    }

    enum LockedViewAction { CANCEL, ALLOW_NAV, ALLOW_OWN_INVENTORY }

    /**
     * What to do with a click while the player is looking at the locked placeholder. A click on
     * the GUI's own slots is cancelled unless it's a nav/tab button (safe to run - for a locked
     * viewer it never saves anything). A click in the player's own inventory is left completely
     * alone, except a shift-click, which vanilla would shove into the fake GUI's empty slots.
     */
    static LockedViewAction classifyLockedViewClick(int rawSlot, int topSize, boolean movesToOtherInventory) {
        boolean clickedGui = rawSlot >= 0 && rawSlot < topSize;
        if (clickedGui) return isNavOrModeSlot(rawSlot) ? LockedViewAction.ALLOW_NAV : LockedViewAction.CANCEL;
        return movesToOtherInventory ? LockedViewAction.CANCEL : LockedViewAction.ALLOW_OWN_INVENTORY;
    }

    /**
     * Whether a drag should be cancelled. A drag staying entirely inside the player's own
     * inventory is always fine. One touching the GUI is cancelled unless it's the player's real,
     * editable Inventory page - and even then only the storage area counts, because anything
     * dragged onto the bottom button row would never be saved.
     */
    static boolean shouldCancelDrag(java.util.Collection<Integer> rawSlots, int topSize, boolean editable, int storageSlots) {
        boolean touchesGui = false;
        boolean touchesNonStorage = false;
        for (int s : rawSlots) {
            if (s >= 0 && s < topSize) {
                touchesGui = true;
                if (s >= storageSlots) touchesNonStorage = true;
            }
        }
        if (!touchesGui) return false;
        return !editable || touchesNonStorage;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!event.hasItem() || !event.getAction().toString().contains("RIGHT_CLICK")) return;

        ItemStack hand = event.getItem();
        if (!isInfinibundle(hand)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (ZappierGames.gameMode == ZappierGames.LOOTHUNT && LootHunt.inClosingWindow()) {
            player.sendMessage(Component.text("Loothunt is ending - the infinibundle is locked for the last moment.", NamedTextColor.RED));
            return;
        }

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

        // No hard block here anymore - if someone else is currently using the team's storage,
        // openTeamInventory() below still opens a GUI for this player, but shows a locked
        // placeholder in the Inventory tab (see isPrimaryViewer) rather than the real contents,
        // so two people can never edit the same storage list at once. Collections/Quests/Shop
        // stay fully usable either way since they're read-only.
        if (viewingPlayer.containsKey(team) && viewingPlayer.get(team) != player) {
            player.sendMessage(Component.text(viewingPlayer.get(team).getName() + " is currently in the team inventory - you can still view Collections/Quests/Shop.", NamedTextColor.YELLOW));
        }

        List<ItemStack> storage = getTeamStorage(team);
        // Never re-sort the shared list while someone has it open: their GUI holds copies of the
        // current page, and when they close, savePage splices that page back in BY POSITION - so a
        // re-sort in between would make it overwrite the wrong range (losing some items and
        // duplicating others). Whoever opens it next with nobody inside sorts it then.
        if (!viewingPlayer.containsKey(team)) {
            applyPriorityOrder(storage);
        }

        // Reopen on the page this player last closed the Infinibundle on (recorded by savePage),
        // clamped to the last page that actually has items; first-time openers land on the last
        // occupied page. Sorting doesn't need to override this: priority items are sorted to the
        // front of the list on every open, so they're always on the earliest pages regardless of
        // which page the player resumes on.
        int lastOccupiedPage = Math.max(0, (storage.size() - 1) / SLOTS_PER_PAGE);
        int lastPageToGo = LootHunt.lastPages.getOrDefault(player.getName().toLowerCase(), -1);
        if (lastPageToGo == -1 || lastPageToGo > lastOccupiedPage) {
            lastPageToGo = lastOccupiedPage;
            LootHunt.lastPages.put(player.getName().toLowerCase(), lastPageToGo);
        }
        openTeamInventory(player, lastPageToGo, ViewMode.INVENTORY);
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
                    List<ItemStack> directStorage = getTeamStorage(team);
                    mergeIntoStorage(directStorage, cursor.clone());
                    applyPriorityOrder(directStorage);
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
        if (titleIsMode(title, "Collections") || titleIsMode(title, "Quests") || titleIsMode(title, "Shop")) {
            boolean isShopTopClick = titleIsMode(title, "Shop") && event.getClickedInventory() == view.getTopInventory();
            boolean isNavSlot = event.getSlot() == 45 || event.getSlot() == 46 || event.getSlot() == 48
                    || event.getSlot() == 49 || event.getSlot() == 50 || event.getSlot() == 51
                    || event.getSlot() == 52 || event.getSlot() == 53;
            boolean allowed = isShopTopClick || (event.getClickedInventory() != view.getBottomInventory() && isNavSlot);
            if (!allowed) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 0.5f);
                return;
            }
        }
        if (!titleIsMode(title, "Inventory") && !titleIsMode(title, "Collections") && !titleIsMode(title, "Quests") && !titleIsMode(title, "Shop")) return;

        // Someone viewing the locked placeholder (not the primary storage-lock holder) has nothing
        // real to interact with in the GUI - but they must still be able to use their own inventory
        // and the tab buttons (Collections / Quests / Shop). So only cancel what would move items
        // into or out of the GUI's own slots (see classifyLockedViewClick). Everything else falls
        // through to the normal handling below: nav buttons run, and clicks in their own inventory
        // are left uncancelled so vanilla handles them.
        if (titleIsMode(title, "Inventory") && !isPrimaryViewer(extractTeamFromTitle(title), player)) {
            LockedViewAction action = classifyLockedViewClick(event.getRawSlot(), view.getTopInventory().getSize(),
                    event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY);
            if (action == LockedViewAction.CANCEL) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getClickedInventory() == view.getBottomInventory() && involvesBundle) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() != view.getTopInventory()) return;
        event.setCancelled(true);

        int slot = event.getSlot();

        // === NAVIGATION AND MODE-SWITCH LOGIC (NO FLICKER) ===
        if (slot == 45 || slot == 46 || slot == 48 || slot == 49 || slot == 50 || slot == 51 || slot == 52 || slot == 53) {
            if (current == null) return;
            if ((slot == 45 || slot == 53) && current.getType() != Material.SPECTRAL_ARROW) return;
            if ((slot == 46 || slot == 52) && current.getType() != Material.ARROW) return;
            if (slot == 48 && current.getType() != Material.BOOK) return;
            if (slot == 49 && current.getType() != Material.CHEST) return;
            if (slot == 50 && current.getType() != Material.FILLED_MAP) return;
            if (slot == 51 && current.getType() != Material.EMERALD) return;

            String team = extractTeamFromTitle(title);
            int currentPage = extractPageFromTitle(title) - 1;
            ViewMode mode = ViewMode.fromTitle(title);

            // Only the player who actually holds the storage lock has anything real to save -
            // someone viewing the locked placeholder never has real content to write back.
            if (mode == ViewMode.INVENTORY && isPrimaryViewer(team, player)) {
                savePage(player, event.getInventory(), title);
            }

            switchingPages.add(player.getUniqueId());

            int newPage;
            ViewMode newMode;
            if (slot == 48) {
                newPage = 0;
                newMode = ViewMode.COLLECTIONS;
            } else if (slot == 49) {
                newPage = 0;
                newMode = ViewMode.INVENTORY;
            } else if (slot == 50) {
                newPage = 0;
                newMode = ViewMode.QUESTS;
            } else if (slot == 51) {
                newPage = 0;
                newMode = ViewMode.SHOP;
            } else {
                newMode = mode;
                if (slot == 45) {
                    newPage = 0; // first page
                } else if (slot == 53) {
                    newPage = Integer.MAX_VALUE; // clamped down to the last page in openTeamInventory
                } else {
                    newPage = slot == 46 ? currentPage - 1 : currentPage + 1;
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

            openTeamInventory(player, newPage, newMode);

            switchingPages.remove(player.getUniqueId());
            return;
        }

        String currentTeam = extractTeamFromTitle(title);
        ViewMode currentMode = ViewMode.fromTitle(title);

        if (currentMode == ViewMode.SHOP) {
            if (slot < SLOTS_PER_PAGE) {
                LootrunShop.handlePurchaseClick(player, getRealTeamName(player), current);
                int currentPage = extractPageFromTitle(title) - 1;
                switchingPages.add(player.getUniqueId());
                openTeamInventory(player, currentPage, ViewMode.SHOP);
                switchingPages.remove(player.getUniqueId());
            }
            return;
        }

        if (currentMode == ViewMode.COLLECTIONS || currentMode == ViewMode.QUESTS) return; // No item interactions in read-only views

        if (currentMode == ViewMode.INVENTORY && !isPrimaryViewer(currentTeam, player)) return; // Locked placeholder - nothing to interact with

        if (slot >= SLOTS_PER_PAGE) return;

        // Item manipulation (Shift, Number keys, Clicks)
        handleItemInteractions(event, player, slot, current, cursor);
    }

    /**
     * The order shift-clicking an item out of the Infinibundle fills the player's inventory:
     * the highest hotbar slot first (8 down to 0), then the main inventory from its lower-right
     * corner backwards (35 down to 9). This is the same direction vanilla uses when shift-clicking
     * out of a chest. (Bukkit's own PlayerInventory#addItem fills 0 upward instead - lowest hotbar
     * slot, then the upper-left of the main inventory - which is what this replaces.)
     */
    private static final int[] SHIFT_CLICK_FILL_ORDER = buildShiftClickFillOrder();

    private static int[] buildShiftClickFillOrder() {
        int[] order = new int[36];
        int i = 0;
        for (int slot = 8; slot >= 0; slot--) order[i++] = slot;
        for (int slot = 35; slot >= 9; slot--) order[i++] = slot;
        return order;
    }

    /**
     * Moves as much of the stack as fits into the player's inventory, in SHIFT_CLICK_FILL_ORDER.
     * Like vanilla, tops up existing partial stacks of the same item first (in that same order),
     * then fills empty slots. Returns whatever didn't fit, or null if it all did.
     */
    private static ItemStack giveToPlayerReverse(Player player, ItemStack stack) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack remaining = stack.clone();

        for (int slot : SHIFT_CLICK_FILL_ORDER) {
            if (remaining.getAmount() <= 0) break;
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR || !existing.isSimilar(remaining)) continue;
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining.getAmount());
            existing.setAmount(existing.getAmount() + move);
            inv.setItem(slot, existing);
            remaining.setAmount(remaining.getAmount() - move);
        }

        for (int slot : SHIFT_CLICK_FILL_ORDER) {
            if (remaining.getAmount() <= 0) break;
            ItemStack existing = inv.getItem(slot);
            if (existing != null && existing.getType() != Material.AIR) continue;
            ItemStack placed = remaining.clone();
            placed.setAmount(Math.min(remaining.getMaxStackSize(), remaining.getAmount()));
            inv.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - placed.getAmount());
        }

        return remaining.getAmount() > 0 ? remaining : null;
    }

    private void handleItemInteractions(InventoryClickEvent event, Player player, int slot, ItemStack current, ItemStack cursor) {
        if (event.isShiftClick()) {
            if (current == null || current.getType() == Material.AIR) return;
            // Whatever doesn't fit stays in the bundle slot (null = everything moved out)
            event.getInventory().setItem(slot, giveToPlayerReverse(player, current));
        } else if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            player.getInventory().setItem(hotbarSlot, current != null ? current.clone() : null);
            event.getInventory().setItem(slot, hotbarItem != null ? hotbarItem.clone() : null);
        } else {
            if (cursor == null || cursor.getType() == Material.AIR) {
                if (event.getClick() == ClickType.RIGHT && current != null && current.getType() != Material.AIR) {
                    // Right-click picks up HALF the stack, rounded up (13 -> take 7, leave 6), like
                    // vanilla; a stack of 1 is taken whole.
                    int total = current.getAmount();
                    int take = (total + 1) / 2;
                    ItemStack held = current.clone();
                    held.setAmount(take);
                    player.setItemOnCursor(held);
                    if (total - take > 0) {
                        ItemStack rest = current.clone();
                        rest.setAmount(total - take);
                        event.getInventory().setItem(slot, rest);
                    } else {
                        event.getInventory().setItem(slot, null);
                    }
                } else {
                    player.setItemOnCursor(current != null ? current.clone() : null);
                    event.getInventory().setItem(slot, null);
                }
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

    private void openTeamInventory(Player player, int page, ViewMode mode) {
        if (page < 0) page = 0;

        // Shop always keys off the player's real scoreboard team (so purchases hit the shared
        // Runners/Hunters balance pool); every other tab uses getTeamName()'s storage key, which
        // is the player's own name during Lootrun (individual infinibundle) or the real team
        // otherwise. Computed fresh here rather than trusting a caller-supplied value, since the
        // Shop title intentionally has no team/player name in it (see titleText below) and so
        // can't be parsed back out when navigating away from it.
        String team = (mode == ViewMode.SHOP) ? getRealTeamName(player) : getTeamName(player);

        // Claim the lock if nobody holds it yet; leave it alone if someone else already does.
        // Bukkit event handlers run one at a time on the main thread, so this putIfAbsent is the
        // atomic "first one in wins" check - there's no window for two players to both succeed.
        // Only the Inventory tab actually mutates shared storage, so only it claims the lock -
        // opening Collections/Quests/Shop must never incidentally lock out a teammate's Inventory tab.
        if (mode == ViewMode.INVENTORY) {
            viewingPlayer.putIfAbsent(team, player);
        }
        boolean isPrimary = isPrimaryViewer(team, player);
        boolean showLockedPlaceholder = mode == ViewMode.INVENTORY && !isPrimary;

        int maxPage = 0;
        List<ItemStack> displayItems = null; // used for collections/quests/shop mode

        if (showLockedPlaceholder) {
            maxPage = 0;
        } else if (mode == ViewMode.COLLECTIONS || mode == ViewMode.QUESTS) {
            // Collections tab shows only non-quest collections; Quests tab shows only quest
            // collections (always, regardless of completion, so progress can be tracked here).
            displayItems = buildCollectionDisplayItems(team, mode == ViewMode.QUESTS);
            maxPage = displayItems.isEmpty() ? 0 : (displayItems.size() - 1) / SLOTS_PER_PAGE;
        } else if (mode == ViewMode.SHOP) {
            displayItems = LootrunShop.buildShopDisplayItems(team);
            maxPage = displayItems.isEmpty() ? 0 : (displayItems.size() - 1) / SLOTS_PER_PAGE;
        } else {
            List<ItemStack> storage = getTeamStorage(team);
            applyPriorityOrder(storage);
            maxPage = storage.isEmpty() ? 0 : (storage.size() / SLOTS_PER_PAGE) + 1;
        }

        // Clamp page BEFORE building the title/inventory below - the title is baked in at
        // creation time, so clamping after would leave "First"/"Last" (which pass 0 or
        // Integer.MAX_VALUE as a sentinel) showing a garbage page number, and every subsequent
        // click would misnavigate since it re-parses the current page from that broken title.
        if (page > maxPage) page = maxPage;

        String titleText = mode == ViewMode.SHOP
                ? "Shop - Page " + (page + 1) + "  |  Balance: " + fmtBalance(Lootrun.getBalance(team))
                : team + titleSeparator() + mode.label + " - Page " + (page + 1);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(titleText));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = SLOTS_PER_PAGE; i < 54; i++) inv.setItem(i, filler);

        // Fill items
        if (showLockedPlaceholder) {
            Player holder = viewingPlayer.get(team);
            String holderName = holder != null ? holder.getName() : "Someone";
            ItemStack lockedItem = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta lm = (SkullMeta) lockedItem.getItemMeta();
            if (holder != null) lm.setOwningPlayer(holder);
            lm.displayName(Component.text(holderName + " is currently in the infinibundle", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lm.lore(List.of(Component.text("Try again once they've closed it.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            lockedItem.setItemMeta(lm);
            inv.setItem(22, lockedItem); // roughly centered
        } else if ((mode == ViewMode.COLLECTIONS || mode == ViewMode.QUESTS || mode == ViewMode.SHOP) && displayItems != null) {
            int start = page * SLOTS_PER_PAGE;
            int end = Math.min(start + SLOTS_PER_PAGE, displayItems.size());
            for (int i = start; i < end; i++) {
                ItemStack dispItem = displayItems.get(i);
                if (dispItem != null) {
                    inv.setItem(i - start, dispItem.clone());
                }
            }
        } else if (mode == ViewMode.INVENTORY) {
            List<ItemStack> storage = getTeamStorage(team);
            int start = page * SLOTS_PER_PAGE;
            int end = Math.min(start + SLOTS_PER_PAGE, storage.size());
            if (start < storage.size()) {
                for (int i = start; i < end; i++) {
                    inv.setItem(i - start, storage.get(i).clone());
                }
            }
        }

        // Navigation buttons - First/Last (spectral arrow) on the outside, Prev/Next (arrow) inside
        if (page > 0) {
            ItemStack first = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta fpm = first.getItemMeta();
            fpm.displayName(Component.text("\u00ab First Page", NamedTextColor.AQUA));
            first.setItemMeta(fpm);
            inv.setItem(45, first);

            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", NamedTextColor.GREEN));
            prev.setItemMeta(pm);
            inv.setItem(46, prev);
        }

        // Next/Last buttons: use the pre-calculated maxPage
        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.displayName(Component.text("Next Page", NamedTextColor.GREEN));
            next.setItemMeta(nm);
            inv.setItem(52, next);

            ItemStack last = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta lpm = last.getItemMeta();
            lpm.displayName(Component.text("Last Page \u00bb", NamedTextColor.AQUA));
            last.setItemMeta(lpm);
            inv.setItem(53, last);
        }

        // Mode-select buttons - Infinibundle dead-center at the bottom, Collections to its
        // immediate left and Quests to its immediate right. Shop only shows up during Lootrun
        // (it's a Lootrun-specific variation, not something that should appear in normal
        // LootHunt games).
        inv.setItem(48, modeButton(Material.BOOK, "Collections", mode == ViewMode.COLLECTIONS));
        inv.setItem(49, modeButton(Material.CHEST, "Infinibundle", mode == ViewMode.INVENTORY));
        inv.setItem(50, modeButton(Material.FILLED_MAP, "Quests", mode == ViewMode.QUESTS));
        if (ZappierGames.gameMode == ZappierGames.LOOTRUN) {
            inv.setItem(51, modeButton(Material.EMERALD, "Shop", mode == ViewMode.SHOP));
        }

        player.openInventory(inv);
    }

    private ItemStack modeButton(Material material, String label, boolean active) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((active ? "\u25b6 " : "") + label, active ? NamedTextColor.GOLD : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds the flattened, paginated display-item list for either the Collections tab
     * (questsOnly=false, non-quest collections only) or the Quests tab (questsOnly=true, quest
     * collections only, shown regardless of completion so progress can be tracked).
     */
    private List<ItemStack> buildCollectionDisplayItems(String team, boolean questsOnly) {
        Set<String> collected = getTeamCollectedItems(team);
        List<ItemStack> displayItems = new ArrayList<>();

        for (LootHunt.Collection coll : LootHunt.getSortedCollections()) {
            if (coll.quest != questsOnly) continue;

            long collectedCount = coll.itemGroups.stream()
                    .filter(group -> group.stream().anyMatch(collected::contains))
                    .count();

            // Header
            ItemStack header = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta hm = header.getItemMeta();
            hm.displayName(Component.text(coll.name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
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

        return displayItems;
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

            // The Infinibundle itself never counts toward any collection - but its own physical
            // bundle contents (separate from the plugin's virtual team storage) still should.
            if (LootHunt.isInfinibundle(item)) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    processContainerForIds(ids, bundleMeta.getItems());
                }
                continue;
            }

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

            // Recurse into bundles (any color). The Infinibundle is already handled/skipped above.
            if (LootHunt.isBundle(item.getType())) {
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
        if (!titleIsMode(title, "Inventory") && !titleIsMode(title, "Collections") && !titleIsMode(title, "Quests") && !titleIsMode(title, "Shop")) return;

        String team = extractTeamFromTitle(title);
        boolean wasPrimaryViewer = isPrimaryViewer(team, player);
        List<String> released = releaseLocksHeldBy(player);

        boolean readOnlyTab = title.contains("Collections") || title.contains("Quests") || title.contains("Shop");
        if (!readOnlyTab && wasPrimaryViewer) {
            // Only the lock holder has anything real to save (a locked viewer only ever saw the
            // placeholder). This also merges any deposits made while they had it open.
            savePage(player, event.getInventory(), title);
        }

        // Closing from a read-only tab never saves a page, so merge pending deposits here too -
        // otherwise they'd sit in the buffer, unscored and invisible, until the next save.
        for (String key : released) flushDepositBuffer(key);
    }

    /**
     * Extracted logic to save items from the current inventory view into the team storage list.
     */
    private void savePage(Player player, Inventory inv, String title) {
        String team = extractTeamFromTitle(title);
        List<ItemStack> storage = getTeamStorage(team);

        int page = extractPageFromTitle(title) - 1;
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
        for (String key : releaseLocksHeldBy(player)) flushDepositBuffer(key);
    }

    /**
     * Cancels drags that would put items into the read-only parts of the GUI. Without this, a
     * drag across the locked placeholder's (or the Collections/Quests/Shop tabs') empty slots
     * would drop the dragged items into the fake GUI, where they'd vanish when it closes.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (top.getSize() != 54 || top.getHolder(false) != null) return; // ours are plain 54-slot custom inventories

        String title = PlainTextComponentSerializer.plainText().serialize(view.title());
        if (!titleIsMode(title, "Inventory") && !titleIsMode(title, "Collections")
                && !titleIsMode(title, "Quests") && !titleIsMode(title, "Shop")) return;

        boolean editable = ViewMode.fromTitle(title) == ViewMode.INVENTORY
                && isPrimaryViewer(extractTeamFromTitle(title), player);
        if (shouldCancelDrag(event.getRawSlots(), top.getSize(), editable, SLOTS_PER_PAGE)) {
            event.setCancelled(true);
        }
    }

    /** Adds an item to a storage key's Infinibundle contents (merging into existing stacks where
     * possible), then re-applies the configured priority ordering. Used by the loadout system to
     * put starting-kit items inside the bundle at game start. */
    public static void addToStorage(String storageKey, ItemStack item) {
        List<ItemStack> storage = getTeamStorage(storageKey);
        mergeIntoStorage(storage, item.clone());
        applyPriorityOrder(storage);
    }

    private static void mergeIntoStorage(List<ItemStack> storage, ItemStack item) {
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

    /**
     * Where a deposit for this storage key should go right now: straight into storage when nobody
     * has the bundle open, otherwise into a pending buffer that gets merged into storage when the
     * viewer closes it. (Writing into the live list under an open GUI would just be overwritten
     * when that GUI saves its page.) Applies whether the viewer is someone else or the depositor.
     */
    private static List<ItemStack> depositTarget(String team) {
        return viewingPlayer.containsKey(team)
                ? depositBuffers.computeIfAbsent(team, k -> new ArrayList<>())
                : getTeamStorage(team);
    }

    /** Merges any pending deposits for this key into its storage (then re-sorts). */
    private static void flushDepositBuffer(String team) {
        List<ItemStack> pending = depositBuffers.remove(team);
        if (pending == null || pending.isEmpty()) return;
        List<ItemStack> storage = getTeamStorage(team);
        storage.addAll(pending);
        compressStorage(storage);
    }

    /** Merges every pending deposit buffer - used at shutdown so buffered items aren't lost. */
    public static void flushAllDepositBuffers() {
        for (String team : new ArrayList<>(depositBuffers.keySet())) flushDepositBuffer(team);
    }

    /**
     * Releases every storage lock this player holds and returns the keys released. Done by value
     * rather than by a key parsed out of the GUI title: the Shop tab's title has no team in it, so
     * closing from there used to leave the lock behind - which made every later deposit buffer
     * forever instead of landing in storage.
     */
    private static List<String> releaseLocksHeldBy(Player player) {
        List<String> released = new ArrayList<>();
        viewingPlayer.entrySet().removeIf(en -> {
            if (en.getValue() == player) {
                released.add(en.getKey());
                return true;
            }
            return false;
        });
        return released;
    }

    private static void compressStorage(List<ItemStack> storage) {
        List<ItemStack> compressed = new ArrayList<>();
        for (ItemStack item : storage) mergeIntoStorage(compressed, item.clone());
        storage.clear();
        storage.addAll(compressed);
        // Re-sort here, not just at display time - this is THE central point every save path
        // (close, page-switch, bulk shift-deposit) rebuilds the list through, so this is what
        // makes priority order an actual property of the stored list itself rather than
        // something that only looked right on whichever page happened to be open when it was
        // last computed.
        applyPriorityOrder(storage);
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

        // Deposits are always allowed, even while someone else has the bundle open: the items go
        // into a pending buffer that's merged in when they close it (see depositTarget).
        final Player viewer = viewingPlayer.get(team);
        final boolean buffered = viewer != null;
        final List<ItemStack> target = depositTarget(team);

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
                .append(Component.text(" stacks → Team Storage", NamedTextColor.GREEN))
                .append(Component.text(buffered && viewer != p ? " (appears once " + viewer.getName() + " closes it)" : "", NamedTextColor.GRAY)));

        // If someone is viewing (including possibly self), they will see update on next page change / reopen
        // But when no one views → change is immediately visible on next open (which is what you want)
    }
}