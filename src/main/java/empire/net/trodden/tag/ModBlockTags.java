package empire.net.trodden.tag;

import empire.net.trodden.Trodden;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Tags used by Trodden. Datapacks can add more entries to "erodible" to make
 * other blocks eligible for wear, without touching any code.
 */
public final class ModBlockTags {

	public static final TagKey<Block> ERODIBLE = TagKey.create(Registries.BLOCK, Trodden.id("erodible"));

	private ModBlockTags() {
	}
}
