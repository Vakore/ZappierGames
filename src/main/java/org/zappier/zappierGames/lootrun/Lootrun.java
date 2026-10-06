package org.zappier.zappierGames.lootrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Team;
import org.zappier.zappierGames.ZappierGames;
import org.zappier.zappierGames.loothunt.InfinibundleListener;
import org.zappier.zappierGames.loothunt.LootHunt;

import java.util.*;
import java.util.stream.Collectors;

import static org.zappier.zappierGames.skybattle.Skybattle.getPlayerTeam;

/**
 * Lootrun: Manhunt-style Runners-vs-Hunters teams, but scored like LootHunt and playable with a
 * spendable point economy (see LootrunShop). Reuses the same scoreboard teams as Manhunt
 * (Runners/Hunters/Runner_Suppliers/Hunter_Suppliers), so /manhunt jointeam (or /lootrun jointeam)
 * works interchangeably.
 *
 * Every player gets their OWN individual infinibundle and collection score (InfinibundleListener
 * keys storage per-player instead of per-team while this mode is active - see its getTeamName()),
 * so runners/hunters on the same team can't funnel items to each other across distance. A team's
 * spendable balance is the live SUM of everyone currently on that side's raw item+collection
 * score, minus however much that side has ever spent in the shop - see getBalance() below.
 */
public class Lootrun {

    public static final String[] RUNNER_SIDE = {"Runners", "Runner_Suppliers"};
    public static final String[] HUNTER_SIDE = {"Hunters", "Hunter_Suppliers"};

    // Cumulative amount each balance group (Runners/Hunters) has ever spent in the shop. A
    // group's spendable balance is (their current live total score) - (this). Deliberately NOT a
    // stored/credited ledger like LootHunt's scoring - that would let the same physical item
    // double-credit a team if it's picked up by one player then handed to a teammate (each
    // player's own "score" would independently rise when they briefly hold it). Computing the
    // *current* total fresh each time closes that off, since the item's value only ever counts
    // once no matter who currently holds it. This also means balance can go negative, if a team
    // spends more than their current holdings are worth and then loses those holdings.
    private static final Map<String, Double> totalSpent = new HashMap<>();

    private static int tickCounter = 0;

    /**
     * Collapses every sub-team onto exactly one of two shared balance pools: Hunters (Hunters +
     * Hunter_Suppliers) and Runners (Runners + Runner_Suppliers + President + Bodyguard, and
     * anything else/unaffiliated). Scoring is still computed per-player, but what a team can
     * actually spend is the SUM of everyone on that side - there is no separate supplier pool.
     * Every read/write goes through this, so callers never need to care which exact sub-team a
     * given player happens to be on.
     */
    public static String balanceGroup(String team) {
        return isHunterSide(team) ? "Hunters" : "Runners";
    }

    /** The live current total raw item+collection score across every online player currently on
     * the given team's side - NOT an accumulated/credited value. Recomputed fresh every call. */
    public static double computeCurrentGroupScore(String team) {
        String group = balanceGroup(team);
        double total = 0.0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            String playerTeam = getPlayerTeam(p);
            if (playerTeam.isEmpty() || !balanceGroup(playerTeam).equals(group)) continue;

            Map<String, List<LootHunt.ItemEntry>> personal = new HashMap<>();
            LootHunt.processContainer(personal, Arrays.asList(p.getInventory().getContents()), "Inventory");
            LootHunt.processContainer(personal, InfinibundleListener.getTeamStorage(p.getName()), "Infinibundle");

            total += personal.values().stream()
                    .flatMap(List::stream)
                    .mapToDouble(e -> e.points)
                    .sum();
            total += LootHunt.calculateCollectionBonus(personal.keySet());
        }

