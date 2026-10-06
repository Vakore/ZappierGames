package org.zappier.zappierGames.lootrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.zappier.zappierGames.ZappierGames;
import org.zappier.zappierGames.loothunt.InfinibundleListener;
import org.zappier.zappierGames.loothunt.LootHunt;

import java.util.*;

import static org.zappier.zappierGames.skybattle.Skybattle.getPlayerTeam;

/**
 * The Lootrun shop: buffs/nerfs purchased with each team's Lootrun points (see Lootrun.java for
 * how those are earned). Prices scale by the current number of online hunters, per the 1-5 hunter
 * cost table, and are fully config-driven (see config.yml's "lootrun.shop" section).
 *
 * A handful of effects from the design doc were too ambiguous to implement confidently (unclear
 * target-selection UX, or no clear vanilla mechanic to hang them on) - those are marked
 * notImplemented and show "Not Implemented" in the shop instead of a working effect, rather than
 * guessing at behavior that might be wrong.
 */
public class LootrunShop implements Listener {

    public enum Category { BUFF, RUNNER_BUFF, HUNTER_BUFF, NERF, HUNTER_NERF }

    public static class EffectDef {
        public final String id;
        public final String displayName;
        public final Category category;
        public final Material icon;
        public final boolean notImplemented;
        public final List<String> loreNotes;

        EffectDef(String id, String displayName, Category category, Material icon, boolean notImplemented, String... loreNotes) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
            this.icon = icon;
            this.notImplemented = notImplemented;
            this.loreNotes = Arrays.asList(loreNotes);
        }

