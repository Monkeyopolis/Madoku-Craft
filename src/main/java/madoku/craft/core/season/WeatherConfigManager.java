package madoku.craft.core.season;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuration for the global Madoku seasonal weather cycle. */
public final class WeatherConfigManager {
	public static final String CONFIG_FOLDER_NAME = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-season";
	public static final String CONFIG_FILE_NAME = "weather";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_WEATHER = "weather";
	public static final String FIELD_TIME_RATE = "time-rate";
	public static final String FIELD_DURATION = "duration";
	public static final String FIELD_CLEAR_WEATHER = "clear-weather";
	public static final String FIELD_RAIN_WEATHER = "rain-weather";
	public static final String FIELD_THUNDERSTORM_WEATHER = "thunderstorm-weather";
	public static final String FIELD_BASE_WEIGHT = "base-weight";
	public static final int DEFAULT_TIME_RATE_MINUTES = 5;
	public static final List<Integer> DEFAULT_DURATIONS_MINUTES = List.of(5, 10, 20);
	public static final int DEFAULT_CLEAR_WEIGHT = 60;
	public static final int DEFAULT_RAIN_WEIGHT = 30;
	public static final int DEFAULT_THUNDERSTORM_WEIGHT = 10;

	private static final Logger LOGGER = LoggerFactory.getLogger(WeatherConfigManager.class);
	private static volatile Settings settings = defaults();

	private WeatherConfigManager() { }

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		settings = defaults();
	}

	public static void reloadConfig() {
		loadConfig();
	}

	public static Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return settings.enabled();
	}

	public static Settings defaults() {
		return new Settings(
			true,
			DEFAULT_TIME_RATE_MINUTES,
			DEFAULT_DURATIONS_MINUTES,
			DEFAULT_CLEAR_WEIGHT,
			DEFAULT_RAIN_WEIGHT,
			DEFAULT_THUNDERSTORM_WEIGHT);
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static JsonObject toJson(Settings value) {
		Settings safe = value == null ? defaults() : value;
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, safe.enabled())
			.object(FIELD_WEATHER, weather -> weather
				.put(FIELD_TIME_RATE, safe.timeRateMinutes())
				.array(FIELD_DURATION, durations -> safe.durationMinutes().forEach(durations::add))
				.object(FIELD_CLEAR_WEATHER, clear -> clear.put(FIELD_BASE_WEIGHT, safe.clearWeight()))
				.object(FIELD_RAIN_WEATHER, rain -> rain.put(FIELD_BASE_WEIGHT, safe.rainWeight()))
				.object(FIELD_THUNDERSTORM_WEATHER, thunderstorm -> thunderstorm.put(FIELD_BASE_WEIGHT, safe.thunderstormWeight())))
			.build();
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		JsonObject weather = object(source, FIELD_WEATHER);
		return new Settings(
			readBoolean(source, FIELD_ENABLED, fallback.enabled()),
			readPositiveInt(weather, FIELD_TIME_RATE, fallback.timeRateMinutes()),
			readDurations(weather, fallback.durationMinutes()),
			readNonNegativeInt(object(weather, FIELD_CLEAR_WEATHER), FIELD_BASE_WEIGHT, fallback.clearWeight()),
			readNonNegativeInt(object(weather, FIELD_RAIN_WEATHER), FIELD_BASE_WEIGHT, fallback.rainWeight()),
			readNonNegativeInt(object(weather, FIELD_THUNDERSTORM_WEATHER), FIELD_BASE_WEIGHT, fallback.thunderstormWeight()));
	}

	private static List<Integer> readDurations(JsonObject weather, List<Integer> fallback) {
		JsonElement element = weather == null ? null : weather.get(FIELD_DURATION);
		if (element == null || !element.isJsonArray()) {
			return fallback;
		}

		List<Integer> durations = new ArrayList<>();
		JsonArray array = element.getAsJsonArray();
		for (JsonElement value : array) {
			try {
				if (value != null && value.isJsonPrimitive()) {
					int duration = value.getAsInt();
					if (duration > 0) durations.add(duration);
				}
			} catch (RuntimeException ignored) {
				// Ignore malformed entries and retain the valid configured values.
			}
		}
		return durations.isEmpty() ? fallback : durations;
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
			LOGGER.error("Failed to load seasonal weather configuration; using defaults.", exception);
		}
	}

	private static JsonObject object(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readPositiveInt(JsonObject source, String key, int fallback) {
		try {
			return source != null && source.has(key) ? Math.max(1, source.get(key).getAsInt()) : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readNonNegativeInt(JsonObject source, String key, int fallback) {
		try {
			return source != null && source.has(key) ? Math.max(0, source.get(key).getAsInt()) : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}


	public record Settings(
		boolean enabled,
		int timeRateMinutes,
		List<Integer> durationMinutes,
		int clearWeight,
		int rainWeight,
		int thunderstormWeight
	) {
		public Settings {
			timeRateMinutes = Math.max(1, timeRateMinutes);
			durationMinutes = List.copyOf(durationMinutes == null || durationMinutes.isEmpty() ? DEFAULT_DURATIONS_MINUTES : durationMinutes);
			clearWeight = Math.max(0, clearWeight);
			rainWeight = Math.max(0, rainWeight);
			thunderstormWeight = Math.max(0, thunderstormWeight);
		}
	}
}