        return total;
    }

    /** Current spendable balance: live total score for this side, minus whatever it's ever spent.
     * Can go negative if the side spent more than its current holdings are worth and then lost
     * those holdings (e.g. a death without keep-inventory). */
    public static double getBalance(String team) {
        String group = balanceGroup(team);
        return computeCurrentGroupScore(team) - totalSpent.getOrDefault(group, 0.0);
    }

    public static boolean trySpend(String team, double amount) {
        if (getBalance(team) < amount) return false;
        totalSpent.merge(balanceGroup(team), amount, Double::sum);
        return true;
    }

    public static void refund(String team, double amount) {
        totalSpent.merge(balanceGroup(team), -amount, Double::sum);
    }

    public static boolean isRunnerSide(String team) {
        return "Runners".equals(team) || "Runner_Suppliers".equals(team);
    }

    public static boolean isHunterSide(String team) {
        return "Hunters".equals(team) || "Hunter_Suppliers".equals(team);
    }

    /** Every currently-online player on either team belonging to the given side. */
    public static List<Player> onlinePlayersOnSide(boolean runnerSide) {
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String team = getPlayerTeam(p);
            if (runnerSide ? isRunnerSide(team) : isHunterSide(team)) {
                players.add(p);
            }
        }
        return players;
    }

    /** How many hunters (Hunters + Hunter_Suppliers) are currently online - drives shop price
     * scaling per the price table (1-5 hunter columns, clamped to that range). */
    public static int currentHunterCount() {
        return onlinePlayersOnSide(false).size();
    }

    public static void start() {
        totalSpent.clear();
        LootrunShop.clearAll();
        InfinibundleListener.clearAll();
        tickCounter = 0;

        ZappierGames.resetPlayers(true, true);
        ZappierGames.gameMode = ZappierGames.LOOTRUN;
        ZappierGames.globalBossBar.setVisible(false); // LootHunt's timer bar otherwise lingers on screen

        for (World world : Bukkit.getWorlds()) {
            // Requirements: locator bar off, keep inventory off, both by default
            world.setGameRule(GameRules.LOCATOR_BAR, false);
            world.setGameRule(GameRules.KEEP_INVENTORY, false);
            try {
                world.setTime(0);
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().info("Skipping setTime() for world '" + world.getName() + "' - it has no world clock.");
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            p.sendTitle(ChatColor.GOLD + "Lootrun", ChatColor.GOLD + "Loot, hunt, and spend.", 10, 70, 20);
            p.sendMessage(Component.text("Use /shop to spend your team's points on buffs and nerfs!", NamedTextColor.GOLD));

            LootHunt.giveInfinibundle(p);

            String team = getPlayerTeam(p);
            if (isHunterSide(team)) {
                giveHunterCompass(p);
            }
        }

        Bukkit.broadcast(Component.text("Lootrun has started!", NamedTextColor.GOLD));
    }

    public static void end() {
        ZappierGames.gameMode = -1;
        Bukkit.broadcast(Component.text("Lootrun has ended.", NamedTextColor.GOLD));
    }

    private static void giveHunterCompass(Player p) {
        ItemStack compass = new ItemStack(org.bukkit.Material.COMPASS);
        compass.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text("Hunter's Compass", NamedTextColor.RED));
        compass.setItemMeta(meta);
        p.getInventory().addItem(compass);
    }

    /** Called every tick while Lootrun is the active game mode (see ZappierGames' main loop). */
    public static void run() {
        tickCounter++;

        // Point every hunter's compass at the nearest runner, live
        updateHunterCompasses();

        LootrunShop.tick();

        if (tickCounter % SCORE_DISPLAY_INTERVAL == 0) {
            updateScoreDisplays();
        }
    }

    private static final int SCORE_DISPLAY_INTERVAL = 20; // once a second

    /**
     * Live team-points sidebar + tab footer for spectators, mirroring LootHunt's live tracker
     * (which only runs during LOOTHUNT, so Lootrun never had any score display at all before).
     */
    private static void updateScoreDisplays() {
        List<Player> spectators = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) spectators.add(p);
        }
        if (spectators.isEmpty()) return;

        List<String> teams = List.of("Runners", "Hunters");
        List<Map.Entry<String, Double>> sorted = new ArrayList<>();
        for (String t : teams) sorted.add(new AbstractMap.SimpleEntry<>(t, getBalance(t)));
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Component tabFooter = Component.text("Lootrun Points", NamedTextColor.GOLD, TextDecoration.BOLD);
        for (Map.Entry<String, Double> e : sorted) {
            tabFooter = tabFooter.append(Component.newline())
                    .append(Component.text(e.getKey() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.0f", e.getValue()), NamedTextColor.GREEN));
        }
        Component tabHeader = Component.text("=== Lootrun ===", NamedTextColor.GOLD);

        for (Player spec : spectators) {
            org.bukkit.scoreboard.Scoreboard board = LootHunt.spectatorBoards.computeIfAbsent(spec.getUniqueId(),
                    k -> Bukkit.getScoreboardManager().getNewScoreboard());

            org.bukkit.scoreboard.Objective obj = board.getObjective("lootrun_sidebar");
            if (obj == null) {
                obj = board.registerNewObjective("lootrun_sidebar", org.bukkit.scoreboard.Criteria.DUMMY,
                        Component.text("Lootrun - Points", NamedTextColor.GOLD));
                obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            }

            Set<String> kept = new HashSet<>();
            for (Map.Entry<String, Double> e : sorted) {
                obj.getScore(e.getKey()).setScore((int) Math.round(e.getValue()));
                kept.add(e.getKey());
            }
            for (String entry : new HashSet<>(board.getEntries())) {
                if (!kept.contains(entry)) obj.getScore(entry).resetScore();
            }

            if (spec.getScoreboard() != board) spec.setScoreboard(board);
            spec.sendPlayerListHeaderAndFooter(tabHeader, tabFooter);
        }
    }

    private static void updateHunterCompasses() {
        List<Player> hunters = onlinePlayersOnSide(false);
        List<Player> runners = onlinePlayersOnSide(true);
        if (runners.isEmpty()) return;

        for (Player hunter : hunters) {
            Player nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Player runner : runners) {
                if (!runner.getWorld().equals(hunter.getWorld())) continue;
                double dist = runner.getLocation().distanceSquared(hunter.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = runner;
                }
            }
            if (nearest != null) {
                hunter.setCompassTarget(nearest.getLocation());
            }
        }
    }

    public static String formatBalanceLine(String viewerTeam) {
        return "Runners: " + String.format("%.0f", getBalance("Runners"))
                + "  |  Hunters: " + String.format("%.0f", getBalance("Hunters"));
    }
}