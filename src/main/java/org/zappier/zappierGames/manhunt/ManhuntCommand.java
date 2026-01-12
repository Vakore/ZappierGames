package org.zappier.zappierGames.manhunt;

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

public class ManhuntCommand implements TabExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Not enough arguments.");
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                int borderSize = 5000;
                if (args.length == 2) {
                    try {
                        borderSize = Integer.parseInt(args[1]);
                    } catch (Exception e) {
                        player.sendMessage(ChatColor.RED + "Bad size. Defaulting to 5000...");
                    }
                }
                player.sendMessage(ChatColor.YELLOW + "Border set to " + borderSize + " blocks in each direciton.");
                Manhunt.start(borderSize, (int)player.getLocation().getX(), (int)player.getLocation().getZ());
                return true;

            case "jointeam":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Not enough arguments.");
                    return false;
                } else {
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    //player.sendMessage(ChatColor.RED + "Team name must be between 3-16 charcaters in length");
                    for (int i = 0; i < ZappierGames.teamList.length; i++) {
                        if (ZappierGames.teamList[i].equals(args[1])) {
                            Team disTeam = scoreboard.getTeam(args[1]);
                            if (disTeam != null) {
                                disTeam.addEntry(player.getName());
                                player.sendMessage(ChatColor.YELLOW + "Joined team " + ZappierGames.teamList[i]);
                                return true;
                            } else {
                                player.sendMessage(ChatColor.RED + "For some reason team " + ZappierGames.teamList[i] + " does not exit?");
                            }
                        }
                    }
                }
                player.sendMessage(ChatColor.RED + "Invalid team name.");
                return false;

            case "leaveteam":
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                for (Team team : scoreboard.getTeams()) {
                    if (team.hasEntry(player.getName())) {
                        team.removeEntry(player.getName());
                        player.sendMessage("You have been removed from team " + team.getName());
                        return true;
                    }
                }
                return true;

            case "setmode":
                //player.sendMessage(ChatColor.RED + "Manhunt modes are WIP");
                if (args.length < 1) {
                    Bukkit.broadcastMessage(ChatColor.RED + "No mode set. Defaulting to manhunt.");
                    return false;
                } else {
                    if (args[1].equals("standard")) {
                        ZappierGames.gameMode = 1;
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode set to standard.");
                    } else if (args[1].equals("infection")) {
                        ZappierGames.gameMode = 2;
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode set to infection.");
                    } else if (args[1].equals("kill_president(s)")) {
                        ZappierGames.gameMode = 3;
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode set to kill president(s).");
                    } else {
                        return false;
                    }
                }
                return true;


            case "compasssettings":
                //player.sendMessage(ChatColor.RED + "Manhunt modes are WIP");
                if (args.length < 1) {
                    Bukkit.broadcastMessage(ChatColor.RED + "No compass settings changed.");
                    return false;
                } else {
                    if (args[1].equals("announceontrack")) {
                        Manhunt.shoutHunterTarget = -Manhunt.shoutHunterTarget;
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "shoutHunterTarget set to " + Manhunt.shoutHunterTarget);
                    } else if (args[1].equals("showdimensions")) {
                        Manhunt.showTrackerDimension = -Manhunt.showTrackerDimension;
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "showTrackerDimension set to " + Manhunt.showTrackerDimension);
                    } else {
                        return false;
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
            return Arrays.asList("start", "jointeam", "leaveteam", "setmode", "compasssettings").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equals("setmode")) {
            return Arrays.asList("standard", "infection", "kill_president(s)").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equals("jointeam")) {
            return Arrays.stream(ZappierGames.teamList).toList().stream()
                    .filter(option -> option.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equals("compasssettings")) {
            return Arrays.asList("announceontrack", "showdimensions").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
