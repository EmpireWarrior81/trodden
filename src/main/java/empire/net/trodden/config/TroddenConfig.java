package empire.net.trodden.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import empire.net.trodden.Trodden;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON config, loaded once on mod init. No UI - edit the file and restart.
 */
public final class TroddenConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "trodden.json";

	private static TroddenConfig instance;

	public int stepGain = 1;
	public int dirtThreshold = 10;
	// Also the wear cap - wear never rises above this.
	public int pathThreshold = 40;
	public int regrowRate = 1;
	// Ticks before an idle position starts regrowing (default: 3 in-game days).
	public long regrowDelay = 72000;
	// Ticks between regrow sweeps.
	public long sweepInterval = 200;
	// If false, non-player living entities near a player wear the ground too.
	public boolean playersOnly = true;

	public static TroddenConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static TroddenConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				TroddenConfig loaded = GSON.fromJson(reader, TroddenConfig.class);
				return loaded != null ? loaded : new TroddenConfig();
			} catch (IOException e) {
				Trodden.LOGGER.warn("Failed to read {}, falling back to defaults", FILE_NAME, e);
				return new TroddenConfig();
			}
		}

		TroddenConfig defaults = new TroddenConfig();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(defaults, writer);
			}
		} catch (IOException e) {
			Trodden.LOGGER.warn("Failed to write default {}", FILE_NAME, e);
		}
		return defaults;
	}
}
