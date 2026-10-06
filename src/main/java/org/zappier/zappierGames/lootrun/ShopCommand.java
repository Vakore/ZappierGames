package org.zappier.zappierGames.lootrun;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.zappier.zappierGames.ZappierGames;
import org.zappier.zappierGames.loothunt.InfinibundleListener;

import java.util.Collections;
import java.util.List;

import static org.zappier.zappierGames.skybattle.Skybattle.getPlayerTeam;

public class ShopCommand implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        if (ZappierGames.gameMode != ZappierGames.LOOTRUN) {
            player.sendMessage(ChatColor.RED + "The shop is only available during Lootrun.");
            return true;
        }

        String team = getPlayerTeam(player);
        if (team.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Join a team first with /lootrun jointeam <team>.");
            return true;
        }

        InfinibundleListener.openShopDirect(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}