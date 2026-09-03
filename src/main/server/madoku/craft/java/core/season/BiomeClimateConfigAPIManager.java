package madoku.craft.java.core.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Static biome temperature and humidity configuration. */
public final class BiomeClimateConfigAPIManager {
	public static final String CONFIG_FOLDER_NAME = "madoku-craft-core/madoku-season";
	public static final String CONFIG_FILE_NAME = "biome-climate";
	public static final String FIELD_BIOME_TEMPERATURE = "biome-temperature";
	public static final String FIELD_BIOME_HUMIDITY = "biome-humidity";
	public static final String FIELD_BIOMES = "biomes";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_TEMPERATURE = "temperature";
	public static final String FIELD_HUMIDITY = "humidity";

	private static final Logger LOGGER = LoggerFactory.getLogger(BiomeClimateConfigAPIManager.class);
	private static volatile Settings settings = defaults();
	private static volatile Settings clientSynchronizedSettings;

	private BiomeClimateConfigAPIManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reloadConfig() {
		loadConfig();
		SeasonBiomeClimateAPIManager.onClimateConfigReloaded();
	}

	public static void reset() {
		settings = defaults();
		clientSynchronizedSettings = null;
	}

	public static Settings getSettings() {
		Settings synchronizedSettings = clientSynchronizedSettings;
		return synchronizedSettings == null ? settings : synchronizedSettings;
	}

	public static void applyClientSynchronizedSettings(Settings synchronizedSettings) {
		clientSynchronizedSettings = synchronizedSettings;
	}

	public static void resetClientSynchronizedSettings() {
		clientSynchronizedSettings = null;
	}

	public static Settings defaults() {
		LinkedHashMap<String, Climate> biomes = new LinkedHashMap<>();
		String[] values = {
			"deep-frozen-ocean,-10,110", "frozen-ocean,0,110", "deep-cold-ocean,10,110", "cold-ocean,20,110",
			"ocean,40,110", "deep-ocean,30,110", "lukewarm-ocean,60,110", "deep-lukewarm-ocean,50,110",
			"warm-ocean,70,110", "mushroom-fields,50,70", "frozen-peaks,-10,70", "jagged-peaks,0,50",
			"stony-peaks,10,30", "meadow,50,50", "cherry-grove,50,50", "grove,10,90", "snowy-slopes,0,60",
			"windswept-hills,40,40", "windswept-gravelly-hills,50,30", "windswept-forest,50,50", "forest,50,50",
			"flower-forest,60,60", "taiga,40,40", "old-growth-pine-taiga,30,60", "old-growth-spruce-taiga,30,40",
			"snowy-taiga,20,50", "birch-forest,60,60", "old-growth-birch-forest,50,70", "dark-forest,70,90",
			"pale-garden,70,70", "jungle,90,90", "bamboo-jungle,110,110", "sparse-jungle,70,70", "river,50,70",
			"frozen-river,0,70", "swamp,90,110", "beach,60,70", "snowy-beach,20,70", "stony-shore,40,60",
			"plains,50,50", "sunflower-plains,60,60", "snowy-plains,10,30", "ice-spikes,-10,60", "desert,100,0",
			"savanna,90,50", "savanna-plateau,80,30", "windswept-savanna,70,10", "badlands,100,10",
			"wooded-badlands,90,30", "eroded-badlands,110,-10"
		};
		for (String value : values) {
			String[] parts = value.split(",");
			biomes.put(parts[0], new Climate(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
		}
		return new Settings(true, true, biomes);
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static JsonObject toJson(Settings value) {
		Settings safe = value == null ? defaults() : value;
		JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object()
			.object(FIELD_BIOME_TEMPERATURE, child -> child.put(FIELD_ENABLED, safe.temperatureEnabled()))
			.object(FIELD_BIOME_HUMIDITY, child -> child.put(FIELD_ENABLED, safe.humidityEnabled()));
		root.object(FIELD_BIOMES, biomes -> {
			for (Map.Entry<String, Climate> entry : safe.biomes().entrySet()) {
				Climate climate = entry.getValue();
				biomes.object(entry.getKey(), biome -> biome
					.put(FIELD_TEMPERATURE, climate.temperature())
					.put(FIELD_HUMIDITY, climate.humidity()));
			}
		});
		return root.build();
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		JsonObject temperature = object(source, FIELD_BIOME_TEMPERATURE);
		JsonObject humidity = object(source, FIELD_BIOME_HUMIDITY);
		JsonObject configuredBiomes = object(source, FIELD_BIOMES);
		LinkedHashMap<String, Climate> biomes = new LinkedHashMap<>();
		for (Map.Entry<String, Climate> entry : fallback.biomes().entrySet()) {
			JsonObject configured = object(configuredBiomes, entry.getKey());
			Climate climate = entry.getValue();
			biomes.put(entry.getKey(), new Climate(
				readInt(configured, FIELD_TEMPERATURE, climate.temperature()),
				readInt(configured, FIELD_HUMIDITY, climate.humidity())));
		}
		return new Settings(readBoolean(temperature, FIELD_ENABLED, true), readBoolean(humidity, FIELD_ENABLED, true), biomes);
	}

	public static Climate getBiomeClimate(String biomeId) {
		if (biomeId == null || biomeId.isBlank()) return null;

		String normalized = biomeId.trim().toLowerCase(java.util.Locale.ROOT);
		Climate climate = getSettings().biomes().get(normalized);
		if (climate != null) return climate;

		int separator = normalized.indexOf(':');
		String path = separator >= 0 ? normalized.substring(separator + 1) : normalized;
		climate = getSettings().biomes().get(path);
		if (climate != null) return climate;

		// Minecraft resource IDs use underscores, while Madoku config IDs use
		// hyphens. Support both forms without requiring existing config files to
		// be rewritten.
		return getSettings().biomes().get(path.replace('_', '-'));
	}

	private static void loadConfig() {
		try {
			Path directory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = directory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, buildDefaultsJson());
			settings = fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, toJson(settings), buildDefaultsJson());
		} catch (IOException | RuntimeException exception) {
			settings = defaults();
			LOGGER.error("Failed to load biome climate configuration; using defaults.", exception);
		}
	}

	private static JsonObject object(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static int readInt(JsonObject source, String key, int fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsInt() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	public record Climate(int temperature, int humidity) {
	}


	public record Settings(boolean temperatureEnabled, boolean humidityEnabled, Map<String, Climate> biomes) {
		public Settings {
			biomes = Map.copyOf(biomes == null ? Map.of() : biomes);
		}
	}
}

