package org.zappier.zappierGames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GetScoreCommand implements TabExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Need item argument.");
            return false;
        }

        double value = LootHunt.getItemValue(args[0]);
        if (value > 0) {
            sender.sendMessage(ChatColor.GREEN + args[0] + " is worth " + LootHunt.getItemValue(args[0]));
        } else {
            sender.sendMessage(ChatColor.GREEN + args[0] + " either has no value or does not exist. ");
        }
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(Material.values()) // Convert Material.values() to a stream
                    .map(Material::name) // Get the material names as strings
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase())) // Filter by input
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
