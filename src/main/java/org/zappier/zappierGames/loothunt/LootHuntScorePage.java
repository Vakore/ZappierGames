package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.zappier.zappierGames.ZappierGames;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.zappier.zappierGames.loothunt.LootHunt.buildCollectionTooltip;
import static org.zappier.zappierGames.loothunt.LootHunt.collections;

public class LootHuntScorePage {
    private static Map<String, Integer> itemNameToId = null;

    private static int getItemIdByName(String name) {
        if (itemNameToId == null) {
            loadItemNameToIdMap();
        }
        return itemNameToId.getOrDefault(name, -1);
    }

    private static void loadItemNameToIdMap() {
        itemNameToId = new HashMap<>();
        try (InputStream is = LootHunt.class.getClassLoader().getResourceAsStream("items.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString()
                    .replaceAll("(?s)^\\s*mcData\\.items\\s*=\\s*", "")
                    .replace(";", "")
                    .trim();

            // Naive but effective for this format
            Pattern p = Pattern.compile("\"id\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher m = p.matcher(json);
            while (m.find()) {
                try {
                    int id = Integer.parseInt(m.group(1));
                    String name = m.group(2).toLowerCase(Locale.ROOT);
                    itemNameToId.put(name, id);
                } catch (NumberFormatException ignored) {}
            }

        } catch (Exception e) {
            ZappierGames.getInstance().getLogger().warning("Could not parse items.txt → " + e.getMessage());
        }
    }

    private static String getItemSpriteBase64() {
        try (InputStream is = LootHunt.class.getClassLoader().getResourceAsStream("itemIconsBase64.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            return reader.lines().collect(Collectors.joining(""));
        } catch (Exception e) {
            ZappierGames.getInstance().getLogger().warning("Failed to load itemIconsBase64.txt: " + e.getMessage());
            return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="; // tiny fallback 1x1
        }
    }

    private static void appendInventorySlot(StringBuilder sb, ItemStack item) {
        sb.append("<div class=\"slot\">");  // slot can stay 16px or 18px with padding

        if (item != null && item.getType() != Material.AIR) {
            String nameLower = item.getType().name().toLowerCase(Locale.ROOT);
            int numericId = getItemIdByName(nameLower);

            if (numericId >= 0 && numericId < 1296) {
                int col = numericId % 36;
                int row = numericId / 36;
                int offsetX = -col * 16;
                int offsetY = -row * 16;

                sb.append("<div class=\"item-sprite\" ")
                        .append("style=\"background-position: ").append(offsetX).append("px ").append(offsetY).append("px;\" ")
                        .append("title=\"").append(escapeHtml(nameLower)).append("\"></div>");

                if (item.getAmount() > 1) {
                    sb.append("<span style=\"position:absolute; bottom:0; right:1px; color:white; text-shadow:1px 1px #000; font-size:9px; font-weight:bold;\">")
                            .append(item.getAmount())
                            .append("</span>");
                }
            } else {
                sb.append("<div style=\"width:16px;height:16px;background:#333;color:#c66;font-size:9px;line-height:16px;text-align:center;\">?</div>");
            }
        }

        sb.append("</div>");
    }

    public static void generateResultsHTML(Map<String, Map<String, Double>> teamItemCounts,
                                           Map<String, List<LootHunt.PlayerResult>> teamPlayers,
                                           Map<String, Map<String, List<LootHunt.ItemEntry>>> teamStorages,
                                           long worldSeed) {

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File htmlFile = new File(ZappierGames.getInstance().getDataFolder(),
                "loothunt-results-" + timestamp + ".html");

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>Loot Hunt Results - ").append(escapeHtml(timestamp)).append("</title>\n")
                .append("    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n")
                .append("    <style>\n")
                .append("        .item-sprite {\n")
                .append("            width: 16px;\n")
                .append("            height: 16px;\n")
                .append("            background-image: url('").append(getItemSpriteBase64()).append("');\n")
                .append("            background-size: 576px 576px;\n")   // adjust if your sheet is different size
                .append("            image-rendering: pixelated;\n")
                .append("        }\n")
                .append("        body { font-family: Arial, sans-serif; background: #0f0f1a; color: #e0e0ff; margin: 0; padding: 20px; }\n")
                .append("        h1, h2, h3 { text-align: center; color: #ffd700; text-shadow: 0 0 10px #ffaa00; }\n")
                .append("        .team { background: #1a1a2e; border-radius: 10px; padding: 20px; margin: 20px auto; max-width: 1200px; box-shadow: 0 0 20px rgba(100,100,255,0.3); }\n")
                .append("        table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n")
                .append("        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #333366; }\n")
                .append("        th { background: #2a2a4a; }\n")
                .append("        tr:hover { background: #25253f; }\n")
                .append("        .skin-head { width: 80px; height: 80px; image-rendering: pixelated; vertical-align: middle; }\n")
                .append("        .skin-body { width: 120px; height: 180px; image-rendering: pixelated; vertical-align: middle; }\n")
                .append("        .score { font-weight: bold; color: #00ff88; font-size: 1.2em; }\n")
                .append("        .details { color: #aaaaff; font-size: 0.9em; }\n")
                .append("        .collection { cursor: help; color: #55ff55; }\n")
                .append("        .kills { color: #ff5555; }\n")
                .append("        .deaths { color: #ff7777; }\n")
                .append("        .inventory-table { margin-top: 10px; }\n")
                .append("        .item-row { font-size: 0.85em; }\n")
                .append("        .nested { margin-left: 20px; font-style: italic; }\n")
                .append("        .inventory-grid { display: grid; grid-template-columns: repeat(9, 32px); gap: 2px; background: #444; padding: 5px; border: 1px solid #666; }\n")
                .append("        .slot { position: relative; width: 32px; height: 32px; background: #888; }\n")
                .append("        .slot img { width: 32px; height: 32px; }\n")
                .append("        .slot span { position: absolute; bottom: 0; right: 0; color: white; text-shadow: 1px 1px black; font-size: 0.8em; }\n")
                .append("        .armor-slots { display: grid; grid-template-columns: 32px; gap: 2px; }\n")
                .append("        .seed-info { text-align: center; color: #aaaaff; font-size: 0.95em; margin-top: 5px; }\n")
                .append("        .offhand-slot { width: 32px; height: 32px; }\n")
                .append("        .chart-container { background: #12122a; border: 1px solid #333366; border-radius: 6px; padding: 15px; margin-top: 10px; }\n")
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <h1>Loot Hunt Results</h1>\n")
                .append("    <p style=\"text-align:center\">Game finished at ").append(escapeHtml(timestamp)).append("</p>\n")
                .append("    <p class=\"seed-info\">World Seed: ").append(worldSeed).append("</p>\n");

        List<Map.Entry<String, Map<String, Double>>> sortedTeams = teamItemCounts.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().values().stream().mapToDouble(Double::doubleValue).sum(),
                        a.getValue().values().stream().mapToDouble(Double::doubleValue).sum()
                ))
                .toList();

        appendPositionMap(sb, teamPlayers);
        appendCombinedScoreChart(sb, teamPlayers, sortedTeams.stream().map(Map.Entry::getKey).toList());

        for (var teamEntry : sortedTeams) {
            String teamName = teamEntry.getKey();
            Map<String, Double> items = teamEntry.getValue();
            double totalScore = calculateTotalScoreWithBonuses(items, teamName);

            sb.append("    <div class=\"team\">\n")
                    .append("        <h2>").append(escapeHtml(teamName))
                    .append(" – <span class=\"score\">").append(String.format("%.1f", totalScore)).append("</span></h2>\n");

            // Collections - updated for itemGroups
            sb.append("        <h3>Collections</h3>\n");
            for (LootHunt.Collection coll : collections.values()) {
                long count = coll.itemGroups.stream()
                        .filter(group -> group.stream().anyMatch(items::containsKey))
                        .count();
                boolean complete = count >= coll.itemGroups.size();
                if (coll.quest && !complete) {
                    continue; // quest collection not completed by this team - hidden from results
                }
                String status = (coll.type.equals("complete") && complete)
                        ? "COMPLETE" : count + "/" + coll.itemGroups.size();
                int bonus = calculateCollectionBonus(coll, (int) count);

                sb.append("        <p class=\"collection\" title=\"")
                        .append(escapeHtml(buildCollectionTooltip(coll, items)))
                        .append("\">")
                        .append(escapeHtml(coll.name)).append(": ").append(status)
                        .append(" (+").append(bonus).append(" bonus)</p>\n");
            }

            // Players
            sb.append("        <h3>Players</h3>\n")
                    .append("        <table><tr><th>Player</th><th>Skin</th><th>Kills / Deaths</th><th>Personal Score</th><th>Inventory Visual</th><th>Inventory List</th></tr>\n");

            List<LootHunt.PlayerResult> players = teamPlayers.getOrDefault(teamName, new ArrayList<>());
            for (LootHunt.PlayerResult pr : players) {
                String headUrl = "https://visage.surgeplay.com/head/128/" + pr.uuid;
                String bodyUrl = "https://visage.surgeplay.com/full/384/" + pr.uuid + "?y=15&p=-18";

                sb.append("        <tr>")
                        .append("<td>").append(escapeHtml(pr.name)).append("</td>")
                        .append("<td><img class=\"skin-head\" src=\"").append(headUrl).append("\" alt=\"Head\"> ")
                        .append("<img class=\"skin-body\" src=\"").append(bodyUrl).append("\" alt=\"Body\"></td>")
                        .append("<td><span class=\"kills\">").append(pr.kills).append("</span> / ")
                        .append("<span class=\"deaths\">").append(pr.deaths).append("</span></td>")
                        .append("<td class=\"score\">").append(String.format("%.1f", pr.personalScore)).append("</td>")
                        .append("<td>");

                // Inventory visual - main inventory grid (9x3 upper + hotbar)
                sb.append("<div class=\"inventory-grid\">");
                for (int i = 9; i < 36; i++) { // Upper inventory
                    appendInventorySlot(sb, pr.inventoryContents[i]);
                }
                sb.append("</div>");

                sb.append("<div class=\"inventory-grid\" style=\"margin-top: 5px;\">"); // Hotbar
                for (int i = 0; i < 9; i++) {
                    appendInventorySlot(sb, pr.inventoryContents[i]);
                }
                sb.append("</div>");

                // Armor slots
                sb.append("<div class=\"armor-slots\" style=\"margin-top: 10px;\">");
                appendInventorySlot(sb, pr.inventoryContents[39]); // Helmet
                appendInventorySlot(sb, pr.inventoryContents[38]); // Chestplate
                appendInventorySlot(sb, pr.inventoryContents[37]); // Leggings
                appendInventorySlot(sb, pr.inventoryContents[36]); // Boots
                sb.append("</div>");

                // Offhand
                sb.append("<div class=\"offhand-slot\" style=\"margin-top: 5px;\">");
                appendInventorySlot(sb, pr.inventoryContents[40]);
                sb.append("</div>");

                sb.append("</td><td>");

                // Inventory list (alphabetical)
                sb.append("<table class=\"inventory-table\">");
                List<String> sortedPersonal = new ArrayList<>(pr.personalInventory.keySet());
                Collections.sort(sortedPersonal);
                for (String itemId : sortedPersonal) {
                    List<LootHunt.ItemEntry> entries = pr.personalInventory.get(itemId);
                    int totalQty = entries.stream().mapToInt(e -> e.quantity).sum();
                    double totalPts = entries.stream().mapToDouble(e -> e.points).sum();
                    String sources = entries.stream().map(e -> e.source).distinct().collect(Collectors.joining(", "));
                    sb.append("<tr class=\"item-row\"><td>").append(escapeHtml(itemId)).append("</td><td>x").append(totalQty)
                            .append("</td><td>").append(String.format("%.1f", totalPts)).append(" pts</td><td title=\"").append(escapeHtml(sources)).append("\">Sources</td></tr>");
                }
                sb.append("</table></td></tr>\n");
            }
            sb.append("        </table>\n");

            // Score History Chart
            appendScoreHistoryChart(sb, teamName, players);

            // Team Storage (alphabetical, with sources)
            sb.append("        <h3>Team Infinibundle Storage</h3>\n")
                    .append("        <table><tr><th>Item</th><th>Quantity</th><th>Points</th><th>Sources</th></tr>\n");
            Map<String, List<LootHunt.ItemEntry>> storage = teamStorages.getOrDefault(teamName, new HashMap<>());
            List<String> sortedStorage = new ArrayList<>(storage.keySet());
            Collections.sort(sortedStorage);
            for (String itemId : sortedStorage) {
                List<LootHunt.ItemEntry> entries = storage.get(itemId);
                int totalQty = entries.stream().mapToInt(e -> e.quantity).sum();
                double totalPts = entries.stream().mapToDouble(e -> e.points).sum();
                String sources = entries.stream().map(e -> e.source).distinct().collect(Collectors.joining("<br>"));
                sb.append("        <tr><td>").append(escapeHtml(itemId))
                        .append("</td><td>x").append(totalQty)
                        .append("</td><td>").append(String.format("%.1f", totalPts))
                        .append("</td><td>").append(sources).append("</td></tr>\n");
            }
            sb.append("        </table>\n");

            sb.append("    </div>\n");
        }

        sb.append("</body>\n")
                .append("</html>\n");

        // Write file
        try (FileWriter writer = new FileWriter(htmlFile)) {
            writer.write(sb.toString());
            ZappierGames.getInstance().getLogger().info("Loot Hunt results saved to: " + htmlFile.getAbsolutePath());
        } catch (IOException e) {
            ZappierGames.getInstance().getLogger().severe("Failed to save results HTML: " + e.getMessage());
        }

        // Broadcast clickable link
        String serverIp = Bukkit.getIp();
        if (serverIp.isEmpty()) serverIp = "localhost";
        int webPort = 8081; // Configurable?
        String url = "http://" + serverIp + ":" + webPort + "/" + htmlFile.getName();

        Component msg = Component.text("Loot Hunt results generated! ", NamedTextColor.GREEN)
                .append(Component.text("Click to view detailed scoreboard", NamedTextColor.YELLOW)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.AQUA))));

        Bukkit.broadcast(msg);

        ZappierGames.getInstance().startResultsWebServer(htmlFile.getName());

        ZappierGames.getInstance().getLogger().info("Results available at: " + url);
    }

    /**
     * Looks up the hex color of a registered scoreboard team (set via /loothunt jointeam),
     * for coloring that team's lines/markers consistently across the report. Returns null for
     * solo players or teams with no assigned color, so callers can fall back to a palette.
     */
    private static String getTeamColorHex(String teamName) {
        try {
            if (teamName == null) return null;
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) return null;
            TextColor color = team.color();
            if (color == null) return null;
            return String.format("#%06X", color.value());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Renders a set of world maps - one per unique dimension (world) visited during the game -
     * each showing every player's path within that dimension, overlaid on a biome-colored
     * background, with dot size reflecting that player's score at each recorded point. Lines are
     * colored per-team (matching the score graphs) so teammates' paths are visually grouped.
     */
    private static void appendPositionMap(StringBuilder sb, Map<String, List<LootHunt.PlayerResult>> teamPlayers) {
        Map<String, String> playerTeam = new HashMap<>();
        List<LootHunt.PlayerResult> allPlayers = new ArrayList<>();
        for (Map.Entry<String, List<LootHunt.PlayerResult>> e : teamPlayers.entrySet()) {
            for (LootHunt.PlayerResult pr : e.getValue()) {
                allPlayers.add(pr);
                playerTeam.put(pr.name, e.getKey());
            }
        }

        // Bucket every recorded snapshot by dimension, then by player, preserving time order -
        // player positions/coordinates aren't comparable across dimensions (Nether uses a 1:8
        // coordinate scale vs. the Overworld, for one), so each dimension needs its own map.
        Map<String, Map<String, List<LootHunt.ScoreSnapshot>>> byDimension = new LinkedHashMap<>();
        for (LootHunt.PlayerResult pr : allPlayers) {
            List<LootHunt.ScoreSnapshot> history = LootHunt.scoreHistory.getOrDefault(pr.name, Collections.emptyList());
            for (LootHunt.ScoreSnapshot snap : history) {
                byDimension.computeIfAbsent(snap.dimension, d -> new LinkedHashMap<>())
                        .computeIfAbsent(pr.name, k -> new ArrayList<>())
                        .add(snap);
            }
        }

        sb.append("    <div class=\"team\">\n")
                .append("        <h2>World Maps \u2014 Player Paths</h2>\n");

        if (byDimension.isEmpty()) {
            sb.append("        <p style=\"text-align:center;color:#8888aa;\">No position data recorded.</p>\n")
                    .append("    </div>\n");
            return;
        }

        List<String> dimensionOrder = new ArrayList<>(byDimension.keySet());
        dimensionOrder.sort(Comparator.comparingInt(LootHuntScorePage::dimensionSortRank).thenComparing(Comparator.naturalOrder()));

        Map<String, String[]> styles = computePlayerStyles(allPlayers, playerTeam);

        for (String dimension : dimensionOrder) {
            appendPositionMapForDimension(sb, dimension, byDimension.get(dimension), styles);
        }

        sb.append("    </div>\n");
    }

    private static int dimensionSortRank(String worldName) {
        try {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                return switch (w.getEnvironment()) {
                    case NORMAL -> 0;
                    case NETHER -> 1;
                    case THE_END -> 2;
                    default -> 3;
                };
            }
        } catch (Throwable ignored) {}
        return 4;
    }

    private static String dimensionDisplayName(String worldName) {
        try {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                return switch (w.getEnvironment()) {
                    case NORMAL -> "Overworld (" + worldName + ")";
                    case NETHER -> "The Nether (" + worldName + ")";
                    case THE_END -> "The End (" + worldName + ")";
                    default -> worldName;
                };
            }
        } catch (Throwable ignored) {}
        return worldName;
    }

    private static void appendPositionMapForDimension(StringBuilder sb, String dimension,
                                                      Map<String, List<LootHunt.ScoreSnapshot>> perPlayerHistory,
                                                      Map<String, String[]> styles) {
        sb.append("        <h3>").append(escapeHtml(dimensionDisplayName(dimension))).append("</h3>\n");

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (List<LootHunt.ScoreSnapshot> history : perPlayerHistory.values()) {
            for (LootHunt.ScoreSnapshot snap : history) {
                minX = Math.min(minX, snap.x);
                maxX = Math.max(maxX, snap.x);
                minZ = Math.min(minZ, snap.z);
                maxZ = Math.max(maxZ, snap.z);
            }
        }
        if (perPlayerHistory.isEmpty()) {
            sb.append("        <p style=\"text-align:center;color:#8888aa;\">No position data recorded in this dimension.</p>\n");
            return;
        }

        double spanX = Math.max(32, maxX - minX);
        double spanZ = Math.max(32, maxZ - minZ);
        minX -= spanX * 0.1;
        maxX += spanX * 0.1;
        minZ -= spanZ * 0.1;
        maxZ += spanZ * 0.1;
        spanX = maxX - minX;
        spanZ = maxZ - minZ;

        int imgSize = 800;
        int imgW, imgH;
        if (spanX >= spanZ) {
            imgW = imgSize;
            imgH = (int) Math.max(200, imgSize * (spanZ / spanX));
        } else {
            imgH = imgSize;
            imgW = (int) Math.max(200, imgSize * (spanX / spanZ));
        }

        World world = Bukkit.getWorld(dimension);
        BufferedImage img = renderWorldMapBackground(world, imgW, imgH, minX, maxX, minZ, maxZ);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        StringBuilder legend = new StringBuilder();
        double finalMinX = minX, finalMinZ = minZ, finalSpanX = spanX, finalSpanZ = spanZ;

        for (Map.Entry<String, List<LootHunt.ScoreSnapshot>> entry : perPlayerHistory.entrySet()) {
            String playerName = entry.getKey();
            List<LootHunt.ScoreSnapshot> history = entry.getValue();
            String hex = styles.getOrDefault(playerName, new String[]{"#aaaaaa", "[]"})[0];
            Color color = Color.decode(hex);

            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 170));
            int prevPx = -1, prevPy = -1;
            for (LootHunt.ScoreSnapshot snap : history) {
                int px = (int) ((snap.x - finalMinX) / finalSpanX * imgW);
                int py = (int) ((snap.z - finalMinZ) / finalSpanZ * imgH);
                if (prevPx >= 0) g.drawLine(prevPx, prevPy, px, py);
                prevPx = px;
                prevPy = py;
            }

            double maxScore = history.stream().mapToDouble(s -> s.score).max().orElse(0.0);
            g.setColor(color);
            for (LootHunt.ScoreSnapshot snap : history) {
                int px = (int) ((snap.x - finalMinX) / finalSpanX * imgW);
                int py = (int) ((snap.z - finalMinZ) / finalSpanZ * imgH);
                double frac = maxScore > 0 ? Math.max(0.15, snap.score / maxScore) : 0.15;
                int radius = (int) (3 + frac * 5);
                g.fillOval(px - radius, py - radius, radius * 2, radius * 2);
            }

            legend.append("<span style=\"color:").append(hex).append(";font-weight:bold;\">\u25CF ")
                    .append(escapeHtml(playerName)).append("</span>&nbsp;&nbsp;");
        }
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            String base64Png = Base64.getEncoder().encodeToString(baos.toByteArray());

            sb.append("        <p style=\"text-align:center;\">").append(legend).append("</p>\n")
                    .append("        <p style=\"text-align:center;color:#aaaaff;font-size:0.85em;\">Dot size reflects that player's score at the time it was recorded; background is colored by biome (approximate, based on currently loaded chunks). Line color matches team color.</p>\n")
                    .append("        <div style=\"text-align:center;\">\n")
                    .append("        <img src=\"data:image/png;base64,").append(base64Png)
                    .append("\" style=\"max-width:100%;border:1px solid #333366;border-radius:6px;\">\n")
                    .append("        </div>\n");
        } catch (IOException e) {
            ZappierGames.getInstance().getLogger().warning("Failed to render position map for " + dimension + ": " + e.getMessage());
            sb.append("        <p style=\"text-align:center;color:#8888aa;\">Position map failed to render.</p>\n");
        }
    }

    private static final Map<String, Color> biomeColorCache = new HashMap<>();

    /**
     * Paints a coarse biome-colored background for a position map. Only samples chunks that are
     * already loaded (skips unloaded ones with a neutral gray) so this can't force a burst of
     * synchronous chunk generation/loading on the main thread right after a game ends.
     */
    private static BufferedImage renderWorldMapBackground(World world, int imgW, int imgH, double minX, double maxX, double minZ, double maxZ) {
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 20, 35));
        g.fillRect(0, 0, imgW, imgH);

        if (world == null) {
            g.dispose();
            return img;
        }

        double spanX = maxX - minX;
        double spanZ = maxZ - minZ;

        int gridCols = Math.min(imgW, 100);
        int gridRows = Math.min(imgH, 100);
        double cellW = (double) imgW / gridCols;
        double cellH = (double) imgH / gridRows;

        for (int gx = 0; gx < gridCols; gx++) {
            for (int gz = 0; gz < gridRows; gz++) {
                double worldX = minX + (gx + 0.5) / gridCols * spanX;
                double worldZ = minZ + (gz + 0.5) / gridRows * spanZ;
                int blockX = (int) Math.floor(worldX);
                int blockZ = (int) Math.floor(worldZ);

                Color color;
                if (world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                    try {
                        int blockY = world.getHighestBlockYAt(blockX, blockZ);
                        String biomeKey = world.getBlockAt(blockX, blockY, blockZ).getBiome().getKey().getKey();
                        color = biomeToColor(biomeKey);
                    } catch (Throwable t) {
                        color = new Color(40, 40, 55);
                    }
                } else {
                    color = new Color(30, 30, 45); // unloaded chunk - don't force it to load just for the map
                }

                g.setColor(color);
                g.fillRect((int) (gx * cellW), (int) (gz * cellH), (int) Math.ceil(cellW), (int) Math.ceil(cellH));
            }
        }

        g.dispose();
        return img;
    }

    /**
     * Deterministic pastel color derived from the biome name's hash, so the same biome always
     * renders the same color on the map without needing a hand-maintained lookup table.
     */
    private static Color biomeToColor(String biomeKey) {
        return biomeColorCache.computeIfAbsent(biomeKey, key -> {
            int hash = key.hashCode();
            float hue = (Math.abs(hash) % 360) / 360f;
            return Color.getHSBColor(hue, 0.45f, 0.55f);
        });
    }

    /**
     * Assigns a color per player: teammates share their scoreboard team's color (or a palette
     * color if the team has none/is solo), and are distinguished from each other via a line dash
     * pattern instead. Returns playerName -> {colorHex, chartJsDashArray}.
     */
    private static Map<String, String[]> computePlayerStyles(List<LootHunt.PlayerResult> players, Map<String, String> playerTeam) {
        String[] palette = {"#ff5555", "#55ff55", "#5599ff", "#ffaa00", "#aa55ff", "#00ffcc", "#ff55aa", "#ffff55"};
        String[] dashPatterns = {"[]", "[6,3]", "[2,2]", "[8,3,2,3]", "[1,3]"};

        Map<String, String> teamColorAssignment = new LinkedHashMap<>();
        Map<String, Integer> teamPlayerCounter = new HashMap<>();
        int[] paletteIdx = {0};

        Map<String, String[]> result = new LinkedHashMap<>();
        for (LootHunt.PlayerResult pr : players) {
            String team = playerTeam.getOrDefault(pr.name, pr.name); // solo fallback: own pseudo-team
            String color = teamColorAssignment.computeIfAbsent(team, t -> {
                String c = getTeamColorHex(t);
                if (c == null) {
                    c = palette[paletteIdx[0] % palette.length];
                    paletteIdx[0]++;
                }
                return c;
            });
            int dashIdx = teamPlayerCounter.merge(team, 1, Integer::sum) - 1;
            result.put(pr.name, new String[]{color, dashPatterns[dashIdx % dashPatterns.length]});
        }
        return result;
    }

    /**
     * Appends a Chart.js line chart showing score history over time for the given players
     * (teammates share their team's color, distinguished from each other via line dash pattern),
     * with biome/structure context available in the tooltip on hover.
     */
    private static void appendScoreHistoryChartGeneric(StringBuilder sb, String chartId, String heading,
                                                       List<LootHunt.PlayerResult> players,
                                                       Map<String, String> playerTeam, String noDataMessage) {
        if (!heading.isEmpty()) {
            sb.append("        <h3>").append(escapeHtml(heading)).append("</h3>\n");
        }
        sb.append("        <div class=\"chart-container\">\n")
                .append("        <canvas id=\"").append(chartId).append("\" height=\"100\"></canvas>\n")
                .append("        </div>\n")
                .append("        <script>\n")
                .append("        (function() {\n")
                .append("            const ctx = document.getElementById('").append(chartId).append("').getContext('2d');\n")
                .append("            const datasets = [];\n");

        Map<String, String[]> styles = computePlayerStyles(players, playerTeam);
        boolean anyData = false;

        for (LootHunt.PlayerResult pr : players) {
            List<LootHunt.ScoreSnapshot> history = LootHunt.scoreHistory.getOrDefault(pr.name, Collections.emptyList());
            if (history.isEmpty()) continue;
            anyData = true;

            StringBuilder dataPoints = new StringBuilder("[");
            StringBuilder metaPoints = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                LootHunt.ScoreSnapshot snap = history.get(i);
                if (i > 0) { dataPoints.append(","); metaPoints.append(","); }
                double minutes = snap.tick / 60.0;
                dataPoints.append("{x:").append(minutes).append(",y:").append(snap.score).append("}");
                String biomeLabel = snap.biomes.isEmpty() ? "unknown" : escapeJs(String.join(", ", snap.biomes));
                String structLabel = snap.structures.isEmpty() ? "none" : escapeJs(String.join(", ", snap.structures));
                String dimLabel = escapeJs(dimensionDisplayName(snap.dimension));
                metaPoints.append("{biome:\"").append(biomeLabel).append("\",structure:\"").append(structLabel)
                        .append("\",dimension:\"").append(dimLabel).append("\"}");
            }
            dataPoints.append("]");
            metaPoints.append("]");

            String[] style = styles.getOrDefault(pr.name, new String[]{"#aaaaaa", "[]"});
            String color = style[0];
            String dash = style[1];

            sb.append("            datasets.push({\n")
                    .append("                label: \"").append(escapeJs(pr.name)).append("\",\n")
                    .append("                data: ").append(dataPoints).append(",\n")
                    .append("                meta: ").append(metaPoints).append(",\n")
                    .append("                borderColor: \"").append(color).append("\",\n")
                    .append("                backgroundColor: \"").append(color).append("\",\n")
                    .append("                borderDash: ").append(dash).append(",\n")
                    .append("                fill: false,\n")
                    .append("                tension: 0.2,\n")
                    .append("                pointRadius: 3\n")
                    .append("            });\n");
        }

        if (!anyData) {
            sb.append("            document.getElementById('").append(chartId)
                    .append("').outerHTML = '<p style=\"text-align:center;color:#8888aa;\">").append(escapeJs(noDataMessage)).append("</p>';\n")
                    .append("        })();\n")
                    .append("        </script>\n");
            return;
        }

        sb.append("            new Chart(ctx, {\n")
                .append("                type: 'line',\n")
                .append("                data: { datasets: datasets },\n")
                .append("                options: {\n")
                .append("                    responsive: true,\n")
                .append("                    parsing: false,\n")
                .append("                    interaction: { mode: 'nearest', axis: 'x', intersect: false },\n")
                .append("                    scales: {\n")
                .append("                        x: { type: 'linear', title: { display: true, text: 'Time (minutes elapsed)', color: '#aaaaff' }, ticks: { color: '#aaaaff' }, grid: { color: '#333366' } },\n")
                .append("                        y: { title: { display: true, text: 'Score', color: '#aaaaff' }, ticks: { color: '#aaaaff' }, grid: { color: '#333366' } }\n")
                .append("                    },\n")
                .append("                    plugins: {\n")
                .append("                        legend: { labels: { color: '#e0e0ff' } },\n")
                .append("                        tooltip: {\n")
                .append("                            callbacks: {\n")
                .append("                                label: function(c) {\n")
                .append("                                    const meta = c.dataset.meta[c.dataIndex];\n")
                .append("                                    return c.dataset.label + ': ' + c.parsed.y.toFixed(1) + ' pts (' + meta.dimension + ' - biome: ' + meta.biome + ', structure: ' + meta.structure + ')';\n")
                .append("                                }\n")
                .append("                            }\n")
                .append("                        }\n")
                .append("                    }\n")
                .append("                }\n")
                .append("            });\n")
                .append("        })();\n")
                .append("        </script>\n");
    }

    private static void appendScoreHistoryChart(StringBuilder sb, String teamName, List<LootHunt.PlayerResult> players) {
        Map<String, String> playerTeam = new HashMap<>();
        for (LootHunt.PlayerResult pr : players) playerTeam.put(pr.name, teamName);
        appendScoreHistoryChartGeneric(sb, "chart-" + sanitizeId(teamName), "Score History", players, playerTeam,
                "No score history recorded for this team.");
    }

    /**
     * A single combined chart at the top of the page with every player's score line, colored by
     * team so it's easy to see how teams are trending relative to each other at a glance.
     */
    private static void appendCombinedScoreChart(StringBuilder sb, Map<String, List<LootHunt.PlayerResult>> teamPlayers, List<String> teamOrder) {
        List<LootHunt.PlayerResult> allPlayers = new ArrayList<>();
        Map<String, String> playerTeam = new HashMap<>();
        for (String team : teamOrder) {
            for (LootHunt.PlayerResult pr : teamPlayers.getOrDefault(team, Collections.emptyList())) {
                allPlayers.add(pr);
                playerTeam.put(pr.name, team);
            }
        }

        sb.append("    <div class=\"team\">\n")
                .append("        <h2>All Players \u2014 Score History</h2>\n");
        appendScoreHistoryChartGeneric(sb, "chart-all-players", "", allPlayers, playerTeam,
                "No score history recorded.");
        sb.append("    </div>\n");
    }

    private static double calculateTotalScoreWithBonuses(Map<String, Double> items, String teamName) {
        double base = items.values().stream().mapToDouble(Double::doubleValue).sum();
        for (LootHunt.Collection coll : collections.values()) {
            int count = (int) coll.itemGroups.stream()
                    .filter(group -> group.stream().anyMatch(items::containsKey))
                    .count();
            base += calculateCollectionBonus(coll, count);
        }
        return base;
    }

    private static int calculateCollectionBonus(LootHunt.Collection coll, int count) {
        if ("progressive".equals(coll.type)) {
            if (!coll.progressiveScores.isEmpty() && count > 0) {
                return coll.progressiveScores.get(Math.min(count - 1, coll.progressiveScores.size() - 1));
            }
        } else if (count >= coll.itemGroups.size()) {
            return coll.completeBonus;
        }
        return 0;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static String sanitizeId(String s) {
        return s == null ? "team" : s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}