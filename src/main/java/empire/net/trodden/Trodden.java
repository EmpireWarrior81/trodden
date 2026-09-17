package empire.net.trodden;

import empire.net.trodden.config.TroddenConfig;
import empire.net.trodden.server.TroddenServerEvents;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Trodden implements ModInitializer {
	public static final String MOD_ID = "trodden";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TroddenConfig.get();
		TroddenServerEvents.register();

		LOGGER.info("Trodden initialized - desire paths are forming.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
