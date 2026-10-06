package org.zappier.zappierGames.lootrun;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.zappier.zappierGames.ZappierGames;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** /lootrun start|jointeam|leaveteam|end - reuses the same scoreboard teams as Manhunt
 * (Runners/Hunters/Runner_Suppliers/Hunter_Suppliers), so /manhunt jointeam works too. */
public class LootrunCommand implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /lootrun <start|jointeam|leaveteam|end>");
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                Lootrun.start();
                return true;

            case "end":
                Lootrun.end();
                return true;

            case "jointeam":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Not enough arguments.");
                    return false;
                }
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                for (String teamName : ZappierGames.teamList) {
                    if (teamName.equalsIgnoreCase(args[1])) {
                        Team team = scoreboard.getTeam(teamName);
                        if (team != null) {
                            team.addEntry(player.getName());
                            player.sendMessage(ChatColor.YELLOW + "Joined team " + teamName);
                            return true;
                        }
                        player.sendMessage(ChatColor.RED + "Team " + teamName + " doesn't exist?");
                        return true;
                    }
                }
                player.sendMessage(ChatColor.RED + "Invalid team name.");
                return false;

            case "leaveteam":
                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                for (Team team : board.getTeams()) {
                    if (team.hasEntry(player.getName())) {
                        team.removeEntry(player.getName());
                        player.sendMessage("You have been removed from team " + team.getName());
                        return true;
                    }
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
            return Arrays.asList("start", "end", "jointeam", "leaveteam").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("jointeam")) {
            return Arrays.asList(ZappierGames.teamList).stream()
                    .filter(option -> option.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}