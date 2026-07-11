package madoku.craft.api.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Static biome temperature and humidity configuration. */
public final class BiomeClimateConfigManager {
	public static final String CONFIG_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-season";
	public static final String CONFIG_FILE_NAME = "biome-climate";
	public static final String FIELD_BIOME_TEMPERATURE = "biome-temperature";
	public static final String FIELD_BIOME_HUMIDITY = "biome-humidity";
	public static final String FIELD_BIOMES = "biomes";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_TEMPERATURE = "temperature";
	public static final String FIELD_HUMIDITY = "humidity";

	private static final Logger LOGGER = LoggerFactory.getLogger(BiomeClimateConfigManager.class);
	private static volatile Settings settings = defaults();

	private BiomeClimateConfigManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		settings = defaults();
	}

	public static Settings getSettings() {
		return settings;
	}

	public static Settings defaults() {
		LinkedHashMap<String, Climate> biomes = new LinkedHashMap<>();
		String[] values = {
			"deep-frozen-ocean,0,100", "frozen-ocean,0,100", "deep-cold-ocean,40,100", "cold-ocean,40,100",
			"ocean,50,100", "deep-ocean,50,100", "lukewarm-ocean,50,100", "deep-lukewarm-ocean,50,100",
			"warm-ocean,60,100", "mushroom-fields,50,70", "frozen-peaks,0,80", "jagged-peaks,0,80",
			"stony-peaks,40,70", "meadow,50,50", "cherry-grove,50,50", "grove,20,50", "snowy-slopes,10,50",
			"windswept-hills,40,30", "windswept-gravelly-hills,40,30", "windswept-forest,50,50", "forest,40,50",
			"flower-forest,50,50", "taiga,50,50", "old-growth-pine-taiga,40,50", "old-growth-spruce-taiga,40,50",
			"snowy-taiga,20,50", "birch-forest,40,50", "old-growth-birch-forest,50,60", "dark-forest,100,80",
			"pale-garden,50,50", "jungle,80,70", "bamboo-jungle,50,100", "sparse-jungle,50,50", "river,50,60",
			"frozen-river,20,60", "swamp,50,100", "beach,50,70", "snowy-beach,20,70", "stony-shore,20,60",
			"plains,50,50", "sunflower-plains,50,50", "snowy-plains,50,20", "ice-spikes,0,80", "desert,80,0",
			"savanna,60,40", "savanna-plateau,60,50", "windswept-savanna,60,50", "badlands,100,20",
			"wooded-badlands,80,40", "eroded-badlands,100,0"
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
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object()
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
				clamp(readInt(configured, FIELD_TEMPERATURE, climate.temperature())),
				clamp(readInt(configured, FIELD_HUMIDITY, climate.humidity()))));
		}
		return new Settings(readBoolean(temperature, FIELD_ENABLED, true), readBoolean(humidity, FIELD_ENABLED, true), biomes);
	}

	public static Climate getBiomeClimate(String biomeId) {
		if (biomeId == null) return null;
		String normalized = biomeId.toLowerCase(java.util.Locale.ROOT);
		Climate climate = settings.biomes().get(normalized);
		if (climate != null) return climate;
		int separator = normalized.indexOf(':');
		return separator >= 0 ? settings.biomes().get(normalized.substring(separator + 1)) : null;
	}

	private static void loadConfig() {
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = directory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, buildDefaultsJson());
			settings = fromJson(normalized);
			JSONFormatManager.writeManagedFile(file, toJson(settings), buildDefaultsJson());
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

	private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }

	public record Climate(int temperature, int humidity) {
		public Climate { temperature = clamp(temperature); humidity = clamp(humidity); }
	}

	public record Settings(boolean temperatureEnabled, boolean humidityEnabled, Map<String, Climate> biomes) {
		public Settings {
			biomes = Map.copyOf(biomes == null ? Map.of() : biomes);
		}
	}
}
