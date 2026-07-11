package madoku.craft.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** Static configuration for seasonal drought and flood processing. */
public final class SeasonalChangesConfigManager {
	public static final String CONFIG_FOLDER_NAME = EcosystemConfigManager.CONFIG_FOLDER_NAME;
	public static final String CONFIG_FILE_NAME = "seasonal-changes";
	public static final int DEFAULT_SEA_LEVEL = 62;
	public static final String FIELD_BIOME_DROUGHT = "biome-drought";
	public static final String FIELD_BIOME_FLOOD = "biome-flood";
	public static final String FIELD_DEPTH_RATE = "depth-rate";
	public static final String FIELD_TIME_RATE = "time-rate";
	public static final String FIELD_ADJUSTMENT_COUNT = "adjustment-count";
	public static final String FIELD_SEA_LEVEL = "sea-level";
	public static final String FIELD_TYPE = "type";
	public static final String FIELD_VALUE = "value";
	public static final String FIELD_ENABLED = "enabled";
	private static final Logger LOGGER = LoggerFactory.getLogger(SeasonalChangesConfigManager.class);
	private static volatile Settings settings = defaults();

	private SeasonalChangesConfigManager() { }
	public static void initialize() { loadConfig(); }
	public static void reset() { settings = defaults(); }
	public static Settings getSettings() { return settings; }
	public static Settings defaults() { return new Settings(true, true, 1, 1, 7, 7, 0, 0, DEFAULT_SEA_LEVEL); }
	public static JsonObject buildDefaultsJson() { return toJson(defaults()); }
	public static JsonObject toJson(Settings value) {
		Settings safe = value == null ? defaults() : value;
		return JSONFormatManager.object()
			.put(FIELD_SEA_LEVEL, safe.seaLevel())
			.object(FIELD_BIOME_DROUGHT, b -> b.put(FIELD_ENABLED, safe.droughtEnabled()).object(FIELD_DEPTH_RATE, d -> d.put(FIELD_TYPE, "subtraction").put(FIELD_VALUE, safe.droughtDepthRate())).put(FIELD_TIME_RATE, safe.droughtTimeRateDays()).put(FIELD_ADJUSTMENT_COUNT, safe.droughtAdjustmentCount()))
			.object(FIELD_BIOME_FLOOD, b -> b.put(FIELD_ENABLED, safe.floodEnabled()).object(FIELD_DEPTH_RATE, d -> d.put(FIELD_TYPE, "addition").put(FIELD_VALUE, safe.floodDepthRate())).put(FIELD_TIME_RATE, safe.floodTimeRateDays()).put(FIELD_ADJUSTMENT_COUNT, safe.floodAdjustmentCount()))
			.build();
	}
	public static Settings fromJson(JsonObject source) {
		JsonObject drought = object(source, FIELD_BIOME_DROUGHT);
		JsonObject flood = object(source, FIELD_BIOME_FLOOD);
		return new Settings(
			readBoolean(drought, FIELD_ENABLED, true), readBoolean(flood, FIELD_ENABLED, true),
			Math.max(1, readInt(object(drought, FIELD_DEPTH_RATE), FIELD_VALUE, 1)), Math.max(1, readInt(object(flood, FIELD_DEPTH_RATE), FIELD_VALUE, 1)),
			Math.max(1, readInt(drought, FIELD_TIME_RATE, 7)), Math.max(1, readInt(flood, FIELD_TIME_RATE, 7)),
			Math.max(0, readInt(drought, FIELD_ADJUSTMENT_COUNT, 0)), Math.max(0, readInt(flood, FIELD_ADJUSTMENT_COUNT, 0)),
			readInt(source, FIELD_SEA_LEVEL, DEFAULT_SEA_LEVEL));
	}
	private static void loadConfig() {
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = directory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, buildDefaultsJson());
			settings = fromJson(normalized);
			JSONFormatManager.writeManagedFile(file, toJson(settings), buildDefaultsJson());
		} catch (IOException | RuntimeException exception) { settings = defaults(); LOGGER.error("Failed to load seasonal changes configuration; using defaults.", exception); }
	}
	private static JsonObject object(JsonObject source, String key) { JsonElement element = source == null ? null : source.get(key); return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject(); }
	private static boolean readBoolean(JsonObject source, String key, boolean fallback) { try { return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback; } catch (RuntimeException e) { return fallback; } }
	private static int readInt(JsonObject source, String key, int fallback) { try { return source != null && source.has(key) ? source.get(key).getAsInt() : fallback; } catch (RuntimeException e) { return fallback; } }
	public record Settings(boolean droughtEnabled, boolean floodEnabled, int droughtDepthRate, int floodDepthRate, int droughtTimeRateDays, int floodTimeRateDays, int droughtAdjustmentCount, int floodAdjustmentCount, int seaLevel) { public Settings { droughtDepthRate = Math.max(1, droughtDepthRate); floodDepthRate = Math.max(1, floodDepthRate); droughtTimeRateDays = Math.max(1, droughtTimeRateDays); floodTimeRateDays = Math.max(1, floodTimeRateDays); } }
}
