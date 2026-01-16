package org.zappier.zappierGames.manhunt;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.zappier.zappierGames.ZappierGames;
import org.bukkit.entity.EntityType;

import java.util.*;

import static org.zappier.zappierGames.skybattle.Skybattle.getPlayerTeam;

public class Manhunt {
    public static int showTrackerDimension = 1;
    public static int shoutHunterTarget = 1;
    public static int presidentDeathLink = -1;
    public static int presidentWearArmor = -1;
    public static int bodyguardRespawn = -1;
    public static int bodyguardHpBonus = 0;
    public static int netherLavaPvP = -1;
    public static int allowSpears = -1;
    public static int bedBombing = -1;
    public static int anchorBombing = -1;
    public static HashMap<String, Boolean> twists = new HashMap<>();
    public static ArrayList<Material> allowedFoods = new ArrayList<>();
    public static HashMap<String, Integer> playerDeaths = new HashMap<>();
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
            new manhuntTwist(Material.GRASS_BLOCK, "No Placing Blocks", "Building and tower-upping", "is strictly forbidden."),
            new manhuntTwist(Material.ELYTRA, "Permanent Elytra", "Everyone has an unbreakable", "elytra equipped permanently."),
            new manhuntTwist(Material.DIAMOND_CHESTPLATE, "Diamond Juggernaut", "The hunter gets full", "diamond armor."),
            new manhuntTwist(Material.NETHERITE_CHESTPLATE, "Netherite Juggernaut", "The hunter gets full", "netherite armor."),
            new manhuntTwist(Material.BARRIER, "Shieldless", "Shields are disabled and", "cannot be used or crafted."),
            new manhuntTwist(Material.BEDROCK, "No Rules", "Anything goes in this", "chaotic classic match."),
            new manhuntTwist(Material.COMPASS, "Normal Manhunt", "The standard manhunt", "experience with no twists."),
            new manhuntTwist(Material.DIAMOND_SWORD, "Axeless and Shieldless", "Combat is limited to swords", "and basic blocking."),
            new manhuntTwist(Material.IRON_AXE, "Axeless", "Axes are disabled for", "all players."),
            new manhuntTwist(Material.TOTEM_OF_UNDYING, "Hunter Lives", "Hunters have 3 lives.", "Game ends when all are lost."),
            //new manhuntTwist(Material.KNOWLEDGE_BOOK, "Hidden Advancements", "Advancement popups are", "hidden from the chat."),
            new manhuntTwist(Material.PLAYER_HEAD, "Runner Lives", "The runner has 2 lives", "instead of just one."),
            new manhuntTwist(Material.CHEST, "Keep Inventory", "Players keep their items", "and XP upon death."),
            new manhuntTwist(Material.SUGAR, "Speed IV", "Everyone has permanent", "Speed IV effect."),
            new manhuntTwist(Material.STICK, "Knockback Stick", "Everyone starts with a", "Knockback X stick."),
            new manhuntTwist(Material.GOLDEN_BOOTS, "Speedrun Race", "The first person to kill", "the dragon wins."),
            new manhuntTwist(Material.SPECTRAL_ARROW, "Glowing", "All players have the", "permanent glowing effect."),
            new manhuntTwist(Material.CARVED_PUMPKIN, "(HONOR SYSTEM) Permanent F1", "Your HUD and hand are", "permanently hidden."),
            new manhuntTwist(Material.ANVIL, "No Armor/Weapon Crafting", "You can only use items", "found in loot chests."),
            new manhuntTwist(Material.FURNACE, "No Smelting", "Furnaces cannot be used.", "Find other ways to cook."),
            new manhuntTwist(Material.FERMENTED_SPIDER_EYE, "Weakness", "Everyone has permanent", "Weakness I applied."),
            new manhuntTwist(Material.PAINTING, "(HONOR SYSTEM) Cursed Texture Pack", "The world looks very", "different and confusing."),
            new manhuntTwist(Material.SOUL_SAND, "Slowness III", "Movement is extremely slow", "for all players."),
            new manhuntTwist(Material.RABBIT_FOOT, "Jump Boost X", "Everyone can leap", "massive distances."),
            new manhuntTwist(Material.CLOCK, "Always Night", "The sun never rises.", "Beware of the monsters."),
            new manhuntTwist(Material.RECOVERY_COMPASS, "Spin again idiot", "The wheel didn't like that.", "Spin again!"),
            new manhuntTwist(Material.FEATHER, "Levitation", "Everyone floats upward", "every other minute."),
            new manhuntTwist(Material.GOLDEN_APPLE, "UHC Manhunt", "Natural regeneration is off.", "Heal with gapples or pots."),
            new manhuntTwist(Material.FISHING_ROD, "1.8 Healing", "Saturation regeneration is off.", "Heal slowly, or with pos."),
            new manhuntTwist(Material.IRON_CHESTPLATE, "Quick Start", "Runner starts with full", "iron gear and tools."),
            //new manhuntTwist(Material.WITHER_SKELETON_SKULL, "Cursed DOOM", "Five random twists are", "active at the same time!"),
            new manhuntTwist(Material.SPYGLASS, "(HONOR SYSTEM) Permanent F5", "Your camera is locked", "in third-person view."),
            new manhuntTwist(Material.TNT, "TNT Run", "TNT spawns at your", "feet every 10 seconds."),
            new manhuntTwist(Material.LEATHER_CHESTPLATE, "Armorless Runner", "The runner is not", "allowed to wear armor."),
            new manhuntTwist(Material.ENDER_EYE, "Sixth Sense", "Runners can also track", "hunters with a compass."),
            //new manhuntTwist(Material.WRITABLE_BOOK, "Side Quest", "Runner must complete 4", "random advancements to win."),
            new manhuntTwist(Material.COOKED_BEEF, "Picky Eaters", "You can only eat 5", "specific random foods.")
    };

    public static void giveKit(Player p) {
        twists.clear();
        for (int i = 0; i < manhuntTwists.length; i++) {
            twists.put(manhuntTwists[i].name, manhuntTwists[i].enabled);
        }

        String playerTeam = getPlayerTeam(p);


        if (twists.get("Sixth Sense") || "Hunters".equals(playerTeam) || "Runner_Suppliers".equals(playerTeam) || "Hunter_Suppliers".equals(playerTeam)) {
            ItemStack compass = new ItemStack(Material.COMPASS);
            compass.addEnchantment(Enchantment.VANISHING_CURSE, 1);

            p.getInventory().addItem(compass);
        }


        if (twists.get("Knockback Stick")) {
            ItemStack stick = new ItemStack(Material.STICK);

            stick.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
            stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 10);

            p.getInventory().addItem(stick);
        }

        if ("Hunters".equals(playerTeam) || "Hunter_Suppliers".equals(playerTeam)) {
            if (Manhunt.twists.get("Diamond Juggernaut")) {
                ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
                helmet.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
                chestplate.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
                leggings.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
                boots.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                p.getInventory().setHelmet(helmet);
                p.getInventory().setChestplate(chestplate);
                p.getInventory().setLeggings(leggings);
                p.getInventory().setBoots(boots);
            }


            if (Manhunt.twists.get("Netherite Juggernaut")) {
                ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
                helmet.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
                chestplate.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
                leggings.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
                boots.addEnchantment(Enchantment.VANISHING_CURSE, 1);
                p.getInventory().setHelmet(helmet);
                p.getInventory().setChestplate(chestplate);
                p.getInventory().setLeggings(leggings);
                p.getInventory().setBoots(boots);
            }
        }


        if (!"President".equals(playerTeam) && Manhunt.twists.get("Permanent Elytra")) {
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            elytra.addEnchantment(Enchantment.VANISHING_CURSE, 1);
            p.getInventory().setChestplate(elytra);
        }
    }

    public static void start(int borderSize, int centerX, int centerZ) {
        announceEnabledTwists();

        playerDeaths.clear();
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
            world.setGameRule(GameRule.KEEP_INVENTORY, false);
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
            try {
                giveKit(p);
            } catch (RuntimeException e) {
                Bukkit.broadcastMessage(e.toString());
            }
        }


        twists.clear();
        for (int i = 0; i < manhuntTwists.length; i++) {
            twists.put(manhuntTwists[i].name, manhuntTwists[i].enabled);
        }

        if (twists.get("No Rules")) {
            netherLavaPvP = 1;
            anchorBombing = 1;
            bedBombing = 1;
        }


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

        allowedFoods.clear();
        if (twists.get("Picky Eaters")) {
            Material[] allPossibleFoods = {
                    Material.APPLE,
                    Material.BAKED_POTATO,
                    Material.BEETROOT,
                    Material.BEETROOT_SOUP,
                    Material.BEEF,
                    Material.BREAD,
                    Material.CARROT,
                    Material.CHICKEN,
                    Material.CHORUS_FRUIT,
                    Material.COD,
                    Material.COOKED_BEEF,
                    Material.COOKED_CHICKEN,
                    Material.COOKED_COD,
                    Material.COOKED_MUTTON,
                    Material.COOKED_PORKCHOP,
                    Material.COOKED_RABBIT,
                    Material.COOKED_SALMON,
                    Material.COOKIE,
                    Material.DRIED_KELP,
                    //Material.ENCHANTED_GOLDEN_APPLE,
                    Material.GOLDEN_APPLE,
                    Material.GOLDEN_CARROT,
                    Material.GLOW_BERRIES,
                    Material.HONEY_BOTTLE,
                    Material.MELON_SLICE,
                    Material.MUSHROOM_STEW,
                    Material.MUTTON,
                    Material.POISONOUS_POTATO,
                    Material.POPPED_CHORUS_FRUIT,
                    Material.PORKCHOP,
                    Material.POTATO,
                    Material.PUMPKIN_PIE,
                    Material.PUFFERFISH,
                    Material.RABBIT,
                    Material.RABBIT_STEW,
                    Material.ROTTEN_FLESH,
                    Material.SALMON,
                    Material.SPIDER_EYE,
                    Material.SUSPICIOUS_STEW,
                    Material.SWEET_BERRIES,
                    Material.TROPICAL_FISH
            };

            // Pick 5 unique random foods
            ArrayList<Material> pool = new ArrayList<>(java.util.Arrays.asList(allPossibleFoods));
            java.util.Collections.shuffle(pool);
            for (int i = 0; i < 5; i++) {
                allowedFoods.add(pool.get(i));
            }

            String foodNames = "";
            for (Material m : allowedFoods) {
                foodNames += ChatColor.YELLOW + m.name().replace("_", " ") + ChatColor.WHITE + ", ";
            }
            Bukkit.broadcastMessage(ChatColor.RED + "Picky Eaters Active! You can ONLY eat: " + foodNames);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            String playerTeam = getPlayerTeam(p);
            if (twists.get("Quick Start") && (playerTeam.equals("Runner") || playerTeam.equals("Bodyguard") || playerTeam.equals("Runner_Suppliers"))) {
                // Armor
                p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
                p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));

                // Tools
                p.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
                p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
                p.getInventory().addItem(new ItemStack(Material.IRON_AXE));
                p.getInventory().addItem(new ItemStack(Material.IRON_SHOVEL));
                p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16)); // Give them some food too
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

        if (twists.get("Always Night")) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(114000);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {continue;}
            String playerTeam = getPlayerTeam(p);
            if (playerTeam.equals("President")) {
                if (presidentWearArmor <= 0) {
                    preventArmor(p, "§cThe president cannot wear armor!");
                }
            }

            if (Manhunt.allowSpears <= 0) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) { continue; }

                // --- Replace Spears with Shovels ---
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item == null || item.getType() == Material.AIR) continue;

                    String materialName = item.getType().name();

                    if (materialName.contains("SPEAR")) {
                        // Replace "SPEAR" with "SHOVEL" (e.g., DIAMOND_SPEAR -> DIAMOND_SHOVEL)
                        String shovelName = materialName.replace("SPEAR", "SHOVEL");
                        Material shovelMat = Material.matchMaterial(shovelName);

                        if (shovelMat != null) {
                            item.setType(shovelMat);
                            p.sendMessage("§eYour spear was converted to a shovel!");
                        }
                    }
                }
            }

            //TWISTS
            if (twists.get("Picky Eaters")) {
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item == null || item.getType() == Material.AIR) continue;

                    if (item.getType().isEdible() && !allowedFoods.contains(item.getType())) {
                        p.sendMessage(ChatColor.RED + "You are a picky eater! You can't have " + item.getType().name());
                        p.getInventory().remove(item);
                    }
                }
            }

            if (funtimer % (20 * 10) == 0 && twists.get("TNT Run")) {
                Location loc = p.getLocation();

                TNTPrimed tnt = (TNTPrimed) loc.getWorld().spawnEntity(loc.add(0, 0.5, 0), EntityType.TNT);

                tnt.setFuseTicks(60);
                tnt.setYield(4.0f);
                //tnt.setSource(p);
                loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            }

            if (twists.get("Speed IV")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 410, 3, false, false));
            }

            if (twists.get("Glowing")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 410, 0, false, false));
            }

            if (twists.get("Slowness III")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 410, 2, false, false));
            }

            if (twists.get("Weakness")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 410, 0, false, false));
            }

            if (twists.get("Levitation") && funtimer == 60 * 20) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 60, 0, false, false));
            }

            if (twists.get("Jump Boost X")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 410, 9, false, false));
            }

            if (twists.get("1.8 Healing")) {
                if (p.getFoodLevel() > 19) {p.setFoodLevel(19);}
            }
            if (twists.get("No Armor/Weapon Crafting") == true) {
                preventArmor(p, "§cCurrent twist prevents armor (no armor)!");
                for (ItemStack i : p.getInventory().getContents()) {
                    if (i == null) continue;
                    String t = i.getType().name().toLowerCase();
                    if (t.contains("_sword") || t.contains("_axe") || t.contains("_spear")) {
                        p.sendMessage(ChatColor.RED + "No weapons, cheater!");
                        p.getInventory().remove(i);
                    }
                }
            }


            if (twists.get("Armorless Runner") && playerTeam.equals("Runner") || playerTeam.equals("Runner_Suppliers") || playerTeam.equals("Bodyguard")) {
                preventArmor(p, "§cTwist says no armor for runners!!");
            }

            if (twists.get("Axeless") || twists.get("Axeless and Shieldless")) {
                //preventArmor(p, "§cCurrent twist prevents armor (no armor)!");
                for (ItemStack i : p.getInventory().getContents()) {
                    if (i == null) continue;
                    String t = i.getType().name().toLowerCase();
                    if (t.contains("_axe")) {
                        p.sendMessage(ChatColor.RED + "No axes in axeless!");
                        p.getInventory().remove(i);
                    }
                }
            }

            if (twists.get("Shieldless") || twists.get("Axeless and Shieldless")) {
                //preventArmor(p, "§cCurrent twist prevents armor (no armor)!");
                for (ItemStack i : p.getInventory().getContents()) {
                    if (i == null) continue;
                    String t = i.getType().name().toLowerCase();
                    if (t.equals("shield")) {
                        p.sendMessage(ChatColor.RED + "No shields in shieldless!");
                        p.getInventory().remove(i);
                    }
                }
            }
        }
    }

    public static void enableRandomTwists(int count) {
        if (count <= 0) {
            return; // nothing to do
        }

        // Collect all currently rollable twists
        List<manhuntTwist> rollableTwists = new ArrayList<>();
        for (manhuntTwist twist : manhuntTwists) {
            if (twist.rollable && !twist.enabled) {  // only consider still-disabled ones
                rollableTwists.add(twist);
            }
        }

        int available = rollableTwists.size();

        if (available < count) {
            String msg = ChatColor.RED + "Not enough rollable twists left! ("
                    + available + "/" + count + " available)";

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(msg);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        // Shuffle and pick first 'count' twists
        Collections.shuffle(rollableTwists, new Random());
        List<manhuntTwist> selected = rollableTwists.subList(0, count);

        // Enable them
        StringBuilder enabledList = new StringBuilder(ChatColor.GREEN + "Enabled twists: ");
        for (manhuntTwist twist : selected) {
            twist.enabled = true;
            enabledList.append(ChatColor.YELLOW)
                    .append(twist.name)
                    .append(ChatColor.WHITE)
                    .append(", ");
        }

        // Broadcast result
        String finalMessage = enabledList.substring(0, enabledList.length() - 2); // remove last ", "
        Bukkit.broadcastMessage(finalMessage);

        // play success sound
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }
    }

    public static void announceEnabledTwists() {
        List<manhuntTwist> enabled = new ArrayList<>();

        for (manhuntTwist twist : manhuntTwists) {
            if (twist.enabled) {
                enabled.add(twist);
            }
        }

        if (enabled.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "» " + ChatColor.WHITE + "No twists are currently active.");
            return;
        }

        // Header
        Bukkit.broadcastMessage(ChatColor.GOLD + "══════════════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "   Active Manhunt Twists (" + enabled.size() + ")");
        Bukkit.broadcastMessage(ChatColor.GOLD + "══════════════════════════════════════");

        for (manhuntTwist twist : enabled) {
            String line = ChatColor.GREEN + twist.name + ChatColor.GRAY + ": " +
                    ChatColor.WHITE + twist.desc1 + " " + twist.desc2;

            Bukkit.broadcastMessage("  " + line);
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "══════════════════════════════════════");
    }
}
