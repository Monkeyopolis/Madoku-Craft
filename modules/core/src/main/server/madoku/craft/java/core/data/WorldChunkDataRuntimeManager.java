package madoku.craft.java.core.data;

import com.google.gson.JsonObject;
import madoku.craft.java.core.chunk.ChunkAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONTypeAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Internal runtime for chunk-owned data stored in native chunk serialization through Fabric attachments. */
final class WorldChunkDataRuntimeManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(WorldChunkDataRuntimeManager.class);
	private static final String DATA_CONFIG_FOLDER = "madoku-craft-core/madoku-data";
	private static final String DATA_CONFIG_FILE = "madoku-data";
	private static final String FIELD_AUTO_SAVE = "auto-save";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_STATUS = "status";
	private static final String FIELD_SYSTEMS = "systems";
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;
	private static final AttachmentType<CompoundTag> CHUNK_DATA_ATTACHMENT = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath("madoku-craft", "chunk-data"), CompoundTag.CODEC
	);

	private static final Set<ChunkDataKey> LOADED_CHUNKS = new LinkedHashSet<>();
	private static volatile long autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
	private static volatile MinecraftServer currentServer;
	private static volatile boolean initialized;
	private static final ChunkAPIManager.ChunkLifecycleListener CHUNK_LISTENER = new ChunkAPIManager.ChunkLifecycleListener() {
		@Override public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
			LOADED_CHUNKS.add(new ChunkDataKey(dimensionId(level), chunkX, chunkZ));
		}

		@Override public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
			LOADED_CHUNKS.remove(new ChunkDataKey(dimensionId(level), chunkX, chunkZ));
		}
	};

	private WorldChunkDataRuntimeManager() { }

	public static void initialize() {
		ChunkAPIManager.registerChunkLifecycleListener(CHUNK_LISTENER);
		loadConfig();
		LOADED_CHUNKS.clear();
		currentServer = null;
		initialized = true;
	}

	public static void reset() {
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
		return ChunkAPIManager.normalizeLevelId(level);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) return;
		currentServer = server;
		LOADED_CHUNKS.clear();
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server != null) currentServer = server;
	}

	public static void autosavePersistedData(MinecraftServer server) {
		// Chunk attachments are marked unsaved by AttachmentTarget#setAttached. Vanilla owns the write.
	}

	public static void onServerStopping(MinecraftServer server) { }

	public static void savePersistedData(MinecraftServer server) { }

	public static JsonObject getChunkData(ServerLevel level, int chunkX, int chunkZ) {
		LevelChunk chunk = loadedChunk(level, chunkX, chunkZ);
		CompoundTag root = attachment(chunk);
		return root == null ? createChunkDefaults(chunkX, chunkZ) : MadokuSavedDataManager.toJson(root);
	}

	public static void setChunkData(ServerLevel level, int chunkX, int chunkZ, JsonObject data) {
		LevelChunk chunk = loadedChunk(level, chunkX, chunkZ);
		if (chunk == null) return;
		JsonObject safeData = data == null ? new JsonObject() : data.deepCopy();
		safeData.addProperty(FIELD_CHUNK_X, chunkX);
		safeData.addProperty(FIELD_CHUNK_Z, chunkZ);
		setAttachment(chunk, MadokuSavedDataManager.toNbt(safeData));
	}

	public static JsonObject getChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId) {
		if (level == null) return new JsonObject();
		return getChunkSystemData(new ChunkDataKey(dimensionId(level), chunkX, chunkZ), systemId);
	}

	public static void setChunkSystemData(ServerLevel level, int chunkX, int chunkZ, String systemId, JsonObject data) {
		if (level != null) setChunkSystemData(new ChunkDataKey(dimensionId(level), chunkX, chunkZ), systemId, data);
	}

	public static JsonObject getChunkSystemData(ChunkDataKey key, String systemId) {
		ServerLevel level = levelFor(key);
		LevelChunk chunk = level == null ? null : loadedChunk(level, key.chunkX, key.chunkZ);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (chunk == null || normalizedSystemId.isBlank()) return new JsonObject();
		CompoundTag root = attachment(chunk);
		CompoundTag systems = root == null ? null : root.getCompoundOrEmpty(FIELD_SYSTEMS);
		Tag value = systems == null ? null : systems.get(normalizedSystemId);
		return value instanceof CompoundTag compound ? MadokuSavedDataManager.toJson(compound) : new JsonObject();
	}

	public static void setChunkSystemData(ChunkDataKey key, String systemId, JsonObject data) {
		ServerLevel level = levelFor(key);
		LevelChunk chunk = level == null ? null : loadedChunk(level, key.chunkX, key.chunkZ);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (chunk == null || normalizedSystemId.isBlank()) return;
		CompoundTag root = attachment(chunk);
		if (root == null) root = MadokuSavedDataManager.toNbt(createChunkDefaults(key.chunkX, key.chunkZ));
		CompoundTag updated = root.copy();
		CompoundTag systems = updated.getCompoundOrEmpty(FIELD_SYSTEMS).copy();
		systems.put(normalizedSystemId, MadokuSavedDataManager.toNbt(data == null ? new JsonObject() : data));
		updated.put(FIELD_SYSTEMS, systems);
		setAttachment(chunk, updated);
	}

	public static void removeChunkSystemData(ChunkDataKey key, String systemId) {
		ServerLevel level = levelFor(key);
		LevelChunk chunk = level == null ? null : loadedChunk(level, key.chunkX, key.chunkZ);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (chunk == null || normalizedSystemId.isBlank()) return;
		CompoundTag root = attachment(chunk);
		if (root == null) return;
		CompoundTag updated = root.copy();
		CompoundTag systems = updated.getCompoundOrEmpty(FIELD_SYSTEMS).copy();
		if (systems.remove(normalizedSystemId) == null) return;
		if (systems.isEmpty()) updated.remove(FIELD_SYSTEMS);
		else updated.put(FIELD_SYSTEMS, systems);
		if (!hasSubsystemData(updated)) ((AttachmentTarget) chunk).removeAttached(CHUNK_DATA_ATTACHMENT);
		else setAttachment(chunk, updated);
	}

	/** Returns data from currently loaded chunks; unloaded chunks are intentionally not force-loaded. */
	public static Map<ChunkDataKey, JsonObject> getAllChunkSystemData(String systemId) {
		String normalizedSystemId = normalizeSystemId(systemId);
		Map<ChunkDataKey, JsonObject> result = new LinkedHashMap<>();
		if (normalizedSystemId.isBlank()) return result;
		for (ChunkDataKey key : Set.copyOf(LOADED_CHUNKS)) {
			JsonObject data = getChunkSystemData(key, normalizedSystemId);
			if (!data.isEmpty()) result.put(key, data);
		}
		return result;
	}

	private static LevelChunk loadedChunk(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) return null;
		LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
		if (chunk != null) LOADED_CHUNKS.add(new ChunkDataKey(dimensionId(level), chunkX, chunkZ));
		return chunk;
	}

	private static CompoundTag attachment(ChunkAccess chunk) {
		return chunk == null ? null : ((AttachmentTarget) chunk).getAttached(CHUNK_DATA_ATTACHMENT);
	}

	private static void setAttachment(ChunkAccess chunk, CompoundTag value) {
		((AttachmentTarget) chunk).setAttached(CHUNK_DATA_ATTACHMENT, value);
	}

	private static ServerLevel levelFor(ChunkDataKey key) {
		if (key == null || currentServer == null || key.dimensionId.isBlank()) return null;
		for (ServerLevel level : currentServer.getAllLevels()) {
			if (key.dimensionId.equals(dimensionId(level))) return level;
		}
		return null;
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

	private static boolean hasSubsystemData(CompoundTag root) {
		CompoundTag systems = root == null ? null : root.getCompoundOrEmpty(FIELD_SYSTEMS);
		return systems != null && !systems.isEmpty();
	}

	private static JsonObject createChunkDefaults(int chunkX, int chunkZ) {
		return JSONFormatAPIManager.object().put(FIELD_CHUNK_X, chunkX).put(FIELD_CHUNK_Z, chunkZ)
			.put(FIELD_STATUS, FullChunkStatus.FULL.name().toLowerCase(java.util.Locale.ROOT)).build();
	}

	private static String normalizeSystemId(String systemId) { return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT); }
	private static long getLong(JsonObject object, String key, long fallback) {
		try { return object != null && object.has(key) ? object.get(key).getAsLong() : fallback; }
		catch (RuntimeException exception) { return fallback; }
	}

	record ChunkDataKey(String dimensionId, int chunkX, int chunkZ) {
		ChunkDataKey { dimensionId = dimensionId == null ? "" : dimensionId.trim().toLowerCase(java.util.Locale.ROOT); }
	}
}
