package madoku.craft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import madoku.craft.MadokuCraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StaticJsonSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(StaticJsonSystem.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile String cachedModVersion;

	private StaticJsonSystem() {
	}

	public static void initialize() {
		try {
			Files.createDirectories(getGlobalRootDirectory());
		} catch (IOException exception) {
			LOGGER.error("Failed to initialize static config root directory at {}", getGlobalRootDirectory(), exception);
		}
	}

	public static Path getGlobalRootDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(MadokuCraft.MOD_ID);
	}

	public static Path getWorldRootDirectory(MinecraftServer server) {
		if (server == null) {
			throw new IllegalArgumentException("Server must not be null.");
		}
		return server.getWorldPath(LevelResource.ROOT).resolve(MadokuCraft.MOD_ID);
	}

	public static Path getOrCreateGlobalSystemDirectory(String systemName) {
		String normalizedName = systemName == null ? "" : systemName.trim();
		if (normalizedName.isEmpty()) {
			throw new IllegalArgumentException("System directory name must not be blank.");
		}

		Path directory = getGlobalRootDirectory().resolve(normalizedName);
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create global system directory: " + directory, exception);
		}
		return directory;
	}

	public static Path getOrCreateWorldSystemDirectory(MinecraftServer server, String systemName) {
		String normalizedName = systemName == null ? "" : systemName.trim();
		if (normalizedName.isEmpty()) {
			throw new IllegalArgumentException("System directory name must not be blank.");
		}

		Path directory = getWorldRootDirectory(server).resolve(normalizedName);
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create world system directory: " + directory, exception);
		}
		return directory;
	}

	public static JsonObject ensureManagedFile(Path file, JsonObject defaults) throws IOException {
		JsonObject fallbackDefaults = defaults == null ? new JsonObject() : defaults;
		JsonObject source = readJsonFile(file);
		JsonObject normalized = normalizeObject(source, fallbackDefaults);
		writeJsonFile(file, normalized);
		return normalized;
	}

	public static JsonObject writeManagedFile(Path file, JsonObject source, JsonObject defaults) throws IOException {
		JsonObject fallbackDefaults = defaults == null ? new JsonObject() : defaults;
		JsonObject normalized = normalizeObject(source == null ? new JsonObject() : source, fallbackDefaults);
		writeJsonFile(file, normalized);
		return normalized;
	}

	public static String getCurrentModVersion() {
		String cached = cachedModVersion;
		if (cached != null && !cached.isBlank()) {
			return cached;
		}

		String resolved = FabricLoader.getInstance()
			.getModContainer(MadokuCraft.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
		cachedModVersion = resolved;
		return resolved;
	}

	public static JsonObject readJsonFile(Path file) throws IOException {
		return readJsonObject(file);
	}

	public static void writeJsonFile(Path file, JsonObject json) throws IOException {
		writeJsonObject(file, json == null ? new JsonObject() : json);
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
			if (source != null && source.isJsonPrimitive() && samePrimitiveType(source.getAsJsonPrimitive(), defaults.getAsJsonPrimitive())) {
				return source.deepCopy();
			}
			return defaults.deepCopy();
		}

		return defaults.deepCopy();
	}

	private static boolean samePrimitiveType(JsonPrimitive source, JsonPrimitive defaults) {
		if (defaults.isBoolean()) {
			return source.isBoolean();
		}
		if (defaults.isNumber()) {
			return source.isNumber();
		}
		if (defaults.isString()) {
			return source.isString();
		}
		return false;
	}

	private static JsonObject readJsonObject(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			return new JsonObject();
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed != null && parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
		} catch (Exception exception) {
			LOGGER.warn("Invalid JSON in {}, recreating from defaults.", file);
		}

		return new JsonObject();
	}

	private static void writeJsonObject(Path file, JsonObject json) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		JsonObject payload = json == null ? new JsonObject() : json.deepCopy();
		payload.addProperty("version", getCurrentModVersion());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(payload, writer);
		}
	}
}
