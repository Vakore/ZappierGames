package org.zappier.zappierGames;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class openGUICommand implements TabExecutor {
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        // Check if Manhunt is active (gameMode == 1, 2, or 3)
        boolean isManhuntActive = ZappierGames.gameMode >= 1 && ZappierGames.gameMode <= 3;

        if (isManhuntActive) {
            new GUI("Manhunt").open(player);
            player.sendMessage(ChatColor.YELLOW + "Opening Manhunt menu (game active)");
        } else {
            new GUI().open(player);
            player.sendMessage(ChatColor.GREEN + "Opening main menu");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}