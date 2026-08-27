package org.zappier.zappierGames.loothunt;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Samples and records each chunk's biome the moment it loads, while a loothunt is active. This
 * builds up full biome coverage of the explored area incrementally over the course of the game,
 * instead of the world map only being able to sample whatever chunks happen to still be loaded
 * (usually almost none of the explored area) at the moment the game ends.
 */
public class LootHuntChunkListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        LootHunt.recordChunkBiome(event.getChunk());
    }
}