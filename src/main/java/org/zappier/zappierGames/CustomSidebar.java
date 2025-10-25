package org.zappier.zappierGames;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import java.util.HashMap;
import java.util.Map;

public class CustomSidebar {

    public final Scoreboard board;
    public final Objective objective;
    public final Map<Integer, String> lines = new HashMap<>();

    public CustomSidebar(String title) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("ScoreboardManager is not available.");
        }
        this.board = manager.getNewScoreboard();
        this.objective = board.registerNewObjective("sidebar", Criteria.DUMMY, Component.text(title));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    // Set a single line of text on the scoreboard
    public void setLine(int score, String content) {
        // Remove old entry if it exists at this score
        if (lines.containsKey(score)) {
            board.resetScores(lines.get(score));
        }

        String entry = content;
        Score s = objective.getScore(entry);
        s.setScore(score);
        lines.put(score, entry);
    }

    // Update or create multiple lines at once
    public void updateLines(Map<Integer, String> newLines) {
        // Reset old scores to avoid visual issues
        for (String entry : lines.values()) {
            board.resetScores(entry);
        }
        lines.clear();

        // Set new scores
        for (Map.Entry<Integer, String> entry : newLines.entrySet()) {
            setLine(entry.getKey(), entry.getValue());
        }
    }
}
