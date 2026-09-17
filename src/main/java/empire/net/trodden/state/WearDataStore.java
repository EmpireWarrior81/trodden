package empire.net.trodden.state;

import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * Small seam around persistence so the storage mechanism (currently a
 * SavedData/PersistentState attached to the level) can be swapped out per
 * Minecraft version without touching the wear/regrow logic.
 */
public interface WearDataStore {
	Map<BlockPos, TrackedPosition> positions();

	void markDirty();
}
