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
import java.nio.charset.StandardCharsets;
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
                int offsetX = -col * 32;
                int offsetY = -row * 32;

                sb.append("<div class=\"item-sprite\" ")
                        .append("style=\"background-position: ").append(offsetX).append("px ").append(offsetY).append("px;\" ")
                        .append("title=\"").append(escapeHtml(nameLower)).append("\"></div>");

                if (item.getAmount() > 1) {
                    sb.append("<span style=\"position:absolute; bottom:0; right:1px; color:white; text-shadow:1px 1px #000; font-size:9px; font-weight:bold;\">")
                            .append(item.getAmount())
                            .append("</span>");
                }
            } else {
                sb.append("<div style=\"width:32px;height:32px;background:#333;color:#c66;font-size:12px;line-height:32px;text-align:center;\">?</div>");
            }
        }

        sb.append("</div>");
    }

    public static void generateResultsHTML(Map<String, Map<String, Double>> teamItemCounts,
                                           Map<String, List<LootHunt.PlayerResult>> teamPlayers,
                                           Map<String, Map<String, List<LootHunt.ItemEntry>>> teamStorages,
                                           long worldSeed, String csvContent) {

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File htmlFile = new File(ZappierGames.getInstance().getDataFolder(),
                "loothunt-results-" + timestamp + ".html");

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("    <title>Loothunt Results - ").append(escapeHtml(timestamp)).append("</title>\n")
                .append("    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n")
                .append("    <style>\n")
                .append("        .item-sprite {\n")
                .append("            width: 32px;\n")
                .append("            height: 32px;\n")
                .append("            background-image: url('").append(getItemSpriteBase64()).append("');\n")
                .append("            background-size: 1152px 1152px;\n")   // 2x the 576px sheet, matching the 32px cells below
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
                .append("        #lh-tooltip { position: fixed; display: none; background: #1a1a2e; color: #ffd700; border: 1px solid #333366; border-radius: 4px; padding: 6px 10px; font-size: 0.85em; pointer-events: none; z-index: 9999; white-space: nowrap; box-shadow: 0 0 10px rgba(0,0,0,0.5); }\n")
                .append("        .lh-team-nav { text-align: center; margin: 15px auto; max-width: 1200px; }\n")
                .append("        .lh-team-tab { display: inline-block; margin: 3px; padding: 8px 14px; border-radius: 6px; background: #1a1a2e; border: 1px solid #333366; cursor: pointer; font-weight: bold; }\n")
                .append("        .lh-team-tab.active { box-shadow: 0 0 8px currentColor; }\n")
                .append("        .lh-nav-btn { background: #2a2a4a; color: #ffd700; border: 1px solid #333366; border-radius: 6px; padding: 8px 16px; cursor: pointer; font-weight: bold; font-size: 0.95em; margin: 0 8px; }\n")
                .append("        .lh-nav-btn:hover { background: #3a3a5a; }\n")
                .append("        .lh-scroll-section { max-height: 320px; overflow-y: auto; border: 1px solid #333366; border-radius: 6px; }\n")
                .append("    </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <div id=\"lh-tooltip\"></div>\n")
                .append("    <script>\n")
                .append("        function lhShowTooltip(evt, text) {\n")
                .append("            const tip = document.getElementById('lh-tooltip');\n")
                .append("            tip.textContent = text;\n")
                .append("            tip.style.display = 'block';\n")
                .append("            tip.style.left = (evt.clientX + 14) + 'px';\n")
                .append("            tip.style.top = (evt.clientY + 14) + 'px';\n")
                .append("        }\n")
                .append("        function lhHideTooltip() {\n")
                .append("            document.getElementById('lh-tooltip').style.display = 'none';\n")
                .append("        }\n")
                .append("        let lhCurrentTeam = 0;\n")
                .append("        let lhTeamNames = [];\n")
                .append("        function lhShowTeam(idx) {\n")
                .append("            const pages = document.querySelectorAll('.team-page');\n")
                .append("            if (pages.length === 0) return;\n")
                .append("            if (idx < 0) idx = pages.length - 1;\n")
                .append("            if (idx >= pages.length) idx = 0;\n")
                .append("            pages.forEach((el, i) => { el.style.display = (i === idx) ? '' : 'none'; });\n")
                .append("            document.querySelectorAll('.lh-team-tab').forEach((el, i) => { el.classList.toggle('active', i === idx); });\n")
                .append("            document.querySelectorAll('.lh-team-label').forEach(el => {\n")
                .append("                el.textContent = lhTeamNames[idx] + ' (' + (idx + 1) + ' / ' + pages.length + ')';\n")
                .append("            });\n")
                .append("            lhCurrentTeam = idx;\n")
                .append("            window.scrollTo({ top: document.getElementById('lh-team-nav-top').offsetTop - 10, behavior: 'smooth' });\n")
                .append("        }\n")
                .append("    </script>\n")
                .append("    <h1>Loothunt Results</h1>\n")
                .append("    <p style=\"text-align:center\">Game finished at ").append(escapeHtml(timestamp)).append("</p>\n")
                .append("    <p class=\"seed-info\">World Seed: ").append(worldSeed).append("</p>\n");

        if ((LootHunt.gameVersion != null && !LootHunt.gameVersion.isBlank()) ||
                (LootHunt.loothuntSeason != null && !LootHunt.loothuntSeason.isBlank())) {
            List<String> infoParts = new ArrayList<>();
            if (LootHunt.loothuntSeason != null && !LootHunt.loothuntSeason.isBlank()) {
                infoParts.add(escapeHtml(LootHunt.loothuntSeason));
            }
            if (LootHunt.gameVersion != null && !LootHunt.gameVersion.isBlank()) {
                infoParts.add("Minecraft " + escapeHtml(LootHunt.gameVersion));
            }
            sb.append("    <p class=\"seed-info\">").append(String.join(" - ", infoParts)).append("</p>\n");
        }

        if (csvContent != null && !csvContent.isBlank()) {
            String base64Csv = Base64.getEncoder().encodeToString(csvContent.getBytes(StandardCharsets.UTF_8));
            sb.append("    <p style=\"text-align:center;\">")
                    .append("<a download=\"loothunt-item-breakdown-").append(escapeHtml(timestamp)).append(".csv\" ")
                    .append("href=\"data:text/csv;base64,").append(base64Csv).append("\" ")
                    .append("style=\"display:inline-block;background:#2a2a4a;color:#ffd700;border:1px solid #333366;border-radius:6px;padding:10px 18px;text-decoration:none;font-weight:bold;\">")
                    .append("\u2b07 Download Item Breakdown (CSV)</a></p>\n");
        }

        List<Map.Entry<String, Map<String, Double>>> sortedTeams = teamItemCounts.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().values().stream().mapToDouble(Double::doubleValue).sum(),
                        a.getValue().values().stream().mapToDouble(Double::doubleValue).sum()
                ))
                .toList();

        appendPositionMap(sb, teamPlayers);
        appendCombinedScoreChart(sb, teamPlayers, sortedTeams.stream().map(Map.Entry::getKey).toList());

        List<String> teamNamesInOrder = sortedTeams.stream().map(Map.Entry::getKey).toList();
        if (!teamNamesInOrder.isEmpty()) {
            sb.append("    <div class=\"lh-team-nav\" id=\"lh-team-nav-top\">\n");
            for (String teamName : teamNamesInOrder) {
                String teamColor = getTeamColorHex(teamName);
                String colorStyle = teamColor != null ? " color:" + teamColor + ";" : "";
                sb.append("        <span class=\"lh-team-tab\" style=\"").append(colorStyle)
                        .append("\" onclick=\"lhShowTeam(").append(teamNamesInOrder.indexOf(teamName)).append(")\">")
                        .append(escapeHtml(teamName)).append("</span>\n");
            }
            sb.append("    </div>\n")
                    .append("    <script>lhTeamNames = [")
                    .append(teamNamesInOrder.stream().map(t -> "\"" + escapeJs(t) + "\"").collect(Collectors.joining(",")))
                    .append("]; document.addEventListener('DOMContentLoaded', function() { lhShowTeam(0); });</script>\n");
        }

        int teamIdx = 0;
        for (var teamEntry : sortedTeams) {
            String teamName = teamEntry.getKey();
            Map<String, Double> items = teamEntry.getValue();
            double totalScore = calculateTotalScoreWithBonuses(items, teamName);

            sb.append("    <div class=\"team team-page\" id=\"team-page-").append(teamIdx).append("\"")
                    .append(teamIdx == 0 ? "" : " style=\"display:none;\"").append(">\n")
                    .append("        <h2>").append(escapeHtml(teamName))
                    .append(" – <span class=\"score\">").append(String.format("%.1f", totalScore)).append("</span></h2>\n");

            // Collections - updated for itemGroups
            StringBuilder collectionLines = new StringBuilder();
            int totalCollectionPoints = 0;
            for (LootHunt.Collection coll : LootHunt.getSortedCollections()) {
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
                totalCollectionPoints += bonus;

                collectionLines.append("        <p class=\"collection\" title=\"")
                        .append(escapeHtml(buildCollectionTooltip(coll, items)))
                        .append("\">")
                        .append(escapeHtml(coll.name)).append(": ").append(status)
                        .append(" (+").append(bonus).append(" bonus)</p>\n");
            }
            sb.append("        <h3>Collections</h3>\n")
                    .append("        <p style=\"text-align:center;color:#55ff55;font-weight:bold;\">Total Collection Points \u2014 ")
                    .append(totalCollectionPoints).append("</p>\n")
                    .append(collectionLines);

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
                sb.append("<div class=\"lh-scroll-section\"><table class=\"inventory-table\">");
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
                sb.append("</table></div></td></tr>\n");
            }
            sb.append("        </table>\n");

            // Score History Chart
            appendScoreHistoryChart(sb, teamName, players);

            // Team navigation - cycle back and forth between teams without scrolling the whole page
            sb.append("        <p style=\"text-align:center;margin-top:15px;\">")
                    .append("<button class=\"lh-nav-btn\" onclick=\"lhShowTeam(lhCurrentTeam - 1)\">\u25c0 Prev Team</button>")
                    .append("<span class=\"lh-team-label\" style=\"color:#ffd700;font-weight:bold;\"></span>")
                    .append("<button class=\"lh-nav-btn\" onclick=\"lhShowTeam(lhCurrentTeam + 1)\">Next Team \u25b6</button>")
                    .append("</p>\n");

            // Team Storage (alphabetical, with sources)
            sb.append("        <h3>Team Infinibundle Storage</h3>\n")
                    .append("        <div class=\"lh-scroll-section\">\n")
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
            sb.append("        </table>\n")
                    .append("        </div>\n");

            sb.append("    </div>\n");
            teamIdx++;
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

    private static int positionMapCounter = 0;

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
        MapBackgroundResult bgResult = renderWorldMapBackground(world, imgW, imgH, minX, maxX, minZ, maxZ);

        String base64Bg;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bgResult.image, "png", baos);
            base64Bg = Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            ZappierGames.getInstance().getLogger().warning("Failed to render position map background for " + dimension + ": " + e.getMessage());
            sb.append("        <p style=\"text-align:center;color:#8888aa;\">Position map failed to render.</p>\n");
            return;
        }

        String mapId = "posmap-" + sanitizeId(dimension) + "-" + (positionMapCounter++);
        String containerId = mapId + "-container";
        StringBuilder checkboxes = new StringBuilder();
        StringBuilder overlayImgs = new StringBuilder();
        StringBuilder hoverDivs = new StringBuilder();
        double finalMinX = minX, finalMinZ = minZ, finalSpanX = spanX, finalSpanZ = spanZ;
        int playerIdx = 0;

        for (Map.Entry<String, List<LootHunt.ScoreSnapshot>> entry : perPlayerHistory.entrySet()) {
            String playerName = entry.getKey();
            List<LootHunt.ScoreSnapshot> history = entry.getValue();
            String hex = styles.getOrDefault(playerName, new String[]{"#aaaaaa", "[]"})[0];
            Color color = Color.decode(hex);

            // Each player's path/dots render onto their own transparent image so a checkbox can
            // toggle that one player on/off without needing to re-render anything.
            BufferedImage overlay = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D go = overlay.createGraphics();
            go.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            go.setStroke(new BasicStroke(2f));
            go.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 170));
            int prevPx = -1, prevPy = -1;
            for (LootHunt.ScoreSnapshot snap : history) {
                int px = (int) ((snap.x - finalMinX) / finalSpanX * imgW);
                int py = (int) ((snap.z - finalMinZ) / finalSpanZ * imgH);
                if (prevPx >= 0) go.drawLine(prevPx, prevPy, px, py);
                prevPx = px;
                prevPy = py;
            }

            double maxScore = history.stream().mapToDouble(s -> s.score).max().orElse(0.0);
            go.setColor(color);
            for (LootHunt.ScoreSnapshot snap : history) {
                int px = (int) ((snap.x - finalMinX) / finalSpanX * imgW);
                int py = (int) ((snap.z - finalMinZ) / finalSpanZ * imgH);
                double frac = maxScore > 0 ? Math.max(0.15, snap.score / maxScore) : 0.15;
                int radius = (int) (3 + frac * 5);
                go.fillOval(px - radius, py - radius, radius * 2, radius * 2);

                // Hover target for points where the player was inside a structure. Uses a data
                // attribute (read by the shared mousemove listener below) instead of the native
                // "title" tooltip - title tooltips only show the browser's help-cursor glyph
                // immediately and the actual text after a long hover delay, which read as broken.
                if (!snap.structures.isEmpty()) {
                    double leftPct = px * 100.0 / imgW;
                    double topPct = py * 100.0 / imgH;
                    String structLabel = escapeHtml(playerName + ": " + String.join(", ", snap.structures));
                    hoverDivs.append("<div class=\"lh-structure-dot\" data-tip=\"").append(structLabel)
                            .append("\" style=\"position:absolute; left:")
                            .append(String.format("%.3f", leftPct)).append("%; top:")
                            .append(String.format("%.3f", topPct)).append("%; width:14px; height:14px; ")
                            .append("margin-left:-7px; margin-top:-7px; border-radius:50%; cursor:pointer; z-index:5; ")
                            .append("border:1px dashed rgba(255,255,255,0.45);\"></div>\n");
                }
            }
            go.dispose();

            String imgId = mapId + "-p" + playerIdx;
            String base64Overlay;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(overlay, "png", baos);
                base64Overlay = Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (IOException e) {
                ZappierGames.getInstance().getLogger().warning("Failed to render position map overlay for " + playerName + ": " + e.getMessage());
                playerIdx++;
                continue;
            }

            overlayImgs.append("<img id=\"").append(imgId).append("\" src=\"data:image/png;base64,").append(base64Overlay)
                    .append("\" style=\"position:absolute; left:0; top:0; width:100%; height:100%; pointer-events:none;\">\n");

            checkboxes.append("<label style=\"color:").append(hex).append(";font-weight:bold;margin-right:14px;cursor:pointer;\">")
                    .append("<input type=\"checkbox\" checked onchange=\"document.getElementById('").append(imgId)
                    .append("').style.display = this.checked ? 'block' : 'none';\"> ")
                    .append(escapeHtml(playerName)).append("</label>");

            playerIdx++;
        }

        // Biome palette + grid for the hover lookup - built from the exact same grid used to
        // paint the background, so hovering anywhere on the map (that isn't a structure dot)
        // shows that tile's biome name.
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        StringBuilder gridJs = new StringBuilder("[");
        for (int row = 0; row < bgResult.gridRows; row++) {
            if (row > 0) gridJs.append(",");
            gridJs.append("[");
            for (int col = 0; col < bgResult.gridCols; col++) {
                if (col > 0) gridJs.append(",");
                String key = bgResult.biomeKeys[row][col];
                gridJs.append(key == null ? -1 : paletteIndex.computeIfAbsent(key, k -> paletteIndex.size()));
            }
            gridJs.append("]");
        }
        gridJs.append("]");

        StringBuilder paletteJs = new StringBuilder("[");
        boolean firstPalette = true;
        for (String key : paletteIndex.keySet()) {
            if (!firstPalette) paletteJs.append(",");
            firstPalette = false;
            paletteJs.append("\"").append(escapeJs(formatBiomeName(key))).append("\"");
        }
        paletteJs.append("]");

        sb.append("        <p style=\"text-align:center;\">").append(checkboxes).append("</p>\n")
                .append("        <p style=\"text-align:center;color:#aaaaff;font-size:0.85em;\">Dot size reflects that player's score at the time it was recorded. Hover the map for biome names, or a dashed circle for the structure a player was in at that point. Use the checkboxes above to show/hide individual players.</p>\n")
                .append("        <div id=\"").append(containerId).append("\" style=\"position:relative;display:inline-block;max-width:100%;\">\n")
                .append("        <img src=\"data:image/png;base64,").append(base64Bg)
                .append("\" style=\"display:block;width:100%;height:auto;border:1px solid #333366;border-radius:6px;\">\n")
                .append(overlayImgs)
                .append(hoverDivs)
                .append("        </div>\n")
                .append("        <script>\n")
                .append("        (function() {\n")
                .append("            const container = document.getElementById('").append(containerId).append("');\n")
                .append("            const biomePalette = ").append(paletteJs).append(";\n")
                .append("            const biomeGrid = ").append(gridJs).append(";\n")
                .append("            const gridCols = ").append(bgResult.gridCols).append(", gridRows = ").append(bgResult.gridRows).append(";\n")
                .append("            container.addEventListener('mousemove', function(e) {\n")
                .append("                const dot = e.target.closest('.lh-structure-dot');\n")
                .append("                if (dot) { lhShowTooltip(e, dot.dataset.tip); return; }\n")
                .append("                const rect = container.getBoundingClientRect();\n")
                .append("                const relX = (e.clientX - rect.left) / rect.width;\n")
                .append("                const relY = (e.clientY - rect.top) / rect.height;\n")
                .append("                if (relX < 0 || relX > 1 || relY < 0 || relY > 1) { lhHideTooltip(); return; }\n")
                .append("                const col = Math.min(gridCols - 1, Math.floor(relX * gridCols));\n")
                .append("                const row = Math.min(gridRows - 1, Math.floor(relY * gridRows));\n")
                .append("                const idx = biomeGrid[row][col];\n")
                .append("                lhShowTooltip(e, idx >= 0 ? biomePalette[idx] : 'Unexplored');\n")
                .append("            });\n")
                .append("            container.addEventListener('mouseleave', lhHideTooltip);\n")
                .append("        })();\n")
                .append("        </script>\n");
    }

    private static final Map<String, Color> biomeColorCache = new HashMap<>();

    /**
     * Paints a coarse biome-colored background for a position map from LootHunt.visitedChunkBiomes
     * - biome samples collected as chunks loaded throughout the game (see LootHuntChunkListener),
     * not sampled live at report time (which could only ever cover whatever tiny sliver of the
     * explored area happened to still be loaded once the game ended).
     */
    /** Result of rendering a position map background: the image itself, plus the same-resolution
     * biome-key grid used to paint it, so JS can do hover lookups without one DOM element per cell. */
    private static class MapBackgroundResult {
        BufferedImage image;
        String[][] biomeKeys; // [row][col], null = never-visited chunk
        int gridCols;
        int gridRows;
    }

    private static MapBackgroundResult renderWorldMapBackground(World world, int imgW, int imgH, double minX, double maxX, double minZ, double maxZ) {
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 20, 35));
        g.fillRect(0, 0, imgW, imgH);

        MapBackgroundResult result = new MapBackgroundResult();
        // No longer bottlenecked by live Bukkit calls (just map lookups), so a finer grid is cheap
        int gridCols = Math.min(imgW, 150);
        int gridRows = Math.min(imgH, 150);
        result.gridCols = gridCols;
        result.gridRows = gridRows;
        result.biomeKeys = new String[gridRows][gridCols];

        if (world == null) {
            g.dispose();
            result.image = img;
            return result;
        }

        Map<Long, String> chunkBiomes = LootHunt.visitedChunkBiomes.get(world.getName());
        if (chunkBiomes == null || chunkBiomes.isEmpty()) {
            g.dispose();
            result.image = img;
            return result;
        }

        double spanX = maxX - minX;
        double spanZ = maxZ - minZ;
        double cellW = (double) imgW / gridCols;
        double cellH = (double) imgH / gridRows;

        for (int gx = 0; gx < gridCols; gx++) {
            for (int gz = 0; gz < gridRows; gz++) {
                double worldX = minX + (gx + 0.5) / gridCols * spanX;
                double worldZ = minZ + (gz + 0.5) / gridRows * spanZ;
                int chunkX = ((int) Math.floor(worldX)) >> 4;
                int chunkZ = ((int) Math.floor(worldZ)) >> 4;

                String biomeKey = chunkBiomes.get(LootHunt.packChunkKey(chunkX, chunkZ));
                result.biomeKeys[gz][gx] = biomeKey;
                Color color = biomeKey != null ? biomeToColor(biomeKey) : new Color(30, 30, 45); // never-visited chunk

                g.setColor(color);
                g.fillRect((int) (gx * cellW), (int) (gz * cellH), (int) Math.ceil(cellW), (int) Math.ceil(cellH));
            }
        }

        g.dispose();
        result.image = img;
        return result;
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

    /** Turns "minecraft:frozen_river" into "Frozen River" for display. */
    private static String formatBiomeName(String biomeKey) {
        String name = biomeKey.contains(":") ? biomeKey.substring(biomeKey.indexOf(':') + 1) : biomeKey;
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
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
     * Appends a Chart.js line chart showing each team's aggregate score over time (one line per
     * team, using their real team color). This uses the team-level score history rather than
     * summing/plotting individual players, since a per-player line double-counts shared team
     * storage (it's counted in full for every teammate).
     */
    private static void appendTeamScoreHistoryChart(StringBuilder sb, String chartId, String heading,
                                                    List<String> teamNames, String noDataMessage) {
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

        String[] palette = {"#ff5555", "#55ff55", "#5599ff", "#ffaa00", "#aa55ff", "#00ffcc", "#ff55aa", "#ffff55"};
        int paletteIdx = 0;
        boolean anyData = false;

        for (String teamName : teamNames) {
            List<LootHunt.TeamScoreSnapshot> history = LootHunt.teamScoreHistory.getOrDefault(teamName, Collections.emptyList());
            if (history.isEmpty()) continue;
            anyData = true;

            StringBuilder dataPoints = new StringBuilder("[");
            StringBuilder metaPoints = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                LootHunt.TeamScoreSnapshot snap = history.get(i);
                if (i > 0) { dataPoints.append(","); metaPoints.append(","); }
                double minutes = snap.tick / 60.0;
                dataPoints.append("{x:").append(minutes).append(",y:").append(snap.score).append("}");
                String biomeLabel = snap.biomes.isEmpty() ? "unknown" : escapeJs(String.join(", ", snap.biomes));
                String structLabel = snap.structures.isEmpty() ? "none" : escapeJs(String.join(", ", snap.structures));
                String dimLabel = snap.dimensions.isEmpty() ? "unknown" :
                        escapeJs(snap.dimensions.stream().map(LootHuntScorePage::dimensionDisplayName).collect(Collectors.joining(", ")));
                metaPoints.append("{biome:\"").append(biomeLabel).append("\",structure:\"").append(structLabel)
                        .append("\",dimension:\"").append(dimLabel).append("\"}");
            }
            dataPoints.append("]");
            metaPoints.append("]");

            String color = getTeamColorHex(teamName);
            if (color == null) {
                color = palette[paletteIdx % palette.length];
                paletteIdx++;
            }

            sb.append("            datasets.push({\n")
                    .append("                label: \"").append(escapeJs(teamName)).append("\",\n")
                    .append("                data: ").append(dataPoints).append(",\n")
                    .append("                meta: ").append(metaPoints).append(",\n")
                    .append("                borderColor: \"").append(color).append("\",\n")
                    .append("                backgroundColor: \"").append(color).append("\",\n")
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
        appendTeamScoreHistoryChart(sb, "chart-" + sanitizeId(teamName), "Score History",
                List.of(teamName), "No score history recorded for this team.");
    }

    /**
     * A single combined chart at the top of the page with every team's aggregate score line.
     */
    private static void appendCombinedScoreChart(StringBuilder sb, Map<String, List<LootHunt.PlayerResult>> teamPlayers, List<String> teamOrder) {
        sb.append("    <div class=\"team\">\n")
                .append("        <h2>All Teams \u2014 Score History</h2>\n");
        appendTeamScoreHistoryChart(sb, "chart-all-players", "", teamOrder,
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