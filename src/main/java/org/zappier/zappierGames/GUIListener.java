package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class GUIListener implements Listener {
    private final ZappierGames plugin;

    public GUIListener(ZappierGames plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if it's our GUI by title
        String title = event.getView().getTitle();
        if (!title.equals("Gamemode Select") && !title.endsWith(" Menu")) return;

        event.setCancelled(true); // Prevent item movement
        event.setCursor(null); // Clear cursor

        if (event.getCurrentItem() == null) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        int slot = event.getRawSlot();
        GUI gui;

        boolean validClick = true;

        // Handle main menu clicks
        if (title.equals("Gamemode Select")) {
            switch (slot) {
                case 10: // Manhunt
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                case 11: // Loothunt
                    gui = new GUI("Loothunt");
                    gui.open(player);
                    break;
                case 12: // Biome Parkour
                    gui = new GUI("Biome Parkour");
                    gui.open(player);
                    break;
                case 13: // Survival Games
                    gui = new GUI("Survival Games");
                    gui.open(player);
                    break;
                case 14: // Skybattle
                    gui = new GUI("Skybattle");
                    gui.open(player);
                    break;
                case 15: // Parkour Race
                    gui = new GUI("Parkour Race");
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Manhunt Menu")) {
            // Handle Manhunt menu clicks
            switch (slot) {
                case 10: // Set Game Mode
                    gui = new GUI("Game Mode");
                    gui.open(player);
                    break;
                case 11: // Set Border Size
                    gui = new GUI("Border Size");
                    gui.open(player);
                    break;
                case 12: // Join Team
                    gui = new GUI("Team Selection");
                    gui.open(player);
                    break;
                case 13: // Compass Settings
                    gui = new GUI("Compass Settings");
                    gui.open(player);
                    break;
                case 14: // Start Manhunt
                    Manhunt.start(ZappierGames.borderSize, (int) player.getLocation().getX(), (int) player.getLocation().getZ());
                    player.closeInventory();
                    break;
                case 26: // Back
                    gui = new GUI();
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Game Mode Menu")) {
            // Handle game mode selection
            switch (slot) {
                case 10: // Standard
                    ZappierGames.gameMode = 1;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode set to Standard.");
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                case 11: // Infection
                    ZappierGames.gameMode = 2;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode set to Infection.");
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                case 12: // Kill President(s)
                    ZappierGames.gameMode = 3;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode set to Kill President(s).");
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                case 26: // Back
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Border Size Menu")) {
            // Handle border size selection
            int borderSize = 0;
            switch (slot) {
                case 10:
                    borderSize = 1000;
                    break;
                case 11:
                    borderSize = 2500;
                    break;
                case 12:
                    borderSize = 5000;
                    break;
                case 13:
                    borderSize = 7500;
                    break;
                case 14:
                    borderSize = 10000;
                    break;
                case 26: // Back
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
            if (borderSize > 0) {
                ZappierGames.borderSize = borderSize;
                player.sendMessage(ChatColor.YELLOW + "Border set to " + borderSize + " blocks (" + ((int)(borderSize / 2)) + ") in each direction.");
                gui = new GUI("Manhunt");
                gui.open(player);
            }
        } else if (title.equals("Team Selection Menu")) {
            // Handle team selection
            if (slot == 26) { // Back
                gui = new GUI("Manhunt");
                gui.open(player);
            } else if (slot >= 10 && slot < 10 + ZappierGames.teamList.length) {
                String teamName = ZappierGames.teamList[slot - 10];
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    team.addEntry(player.getName());
                    player.sendMessage(ChatColor.YELLOW + "Joined team " + teamName);
                } else {
                    player.sendMessage(ChatColor.RED + "Team " + teamName + " does not exist!");
                }
                gui = new GUI("Manhunt");
                gui.open(player);
            } else {
                validClick = false;
            }
        } else if (title.equals("Compass Settings Menu")) {
            // Handle compass settings
            switch (slot) {
                case 10: // Announce on Track
                    ZappierGames.shoutHunterTarget = -ZappierGames.shoutHunterTarget;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "shoutHunterTarget set to " + ZappierGames.shoutHunterTarget);
                    gui = new GUI("Compass Settings");
                    gui.open(player);
                    break;
                case 11: // Show Dimensions
                    ZappierGames.showTrackerDimension = -ZappierGames.showTrackerDimension;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "showTrackerDimension set to " + ZappierGames.showTrackerDimension);
                    gui = new GUI("Compass Settings");
                    gui.open(player);
                    break;
                case 26: // Back
                    gui = new GUI("Manhunt");
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Skybattle Menu")) {
            switch (slot) {
            case 10: // Set Border Size
                gui = new GUI("Skybattle Border Size");
                gui.open(player);
                validClick = true;
                break;
            case 11: // Start Skybattle
                ZappierGames.gameMode = 4; // Skybattle gameMode
                World skybattleWorld = Bukkit.getWorld("skybattle_world");
                if (skybattleWorld != null) {
                    Skybattle.start(skybattleWorld, ZappierGames.borderSize);
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Skybattle started!");
                    validClick = true;
                } else {
                    player.sendMessage(ChatColor.RED + "Skybattle world not found!");
                }
                break;
            case 26: // Back
                gui = new GUI();
                gui.open(player);
                validClick = true;
                break;
            }
        } else if (title.equals("Skybattle Border Size Menu")) {
            int borderSize = 0;
            switch (slot) {
                case 10: borderSize = 100; break;
                case 11: borderSize = 200; break;
                case 12: borderSize = 500; break;
                case 26: // Back
                    gui = new GUI("Skybattle");
                    gui.open(player);
                    validClick = true;
                    break;
            }
            if (borderSize > 0) {
                ZappierGames.borderSize = borderSize;
                World skybattleWorld = Bukkit.getWorld("skybattle_world");
                if (skybattleWorld != null) {
                    skybattleWorld.getWorldBorder().setSize(borderSize);
                    skybattleWorld.getWorldBorder().setCenter(0, 0);
                }
                player.sendMessage(ChatColor.YELLOW + "Skybattle border set to " + borderSize + " blocks.");
                gui = new GUI("Skybattle");
                gui.open(player);
                validClick = true;
            }
        } else if (title.endsWith(" Menu")) {
            // Handle placeholder submenus
            if (slot == 26) { // Back
                gui = new GUI();
                gui.open(player);
            } else if (slot == 13) {
                player.sendMessage("§e" + title + " configuration coming soon!");
            } else {
                validClick = false;
            }
        } else {
            validClick = false;
        }

        // Play sound on valid click
        if (validClick) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals("Gamemode Select") && !event.getView().getTitle().endsWith(" Menu")) return;
        event.setCancelled(true); // Prevent dragging
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!event.getView().getTitle().equals("Gamemode Select") && !event.getView().getTitle().endsWith(" Menu")) return;
        player.setItemOnCursor(null); // Clear cursor on close
    }
}