package madoku.craft.api.chunk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class ChunkConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChunkConfigManager.class);
	private static final String CHUNK_CONFIG_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-chunk";
	private static final String CHUNK_CONFIG_FILE_NAME = "madoku-chunk";
	private static final String GROUP_CHUNK_DISCOVERY = "chunk-discovery";
	private static final String GROUP_CHUNK_PROCESSOR = "chunk-processor";
	private static final String FIELD_ENABLED = "enabled";

	private static volatile Settings settings = Settings.defaults();

	private ChunkConfigManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static boolean isChunkDiscoveryEnabled() {
		return settings.chunkDiscovery.enabled;
	}

	public static boolean isChunkProcessorEnabled() {
		return settings.chunkProcessor.enabled;
	}

	static int resolveAdaptiveChunkWorkUnits(long intervalTicks) {
		long clampedInterval = Math.max(1L, Math.min(20L, intervalTicks));
		int workUnits = (int) (11L - ((clampedInterval + 1L) / 2L));
		return Math.max(1, Math.min(10, workUnits));
	}

	private static void loadConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(CHUNK_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(rootDirectory, CHUNK_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load Madoku chunk config; using defaults.", exception);
		}
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static JsonObject readObject(JsonObject source, String key) {
		if (source == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonObject()) {
			return new JsonObject();
		}
		return element.getAsJsonObject();
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static final class Settings {
		final ChunkGroupSettings chunkDiscovery;
		final ChunkGroupSettings chunkProcessor;

		private Settings(ChunkGroupSettings chunkDiscovery, ChunkGroupSettings chunkProcessor) {
			this.chunkDiscovery = chunkDiscovery;
			this.chunkProcessor = chunkProcessor;
		}

		static Settings defaults() {
			return new Settings(ChunkGroupSettings.defaults(), ChunkGroupSettings.defaults());
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(
				ChunkGroupSettings.fromJson(readObject(source, GROUP_CHUNK_DISCOVERY), defaults.chunkDiscovery),
				ChunkGroupSettings.fromJson(readObject(source, GROUP_CHUNK_PROCESSOR), defaults.chunkProcessor)
			);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.object(GROUP_CHUNK_DISCOVERY, group -> chunkDiscovery.toConfigJson(group))
				.object(GROUP_CHUNK_PROCESSOR, group -> chunkProcessor.toConfigJson(group))
				.build();
		}
	}

	static final class ChunkGroupSettings {
		final boolean enabled;

		private ChunkGroupSettings(boolean enabled) {
			this.enabled = enabled;
		}

		static ChunkGroupSettings defaults() {
			return new ChunkGroupSettings(true);
		}

		static ChunkGroupSettings fromJson(JsonObject source, ChunkGroupSettings defaults) {
			ChunkGroupSettings base = defaults == null ? defaults() : defaults;
			return new ChunkGroupSettings(getBoolean(source, FIELD_ENABLED, base.enabled));
		}

		void toConfigJson(JsonFormatBuilder.ObjectBuilder builder) {
			if (builder != null) {
				builder.put(FIELD_ENABLED, enabled);
			}
		}
	}
}
