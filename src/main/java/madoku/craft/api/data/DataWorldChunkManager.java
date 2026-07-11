package madoku.craft.api.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.JSONTypeManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime group for indexing and storing per-world chunk JSON data. */
public final class DataWorldChunkManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataWorldChunkManager.class);
	private static final String DATA_CONFIG_FOLDER = MadokuAPIManager.API_FOLDER_NAME + "/madoku-data";
	private static final String DATA_CONFIG_FILE = "madoku-data";
	private static final String DATA_INDEX_FOLDER = MadokuAPIManager.API_FOLDER_NAME + "/madoku-data";
	private static final String FIELD_AUTO_SAVE = "auto-save";
	private static final String FIELD_CHUNKS = "chunks";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_FILE = "file";
	private static final String FIELD_STATUS = "status";
	private static final String FIELD_SYSTEMS = "systems";
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;

	private static final Map<Dimension, Map<Long, JsonObject>> CHUNK_DATA = new EnumMap<>(Dimension.class);
	private static volatile long autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile boolean initialized;
	private static volatile boolean eventHandlersRegistered;

	private DataWorldChunkManager() {
	}

	public static void initialize() {
		loadConfig();
		for (Dimension dimension : Dimension.values()) {
			CHUNK_DATA.put(dimension, new LinkedHashMap<>());
		}
		lastAutosaveBucket = Long.MIN_VALUE;
		if (!eventHandlersRegistered) {
			ServerChunkEvents.CHUNK_LOAD.register(DataWorldChunkManager::onChunkLoad);
			ServerChunkEvents.CHUNK_UNLOAD.register(DataWorldChunkManager::onChunkUnload);
			eventHandlersRegistered = true;
		}
		initialized = true;
	}

	public static void reset() {
		for (Map<Long, JsonObject> chunks : CHUNK_DATA.values()) {
			chunks.clear();
		}
		lastAutosaveBucket = Long.MIN_VALUE;
		initialized = false;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static long getAutoSaveMinutes() {
		return autoSaveMinutes;
	}

	public static long getAutoSaveIntervalTicks() {
		try {
			return Math.multiplyExact(Math.max(1L, autoSaveMinutes), MadokuTimeManager.SECONDS_PER_MINUTE * MadokuTimeManager.TICKS_PER_SECOND);
		} catch (ArithmeticException exception) {
			return DEFAULT_AUTO_SAVE_MINUTES * MadokuTimeManager.SECONDS_PER_MINUTE * MadokuTimeManager.TICKS_PER_SECOND;
		}
	}

	public static String dimensionId(ServerLevel level) {
		Dimension dimension = dimensionOf(level);
		return dimension == null ? "" : dimension.levelId;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		clearChunkData();
		for (Dimension dimension : Dimension.values()) {
			JsonObject index = MadokuJSONManager.loadWorldData(server, DATA_INDEX_FOLDER, dimension.indexFile, createIndexDefaults());
			JsonArray entries = getArray(index, FIELD_CHUNKS);
			for (JsonElement element : entries) {
				if (element == null || !element.isJsonObject()) continue;
				JsonObject entry = element.getAsJsonObject();
				int chunkX = getInt(entry, FIELD_CHUNK_X, 0);
				int chunkZ = getInt(entry, FIELD_CHUNK_Z, 0);
				String file = getString(entry, FIELD_FILE, chunkFileName(chunkX, chunkZ));
				JsonObject chunk = MadokuJSONManager.loadWorldData(server, dimension.folderName, file, createChunkDefaults(chunkX, chunkZ));
				CHUNK_DATA.get(dimension).put(pack(chunkX, chunkZ), chunk);
			}
		}
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), getAutoSaveIntervalTicks());
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) return;
		for (ServerLevel level : server.getAllLevels()) {
			Dimension dimension = dimensionOf(level);
			if (dimension == null) continue;
			level.getChunkSource().chunkMap.forEachReadyToSendChunk((LevelChunk chunk) -> {
				if (chunk != null) putLoadedChunk(level, chunk.getPos(), FullChunkStatus.FULL);
			});
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) return;
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), getAutoSaveIntervalTicks());
		if (bucket == lastAutosaveBucket) return;
		lastAutosaveBucket = bucket;
		savePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) return;
		for (Dimension dimension : Dimension.values()) {
			Map<Long, JsonObject> chunks = CHUNK_DATA.get(dimension);
			if (chunks == null) continue;
			JSONFormatManager.ArrayBuilder indexEntries = JSONFormatManager.array();
			for (Map.Entry<Long, JsonObject> chunkEntry : chunks.entrySet()) {
				int chunkX = unpackX(chunkEntry.getKey());
				int chunkZ = unpackZ(chunkEntry.getKey());
				String file = chunkFileName(chunkX, chunkZ);
				JsonObject data = chunkEntry.getValue() == null ? createChunkDefaults(chunkX, chunkZ) : chunkEntry.getValue().deepCopy();
				data.addProperty(FIELD_CHUNK_X, chunkX);
				data.addProperty(FIELD_CHUNK_Z, chunkZ);
				MadokuJSONManager.saveWorldData(server, dimension.folderName, file, data);
				indexEntries.object(entry -> entry
					.put(FIELD_CHUNK_X, chunkX)
					.put(FIELD_CHUNK_Z, chunkZ)
					.put(FIELD_FILE, file));
			}
			MadokuJSONManager.saveWorldData(server, DATA_INDEX_FOLDER, dimension.indexFile,
				JSONFormatManager.object().put(FIELD_CHUNKS, indexEntries.build()).build());
		}
	}

	public static JsonObject getChunkData(ServerLevel level, int chunkX, int chunkZ) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return new JsonObject();
		JsonObject data = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).get(pack(chunkX, chunkZ));
		return data == null ? createChunkDefaults(chunkX, chunkZ) : data.deepCopy();
	}

	public static void setChunkData(ServerLevel level, int chunkX, int chunkZ, JsonObject data) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return;
		JsonObject safeData = data == null ? new JsonObject() : data.deepCopy();
		safeData.addProperty(FIELD_CHUNK_X, chunkX);
		safeData.addProperty(FIELD_CHUNK_Z, chunkZ);
		CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).put(pack(chunkX, chunkZ), safeData);
	}

	public static JsonObject getChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId) {
		Dimension dimension = dimensionOf(level);
		return dimension == null ? new JsonObject() : getChunkSystemData(new ChunkDataKey(dimension.levelId, chunkX, chunkZ), systemId);
	}

	public static void setChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId, JsonObject data) {
		Dimension dimension = dimensionOf(level);
		if (dimension != null) {
			setChunkSystemData(new ChunkDataKey(dimension.levelId, chunkX, chunkZ), systemId, data);
		}
	}

	public static JsonObject getChunkSystemData(ChunkDataKey key, String systemId) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return new JsonObject();
		JsonObject chunk = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).get(pack(key.chunkX, key.chunkZ));
		JsonObject systems = getObject(chunk, FIELD_SYSTEMS);
		JsonElement value = systems.get(normalizedSystemId);
		return value != null && value.isJsonObject() ? value.getAsJsonObject().deepCopy() : new JsonObject();
	}

	public static void setChunkSystemData(ChunkDataKey key, String systemId, JsonObject data) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return;
		Map<Long, JsonObject> chunks = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
		JsonObject chunk = chunks.computeIfAbsent(pack(key.chunkX, key.chunkZ), ignored -> createChunkDefaults(key.chunkX, key.chunkZ));
		JsonObject systems = getObject(chunk, FIELD_SYSTEMS);
		systems.add(normalizedSystemId, data == null ? new JsonObject() : data.deepCopy());
		chunk.add(FIELD_SYSTEMS, systems);
	}

	public static void removeChunkSystemData(ChunkDataKey key, String systemId) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return;
		Map<Long, JsonObject> chunks = CHUNK_DATA.get(dimension);
		if (chunks == null) return;
		JsonObject chunk = chunks.get(pack(key.chunkX, key.chunkZ));
		JsonObject systems = getObject(chunk, FIELD_SYSTEMS);
		if (systems.remove(normalizedSystemId) != null) {
			if (systems.isEmpty()) chunk.remove(FIELD_SYSTEMS);
			else chunk.add(FIELD_SYSTEMS, systems);
		}
	}

	public static Map<ChunkDataKey, JsonObject> getAllChunkSystemData(String systemId) {
		String normalizedSystemId = normalizeSystemId(systemId);
		Map<ChunkDataKey, JsonObject> result = new LinkedHashMap<>();
		if (normalizedSystemId.isBlank()) return result;
		for (Dimension dimension : Dimension.values()) {
			Map<Long, JsonObject> chunks = CHUNK_DATA.get(dimension);
			if (chunks == null) continue;
			for (Map.Entry<Long, JsonObject> entry : chunks.entrySet()) {
				JsonObject systems = getObject(entry.getValue(), FIELD_SYSTEMS);
				JsonElement value = systems.get(normalizedSystemId);
				if (value != null && value.isJsonObject()) {
					result.put(new ChunkDataKey(dimension.levelId, unpackX(entry.getKey()), unpackZ(entry.getKey())), value.getAsJsonObject().deepCopy());
				}
			}
		}
		return result;
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level != null && chunk != null) putLoadedChunk(level, chunk.getPos(), FullChunkStatus.FULL);
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		// Chunk data remains indexed after unload so it can be written at autosave/server stop.
	}

	private static void putLoadedChunk(ServerLevel level, ChunkPos position, FullChunkStatus status) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null || position == null) return;
		Map<Long, JsonObject> chunks = CHUNK_DATA.get(dimension);
		JsonObject data = chunks.computeIfAbsent(position.pack(), ignored -> createChunkDefaults(position.x(), position.z()));
		data.addProperty(FIELD_CHUNK_X, position.x());
		data.addProperty(FIELD_CHUNK_Z, position.z());
		data.addProperty(FIELD_STATUS, status.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static void loadConfig() {
		JsonObject defaults = JSONFormatManager.object().solo(FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES).build();
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(DATA_CONFIG_FOLDER);
			Path file = directory.resolve(DATA_CONFIG_FILE + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, defaults, JSONTypeManager.STATIC_CONFIG, null);
			autoSaveMinutes = Math.max(1L, getLong(normalized, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
			JSONFormatManager.writeManagedFile(file,
				JSONFormatManager.object().solo(FIELD_AUTO_SAVE, autoSaveMinutes).build(), defaults,
				JSONTypeManager.STATIC_CONFIG, null);
		} catch (IOException | RuntimeException exception) {
			autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
			LOGGER.error("Failed to load Madoku data config; using defaults.", exception);
		}
	}

	private static void clearChunkData() {
		for (Dimension dimension : Dimension.values()) {
			CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).clear();
		}
	}

	private static JsonObject createIndexDefaults() {
		return JSONFormatManager.object().put(FIELD_CHUNKS, new JsonArray()).build();
	}

	private static JsonObject createChunkDefaults(int chunkX, int chunkZ) {
		return JSONFormatManager.object()
			.put(FIELD_CHUNK_X, chunkX)
			.put(FIELD_CHUNK_Z, chunkZ)
			.put(FIELD_STATUS, FullChunkStatus.FULL.name().toLowerCase(java.util.Locale.ROOT))
			.build();
	}

	private static JsonArray getArray(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static JsonObject getObject(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject().deepCopy() : new JsonObject();
	}

	private static String normalizeSystemId(String systemId) {
		return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String getString(JsonObject object, String key, String fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsString() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsInt() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsLong() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	private static String chunkFileName(int chunkX, int chunkZ) {
		return "chunk-" + chunkX + "-" + chunkZ;
	}

	private static Dimension dimensionOf(ServerLevel level) {
		if (level == null) return null;
		if (Level.OVERWORLD.equals(level.dimension())) return Dimension.OVERWORLD;
		if (Level.NETHER.equals(level.dimension())) return Dimension.NETHER;
		if (Level.END.equals(level.dimension())) return Dimension.END;
		return null;
	}

	private static Dimension dimensionOf(ChunkDataKey key) {
		if (key == null) return null;
		for (Dimension dimension : Dimension.values()) {
			if (dimension.levelId.equals(key.dimensionId)) return dimension;
		}
		return null;
	}

	private static long pack(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	private static int unpackX(long packed) { return (int) (packed >> 32); }
	private static int unpackZ(long packed) { return (int) packed; }

	public record ChunkDataKey(String dimensionId, int chunkX, int chunkZ) {
		public ChunkDataKey {
			dimensionId = dimensionId == null ? "" : dimensionId.trim().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private enum Dimension {
		OVERWORLD("minecraft:overworld", "madoku-data-overworld", MadokuAPIManager.API_FOLDER_NAME + "/madoku-data/madoku-overworld"),
		NETHER("minecraft:the_nether", "madoku-data-nether", MadokuAPIManager.API_FOLDER_NAME + "/madoku-data/madoku-nether"),
		END("minecraft:the_end", "madoku-data-end", MadokuAPIManager.API_FOLDER_NAME + "/madoku-data/madoku-end");

		private final String levelId;
		private final String indexFile;
		private final String folderName;

		Dimension(String levelId, String indexFile, String folderName) {
			this.levelId = levelId;
			this.indexFile = indexFile;
			this.folderName = folderName;
		}
	}
}
