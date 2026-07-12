package madoku.craft.api.scheduler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.time.MadokuTimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** Global configuration for the Madoku scheduler runtime subsystem. */
public final class SchedulerConfigManager {
	public static final String CONFIG_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-scheduler";
	public static final String CONFIG_FILE_NAME = "madoku-scheduler";
	public static final String FIELD_DEFAULT_EXPIRATION_DAYS = "default-expiration-days";
	public static final String FIELD_INACTIVE_EXPIRATION_MINUTES = "inactive-expiration-minutes";

	public static final int DEFAULT_EXPIRATION_DAYS = 14;
	public static final long DEFAULT_INACTIVE_EXPIRATION_MINUTES = 5L;

	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerConfigManager.class);
	private static volatile Settings settings = defaults();

	private SchedulerConfigManager() { }

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
		return new Settings(DEFAULT_EXPIRATION_DAYS, DEFAULT_INACTIVE_EXPIRATION_MINUTES);
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static JsonObject toJson(Settings value) {
		Settings safe = value == null ? defaults() : value;
		return JSONFormatManager.object()
			.put(FIELD_DEFAULT_EXPIRATION_DAYS, safe.defaultExpirationDays())
			.put(FIELD_INACTIVE_EXPIRATION_MINUTES, safe.inactiveExpirationMinutes())
			.build();
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		return new Settings(
			readInt(source, FIELD_DEFAULT_EXPIRATION_DAYS, fallback.defaultExpirationDays()),
			readLong(source, FIELD_INACTIVE_EXPIRATION_MINUTES, fallback.inactiveExpirationMinutes())
		);
	}

	public static long getInactiveExpirationTicks() {
		try {
			return Math.multiplyExact(
				Math.multiplyExact(settings.inactiveExpirationMinutes(), MadokuTimeManager.SECONDS_PER_MINUTE),
				MadokuTimeManager.TICKS_PER_SECOND);
		} catch (ArithmeticException exception) {
			return DEFAULT_INACTIVE_EXPIRATION_MINUTES
				* MadokuTimeManager.SECONDS_PER_MINUTE
				* MadokuTimeManager.TICKS_PER_SECOND;
		}
	}

	private static void loadConfig() {
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = directory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, buildDefaultsJson());
			Settings loaded = fromJson(normalized);
			JSONFormatManager.writeManagedFile(file, toJson(loaded), buildDefaultsJson());
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = defaults();
			LOGGER.error("Failed to load Madoku scheduler config; using defaults.", exception);
		}
	}

	private static long readLong(JsonObject source, String key, long fallback) {
		JsonElement element = source == null ? null : source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int readInt(JsonObject source, String key, int fallback) {
		JsonElement element = source == null ? null : source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try {
			return element.getAsInt();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	public record Settings(int defaultExpirationDays, long inactiveExpirationMinutes) {
		public Settings {
			defaultExpirationDays = Math.max(1, defaultExpirationDays);
			inactiveExpirationMinutes = Math.max(1L, inactiveExpirationMinutes);
		}
	}
}
