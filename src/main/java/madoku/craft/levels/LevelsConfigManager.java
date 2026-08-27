package madoku.craft.levels;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.levels.MadokuLevelsManager.LevelStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;

/** Owns the static Madoku Levels configuration and its managed JSON shape. */
public final class LevelsConfigManager {
	public static final String CONFIG_ROOT_FOLDER_NAME = "madoku-craft";
	public static final String CONFIG_FOLDER_NAME = "madoku-craft-levels";
	public static final String CONFIG_FILE_NAME = "madoku-levels";

	private static final Logger LOGGER = LoggerFactory.getLogger(LevelsConfigManager.class);
	private static volatile Settings settings = Settings.defaults();

	private LevelsConfigManager() { }

	public static void initialize() {
		load();
	}

	public static void reload() {
		load();
	}

	public static void reset() {
		settings = Settings.defaults();
	}

	public static Settings settings() {
		return settings;
	}

	public static boolean isEnabled() {
		return settings.enabled();
	}

	public static PlayerSettings player() {
		return settings.player();
	}

	public static StatSettings stat(LevelStat stat) {
		return stat == null ? StatSettings.defaults() : settings.stats().getOrDefault(stat, StatSettings.defaults());
	}

	public static JsonObject buildDefaultsJson() {
		return Settings.defaults().toJson();
	}

	private static void load() {
		Settings fallback = Settings.defaults();
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = directory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, fallback.toJson());
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(file, loaded.toJson(), fallback.toJson());
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.warn("Using default Madoku Levels settings after configuration load failure.", exception);
		}
	}

	public enum IncrementType {
		FLAT("flat"),
		PERCENTAGE("percentage");

		private final String id;

		IncrementType(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		private static IncrementType fromId(String id) {
			for (IncrementType type : values()) {
				if (type.id.equalsIgnoreCase(id == null ? "" : id.trim())) {
					return type;
				}
			}
			return FLAT;
		}
	}

	public record Settings(boolean enabled, PlayerSettings player, EnumMap<LevelStat, StatSettings> stats) {
		private static Settings defaults() {
			EnumMap<LevelStat, StatSettings> stats = new EnumMap<>(LevelStat.class);
			for (LevelStat stat : LevelStat.values()) {
				stats.put(stat, StatSettings.defaultsFor(stat));
			}
			return new Settings(true, PlayerSettings.defaults(), stats);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			JsonObject levels = object(source, "levels");
			JsonObject player = object(levels, "player");
			EnumMap<LevelStat, StatSettings> stats = new EnumMap<>(LevelStat.class);
			JsonObject statsObject = object(source, "stats");
			for (LevelStat stat : LevelStat.values()) {
				stats.put(stat, StatSettings.fromJson(object(statsObject, stat.id()), defaults.stats().get(stat)));
			}
			return new Settings(
				readBoolean(source, "enabled", defaults.enabled()),
				new PlayerSettings(
					readPositiveInt(player, "max-level", defaults.player().maxLevel()),
					readNonNegativeDouble(player, "base-xp-requirement", defaults.player().baseXpRequirement()),
					readNonNegativeDouble(player, "base-xp-multiplier", defaults.player().baseXpMultiplier())
				),
				stats
			);
		}

		private JsonObject toJson() {
			JsonObject statsJson = new JsonObject();
			for (LevelStat stat : LevelStat.values()) {
				statsJson.add(stat.id(), stats.getOrDefault(stat, StatSettings.defaultsFor(stat)).toJson());
			}
			return JSONFormatManager.object()
				.put("enabled", enabled)
				.object("levels", levels -> levels.object("player", player -> player
					.put("max-level", this.player.maxLevel())
					.put("base-xp-requirement", this.player.baseXpRequirement())
					.put("base-xp-multiplier", this.player.baseXpMultiplier())))
				.put("stats", statsJson)
				.build();
		}
	}

	public record PlayerSettings(int maxLevel, double baseXpRequirement, double baseXpMultiplier) {
		private static PlayerSettings defaults() {
			return new PlayerSettings(60, 5.0d, 0.05d);
		}
	}

	public record StatSettings(int maxLevel, IncrementType type, double value) {
		private static StatSettings defaults() {
			return new StatSettings(10, IncrementType.FLAT, 0.0d);
		}

		private static StatSettings defaultsFor(LevelStat stat) {
			return new StatSettings(10, IncrementType.FLAT, stat == null ? 0.0d : stat.defaultIncrement());
		}

		private static StatSettings fromJson(JsonObject source, StatSettings fallback) {
			StatSettings defaults = fallback == null ? defaults() : fallback;
			JsonObject increment = object(source, "level-increment");
			return new StatSettings(
				readPositiveInt(source, "max-level", defaults.maxLevel()),
				IncrementType.fromId(readString(increment, "type", defaults.type().id())),
				readNonNegativeDouble(increment, "value", defaults.value())
			);
		}

		private JsonObject toJson() {
			return JSONFormatManager.object()
				.put("max-level", maxLevel)
				.object("level-increment", increment -> increment
					.put("type", type.id())
					.put("value", value))
				.build();
		}
	}

	private static JsonObject object(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static String readString(JsonObject source, String key, String fallback) {
		try { return source != null && source.has(key) ? source.get(key).getAsString() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static int readPositiveInt(JsonObject source, String key, int fallback) {
		try { return source != null && source.has(key) ? Math.max(1, source.get(key).getAsInt()) : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static double readNonNegativeDouble(JsonObject source, String key, double fallback) {
		try {
			double value = source != null && source.has(key) ? source.get(key).getAsDouble() : fallback;
			return Double.isFinite(value) ? Math.max(0.0d, value) : fallback;
		} catch (RuntimeException exception) { return fallback; }
	}
}
