package madoku.craft.core.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Configuration for weather, water, and seasonal climate transitions. */
public final class EnvironmentTransitionConfigManager {
	public static final String CONFIG_FOLDER_NAME = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-season";
	public static final String CONFIG_FILE_NAME = "environment-transition";
	public static final String FIELD_TRANSITION_WEATHER = "transition-weather";
	public static final String FIELD_TRANSITION_WATER = "transition-water";
	public static final String FIELD_TRANSITION_COLOR = "transition-color";
	public static final String FIELD_SEASON_TRANSITIONS = "season-transitions";
	public static final String FIELD_TEMPERATURE = "temperature";
	public static final String FIELD_HUMIDITY = "humidity";
	public static final String FIELD_SEASONS = "seasons";
	public static final String FIELD_ADJUSTMENT = "adjustment";
	public static final String FIELD_TYPE = "type";
	public static final String FIELD_VALUE = "value";
	public static final String FIELD_ADJUSTMENT_RATES = "adjustment-rates";
	public static final String FIELD_TIME_RATE = "time-rate";
	public static final String FIELD_ADJUSTMENT_COUNT = "adjustment-count";
	public static final String FIELD_ENABLED = "enabled";

	private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentTransitionConfigManager.class);
	private static volatile Settings settings = defaults();
	private static volatile Settings clientSynchronizedSettings;

	private EnvironmentTransitionConfigManager() { }

	public static void initialize() {
		loadConfig();
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
		LinkedHashMap<String, Adjustment> temperature = new LinkedHashMap<>();
		temperature.put("spring", new Adjustment("addition", 6.0));
		temperature.put("summer", new Adjustment("addition", 9.0));
		temperature.put("fall", new Adjustment("subtraction", 6.0));
		temperature.put("winter", new Adjustment("subtraction", 9.0));
		LinkedHashMap<String, Adjustment> humidity = new LinkedHashMap<>();
		humidity.put("spring", new Adjustment("addition", 9.0));
		humidity.put("summer", new Adjustment("addition", 6.0));
		humidity.put("fall", new Adjustment("subtraction", 6.0));
		humidity.put("winter", new Adjustment("subtraction", 9.0));
		return new Settings(true, true, true, true, true, true, temperature, humidity, 7, 4);
	}

	public static JsonObject buildDefaultsJson() { return toJson(defaults()); }

	public static JsonObject toJson(Settings value) {
		Settings safe = value == null ? defaults() : value;
		JSONFormatManager.ObjectBuilder root = JSONFormatManager.object()
			.object(FIELD_TRANSITION_WEATHER, b -> b.put(FIELD_ENABLED, safe.weatherEnabled()))
			.object(FIELD_TRANSITION_WATER, b -> b.put(FIELD_ENABLED, safe.waterEnabled()))
			.object(FIELD_TRANSITION_COLOR, b -> b.put(FIELD_ENABLED, safe.transitionColorEnabled()))
			.object(FIELD_SEASON_TRANSITIONS, b -> {
				b.put(FIELD_ENABLED, safe.seasonTransitionsEnabled());
				b.object(FIELD_TEMPERATURE, t -> writeSeasonAdjustments(t, safe.temperatureAdjustments(), safe.temperatureEnabled()));
				b.object(FIELD_HUMIDITY, h -> writeSeasonAdjustments(h, safe.humidityAdjustments(), safe.humidityEnabled()));
				b.object(FIELD_ADJUSTMENT_RATES, rates -> rates.put(FIELD_TIME_RATE, safe.timeRateDays())
					.put(FIELD_ADJUSTMENT_COUNT, safe.adjustmentCount()));
			});
		return root.build();
	}

	private static void writeSeasonAdjustments(JSONFormatManager.ObjectBuilder parent, Map<String, Adjustment> values, boolean enabled) {
		parent.put(FIELD_ENABLED, enabled);
		parent.object(FIELD_SEASONS, seasons -> values.forEach((season, adjustment) -> seasons.object(season, s ->
			s.object(FIELD_ADJUSTMENT, a -> a.put(FIELD_TYPE, adjustment.type()).put(FIELD_VALUE, adjustment.value())))));
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		JsonObject transitions = object(source, FIELD_SEASON_TRANSITIONS);
		JsonObject temperature = object(transitions, FIELD_TEMPERATURE);
		JsonObject humidity = object(transitions, FIELD_HUMIDITY);
		JsonObject rates = object(transitions, FIELD_ADJUSTMENT_RATES);
		return new Settings(
			readBoolean(object(source, FIELD_TRANSITION_WEATHER), FIELD_ENABLED, true),
			readBoolean(object(source, FIELD_TRANSITION_WATER), FIELD_ENABLED, true),
			readBoolean(object(source, FIELD_TRANSITION_COLOR), FIELD_ENABLED, true),
			readBoolean(transitions, FIELD_ENABLED, true), readBoolean(temperature, FIELD_ENABLED, true), readBoolean(humidity, FIELD_ENABLED, true),
			readAdjustments(temperature, fallback.temperatureAdjustments()), readAdjustments(humidity, fallback.humidityAdjustments()),
			Math.max(1, readInt(rates, FIELD_TIME_RATE, fallback.timeRateDays())),
			Math.max(0, readInt(rates, FIELD_ADJUSTMENT_COUNT, fallback.adjustmentCount()))
		);
	}

	private static Map<String, Adjustment> readAdjustments(JsonObject root, Map<String, Adjustment> fallback) {
		LinkedHashMap<String, Adjustment> result = new LinkedHashMap<>();
		JsonObject seasons = object(root, FIELD_SEASONS);
		for (Map.Entry<String, Adjustment> entry : fallback.entrySet()) {
			JsonObject adjustment = object(object(seasons, entry.getKey()), FIELD_ADJUSTMENT);
			String type = readString(adjustment, FIELD_TYPE, entry.getValue().type());
			if (!type.equals("addition") && !type.equals("subtraction")) type = entry.getValue().type();
			result.put(entry.getKey(), new Adjustment(type, Math.max(0.0, Math.min(100.0, readDouble(adjustment, FIELD_VALUE, entry.getValue().value())))));
		}
		return result;
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
			LOGGER.error("Failed to load environment transition configuration; using defaults.", exception);
		}
	}

	private static JsonObject object(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}
	private static boolean readBoolean(JsonObject source, String key, boolean fallback) { try { return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback; } catch (RuntimeException e) { return fallback; } }
	private static int readInt(JsonObject source, String key, int fallback) { try { return source != null && source.has(key) ? source.get(key).getAsInt() : fallback; } catch (RuntimeException e) { return fallback; } }
	private static double readDouble(JsonObject source, String key, double fallback) { try { return source != null && source.has(key) ? source.get(key).getAsDouble() : fallback; } catch (RuntimeException e) { return fallback; } }
	private static String readString(JsonObject source, String key, String fallback) { try { return source != null && source.has(key) ? source.get(key).getAsString().toLowerCase(java.util.Locale.ROOT) : fallback; } catch (RuntimeException e) { return fallback; } }

	public record Adjustment(String type, double value) {
		public Adjustment { type = type == null ? "addition" : type.toLowerCase(java.util.Locale.ROOT); value = Math.max(0.0, Math.min(100.0, value)); }
	}


	public record Settings(boolean weatherEnabled, boolean waterEnabled, boolean transitionColorEnabled, boolean seasonTransitionsEnabled, boolean temperatureEnabled, boolean humidityEnabled, Map<String, Adjustment> temperatureAdjustments, Map<String, Adjustment> humidityAdjustments, int timeRateDays, int adjustmentCount) {
		public Settings { temperatureAdjustments = Map.copyOf(temperatureAdjustments == null ? Map.of() : temperatureAdjustments); humidityAdjustments = Map.copyOf(humidityAdjustments == null ? Map.of() : humidityAdjustments); timeRateDays = Math.max(1, timeRateDays); adjustmentCount = Math.max(0, adjustmentCount); }
	}
}
