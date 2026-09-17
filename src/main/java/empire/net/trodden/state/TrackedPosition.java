package empire.net.trodden.state;

import net.minecraft.world.level.block.Block;

/**
 * Mutable per-position wear state. Only positions mid-transition are tracked;
 * fully-grown grass is never stored.
 *
 * `origin` is the eligible block this position started out as (e.g.
 * minecraft:grass_block, or a modded grass-like block) - it's what the
 * position reverts to at wear 0, and what compat/ErosionTargets looks up
 * dirt/path equivalents by.
 */
public final class TrackedPosition {
	public final Block origin;
	public int wear;
	public long lastStepTick;

	public TrackedPosition(Block origin, int wear, long lastStepTick) {
		this.origin = origin;
		this.wear = wear;
		this.lastStepTick = lastStepTick;
	}
}
