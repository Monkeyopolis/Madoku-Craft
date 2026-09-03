package madoku.craft.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.json.JSONFormatAPIManager;
import madoku.craft.core.json.JSONAPIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client configuration subsystem for Madoku HUD. */
public final class HudConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(HudConfigManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-hud";
	private static final String CONFIG_FILE_NAME = "madoku-hud";
	private static final String HUD_GROUP = "hud";
	private static final String ENABLED = "enabled";
	private static final String COLORED_TEXT = "colored-text";
	private static volatile Settings settings = Settings.defaults();

	private HudConfigManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		settings = Settings.defaults();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static boolean isEnabled(String entry) {
		return settings.enabled && settings.entries.getOrDefault(normalize(entry), EntrySettings.DISABLED).enabled;
	}

	public static boolean isColored(String entry) {
		return isEnabled(entry) && settings.entries.getOrDefault(normalize(entry), EntrySettings.DISABLED).coloredText;
	}

	public static JsonObject buildDefaultsJson() {
		return Settings.defaults().toConfigJson();
	}

	private static void loadConfig() {
		Settings fallback = Settings.defaults();
		try {
			Path directory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = directory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, fallback.toConfigJson());
			Settings loaded = Settings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, loaded.toConfigJson(), fallback.toConfigJson());
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load Madoku HUD configuration; using defaults.", exception);
		}
	}

	private static JsonObject readObject(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static final class EntrySettings {
		private final boolean enabled;
		private final boolean coloredText;
		private final boolean supportsColoredText;
		private static final EntrySettings DISABLED = new EntrySettings(false, false, false);

		private EntrySettings(boolean enabled, boolean coloredText, boolean supportsColoredText) {
			this.enabled = enabled;
			this.coloredText = coloredText;
			this.supportsColoredText = supportsColoredText;
		}

		private static EntrySettings defaults(boolean coloredText) {
			return new EntrySettings(true, coloredText, coloredText);
		}

		private static EntrySettings fromJson(JsonObject source, EntrySettings fallback) {
			return new EntrySettings(
				readBoolean(source, ENABLED, fallback.enabled),
				readBoolean(source, COLORED_TEXT, fallback.coloredText),
				fallback.supportsColoredText);
		}

		private void write(JSONFormatAPIManager.ObjectBuilder builder) {
			builder.put(ENABLED, enabled);
			if (supportsColoredText) {
				builder.put(COLORED_TEXT, coloredText);
			}
		}
	}

	private static final class Settings {
		private final boolean enabled;
		private final Map<String, EntrySettings> entries;

		private Settings(boolean enabled, Map<String, EntrySettings> entries) {
			this.enabled = enabled;
			this.entries = Map.copyOf(entries);
		}

		private static Settings defaults() {
			LinkedHashMap<String, EntrySettings> entries = new LinkedHashMap<>();
			entries.put("day", EntrySettings.defaults(false));
			entries.put("time", EntrySettings.defaults(true));
			entries.put("season", EntrySettings.defaults(true));
			entries.put("temperature", EntrySettings.defaults(true));
			entries.put("humidity", EntrySettings.defaults(true));
			entries.put("biome", EntrySettings.defaults(false));
			entries.put("difficulty", EntrySettings.defaults(true));
			entries.put("health", EntrySettings.defaults(false));
			entries.put("hunger", EntrySettings.defaults(false));
			entries.put("armor", EntrySettings.defaults(false));
			entries.put("oxygen", EntrySettings.defaults(false));
			entries.put("luck", EntrySettings.defaults(false));
			return new Settings(true, entries);
		}

		private static Settings fromJson(JsonObject source) {
			Settings fallback = defaults();
			JsonObject hud = readObject(source, HUD_GROUP);
			LinkedHashMap<String, EntrySettings> entries = new LinkedHashMap<>();
			for (Map.Entry<String, EntrySettings> entry : fallback.entries.entrySet()) {
				entries.put(entry.getKey(), EntrySettings.fromJson(readObject(hud, entry.getKey()), entry.getValue()));
			}
			return new Settings(readBoolean(source, ENABLED, fallback.enabled), entries);
		}

		private JsonObject toConfigJson() {
			JSONFormatAPIManager.ObjectBuilder root = JSONFormatAPIManager.object();
			root.object(HUD_GROUP, hud -> {
				for (Map.Entry<String, EntrySettings> entry : entries.entrySet()) {
					hud.object(entry.getKey(), child -> entry.getValue().write(child));
				}
			});
			return root.build();
		}
	}
}

