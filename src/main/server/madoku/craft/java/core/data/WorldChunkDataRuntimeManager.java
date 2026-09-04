package madoku.craft.java.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import madoku.craft.java.core.chunk.ChunkAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONTypeAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Internal runtime for indexed per-dimension chunk data backed by vanilla SavedData. */
final class WorldChunkDataRuntimeManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(WorldChunkDataRuntimeManager.class);
	private static final String DATA_CONFIG_FOLDER = "madoku-craft-core/madoku-data";
	private static final String DATA_CONFIG_FILE = "madoku-data";
	private static final String FIELD_AUTO_SAVE = "auto-save";
	private static final String FIELD_CHUNKS = "chunks";
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;

	private static final Map<Dimension, Long2ObjectOpenHashMap<JsonObject>> CHUNK_DATA = new EnumMap<>(Dimension.class);
	private static final Map<Dimension, MadokuSavedData> SAVED_DATA = new EnumMap<>(Dimension.class);
	private static final Set<ChunkDataKey> DIRTY_CHUNKS = new LinkedHashSet<>();
	private static final Set<ChunkDataKey> LOADED_CHUNKS = new LinkedHashSet<>();
	private static volatile long autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
	private static volatile MinecraftServer currentServer;
	private static volatile boolean initialized;
	private static final ChunkAPIManager.ChunkLifecycleListener CHUNK_LISTENER = new ChunkAPIManager.ChunkLifecycleListener() {
		@Override public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) { }
		@Override public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) { releaseChunk(level, chunkX, chunkZ); }
	};

	private WorldChunkDataRuntimeManager() { }

	public static void initialize() {
		ChunkAPIManager.registerChunkLifecycleListener(CHUNK_LISTENER);
		loadConfig();
		for (Dimension dimension : Dimension.values()) CHUNK_DATA.put(dimension, new Long2ObjectOpenHashMap<>());
		DIRTY_CHUNKS.clear();
		LOADED_CHUNKS.clear();
		SAVED_DATA.clear();
		initialized = true;
	}

	public static void reset() {
		for (Long2ObjectOpenHashMap<JsonObject> chunks : CHUNK_DATA.values()) chunks.clear();
		SAVED_DATA.clear();
		DIRTY_CHUNKS.clear();
		LOADED_CHUNKS.clear();
		currentServer = null;
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }
	public static long getAutoSaveMinutes() { return autoSaveMinutes; }

	public static long getAutoSaveIntervalTicks() {
		try {
			return Math.multiplyExact(Math.max(1L, autoSaveMinutes), TimeAPIManager.SECONDS_PER_MINUTE * TimeAPIManager.TICKS_PER_SECOND);
		} catch (ArithmeticException exception) {
			return DEFAULT_AUTO_SAVE_MINUTES * TimeAPIManager.SECONDS_PER_MINUTE * TimeAPIManager.TICKS_PER_SECOND;
		}
	}

	public static String dimensionId(ServerLevel level) {
		Dimension dimension = dimensionOf(level);
		return dimension == null ? "" : dimension.levelId;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		clearChunkData();
		SAVED_DATA.clear();
		for (Dimension dimension : Dimension.values()) {
			ServerLevel level = levelFor(server, dimension);
			if (level != null) SAVED_DATA.put(dimension, MadokuSavedDataManager.chunks(level));
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		for (Dimension dimension : Dimension.values()) {
			ServerLevel level = levelFor(server, dimension);
			if (level != null) SAVED_DATA.putIfAbsent(dimension, MadokuSavedDataManager.chunks(level));
		}
	}

	public static void autosavePersistedData(MinecraftServer server) { }

	public static void onServerStopping(MinecraftServer server) { }

	public static void savePersistedData(MinecraftServer server) {
		if (server != null && !DIRTY_CHUNKS.isEmpty()) {
			DataSaveCoordinatorManager.recordDirtyChunks(DIRTY_CHUNKS.size());
			DIRTY_CHUNKS.clear();
		}
	}

	static void releaseChunk(ServerLevel level, int chunkX, int chunkZ) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return;
		ChunkDataKey key = new ChunkDataKey(dimension.levelId, chunkX, chunkZ);
		DIRTY_CHUNKS.remove(key);
		Long2ObjectOpenHashMap<JsonObject> chunks = CHUNK_DATA.get(dimension);
		if (chunks != null) chunks.remove(pack(chunkX, chunkZ));
		LOADED_CHUNKS.remove(key);
	}

	public static JsonObject getChunkData(ServerLevel level, int chunkX, int chunkZ) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return new JsonObject();
		loadChunkIfNeeded(level.getServer(), dimension, chunkX, chunkZ);
		JsonObject data = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).get(pack(chunkX, chunkZ));
		return data == null ? createChunkDefaults(chunkX, chunkZ) : data.deepCopy();
	}

	public static void setChunkData(ServerLevel level, int chunkX, int chunkZ, JsonObject data) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return;
		loadChunkIfNeeded(level.getServer(), dimension, chunkX, chunkZ);
		JsonObject safeData = data == null ? new JsonObject() : data.deepCopy();
		safeData.addProperty("chunk-x", chunkX);
		safeData.addProperty("chunk-z", chunkZ);
		CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).put(pack(chunkX, chunkZ), safeData);
		persistChunk(dimension, new ChunkDataKey(dimension.levelId, chunkX, chunkZ), safeData);
	}

	public static JsonObject getChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId) {
		Dimension dimension = dimensionOf(level);
		return dimension == null ? new JsonObject() : getChunkSystemData(new ChunkDataKey(dimension.levelId, chunkX, chunkZ), systemId);
	}

	public static void setChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId, JsonObject data) {
		Dimension dimension = dimensionOf(level);
		if (dimension != null) setChunkSystemData(new ChunkDataKey(dimension.levelId, chunkX, chunkZ), systemId, data);
	}

	public static JsonObject getChunkSystemData(ChunkDataKey key, String systemId) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return new JsonObject();
		loadChunkIfNeeded(currentServer, dimension, key.chunkX, key.chunkZ);
		JsonObject chunk = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).get(pack(key.chunkX, key.chunkZ));
		JsonObject systems = getObjectReference(chunk, "systems");
		JsonElement value = systems.get(normalizedSystemId);
		return value != null && value.isJsonObject() ? value.getAsJsonObject().deepCopy() : new JsonObject();
	}

	public static void setChunkSystemData(ChunkDataKey key, String systemId, JsonObject data) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return;
		loadChunkIfNeeded(currentServer, dimension, key.chunkX, key.chunkZ);
		Long2ObjectOpenHashMap<JsonObject> chunks = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>());
		long packedChunk = pack(key.chunkX, key.chunkZ);
		JsonObject chunk = chunks.computeIfAbsent(packedChunk, ignored -> createChunkDefaults(key.chunkX, key.chunkZ));
		JsonObject systems = getObjectReference(chunk, "systems");
		systems.add(normalizedSystemId, data == null ? new JsonObject() : data.deepCopy());
		chunk.add("systems", systems);
		persistChunk(dimension, key, chunk);
	}

	public static void removeChunkSystemData(ChunkDataKey key, String systemId) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return;
		loadChunkIfNeeded(currentServer, dimension, key.chunkX, key.chunkZ);
		Long2ObjectOpenHashMap<JsonObject> chunks = CHUNK_DATA.get(dimension);
		if (chunks == null) return;
		JsonObject chunk = chunks.get(pack(key.chunkX, key.chunkZ));
		JsonObject systems = getObjectReference(chunk, "systems");
		if (systems.remove(normalizedSystemId) == null) return;
		if (systems.isEmpty()) {
			chunks.remove(pack(key.chunkX, key.chunkZ));
			persistChunk(dimension, key, null);
		} else {
			chunk.add("systems", systems);
			persistChunk(dimension, key, chunk);
		}
	}

	public static Map<ChunkDataKey, JsonObject> getAllChunkSystemData(String systemId) {
		String normalizedSystemId = normalizeSystemId(systemId);
		Map<ChunkDataKey, JsonObject> result = new LinkedHashMap<>();
		if (normalizedSystemId.isBlank()) return result;
		for (Dimension dimension : Dimension.values()) {
			loadAllChunks(dimension);
			Long2ObjectOpenHashMap<JsonObject> chunks = CHUNK_DATA.get(dimension);
			if (chunks == null) continue;
			for (var entry : chunks.long2ObjectEntrySet()) {
				JsonObject systems = getObjectReference(entry.getValue(), "systems");
				JsonElement value = systems.get(normalizedSystemId);
				if (value != null && value.isJsonObject()) result.put(
					new ChunkDataKey(dimension.levelId, unpackX(entry.getLongKey()), unpackZ(entry.getLongKey())),
					value.getAsJsonObject().deepCopy());
			}
		}
		return result;
	}

	private static void persistChunk(Dimension dimension, ChunkDataKey key, JsonObject data) {
		MadokuSavedData savedData = savedDataFor(dimension);
		if (savedData == null) return;
		CompoundTag root = savedData.copyData();
		CompoundTag chunks = root.getCompoundOrEmpty(FIELD_CHUNKS).copy();
		String chunkKey = chunkKey(key.chunkX, key.chunkZ);
		if (data == null || !hasSubsystemData(data)) chunks.remove(chunkKey);
		else chunks.put(chunkKey, MadokuSavedDataManager.toNbt(data));
		root.put(FIELD_CHUNKS, chunks);
		savedData.replaceData(root);
		DIRTY_CHUNKS.add(key);
	}

	private static void loadChunkIfNeeded(MinecraftServer server, Dimension dimension, int chunkX, int chunkZ) {
		ChunkDataKey key = new ChunkDataKey(dimension.levelId, chunkX, chunkZ);
		if (server == null || !LOADED_CHUNKS.add(key)) return;
		MadokuSavedData savedData = savedDataFor(dimension);
		if (savedData == null) return;
		Tag tag = savedData.copyData().getCompoundOrEmpty(FIELD_CHUNKS).get(chunkKey(chunkX, chunkZ));
		if (tag instanceof CompoundTag compound) {
			CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>())
				.put(pack(chunkX, chunkZ), MadokuSavedDataManager.toJson(compound));
		}
	}

	private static void loadAllChunks(Dimension dimension) {
		MadokuSavedData savedData = savedDataFor(dimension);
		if (savedData == null) return;
		CompoundTag chunks = savedData.copyData().getCompoundOrEmpty(FIELD_CHUNKS);
		for (Map.Entry<String, Tag> entry : chunks.entrySet()) {
			int[] coordinates = parseChunkKey(entry.getKey());
			if (coordinates == null || !(entry.getValue() instanceof CompoundTag compound)) continue;
			long packed = pack(coordinates[0], coordinates[1]);
			CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).put(packed, MadokuSavedDataManager.toJson(compound));
			LOADED_CHUNKS.add(new ChunkDataKey(dimension.levelId, coordinates[0], coordinates[1]));
		}
	}

	private static MadokuSavedData savedDataFor(Dimension dimension) {
		MadokuSavedData savedData = SAVED_DATA.get(dimension);
		if (savedData != null) return savedData;
		ServerLevel level = currentServer == null ? null : levelFor(currentServer, dimension);
		if (level == null) return null;
		savedData = MadokuSavedDataManager.chunks(level);
		SAVED_DATA.put(dimension, savedData);
		return savedData;
	}

	private static void loadConfig() {
		JsonObject defaults = JSONFormatAPIManager.object().solo(FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES).build();
		try {
			Path directory = JSONAPIManager.getOrCreateGlobalSystemDirectory(DATA_CONFIG_FOLDER);
			Path file = directory.resolve(DATA_CONFIG_FILE + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, defaults, JSONTypeAPIManager.STATIC_CONFIG, null);
			autoSaveMinutes = Math.max(1L, getLong(normalized, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
		} catch (IOException | RuntimeException exception) {
			autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
			LOGGER.error("Failed to load Madoku data config; using defaults.", exception);
		}
	}

	private static void clearChunkData() {
		for (Dimension dimension : Dimension.values()) CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).clear();
		DIRTY_CHUNKS.clear();
		LOADED_CHUNKS.clear();
	}

	private static boolean hasSubsystemData(JsonObject data) {
		JsonElement systems = data == null ? null : data.get("systems");
		return systems != null && systems.isJsonObject() && !systems.getAsJsonObject().isEmpty();
	}

	private static JsonObject createChunkDefaults(int chunkX, int chunkZ) {
		return JSONFormatAPIManager.object().put("chunk-x", chunkX).put("chunk-z", chunkZ)
			.put("status", FullChunkStatus.FULL.name().toLowerCase(java.util.Locale.ROOT)).build();
	}

	private static JsonObject getObjectReference(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static String chunkKey(int chunkX, int chunkZ) { return chunkX + "," + chunkZ; }
	private static int[] parseChunkKey(String key) {
		if (key == null) return null;
		String[] parts = key.split(",", 2);
		if (parts.length != 2) return null;
		try { return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) }; }
		catch (NumberFormatException ignored) { return null; }
	}
	private static long pack(int chunkX, int chunkZ) { return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL); }
	private static int unpackX(long packed) { return (int) (packed >> 32); }
	private static int unpackZ(long packed) { return (int) packed; }
	private static String normalizeSystemId(String systemId) { return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT); }
	private static long getLong(JsonObject object, String key, long fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsLong() : fallback; }
		catch (RuntimeException exception) { return fallback; }
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
		for (Dimension dimension : Dimension.values()) if (dimension.levelId.equals(key.dimensionId)) return dimension;
		return null;
	}
	private static ServerLevel levelFor(MinecraftServer server, Dimension dimension) {
		if (server == null) return null;
		return switch (dimension) {
			case OVERWORLD -> server.overworld();
			case NETHER -> server.getLevel(Level.NETHER);
			case END -> server.getLevel(Level.END);
		};
	}

	public record ChunkDataKey(String dimensionId, int chunkX, int chunkZ) {
		public ChunkDataKey { dimensionId = dimensionId == null ? "" : dimensionId.trim().toLowerCase(java.util.Locale.ROOT); }
	}

	private enum Dimension {
		OVERWORLD("minecraft:overworld"), NETHER("minecraft:the_nether"), END("minecraft:the_end");
		private final String levelId;
		Dimension(String levelId) { this.levelId = levelId; }
	}
}
