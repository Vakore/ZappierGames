package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class GUI {
    private final Inventory inv;
    private final String type;

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
                    "§7Announce on Track: " + (ZappierGames.shoutHunterTarget == 1 ? "On" : "Off"),
                    "§7Show Dimensions: " + (ZappierGames.showTrackerDimension == 1 ? "On" : "Off"),
                    "§aClick to configure."));
            inv.setItem(14, createGuiItem(Material.EMERALD, 1, "§2Start Manhunt",
                    "§7Start the game with current settings!", "§aClick to begin."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to the main menu."));
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
                    "§7Current: " + (ZappierGames.shoutHunterTarget == 1 ? "On" : "Off"), "§aClick to toggle."));
            inv.setItem(11, createGuiItem(Material.PAPER, 1, "§eShow Dimensions",
                    "§7Current: " + (ZappierGames.showTrackerDimension == 1 ? "On" : "Off"), "§aClick to toggle."));
            inv.setItem(26, createGuiItem(Material.BARRIER, 1, "§cBack",
                    "§7Return to Manhunt menu."));
            // Add this to the Skybattle submenu case in initializeSubmenu:
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
                    "§eFast TNT",
                    "§eWIP 1",
                    "§eWIP 2",
                    "§eWIP 3",
                    "§eWIP 4",
                    "§eWIP 5",
                    "§eWIP 6",
                    "§eWIP 7",
                    "§eWIP 8",
                    "§eWIP 9",
                    "§eWIP 10",
                    "WIP 11",
                    "WIP 12",
                    "WIP 13",
                    "WIP 14",
                    "WIP 15"
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