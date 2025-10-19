package org.zappier.zappierGames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LoothuntCommand implements TabExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Not enough arguments.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Game duration required");
                    return false;
                }
                try {
                    LootHunt.start(Double.parseDouble(args[1]));
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Invalid duration");
                    System.out.println(e);
                    return false;
                }
                return true;

            case "end":
                LootHunt.endGame();
                return true;

            case "scores":
                player.sendMessage(ChatColor.RED + "loothunt scores WIP");
                return true;

            case "jointeam":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Not enough arguments.");
                } else {
                    if (args[1].length() < 3 || args[1].length() > 16) {
                        player.sendMessage(ChatColor.RED + "Team name must be between 3-16 charcaters in length");
                    }
                    String prefix = args[1].toUpperCase();

                    NamedTextColor teamColor = getTeamColor(prefix);

                    String[] prefixInitials = prefix.split("_");
                    String prefixInitial = "";
                    for (int i = 0; i < prefixInitials.length; i++) {
                        prefixInitial += prefixInitials[i].charAt(0);
                    }

                    if (prefix.equals("AQUA")) {prefixInitial = "❄";}
                    if (prefix.equals("BLACK")) {prefixInitial = "♣";}
                    if (prefix.equals("BLUE")) {prefixInitial = "\uD83C\uDFA3";}
                    if (prefix.equals("DARK_BLUE")) {prefixInitial = "\uD83D\uDEE1";}
                    if (prefix.equals("DARK_GRAY")) {prefixInitial = "\uD83E\uDE93";}
                    if (prefix.equals("DARK_GREEN")) {prefixInitial = "\uD83C\uDFF9";}
                    if (prefix.equals("DARK_PURPLE")) {prefixInitial = "\uD83E\uDDEA";}
                    if (prefix.equals("DARK_RED")) {prefixInitial = "⚗";}
                    if (prefix.equals("GREEN")) {prefixInitial = "☣";}
                    if (prefix.equals("YELLOW")) {prefixInitial = "☢";}
                    if (prefix.equals("GOLD")) {prefixInitial = "☀";}
                    if (prefix.equals("LIGHT_PURPLE")) {prefixInitial = "\uD83D\uDD31";}
                    if (prefix.equals("RED")) {prefixInitial = "\uD83D\uDDE1";}
                    if (prefix.equals("RED")) {prefixInitial = "\uD83D\uDDE1";}
                    if (prefix.equals("WHITE")) {prefixInitial = "♜";}

                    TextComponent coloredPrefix = (teamColor != null)
                            ? Component.text("[" + prefixInitial + "] ", teamColor)
                            : Component.text("[" + prefix + "] ", NamedTextColor.GRAY);

                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    Bukkit.broadcastMessage(player.getName().toUpperCase() + " has joined team " + prefix);

                    Team team = scoreboard.getTeam(player.getName());
                    if (team == null) {
                        team = scoreboard.registerNewTeam(player.getName());
                    } else if (teamColor != null) {
                        team.color(teamColor);
                    }

                    // Set the prefix and assign the player to the team
                    team.prefix(coloredPrefix);
                    team.addEntry(player.getName());



                    Bukkit.broadcastMessage("LOOTHUNT: " + player.getName().toUpperCase() + " has joined team " + prefix);
                    LootHunt.playerTeams.put(player.getName().toUpperCase(), prefix);
                }
                return true;

            case "leaveteam":
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                Team team = scoreboard.getTeam(player.getName());

                if (team != null) {
                    team.removeEntry(player.getName());
                    team.unregister();
                    Bukkit.broadcastMessage("LOOTHUNT: " + player.getName().toUpperCase() + " is no longer on a team");
                    LootHunt.playerTeams.remove(player.getName().toUpperCase());
                } else {
                    player.sendMessage(ChatColor.RED + "No team to leave, you are not currently on one!");
                }
                return true;

            case "debug":
                for (Map.Entry<String, String> i : LootHunt.playerTeams.entrySet()) {
                    player.sendMessage(i.getKey() + " " + i.getValue() + ", ");
                }
                return true;

            default:
                player.sendMessage(ChatColor.RED + "Bad arguments.");
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "end", "scores", "jointeam", "leaveteam").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equals("jointeam")) {
            return Arrays.asList("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE").stream()
                    .filter(option -> option.startsWith(args[1].toUpperCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private NamedTextColor getTeamColor(String name) {
        try {
            return NamedTextColor.NAMES.value(name.toLowerCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
