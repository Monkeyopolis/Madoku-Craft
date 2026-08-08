package madoku.craft.farming;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;

import java.io.IOException;
import java.nio.file.Path;

public final class FarmingConfigManager {
	public static final String CONFIG_ROOT_FOLDER_NAME = "madoku-craft-farming";
	public static final String CONFIG_FILE_NAME = "madoku-farming";
	public static final String CROPS_CONFIG_FOLDER_NAME = "madoku-crops";
	public static final String COMPOSTER_CONFIG_FOLDER_NAME = "madoku-composter";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_RAIN_GROWTH_BOOST = "rain-growth-boost";
	public static final String FIELD_FERTILIZED_GROWTH_BOOST = "fertilized-growth-boost";
	public static final String FIELD_OUT_OF_SEASON_PENALTY = "out-of-season-penalty";
	public static final String FIELD_DRY_FARMLAND_PENALTY = "dry-farmland-penalty";
	public static final String FIELD_PARTICLE_COUNT = "particleCount";
	public static final String FIELD_PARTICLE_SPREAD = "particleSpread";
	public static final String FIELD_PARTICLE_Y_OFFSET = "particleYOffset";

	public static final double DEFAULT_RAIN_GROWTH_BOOST = 0.25d;
	public static final double DEFAULT_FERTILIZED_GROWTH_BOOST = 0.5d;
	public static final double DEFAULT_OUT_OF_SEASON_PENALTY = 0.5d;
	public static final double DEFAULT_DRY_FARMLAND_PENALTY = 0.5d;
	public static final int MAX_PARTICLE_COUNT = 4;
	public static final int DEFAULT_PARTICLE_COUNT = 2;
	public static final double DEFAULT_PARTICLE_SPREAD = 0.12d;
	public static final double DEFAULT_PARTICLE_Y_OFFSET = 0.1d;

	private FarmingConfigManager() {
	}

	public static void initialize() {
		try {
			loadFarmingSettings();
		} catch (IOException | RuntimeException ignored) {
			// FarmingCropsManager owns runtime fallbacks; this call only ensures
			// the managed farming file exists before item configuration is loaded.
		}
	}

	public static JsonObject loadFarmingSettings() throws IOException {
		Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_ROOT_FOLDER_NAME);
		return JSONFormatManager.ensureManagedFile(
			directory.resolve(CONFIG_FILE_NAME + ".json"),
			buildFarmingDefaults()
		);
	}

	public static Path resolveCropsConfigDirectory() throws IOException {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_ROOT_FOLDER_NAME)
			.resolve(CROPS_CONFIG_FOLDER_NAME);
	}

	public static Path resolveComposterConfigDirectory() throws IOException {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_ROOT_FOLDER_NAME)
			.resolve(COMPOSTER_CONFIG_FOLDER_NAME);
	}

	public static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		JsonElement element = source == null ? null : source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) return fallback;
		try { return element.getAsBoolean(); } catch (RuntimeException ignored) { return fallback; }
	}

	public static JsonObject buildFarmingDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_RAIN_GROWTH_BOOST, DEFAULT_RAIN_GROWTH_BOOST)
			.put(FIELD_FERTILIZED_GROWTH_BOOST, DEFAULT_FERTILIZED_GROWTH_BOOST)
			.put(FIELD_OUT_OF_SEASON_PENALTY, DEFAULT_OUT_OF_SEASON_PENALTY)
			.put(FIELD_DRY_FARMLAND_PENALTY, DEFAULT_DRY_FARMLAND_PENALTY)
			.build();
	}
}

