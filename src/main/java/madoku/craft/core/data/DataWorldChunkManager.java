package madoku.craft.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.chunk.MadokuChunkManager;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.JSONTypeManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.time.MadokuTimeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Runtime group for indexed per-dimension NBT world data. */
public final class DataWorldChunkManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataWorldChunkManager.class);
	private static final String DATA_CONFIG_FOLDER = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-data";
	private static final String WORLD_DATA_FOLDER = MadokuCoreManager.CORE_FOLDER_NAME + "/madoku-data/madoku-data-world";
	private static final String DATA_CONFIG_FILE = "madoku-data";
	private static final String FIELD_AUTO_SAVE = "auto-save";
	private static final String FIELD_VERSION = "version";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_DATA = "data";
	private static final int DATA_VERSION = 1;
	private static final long DEFAULT_AUTO_SAVE_MINUTES = 5L;

	private static final Map<Dimension, Long2ObjectOpenHashMap<JsonObject>> CHUNK_DATA = new EnumMap<>(Dimension.class);
	private static final Set<ChunkDataKey> DIRTY_CHUNKS = new LinkedHashSet<>();
	private static final Set<ChunkDataKey> LOADED_CHUNKS = new LinkedHashSet<>();
	private static volatile long autoSaveMinutes = DEFAULT_AUTO_SAVE_MINUTES;
	private static volatile MinecraftServer currentServer;
	private static volatile boolean initialized;
	private static final MadokuChunkManager.ChunkLifecycleListener CHUNK_LISTENER = new MadokuChunkManager.ChunkLifecycleListener() {
		@Override
		public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
			// Chunk state is loaded lazily by the subsystem that requests it.
		}

		@Override
		public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
			releaseChunk(level, chunkX, chunkZ);
		}
	};

	private DataWorldChunkManager() { }

	public static void initialize() {
		MadokuChunkManager.registerChunkLifecycleListener(CHUNK_LISTENER);
		loadConfig();
		for (Dimension dimension : Dimension.values()) {
			CHUNK_DATA.put(dimension, new Long2ObjectOpenHashMap<>());
		}
		DIRTY_CHUNKS.clear();
		LOADED_CHUNKS.clear();
		initialized = true;
	}

	public static void reset() {
		for (Long2ObjectOpenHashMap<JsonObject> chunks : CHUNK_DATA.values()) chunks.clear();
		DIRTY_CHUNKS.clear();
		LOADED_CHUNKS.clear();
		currentServer = null;
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
		currentServer = server;
		clearChunkData();
	}

	public static void onServerStarted(MinecraftServer server) {
		currentServer = server;
		// Chunk data is created lazily by subsystem writes.
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server != null) savePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) { }

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || DIRTY_CHUNKS.isEmpty()) return;
		Set<ChunkDataKey> dirtyChunks = new LinkedHashSet<>(DIRTY_CHUNKS);
		DIRTY_CHUNKS.removeAll(dirtyChunks);
		DataSaveCoordinatorManager.recordDirtyChunks(dirtyChunks.size());
		for (ChunkDataKey chunkKey : dirtyChunks) {
			Dimension dimension = dimensionOf(chunkKey);
			if (dimension == null) continue;
			Long2ObjectOpenHashMap<JsonObject> source = CHUNK_DATA.get(dimension);
			JsonObject data = source == null ? null : source.get(pack(chunkKey.chunkX, chunkKey.chunkZ));
			JsonObject snapshot = hasSubsystemData(data) ? data.deepCopy() : null;
			Path file = resolveChunkDataFile(server, dimension, chunkKey.chunkX, chunkKey.chunkZ);
			queueChunkSnapshot(dimension, chunkKey, file, snapshot);
		}
	}

	static void releaseChunk(ServerLevel level, int chunkX, int chunkZ) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return;
		ChunkDataKey key = new ChunkDataKey(dimension.levelId, chunkX, chunkZ);
		long packedChunk = pack(chunkX, chunkZ);
		Long2ObjectOpenHashMap<JsonObject> chunks = CHUNK_DATA.get(dimension);
		boolean dirty = DIRTY_CHUNKS.remove(key);
		JsonObject data = chunks == null ? null : chunks.remove(packedChunk);
		LOADED_CHUNKS.remove(key);
		if (!dirty) return;

		MinecraftServer server = level.getServer();
		if (server == null) {
			if (chunks != null && data != null) chunks.put(packedChunk, data);
			DIRTY_CHUNKS.add(key);
			LOADED_CHUNKS.add(key);
			return;
		}
		Path file = resolveChunkDataFile(server, dimension, chunkX, chunkZ);
		DataSaveCoordinatorManager.recordDirtyChunks(1);
		queueChunkSnapshot(dimension, key, file, hasSubsystemData(data) ? data.deepCopy() : null);
	}

	public static JsonObject getChunkData(ServerLevel level, int chunkX, int chunkZ) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return new JsonObject();
		loadChunkIfNeeded(level == null ? currentServer : level.getServer(), dimension, chunkX, chunkZ);
		JsonObject data = CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).get(pack(chunkX, chunkZ));
		return data == null ? createChunkDefaults(chunkX, chunkZ) : data.deepCopy();
	}

	public static void setChunkData(ServerLevel level, int chunkX, int chunkZ, JsonObject data) {
		Dimension dimension = dimensionOf(level);
		if (dimension == null) return;
		loadChunkIfNeeded(level == null ? currentServer : level.getServer(), dimension, chunkX, chunkZ);
		JsonObject safeData = data == null ? new JsonObject() : data.deepCopy();
		safeData.addProperty("chunk-x", chunkX);
		safeData.addProperty("chunk-z", chunkZ);
		CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).put(pack(chunkX, chunkZ), safeData);
		markDirty(dimension, chunkX, chunkZ);
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
		markDirty(dimension, key.chunkX, key.chunkZ);
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
		if (systems.remove(normalizedSystemId) != null) {
			if (systems.isEmpty()) chunk.remove("systems");
			else chunk.add("systems", systems);
			if (systems.isEmpty()) chunks.remove(pack(key.chunkX, key.chunkZ));
			markDirty(dimension, key.chunkX, key.chunkZ);
		}
	}

	public static Map<ChunkDataKey, JsonObject> getAllChunkSystemData(String systemId) {
		String normalizedSystemId = normalizeSystemId(systemId);
		Map<ChunkDataKey, JsonObject> result = new LinkedHashMap<>();
		if (normalizedSystemId.isBlank()) return result;
		for (Dimension dimension : Dimension.values()) {
			loadChunkFiles(currentServer, dimension);
			Long2ObjectOpenHashMap<JsonObject> chunks = CHUNK_DATA.get(dimension);
			if (chunks == null) continue;
			for (var entry : chunks.long2ObjectEntrySet()) {
				JsonObject systems = getObjectReference(entry.getValue(), "systems");
				JsonElement value = systems.get(normalizedSystemId);
				if (value != null && value.isJsonObject()) result.put(new ChunkDataKey(dimension.levelId, unpackX(entry.getLongKey()), unpackZ(entry.getLongKey())), value.getAsJsonObject().deepCopy());
			}
		}
		return result;
	}

	private static void writeChunkSnapshot(Path file, ChunkDataKey chunkKey, JsonObject snapshot) throws IOException {
		if (!hasSubsystemData(snapshot)) {
			Files.deleteIfExists(file);
			return;
		}
		CompoundTag root = new CompoundTag();
		root.putInt(FIELD_VERSION, DATA_VERSION);
		root.putInt(FIELD_CHUNK_X, chunkKey.chunkX);
		root.putInt(FIELD_CHUNK_Z, chunkKey.chunkZ);
		Tag data = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, snapshot == null ? new JsonObject() : snapshot);
		if (data instanceof CompoundTag compound) root.put(FIELD_DATA, compound);
		Path parent = file.toAbsolutePath().normalize().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, "madoku-chunk-data-", ".tmp");
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

	private static void queueChunkSnapshot(Dimension dimension, ChunkDataKey chunkKey, Path file, JsonObject snapshot) {
		DataSaveCoordinatorManager.submit(
			"chunk-data-" + dimension.fileName + "-" + chunkKey.chunkX + "-" + chunkKey.chunkZ,
			file,
			() -> writeChunkSnapshot(file, chunkKey, snapshot)
		);
	}

	private static Path resolveChunkDataFile(MinecraftServer server, Dimension dimension, int chunkX, int chunkZ) {
		return MadokuJSONManager.getWorldRootDirectory(server)
			.resolve(WORLD_DATA_FOLDER)
			.resolve(dimension.fileName)
			.resolve(chunkX + "_" + chunkZ + ".nbt");
	}

	private static void loadChunkIfNeeded(MinecraftServer server, Dimension dimension, int chunkX, int chunkZ) {
		ChunkDataKey key = new ChunkDataKey(dimension.levelId, chunkX, chunkZ);
		if (server == null || !LOADED_CHUNKS.add(key)) return;
		Path file = resolveChunkDataFile(server, dimension, chunkX, chunkZ);
		if (!Files.isRegularFile(file)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, root.getCompoundOrEmpty(FIELD_DATA));
			if (json.isJsonObject()) {
				CHUNK_DATA.get(dimension).put(pack(chunkX, chunkZ), json.getAsJsonObject());
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku chunk data file {}", file, exception);
		}
	}

	private static void loadChunkFiles(MinecraftServer server, Dimension dimension) {
		if (server == null) return;
		Path directory = MadokuJSONManager.getWorldRootDirectory(server)
			.resolve(WORLD_DATA_FOLDER)
			.resolve(dimension.fileName);
		if (!Files.isDirectory(directory)) return;
		try (var files = Files.list(directory)) {
			files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".nbt"))
				.forEach(path -> {
					String name = path.getFileName().toString();
					String[] parts = name.substring(0, name.length() - 4).split("_", 2);
					if (parts.length != 2) return;
					try {
						loadChunkIfNeeded(server, dimension, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
					} catch (NumberFormatException ignored) { }
				});
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to list Madoku chunk data directory {}", directory, exception);
		}
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
		for (Dimension dimension : Dimension.values()) CHUNK_DATA.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>()).clear();
		DIRTY_CHUNKS.clear();
		LOADED_CHUNKS.clear();
	}

	private static boolean hasSubsystemData(JsonObject data) {
		if (data == null) return false;
		JsonElement systems = data.get("systems");
		return systems != null && systems.isJsonObject() && !systems.getAsJsonObject().isEmpty();
	}

	private static JsonObject createChunkDefaults(int chunkX, int chunkZ) {
		return JSONFormatManager.object().put("chunk-x", chunkX).put("chunk-z", chunkZ).put("status", FullChunkStatus.FULL.name().toLowerCase(java.util.Locale.ROOT)).build();
	}

	private static JsonObject getObjectReference(JsonObject object, String key) {
		JsonElement element = object == null ? null : object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static void markDirty(Dimension dimension, int chunkX, int chunkZ) {
		DIRTY_CHUNKS.add(new ChunkDataKey(dimension.levelId, chunkX, chunkZ));
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
