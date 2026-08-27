package madoku.craft.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** Runtime group for building and normalizing managed JSON formats. */
public final class JSONFormatManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(JSONFormatManager.class);
	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static volatile boolean initialized;

	@FunctionalInterface
	public interface DynamicEntryNormalizer {
		JsonElement normalize(String key, JsonElement sourceValue);
	}

	@FunctionalInterface
	public interface DynamicFileSupport {
		boolean isSupported(String fileKey, JsonObject sourceRoot);
	}

	private JSONFormatManager() { }

	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }

	public static ObjectBuilder object() { return new ObjectBuilder(); }
	public static ArrayBuilder array() { return new ArrayBuilder(); }
	public static ObjectBuilder config() { return object(); }
	public static ObjectBuilder data() { return object(); }

	public static JsonObject ensureManagedFile(Path file, JsonObject defaults) throws IOException {
		return ensureManagedFile(file, defaults, JSONTypeManager.STATIC_CONFIG, null);
	}

	public static JsonObject ensureManagedFile(
		Path file,
		JsonObject defaults,
		JSONTypeManager.Type type,
		DynamicEntryNormalizer dynamicEntryNormalizer
	) throws IOException {
		JsonObject safeDefaults = defaults == null ? new JsonObject() : defaults.deepCopy();
		ManagedDocument source = readManagedDocument(file);
		JsonObject normalized = normalizeObject(source.data(), safeDefaults, dynamicEntryNormalizer);
		normalized.addProperty(JSONTypeManager.FIELD_ENABLED,
			JSONTypeManager.readEnabled(source.settings(), JSONTypeManager.readEnabled(safeDefaults, true)));
		writeManagedDocument(file, normalized, source.settings(), type);
		return normalized.deepCopy();
	}

	public static JsonObject writeManagedFile(Path file, JsonObject source, JsonObject defaults) throws IOException {
		return writeManagedFile(file, source, defaults, JSONTypeManager.STATIC_CONFIG, null);
	}

	public static JsonObject writeManagedFile(
		Path file,
		JsonObject source,
		JsonObject defaults,
		DynamicEntryNormalizer dynamicEntryNormalizer
	) throws IOException {
		return writeManagedFile(file, source, defaults, JSONTypeManager.DYNAMIC_CONFIG, dynamicEntryNormalizer);
	}

	public static JsonObject writeManagedFile(
		Path file,
		JsonObject source,
		JsonObject defaults,
		JSONTypeManager.Type type,
		DynamicEntryNormalizer dynamicEntryNormalizer
	) throws IOException {
		JsonObject safeDefaults = defaults == null ? new JsonObject() : defaults.deepCopy();
		JsonObject safeSource = source == null ? new JsonObject() : source.deepCopy();
		JsonObject normalized = normalizeObject(safeSource, safeDefaults, dynamicEntryNormalizer);
		ManagedDocument existing = readManagedDocument(file);
		JsonObject settings = existing.settings();
		boolean enabled = JSONTypeManager.readEnabled(settings, true);
		if (safeSource.has(JSONTypeManager.FIELD_ENABLED)) enabled = JSONTypeManager.readEnabled(safeSource, enabled);
		normalized.addProperty(JSONTypeManager.FIELD_ENABLED, enabled);
		writeManagedDocument(file, normalized, settings, type);
		return normalized.deepCopy();
	}

	public static Map<String, JsonObject> ensureManagedFolder(
		Path folder,
		Map<String, JsonObject> staticDefaultsByFile,
		Function<String, JsonObject> dynamicDefaultsProvider,
		DynamicFileSupport dynamicFileSupport,
		DynamicEntryNormalizer dynamicEntryNormalizer
	) throws IOException {
		Files.createDirectories(folder);
		Map<String, JsonObject> normalizedFiles = new LinkedHashMap<>();
		Map<String, JsonObject> staticDefaults = staticDefaultsByFile == null ? Map.of() : staticDefaultsByFile;
		Function<String, JsonObject> defaultsProvider = dynamicDefaultsProvider == null ? ignored -> new JsonObject() : dynamicDefaultsProvider;
		BiPredicate<String, JsonObject> supportPredicate = dynamicFileSupport == null ? (ignored, ignoredRoot) -> true : dynamicFileSupport::isSupported;

		for (Map.Entry<String, JsonObject> entry : staticDefaults.entrySet()) {
			String fileKey = normalizeFileKey(entry.getKey());
			if (fileKey.isBlank()) continue;
			Path file = safeChild(folder, fileKey + ".json");
			JsonObject normalized = ensureManagedFile(file, entry.getValue(), JSONTypeManager.DYNAMIC_CONFIG, dynamicEntryNormalizer);
			normalizedFiles.put(fileKey, normalized);
		}

		try (Stream<Path> stream = Files.list(folder)) {
			stream.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
				.forEach(path -> {
					String fileName = path.getFileName().toString();
					String fileKey = fileName.substring(0, fileName.length() - ".json".length()).trim();
					if (fileKey.isEmpty() || normalizedFiles.containsKey(fileKey)) return;
					try {
						ManagedDocument source = readManagedDocument(path);
						JsonObject sourceData = source.data();
						sourceData.addProperty(JSONTypeManager.FIELD_ENABLED, JSONTypeManager.readEnabled(source.settings(), true));
						if (!supportPredicate.test(fileKey, sourceData)) {
							Files.deleteIfExists(path);
							return;
						}
						JsonObject defaults = defaultsProvider.apply(fileKey);
						JsonObject safeDefaults = defaults == null ? new JsonObject() : defaults;
						JsonObject normalized = normalizeObject(sourceData, safeDefaults, dynamicEntryNormalizer);
						normalized.addProperty(JSONTypeManager.FIELD_ENABLED, JSONTypeManager.readEnabled(source.settings(), true));
						writeManagedDocument(path, normalized, source.settings(), JSONTypeManager.DYNAMIC_CONFIG);
						normalizedFiles.put(fileKey, normalized);
					} catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
		} catch (RuntimeException exception) {
			if (exception.getCause() instanceof IOException ioException) throw ioException;
			throw exception;
		}
		return normalizedFiles;
	}

	public static ManagedDocument readManagedDocument(Path file) throws IOException {
		JsonObject raw = readJsonObject(file);
		JsonElement settingsElement = raw.get(JSONTypeManager.SETTINGS_GROUP);
		JsonObject settings = settingsElement != null && settingsElement.isJsonObject()
			? settingsElement.getAsJsonObject().deepCopy() : new JsonObject();
		raw.remove(JSONTypeManager.SETTINGS_GROUP);
		return new ManagedDocument(raw, settings);
	}

	public static void writeManagedDocument(
		Path file,
		JsonObject data,
		JsonObject existingSettings,
		JSONTypeManager.Type type
	) throws IOException {
		JsonObject root = new JsonObject();
		JsonObject settings = JSONTypeManager.createSettings(existingSettings, type, true);
		root.add(JSONTypeManager.SETTINGS_GROUP, settings);
		if (data != null) {
			for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
				if (JSONTypeManager.SETTINGS_GROUP.equals(entry.getKey())) continue;
				if (JSONTypeManager.FIELD_ENABLED.equals(entry.getKey())) {
					settings.addProperty(JSONTypeManager.FIELD_ENABLED, JSONTypeManager.readEnabled(data, JSONTypeManager.readEnabled(settings, true)));
					continue;
				}
				root.add(entry.getKey(), entry.getValue() == null ? JsonNull.INSTANCE : entry.getValue().deepCopy());
			}
		}
		writeJsonObject(file, root, !type.isData());
	}

	private static JsonObject normalizeObject(JsonObject source, JsonObject defaults, DynamicEntryNormalizer dynamicEntryNormalizer) {
		JsonObject normalized = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
			String key = entry.getKey();
			if (JSONTypeManager.SETTINGS_GROUP.equals(key)) continue;
			normalized.add(key, normalizeElement(source == null ? null : source.get(key), entry.getValue(), dynamicEntryNormalizer));
		}
		if (source != null && dynamicEntryNormalizer != null) {
			for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
				String key = entry.getKey();
				if (JSONTypeManager.SETTINGS_GROUP.equals(key)
					|| "general".equals(key)
					|| "main".equals(key)
					|| "version".equals(key)
					|| normalized.has(key)) continue;
				JsonElement value = dynamicEntryNormalizer.normalize(key, entry.getValue());
				if (value != null && !value.isJsonNull()) normalized.add(key, value.deepCopy());
			}
		}
		return normalized;
	}

	private static JsonElement normalizeElement(JsonElement source, JsonElement defaults, DynamicEntryNormalizer dynamicEntryNormalizer) {
		if (defaults == null || defaults.isJsonNull()) return JsonNull.INSTANCE;
		if (defaults.isJsonObject()) {
			return source != null && source.isJsonObject()
				? normalizeObject(source.getAsJsonObject(), defaults.getAsJsonObject(), dynamicEntryNormalizer)
				: defaults.deepCopy();
		}
		if (defaults.isJsonArray()) return source != null && source.isJsonArray() ? source.deepCopy() : defaults.deepCopy();
		if (defaults.isJsonPrimitive() && source != null && source.isJsonPrimitive()
			&& samePrimitiveType(source.getAsJsonPrimitive(), defaults.getAsJsonPrimitive())) return source.deepCopy();
		return defaults.deepCopy();
	}

	private static boolean samePrimitiveType(com.google.gson.JsonPrimitive source, com.google.gson.JsonPrimitive defaults) {
		if (defaults.isBoolean()) return source.isBoolean();
		if (defaults.isNumber()) return source.isNumber();
		if (defaults.isString()) return source.isString();
		return false;
	}

	private static String normalizeFileKey(String rawKey) { return rawKey == null ? "" : rawKey.trim().toLowerCase(); }

	private static Path safeChild(Path folder, String name) {
		Path root = folder.toAbsolutePath().normalize();
		Path child = root.resolve(name).normalize();
		if (!child.startsWith(root)) throw new IllegalArgumentException("JSON file must remain inside its managed folder.");
		return child;
	}

	private static JsonObject readJsonObject(Path file) throws IOException {
		if (!Files.isRegularFile(file)) return new JsonObject();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (Exception exception) {
			LOGGER.warn("Invalid JSON in {}, recreating from defaults.", file);
			return new JsonObject();
		}
	}

	private static void writeJsonObject(Path file, JsonObject json, boolean pretty) throws IOException {
		Path parent = file.toAbsolutePath().normalize().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temporaryFile = Files.createTempFile(parent, "madoku-json-", ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
				(pretty ? PRETTY_GSON : COMPACT_GSON).toJson(json == null ? new JsonObject() : json, writer);
			}
			try {
				Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException | AccessDeniedException exception) {
				moveWithRetries(temporaryFile, file);
			}
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	private static void moveWithRetries(Path source, Path target) throws IOException {
		IOException lastFailure = null;
		for (int attempt = 0; attempt < 8; attempt++) {
			try {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (AccessDeniedException exception) {
				lastFailure = exception;
				if (attempt == 7) {
					break;
				}
				try {
					Thread.sleep(25L);
				} catch (InterruptedException interruptedException) {
					Thread.currentThread().interrupt();
					throw exception;
				}
			}
		}
		throw lastFailure == null
			? new IOException("Failed to replace managed JSON file: " + target)
			: lastFailure;
	}

	public static final class ManagedDocument {
		private final JsonObject data;
		private final JsonObject settings;
		private final boolean hasSettings;

		private ManagedDocument(JsonObject data, JsonObject settings) {
			this.data = data == null ? new JsonObject() : data.deepCopy();
			this.settings = settings == null ? new JsonObject() : settings.deepCopy();
			this.hasSettings = settings != null && !settings.isEmpty();
		}

		public JsonObject data() { return data.deepCopy(); }
		public JsonObject settings() { return settings.deepCopy(); }
		public boolean hasSettings() { return hasSettings; }
	}

	public static final class ObjectBuilder {
		private final JsonObject object = new JsonObject();
		private ObjectBuilder() { }
		public ObjectBuilder put(String key, String value) { if (!isBlank(key)) object.addProperty(key, value); return this; }
		public ObjectBuilder put(String key, Number value) { if (!isBlank(key)) object.addProperty(key, value); return this; }
		public ObjectBuilder put(String key, Boolean value) { if (!isBlank(key)) object.addProperty(key, value); return this; }
		public ObjectBuilder put(String key, JsonElement value) { if (!isBlank(key)) object.add(key, value == null ? JsonNull.INSTANCE : value.deepCopy()); return this; }
		public ObjectBuilder solo(String key, String value) { return put(key, value); }
		public ObjectBuilder solo(String key, Number value) { return put(key, value); }
		public ObjectBuilder solo(String key, Boolean value) { return put(key, value); }
		public ObjectBuilder solo(String key, JsonElement value) { return put(key, value); }
		public ObjectBuilder putAll(JsonObject source) { if (source != null) source.entrySet().forEach(entry -> put(entry.getKey(), entry.getValue())); return this; }
		public ObjectBuilder group(String key, Consumer<ObjectBuilder> consumer) { return object(key, consumer); }
		public ObjectBuilder object(String key, Consumer<ObjectBuilder> consumer) { if (!isBlank(key)) { ObjectBuilder child = JSONFormatManager.object(); if (consumer != null) consumer.accept(child); object.add(key, child.build()); } return this; }
		public ObjectBuilder array(String key, Consumer<ArrayBuilder> consumer) { if (!isBlank(key)) { ArrayBuilder child = JSONFormatManager.array(); if (consumer != null) consumer.accept(child); object.add(key, child.build()); } return this; }
		public JsonObject build() { return object.deepCopy(); }
	}

	public static final class ArrayBuilder {
		private final JsonArray array = new JsonArray();
		private ArrayBuilder() { }
		public ArrayBuilder add(String value) { array.add(value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value)); return this; }
		public ArrayBuilder add(Number value) { array.add(value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value)); return this; }
		public ArrayBuilder add(Boolean value) { array.add(value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value)); return this; }
		public ArrayBuilder add(JsonElement value) { array.add(value == null ? JsonNull.INSTANCE : value.deepCopy()); return this; }
		public ArrayBuilder group(Consumer<ObjectBuilder> consumer) { return object(consumer); }
		public ArrayBuilder object(Consumer<ObjectBuilder> consumer) { ObjectBuilder child = JSONFormatManager.object(); if (consumer != null) consumer.accept(child); array.add(child.build()); return this; }
		public ArrayBuilder array(Consumer<ArrayBuilder> consumer) { ArrayBuilder child = JSONFormatManager.array(); if (consumer != null) consumer.accept(child); array.add(child.build()); return this; }
		public ArrayBuilder addAll(JsonArray source) { if (source != null) source.forEach(this::add); return this; }
		public JsonArray build() { return array.deepCopy(); }
	}

	private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
