package org.zappier.zappierGames;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class VoidGenerator extends ChunkGenerator {
    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        // Create an empty chunk with air blocks
        ChunkData chunk = createChunkData(world);
        // Optionally set biome (e.g., PLAINS for consistency)
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                biome.setBiome(bx, bz, org.bukkit.block.Biome.PLAINS);
            }
        }
        return chunk;
    }

    // Disable default structures and features
    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true; // Allow spawning anywhere
    }
}