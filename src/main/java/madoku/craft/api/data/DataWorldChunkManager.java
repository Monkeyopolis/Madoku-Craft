package madoku.craft.api.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.JSONTypeManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Runtime group for indexed per-dimension NBT world data. */
public final class DataWorldChunkManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataWorldChunkManager.class);
	private static final String DATA_CONFIG_FOLDER = MadokuAPIManager.API_FOLDER_NAME + "/madoku-data/madoku-data-world";
	private static final String DATA_CONFIG_FILE = "madoku-data";
	private static final String FIELD_AUTO_SAVE = "auto-save";
	private static final String FIELD_VERSION = "version";
	private static final String FIELD_CHUNKS = "chunks";
	private static final int DATA_VERSION = 1;
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;

	private static final Map<Dimension, Map<Long, JsonObject>> CHUNK_DATA = new EnumMap<>(Dimension.class);
	private static final Set<Dimension> DIRTY_DIMENSIONS = EnumSet.noneOf(Dimension.class);
	private static volatile long autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
	private static volatile boolean initialized;
	private static volatile boolean eventHandlersRegistered;

	private DataWorldChunkManager() { }

	public static void initialize() {
		loadConfig();
		for (Dimension dimension : Dimension.values()) {
			CHUNK_DATA.put(dimension, new LinkedHashMap<>());
		}
		DIRTY_DIMENSIONS.clear();
		if (!eventHandlersRegistered) {
			ServerChunkEvents.CHUNK_LOAD.register(DataWorldChunkManager::onChunkLoad);
			ServerChunkEvents.CHUNK_UNLOAD.register(DataWorldChunkManager::onChunkUnload);
			eventHandlersRegistered = true;
		}
		initialized = true;
	}

	public static void reset() {
		for (Map<Long, JsonObject> chunks : CHUNK_DATA.values()) chunks.clear();
		DIRTY_DIMENSIONS.clear();
		initialized = false;
	}

	public static boolean isInitialized() { return initialized; }

	public static long getAutoSaveMinutes() { return autoSaveMinutes; }

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
		for (Dimension dimension : Dimension.values()) loadDimension(server, dimension);
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
		if (server != null) savePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) { }

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || DIRTY_DIMENSIONS.isEmpty()) return;
		Set<Dimension> dimensions = EnumSet.copyOf(DIRTY_DIMENSIONS);
		DIRTY_DIMENSIONS.removeAll(dimensions);
		for (Dimension dimension : dimensions) {
			Map<Long, JsonObject> source = CHUNK_DATA.get(dimension);
			if (source == null) continue;
			Map<Long, JsonObject> snapshot = new LinkedHashMap<>();
			for (Map.Entry<Long, JsonObject> entry : source.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null) snapshot.put(entry.getKey(), entry.getValue().deepCopy());
			}
			DataSaveCoordinatorManager.recordDirtyChunks(snapshot.size());
			Path file = resolveDataFile(server, dimension);
			DataSaveCoordinatorManager.submit("chunk-data-" + dimension.fileName, file, () -> writeDimensionSnapshot(file, snapshot));
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
		safeData.addProperty("chunk-x", chunkX);
		safeData.addProperty("chunk-z", chunkZ);
		CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).put(pack(chunkX, chunkZ), safeData);
		DIRTY_DIMENSIONS.add(dimension);
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
		JsonObject chunk = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).get(pack(key.chunkX, key.chunkZ));
		JsonObject systems = getObject(chunk, "systems");
		JsonElement value = systems.get(normalizedSystemId);
		return value != null && value.isJsonObject() ? value.getAsJsonObject().deepCopy() : new JsonObject();
	}

	public static void setChunkSystemData(ChunkDataKey key, String systemId, JsonObject data) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return;
		Map<Long, JsonObject> chunks = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
		long packedChunk = pack(key.chunkX, key.chunkZ);
		JsonObject chunk = chunks.computeIfAbsent(packedChunk, ignored -> createChunkDefaults(key.chunkX, key.chunkZ));
		JsonObject systems = getObject(chunk, "systems");
		systems.add(normalizedSystemId, data == null ? new JsonObject() : data.deepCopy());
		chunk.add("systems", systems);
		DIRTY_DIMENSIONS.add(dimension);
	}

	public static void removeChunkSystemData(ChunkDataKey key, String systemId) {
		Dimension dimension = dimensionOf(key);
		String normalizedSystemId = normalizeSystemId(systemId);
		if (dimension == null || normalizedSystemId.isBlank()) return;
		Map<Long, JsonObject> chunks = CHUNK_DATA.get(dimension);
		if (chunks == null) return;
		JsonObject chunk = chunks.get(pack(key.chunkX, key.chunkZ));
		JsonObject systems = getObject(chunk, "systems");
		if (systems.remove(normalizedSystemId) != null) {
			if (systems.isEmpty()) chunk.remove("systems");
			else chunk.add("systems", systems);
			DIRTY_DIMENSIONS.add(dimension);
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
				JsonObject systems = getObject(entry.getValue(), "systems");
				JsonElement value = systems.get(normalizedSystemId);
				if (value != null && value.isJsonObject()) result.put(new ChunkDataKey(dimension.levelId, unpackX(entry.getKey()), unpackZ(entry.getKey())), value.getAsJsonObject().deepCopy());
			}
		}
		return result;
	}

	private static void loadDimension(MinecraftServer server, Dimension dimension) {
		Path file = resolveDataFile(server, dimension);
		if (!Files.isRegularFile(file)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			CompoundTag chunks = root.getCompoundOrEmpty(FIELD_CHUNKS);
			for (String key : chunks.keySet()) {
				try {
					long packed = Long.parseLong(key);
					CompoundTag chunk = chunks.getCompoundOrEmpty(key);
					JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, chunk);
					if (json.isJsonObject()) CHUNK_DATA.get(dimension).put(packed, json.getAsJsonObject());
				} catch (RuntimeException ignored) { }
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku NBT data file {}", file, exception);
		}
	}

	private static void writeDimensionSnapshot(Path file, Map<Long, JsonObject> snapshot) throws IOException {
		CompoundTag root = new CompoundTag();
		root.putInt(FIELD_VERSION, DATA_VERSION);
		CompoundTag chunks = new CompoundTag();
		for (Map.Entry<Long, JsonObject> entry : snapshot.entrySet()) {
			Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, entry.getValue());
			if (tag instanceof CompoundTag compound) chunks.put(Long.toString(entry.getKey()), compound);
		}
		root.put(FIELD_CHUNKS, chunks);
		Path parent = file.toAbsolutePath().normalize().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "madoku-data-", ".tmp");
		try {
			NbtIo.writeCompressed(root, temporary);
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level != null && chunk != null) putLoadedChunk(level, chunk.getPos(), FullChunkStatus.FULL);
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) { }

	private static void putLoadedChunk(ServerLevel level, ChunkPos position, FullChunkStatus status) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null || position == null) return;
		Map<Long, JsonObject> chunks = CHUNK_DATA.get(dimension);
		long packedChunk = position.pack();
		if (chunks.containsKey(packedChunk)) return;
		chunks.put(packedChunk, createChunkDefaults(position.x(), position.z()));
		DIRTY_DIMENSIONS.add(dimension);
	}

	private static Path resolveDataFile(MinecraftServer server, Dimension dimension) {
		return MadokuJSONManager.getWorldRootDirectory(server)
			.resolve(DATA_CONFIG_FOLDER)
			.resolve(dimension.fileName + ".nbt");
	}

	private static void loadConfig() {
		JsonObject defaults = JSONFormatManager.object().solo(FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES).build();
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(DATA_CONFIG_FOLDER);
			Path file = directory.resolve(DATA_CONFIG_FILE + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, defaults, JSONTypeManager.STATIC_CONFIG, null);
			autoSaveMinutes = Math.max(1L, getLong(normalized, FIELD_AUTO_SAVE, DEFAULT_AUTO_SAVE_MINUTES));
			JSONFormatManager.writeManagedFile(file, JSONFormatManager.object().solo(FIELD_AUTO_SAVE, autoSaveMinutes).build(), defaults, JSONTypeManager.STATIC_CONFIG, null);
		} catch (IOException | RuntimeException exception) {
			autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
			LOGGER.error("Failed to load Madoku data config; using defaults.", exception);
		}
	}

	private static void clearChunkData() {
		for (Dimension dimension : Dimension.values()) CHUNK_DATA.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>()).clear();
		DIRTY_DIMENSIONS.clear();
	}

	private static JsonObject createChunkDefaults(int chunkX, int chunkZ) {
		return JSONFormatManager.object().put("chunk-x", chunkX).put("chunk-z", chunkZ).put("status", FullChunkStatus.FULL.name().toLowerCase(java.util.Locale.ROOT)).build();
	}

	private static JsonObject getObject(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject().deepCopy() : new JsonObject();
	}

	private static String normalizeSystemId(String systemId) { return systemId == null ? "" : systemId.trim().toLowerCase(java.util.Locale.ROOT); }

	private static long pack(int chunkX, int chunkZ) { return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL); }
	private static int unpackX(long packed) { return (int) (packed >> 32); }
	private static int unpackZ(long packed) { return (int) packed; }

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

	public record ChunkDataKey(String dimensionId, int chunkX, int chunkZ) {
		public ChunkDataKey { dimensionId = dimensionId == null ? "" : dimensionId.trim().toLowerCase(java.util.Locale.ROOT); }
	}

	private enum Dimension {
		OVERWORLD("minecraft:overworld", "madoku-data-overworld"),
		NETHER("minecraft:the_nether", "madoku-data-nether"),
		END("minecraft:the_end", "madoku-data-end");

		private final String levelId;
		private final String fileName;

		Dimension(String levelId, String fileName) {
			this.levelId = levelId;
			this.fileName = fileName;
		}
	}
}
