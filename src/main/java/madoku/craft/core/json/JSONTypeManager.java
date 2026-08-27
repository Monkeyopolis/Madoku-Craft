package madoku.craft.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/** Runtime group for identifying the format and purpose of managed JSON files. */
public final class JSONTypeManager {
	public static final String SETTINGS_GROUP = "settings";
	public static final String FIELD_VERSION = "version";
	public static final String FIELD_TYPE = "type";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_AUTOSAVE = "autoSave";

	public enum Format {
		STATIC("static"),
		DYNAMIC("dynamic");

		private final String id;

		Format(String id) { this.id = id; }
		public String id() { return id; }
	}

	public enum Purpose {
		DATA("data"),
		CONFIG("config");

		private final String id;

		Purpose(String id) { this.id = id; }
		public String id() { return id; }
	}

	public record Type(Format format, Purpose purpose) {
		public Type {
			format = format == null ? Format.STATIC : format;
			purpose = purpose == null ? Purpose.CONFIG : purpose;
			if (purpose == Purpose.DATA && format != Format.STATIC) {
				throw new IllegalArgumentException("DATA JSON files must use STATIC format.");
			}
		}

		public boolean isStatic() { return format == Format.STATIC; }
		public boolean isDynamic() { return format == Format.DYNAMIC; }
		public boolean isData() { return purpose == Purpose.DATA; }
		public boolean isConfig() { return purpose == Purpose.CONFIG; }
	}

	public static final Type STATIC_DATA = new Type(Format.STATIC, Purpose.DATA);
	public static final Type STATIC_CONFIG = new Type(Format.STATIC, Purpose.CONFIG);
	public static final Type DYNAMIC_CONFIG = new Type(Format.DYNAMIC, Purpose.CONFIG);

	private static volatile boolean initialized;

	private JSONTypeManager() { }

	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }

	public static Type identify(JsonObject settings) {
		Format format = Format.STATIC;
		Purpose purpose = Purpose.CONFIG;
		JsonElement typeElement = settings == null ? null : settings.get(FIELD_TYPE);
		if (typeElement != null && typeElement.isJsonArray()) {
			JsonArray values = typeElement.getAsJsonArray();
			if (values.size() > 0 && "dynamic".equals(normalizeTypeId(stringValue(values.get(0)), "static"))) {
				format = Format.DYNAMIC;
			}
			if (values.size() > 1 && "data".equals(normalizeTypeId(stringValue(values.get(1)), "config"))) {
				purpose = Purpose.DATA;
			}
		}
		return purpose == Purpose.DATA ? STATIC_DATA : new Type(format, purpose);
	}

	public static boolean isType(JsonObject settings, Type expected) {
		return expected != null && expected.equals(identify(settings));
	}

	static JsonObject createSettings(JsonObject source, Type type, boolean defaultEnabled) {
		JsonObject sourceSettings = source == null ? new JsonObject() : source.deepCopy();
		boolean enabled = readBoolean(sourceSettings, FIELD_ENABLED, defaultEnabled);
		JsonObject settings = new JsonObject();
		settings.addProperty(FIELD_VERSION, MadokuJSONManager.getCurrentModVersion());
		settings.addProperty(FIELD_ENABLED, enabled);
		JsonArray typeArray = new JsonArray();
		typeArray.add(type.format().id());
		typeArray.add(type.purpose().id());
		settings.add(FIELD_TYPE, typeArray);
		for (var entry : sourceSettings.entrySet()) {
			if (FIELD_VERSION.equals(entry.getKey()) || FIELD_ENABLED.equals(entry.getKey()) || FIELD_TYPE.equals(entry.getKey())) {
				continue;
			}
			settings.add(entry.getKey(), entry.getValue() == null ? com.google.gson.JsonNull.INSTANCE : entry.getValue().deepCopy());
		}
		return settings;
	}

	static boolean readEnabled(JsonObject settings, boolean fallback) {
		return readBoolean(settings, FIELD_ENABLED, fallback);
	}

	static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		try {
			return source != null && source.has(key) ? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static String normalizeTypeId(String value, String fallback) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		return normalized.isBlank() ? fallback : normalized;
	}

	private static String stringValue(JsonElement value) {
		try { return value == null ? "" : value.getAsString(); }
		catch (RuntimeException exception) { return ""; }
	}
}
