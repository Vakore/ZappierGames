package org.zappier.zappierGames.loothunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.zappier.zappierGames.GUI;
import org.zappier.zappierGames.ZappierGames;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Customizable LootHunt starting loadout.
 *
 * Players choose where each item of the starting kit (the five stone tools, the Infinibundle and
 * any configured shulker boxes) goes: any of their 36 inventory slots, the offhand, or inside the
 * Infinibundle itself. Choices are saved per player (loothunt_loadouts.json in the plugin's data
 * folder, keyed by UUID - same idea as Skybattle's skybattle_slots.json) and applied by
 * {@link #giveKit(Player)} whenever a LootHunt starts.
 *
 * The editor is a 54-slot chest GUI, but it never contains real items. It renders tagged "ghost"
 * copies from an in-memory layout model, and every click is cancelled and interpreted as a
 * select / place action on that model. That's what guarantees nothing can be taken out of the GUI
 * (no free stone sword) or put into it (no items lost into a GUI nobody reads back). See
 * {@link #onClick}, {@link #onDrag} and {@link #onClose} for the extra layers of protection.
 */
public class LootHuntLoadout implements Listener {

    // ========================================================================================
    // Layout model
    // ========================================================================================

    public enum Kind { INVENTORY, OFFHAND, BUNDLE }

    /** Where one kit item goes. Inventory index is the vanilla player-inventory slot (0-8 hotbar,
     * 9-35 backpack); a bundle index is just an insertion-order position inside the Infinibundle. */
    public record Placement(Kind kind, int index) {
        static final int INVENTORY_SLOTS = 36;
        static final int BUNDLE_SLOTS = 45;
        static final Placement OFFHAND_CELL = new Placement(Kind.OFFHAND, 0);

        static Placement inv(int index) { return new Placement(Kind.INVENTORY, index); }
        static Placement bundle(int index) { return new Placement(Kind.BUNDLE, index); }

        boolean isValid() {
            return switch (kind) {
                case INVENTORY -> index >= 0 && index < INVENTORY_SLOTS;
                case OFFHAND -> index == 0;
                case BUNDLE -> index >= 0 && index < BUNDLE_SLOTS;
            };
        }

        int[] encode() { return new int[]{kind.ordinal(), index}; }

        static Placement decode(int[] raw) {
            if (raw == null || raw.length != 2) return null;
            if (raw[0] < 0 || raw[0] >= Kind.values().length) return null;
            Placement p = new Placement(Kind.values()[raw[0]], raw[1]);
            return p.isValid() ? p : null;
        }

        String describe() {
            return switch (kind) {
                case INVENTORY -> index < 9
                        ? "Hotbar slot " + (index + 1)
                        : "Backpack row " + ((index - 9) / 9 + 1) + ", column " + ((index - 9) % 9 + 1);
                case OFFHAND -> "Offhand";
                case BUNDLE -> "Infinibundle slot " + (index + 1);
            };
        }
    }

    private record KitItem(String key, Material material, String name, Placement defaultPlacement, boolean canBeInBundle) {}

    private static final String INFINIBUNDLE_KEY = "INFINIBUNDLE";

    /** The current starting kit, in a stable order. Keys (not slots) identify items, so a saved
     * layout survives the shulker-colors config changing - unknown keys are ignored, new items
     * simply get their default position. Defaults reproduce the original hardcoded kit: tools in
     * hotbar slots 1-5, the Infinibundle in slot 6, shulkers from slot 9 onward. */
    private static List<KitItem> currentKit() {
        List<KitItem> kit = new ArrayList<>();
        kit.add(new KitItem("STONE_SWORD", Material.STONE_SWORD, "Stone Sword", Placement.inv(0), true));
        kit.add(new KitItem("STONE_AXE", Material.STONE_AXE, "Stone Axe", Placement.inv(1), true));
        kit.add(new KitItem("STONE_PICKAXE", Material.STONE_PICKAXE, "Stone Pickaxe", Placement.inv(2), true));
        kit.add(new KitItem("STONE_SHOVEL", Material.STONE_SHOVEL, "Stone Shovel", Placement.inv(3), true));
        kit.add(new KitItem("STONE_HOE", Material.STONE_HOE, "Stone Hoe", Placement.inv(4), true));
        // Can't live inside itself, hence canBeInBundle = false.
        kit.add(new KitItem(INFINIBUNDLE_KEY, Material.BUNDLE, "Infinibundle", Placement.inv(5), false));

        Map<Material, Integer> seen = new HashMap<>();
        Material[] shulkers = LootHunt.getShulkerColors();
        for (int i = 0; i < shulkers.length; i++) {
            Material m = shulkers[i];
            int occurrence = seen.merge(m, 1, Integer::sum) - 1; // a color listed twice stays two items
            String key = "SHULKER:" + m.name() + (occurrence > 0 ? "#" + occurrence : "");
            String name = prettyName(m) + (occurrence > 0 ? " (" + (occurrence + 1) + ")" : "");
            kit.add(new KitItem(key, m, name, Placement.inv(8 + i), true));
        }
        return kit;
    }

    private static String prettyName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static boolean isAllowed(KitItem item, Placement p) {
        return p != null && p.isValid() && !(p.kind() == Kind.BUNDLE && !item.canBeInBundle());
    }

    /**
     * Turns a possibly-partial/stale saved layout into a complete, conflict-free one for the
     * current kit: valid saved placements are honored first (in kit order, so duplicates resolve
     * deterministically), then anything without a usable placement gets its default spot, else the
     * first free inventory slot, else the first free Infinibundle slot.
     */
    private static Map<String, Placement> resolve(List<KitItem> kit, Map<String, Placement> savedLayout) {
        Map<String, Placement> result = new LinkedHashMap<>();
        Set<Placement> taken = new HashSet<>();

        for (KitItem item : kit) {
            Placement p = savedLayout == null ? null : savedLayout.get(item.key());
            if (isAllowed(item, p) && taken.add(p)) result.put(item.key(), p);
        }

        for (KitItem item : kit) {
            if (result.containsKey(item.key())) continue;
            Placement chosen = null;

            Placement def = item.defaultPlacement();
            if (isAllowed(item, def) && !taken.contains(def)) chosen = def;

            for (int i = 0; chosen == null && i < Placement.INVENTORY_SLOTS; i++) {
                if (!taken.contains(Placement.inv(i))) chosen = Placement.inv(i);
            }
            if (chosen == null && !taken.contains(Placement.OFFHAND_CELL)) chosen = Placement.OFFHAND_CELL;
            for (int i = 0; chosen == null && item.canBeInBundle() && i < Placement.BUNDLE_SLOTS; i++) {
                if (!taken.contains(Placement.bundle(i))) chosen = Placement.bundle(i);
            }

            if (chosen != null) {
                taken.add(chosen);
                result.put(item.key(), chosen);
            }
        }
        return result;
    }

    // ========================================================================================
    // Persistence (loothunt_loadouts.json: { "<uuid>": { "<item key>": [kindOrdinal, index] } })
    // ========================================================================================

    private static final Map<String, Map<String, Placement>> saved = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;

    public static void load(File dataFolder) {
        file = new File(dataFolder, "loothunt_loadouts.json");
        saved.clear();
        if (!file.exists()) return;

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Map<String, int[]>>>() {}.getType();
            Map<String, Map<String, int[]>> raw = GSON.fromJson(reader, type);
            if (raw == null) return;
            for (Map.Entry<String, Map<String, int[]>> player : raw.entrySet()) {
                Map<String, Placement> layout = new LinkedHashMap<>();
                if (player.getValue() != null) {
                    for (Map.Entry<String, int[]> entry : player.getValue().entrySet()) {
                        Placement p = Placement.decode(entry.getValue());
                        if (p != null) layout.put(entry.getKey(), p);
                    }
                }
                saved.put(player.getKey(), layout);
            }
        } catch (Exception e) {
            // Don't let a damaged file take the plugin down, and don't let the next save silently
            // overwrite it - set it aside so it can still be inspected/recovered by hand.
            Bukkit.getLogger().warning("Failed to load loothunt_loadouts.json (" + e.getMessage() + ") - moving it aside and starting fresh.");
            saved.clear();
            try {
                Files.move(file.toPath(), new File(dataFolder, "loothunt_loadouts.json.corrupt").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nothing more we can do
            }
        }
    }

    public static void saveAll() {
        if (file == null) return;

        Map<String, Map<String, int[]>> raw = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Placement>> player : saved.entrySet()) {
            Map<String, int[]> layout = new LinkedHashMap<>();
            for (Map.Entry<String, Placement> entry : player.getValue().entrySet()) {
                layout.put(entry.getKey(), entry.getValue().encode());
            }
            raw.put(player.getKey(), layout);
        }

        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            // Write-then-rename so a crash mid-write can't leave a half-written file behind.
            Path tmp = new File(parent, file.getName() + ".tmp").toPath();
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(raw, writer);
            }
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning("Failed to save loothunt_loadouts.json: " + e.getMessage());
        }
    }

    private static Map<String, Placement> layoutFor(UUID playerId) {
        return resolve(currentKit(), saved.get(playerId.toString()));
    }

    // ========================================================================================
    // Applying the kit at game start
    // ========================================================================================

    /** Gives the LootHunt starting kit, positioned per the player's saved loadout (or the
     * original default positions if they never customized it). Called from
     * {@link LootHunt#giveStartingItems} - by then the inventory is cleared and the Infinibundle
     * storage has been reset, so inserting into the bundle here is safe. */
    public static void giveKit(Player player) {
        List<KitItem> kit = currentKit();
        Map<String, Placement> layout = layoutFor(player.getUniqueId());
        String storageKey = InfinibundleListener.getStorageKey(player);
        PlayerInventory inv = player.getInventory();

        List<Map.Entry<Placement, ItemStack>> bundleBound = new ArrayList<>();
        for (KitItem item : kit) {
            ItemStack stack = INFINIBUNDLE_KEY.equals(item.key())
                    ? LootHunt.createInfinibundleItem(player)
                    : new ItemStack(item.material());
            if (item.key().startsWith("SHULKER:")) {
                ZappierGames.getInstance().getLogger().info("Giving shulker box " + item.material() + " to " + player.getName());
            }

            Placement p = layout.get(item.key());
            if (p == null) {
                giveAnywhere(player, storageKey, stack);
                continue;
            }
            switch (p.kind()) {
                case INVENTORY -> inv.setItem(p.index(), stack);
                case OFFHAND -> inv.setItemInOffHand(stack);
                case BUNDLE -> bundleBound.add(Map.entry(p, stack));
            }
        }

        // Insert in the order the player laid them out. (The Infinibundle's own priority sorting,
        // if enabled in config, may still float matching items forward afterwards.)
        bundleBound.sort(Comparator.comparingInt((Map.Entry<Placement, ItemStack> en) -> en.getKey().index()));
        for (Map.Entry<Placement, ItemStack> en : bundleBound) {
            InfinibundleListener.addToStorage(storageKey, en.getValue());
        }
    }

    /** Last-resort placement for an item with no usable spot: any free inventory slot, else the
     * Infinibundle (never silently dropped), else at the player's feet for the bundle itself. */
    private static void giveAnywhere(Player player, String storageKey, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            if (LootHunt.isInfinibundle(leftover)) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            } else {
                InfinibundleListener.addToStorage(storageKey, leftover);
            }
        }
    }

    // ========================================================================================
    // Editor GUI
    // ========================================================================================

    private static final int GUI_SIZE = 54;
    private static final int OFFHAND_GUI_SLOT = 40;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_RESET = 47;
    private static final int SLOT_TOGGLE = 49;
    private static final int SLOT_HELP = 51;
    private static final int SLOT_DONE = 53;

    /** Identifies the editor inventory (more robust than matching on its title) and carries the
     * working state for one editing session. */
    static final class EditorHolder implements InventoryHolder {
        final UUID playerId;
        final List<KitItem> kit;
        final Map<String, Placement> working;
        boolean bundleView = false;   // false = backpack + offhand, true = Infinibundle contents
        String selected = null;       // key of the picked-up kit item, if any
        boolean resetArmed = false;   // reset needs a confirming second click
        Inventory inventory;

        EditorHolder(UUID playerId, List<KitItem> kit, Map<String, Placement> working) {
            this.playerId = playerId;
            this.kit = kit;
            this.working = working;
        }

        KitItem itemByKey(String key) {
            for (KitItem item : kit) if (item.key().equals(key)) return item;
            return null;
        }

        String keyAt(Placement target) {
            for (Map.Entry<String, Placement> e : working.entrySet()) {
                if (e.getValue().equals(target)) return e.getKey();
            }
            return null;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void openEditor(Player player) {
        sweepGhosts(player);

        EditorHolder holder = new EditorHolder(player.getUniqueId(), currentKit(),
                new LinkedHashMap<>(layoutFor(player.getUniqueId())));
        // Constant title on purpose: it deliberately contains none of the words other listeners
        // in this plugin key off ("Inventory", "Collections", "Quests", "Shop") and doesn't end in
        // " Menu". (InventoryView#setTitle is deprecated as broken, so the title can't reflect the
        // current view - the toggle button does that instead.)
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, Component.text("Loothunt Loadout Editor", NamedTextColor.DARK_GREEN));
        holder.inventory = inv;
        render(holder);
        player.openInventory(inv);
    }

    // ---- slot <-> placement mapping -----------------------------------------------------

    // The inventory view mirrors the vanilla inventory screen: backpack rows on top, hotbar below.
    private static int guiToInv(int gui) { return gui < 27 ? gui + 9 : gui - 27; }
    private static int invToGui(int inv) { return inv < 9 ? inv + 27 : inv - 9; }

    private static Placement placementForGuiSlot(boolean bundleView, int gui) {
        if (!bundleView) {
            if (gui >= 0 && gui < Placement.INVENTORY_SLOTS) return Placement.inv(guiToInv(gui));
            if (gui == OFFHAND_GUI_SLOT) return Placement.OFFHAND_CELL;
            return null;
        }
        return gui >= 0 && gui < Placement.BUNDLE_SLOTS ? Placement.bundle(gui) : null;
    }

    private static int guiSlotFor(boolean bundleView, Placement p) {
        if (!bundleView) {
            if (p.kind() == Kind.INVENTORY) return invToGui(p.index());
            if (p.kind() == Kind.OFFHAND) return OFFHAND_GUI_SLOT;
            return -1;
        }
        return p.kind() == Kind.BUNDLE ? p.index() : -1;
    }

    // ---- rendering ----------------------------------------------------------------------

    private static void render(EditorHolder h) {
        Inventory inv = h.inventory;
        inv.clear();

        if (!h.bundleView) {
            for (int g = 0; g < Placement.INVENTORY_SLOTS; g++) inv.setItem(g, emptyCell(placementForGuiSlot(false, g)));
            for (int g = Placement.INVENTORY_SLOTS; g < 45; g++) inv.setItem(g, filler(Material.BLACK_STAINED_GLASS_PANE));
            inv.setItem(OFFHAND_GUI_SLOT, emptyCell(Placement.OFFHAND_CELL));
        } else {
            for (int g = 0; g < Placement.BUNDLE_SLOTS; g++) inv.setItem(g, emptyCell(Placement.bundle(g)));
        }

        for (KitItem item : h.kit) {
            Placement p = h.working.get(item.key());
            if (p == null) continue;
            int guiSlot = guiSlotFor(h.bundleView, p);
            if (guiSlot >= 0) inv.setItem(guiSlot, ghost(item, p, item.key().equals(h.selected)));
        }

        for (int g = 45; g < GUI_SIZE; g++) inv.setItem(g, filler(Material.GRAY_STAINED_GLASS_PANE));

        inv.setItem(SLOT_BACK, control(Material.BARRIER, "Back", NamedTextColor.RED,
                "Return to the Loothunt menu.", "Your changes are saved automatically."));

        if (h.resetArmed) {
            inv.setItem(SLOT_RESET, control(Material.TNT, "Click again to CONFIRM reset", NamedTextColor.DARK_RED,
                    "Puts every item back in its default spot."));
        } else {
            inv.setItem(SLOT_RESET, control(Material.TNT, "Reset to Default", NamedTextColor.RED,
                    "Puts every item back in its default spot.", "(Asks for a second click to confirm.)"));
        }

        String selectedName = h.selected == null ? "nothing" : h.itemByKey(h.selected).name();
        if (!h.bundleView) {
            inv.setItem(SLOT_TOGGLE, control(Material.BUNDLE, "Viewing: Backpack - click for Infinibundle", NamedTextColor.AQUA,
                    "Switch to the Infinibundle's contents.", "Currently selected: " + selectedName));
        } else {
            inv.setItem(SLOT_TOGGLE, control(Material.CHEST, "Viewing: Infinibundle - click for Backpack", NamedTextColor.GOLD,
                    "Switch back to your backpack and offhand.", "Currently selected: " + selectedName));
        }

        inv.setItem(SLOT_HELP, control(Material.BOOK, "How this works", NamedTextColor.YELLOW,
                "1. Click one of your kit items to select it.",
                "2. Click an empty slot to move it there, or",
                "    another kit item to swap places.",
                "3. Use the bundle/chest button to switch between",
                "    your backpack and the Infinibundle.",
                "",
                "Nothing here is real - you can't take items out",
                "or put anything in. Changes apply to your next",
                "Loothunt.",
                "",
                "Infinibundle slots set the order items are put",
                "in. The Infinibundle's own priority sorting may",
                "still move matching items forward."));

        inv.setItem(SLOT_DONE, control(Material.EMERALD_BLOCK, "Done", NamedTextColor.GREEN,
                "Close the editor.", "Your changes are saved automatically."));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack finish(ItemStack stack, ItemMeta meta) {
        meta.getPersistentDataContainer().set(ghostKey(), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack filler(Material pane) {
        ItemStack stack = new ItemStack(pane);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        return finish(stack, meta);
    }

    private static ItemStack control(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name, color));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(line(l, NamedTextColor.GRAY));
        meta.lore(lines);
        return finish(stack, meta);
    }

    private static ItemStack emptyCell(Placement p) {
        Material pane = switch (p.kind()) {
            case INVENTORY -> p.index() < 9 ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            case OFFHAND -> Material.PURPLE_STAINED_GLASS_PANE;
            case BUNDLE -> Material.ORANGE_STAINED_GLASS_PANE;
        };
        ItemStack stack = new ItemStack(pane);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line("Empty - " + p.describe(), NamedTextColor.GRAY));
        meta.lore(List.of(line("Select a kit item, then click here to place it.", NamedTextColor.DARK_GRAY)));
        return finish(stack, meta);
    }

    private static ItemStack ghost(KitItem item, Placement p, boolean selected) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(item.name(), selected ? NamedTextColor.GOLD : NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Position: " + p.describe(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (selected) {
            lore.add(line("SELECTED", NamedTextColor.GREEN));
            lore.add(line("Click a slot to move it there, or", NamedTextColor.GRAY));
            lore.add(line("another kit item to swap places.", NamedTextColor.GRAY));
            lore.add(line("Click this again to deselect.", NamedTextColor.DARK_GRAY));
            meta.setEnchantmentGlintOverride(true);
        } else {
            lore.add(line("Click to select, then click where", NamedTextColor.GRAY));
            lore.add(line("it should go.", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return finish(stack, meta);
    }

    // ---- ghost tagging / safety nets ------------------------------------------------------

    private static NamespacedKey ghostKey;

    private static NamespacedKey ghostKey() {
        if (ghostKey == null) ghostKey = new NamespacedKey(ZappierGames.getInstance(), "loadout_ghost");
        return ghostKey;
    }

    private static boolean isGhost(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ghostKey(), PersistentDataType.BYTE);
    }

    /** If a ghost item ever ended up in someone's real inventory or cursor (it can't through this
     * GUI, but this is a cheap backstop), delete it. */
    private static void sweepGhosts(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGhost(contents[i])) inv.setItem(i, null);
        }
        if (isGhost(player.getItemOnCursor())) player.setItemOnCursor(null);
    }

    /** The opposite backstop: if anything real somehow got into the editor, hand it back instead
     * of letting it vanish along with the closed inventory. */
    private static void returnForeignItems(Player player, Inventory top) {
        for (ItemStack item : top.getContents()) {
            if (item == null || item.getType().isAir() || isGhost(item)) continue;
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    // ---- click handling -----------------------------------------------------------------

    private static void feedbackPick(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
    }

    private static void feedbackPlace(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.6f);
    }

    private static void feedbackReject(Player p, String reason) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        if (reason != null) p.sendActionBar(Component.text(reason, NamedTextColor.RED));
    }

    private enum Outcome { NEED_SELECTION, PICKED, DESELECTED, PLACED, SWAPPED, REJECTED }

    private record ClickResult(Outcome outcome, String message) {
        static ClickResult of(Outcome outcome) { return new ClickResult(outcome, null); }
    }

    /**
     * The editor's whole select / place / swap rule set for a click on a cell, with no Bukkit
     * calls so it can be exercised without a server. Mutates h.working / h.selected and reports
     * what happened; the caller handles sounds, messages and re-rendering.
     */
    static ClickResult processCellClick(EditorHolder h, Placement target) {
        String occupant = h.keyAt(target);

        // Nothing picked up yet: this click picks something up (or there's nothing to pick up).
        if (h.selected == null) {
            if (occupant == null) return ClickResult.of(Outcome.NEED_SELECTION);
            h.selected = occupant;
            return ClickResult.of(Outcome.PICKED);
        }

        // Clicking the selected item again just puts it down where it is.
        if (h.selected.equals(occupant)) {
            h.selected = null;
            return ClickResult.of(Outcome.DESELECTED);
        }

        KitItem moving = h.itemByKey(h.selected);
        if (moving == null) { // stale selection - can't happen in practice, but never NPE a click
            h.selected = null;
            return ClickResult.of(Outcome.NEED_SELECTION);
        }
        Placement from = h.working.get(h.selected);

        if (target.kind() == Kind.BUNDLE && !moving.canBeInBundle()) {
            return new ClickResult(Outcome.REJECTED, moving.name() + " can't be stored inside the Infinibundle.");
        }

        if (occupant == null) {
            h.working.put(h.selected, target);
            h.selected = null;
            return ClickResult.of(Outcome.PLACED);
        }

        // Swap: the displaced item takes the selected item's old spot - which must be legal for it.
        KitItem displaced = h.itemByKey(occupant);
        if (from == null || (from.kind() == Kind.BUNDLE && !displaced.canBeInBundle())) {
            return new ClickResult(Outcome.REJECTED, "Can't swap - " + displaced.name() + " can't be stored inside the Infinibundle.");
        }
        h.working.put(occupant, from);
        h.working.put(h.selected, target);
        h.selected = null;
        return ClickResult.of(Outcome.SWAPPED);
    }

    private static void handleSlotClick(Player p, EditorHolder h, int slot) {
        boolean resetWasArmed = h.resetArmed;
        if (slot != SLOT_RESET) h.resetArmed = false;

        switch (slot) {
            case SLOT_BACK -> {
                commit(h);
                new GUI("Loothunt").open(p);
                return;
            }
            case SLOT_DONE -> {
                p.closeInventory();
                return;
            }
            case SLOT_TOGGLE -> {
                h.bundleView = !h.bundleView;
                feedbackPick(p);
                if (h.selected != null) {
                    p.sendActionBar(Component.text("Selected: " + h.itemByKey(h.selected).name()
                            + " - click a slot to place it", NamedTextColor.GOLD));
                }
                render(h);
                return;
            }
            case SLOT_RESET -> {
                if (!resetWasArmed) {
                    h.resetArmed = true;
                    feedbackPick(p);
                } else {
                    h.working.clear();
                    h.working.putAll(resolve(h.kit, null));
                    h.selected = null;
                    h.resetArmed = false;
                    feedbackPlace(p);
                    p.sendActionBar(Component.text("Loadout reset to default.", NamedTextColor.GREEN));
                }
                render(h);
                return;
            }
            case SLOT_HELP -> {
                return;
            }
            default -> { /* a cell, handled below (or decoration) */ }
        }

        Placement target = placementForGuiSlot(h.bundleView, slot);
        if (target == null) {
            render(h); // decoration - just undo a pending reset confirmation, if any
            return;
        }

        ClickResult result = processCellClick(h, target);
        switch (result.outcome()) {
            case NEED_SELECTION -> feedbackReject(p, "Click one of your kit items first, then click where it should go.");
            case PICKED -> {
                feedbackPick(p);
                p.sendActionBar(Component.text("Selected: " + h.itemByKey(h.selected).name()
                        + " - click a slot to place it", NamedTextColor.GOLD));
            }
            case DESELECTED -> feedbackPick(p);
            case PLACED, SWAPPED -> feedbackPlace(p);
            case REJECTED -> feedbackReject(p, result.message());
        }
        render(h);
    }

    // ---- events -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof EditorHolder holder)) return;

        // Nothing real may ever move in or out while this GUI is open - not via the editor's own
        // slots, and not via the player's real inventory underneath it (shift-click, number keys,
        // swap-offhand, drop, double-click collect, creative clone...). Every click is cancelled;
        // the ones we understand are then interpreted against the layout model instead.
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(holder.playerId)) return;

        ClickType click = e.getClick();
        boolean simpleClick = click == ClickType.LEFT || click == ClickType.RIGHT
                || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        int raw = e.getRawSlot();
        if (simpleClick && raw >= 0 && raw < top.getSize()) {
            handleSlotClick(p, holder, raw);
        }

        // Cancelling already makes the server resync the clicked slot and cursor; this makes sure
        // everything else we re-rendered shows up promptly too.
        Bukkit.getScheduler().runTask(ZappierGames.getInstance(), p::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof EditorHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof EditorHolder holder)) return;
        commit(holder);
        if (e.getPlayer() instanceof Player p) {
            returnForeignItems(p, e.getInventory());
            sweepGhosts(p);
        }
    }

    /** Stores an editing session's result for that player and writes the file. A no-op when the
     * session changed nothing, so merely opening the editor never pins the current defaults. */
    private static void commit(EditorHolder h) {
        String id = h.playerId.toString();
        if (h.working.equals(resolve(h.kit, saved.get(id)))) return;
        saved.put(id, new LinkedHashMap<>(h.working));
        saveAll();
    }

    /** For onDisable: editors still open at shutdown/reload never get an InventoryCloseEvent
     * delivered to this listener, so persist their pending changes explicitly. */
    public static void commitOpenEditors() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder holder) {
                commit(holder);
            }
        }
    }
}