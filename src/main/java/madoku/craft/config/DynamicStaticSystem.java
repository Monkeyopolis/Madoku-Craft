package madoku.craft.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

public final class DynamicStaticSystem {
	@FunctionalInterface
	public interface DynamicEntryNormalizer {
		JsonElement normalize(String key, JsonElement sourceValue);
	}

	@FunctionalInterface
	public interface DynamicFileSupport {
		boolean isSupported(String fileKey, JsonObject sourceRoot);
	}

	private DynamicStaticSystem() {
	}

	public static JsonObject ensureManagedFile(Path file, JsonObject defaults, DynamicEntryNormalizer dynamicEntryNormalizer) throws IOException {
		JsonObject fallbackDefaults = defaults == null ? new JsonObject() : defaults;
		JsonManagerSystem.ManagedJsonDocument source = JsonManagerSystem.readManagedDocument(
			file,
			JsonManagerSystem.ManagedJsonType.DYNAMIC,
			fallbackDefaults
		);
		JsonObject normalized = normalizeObject(source.main(), fallbackDefaults, dynamicEntryNormalizer);
		JsonManagerSystem.writeManagedDocument(file, JsonManagerSystem.ManagedJsonType.DYNAMIC, normalized, fallbackDefaults);
		return normalized;
	}

	public static JsonObject writeManagedFile(
		Path file,
		JsonObject source,
		JsonObject defaults,
		DynamicEntryNormalizer dynamicEntryNormalizer
	) throws IOException {
		JsonObject fallbackDefaults = defaults == null ? new JsonObject() : defaults;
		JsonObject normalized = normalizeObject(source == null ? new JsonObject() : source, fallbackDefaults, dynamicEntryNormalizer);
		JsonManagerSystem.writeManagedDocument(file, JsonManagerSystem.ManagedJsonType.DYNAMIC, normalized, fallbackDefaults);
		return normalized;
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
			Path file = folder.resolve(fileKey + ".json");
			JsonObject defaults = entry.getValue() == null ? new JsonObject() : entry.getValue();
			JsonObject normalized = ensureManagedFile(file, defaults, dynamicEntryNormalizer);
			normalizedFiles.put(fileKey, normalized);
		}

		try (Stream<Path> stream = Files.list(folder)) {
			stream
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
				.forEach(path -> {
					String fileName = path.getFileName().toString();
					String fileKey = fileName.substring(0, fileName.length() - ".json".length()).trim();
					if (fileKey.isEmpty() || normalizedFiles.containsKey(fileKey)) {
						return;
					}

					try {
						JsonManagerSystem.ManagedJsonDocument source = JsonManagerSystem.readManagedDocument(
							path,
							JsonManagerSystem.ManagedJsonType.DYNAMIC,
							new JsonObject()
						);
						JsonObject sourceRoot = source.main();
						if (!supportPredicate.test(fileKey, sourceRoot)) {
							Files.deleteIfExists(path);
							return;
						}

						JsonObject defaults = defaultsProvider.apply(fileKey);
						JsonObject safeDefaults = defaults == null ? new JsonObject() : defaults;
						JsonObject normalized = normalizeObject(sourceRoot, safeDefaults, dynamicEntryNormalizer);
						JsonManagerSystem.writeManagedDocument(path, JsonManagerSystem.ManagedJsonType.DYNAMIC, normalized, safeDefaults);
						normalizedFiles.put(fileKey, normalized);
					} catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
		} catch (RuntimeException runtimeException) {
			if (runtimeException.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			throw runtimeException;
		}

		return normalizedFiles;
	}

	private static String normalizeFileKey(String rawKey) {
		if (rawKey == null) {
			return "";
		}
		return rawKey.trim().toLowerCase();
	}

	private static JsonObject normalizeObject(JsonObject source, JsonObject defaults, DynamicEntryNormalizer dynamicEntryNormalizer) {
		JsonObject normalized = new JsonObject();

		for (var entry : defaults.entrySet()) {
			String key = entry.getKey();
			JsonElement defaultValue = entry.getValue();
			JsonElement sourceValue = source == null ? null : source.get(key);
			normalized.add(key, normalizeElement(sourceValue, defaultValue, dynamicEntryNormalizer));
		}

		if (source != null && dynamicEntryNormalizer != null) {
			for (var entry : source.entrySet()) {
				String key = entry.getKey();
				if ("general".equals(key) || "main".equals(key) || "version".equals(key) || normalized.has(key)) {
					continue;
				}

				JsonElement normalizedDynamic = dynamicEntryNormalizer.normalize(key, entry.getValue());
				if (normalizedDynamic == null || normalizedDynamic.isJsonNull()) {
					continue;
				}
				normalized.add(key, normalizedDynamic.deepCopy());
			}
		}

		return normalized;
	}

	private static JsonElement normalizeElement(JsonElement source, JsonElement defaults, DynamicEntryNormalizer dynamicEntryNormalizer) {
		if (defaults == null || defaults.isJsonNull()) {
			return JsonNull.INSTANCE;
		}

		if (defaults.isJsonObject()) {
			if (source != null && source.isJsonObject()) {
				return normalizeObject(source.getAsJsonObject(), defaults.getAsJsonObject(), dynamicEntryNormalizer);
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
}
