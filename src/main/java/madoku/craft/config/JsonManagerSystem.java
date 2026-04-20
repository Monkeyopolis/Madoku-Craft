package madoku.craft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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

public final class JsonManagerSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonManagerSystem.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FIELD_GENERAL = "general";
	private static final String FIELD_MAIN = "main";
	private static final String FIELD_VERSION = "version";
	private static final String FIELD_TYPE = "type";
	private static final String FIELD_ENABLED = "enabled";
	private static volatile String cachedModVersion;

	private JsonManagerSystem() {
	}

	public static void initialize() {
		try {
			Files.createDirectories(getGlobalRootDirectory());
		} catch (IOException exception) {
			LOGGER.error("Failed to initialize JSON root directory at {}", getGlobalRootDirectory(), exception);
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

	static ManagedJsonDocument readManagedDocument(Path file, ManagedJsonType type, JsonObject defaults) throws IOException {
		JsonObject raw = readJsonObject(file);
		JsonObject main = readObject(raw, FIELD_MAIN);
		JsonObject general = readObject(raw, FIELD_GENERAL);
		boolean enabled = resolveManagedEnabled(main, general, defaults);
		return new ManagedJsonDocument(createGeneral(type, enabled), main);
	}

	static void writeManagedDocument(Path file, ManagedJsonType type, JsonObject main, JsonObject defaults) throws IOException {
		JsonObject payload = new JsonObject();
		JsonObject safeMain = main == null ? new JsonObject() : main.deepCopy();
		payload.add(FIELD_GENERAL, createGeneral(type, resolveManagedEnabled(safeMain, null, defaults)));
		payload.add(FIELD_MAIN, safeMain);
		writeJsonObject(file, payload);
	}

	static boolean samePrimitiveType(JsonPrimitive source, JsonPrimitive defaults) {
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

	private static JsonObject createGeneral(ManagedJsonType type, boolean enabled) {
		JsonObject general = new JsonObject();
		general.addProperty(FIELD_VERSION, getCurrentModVersion());
		general.addProperty(FIELD_TYPE, type.id());
		general.addProperty(FIELD_ENABLED, enabled);
		return general;
	}

	private static boolean resolveManagedEnabled(JsonObject main, JsonObject general, JsonObject defaults) {
		Boolean mainEnabled = readBoolean(main, FIELD_ENABLED);
		if (mainEnabled != null) {
			return mainEnabled;
		}

		Boolean generalEnabled = readBoolean(general, FIELD_ENABLED);
		if (generalEnabled != null) {
			return generalEnabled;
		}

		Boolean defaultEnabled = readBoolean(defaults, FIELD_ENABLED);
		if (defaultEnabled != null) {
			return defaultEnabled;
		}

		return true;
	}

	private static Boolean readBoolean(JsonObject root, String key) {
		if (root == null || key == null) {
			return null;
		}

		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return null;
		}

		try {
			return element.getAsBoolean();
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject().deepCopy() : new JsonObject();
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

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(json == null ? new JsonObject() : json.deepCopy(), writer);
		}
	}

	enum ManagedJsonType {
		STATIC("static"),
		DYNAMIC("dynamic");

		private final String id;

		ManagedJsonType(String id) {
			this.id = id;
		}

		String id() {
			return id;
		}
	}

	static final class ManagedJsonDocument {
		private final JsonObject general;
		private final JsonObject main;

		private ManagedJsonDocument(JsonObject general, JsonObject main) {
			this.general = general == null ? new JsonObject() : general.deepCopy();
			this.main = main == null ? new JsonObject() : main.deepCopy();
		}

		JsonObject general() {
			return general.deepCopy();
		}

		JsonObject main() {
			return main.deepCopy();
		}
	}
}
