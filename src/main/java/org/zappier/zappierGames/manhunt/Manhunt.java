package org.zappier.zappierGames.manhunt;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.zappier.zappierGames.ZappierGames;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;

import static org.zappier.zappierGames.skybattle.Skybattle.getPlayerTeam;

public class Manhunt {
    public static int showTrackerDimension = 1;
    public static int shoutHunterTarget = 1;
    public static int presidentDeathLink = -1;
    public static int presidentWearArmor = -1;
    public static int bodyguardRespawn = -1;
    public static int bodyguardHpBonus = 0;
    public static HashMap<String, Boolean> twists = new HashMap<>();
    public static int funtimer = 0;

    public static class manhuntTwist {
        public String name;
        public String desc1;
        public String desc2;
        public Material mat;
        public boolean enabled;
        public boolean rollable;
        manhuntTwist(Material mat, String name, String desc1, String desc2) {
            this.name = name;
            this.desc1 = desc1;
            this.desc2 = desc2;
            this.mat = mat;
            this.enabled = false;
            this.rollable = true;
        }
    };

    public static manhuntTwist[] manhuntTwists = {
            new manhuntTwist(Material.ELYTRA, "Permanent Elytra", "Everyone has an unbreakable", "elytra equipped permanently."),
            new manhuntTwist(Material.NETHERITE_CHESTPLATE, "Juggernaut", "The runner has massive health", "and permanent resistance."),
            new manhuntTwist(Material.BARRIER, "Shieldless", "Shields are disabled and", "cannot be used or crafted."),
            new manhuntTwist(Material.BEDROCK, "No Rules", "Anything goes in this", "chaotic classic match."),
            new manhuntTwist(Material.COMPASS, "Normal Manhunt", "The standard manhunt", "experience with no twists."),
            new manhuntTwist(Material.DIAMOND_SWORD, "Axeless and Shieldless", "Combat is limited to swords", "and basic blocking."),
            new manhuntTwist(Material.IRON_AXE, "Axeless", "Axes are disabled for", "all players."),
            new manhuntTwist(Material.TOTEM_OF_UNDYING, "Hunter Lives", "Hunters have 3 lives.", "Game ends when all are lost."),
            new manhuntTwist(Material.KNOWLEDGE_BOOK, "Hidden Advancements", "Advancement popups are", "hidden from the chat."),
            new manhuntTwist(Material.PLAYER_HEAD, "Runner Lives", "The runner has 2 lives", "instead of just one."),
            new manhuntTwist(Material.CHEST, "Keep Inventory", "Players keep their items", "and XP upon death."),
            new manhuntTwist(Material.SUGAR, "Speed IV", "Everyone has permanent", "Speed IV effect."),
            new manhuntTwist(Material.STICK, "Knockback Stick", "Everyone starts with a", "powerful Knockback stick."),
            new manhuntTwist(Material.REPEATER, "Role Swap", "Hunters and runners swap", "roles every 5 minutes."),
            new manhuntTwist(Material.GOLDEN_BOOTS, "Speedrun Race", "The first person to kill", "the dragon wins."),
            new manhuntTwist(Material.SPECTRAL_ARROW, "Glowing", "All players have the", "permanent glowing effect."),
            new manhuntTwist(Material.CARVED_PUMPKIN, "Permanent F1", "Your HUD and hand are", "permanently hidden."),
            new manhuntTwist(Material.ANVIL, "No Armor/Weapon Crafting", "You can only use items", "found in loot chests."),
            new manhuntTwist(Material.FURNACE, "No Smelting", "Furnaces cannot be used.", "Find other ways to cook."),
            new manhuntTwist(Material.FERMENTED_SPIDER_EYE, "Weakness", "Everyone has permanent", "Weakness I applied."),
            //new manhuntTwist(Material.PAINTING, "Cursed Texture Pack", "The world looks very", "different and confusing."),
            new manhuntTwist(Material.SOUL_SAND, "Slowness III", "Movement is extremely slow", "for all players."),
            new manhuntTwist(Material.RABBIT_FOOT, "Jump Boost X", "Everyone can leap", "massive distances."),
            new manhuntTwist(Material.CLOCK, "Always Night", "The sun never rises.", "Beware of the monsters."),
            //new manhuntTwist(Material.RECOVERY_COMPASS, "Spin again idiot", "The wheel didn't like that.", "Spinning one more time!"),
            new manhuntTwist(Material.FEATHER, "Levitation", "Everyone floats upward", "every other minute."),
            new manhuntTwist(Material.GOLDEN_APPLE, "UHC Manhunt", "Natural regeneration is off.", "Heal with gapples or pots."),
            new manhuntTwist(Material.FISHING_ROD, "1.8 Healing", "Saturation regeneration is off.", "Heal slowly, or with pos."),
            new manhuntTwist(Material.IRON_CHESTPLATE, "Quick Start", "Runner starts with full", "iron gear and tools."),
            //new manhuntTwist(Material.WITHER_SKELETON_SKULL, "Cursed DOOM", "Five random twists are", "active at the same time!"),
            new manhuntTwist(Material.SPYGLASS, "Permanent F5", "Your camera is locked", "in third-person view."),
            new manhuntTwist(Material.TNT, "TNT Run", "TNT spawns at your", "feet every 10 seconds."),
            new manhuntTwist(Material.LEATHER_CHESTPLATE, "Armorless Runner", "The runner is not", "allowed to wear armor."),
            new manhuntTwist(Material.GRASS_BLOCK, "No Placing Blocks", "Building and tower-upping", "is strictly forbidden."),
            new manhuntTwist(Material.ENDER_EYE, "Third Eye", "Runners can also track", "hunters with a compass."),
            new manhuntTwist(Material.WRITABLE_BOOK, "Side Quest", "Runner must complete 4", "random advancements to win."),
            new manhuntTwist(Material.COOKED_BEEF, "Picky Eaters", "You can only eat 5", "specific random foods.")
    };

