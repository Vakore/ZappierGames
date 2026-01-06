package org.zappier.zappierGames;

import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.zappier.zappierGames.biomeparkour.BiomeParkour;
import org.zappier.zappierGames.loothunt.LootHunt;
import org.zappier.zappierGames.manhunt.Manhunt;
import org.zappier.zappierGames.skybattle.Skybattle;

import java.util.List;
import java.util.Map;

public class GUIListener implements Listener {
    private final ZappierGames plugin;

    public GUIListener(ZappierGames plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.equals("Gamemode Select") && !title.endsWith(" Menu")) return;

        event.setCancelled(true);
        event.setCursor(null);

        if (event.getCurrentItem() == null) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        int slot = event.getRawSlot();
        GUI gui;
        boolean validClick = true;

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
        } else if (title.equals("Loothunt Menu")) {
            switch (slot) {
                case 10: // Join Team
                    gui = new GUI("Loothunt Team Selection");
                    gui.open(player);
                    break;
                case 11: // Set Duration
                    gui = new GUI("Loothunt Duration");
                    gui.open(player);
                    break;
                case 12: // Start Loothunt
                    if (ZappierGames.loothuntDuration > 0) {
                        LootHunt.start(ZappierGames.loothuntDuration);
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "LootHunt started with duration " + ZappierGames.loothuntDuration + " minutes!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Please set a game duration first!");
                    }
                    break;
                case 13: // View Endgame Scores
                    gui = new GUI("Loothunt Endgame Scores");
                    gui.open(player);
                    break;
                case 14: // Toggle Pause
                    LootHunt.paused = !LootHunt.paused;
                    Bukkit.broadcastMessage(LootHunt.paused ? (ChatColor.RED + "Loothunt Paused") : (ChatColor.GREEN + "Loothunt Resumed"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        p.sendTitle(LootHunt.paused ? (ChatColor.RED + "Loothunt Paused") : (ChatColor.GREEN + "Loothunt Resumed"), "", 10, 70, 20);
                    }
                    player.closeInventory();
                    break;
                case 26: // Back
                    gui = new GUI();
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Loothunt Team Selection Menu")) {
            if (slot == 26) { // Back
                gui = new GUI("Loothunt");
                gui.open(player);
            } else if (slot >= 10 && slot <= 19) { // Team slots
                int teamIndex = slot - 10;
                String[] teamNames = {"RED", "BLUE", "GREEN", "DARK_GREEN", "GOLD", "WHITE", "AQUA", "LIGHT_PURPLE", "DARK_PURPLE", "YELLOW"};
                if (teamIndex < teamNames.length) {
                    String teamName = teamNames[teamIndex];
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    Team team = scoreboard.getTeam(teamName);

                    if (team != null) {
                        team.addEntry(player.getName());
                        Bukkit.broadcastMessage(player.getName().toUpperCase() + " has joined team " + teamName);
                        player.sendMessage(ChatColor.YELLOW + "Joined team " + getTeamColorLegacy(teamName) + teamName + ChatColor.YELLOW + "!");
                    } else {
                        team = scoreboard.registerNewTeam(teamName);
                        team.setAllowFriendlyFire(true);
                        NamedTextColor teamColor = getTeamColor(teamName);
                        team.color(teamColor);
                        String prefixInitial = getTeamPrefixInitial(teamName);
                        TextComponent coloredPrefix = (teamColor != null)
                                ? Component.text("[" + prefixInitial + "] ", teamColor)
                                : Component.text("[" + teamName + "] ", NamedTextColor.GRAY);
                        team.prefix(coloredPrefix);
                        team.addEntry(player.getName());
                        Bukkit.broadcastMessage(player.getName().toUpperCase() + " has joined team " + teamName);
                        player.sendMessage(ChatColor.YELLOW + "Created and joined team §c" + teamName + ChatColor.YELLOW + "!");
                    }
                    gui = new GUI("Loothunt");
                    gui.open(player);
                } else {
                    validClick = false;
                }
            } else {
                validClick = false;
            }
        } else if (title.equals("Loothunt Duration Menu")) {
            double duration = 0;
            switch (slot) {
                case 10:
                    duration = 10;
                    break;
                case 11:
                    duration = 20;
                    break;
                case 12:
                    duration = 30;
                    break;
                case 13:
                    duration = 60;
                    break;
                case 14:
                    duration = 90;
                    break;
                case 15:
                    duration = 120;
                    break;
                case 26: // Back
                    gui = new GUI("Loothunt");
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
            if (duration > 0) {
                ZappierGames.loothuntDuration = duration;
                player.sendMessage(ChatColor.YELLOW + "LootHunt duration set to " + duration + " minutes.");
                gui = new GUI("Loothunt");
                gui.open(player);
            }
        } else if (title.equals("Loothunt Endgame Scores Menu")) {
            if (slot == 26) { // Back
                gui = new GUI("Loothunt");
                gui.open(player);
            } else if (slot >= 10 && slot <= 26) { // Player heads
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() == Material.PLAYER_HEAD) {
                    SkullMeta meta = (SkullMeta) item.getItemMeta();
                    if (meta != null && meta.getOwner() != null) {
                        String targetPlayer = meta.getOwner().toUpperCase();
                        Map<String, List<LootHunt.ItemEntry>> playerItems = LootHunt.playerItemCounts.get(targetPlayer);
                        if (playerItems == null || playerItems.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "No items found for player " + targetPlayer + ". They may not have participated.");
                        } else {
                            player.sendMessage(ChatColor.GREEN + "=== Endgame Items for " + targetPlayer + " ===");
                            for (Map.Entry<String, List<LootHunt.ItemEntry>> entry : playerItems.entrySet()) {
                                String itemId = entry.getKey();
                                List<LootHunt.ItemEntry> items = entry.getValue();
                                for (LootHunt.ItemEntry itemEntry : items) {
                                    if (itemEntry.points == 0) {continue;}
                                    player.sendMessage(ChatColor.YELLOW + itemId + ": " +
                                            ChatColor.GRAY + "Quantity: " + itemEntry.quantity + ", " +
                                            "Points: " + String.format("%.1f", itemEntry.points));
                                }
                            }
                            player.sendMessage(ChatColor.GREEN + "=================================");
                        }
                    }
                } else {
                    validClick = false;
                }
            } else {
                validClick = false;
            }
        } else if (title.equals("Manhunt Menu")) {
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
            switch (slot) {
                case 10: // Announce on Track
                    ZappierGames.shoutHunterTarget = -ZappierGames.shoutHunterTarget;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "shoutHunterTarget set to " + ((ZappierGames.shoutHunterTarget == 1) ? "On" : "Off"));
                    gui = new GUI("Compass Settings");
                    gui.open(player);
                    break;
                case 11: // Show Dimensions
                    ZappierGames.showTrackerDimension = -ZappierGames.showTrackerDimension;
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "showTrackerDimension set to " + ((ZappierGames.showTrackerDimension == 1) ? "On" : "Off"));
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
                case 10: // Join Team
                    gui = new GUI("Skybattle Team Selection");
                    gui.open(player);
                    break;
                case 11: // Twists
                    gui = new GUI("Skybattle Twists");
                    gui.open(player);
                    break;
                case 12: // Select Map
                    gui = new GUI("Skybattle Map Selection");
                    gui.open(player);
                    break;
                case 13: // Start Skybattle
                    ZappierGames.gameMode = 10;
                    World skybattleWorld = Bukkit.getWorld("skybattle_world");
                    if (skybattleWorld != null) {
                        Skybattle.start(skybattleWorld, 5000);
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "Skybattle started!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Skybattle world not found!");
                    }
                    break;
                case 26: // Back
                    gui = new GUI();
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Skybattle Team Selection Menu")) {
            if (slot == 26) { // Back
                gui = new GUI("Skybattle");
                gui.open(player);
            } else if (slot >= 10 && slot <= 19) { // Team slots
                int teamIndex = slot - 10;
                String[] teamNames = {"RED", "BLUE", "GREEN", "DARK_GREEN", "GOLD", "WHITE", "AQUA", "LIGHT_PURPLE", "DARK_PURPLE", "YELLOW"};
                if (teamIndex < teamNames.length) {
                    String teamName = teamNames[teamIndex];
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    Team team = scoreboard.getTeam(teamName);

                    if (team != null) {
                        team.addEntry(player.getName());
                        Bukkit.broadcastMessage(player.getName().toUpperCase() + " has joined team " + teamName);
                        player.sendMessage(ChatColor.YELLOW + "Joined team §c" + teamName + ChatColor.YELLOW + "!");
                    } else {
                        team = scoreboard.registerNewTeam(teamName);
                        team.setAllowFriendlyFire(true);
                        NamedTextColor teamColor = getTeamColor(teamName);
                        team.color(teamColor);
                        String prefixInitial = getTeamPrefixInitial(teamName);
                        TextComponent coloredPrefix = (teamColor != null)
                                ? Component.text("[" + prefixInitial + "] ", teamColor)
                                : Component.text("[" + teamName + "] ", NamedTextColor.GRAY);
                        team.prefix(coloredPrefix);
                        team.addEntry(player.getName());
                        Bukkit.broadcastMessage(player.getName().toUpperCase() + " has joined team " + teamName);
                        player.sendMessage(ChatColor.YELLOW + "Created and joined team §c" + teamName + ChatColor.YELLOW + "!");
                    }
                    gui = new GUI("Skybattle");
                    gui.open(player);
                } else {
                    validClick = false;
                }
            } else {
                validClick = false;
            }
        } else if (title.equals("Skybattle Twists Menu")) {
            if (slot == 26) { // Back
                gui = new GUI("Skybattle");
                gui.open(player);
            } else if (slot >= 10 && slot <= 17) { // 8 twist slots
                int twistIndex = slot - 10;
                String[] twistNames = {
                        "§eFast TNT", "§eWIP 1", "§eWIP 2", "§eWIP 3", "§eWIP 4",
                        "§eWIP 5", "§eWIP 6", "§eWIP 7", "§eWIP 8", "§eWIP 9",
                        "§eWIP 10", "WIP 11", "WIP 12", "WIP 13", "WIP 14", "WIP 15"
                };
                Skybattle.TWISTS[twistIndex] = -Skybattle.TWISTS[twistIndex];
                String status = (Skybattle.TWISTS[twistIndex] > 0) ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                player.sendMessage(ChatColor.YELLOW + "Twist " + twistNames[twistIndex] + " set to " + status + "!");
                gui = new GUI("Skybattle Twists");
                gui.open(player);
            } else {
                validClick = false;
            }
        } else if (title.equals("Skybattle Map Selection Menu")) {
            if (slot == 26) { // Back
                gui = new GUI("Skybattle");
                gui.open(player);
            } else if (slot >= 10 && slot <= 14) {
                player.sendMessage(ChatColor.GOLD + "Map selection is §cWIP§r§6!");
            } else {
                validClick = false;
            }
        } else if (title.equals("Parkour Race Menu")) {
            switch (slot) {
                case 13: // Resume Parkour Race
                    ZappierGames.gameMode = 20;
                    ParkourRace.startTime = -1;
                    break;
                case 14: // Start Parkour Race
                    ZappierGames.gameMode = 20;
                    World skybattleWorld = Bukkit.getWorld("skybattle_world");
                    if (skybattleWorld != null) {
                        ParkourRace.start(skybattleWorld);
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "Parkour Race started!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Skybattle world not found!");
                    }
                    break;
                case 26: // Back
                    gui = new GUI();
                    gui.open(player);
                    break;
                default:
                    validClick = false;
            }
        } else if (title.equals("Biome Parkour Menu")) {
            switch (slot) {
                case 10: // Border Size
                    gui = new GUI("Biome Parkour Border Size");
                    gui.open(player);
                    break;
                case 11: // Starting Speed
                    gui = new GUI("Biome Parkour Speed");
                    gui.open(player);
                    break;
                case 12: // Speed Increase
                    gui = new GUI("Biome Parkour Acceleration");
                    gui.open(player);
                    break;
                case 13: // Duration
                    gui = new GUI("Biome Parkour Duration");
                    gui.open(player);
                    break;
                case 14: // Respawn Penalty Toggle
                    BiomeParkour.respawnLosePoints = !BiomeParkour.respawnLosePoints;
                    player.sendMessage(ChatColor.YELLOW + "Respawn point loss: " + (BiomeParkour.respawnLosePoints ? "§aEnabled" : "§cDisabled"));
                    gui = new GUI("Biome Parkour");
                    gui.open(player);
                    break;
                case 15: // Start Game
                    BiomeParkour.start(
                            BiomeParkour.borderSize,
                            player.getLocation().getX(),
                            player.getLocation().getZ(),
                            BiomeParkour.baseBorderSpeed,
                            BiomeParkour.speedIncreaseRate,
                            BiomeParkour.respawnLosePoints,
                            BiomeParkour.maxGameMinutes
                    );
                    player.closeInventory();
                    break;
                case 26: // Back
                    gui = new GUI();
                    gui.open(player);
                    break;
            }

        } else if (title.equals("Biome Parkour Border Size Menu")) {
            int[] sizes = {50, 100, 150, 200, 250};
            if (slot >= 10 && slot < 10 + sizes.length) {
                BiomeParkour.borderSize = sizes[slot - 10];
                player.sendMessage(ChatColor.YELLOW + "Border size set to " + BiomeParkour.borderSize);
                gui = new GUI("Biome Parkour");
                gui.open(player);
            } else if (slot == 26) {
                gui = new GUI("Biome Parkour");
                gui.open(player);
            }

        } else if (title.equals("Biome Parkour Speed Menu")) {
            double[] speeds = {0.05, 0.1, 0.15, 0.2, 0.3};
            if (slot >= 10 && slot < 10 + speeds.length) {
                BiomeParkour.baseBorderSpeed = speeds[slot - 10];
                BiomeParkour.currentBorderSpeed = BiomeParkour.baseBorderSpeed;
                player.sendMessage(ChatColor.YELLOW + "Starting speed set to " + String.format("%.3f", BiomeParkour.baseBorderSpeed));
                gui = new GUI("Biome Parkour");
                gui.open(player);
            } else if (slot == 26) {
                gui = new GUI("Biome Parkour");
                gui.open(player);
            }

        } else if (title.equals("Biome Parkour Acceleration Menu")) {
            double[] rates = {0.0, 0.002, 0.005, 0.01, 0.02};
            if (slot >= 10 && slot < 10 + rates.length) {
                BiomeParkour.speedIncreaseRate = rates[slot - 10];
                player.sendMessage(ChatColor.YELLOW + "Speed increase rate set to " + String.format("%.4f", BiomeParkour.speedIncreaseRate));
                gui = new GUI("Biome Parkour");
                gui.open(player);
            } else if (slot == 26) {
                gui = new GUI("Biome Parkour");
                gui.open(player);
            }

        } else if (title.equals("Biome Parkour Duration Menu")) {
            int[] mins = {10, 20, 30, 45, 60, 120};
            if (slot >= 10 && slot < 10 + mins.length) {
                BiomeParkour.maxGameMinutes = mins[slot - 10];
                player.sendMessage(ChatColor.YELLOW + "Game duration set to " + BiomeParkour.maxGameMinutes + " minutes");
                gui = new GUI("Biome Parkour");
                gui.open(player);
            } else if (slot == 26) {
                gui = new GUI("Biome Parkour");
                gui.open(player);
            }
        } else if (title.endsWith(" Menu")) {
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

        if (validClick) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals("Gamemode Select") && !event.getView().getTitle().endsWith(" Menu")) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!event.getView().getTitle().equals("Gamemode Select") && !event.getView().getTitle().endsWith(" Menu")) return;
        player.setItemOnCursor(null);
    }

    private NamedTextColor getTeamColor(String prefix) {
        return switch (prefix.toUpperCase()) {
            case "AQUA" -> NamedTextColor.AQUA;
            case "BLACK" -> NamedTextColor.BLACK;
            case "BLUE" -> NamedTextColor.BLUE;
            case "DARK_BLUE" -> NamedTextColor.DARK_BLUE;
            case "DARK_GRAY" -> NamedTextColor.DARK_GRAY;
            case "DARK_GREEN" -> NamedTextColor.DARK_GREEN;
            case "DARK_PURPLE" -> NamedTextColor.DARK_PURPLE;
            case "DARK_RED" -> NamedTextColor.DARK_RED;
            case "GREEN" -> NamedTextColor.GREEN;
            case "YELLOW" -> NamedTextColor.YELLOW;
            case "GOLD" -> NamedTextColor.GOLD;
            case "LIGHT_PURPLE" -> NamedTextColor.LIGHT_PURPLE;
            case "RED" -> NamedTextColor.RED;
            case "WHITE" -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    private String getTeamColorLegacy(String teamName) {
        NamedTextColor color = getTeamColor(teamName);
        if (color == null) {
            return "§7"; // Gray as fallback
        }

        if (color.equals(NamedTextColor.BLACK))          return "§0";
        if (color.equals(NamedTextColor.DARK_BLUE))      return "§1";
        if (color.equals(NamedTextColor.DARK_GREEN))     return "§2";
        if (color.equals(NamedTextColor.DARK_AQUA))      return "§3";
        if (color.equals(NamedTextColor.DARK_RED))       return "§4";
        if (color.equals(NamedTextColor.DARK_PURPLE))    return "§5";
        if (color.equals(NamedTextColor.GOLD))           return "§6";
        if (color.equals(NamedTextColor.GRAY))           return "§7";
        if (color.equals(NamedTextColor.DARK_GRAY))      return "§8";
        if (color.equals(NamedTextColor.BLUE))           return "§9";
        if (color.equals(NamedTextColor.GREEN))          return "§a";
        if (color.equals(NamedTextColor.AQUA))           return "§b";
        if (color.equals(NamedTextColor.RED))            return "§c";
        if (color.equals(NamedTextColor.LIGHT_PURPLE))   return "§d";
        if (color.equals(NamedTextColor.YELLOW))         return "§e";
        if (color.equals(NamedTextColor.WHITE))          return "§f";

        return "§7"; // Final fallback if somehow a non-named color slips through
    }

    private String getTeamPrefixInitial(String prefix) {
        return switch (prefix.toUpperCase()) {
            case "AQUA" -> "❄";
            case "BLACK" -> "♣";
            case "BLUE" -> "\uD83C\uDFA3";
            case "DARK_BLUE" -> "\uD83D\uDEE1";
            case "DARK_GRAY" -> "\uD83E\uDE93";
            case "DARK_GREEN" -> "\uD83C\uDFF9";
            case "DARK_PURPLE" -> "\uD83E\uDDEA";
            case "DARK_RED" -> "⚗";
            case "GREEN" -> "☣";
            case "YELLOW" -> "☢";
            case "GOLD" -> "☀";
            case "LIGHT_PURPLE" -> "\uD83D\uDD31";
            case "RED" -> "\uD83D\uDDE1";
            case "WHITE" -> "♜";
            default -> {
                String[] prefixInitials = prefix.split("_");
                StringBuilder prefixInitial = new StringBuilder();
                for (String part : prefixInitials) {
                    if (part.length() > 0) {
                        prefixInitial.append(part.charAt(0));
                    }
                }
                yield prefixInitial.toString();
            }
        };
    }
}