/**
 * TO BUILD:
 */

package org.zappier.zappierGames;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.zappier.zappierGames.biomeparkour.BiomeParkour;
import org.zappier.zappierGames.loothunt.*;
import org.zappier.zappierGames.manhunt.CompassTrackerListener;
import org.zappier.zappierGames.manhunt.Manhunt;
import org.zappier.zappierGames.manhunt.ManhuntCommand;
import org.zappier.zappierGames.manhunt.TrackerGUIListener;
import org.zappier.zappierGames.skybattle.CreeperSpawnListener;
import org.zappier.zappierGames.skybattle.CustomPearlsListener;
import org.zappier.zappierGames.skybattle.Skybattle;

import java.util.*;
import java.util.stream.Collectors;

public final class ZappierGames extends JavaPlugin {
    // Singleton instance
    public static ZappierGames instance;


    public static double loothuntDuration = 0; // Default to 0, must be set before starting
    public static final int LOOTHUNT = 0;
    public static final int MANHUNT = 1;

    public static String[] teamList = {"Runners", "Hunters", "Runner_Suppliers", "Hunter_Suppliers", "President", "Bodyguard", "Spectator"};

    //MASTER LOOP
    private BukkitRunnable gameTask;//run(); basically
    public static int timer = 0;//The timer used to determine game length for any game
    public static int gameMode = -1;//-1 means no game playing rn
    public static BossBar globalBossBar = Bukkit.createBossBar("Title", BarColor.PURPLE, BarStyle.SOLID);

    //COMPASS TRACKING
    //who tracks who
    public final Map<String, String> trackingPairs = new HashMap<>();
    //where each player is in each dimension
    private final Map<String, int[]> playerPositions = new HashMap<>();

    //border
    public static int borderSize = 2500;

    //LOOTHUNT
    private final Map<UUID, Integer> playerScores = new HashMap<>();

