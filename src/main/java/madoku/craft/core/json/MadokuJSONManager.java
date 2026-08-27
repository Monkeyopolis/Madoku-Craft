package madoku.craft.core.json;

import com.google.gson.JsonObject;
import madoku.craft.MadokuCraft;
import madoku.craft.core.data.DataSaveCoordinatorManager;
import madoku.craft.core.time.MadokuTimeManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime API subsystem orchestrating JSON formatting and type management. */
public final class MadokuJSONManager {
	private static final String GLOBAL_ROOT_FOLDER_NAME = MadokuCraft.MOD_ID;
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuJSONManager.class);
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;
	private static final Map<Path, JsonObject> SETTINGS_CACHE = new ConcurrentHashMap<>();
	private static volatile String cachedModVersion;
	private static volatile boolean initialized;

	private MadokuJSONManager() { }

	public static void initialize() {
		JSONTypeManager.initialize();
		JSONFormatManager.initialize();
		initialized = true;
	}

	public static void reset() {
		SETTINGS_CACHE.clear();
		JSONFormatManager.reset();
		JSONTypeManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static Path getGlobalRootDirectory() { return FabricLoader.getInstance().getConfigDir().resolve(GLOBAL_ROOT_FOLDER_NAME); }

	public static Path getOrCreateGlobalRootDirectory() {
		Path root = getGlobalRootDirectory();
		try { Files.createDirectories(root); } catch (IOException exception) { throw new IllegalStateException("Failed to create global API root directory: " + root, exception); }
		return root;
	}

	public static Path getWorldRootDirectory(MinecraftServer server) {
		if (server == null) throw new IllegalArgumentException("Server must not be null.");
		return server.getWorldPath(LevelResource.ROOT).resolve(MadokuCraft.MOD_ID);
	}

	public static Path getOrCreateGlobalSystemDirectory(String systemName) {
		Path directory = getGlobalRootDirectory().resolve(requireRelativePath(systemName, "system directory")).normalize();
		ensureInside(getGlobalRootDirectory(), directory, "System directory");
		try { Files.createDirectories(directory); } catch (IOException exception) { throw new IllegalStateException("Failed to create global system directory: " + directory, exception); }
		return directory;
	}

	public static Path getOrCreateWorldSystemDirectory(MinecraftServer server, String systemName) {
		Path root = getWorldRootDirectory(server).toAbsolutePath().normalize();
		Path directory = root.resolve(requireRelativePath(systemName, "world system directory")).normalize();
		ensureInside(root, directory, "World system directory");
		try { Files.createDirectories(directory); } catch (IOException exception) { throw new IllegalStateException("Failed to create world system directory: " + directory, exception); }
		return directory;
	}

	public static JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName) { return loadWorldData(server, folderName, jsonName, new JsonObject()); }

	public static synchronized JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject defaults) {
		Path file = resolveWorldFile(server, folderName, jsonName, true);
		JsonObject safeDefaults = defaults == null ? new JsonObject() : defaults.deepCopy();
		try {
			JSONFormatManager.ManagedDocument source = JSONFormatManager.readManagedDocument(file);
			JsonObject data = source.data();
			if (!source.hasSettings() || !Files.isRegularFile(file) || data.isEmpty()) data = safeDefaults.deepCopy();
			JSONFormatManager.writeManagedDocument(file, data, source.settings(), JSONTypeManager.STATIC_DATA);
			cacheSettings(file, source.settings());
			return data.deepCopy();
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load world data file {}", file, exception);
			return safeDefaults.deepCopy();
		}
	}

	public static void saveWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject data) {
		Path file = resolveWorldFile(server, folderName, jsonName, false);
		JsonObject settings = SETTINGS_CACHE.get(file);
		JsonObject snapshot = data == null ? new JsonObject() : data.deepCopy();
		JsonObject capturedSettings = settings == null ? new JsonObject() : settings.deepCopy();
		DataSaveCoordinatorManager.submit(
			"json-" + file.getFileName(),
			file,
			() -> JSONFormatManager.writeManagedDocument(file, snapshot, capturedSettings, JSONTypeManager.STATIC_DATA)
		);
	}

	public static synchronized long getAutoSaveIntervalTicks(MinecraftServer server, String folderName, String jsonName) {
		Path file = resolveWorldFile(server, folderName, jsonName, false);
		JsonObject settings = SETTINGS_CACHE.get(file);
		if (settings == null) {
			try { settings = JSONFormatManager.readManagedDocument(file).settings(); cacheSettings(file, settings); }
			catch (IOException | RuntimeException exception) { LOGGER.error("Failed to read world data settings {}", file, exception); return minutesToTicks(DEFAULT_AUTO_SAVE_MINUTES); }
		}
		return minutesToTicks(readLong(settings, JSONTypeManager.FIELD_AUTOSAVE, DEFAULT_AUTO_SAVE_MINUTES));
	}

	public static synchronized void deleteWorldData(MinecraftServer server, String folderName, String jsonName) {
		Path file = resolveWorldFile(server, folderName, jsonName, false);
		try { Files.deleteIfExists(file); } catch (IOException exception) { LOGGER.error("Failed to delete world data file {}", file, exception); }
		SETTINGS_CACHE.remove(file);
	}

	public static synchronized void clearRuntimeState() { SETTINGS_CACHE.clear(); }

	public static String getCurrentModVersion() {
		String cached = cachedModVersion;
		if (cached != null && !cached.isBlank()) return cached;
		cachedModVersion = FabricLoader.getInstance().getModContainer(MadokuCraft.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		return cachedModVersion;
	}

	/** Converts vanilla JSON registry paths to Minecraft's underscore-based form while preserving custom registry paths. */
	public static String normalizeRegistryIdentifierForLookup(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return "";
		}
		int separator = normalized.indexOf(':');
		if (separator < 0) {
			return "minecraft:" + normalized.replace('-', '_');
		}
		String namespace = normalized.substring(0, separator);
		String path = normalized.substring(separator + 1);
		return namespace + ":" + ("minecraft".equals(namespace) ? path.replace('-', '_') : path);
	}

	/** Converts a registry identifier to the hyphen-based form used by Madoku JSON files. */
	public static String normalizeRegistryIdentifierForJson(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return "";
		}
		int separator = normalized.indexOf(':');
		if (separator < 0) {
			return "minecraft:" + normalized.replace('_', '-');
		}
		return normalized.substring(0, separator + 1)
			+ normalized.substring(separator + 1).replace('_', '-');
	}

	static void cacheSettings(Path file, JsonObject settings) { if (file != null && settings != null) SETTINGS_CACHE.put(file, settings.deepCopy()); }

	private static Path resolveWorldFile(MinecraftServer server, String folderName, String jsonName, boolean createDirectory) {
		Path root = getWorldRootDirectory(server).toAbsolutePath().normalize();
		String folder = requireRelativePath(folderName, "world data folder");
		Path directory = root.resolve(folder).normalize();
		ensureInside(root, directory, "World data folder");
		if (createDirectory) try { Files.createDirectories(directory); } catch (IOException exception) { throw new IllegalStateException("Failed to create world data directory: " + directory, exception); }
		String name = jsonName == null ? "" : jsonName.trim();
		if (name.isBlank()) throw new IllegalArgumentException("JSON file name must not be blank.");
		if (!name.toLowerCase().endsWith(".json")) name += ".json";
		Path file = directory.resolve(name).normalize();
		ensureInside(directory, file, "JSON file");
		return file;
	}

	private static String requireRelativePath(String value, String label) {
		if (value == null || value.trim().isBlank()) throw new IllegalArgumentException(label + " must not be blank.");
		Path path = Path.of(value.trim());
		if (path.isAbsolute()) throw new IllegalArgumentException(label + " must be relative.");
		return value.trim();
	}

	private static void ensureInside(Path root, Path child, String label) {
		if (!child.startsWith(root.toAbsolutePath().normalize())) throw new IllegalArgumentException(label + " must remain inside its root.");
	}

	private static long readLong(JsonObject object, String key, long fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsLong() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static long minutesToTicks(long minutes) {
		long safe = Math.max(1L, minutes);
		try { return Math.multiplyExact(safe, MadokuTimeManager.SECONDS_PER_MINUTE * MadokuTimeManager.TICKS_PER_SECOND); }
		catch (ArithmeticException exception) { return DEFAULT_AUTO_SAVE_MINUTES * MadokuTimeManager.SECONDS_PER_MINUTE * MadokuTimeManager.TICKS_PER_SECOND; }
	}
}
