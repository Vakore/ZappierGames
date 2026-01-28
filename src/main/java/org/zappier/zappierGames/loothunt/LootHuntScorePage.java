package org.zappier.zappierGames.loothunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.zappier.zappierGames.ZappierGames;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
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
                String status = (coll.type.equals("complete") && count >= coll.itemGroups.size())
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
}