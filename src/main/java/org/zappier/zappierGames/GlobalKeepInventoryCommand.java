package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalKeepInventoryCommand implements TabExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Need true/false argument.");
            return false;
        }

        boolean keepInv = false;
        if (args[0].toLowerCase().equals("true")) {
            keepInv = true;
        } else if (args[0].toLowerCase().equals("false")) {
            keepInv = false;
        } else {
            sender.sendMessage(ChatColor.RED + "Bad argument. Must be 'true' or 'false'");
            return false;
        }

        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, keepInv);
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Keep inventory set to " + keepInv + " across all dimensions");

        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("true", "false").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
