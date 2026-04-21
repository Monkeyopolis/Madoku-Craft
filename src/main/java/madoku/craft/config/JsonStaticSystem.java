package madoku.craft.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

public final class JsonStaticSystem {
	private JsonStaticSystem() {
	}

	public static ManagedStaticDocument readManagedDocument(Path file) throws IOException {
		JsonManagerSystem.ManagedJsonDocument source = JsonManagerSystem.readManagedDocument(
			file,
			JsonManagerSystem.ManagedJsonType.STATIC
		);
		return new ManagedStaticDocument(source.general(), source.main());
	}

	public static ManagedStaticDocument writeManagedDocument(Path file, JsonObject main, JsonObject general) throws IOException {
		JsonObject safeMain = main == null ? new JsonObject() : main.deepCopy();
		JsonObject safeGeneral = general == null ? new JsonObject() : general.deepCopy();
		JsonManagerSystem.writeManagedDocumentWithGeneral(file, JsonManagerSystem.ManagedJsonType.STATIC, safeGeneral, safeMain);
		return new ManagedStaticDocument(safeGeneral, safeMain);
	}

	public static JsonObject ensureManagedFile(Path file, JsonObject defaults) throws IOException {
		JsonObject fallbackDefaults = defaults == null ? new JsonObject() : defaults;
		JsonManagerSystem.ManagedJsonDocument source = JsonManagerSystem.readManagedDocument(
			file,
			JsonManagerSystem.ManagedJsonType.STATIC,
			fallbackDefaults
		);
		JsonObject normalized = normalizeObject(source.main(), fallbackDefaults);
		JsonManagerSystem.writeManagedDocument(file, JsonManagerSystem.ManagedJsonType.STATIC, normalized, fallbackDefaults);
		return normalized;
	}

	public static JsonObject writeManagedFile(Path file, JsonObject source, JsonObject defaults) throws IOException {
		JsonObject fallbackDefaults = defaults == null ? new JsonObject() : defaults;
		JsonObject normalized = normalizeObject(source == null ? new JsonObject() : source, fallbackDefaults);
		JsonManagerSystem.writeManagedDocument(file, JsonManagerSystem.ManagedJsonType.STATIC, normalized, fallbackDefaults);
		return normalized;
	}

	private static JsonObject normalizeObject(JsonObject source, JsonObject defaults) {
		JsonObject normalized = new JsonObject();
		for (var entry : defaults.entrySet()) {
			String key = entry.getKey();
			JsonElement defaultValue = entry.getValue();
			JsonElement sourceValue = source == null ? null : source.get(key);
			normalized.add(key, normalizeElement(sourceValue, defaultValue));
		}
		return normalized;
	}

	private static JsonElement normalizeElement(JsonElement source, JsonElement defaults) {
		if (defaults == null || defaults.isJsonNull()) {
			return JsonNull.INSTANCE;
		}

		if (defaults.isJsonObject()) {
			if (source != null && source.isJsonObject()) {
				return normalizeObject(source.getAsJsonObject(), defaults.getAsJsonObject());
			}
			return defaults.deepCopy();
		}

		if (defaults.isJsonArray()) {
			if (source != null && source.isJsonArray()) {
				return source.deepCopy();
			}
			return defaults.deepCopy();
		}

		if (defaults.isJsonPrimitive()) {
			if (source != null
				&& source.isJsonPrimitive()
				&& JsonManagerSystem.samePrimitiveType(source.getAsJsonPrimitive(), defaults.getAsJsonPrimitive())) {
				return source.deepCopy();
			}
			return defaults.deepCopy();
		}

		return defaults.deepCopy();
	}

	public static final class ManagedStaticDocument {
		private final JsonObject general;
		private final JsonObject main;

		private ManagedStaticDocument(JsonObject general, JsonObject main) {
			this.general = general == null ? new JsonObject() : general.deepCopy();
			this.main = main == null ? new JsonObject() : main.deepCopy();
		}

		public JsonObject general() {
			return general.deepCopy();
		}

		public JsonObject main() {
			return main.deepCopy();
		}
	}
}
