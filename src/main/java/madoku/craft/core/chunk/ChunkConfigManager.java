package madoku.craft.core.chunk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class ChunkConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ChunkConfigManager.class);
	private static final String CHUNK_CONFIG_FOLDER_NAME = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-chunk";
	private static final String CHUNK_CONFIG_FILE_NAME = "madoku-chunk";
	private static final String FIELD_ENABLED = "enabled";

	private static volatile Settings settings = Settings.defaults();

	private ChunkConfigManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static boolean isChunkSystemEnabled() {
		return settings.enabled;
	}

	private static void loadConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CHUNK_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(rootDirectory, CHUNK_CONFIG_FILE_NAME);
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
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
		final boolean enabled;

		private Settings(boolean enabled) {
			this.enabled = enabled;
		}

		static Settings defaults() {
			return new Settings(true);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			return new Settings(getBoolean(source, FIELD_ENABLED, defaults.enabled));
		}

		JsonObject toConfigJson() {
			return JSONFormatManager.object()
				.put(FIELD_ENABLED, enabled)
				.build();
		}
	}
}