    public static void resetPlayers(boolean clearInv) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (clearInv) {p.getInventory().clear();}
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setExperienceLevelAndProgress(0);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.clearActivePotionEffects();
            p.setCollidable(true);
            p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20.0);
        }
    }


    //COMMANDS
    public class GetcompassCommand implements TabExecutor {
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }

            Player player = (Player) sender;
            ItemStack compass = new ItemStack(Material.COMPASS);
            compass.addEnchantment(Enchantment.VANISHING_CURSE, 1);

            player.getInventory().addItem(compass);
            player.sendMessage(ChatColor.GREEN + "You have been given a tracking compass.");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return Collections.emptyList();
        }
    }

    public class TrackplayerCommand implements TabExecutor {
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Need to select player to target.");
                return false;
            }

            Player player = (Player) sender;
            Player target = (Player) Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not online.");
                trackingPairs.put(player.getName(), args[0]);
                return true;
            } else {
                sender.sendMessage(ChatColor.GREEN + "Now tracking " + target.getName());
                trackingPairs.put(player.getName(), target.getName());
                if (Manhunt.shoutHunterTarget > 0) {
                    Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " is tracking " + target.getName() + "!");
                }
            }
            return true;
        }


        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    public static ZappierGames getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LootHunt.loadConfig(getConfig());
        // Set the singleton instance
        instance = this;

        // Register command
        getLogger().info("ZappierGames - Now running!");
        saveDefaultConfig(); // Save default config if it doesn't exist
        loadItemValues();
        createVoidWorld("skybattle_world");
        Skybattle.init(instance);
        ParkourRace.init(instance);
        this.getCommand("loothunt").setExecutor(new LoothuntCommand());
        this.getCommand("manhunt").setExecutor(new ManhuntCommand());
        this.getCommand("getcompass").setExecutor(new GetcompassCommand());
        this.getCommand("gc").setExecutor(new GetcompassCommand());
        this.getCommand("trackplayer").setExecutor(new TrackplayerCommand());
        this.getCommand("trp").setExecutor(new TrackplayerCommand());
        this.getCommand("getscore").setExecutor(new GetScoreCommand());
        this.getCommand("globalkeepinventory").setExecutor(new GlobalKeepInventoryCommand());
        this.getCommand("GUI").setExecutor(new openGUICommand());

        getServer().getPluginManager().registerEvents(new LootHuntKillListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new AutoTNTListener(this, "skybattle_world"), this);
        getServer().getPluginManager().registerEvents(new PlayerSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new CustomPearlsListener(this), this);
        getServer().getPluginManager().registerEvents(new CreeperSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new DamageHandler(), this);
        getServer().getPluginManager().registerEvents(new InfinibundleListener(), this);

        //!!!
        getServer().getPluginManager().registerEvents(new CompassTrackerListener(this), this);
        getServer().getPluginManager().registerEvents(new TrackerGUIListener(this), this);


        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        NamedTextColor[] teamColors = {NamedTextColor.GREEN, NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.DARK_PURPLE, NamedTextColor.BLUE, NamedTextColor.AQUA, NamedTextColor.DARK_GRAY};
        for (int i = 0; i < teamList.length; i++) {
            Team team = scoreboard.getTeam(teamList[i]);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamList[i]);
            }
            team.color(teamColors[i]);
        }

        gameTask = new BukkitRunnable() {
            int updateCompassTimer = 20;
            @Override
            public void run() {
                //Compass logic
                updateCompassTimer--;
                if (updateCompassTimer <= 0) {
                    updateCompassTimer = 10;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        //player.sendMessage(ChatColor.GREEN + "Hello, " + player.getName() + "!");
                        Location playerLoc = player.getLocation();
                        int[] playerVec = {(int) playerLoc.getX(), (int) playerLoc.getY(), (int) playerLoc.getZ()};
                        String trackingDimension = playerLoc.getWorld().getName();
                        if (trackingDimension == null) {
                            trackingDimension = "world";
                        }
                        String playerLocStr = player.getName() + " # " + trackingDimension;
                        playerPositions.put(playerLocStr, playerVec);
                        //player.sendMessage(playerLocStr);

                        if (trackingPairs.get(player.getName()) != null && Bukkit.getPlayer(trackingPairs.get(player.getName())) != null) {
                            String targetLocStr = trackingPairs.get(player.getName()) + " # " + trackingDimension;
                            //player.sendMessage(targetLocStr);
                            int[] targetPos = playerPositions.get(targetLocStr);
                            if (targetPos != null) {
                                String targetWorld = Bukkit.getPlayer(trackingPairs.get(player.getName())).getWorld().getName();
                                World.Environment targetEnv = Bukkit.getPlayer(trackingPairs.get(player.getName())).getWorld().getEnvironment();

                                String targetDimension = "Unknown";
                                ChatColor leTrackColor = ChatColor.GREEN;
                                switch (targetEnv) {
                                    case NORMAL:
                                        targetDimension = "Overworld";
                                        break;
                                    case NETHER:
                                        targetDimension = "Nether";
                                        break;
                                    case THE_END:
                                        targetDimension = "End";
                                        break;
                                    default:  // CUSTOM or any other unknown
                                        targetDimension = targetWorld;
                                        break;
                                }
                                Location trackedLocation = new Location(player.getWorld(), targetPos[0], targetPos[1], targetPos[2]);
                                //player.setLastDeathLocation(trackedLocation);
                                for (ItemStack item : player.getInventory().getContents()) {
                                    if (item != null && item.getType() == Material.COMPASS) {
                                        if (item.hasItemMeta() && item.getItemMeta() instanceof CompassMeta compassMeta) {
                                            if (item.getItemMeta().hasEnchant(Enchantment.VANISHING_CURSE)) {
                                                compassMeta.setLodestone(trackedLocation);
                                                compassMeta.setLodestoneTracked(false);
                                                item.setItemMeta(compassMeta);
                                            }
                                        }
                                    }
                                }

                                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                                boolean holdingTracker = false;
                                if (itemInHand != null && itemInHand.getType() == Material.COMPASS) {
                                    ItemMeta meta = itemInHand.getItemMeta();
                                    if (meta != null && meta.hasEnchant(Enchantment.VANISHING_CURSE)) {
                                        holdingTracker = true;
                                    }
                                }

                                //Do the same for offhand
                                if (!holdingTracker) {
                                    itemInHand = player.getInventory().getItemInOffHand();
                                    if (itemInHand != null && itemInHand.getType() == Material.COMPASS) {
                                        ItemMeta meta = itemInHand.getItemMeta();
                                        if (meta != null && meta.hasEnchant(Enchantment.VANISHING_CURSE)) {
                                            holdingTracker = true;
                                        }
                                    }
                                }
                                if (holdingTracker) {
                                    if (Manhunt.showTrackerDimension > 0) {
                                        player.sendActionBar(leTrackColor + "Distance to " + trackingPairs.get(player.getName()) + " (" + targetDimension + "): " + (int) player.getLocation().distance(trackedLocation));
                                    } else {
                                        player.sendActionBar(leTrackColor + "Distance to " + trackingPairs.get(player.getName()) + ": " + (int) player.getLocation().distance(trackedLocation));
                                    }
                                    //player.sendActionBar(ChatColor.GREEN + targetLocStr + ": " + targetPos[0] + ", " + targetPos[1] + ", " + targetPos[2]);
                                }
                            }
                        }
                    }
                }



                if (gameMode == LOOTHUNT) {
                    LootHunt.run();
                } else if (gameMode == 1 || gameMode == 2 || gameMode == 3) {
                    //run manhunt
                    Manhunt.run();
                } else if (gameMode == 10) {
                    World skybattleWorld = Bukkit.getWorld("skybattle_world");
                    if (skybattleWorld != null) {
                        Skybattle.run(skybattleWorld);
                    } else {
                        getLogger().info("Sky battle not running!");
                    }
                } else if (gameMode == 20) {
                    World skybattleWorld = Bukkit.getWorld("skybattle_world");
                    if (skybattleWorld != null) {
                        ParkourRace.run(skybattleWorld);
                    } else {
                        getLogger().info("Parkour Race not running!");
                    }
                } else if (gameMode == 30) {
                    BiomeParkour.run();
                }
            }
        };
        gameTask.runTaskTimer(ZappierGames.this, 0, 1);
    }

    @Override
    public void onDisable() {
        // Cleanup and stop any running tasks
        if (gameTask != null) {
            gameTask.cancel();
        }
        getLogger().info("ZappierGames - Now disabled");
    }

    private void createVoidWorld(String worldName) {
        // Create world with void generator
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidGenerator());
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT); // Flat type, but generator overrides to void
        World world = creator.createWorld();

        if (world != null) {
            // Configure world settings
            world.setPVP(true);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.FALL_DAMAGE, true);
            world.setGameRule(GameRule.KEEP_INVENTORY, false);

            // Set spawn point at y=100 (safe height for platforms)
            world.setSpawnLocation(0, 100, 0);

            // Set world border
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            //border.setSize(borderSize);

            getLogger().info("Void world '" + worldName + "' created successfully!");
        } else {
            getLogger().warning("Failed to create void world '" + worldName + "'.");
        }
    }

    //LOOTHUNT CONFIG
    private void loadItemValues() {
        LootHunt.itemValues = new HashMap<>();
        FileConfiguration config = getConfig();

        // Read the "item-values" section
        if (config.isConfigurationSection("item-values")) {
            for (String key : config.getConfigurationSection("item-values").getKeys(false)) {
                try {
                    double value = config.getDouble("item-values." + key);
                    LootHunt.itemValues.put(key, value);
                } catch (IllegalArgumentException e) {
                    //getLogger().warning("Invalid material in config: " + key);
                }
            }
        }
    }


}
