package madoku.craft.java.core.json;

import com.google.gson.JsonObject;
import madoku.craft.java.MadokuCraft;
import madoku.craft.java.core.data.MadokuSavedData;
import madoku.craft.java.core.data.MadokuSavedDataManager;
import net.minecraft.nbt.CompoundTag;
import madoku.craft.java.core.time.TimeAPIManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime API subsystem orchestrating JSON formatting and type management. */
public final class JSONAPIManager {
	private static final String GLOBAL_ROOT_FOLDER_NAME = MadokuCraft.MOD_ID;
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;
	private static final Map<String, JsonObject> SETTINGS_CACHE = new ConcurrentHashMap<>();
	private static volatile String cachedModVersion;
	private static volatile boolean initialized;

	private JSONAPIManager() { }

	public static void initialize() {
		JSONTypeAPIManager.initialize();
		JSONFormatAPIManager.initialize();
		initialized = true;
	}

	public static void reset() {
		SETTINGS_CACHE.clear();
		JSONFormatAPIManager.reset();
		JSONTypeAPIManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static Path getGlobalRootDirectory() { return FabricLoader.getInstance().getConfigDir().resolve(GLOBAL_ROOT_FOLDER_NAME); }

	public static Path getOrCreateGlobalRootDirectory() {
		Path root = getGlobalRootDirectory();
		try { Files.createDirectories(root); } catch (IOException exception) { throw new IllegalStateException("Failed to create global API root directory: " + root, exception); }
		return root;
	}

	public static Path getOrCreateGlobalSystemDirectory(String systemName) {
		Path directory = getGlobalRootDirectory().resolve(requireRelativePath(systemName, "system directory")).normalize();
		ensureInside(getGlobalRootDirectory(), directory, "System directory");
		try { Files.createDirectories(directory); } catch (IOException exception) { throw new IllegalStateException("Failed to create global system directory: " + directory, exception); }
		return directory;
	}

	public static JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName) { return loadWorldData(server, folderName, jsonName, new JsonObject()); }

	public static synchronized JsonObject loadWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject defaults) {
		String key = worldDataKey(folderName, jsonName);
		JsonObject safeDefaults = defaults == null ? new JsonObject() : defaults.deepCopy();
		MadokuSavedData savedData = MadokuSavedDataManager.jsonWorld(server);
		CompoundTag root = savedData.copyData();
		CompoundTag entries = root.getCompoundOrEmpty("entries");
		CompoundTag entry = entries.getCompoundOrEmpty(key);
		JsonObject data = entry.contains("data") ? MadokuSavedDataManager.toJson(entry.getCompoundOrEmpty("data")) : null;
		JsonObject settings = entry.contains("settings") ? MadokuSavedDataManager.toJson(entry.getCompoundOrEmpty("settings")) : new JsonObject();
		if (data == null || data.isEmpty()) data = safeDefaults.deepCopy();
		cacheSettings(key, settings);
		putJsonWorldEntry(savedData, key, data, settings);
		return data.deepCopy();
	}

	public static synchronized void saveWorldData(MinecraftServer server, String folderName, String jsonName, JsonObject data) {
		if (server == null) return;
		String key = worldDataKey(folderName, jsonName);
		JsonObject settings = SETTINGS_CACHE.getOrDefault(key, new JsonObject());
		putJsonWorldEntry(MadokuSavedDataManager.jsonWorld(server), key,
			data == null ? new JsonObject() : data, settings);
	}

	public static synchronized long getAutoSaveIntervalTicks(MinecraftServer server, String folderName, String jsonName) {
		String key = worldDataKey(folderName, jsonName);
		JsonObject settings = SETTINGS_CACHE.get(key);
		if (settings == null) {
			CompoundTag entry = MadokuSavedDataManager.jsonWorld(server).copyData().getCompoundOrEmpty("entries").getCompoundOrEmpty(key);
			settings = entry.contains("settings") ? MadokuSavedDataManager.toJson(entry.getCompoundOrEmpty("settings")) : new JsonObject();
			cacheSettings(key, settings);
		}
		return minutesToTicks(readLong(settings, JSONTypeAPIManager.FIELD_AUTOSAVE, DEFAULT_AUTO_SAVE_MINUTES));
	}

	public static synchronized void deleteWorldData(MinecraftServer server, String folderName, String jsonName) {
		String key = worldDataKey(folderName, jsonName);
		MadokuSavedData savedData = MadokuSavedDataManager.jsonWorld(server);
		CompoundTag root = savedData.copyData();
		CompoundTag entries = root.getCompoundOrEmpty("entries").copy();
		entries.remove(key);
		root.put("entries", entries);
		savedData.replaceData(root);
		SETTINGS_CACHE.remove(key);
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

	static void cacheSettings(String key, JsonObject settings) { if (key != null && settings != null) SETTINGS_CACHE.put(key, settings.deepCopy()); }

	private static void putJsonWorldEntry(MadokuSavedData savedData, String key, JsonObject data, JsonObject settings) {
		if (savedData == null) return;
		CompoundTag root = savedData.copyData();
		CompoundTag entries = root.getCompoundOrEmpty("entries").copy();
		CompoundTag entry = new CompoundTag();
		entry.put("data", MadokuSavedDataManager.toNbt(data));
		entry.put("settings", MadokuSavedDataManager.toNbt(settings));
		entries.put(key, entry);
		root.put("entries", entries);
		savedData.replaceData(root);
	}

	private static String worldDataKey(String folderName, String jsonName) {
		String name = jsonName == null ? "" : jsonName.trim();
		if (!name.toLowerCase(Locale.ROOT).endsWith(".json")) name += ".json";
		return requireRelativePath(folderName, "world data folder") + "/" + name;
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
		try { return Math.multiplyExact(safe, TimeAPIManager.SECONDS_PER_MINUTE * TimeAPIManager.TICKS_PER_SECOND); }
		catch (ArithmeticException exception) { return DEFAULT_AUTO_SAVE_MINUTES * TimeAPIManager.SECONDS_PER_MINUTE * TimeAPIManager.TICKS_PER_SECOND; }
	}
}