        /** Nerfs/Hunter Nerfs are bought by one side to apply against the other; everything else
         * is bought by a team for itself. */
        public boolean isOpponentTargeted() {
            return category == Category.NERF || category == Category.HUNTER_NERF;
        }
    }

    public static final List<EffectDef> EFFECTS = List.of(
            new EffectDef("EXTRA_HEARTS", "+2 Hearts", Category.BUFF, Material.GOLDEN_APPLE, false),
            new EffectDef("ACHIEVEMENTS_OFF", "Achievements Off", Category.BUFF, Material.BOOK, true),
            new EffectDef("FIRE_RESISTANCE", "Fire Resistance", Category.BUFF, Material.MAGMA_CREAM, false),
            new EffectDef("WATER_BREATHING", "Water Breathing", Category.BUFF, Material.PUFFERFISH, false),
            new EffectDef("HASTE", "Haste", Category.BUFF, Material.GOLDEN_PICKAXE, false, "Stacks up to level 2"),
            new EffectDef("REDUCE_FALL_DAMAGE", "Reduce Fall Damage", Category.BUFF, Material.FEATHER, false),
            new EffectDef("SHRINK_SOMEONE", "Shrink Someone", Category.BUFF, Material.SLIME_BALL, true),
            new EffectDef("KEEP_INVENTORY_BUFF", "Keep Inventory", Category.BUFF, Material.ENDER_CHEST, false),

            new EffectDef("RUNNER_COMPASS", "Runner Compass", Category.RUNNER_BUFF, Material.COMPASS, false),
            new EffectDef("EXTRA_LIFE", "Extra Life", Category.RUNNER_BUFF, Material.TOTEM_OF_UNDYING, false, "Respawn normally instead of being eliminated"),

            new EffectDef("HUNTER_INSURANCE", "Hunter Insurance Policy", Category.HUNTER_BUFF, Material.SHIELD, false, "Keep inventory on death"),
            new EffectDef("CURSE_OF_VANISHING_ITEMS", "Curse of Vanishing Items", Category.HUNTER_BUFF, Material.SOUL_SAND, true),

            new EffectDef("NO_OFFHAND", "No Offhand", Category.NERF, Material.SHIELD, false),
            new EffectDef("TNT_SPAWNING", "TNT Spawning", Category.NERF, Material.TNT, false, "Every 30 seconds"),
            new EffectDef("HUNGER", "Hunger", Category.NERF, Material.ROTTEN_FLESH, false),
            new EffectDef("SLOWNESS_1", "Slowness 1", Category.NERF, Material.MUD, false),
            new EffectDef("INFESTATION", "Infestation", Category.NERF, Material.SPAWNER, true),
            new EffectDef("INCREASE_FALL_DAMAGE", "Increase Fall Damage", Category.NERF, Material.ANVIL, false),
            new EffectDef("PERMANENT_FROST_WALKER", "Permanent Frost Walker", Category.NERF, Material.PACKED_ICE, false),
            new EffectDef("MINUS_ONE_HOTBAR_SLOT", "-1 Hotbar Slot", Category.NERF, Material.BARRIER, false),
            new EffectDef("CURSE_OF_BINDING_PUMPKIN", "Curse of Binding Pumpkin", Category.NERF, Material.CARVED_PUMPKIN, true),
            new EffectDef("NEUTRAL_MOBS_AGGRO", "Neutral Mobs Aggro", Category.NERF, Material.ZOMBIE_HEAD, true),
            new EffectDef("ETERNAL_FIRE", "Eternal Fire", Category.NERF, Material.FIRE_CHARGE, true),
            new EffectDef("GLOWING", "Glowing", Category.NERF, Material.GLOWSTONE_DUST, false),

            new EffectDef("RESPAWN_COOLDOWN", "Respawn Cooldown (3 min)", Category.HUNTER_NERF, Material.CLOCK, false)
    );

    private static final Map<String, EffectDef> BY_ID = new LinkedHashMap<>();
    static {
        for (EffectDef e : EFFECTS) BY_ID.put(e.id, e);
    }

    // effectId -> [1 hunter, 2 hunters, 3 hunters, 4 hunters, 5+ hunters] cost
    private static final Map<String, double[]> prices = new HashMap<>();

    public static void loadConfig(ConfigurationSection shopSection) {
        prices.clear();
        if (shopSection == null) return;
        for (String id : shopSection.getKeys(false)) {
            List<Double> list = shopSection.getDoubleList(id);
            double[] arr = new double[5];
            for (int i = 0; i < 5; i++) {
                arr[i] = i < list.size() ? list.get(i) : (list.isEmpty() ? 0 : list.get(list.size() - 1));
            }
            prices.put(id.toUpperCase(), arr);
        }
    }

    public static double getPrice(String id) {
        double[] arr = prices.get(id);
        if (arr == null) return 0;
        int hunterCount = Math.max(1, Math.min(5, Lootrun.currentHunterCount()));
        return arr[hunterCount - 1];
    }

    // team the effect is currently active AGAINST (for nerfs) or FOR (for buffs) -> effect ids
    private static final Map<String, Set<String>> activeEffects = new HashMap<>();
    // per-team stacking level for effects that scale (Haste capped 0-1; Hearts uncapped - each
    // purchase adds another +2 hearts, same as prices clearly intend "purchasable multiple times")
    private static final Map<String, Integer> hasteLevel = new HashMap<>();
    private static final Map<String, Integer> heartsLevel = new HashMap<>();
    // players currently serving a respawn-cooldown timeout (see RESPAWN_COOLDOWN)
    private static final Set<UUID> respawnCoolingDown = new HashSet<>();
    // Players whose most recent death had keep-inventory active - checked (and cleared) on their
    // next respawn so they don't get handed a duplicate infinibundle (see onPlayerRespawn).
    private static final Set<UUID> keptInventoryLastDeath = new HashSet<>();
    // Unused Extra Life charges per balance group (realistically just "Runners"). Consumed on
    // death in onPlayerDeath below - a flat respawn, no damage-prevention trickery.
    private static final Map<String, Integer> extraLifeCharges = new HashMap<>();
    // Runners who died with zero Extra Life charges remaining - sent to Spectator on their next
    // respawn instead of a normal one (see onPlayerRespawn).
    private static final Set<UUID> eliminatedRunners = new HashSet<>();

    public static void clearAll() {
        activeEffects.clear();
        hasteLevel.clear();
        heartsLevel.clear();
        respawnCoolingDown.clear();
        keptInventoryLastDeath.clear();
        extraLifeCharges.clear();
        eliminatedRunners.clear();
    }

    private static final NamespacedKey EFFECT_ID_KEY = new NamespacedKey("zappiergames", "lootrun_effect_id");

    /** Builds the flattened, paginated shop display for the given viewing team. Each category
     * starts on its own fresh row (padded with nulls, same pattern as the Collections tab), and
     * a category with more items than fit in one row naturally spills onto the next page via the
     * normal pagination math - no special handling needed for that part. */
    public static List<ItemStack> buildShopDisplayItems(String team) {
        List<ItemStack> items = new ArrayList<>();
        Category lastCategory = null;

        for (EffectDef def : EFFECTS) {
            if (def.category != lastCategory) {
                if (lastCategory != null) padToNextRow(items);
                items.add(categoryHeader(def.category));
                lastCategory = def.category;
            }
            items.add(buildShopItem(team, def));
        }

        return items;
    }

    private static void padToNextRow(List<ItemStack> items) {
        int remainder = items.size() % 9;
        if (remainder != 0) {
            for (int i = 0; i < 9 - remainder; i++) items.add(null);
        }
    }

    private static ItemStack categoryHeader(Category category) {
        ItemStack item = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("\u2500\u2500\u2500 " + prettyCategory(category) + " \u2500\u2500\u2500", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static String prettyCategory(Category c) {
        return switch (c) {
            case BUFF -> "Buffs";
            case RUNNER_BUFF -> "Runner Buffs";
            case HUNTER_BUFF -> "Hunter Buffs";
            case NERF -> "Nerfs";
            case HUNTER_NERF -> "Hunter Nerfs";
        };
    }

    private static ItemStack buildShopItem(String team, EffectDef def) {
        double price = getPrice(def.id);
        String owningGroup = owningGroupFor(def);
        boolean sideRestricted = owningGroup != null;
        boolean viewerMatchesSide = !sideRestricted || Lootrun.balanceGroup(team).equals(owningGroup);
        boolean activeOnOwningSide = sideRestricted && activeEffects.getOrDefault(owningGroup, Set.of()).contains(def.id);

        // Wrong side, and nothing currently active to remove - show a plain barrier, not buyable
        if (!def.notImplemented && sideRestricted && !viewerMatchesSide && !activeOnOwningSide) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            bm.displayName(Component.text(def.displayName, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            bm.lore(List.of(Component.text("Not available to your team", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
            barrier.setItemMeta(bm);
            return barrier;
        }

        ItemStack item = new ItemStack(def.icon);
        ItemMeta meta = item.getItemMeta();

        boolean activeAgainstOrForMe = activeEffects.getOrDefault(team, Set.of()).contains(def.id);

        List<Component> lore = new ArrayList<>();
        if (def.notImplemented) {
            meta.displayName(Component.text(def.displayName, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Not Implemented", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            meta.displayName(Component.text(def.displayName, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            for (String note : def.loreNotes) {
                lore.add(Component.text(note, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            if (sideRestricted && !viewerMatchesSide) {
                // Must be activeOnOwningSide to have reached here - wrong side, but can pay to remove it
                lore.add(Component.text("The " + owningGroup + " side has this active!", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to REMOVE - " + fmt(price) + " points", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            } else if (def.isOpponentTargeted() && activeAgainstOrForMe) {
                lore.add(Component.text("Currently active against your team!", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to REMOVE - " + fmt(price) + " points", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            } else if (def.isOpponentTargeted()) {
                lore.add(Component.text("Click to apply to the opposing team", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Cost: " + fmt(price) + " points", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Click to apply to your team", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Cost: " + fmt(price) + " points", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Your balance: " + fmt(Lootrun.getBalance(team)), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(EFFECT_ID_KEY, PersistentDataType.STRING, def.id);
        item.setItemMeta(meta);
        return item;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.1f", d);
    }

    /** Runner Buffs can only be bought (applied) by Runners, Hunter Buffs only by Hunters - the
     * opposing side can still pay to remove an active one, but never apply it themselves. Returns
     * null for categories with no such restriction. */
    private static String owningGroupFor(EffectDef def) {
        if (def.category == Category.RUNNER_BUFF) return "Runners";
        if (def.category == Category.HUNTER_BUFF) return "Hunters";
        return null;
    }

    /** Handles a click on a shop item: resolves which effect it is from the item's persistent
     * data, then purchases/applies it (or removes it, if it's an active nerf against this team). */
    public static void handlePurchaseClick(Player player, String team, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String id = meta.getPersistentDataContainer().get(EFFECT_ID_KEY, PersistentDataType.STRING);
        if (id == null) return;

        EffectDef def = BY_ID.get(id);
        if (def == null) return;

        if (def.notImplemented) {
            player.sendMessage(Component.text(def.displayName + " is not implemented yet.", NamedTextColor.RED));
            playFailSound(player);
            return;
        }

        double price = getPrice(id);

        String owningGroup = owningGroupFor(def);
        boolean sideRestricted = owningGroup != null;
        boolean viewerMatchesSide = !sideRestricted || Lootrun.balanceGroup(team).equals(owningGroup);

        if (sideRestricted && !viewerMatchesSide) {
            boolean activeOnOwningSide = activeEffects.getOrDefault(owningGroup, Set.of()).contains(id);
            if (!activeOnOwningSide) {
                player.sendMessage(Component.text("This buff isn't available to your team.", NamedTextColor.RED));
                playFailSound(player);
                return;
            }
            if (!Lootrun.trySpend(team, price)) {
                player.sendMessage(Component.text("Not enough points! Need " + fmt(price) + ".", NamedTextColor.RED));
                playFailSound(player);
                return;
            }
            activeEffects.getOrDefault(owningGroup, Set.of()).remove(id);
            removeEffect(id, owningGroup);
            player.sendMessage(Component.text("Removed " + def.displayName + " from the " + owningGroup + " side.", NamedTextColor.GREEN));
            playSuccessSound(player);
            return;
        }

        boolean currentlyActive = activeEffects.getOrDefault(team, Set.of()).contains(id);

        if (def.isOpponentTargeted() && currentlyActive) {
            // Paying to remove a nerf currently active against you, same price as buying it
            if (!Lootrun.trySpend(team, price)) {
                player.sendMessage(Component.text("Not enough points! Need " + fmt(price) + ".", NamedTextColor.RED));
                playFailSound(player);
                return;
            }
            activeEffects.getOrDefault(team, Set.of()).remove(id);
            removeEffect(id, team);
            player.sendMessage(Component.text("Removed " + def.displayName + " from your team.", NamedTextColor.GREEN));
            playSuccessSound(player);
        } else {
            if (!Lootrun.trySpend(team, price)) {
                player.sendMessage(Component.text("Not enough points! Need " + fmt(price) + ".", NamedTextColor.RED));
                playFailSound(player);
                return;
            }
            String targetTeam = def.isOpponentTargeted() ? opposingTeam(team) : team;
            if (targetTeam == null) {
                Lootrun.refund(team, price);
                player.sendMessage(Component.text("Couldn't determine which team to target.", NamedTextColor.RED));
                playFailSound(player);
                return;
            }
            activeEffects.computeIfAbsent(targetTeam, k -> new HashSet<>()).add(id);
            applyEffect(id, targetTeam);
            player.sendMessage(Component.text("Purchased " + def.displayName + " for " + fmt(price) + " points.", NamedTextColor.GREEN));
            playSuccessSound(player);
        }

        // NOTE: deliberately not closing the inventory here - the caller (InfinibundleListener)
        // re-renders the same Shop page right after this call, so the price/active-status/balance
        // update immediately without kicking the player out of the shop.
    }

    private static void playFailSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    private static void playSuccessSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.6f);
    }

    private static String opposingTeam(String buyerTeam) {
        if (Lootrun.isRunnerSide(buyerTeam)) return "Hunters";
        if (Lootrun.isHunterSide(buyerTeam)) return "Runners";
        return null;
    }

    // ================= Effect application =================

    private static void applyEffect(String id, String targetTeam) {
        switch (id) {
            case "HASTE" -> hasteLevel.merge(targetTeam, 1, (a, b) -> Math.min(1, a + b));
            case "EXTRA_HEARTS" -> heartsLevel.merge(targetTeam, 1, Integer::sum);
            case "REDUCE_FALL_DAMAGE" -> {
                for (Player p : teamMembers(targetTeam)) {
                    var attr = p.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER);
                    // Guard against halving again on a repeat purchase - this is a flat 0.5x, not
                    // a stacking effect like Hearts/Haste.
                    if (attr != null && attr.getBaseValue() > 0.5) attr.setBaseValue(attr.getBaseValue() * 0.5);
                }
            }
            case "EXTRA_LIFE" -> extraLifeCharges.merge(targetTeam, 1, Integer::sum);
            case "MINUS_ONE_HOTBAR_SLOT" -> {
                for (Player p : teamMembers(targetTeam)) lockHotbarSlot(p);
            }
            default -> { /* everything else is applied continuously in tick() or via event hooks */ }
        }
    }

    private static void removeEffect(String id, String targetTeam) {
        switch (id) {
            case "MINUS_ONE_HOTBAR_SLOT" -> {
                for (Player p : teamMembers(targetTeam)) unlockHotbarSlot(p);
            }
            case "GLOWING" -> {
                for (Player p : teamMembers(targetTeam)) p.removePotionEffect(PotionEffectType.GLOWING);
            }
            case "HUNGER" -> {
                for (Player p : teamMembers(targetTeam)) p.removePotionEffect(PotionEffectType.HUNGER);
            }
            case "SLOWNESS_1" -> {
                for (Player p : teamMembers(targetTeam)) p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            case "EXTRA_LIFE" -> {
                // Only takes away an unused charge - never retroactively eliminates a runner who
                // already used theirs. If this drops to 0, clear the active flag too so the shop
                // UI stops offering a removal and runners can buy fresh ones again.
                int remaining = extraLifeCharges.merge(targetTeam, -1, Integer::sum);
                if (remaining <= 0) {
                    extraLifeCharges.put(targetTeam, 0);
                    activeEffects.getOrDefault(targetTeam, Set.of()).remove("EXTRA_LIFE");
                }
            }
            default -> { /* one-shot/attribute effects don't need explicit reversal */ }
        }
    }

    private static void lockHotbarSlot(Player p) {
        ItemStack lock = new ItemStack(Material.BARRIER);
        ItemMeta meta = lock.getItemMeta();
        meta.displayName(Component.text("Locked", NamedTextColor.RED));
        meta.getPersistentDataContainer().set(EFFECT_ID_KEY, PersistentDataType.STRING, "HOTBAR_LOCK");
        lock.setItemMeta(meta);
        p.getInventory().setItem(8, lock);
    }

    private static void unlockHotbarSlot(Player p) {
        ItemStack current = p.getInventory().getItem(8);
        if (current != null && current.hasItemMeta()
                && "HOTBAR_LOCK".equals(current.getItemMeta().getPersistentDataContainer().get(EFFECT_ID_KEY, PersistentDataType.STRING))) {
            p.getInventory().setItem(8, null);
        }
    }

    private static List<Player> teamMembers(String team) {
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (team.equals(getPlayerTeam(p))) players.add(p);
        }
        return players;
    }

    /** Called every second from Lootrun.run() - reapplies continuous effects (potion effects,
     * enchantments) so they survive respawns/effect expiry instead of only applying once. */
    public static void tick() {
        for (Map.Entry<String, Set<String>> entry : activeEffects.entrySet()) {
            String team = entry.getKey();
            List<Player> members = teamMembers(team);
            if (members.isEmpty()) continue;

            for (String id : entry.getValue()) {
                for (Player p : members) {
                    switch (id) {
                        case "EXTRA_HEARTS" -> {
                            var attr = p.getAttribute(Attribute.MAX_HEALTH);
                            double target = 20.0 + 4.0 * heartsLevel.getOrDefault(team, 1); // 1 heart = 2 HP, so +2 hearts = +4 HP per purchase
                            if (attr != null && attr.getBaseValue() < target) attr.setBaseValue(target);
                        }
                        case "FIRE_RESISTANCE" -> p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 140, 0, true, false));
                        case "WATER_BREATHING" -> p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 140, 0, true, false));
                        case "HASTE" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 140, hasteLevel.getOrDefault(team, 0), true, false));
                        case "HUNGER" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 140, 0, true, false));
                        case "SLOWNESS_1" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 0, true, false));
                        case "GLOWING" -> p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 140, 0, true, false));
                        case "NO_OFFHAND" -> p.getInventory().setItemInOffHand(null);
                        case "PERMANENT_FROST_WALKER" -> applyFrostWalker(p);
                        case "MINUS_ONE_HOTBAR_SLOT" -> lockHotbarSlot(p);
                        default -> { /* handled elsewhere */ }
                    }
                }
            }

            if (entry.getValue().contains("TNT_SPAWNING") && Lootrun.currentHunterCount() >= 0) {
                tntSpawnTickCounters.merge(team, 1, Integer::sum);
                if (tntSpawnTickCounters.get(team) >= 30) {
                    tntSpawnTickCounters.put(team, 0);
                    spawnTntNear(members);
                }
            }
        }
    }

    private static final Map<String, Integer> tntSpawnTickCounters = new HashMap<>();

    private static void spawnTntNear(List<Player> members) {
        if (members.isEmpty()) return;
        Player target = members.get(new Random().nextInt(members.size()));
        var loc = target.getLocation().add((Math.random() - 0.5) * 4, 1, (Math.random() - 0.5) * 4);
        target.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.TNT);
    }

    private static void applyFrostWalker(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        if (boots != null && boots.getType() != Material.AIR && !boots.containsEnchantment(Enchantment.FROST_WALKER)) {
            boots.addUnsafeEnchantment(Enchantment.FROST_WALKER, 2);
        }
    }

    // ================= Event hooks for effects that need them =================

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (ZappierGames.gameMode != ZappierGames.LOOTRUN) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        String team = getPlayerTeam(p);
        if (activeEffects.getOrDefault(team, Set.of()).contains("INCREASE_FALL_DAMAGE")) {
            event.setDamage(event.getDamage() * 2.0);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (ZappierGames.gameMode != ZappierGames.LOOTRUN) return;
        Player p = event.getEntity();
        String team = getPlayerTeam(p);

        // The Infinibundle itself is a UI tool, not loot - it should never end up as a ground
        // item. It gets re-given fresh in onPlayerRespawn below regardless of keep-inventory.
        event.getDrops().removeIf(LootHunt::isInfinibundle);

        // Every player's bundle is their own individual storage in this mode (not shared team
        // storage), so when a runner dies, spill its contents onto the ground as real dropped
        // items - giving hunters a shot at the loot - then empty that storage. This happens
        // regardless of any active keep-inventory effect, since it's a separate mechanic from
        // what happens to the rest of their inventory.
        if (Lootrun.isRunnerSide(team)) {
            List<ItemStack> storage = InfinibundleListener.getTeamStorage(p.getName());
            if (!storage.isEmpty()) {
                for (ItemStack item : storage) {
                    p.getWorld().dropItemNaturally(p.getLocation(), item);
                }
                storage.clear();
            }

            // Extra Life: a flat charge, not a totem - no damage-prevention, the player really
            // does die. If a charge is available, consume it and they just respawn normally; if
            // not, they're eliminated to Spectator on respawn instead (see onPlayerRespawn).
            String group = Lootrun.balanceGroup(team);
            int charges = extraLifeCharges.getOrDefault(group, 0);
            if (charges > 0) {
                extraLifeCharges.put(group, charges - 1);
                if (charges - 1 <= 0) {
                    activeEffects.getOrDefault(group, Set.of()).remove("EXTRA_LIFE");
                }
                p.sendMessage(Component.text("Extra Life used! You'll respawn normally.", NamedTextColor.GOLD));
                Bukkit.broadcastMessage(ChatColor.GOLD + p.getName() + " used an Extra Life!");
            } else {
                eliminatedRunners.add(p.getUniqueId());
            }
        }

        boolean keepInv = activeEffects.getOrDefault(team, Set.of()).contains("KEEP_INVENTORY_BUFF")
                || activeEffects.getOrDefault(team, Set.of()).contains("HUNTER_INSURANCE");
        if (keepInv) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            // Their whole inventory (infinibundle included) survives death as-is - don't hand
            // them a second one on respawn, see onPlayerRespawn below.
            keptInventoryLastDeath.add(p.getUniqueId());
        } else {
            keptInventoryLastDeath.remove(p.getUniqueId());
        }

        String hunterVictimTeam = Lootrun.isHunterSide(team) ? team : null;
        if (hunterVictimTeam != null && activeEffects.getOrDefault(hunterVictimTeam, Set.of()).contains("RESPAWN_COOLDOWN")) {
            respawnCoolingDown.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(ZappierGames.getInstance(), () -> {
                respawnCoolingDown.remove(p.getUniqueId());
                if (p.isOnline()) {
                    p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    p.sendMessage(Component.text("Your respawn cooldown is over!", NamedTextColor.GREEN));
                }
            }, 20L * 60L * 3L); // 3 minutes
        }
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (ZappierGames.gameMode != ZappierGames.LOOTRUN) return;
        Player p = event.getPlayer();
        if (respawnCoolingDown.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(ZappierGames.getInstance(), () -> p.setGameMode(org.bukkit.GameMode.SPECTATOR));
        }

        if (eliminatedRunners.remove(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(ZappierGames.getInstance(), () -> {
                if (p.isOnline()) {
                    p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    p.sendMessage(Component.text("You've been eliminated - no Extra Lives remaining!", NamedTextColor.RED));
                }
            });
            return; // no point handing an eliminated runner a fresh infinibundle
        }

        // Only hand out a fresh Infinibundle if keep-inventory WASN'T active for that death - if
        // it was, their whole inventory (bundle included) survived as-is, so giving another one
        // here would just duplicate it.
        if (!keptInventoryLastDeath.remove(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(ZappierGames.getInstance(), () -> {
                if (p.isOnline()) LootHunt.giveInfinibundle(p);
            });
        }
    }
}
