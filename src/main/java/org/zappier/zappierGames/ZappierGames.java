/**
 * TO BUILD:
 */

package org.zappier.zappierGames;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.zappier.zappierGames.biomeparkour.BiomeParkour;
import org.zappier.zappierGames.loothunt.*;
import org.zappier.zappierGames.manhunt.*;
import org.zappier.zappierGames.skybattle.CreeperSpawnListener;
import org.zappier.zappierGames.skybattle.CustomPearlsListener;
import org.zappier.zappierGames.skybattle.Skybattle;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public final class ZappierGames extends JavaPlugin {
    // Singleton instance
    public static ZappierGames instance;


    public static double loothuntDuration = 0; // Default to 0, must be set before starting
    public static final int LOOTHUNT = 0;
    public static final int MANHUNT = 1;
    public static boolean shouldSave = false;


    public static boolean noPvP = false;
    public static String[] teamList = {"Runners", "Hunters", "Runner_Suppliers", "Hunter_Suppliers", "President", "Bodyguard", "Spectator"};

    //MASTER LOOP
    private BukkitRunnable gameTask;//run(); basically
    public static int timer = 0;//The timer used to determine game length for any game
    public static int gameMode = -1;//-1 means no game playing rn
    public static BossBar globalBossBar = Bukkit.createBossBar("Title", BarColor.PURPLE, BarStyle.SOLID);

    //COMPASS TRACKING
    //who tracks who
    public static final Map<String, String> trackingPairs = new HashMap<>();
    //where each player is in each dimension
    private final Map<String, int[]> playerPositions = new HashMap<>();

    //border
    public static int borderSize = 2500;

    //LOOTHUNT
    private final Map<UUID, Integer> playerScores = new HashMap<>();

    public static void resetPlayers(boolean clearInv, boolean clearAdvancements) {
        ZappierGames.getInstance().stopResultsWebServer();
        ZappierGames.noPvP = false;
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
            if (clearAdvancements) {
                for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
                    Advancement advancement = it.next();
                    AdvancementProgress progress = p.getAdvancementProgress(advancement);

                    // Only touch it if the player has any progress
                    if (!progress.getRemainingCriteria().isEmpty()) {
                        for (String criterion : progress.getAwardedCriteria()) {
                            progress.revokeCriteria(criterion);
                        }
                    }
                }
            }
        }


        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
            world.setTime(0);
        }
    }


    //COMMANDS
    public class GetcompassCommand implements TabExecutor {
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            } else if (ZappierGames.gameMode <= 0 || ZappierGames.gameMode > 5) {
                sender.sendMessage(ChatColor.RED + "Only usable during manhunts!");
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



    public class LoadStateCommand implements TabExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            // Optional: restrict to ops / console only
            if (!sender.hasPermission("zappiergames.loadstate") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            // Optional: add confirmation for safety (reload can be dangerous)
            if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
                sender.sendMessage(ChatColor.YELLOW + "Warning: This will reload the game state from gamestate.yml.");
                sender.sendMessage(ChatColor.YELLOW + "This may reset timers, twists, tracking, etc.");
                sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/loadstate confirm" + ChatColor.YELLOW + " to proceed.");
                return true;
            }

            // Actually load the state
            loadGameState();

            sender.sendMessage(ChatColor.GREEN + "Game state has been reloaded from gamestate.yml.");

            // Optional: broadcast or log
            Bukkit.broadcastMessage(ChatColor.GRAY + "[Admin] " + sender.getName() + " reloaded game state.");
            getLogger().info(sender.getName() + " manually reloaded game state via /loadstate");

            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                return Collections.singletonList("confirm");
            }
            return Collections.emptyList();
        }
    }

    public class GetInfinibundleCommand implements TabExecutor {

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }

            // Optional: restrict to admins / ops (uncomment if desired)
            // if (!player.hasPermission("zappiergames.getinfinibundle") && !player.isOp()) {
            //     player.sendMessage(ChatColor.RED + "You don't have permission to get an Infinibundle!");
            //     return true;
            // }

            String teamName = getTeamName(player);
            NamedTextColor teamColor = getTeamColor(teamName); // We'll define this helper

            ItemStack infinibundle = createInfinibundle(teamName, teamColor);

            // Give to player (with overflow handling)
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(infinibundle);
            if (!leftovers.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftovers.get(0));
                player.sendMessage(Component.text("Your inventory was full! Infinibundle dropped on the ground.", NamedTextColor.YELLOW));
            }

            player.sendMessage(Component.text()
                    .append(Component.text("You received an ", NamedTextColor.GREEN))
                    .append(Component.text("Infinibundle", teamColor))
                    .append(Component.text(" for team: ", NamedTextColor.GREEN))
                    .append(Component.text(teamName, teamColor))
            );

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);

            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
            return Collections.emptyList();
        }

        // Helper: Get team name (same as in InfinibundleListener)
        private String getTeamName(Player player) {
            Team team = player.getScoreboard().getEntryTeam(player.getName());
            return team != null ? team.getName() : "(Solo) " + player.getName();
        }

        // Helper: Get color based on team name (adjust as needed)
        private NamedTextColor getTeamColor(String teamName) {
            String lower = teamName.toLowerCase(Locale.ROOT);
            if (lower.contains("hunter") || lower.contains("red"))     return NamedTextColor.RED;
            if (lower.contains("runner") || lower.contains("green"))   return NamedTextColor.GREEN;
            if (lower.contains("president"))                           return NamedTextColor.GOLD;
            if (lower.contains("bodyguard"))                           return NamedTextColor.DARK_AQUA;
            if (lower.contains("spectator"))                           return NamedTextColor.GRAY;
            return NamedTextColor.WHITE; // default
        }

        // Main bundle creation logic (copied/adapted from your existing code)
        private ItemStack createInfinibundle(String teamName, NamedTextColor teamTextColor) {
            Material bundleMaterial = Material.BUNDLE;
            String lowerTeam = teamName.toLowerCase(Locale.ROOT);

            // Your existing color detection logic
            if (lowerTeam.contains("black"))      bundleMaterial = Material.BLACK_BUNDLE;
            else if (lowerTeam.contains("red"))   bundleMaterial = Material.RED_BUNDLE;
            else if (lowerTeam.contains("green")) bundleMaterial = Material.GREEN_BUNDLE;
            else if (lowerTeam.contains("brown")) bundleMaterial = Material.BROWN_BUNDLE;
            else if (lowerTeam.contains("blue"))  bundleMaterial = Material.BLUE_BUNDLE;
            else if (lowerTeam.contains("purple")) bundleMaterial = Material.PURPLE_BUNDLE;
            else if (lowerTeam.contains("cyan"))  bundleMaterial = Material.CYAN_BUNDLE;
            else if (lowerTeam.contains("gray"))  bundleMaterial = Material.GRAY_BUNDLE;
            else if (lowerTeam.contains("pink"))  bundleMaterial = Material.PINK_BUNDLE;
            else if (lowerTeam.contains("lime"))  bundleMaterial = Material.LIME_BUNDLE;
            else if (lowerTeam.contains("yellow")) bundleMaterial = Material.YELLOW_BUNDLE;
            else if (lowerTeam.contains("light_blue")) bundleMaterial = Material.LIGHT_BLUE_BUNDLE;
            else if (lowerTeam.contains("magenta")) bundleMaterial = Material.MAGENTA_BUNDLE;
            else if (lowerTeam.contains("orange")) bundleMaterial = Material.ORANGE_BUNDLE;
            else if (lowerTeam.contains("white")) bundleMaterial = Material.WHITE_BUNDLE;

            ItemStack infinibundle = new ItemStack(bundleMaterial);

            ItemMeta meta = infinibundle.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Infinibundle", teamTextColor)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

                meta.lore(List.of(
                        Component.text("R-CLICK: open team inventory", NamedTextColor.GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        Component.text("L-CLICK (cursor): put item inside", NamedTextColor.GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        Component.text("SHIFT + L-CLICK: put inventory inside", NamedTextColor.GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                ));

                meta.setCustomModelData(900009);
                infinibundle.setItemMeta(meta);
            }

            return infinibundle;
        }
    }


    private static HttpServer resultsWebServer = null;
    private static final int WEB_PORT = 8081;
    private static final long RESULTS_AVAILABLE_MINUTES = 10;
    public static ZappierGames getInstance() {
        return instance;
    }

    private File gameStateFile;
    private FileConfiguration gameStateConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LootHunt.loadConfig(getConfig());

        instance = this;

        gameStateFile = new File(getDataFolder(), "gamestate.yml");
        if (!gameStateFile.exists()) {
            try {
                gameStateFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create gamestate.yml!");
                e.printStackTrace();
            }
        }
        gameStateConfig = YamlConfiguration.loadConfiguration(gameStateFile);

        //loadGameState();//don't do this automatically

        getLogger().info("ZappierGames - Now running!");
        saveDefaultConfig();
        loadItemValues();
        createVoidWorld("skybattle_world");

        Skybattle.init(instance);
        ParkourRace.init(instance);

        // Register commands
        this.getCommand("loothunt").setExecutor(new LoothuntCommand());
        this.getCommand("manhunt").setExecutor(new ManhuntCommand());
        this.getCommand("getcompass").setExecutor(new GetcompassCommand());
        this.getCommand("gc").setExecutor(new GetcompassCommand());
        this.getCommand("trackplayer").setExecutor(new TrackplayerCommand());
        this.getCommand("trp").setExecutor(new TrackplayerCommand());
        this.getCommand("getscore").setExecutor(new GetScoreCommand());
        this.getCommand("globalkeepinventory").setExecutor(new GlobalKeepInventoryCommand());
        this.getCommand("GUI").setExecutor(new openGUICommand());
        this.getCommand("loadstate").setExecutor(new LoadStateCommand());
        this.getCommand("loadstate").setTabCompleter(new LoadStateCommand());
        this.getCommand("getinfinibundle").setExecutor(new GetInfinibundleCommand());
        this.getCommand("getinfinibundle").setTabCompleter(new GetInfinibundleCommand());

        // Register events
        getServer().getPluginManager().registerEvents(new LootHuntKillListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new AutoTNTListener(this, "skybattle_world"), this);
        getServer().getPluginManager().registerEvents(new PlayerSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new CustomPearlsListener(this), this);
        getServer().getPluginManager().registerEvents(new CreeperSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new DamageHandler(), this);
        getServer().getPluginManager().registerEvents(new InfinibundleListener(), this);
        getServer().getPluginManager().registerEvents(new CompassTrackerListener(this), this);
        getServer().getPluginManager().registerEvents(new TrackerGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ManhuntEnforcement(), this);
        getServer().getPluginManager().registerEvents(new ItemValueActionBarListener(), this);

        // Team colors
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        NamedTextColor[] teamColors = {NamedTextColor.GREEN, NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE,
                NamedTextColor.DARK_PURPLE, NamedTextColor.BLUE, NamedTextColor.AQUA, NamedTextColor.DARK_GRAY};
        for (int i = 0; i < teamList.length; i++) {
            Team team = scoreboard.getTeam(teamList[i]);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamList[i]);
            }
            team.color(teamColors[i]);
        }

        // Main game loop
        gameTask = new BukkitRunnable() {
            int updateCompassTimer = 20;

            @Override
            public void run() {
                if (shouldSave) {
                    saveGameState();
                    shouldSave = false;
                    Bukkit.broadcastMessage("Manhunt/Loothunt game state saved.");
                }
                updateCompassTimer--;
                if (updateCompassTimer <= 0) {
                    updateCompassTimer = 10;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Location playerLoc = player.getLocation();
                        int[] playerVec = {(int) playerLoc.getX(), (int) playerLoc.getY(), (int) playerLoc.getZ()};
                        String trackingDimension = playerLoc.getWorld().getName();
                        if (trackingDimension == null) {
                            trackingDimension = "world";
                        }
                        String playerLocStr = player.getName() + " # " + trackingDimension;
                        playerPositions.put(playerLocStr, playerVec);

                        if (trackingPairs.get(player.getName()) != null && Bukkit.getPlayer(trackingPairs.get(player.getName())) != null) {
                            String targetLocStr = trackingPairs.get(player.getName()) + " # " + trackingDimension;
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
                                    default:
                                        targetDimension = targetWorld;
                                        break;
                                }
                                Location trackedLocation = new Location(player.getWorld(), targetPos[0], targetPos[1], targetPos[2]);

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
                                }
                            }
                        }
                    }
                }

                if (gameMode == LOOTHUNT) {
                    LootHunt.run();
                } else if (gameMode == 1 || gameMode == 2 || gameMode == 3) {
                    Manhunt.run();
                } else if (gameMode == 10) {
                    World skybattleWorld = Bukkit.getWorld("skybattle_world");
                    if (skybattleWorld != null) {
                        Skybattle.run(skybattleWorld);
                    }
                } else if (gameMode == 20) {
                    World skybattleWorld = Bukkit.getWorld("skybattle_world");
                    if (skybattleWorld != null) {
                        ParkourRace.run(skybattleWorld);
                    }
                } else if (gameMode == 30) {
                    BiomeParkour.run();
                }
            }
        };
        gameTask.runTaskTimer(this, 0L, 1L);
    }

    @Override
    public void onDisable() {
        if (gameTask != null) {
            gameTask.cancel();
        }
        // Stop web server if still running
        stopResultsWebServer();
        saveGameState();
        getLogger().info("ZappierGames - Now disabled");
    }

    private class SingleFileResultsHandler implements HttpHandler {
        private final String allowedFileName;

        SingleFileResultsHandler(String fileName) {
            this.allowedFileName = fileName;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/")) path = path.substring(1);

            if (!path.equals(allowedFileName)) {
                String msg = "404 - Not found";
                exchange.sendResponseHeaders(404, msg.getBytes().length);
                exchange.getResponseBody().write(msg.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            // Now we can safely call getDataFolder() because this is non-static
            File file = new File(ZappierGames.this.getDataFolder(), path);

            if (!file.exists() || !file.isFile()) {
                String msg = "File no longer available";
                exchange.sendResponseHeaders(404, msg.getBytes().length);
                exchange.getResponseBody().write(msg.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, file.length());
            java.nio.file.Files.copy(file.toPath(), exchange.getResponseBody());
            exchange.getResponseBody().close();
        }
    }


    public void startResultsWebServer(String htmlFileName) {
        stopResultsWebServer();

        try {
            resultsWebServer = HttpServer.create(new InetSocketAddress(WEB_PORT), 0);

            // Pass 'this' implicitly since handler is non-static inner class
            resultsWebServer.createContext("/", new SingleFileResultsHandler(htmlFileName));

            resultsWebServer.setExecutor(null);
            resultsWebServer.start();

            getLogger().info("Temporary results web server started on port " + WEB_PORT + " for file: " + htmlFileName);

            Bukkit.getScheduler().runTaskLater(this, this::stopResultsWebServer,
                    RESULTS_AVAILABLE_MINUTES * 60 * 20L);

            /*String serverIp = Bukkit.getIp();
            if (serverIp == null || serverIp.isEmpty() || serverIp.equals("0.0.0.0")) {
                serverIp = "your-server-ip"; // or fetch external IP if needed
            }
            String url = "http://" + serverIp + ":" + WEB_PORT + "/" + htmlFileName;

            Component message = Component.text("Loot Hunt results ready! ", NamedTextColor.GREEN)
                    .append(Component.text("Click here to view (available for " + RESULTS_AVAILABLE_MINUTES + " min)", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.AQUA))));*/

            //Bukkit.broadcast(message);
            //getLogger().info("Results URL: " + url);

        } catch (IOException e) {
            getLogger().severe("Failed to start temporary results web server: " + e.getMessage());
        }
    }

    public void stopResultsWebServer() {
        if (resultsWebServer != null) {
            resultsWebServer.stop(0);
            resultsWebServer = null;
            getLogger().info("Temporary results web server stopped (no longer using resources)");
        }
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




    public void saveGameState() {
        // Manhunt static fields
        gameStateConfig.set("manhunt.showTrackerDimension", Manhunt.showTrackerDimension);
        gameStateConfig.set("manhunt.shoutHunterTarget",    Manhunt.shoutHunterTarget);
        gameStateConfig.set("manhunt.presidentDeathLink",   Manhunt.presidentDeathLink);
        gameStateConfig.set("manhunt.presidentWearArmor",   Manhunt.presidentWearArmor);
        gameStateConfig.set("manhunt.bodyguardRespawn",     Manhunt.bodyguardRespawn);
        gameStateConfig.set("manhunt.bodyguardHpBonus",     Manhunt.bodyguardHpBonus);
        gameStateConfig.set("manhunt.netherLavaPvP",        Manhunt.netherLavaPvP);
        gameStateConfig.set("manhunt.netherCobwebPvP",        Manhunt.netherCobwebPvP);
        gameStateConfig.set("manhunt.allowSpears",          Manhunt.allowSpears);
        gameStateConfig.set("manhunt.bedBombing",           Manhunt.bedBombing);
        gameStateConfig.set("manhunt.neverBedBomb",           Manhunt.neverBedBomb);
        gameStateConfig.set("manhunt.anchorBombing",        Manhunt.anchorBombing);
        gameStateConfig.set("manhunt.funtimer",             Manhunt.funtimer);

        // Twists enabled state
        for (Manhunt.manhuntTwist twist : Manhunt.manhuntTwists) {
            gameStateConfig.set("manhunt.twists." + twist.name.replace(" ", "_"), twist.enabled);
        }

        // playerDeaths
        gameStateConfig.createSection("manhunt.playerDeaths", Manhunt.playerDeaths);

        // LootHunt / Infinibundle related (select what actually needs persistence)
        gameStateConfig.set("loothunt.noPvP", LootHunt.noPvP);
        gameStateConfig.set("loothunt.paused", true);

        gameStateConfig.createSection("loothunt.playerKillCounts", LootHunt.playerKillCounts);
        gameStateConfig.createSection("loothunt.playerDeathCounts", LootHunt.playerDeathCounts);

        // Infinibundle teamStorages (now persisted with ItemStack serialization)
        for (Map.Entry<String, List<ItemStack>> entry : InfinibundleListener.teamStorages.entrySet()) {
            List<Map<String, Object>> serializedItems = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                if (item != null) {
                    serializedItems.add(item.serialize());
                } else {
                    serializedItems.add(null); // Handle null items if possible, though rare
                }
            }
            gameStateConfig.set("infinibundle.teamStorages." + entry.getKey(), serializedItems);
        }

        // ZappierGames main fields
        gameStateConfig.set("core.gameMode", ZappierGames.gameMode);
        gameStateConfig.set("core.timer", ZappierGames.timer);
        gameStateConfig.set("core.loothuntDuration", ZappierGames.loothuntDuration);
        gameStateConfig.set("core.noPvP", ZappierGames.noPvP);           // or use LootHunt.noPvP — pick one source of truth
        gameStateConfig.set("core.borderSize", ZappierGames.borderSize);

        // trackingPairs (String → String)
        gameStateConfig.createSection("core.trackingPairs", ZappierGames.trackingPairs);

        // playerPositions (String → int[3]  →  x,y,z)
        Map<String, List<Integer>> serializedPositions = new HashMap<>();
        for (Map.Entry<String, int[]> entry : ZappierGames.this.playerPositions.entrySet()) {
            List<Integer> coords = new ArrayList<>();
            int[] pos = entry.getValue();
            if (pos != null && pos.length >= 3) {
                coords.add(pos[0]);
                coords.add(pos[1]);
                coords.add(pos[2]);
            }
            serializedPositions.put(entry.getKey(), coords);
        }
        gameStateConfig.createSection("core.playerPositions", serializedPositions);

        // playerScores (UUID → Integer)
        Map<String, Integer> serializedScores = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : ZappierGames.this.playerScores.entrySet()) {
            serializedScores.put(entry.getKey().toString(), entry.getValue());
        }
        gameStateConfig.createSection("core.playerScores", serializedScores);

        // BossBar state (title, color, style, progress)
        if (ZappierGames.globalBossBar != null) {
            gameStateConfig.set("core.bossbar.title", ZappierGames.globalBossBar.getTitle());
            gameStateConfig.set("core.bossbar.color", ZappierGames.globalBossBar.getColor().name());
            gameStateConfig.set("core.bossbar.style", ZappierGames.globalBossBar.getStyle().name());
            gameStateConfig.set("core.bossbar.progress", ZappierGames.globalBossBar.getProgress());
        }
        gameStateConfig.set("core.startTimerTicks", LootHunt.startTimer);

        try {
            gameStateConfig.save(gameStateFile);
            getLogger().info("Game state saved to gamestate.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to save game state!");
            e.printStackTrace();
        }
    }

    public void loadGameState() {
        if (!gameStateFile.exists()) return;

        // Manhunt values
        Manhunt.showTrackerDimension = gameStateConfig.getInt("manhunt.showTrackerDimension", 1);
        Manhunt.shoutHunterTarget    = gameStateConfig.getInt("manhunt.shoutHunterTarget",    1);
        Manhunt.presidentDeathLink   = gameStateConfig.getInt("manhunt.presidentDeathLink",   -1);
        Manhunt.presidentWearArmor   = gameStateConfig.getInt("manhunt.presidentWearArmor",   -1);
        Manhunt.bodyguardRespawn     = gameStateConfig.getInt("manhunt.bodyguardRespawn",     -1);
        Manhunt.bodyguardHpBonus     = gameStateConfig.getInt("manhunt.bodyguardHpBonus",     0);
        Manhunt.netherLavaPvP        = gameStateConfig.getInt("manhunt.netherLavaPvP",        -1);
        Manhunt.netherCobwebPvP        = gameStateConfig.getInt("manhunt.netherCobwebPvP",        -1);
        Manhunt.allowSpears          = gameStateConfig.getInt("manhunt.allowSpears",          -1);
        Manhunt.bedBombing           = gameStateConfig.getInt("manhunt.bedBombing",           -1);
        Manhunt.neverBedBomb           = gameStateConfig.getInt("manhunt.neverBedBomb",           -1);
        Manhunt.anchorBombing        = gameStateConfig.getInt("manhunt.anchorBombing",        -1);
        Manhunt.funtimer             = gameStateConfig.getInt("manhunt.funtimer",             0);

        // Twists
        for (Manhunt.manhuntTwist twist : Manhunt.manhuntTwists) {
            String key = "manhunt.twists." + twist.name.replace(" ", "_");
            if (gameStateConfig.isBoolean(key)) {
                twist.enabled = gameStateConfig.getBoolean(key, false);
            }
        }

        // playerDeaths
        if (gameStateConfig.isConfigurationSection("manhunt.playerDeaths")) {
            Manhunt.playerDeaths.clear();
            for (String uuid : gameStateConfig.getConfigurationSection("manhunt.playerDeaths").getKeys(false)) {
                Manhunt.playerDeaths.put(uuid, gameStateConfig.getInt("manhunt.playerDeaths." + uuid, 0));
            }
        }

        // LootHunt
        LootHunt.noPvP  = gameStateConfig.getBoolean("loothunt.noPvP", false);
        LootHunt.paused = gameStateConfig.getBoolean("loothunt.paused", false);

        // playerKillCounts and playerDeathCounts (now loaded)
        if (gameStateConfig.isConfigurationSection("loothunt.playerKillCounts")) {
            LootHunt.playerKillCounts.clear();
            for (String key : gameStateConfig.getConfigurationSection("loothunt.playerKillCounts").getKeys(false)) {
                LootHunt.playerKillCounts.put(key, gameStateConfig.getInt("loothunt.playerKillCounts." + key, 0));
            }
        }
        if (gameStateConfig.isConfigurationSection("loothunt.playerDeathCounts")) {
            LootHunt.playerDeathCounts.clear();
            for (String key : gameStateConfig.getConfigurationSection("loothunt.playerDeathCounts").getKeys(false)) {
                LootHunt.playerDeathCounts.put(key, gameStateConfig.getInt("loothunt.playerDeathCounts." + key, 0));
            }
        }

        // Infinibundle teamStorages (now loaded with ItemStack deserialization)
        // In loadGameState(), inside the teamStorages loading block:

        if (gameStateConfig.isConfigurationSection("infinibundle.teamStorages")) {
            InfinibundleListener.teamStorages.clear();

            for (String teamKey : gameStateConfig.getConfigurationSection("infinibundle.teamStorages").getKeys(false)) {
                // Use the actual return type of getMapList
                List<Map<?, ?>> rawList = gameStateConfig.getMapList("infinibundle.teamStorages." + teamKey);

                List<ItemStack> items = new ArrayList<>();

                for (Map<?, ?> rawMap : rawList) {
                    if (rawMap == null) {
                        items.add(null);
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) rawMap;

                    try {
                        items.add(ItemStack.deserialize(itemMap));
                    } catch (Exception e) {
                        getLogger().warning("Failed to deserialize ItemStack for team " + teamKey + ": " + e.getMessage());
                    }
                }

                InfinibundleListener.teamStorages.put(teamKey, items);
            }
        }

        ZappierGames.gameMode         = gameStateConfig.getInt("core.gameMode", -1);
        ZappierGames.timer            = gameStateConfig.getInt("core.timer", 0);
        ZappierGames.loothuntDuration = gameStateConfig.getDouble("core.loothuntDuration", 0);
        ZappierGames.noPvP            = gameStateConfig.getBoolean("core.noPvP", false);
        ZappierGames.borderSize       = gameStateConfig.getInt("core.borderSize", 2500);

        // trackingPairs
        if (gameStateConfig.isConfigurationSection("core.trackingPairs")) {
            ZappierGames.trackingPairs.clear();
            for (String key : gameStateConfig.getConfigurationSection("core.trackingPairs").getKeys(false)) {
                String value = gameStateConfig.getString("core.trackingPairs." + key);
                if (value != null) {
                    ZappierGames.trackingPairs.put(key, value);
                }
            }
        }

        // playerPositions
        if (gameStateConfig.isConfigurationSection("core.playerPositions")) {
            ZappierGames.this.playerPositions.clear();
            for (String playerKey : gameStateConfig.getConfigurationSection("core.playerPositions").getKeys(false)) {
                List<Integer> coords = gameStateConfig.getIntegerList("core.playerPositions." + playerKey);
                if (coords.size() >= 3) {
                    int[] pos = new int[]{coords.get(0), coords.get(1), coords.get(2)};
                    ZappierGames.this.playerPositions.put(playerKey, pos);
                }
            }
        }

        // playerScores
        if (gameStateConfig.isConfigurationSection("core.playerScores")) {
            ZappierGames.this.playerScores.clear();
            for (String uuidStr : gameStateConfig.getConfigurationSection("core.playerScores").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    int score = gameStateConfig.getInt("core.playerScores." + uuidStr, 0);
                    ZappierGames.this.playerScores.put(uuid, score);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID in playerScores: " + uuidStr);
                }
            }
        }

        LootHunt.startTimer = gameStateConfig.getDouble("core.startTimerTicks", 240.0 * 20);
        // BossBar recreation
        String title    = gameStateConfig.getString("core.bossbar.title", "Title");
        String colorStr = gameStateConfig.getString("core.bossbar.color", "PURPLE");
        String styleStr = gameStateConfig.getString("core.bossbar.style", "SOLID");
        double progress = gameStateConfig.getDouble("core.bossbar.progress", 1.0);

        BarColor color;
        BarStyle style;
        try {
            color = BarColor.valueOf(colorStr);
        } catch (Exception e) {
            color = BarColor.PURPLE;
        }
        try {
            style = BarStyle.valueOf(styleStr);
        } catch (Exception e) {
            style = BarStyle.SOLID;
        }

        // Recreate boss bar
        ZappierGames.globalBossBar = Bukkit.createBossBar(title, color, style);

        double elapsed   = ZappierGames.timer;
        double total     = ZappierGames.loothuntDuration;

        if (total <= 0) {
            progress = 0.0;  // safety if duration not set
        } else if (ZappierGames.timer >= total) {
            progress = 0.0;  // or 1.0 depending on fill/drain style
        } else {
            // Most common: drain-style (starts full, empties)
            progress = 1.0 - (elapsed / total);

            // Alternative: fill-style (starts empty, fills up)
            // progress = elapsed / total;
        }

        // Always clamp – defense in depth
        progress = Math.max(0.0, Math.min(1.0, progress));

        ZappierGames.globalBossBar.setProgress(progress);

        getLogger().info("Game state loaded from gamestate.yml");
    }

    public void autoSaveGameState() {
        saveGameState();
    }
}