    public static void start(int borderSize, int centerX, int centerZ) {
        funtimer = 0;
        /*for (int i = 0; i < manhuntTwists.length; i++) {
            Bukkit.broadcastMessage(manhuntTwists[i].name);
        }*/

        if (ZappierGames.gameMode < 1 || ZappierGames.gameMode > 10) {
            ZappierGames.gameMode = 1;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Manhunt mode unset, defaulting to standard.");
        }

        ZappierGames.globalBossBar.setVisible(false);
        ZappierGames.resetPlayers(true);

        for (World world : Bukkit.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            if (world.isBedWorks()) {
                border.setCenter(centerX, centerZ);
                border.setSize(borderSize);
            } else {
                border.setCenter(0, 0);
                border.setSize(30000000);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.clearActivePotionEffects();
            p.setCollidable(true);

            switch (ZappierGames.gameMode) {
                case 1:
                    p.sendTitle(ChatColor.RED + "Manhunt", ChatColor.RED + "Beat the game, or die trying.");
                    break;
                case 2:
                    p.sendTitle(ChatColor.GREEN + "Manhunt", ChatColor.GREEN + "Beat the game, don't get infected!");
                    break;
                case 3:
                    p.sendTitle(ChatColor.BLUE + "Manhunt", ChatColor.BLUE + "Protect the president!");
                    break;
            }

            p.sendActionBar(ChatColor.GREEN + "Use /gc to get a tracker, and /trp <player> to select target.");
            p.sendMessage(ChatColor.GREEN + "Use /gc to get a tracker, and /trp <player> to select target.");
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExperienceLevelAndProgress(0);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            p.getWorld().setTime(0);
            if (getPlayerTeam(p).equals("Bodyguard")) {
                p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0 + Manhunt.bodyguardHpBonus);
                p.setHealth(20.0 + Manhunt.bodyguardHpBonus);
            }
        }
    }

    public static void preventArmor(Player player, String reason) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean removed = false;

        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];

            if (piece != null && piece.getType() != Material.AIR) {
                // Try to put it back in their normal inventory
                // addItem returns a HashMap of items that didn't fit
                var overflow = player.getInventory().addItem(piece);

                // If inventory is full, drop it at their feet
                if (!overflow.isEmpty()) {
                    for (ItemStack item : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }

                // Clear that armor slot
                armor[i] = null;
                removed = true;
            }
        }

        // Update the player's armor if we changed anything
        if (removed) {
            player.getInventory().setArmorContents(armor);
            player.sendMessage(reason);
        }
    }

    public static void run() {
        funtimer++;
        if (funtimer > 20 * 120) {
            funtimer = 0;
        }
        twists.clear();
        for (int i = 0; i < manhuntTwists.length; i++) {
            twists.put(manhuntTwists[i].name, manhuntTwists[i].enabled);
        }

        //TWISTS
        if (twists.get("UHC Manhunt")) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            }
        }

        if (twists.get("Keep Inventory")) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.KEEP_INVENTORY, true);
            }
        }

        if (twists.get("Always Night")) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(114000);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {continue;}
            if (getPlayerTeam(p).equals("President")) {
                if (presidentWearArmor <= 0) {
                    preventArmor(p, "§cThe president cannot wear armor!");
                }
            }

            //TWISTS

            if (funtimer % (20 * 10) == 0 && twists.get("TNT Run")) {
                Location loc = p.getLocation();

                TNTPrimed tnt = (TNTPrimed) loc.getWorld().spawnEntity(loc.add(0, 0.5, 0), EntityType.TNT);

                tnt.setFuseTicks(80);
                tnt.setYield(4.0f);
                //tnt.setSource(p);
                loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            }

            if (twists.get("Speed IV")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 3));
            }

            if (twists.get("Slowness III")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 2));
            }

            if (twists.get("Weakness")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 0));
            }

            if (twists.get("Jump Boost X")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 9));
            }

            if (twists.get("1.8 Healing")) {
                if (p.getFoodLevel() > 19) {p.setFoodLevel(19);}
            }
            if (twists.get("No Armor/Weapon Crafting") == true) {
                preventArmor(p, "§cCurrent twist prevents armor (no armor)!");
                for (ItemStack i : p.getInventory().getContents()) {
                    String t = i.getType().name().toLowerCase();
                    if (t.contains("_sword") || t.contains("_axe") || t.contains("_spear")) {
                        p.sendMessage(ChatColor.RED + "No weapons, cheater!");
                        p.getInventory().remove(i);
                    }
                }
            }

            if (twists.get("Axeless") || twists.get("Axeless and Shieldless")) {
                preventArmor(p, "§cCurrent twist prevents armor (no armor)!");
                for (ItemStack i : p.getInventory().getContents()) {
                    String t = i.getType().name().toLowerCase();
                    if (t.contains("_axe")) {
                        p.sendMessage(ChatColor.RED + "No axes in axeless!");
                        p.getInventory().remove(i);
                    }
                }
            }

            if (twists.get("Shieldless") || twists.get("Axeless and Shieldless")) {
                preventArmor(p, "§cCurrent twist prevents armor (no armor)!");
                for (ItemStack i : p.getInventory().getContents()) {
                    String t = i.getType().name().toLowerCase();
                    if (t.equals("shield")) {
                        p.sendMessage(ChatColor.RED + "No shields in shieldless!");
                        p.getInventory().remove(i);
                    }
                }
            }
        }
    }
}
