package madoku.craft.api.season;

import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Root configuration for the Madoku Season runtime subsystem. */
public final class SeasonConfigManager {
	public static final String CONFIG_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-season";
	public static final String CONFIG_FILE_NAME = "season";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_SEASON_LENGTH_DAYS = "season-length-days";
	public static final int DEFAULT_SEASON_LENGTH_DAYS = 28;
	public static final int DEFAULT_DAYS_PER_WEEK = 7;

	private static final Logger LOGGER = LoggerFactory.getLogger(SeasonConfigManager.class);
	private static volatile Settings settings = defaults();

	private SeasonConfigManager() {
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
		return new Settings(true, DEFAULT_SEASON_LENGTH_DAYS);
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static JsonObject toJson(Settings value) {
		Settings safe = value == null ? defaults() : value;
		return JSONFormatManager.object()
			.put(FIELD_ENABLED, safe.enabled())
			.put(FIELD_SEASON_LENGTH_DAYS, safe.seasonLengthDays())
			.build();
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		boolean enabled = readBoolean(source, FIELD_ENABLED, fallback.enabled());
		int seasonLength = Math.max(1, Math.min(365, readInt(source, FIELD_SEASON_LENGTH_DAYS, fallback.seasonLengthDays())));
		return new Settings(enabled, seasonLength);
	}

	public static String normalizeKey(String value) {
		if (value == null) return "";
		return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
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
			LOGGER.error("Failed to load Madoku Season root configuration; using defaults.", exception);
		}
	}


	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try {
			return source != null && source.has(key) && source.get(key).isJsonPrimitive()
				? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readInt(JsonObject source, String key, int fallback) {
		try {
			return source != null && source.has(key) && source.get(key).isJsonPrimitive()
				? source.get(key).getAsInt() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public record Settings(boolean enabled, int seasonLengthDays) {
		public Settings {
			seasonLengthDays = Math.max(1, Math.min(365, seasonLengthDays));
		}
	}
}
