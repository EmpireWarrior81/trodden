package empire.net.trodden.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import empire.net.trodden.Trodden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World-level persistence for tracked positions.
 *
 * TODO(version-porting): Persistence is now entirely Codec-driven -
 * SavedData no longer has a save()/load() pair to override; instead a
 * SavedDataType<T> bundles an id, a constructor and a Codec<T>, and
 * ServerLevel#getDataStorage()#computeIfAbsent(SavedDataType) does the rest.
 * This whole file, and only this file, is what should need to change again
 * when this shape shifts on a future version; everything else talks to
 * WearDataStore.
 */
public final class WearSavedData extends SavedData implements WearDataStore {
	private record Entry(BlockPos pos, Block origin, int wear, long lastStepTick) {
	}

	private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			BuiltInRegistries.BLOCK.byNameCodec().fieldOf("origin").forGetter(Entry::origin),
			Codec.INT.fieldOf("wear").forGetter(Entry::wear),
			Codec.LONG.fieldOf("lastStepTick").forGetter(Entry::lastStepTick)
	).apply(instance, Entry::new));

	private static final Codec<WearSavedData> CODEC =
			ENTRY_CODEC.listOf().xmap(WearSavedData::fromEntries, WearSavedData::toEntries);

	// TODO(version-porting): SavedDataType#computeIfAbsent replaces the old
	// SavedData.Factory + string-key wiring.
	private static final SavedDataType<WearSavedData> TYPE = new SavedDataType<>(
			Trodden.id("trodden_wear"), WearSavedData::new, CODEC, DataFixTypes.LEVEL);

	private final Map<BlockPos, TrackedPosition> positions = new HashMap<>();

	public static WearSavedData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	public Map<BlockPos, TrackedPosition> positions() {
		return positions;
	}

	@Override
	public void markDirty() {
		setDirty();
	}

	private static WearSavedData fromEntries(List<Entry> entries) {
		WearSavedData data = new WearSavedData();
		for (Entry entry : entries) {
			data.positions.put(entry.pos(), new TrackedPosition(entry.origin(), entry.wear(), entry.lastStepTick()));
		}
		return data;
	}

	private static List<Entry> toEntries(WearSavedData data) {
		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<BlockPos, TrackedPosition> entry : data.positions.entrySet()) {
			TrackedPosition tracked = entry.getValue();
			entries.add(new Entry(entry.getKey(), tracked.origin, tracked.wear, tracked.lastStepTick));
		}
		return entries;
	}
}
