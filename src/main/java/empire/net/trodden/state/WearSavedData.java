package empire.net.trodden.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * World-level persistence for tracked positions.
 *
 * TODO(version-porting): The SavedData/DimensionDataStorage API (this class,
 * plus the SavedData.Factory wiring in get()) is one of the parts of the MC
 * API most likely to change shape between versions - e.g. pre-1.20.5 it used
 * a Function<CompoundTag, T> deserializer instead of the
 * BiFunction<CompoundTag, HolderLookup.Provider, T> used here. When porting
 * (e.g. via Stonecutter), this file - and only this file - should need to
 * change; everything else talks to WearDataStore.
 */
public final class WearSavedData extends SavedData implements WearDataStore {
	private static final String STORAGE_KEY = "trodden_wear";

	private final Map<BlockPos, TrackedPosition> positions = new HashMap<>();

	public static WearSavedData get(ServerLevel level) {
		// TODO(version-porting): DimensionDataStorage#computeIfAbsent + SavedData.Factory.
		return level.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(WearSavedData::new, WearSavedData::load, DataFixTypes.LEVEL),
				STORAGE_KEY
		);
	}

	@Override
	public Map<BlockPos, TrackedPosition> positions() {
		return positions;
	}

	@Override
	public void markDirty() {
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<BlockPos, TrackedPosition> entry : positions.entrySet()) {
			BlockPos pos = entry.getKey();
			TrackedPosition tracked = entry.getValue();

			ResourceLocation originId = BuiltInRegistries.BLOCK.getKey(tracked.origin);

			CompoundTag entryTag = new CompoundTag();
			entryTag.putInt("x", pos.getX());
			entryTag.putInt("y", pos.getY());
			entryTag.putInt("z", pos.getZ());
			entryTag.putString("originNamespace", originId.getNamespace());
			entryTag.putString("originPath", originId.getPath());
			entryTag.putInt("wear", tracked.wear);
			entryTag.putLong("lastStepTick", tracked.lastStepTick);
			list.add(entryTag);
		}
		tag.put("positions", list);
		return tag;
	}

	private static WearSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		WearSavedData data = new WearSavedData();
		ListTag list = tag.getList("positions", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entryTag = list.getCompound(i);
			BlockPos pos = new BlockPos(entryTag.getInt("x"), entryTag.getInt("y"), entryTag.getInt("z"));

			Block origin = Blocks.GRASS_BLOCK;
			if (entryTag.contains("originNamespace")) {
				ResourceLocation originId = ResourceLocation.fromNamespaceAndPath(
						entryTag.getString("originNamespace"), entryTag.getString("originPath"));
				Block resolved = BuiltInRegistries.BLOCK.get(originId);
				// Falls back to grass_block if the block came from a mod that's no
				// longer installed (a defaulted registry returns air for unknown ids).
				if (resolved != Blocks.AIR) {
					origin = resolved;
				}
			}

			TrackedPosition tracked = new TrackedPosition(origin, entryTag.getInt("wear"), entryTag.getLong("lastStepTick"));
			data.positions.put(pos, tracked);
		}
		return data;
	}
}
