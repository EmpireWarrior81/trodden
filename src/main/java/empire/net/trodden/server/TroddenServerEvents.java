package empire.net.trodden.server;

import empire.net.trodden.compat.ErosionTargets;
import empire.net.trodden.config.TroddenConfig;
import empire.net.trodden.state.TrackedPosition;
import empire.net.trodden.state.WearDataStore;
import empire.net.trodden.state.WearSavedData;
import empire.net.trodden.tag.ModBlockTags;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The two loops described in the mod design: wearing-in (every world tick,
 * driven by whoever is standing where) and regrowing (a periodic sweep over
 * everything currently tracked).
 */
public final class TroddenServerEvents {
	private static final Map<UUID, BlockPos> LAST_FOOT_POS = new HashMap<>();

	private static final double NON_PLAYER_RANGE = 16.0;

	private TroddenServerEvents() {
	}

	public static void register() {
		// TODO(version-porting): ServerTickEvents.END_WORLD_TICK is the Fabric API
		// hook for "once per world, at the end of its tick". The event name/package
		// (net.fabricmc.fabric.api.event.lifecycle.v1) has been stable for a long
		// time but is worth re-checking first when bumping Fabric API majors.
		ServerTickEvents.END_WORLD_TICK.register(TroddenServerEvents::onEndWorldTick);
	}

	private static void onEndWorldTick(ServerLevel level) {
		TroddenConfig config = TroddenConfig.get();
		WearDataStore store = WearSavedData.get(level);
		long time = level.getGameTime();

		wearIn(level, config, store, time);

		if (config.sweepInterval > 0 && time % config.sweepInterval == 0) {
			regrow(level, config, store, time);
		}
	}

	private static void wearIn(ServerLevel level, TroddenConfig config, WearDataStore store, long time) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.onGround()) {
				continue;
			}
			handleStep(level, config, store, time, player.getUUID(), footPosition(player));
		}

		if (!config.playersOnly) {
			for (ServerPlayer player : level.players()) {
				AABB area = player.getBoundingBox().inflate(NON_PLAYER_RANGE);
				for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> !(e instanceof Player))) {
					if (!entity.onGround()) {
						continue;
					}
					handleStep(level, config, store, time, entity.getUUID(), footPosition(entity));
				}
			}
		}
	}

	// TODO(version-porting): "the block directly under their feet" - blockPosition()
	// is the block the entity's feet are standing in; .below() steps down to the
	// block they're walking on. This mapping from entity position -> block position
	// is a likely spot for subtle behavior changes across versions.
	private static BlockPos footPosition(LivingEntity entity) {
		return entity.blockPosition().below();
	}

	private static void handleStep(ServerLevel level, TroddenConfig config, WearDataStore store, long time,
			UUID entityId, BlockPos feet) {
		BlockPos last = LAST_FOOT_POS.put(entityId, feet);
		if (feet.equals(last)) {
			return; // standing still (at block resolution) does nothing
		}

		BlockState state = level.getBlockState(feet);
		TrackedPosition tracked = store.positions().get(feet);
		if (tracked == null && !isEligible(state)) {
			return;
		}

		if (tracked == null) {
			tracked = new TrackedPosition(state.getBlock(), 0, time);
			store.positions().put(feet, tracked);
		}

		tracked.wear = Math.min(tracked.wear + config.stepGain, config.pathThreshold);
		tracked.lastStepTick = time;
		applyStage(level, feet, tracked, config, store);
		store.markDirty();
	}

	private static void regrow(ServerLevel level, TroddenConfig config, WearDataStore store, long time) {
		Iterator<Map.Entry<BlockPos, TrackedPosition>> it = store.positions().entrySet().iterator();
		boolean changed = false;

		while (it.hasNext()) {
			Map.Entry<BlockPos, TrackedPosition> entry = it.next();
			TrackedPosition tracked = entry.getValue();

			if (time - tracked.lastStepTick <= config.regrowDelay) {
				continue;
			}

			tracked.wear = Math.max(0, tracked.wear - config.regrowRate);
			applyStage(level, entry.getKey(), tracked, config, store);
			changed = true;

			if (tracked.wear <= 0) {
				it.remove();
			}
		}

		if (changed) {
			store.markDirty();
		}
	}

	/**
	 * Reads block eligibility from the erodible block tag (datapack-extensible),
	 * NOT a hardcoded block list.
	 */
	private static boolean isEligible(BlockState state) {
		return state.is(ModBlockTags.ERODIBLE);
	}

	private static void applyStage(ServerLevel level, BlockPos pos, TrackedPosition tracked, TroddenConfig config,
			WearDataStore store) {
		BlockState current = level.getBlockState(pos);
		// TODO(compat): dirt/path targets are resolved per-origin so modded
		// grass-like blocks (registered via ErosionTargets) erode into their own
		// dirt/path equivalents instead of always vanilla dirt/dirt_path.
		Block dirtTarget = ErosionTargets.dirtFor(tracked.origin);
		Block pathTarget = ErosionTargets.pathFor(tracked.origin);

		boolean managed = current.is(tracked.origin) || current.is(dirtTarget) || current.is(pathTarget);
		if (!managed && !isEligible(current)) {
			// Something else changed this block - stop fighting it.
			store.positions().remove(pos);
			return;
		}

		Block desired = stageBlock(tracked, config, dirtTarget, pathTarget);
		if (current.getBlock() == desired) {
			return;
		}

		if (desired == pathTarget && current.is(dirtTarget) && isCoveredBySolidBlock(level, pos)) {
			// Vanilla already reverted this to dirt (something solid was placed
			// on top) - respect that instead of forcing the path back.
			store.positions().remove(pos);
			return;
		}

		BlockState desiredState = desired.defaultBlockState();
		if (desiredState.hasProperty(BlockStateProperties.SNOWY)) {
			BlockState above = level.getBlockState(pos.above());
			boolean snowy = above.is(Blocks.SNOW) || above.is(Blocks.SNOW_BLOCK);
			desiredState = desiredState.setValue(BlockStateProperties.SNOWY, snowy);
		}

		level.setBlock(pos, desiredState, Block.UPDATE_ALL);
	}

	private static boolean isCoveredBySolidBlock(ServerLevel level, BlockPos pos) {
		BlockPos above = pos.above();
		return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
	}

	private static Block stageBlock(TrackedPosition tracked, TroddenConfig config, Block dirtTarget, Block pathTarget) {
		if (tracked.wear >= config.pathThreshold) {
			return pathTarget;
		}
		if (tracked.wear >= config.dirtThreshold) {
			return dirtTarget;
		}
		return tracked.origin;
	}
}
