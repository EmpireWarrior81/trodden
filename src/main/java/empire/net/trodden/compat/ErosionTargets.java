package empire.net.trodden.compat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Compat hook for other mods: lets a modded "grass-like" block erode into its
 * own dirt-like and path-like blocks instead of always falling back to vanilla
 * dirt/dirt_path.
 *
 * A mod that adds e.g. "examplemod:lush_grass" to the trodden:erodible tag
 * should also call, in its own ModInitializer:
 *
 *   ErosionTargets.register(ExampleBlocks.LUSH_GRASS, ExampleBlocks.LUSH_DIRT, ExampleBlocks.LUSH_PATH);
 *
 * Blocks with no registered targets erode to vanilla dirt / dirt_path.
 */
public final class ErosionTargets {
	private static final Map<Block, Block> DIRT_TARGETS = new HashMap<>();
	private static final Map<Block, Block> PATH_TARGETS = new HashMap<>();

	private ErosionTargets() {
	}

	public static void register(Block source, Block dirtLike, Block pathLike) {
		DIRT_TARGETS.put(source, dirtLike);
		PATH_TARGETS.put(source, pathLike);
	}

	public static Block dirtFor(Block source) {
		return DIRT_TARGETS.getOrDefault(source, Blocks.DIRT);
	}

	public static Block pathFor(Block source) {
		return PATH_TARGETS.getOrDefault(source, Blocks.DIRT_PATH);
	}
}
