package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.zappier.zappierGames.biomeparkour.BiomeParkour;
import org.zappier.zappierGames.loothunt.LootHunt;
import org.zappier.zappierGames.manhunt.Manhunt;
import org.zappier.zappierGames.skybattle.Skybattle;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

public class GUI {
    private final Inventory inv;
    private final String type;
    // In GUI.java, you can define these as fields or just hardcode the strings
    public static final String MANHUNT_WHEEL_MENU = "Wheel of Manhunt Settings";
    public static final String MANHUNT_TWISTS_TOGGLE = "Twists - Toggle Enabled";
    public static final String MANHUNT_TWISTS_ROLLABLE = "Twists - Toggle Rollable";
    public static final String MANHUNT_TWISTS_RANDOMIZE = "Twists - Randomize";

    public GUI() {
        this.type = "MAIN";
        inv = Bukkit.createInventory(null, 27, "Gamemode Select");
        initializeMainMenu();
    }

    public GUI(String submenuType) {
        this.type = submenuType;
        inv = Bukkit.createInventory(null, 27, submenuType + " Menu");
        initializeSubmenu(submenuType);
    }

    private void initializeMainMenu() {
        inv.setItem(10, createGuiItem(Material.ENDER_EYE, 1, "§6Manhunt",
                "§7Compete to hunt or be hunted!", "§aClick to configure."));
        inv.setItem(11, createGuiItem(Material.DIAMOND, 1, "§bLoothunt",
                "§7Gather resources to win!", "§aClick to configure."));
        inv.setItem(12, createGuiItem(Material.GRASS_BLOCK, 1, "§aBiome Parkour",
                "§7Race through diverse biomes!", "§aClick to configure."));
        inv.setItem(13, createGuiItem(Material.CHEST, 1, "§cSurvival Games",
                "§7Last player standing wins!", "§aClick to configure."));
        inv.setItem(14, createGuiItem(Material.DIAMOND_SWORD, 1, "§9Skybattle",
                "§7Fight in the skies!", "§aClick to configure."));
        inv.setItem(15, createGuiItem(Material.LEATHER_BOOTS, 1, "§eParkour Race",
                "§7Fastest parkourer wins!", "§aClick to configure."));

        ItemStack filler = createFillerItem();
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private void initializeSubmenu(String submenuType) {
        if (submenuType.equals("Manhunt")) {
            inv.setItem(10, createGuiItem(Material.BOOK, 1, "§eSet Game Mode",
                    "§7Current: " + getGameModeName(), "§aClick to select mode."));
            inv.setItem(11, createGuiItem(Material.MAP, 1, "§bSet Border Size",
                    "§7Current: " + ZappierGames.borderSize, "§aClick to select size."));
            inv.setItem(12, createGuiItem(Material.GREEN_BANNER, 1, "§aJoin Team",
                    "§7Join a team for Manhunt.", "§aClick to select."));
            inv.setItem(13, createGuiItem(Material.COMPASS, 1, "§9Compass Settings",
                    "§7Announce on Track: " + (Manhunt.shoutHunterTarget == 1 ? "On" : "Off"),
                    "§7Show Dimensions: " + (Manhunt.showTrackerDimension == 1 ? "On" : "Off"),
                    "§aClick to configure."));
            inv.setItem(14, createGuiItem(Material.LAPIS_LAZULI, 1, "§2Presidential Settings",
                    "§7Change presidential settings.", "§aClick to select."));
            inv.setItem(15, createGuiItem(Material.CLOCK, 1, "§2Manhunt Wheel Options",
                    "§7Configure random modifiers for your manhunt.", "§aClick to select."));
            inv.setItem(16, createGuiItem(Material.EMERALD, 1, "§2Start Manhunt",
                    "§7Start the game with current settings!", "§aClick to begin."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
        } else if (submenuType.equals(MANHUNT_WHEEL_MENU)) {
            inv.setItem(11, createGuiItem(Material.TNT, 1, "§cRandomize Twists!",
                    "§7Click to announce", "§7and roll random twists!", " ", "§eJust announces — no auto-roll"));

            inv.setItem(13, createGuiItem(Material.COMPARATOR, 1, "§eToggle Twists (Enabled)",
                    "§7Manually turn twists", "§7on/off", " ", "§aCurrent page: 1"));

            inv.setItem(15, createGuiItem(Material.REPEATER, 1, "§6Toggle Rollable",
                    "§7Decide which twists", "§7can be randomly selected", " ", "§aCurrent page: 1"));

            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu"));
        } else if (submenuType.contains(MANHUNT_TWISTS_TOGGLE) || submenuType.contains(MANHUNT_TWISTS_ROLLABLE)) {
            boolean isToggleEnabled = submenuType.contains(MANHUNT_TWISTS_TOGGLE);
            String titlePart = isToggleEnabled ? "Enabled" : "Rollable";

            // We show 7 twists per page (slots 10–16)
            //Bukkit.broadcastMessage(submenuType);
            //Bukkit.broadcastMessage("" + submenuType.split(" ")[submenuType.split(" ").length - 1]);
            int page = 1;
            try {
                page = parseInt(submenuType.split(" ")[submenuType.split(" ").length - 1]);
            } catch (Exception e) {

            }

            int startIndex = (page - 1) * 7;
            int endIndex = Math.min(startIndex + 7, Manhunt.manhuntTwists.length);

            for (int i = startIndex, slot = 10; i < endIndex; i++, slot++) {
                Manhunt.manhuntTwist twist = Manhunt.manhuntTwists[i];

                Material displayMat = twist.mat;
                String statusColor = isToggleEnabled
                        ? (twist.enabled  ? "§a" : "§c")
                        : (twist.rollable ? "§a" : "§c");

                String statusText = isToggleEnabled
                        ? (twist.enabled  ? "§aENABLED" : "§cDISABLED")
                        : (twist.rollable ? "§aROLLABLE" : "§cNOT ROLLABLE");

                inv.setItem(slot, createGuiItem(displayMat, 1,
                        "§f" + twist.name,
                        "§7" + twist.desc1,
                        "§7" + twist.desc2,
                        " ",
                        statusColor + "Current: " + statusText,
                        "§aClick to toggle"));
            }

            // Navigation / info
            if (page > 1) {
                inv.setItem(9, createGuiItem(Material.ARROW, 1, "§ePrevious Page"));
            }
            inv.setItem(17, createGuiItem(Material.ARROW, 1, "§eNext Page"));

            inv.setItem(4, createGuiItem(Material.BOOK, 1, "§6" + titlePart + " Status",
                    "§7Green = active", "§7Red = inactive"));

            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));
        } else if (submenuType.equals(MANHUNT_TWISTS_RANDOMIZE)) {
            inv.setItem(13, createGuiItem(Material.TNT, 1, "§c§lRANDOMIZE TWISTS!",
                    "§7Click to broadcast:", " ", "§eRandomizing twists!"));

            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));
        } else if (submenuType.equals("Loothunt")) {
            inv.setItem(10, createGuiItem(Material.GREEN_BANNER, 1, "§aJoin Team",
                    "§7Join a team for Loothunt.", "§aClick to select."));
            inv.setItem(11, createGuiItem(Material.CLOCK, 1, "§bSet Duration",
                    "§7Set the game duration.", "§aClick to select."));
            inv.setItem(12, createGuiItem(Material.EMERALD, 1, "§2Start Loothunt",
                    "§7Start the game with current settings!", "§aClick to begin."));
            inv.setItem(13, createGuiItem(Material.PLAYER_HEAD, 1, "§eView Endgame Scores",
                    "§7View scores from the last game.", "§aClick to view."));
            inv.setItem(15, createGuiItem(LootHunt.noPvP ? Material.POPPY : Material.IRON_SWORD, 1, "§eToggle PvP",
                    "§7PvP (Currently " + (LootHunt.noPvP ? "Off" : "On") + ")", "§aClick to toggle."));
            if (!LootHunt.paused) {
                inv.setItem(14, createGuiItem(Material.CAMPFIRE, 1, "§ePause Game",
                        "§7Pause loothunt.", "§aClick to pause."));
            } else {
                inv.setItem(14, createGuiItem(Material.SOUL_CAMPFIRE, 1, "§eUnpause Game",
                        "§7Unpause loothunt.", "§aClick to unpause."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
        } else if (submenuType.equals("Loothunt Team Selection")) {
            String[] teamNames = {"RED", "BLUE", "GREEN", "DARK_GREEN", "GOLD", "WHITE", "AQUA", "LIGHT_PURPLE", "DARK_PURPLE", "YELLOW"};
            Material[] teamWools = {
                    Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL, Material.GREEN_WOOL,
                    Material.ORANGE_WOOL, Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL,
                    Material.MAGENTA_WOOL, Material.PURPLE_WOOL, Material.YELLOW_WOOL
            };

            for (int i = 0; i < teamNames.length; i++) {
                inv.setItem(10 + i, createGuiItem(teamWools[i], 1, "§a" + teamNames[i],
                        "§7Join " + teamNames[i] + " team.", "§aClick to join."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Loothunt menu."));
        } else if (submenuType.equals("Loothunt Duration")) {
            inv.setItem(10, createGuiItem(Material.CLOCK, 1, "§b10 Minutes",
                    "§7Short game.", "§aClick to set."));
            inv.setItem(11, createGuiItem(Material.CLOCK, 1, "§b20 Minutes",
                    "§7Medium game.", "§aClick to set."));
            inv.setItem(12, createGuiItem(Material.CLOCK, 1, "§b30 Minutes",
                    "§7Long game.", "§aClick to set."));
            inv.setItem(13, createGuiItem(Material.CLOCK, 1, "§b60 Minutes",
                    "§7Epic game.", "§aClick to set."));
            inv.setItem(14, createGuiItem(Material.CLOCK, 1, "§b90 Minutes",
                    "§WLegendary game.", "§aClick to set."));
            inv.setItem(15, createGuiItem(Material.CLOCK, 1, "§b120 Minutes",
                    "§LWell-Done game.", "§aClick to set."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Loothunt menu."));
        } else if (submenuType.equals("Loothunt Endgame Scores")) {
            // Add player heads for participants
            List<String> participants = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            for (int i = 0; i < participants.size() && i < 17; i++) { // Limit to slots 10-26
                String playerName = participants.get(i);
                inv.setItem(10 + i, createPlayerHead(playerName));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Loothunt menu."));
        } else if (submenuType.equals("Game Mode")) {
            inv.setItem(10, createGuiItem(Material.BOOK, 1, "§6Standard",
                    "§7Beat the game, or die trying.", "§aClick to select."));
            inv.setItem(11, createGuiItem(Material.ZOMBIE_HEAD, 1, "§2Infection",
                    "§7Beat the game, don't get infected!", "§aClick to select."));
            inv.setItem(12, createGuiItem(Material.SHIELD, 1, "§1Kill President(s)",
                    "§7Protect the president!", "§aClick to select."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu."));
        } else if (submenuType.equals("Border Size")) {
            inv.setItem(10, createGuiItem(Material.PAPER, 1, "§b1000 Blocks",
                    "§7Small border.", "§aClick to set."));
            inv.setItem(11, createGuiItem(Material.PAPER, 1, "§b2500 Blocks",
                    "§7Medium border.", "§aClick to set."));
            inv.setItem(12, createGuiItem(Material.PAPER, 1, "§b5000 Blocks",
                    "§7Large border.", "§aClick to set."));
            inv.setItem(13, createGuiItem(Material.PAPER, 1, "§b7500 Blocks",
                    "§7Extra large border.", "§aClick to set."));
            inv.setItem(14, createGuiItem(Material.PAPER, 1, "§b10000 Blocks",
                    "§7Huge border.", "§aClick to set."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu."));
        } else if (submenuType.equals("Team Selection")) {
            for (int i = 0; i < ZappierGames.teamList.length; i++) {
                inv.setItem(10 + i, createGuiItem(Material.GREEN_BANNER, 1, "§a" + ZappierGames.teamList[i],
                        "§7Join this team.", "§aClick to join."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu."));
        } else if (submenuType.equals("Compass Settings")) {
            inv.setItem(10, createGuiItem(Material.PAPER, 1, "§eAnnounce on Track",
                    "§7Current: " + (Manhunt.shoutHunterTarget == 1 ? "On" : "Off"), "§aClick to toggle."));
            inv.setItem(11, createGuiItem(Material.PAPER, 1, "§eShow Dimensions",
                    "§7Current: " + (Manhunt.showTrackerDimension == 1 ? "On" : "Off"), "§aClick to toggle."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu."));
        } else if (submenuType.equals("Presidential Settings")) {
            inv.setItem(10, createGuiItem((Manhunt.presidentDeathLink == 1 ? Material.SKELETON_SKULL : Material.TOTEM_OF_UNDYING), 1, "§ePresidential Death Link",
                    "§7Current: " + (Manhunt.presidentDeathLink == 1 ? "On" : "Off"), "§aWhen on, if one president dies, all die."));
            inv.setItem(11, createGuiItem((Manhunt.bodyguardRespawn == 1 ? Material.SOUL_CAMPFIRE : Material.WITHER_SKELETON_SKULL), 1, "§eCan Bodyguards Respawn",
                    "§7Current: " + (Manhunt.bodyguardRespawn == 1 ? "On" : "Off"), "§aClick to toggle."));
            inv.setItem(12, createGuiItem(Material.GOLDEN_APPLE, 1, "§eBodyguard HP bonus.",
                    "§7Current: " + Manhunt.bodyguardHpBonus, "§aClick to increment."));
            inv.setItem(13, createGuiItem(Material.IRON_CHESTPLATE, 1, "§ePresident Can Wear Armor.",
                    "§7Current: " + (Manhunt.presidentWearArmor == 1 ? "On" : "Off"), "§aClick to toggle."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu."));
        } else if (submenuType.equals("Skybattle")) {
            inv.setItem(10, createGuiItem(Material.GREEN_BANNER, 1, "§aJoin Team",
                    "§7Join a team for Skybattle.", "§aClick to select."));
            inv.setItem(11, createGuiItem(Material.BLAZE_POWDER, 1, "§eTwists",
                    "§7Enable/disable game twists.", "§aClick to configure."));
            inv.setItem(12, createGuiItem(Material.MAP, 1, "§bSelect Map",
                    "§7Choose your battlefield.", "§aClick to select."));
            inv.setItem(13, createGuiItem(Material.EMERALD, 1, "§2Start Skybattle",
                    "§7Start the game in the void world!", "§aClick to begin."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
        } else if (submenuType.equals("Skybattle Team Selection")) {
            String[] teamNames = {"RED", "BLUE", "GREEN", "DARK_GREEN", "GOLD", "WHITE", "AQUA", "LIGHT_PURPLE", "DARK_PURPLE", "YELLOW"};
            Material[] teamWools = {
                    Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL, Material.GREEN_WOOL,
                    Material.ORANGE_WOOL, Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL,
                    Material.MAGENTA_WOOL, Material.PURPLE_WOOL, Material.YELLOW_WOOL
            };

            for (int i = 0; i < teamNames.length; i++) {
                inv.setItem(10 + i, createGuiItem(teamWools[i], 1, "§a" + teamNames[i],
                        "§7Join " + teamNames[i] + " team.", "§aClick to join."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Skybattle menu."));
        } else if (submenuType.equals("Skybattle Twists")) {
            String[] twistNames = {
                    "§eFast TNT", "§eWIP 1", "§eWIP 2", "§eWIP 3", "§eWIP 4",
                    "§eWIP 5", "§eWIP 6", "§eWIP 7", "§eWIP 8", "§eWIP 9",
                    "§eWIP 10", "WIP 11", "WIP 12", "WIP 13", "WIP 14", "WIP 15"
            };
            for (int i = 0; i < 8; i++) { // Assuming 8 twists
                String status = (Skybattle.TWISTS[i] > 0) ? "§aON" : "§cOFF";
                inv.setItem(10 + i, createGuiItem(Material.BLAZE_POWDER, 1, twistNames[i],
                        "§7Current: " + status, "§aClick to toggle."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Skybattle menu."));
        } else if (submenuType.equals("Skybattle Map Selection")) {
            inv.setItem(10, createGuiItem(Material.MAP, 1, "§bSlushly's Cake",
                    "§7The classic."));
            inv.setItem(11, createGuiItem(Material.MAP, 1, "§bWIP",
                    "§7WIP", "§7§oWIP"));
            inv.setItem(12, createGuiItem(Material.MAP, 1, "§bWIP",
                    "§7WIP.", "§7§o(WIP)"));
            inv.setItem(13, createGuiItem(Material.MAP, 1, "§bWIP",
                    "§7WIP.", "§7§o(WIP)"));
            inv.setItem(14, createGuiItem(Material.MAP, 1, "§bWIP",
                    "§7WIP", "§7§o(WIP)"));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Skybattle menu."));
        } else if (submenuType.equals("Parkour Race")) {
            inv.setItem(13, createGuiItem(Material.GOLD_BLOCK, 1, "§e" + submenuType + " Settings",
                    "§7Resume Parkour Race", "§a(Only activates block switch functionality)"));
            inv.setItem(14, createGuiItem(Material.EMERALD, 1, "§2Start Parkour Race",
                    "§7Start the game with current settings!", "§aClick to begin."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
        } else if (submenuType.equals("Biome Parkour")) {
            inv.setItem(10, createGuiItem(Material.PAPER, 1, "§bBorder Size",
                    "§7Current: " + BiomeParkour.borderSize + " blocks",
                    "§aClick to change."));

            inv.setItem(11, createGuiItem(Material.BLAZE_POWDER, 1, "§eStarting Speed",
                    "§7Current: " + String.format("%.3f", BiomeParkour.baseBorderSpeed),
                    "§aClick to adjust."));

            inv.setItem(12, createGuiItem(Material.FIREWORK_ROCKET, 1, "§6Speed Increase Rate",
                    "§7Current: " + String.format("%.4f", BiomeParkour.speedIncreaseRate) + "/tick",
                    "§aClick to adjust."));

            inv.setItem(13, createGuiItem(Material.CLOCK, 1, "§bGame Duration",
                    "§7Current: " + BiomeParkour.maxGameMinutes + " minutes",
                    "§aClick to set."));

            inv.setItem(14, createGuiItem(Material.TOTEM_OF_UNDYING, 1, "§cRespawn Penalty",
                    "§7Lose 100 points on respawn: " + (BiomeParkour.respawnLosePoints ? "§aYes" : "§cNo"),
                    "§aClick to toggle."));

            inv.setItem(15, createGuiItem(Material.EMERALD, 1, "§2Start Biome Parkour",
                    "§7Start the game with current settings!",
                    "§aClick to begin."));

            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
        } else if (submenuType.equals("Biome Parkour Border Size")) {
            int[] sizes = {500, 1000, 2000, 3000, 5000};
            for (int i = 0; i < sizes.length; i++) {
                inv.setItem(10 + i, createGuiItem(Material.PAPER, 1, "§b" + sizes[i] + " blocks",
                        "§aClick to set border size."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));

        } else if (submenuType.equals("Biome Parkour Speed")) {
            double[] speeds = {0.05, 0.1, 0.15, 0.2, 0.3};
            for (int i = 0; i < speeds.length; i++) {
                inv.setItem(10 + i, createGuiItem(Material.BLAZE_POWDER, 1, "§e" + String.format("%.3f", speeds[i]),
                        "§aClick to set starting speed."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));

        } else if (submenuType.equals("Biome Parkour Acceleration")) {
            double[] rates = {0.0, 0.002, 0.005, 0.01, 0.02};
            for (int i = 0; i < rates.length; i++) {
                inv.setItem(10 + i, createGuiItem(Material.FIREWORK_ROCKET, 1, "§6+" + String.format("%.4f", rates[i]) + "/tick",
                        rates[i] == 0 ? "§7No acceleration" : "",
                        "§aClick to set speed increase."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));

        } else if (submenuType.equals("Biome Parkour Duration")) {
            int[] mins = {10, 20, 30, 45, 60, 120};
            for (int i = 0; i < mins.length; i++) {
                inv.setItem(10 + i, createGuiItem(Material.CLOCK, 1, "§b" + mins[i] + " minutes",
                        "§aClick to set game duration."));
            }
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));
        } else if (submenuType.equals("Survival Games")) {
            inv.setItem(13, createGuiItem(Material.EMERALD, 1, "§2Start Survival Games",
                    "§7Start the game with current settings!", "§aClick to begin."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack"));
        } else {
            inv.setItem(13, createGuiItem(Material.BOOK, 1, "§e" + submenuType + " Settings",
                    "§7Placeholder for configuration.", "§aWork in progress!"));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
        }

        ItemStack filler = createFillerItem();
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    public String getGameModeName() {
        switch (ZappierGames.gameMode) {
            case 1: return "Standard";
            case 2: return "Infection";
            case 3: return "Kill President(s)";
            default: return "Unknown";
        }
    }

    private ItemStack createGuiItem(final Material material, final int amount, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, amount);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setDisplayName("§e" + playerName);
        meta.setLore(Arrays.asList("§7View endgame scores for " + playerName, "§aClick to view."));
        meta.setOwner(playerName); // Set the head's skin to the player's
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createFillerItem() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        return filler;
    }

    public void open(Player player) {
        player.openInventory(inv);
    }

    public String getType() {
        return type;
    }
}