package madoku.craft.api.chunk;

import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MadokuChunkManager {
	private static final Map<String, Map<Long, FullChunkStatus>> CHUNK_STATUSES_BY_LEVEL = new LinkedHashMap<>();
	private static final List<ChunkLifecycleListener> CHUNK_LIFECYCLE_LISTENERS = new CopyOnWriteArrayList<>();

	private MadokuChunkManager() {
	}

	public interface ChunkLifecycleListener {
		void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ);

		void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ);
	}

	public interface ChunkProcessor {
		default boolean acceptsWorld(ServerLevel level) {
			return true;
		}

		void handleRandomPosition(ServerLevel level, BlockPos position, RandomSource random);
	}

	public static void initialize() {
		ChunkConfigManager.initialize();
		ChunkLifecycleManager.initialize();
	}

	public static void reset() {
		CHUNK_STATUSES_BY_LEVEL.clear();
		ChunkLifecycleManager.reset();
		ChunkProcessorManager.reset();
	}

	public static void registerChunkLifecycleListener(ChunkLifecycleListener listener) {
		if (listener == null || CHUNK_LIFECYCLE_LISTENERS.contains(listener)) {
			return;
		}
		CHUNK_LIFECYCLE_LISTENERS.add(listener);
	}

	public static void registerChunkProcessor(String processorId, ChunkProcessor processor) {
		ChunkProcessorManager.registerChunkProcessor(processorId, processor);
	}

	public static void setChunkProcessorActive(String processorId, boolean active) {
		ChunkProcessorManager.setChunkProcessorActive(processorId, active);
	}

	public static void dispatchRandomPosition(ServerLevel level, BlockPos position, RandomSource random) {
		ChunkProcessorManager.dispatchRandomPosition(level, position, random);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		CHUNK_STATUSES_BY_LEVEL.clear();
		ChunkLifecycleManager.loadPersistedData(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		ChunkLifecycleManager.onServerStarted(server);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		ChunkLifecycleManager.autosavePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		ChunkLifecycleManager.onServerStopping(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		ChunkLifecycleManager.savePersistedData(server);
	}

	public static boolean isChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		return isChunkAccessible(level, chunkX, chunkZ);
	}

	public static boolean isChunkAccessible(ServerLevel level, int chunkX, int chunkZ) {
		FullChunkStatus status = getStoredChunkStatus(level, chunkX, chunkZ);
		if (status != null) {
			return status.isOrAfter(FullChunkStatus.FULL);
		}
		if (level == null) {
			return false;
		}
		boolean liveLoaded = level.getChunkSource().hasChunk(chunkX, chunkZ);
		if (liveLoaded) {
			putChunkStatus(levelId(level), packChunk(chunkX, chunkZ), FullChunkStatus.FULL);
		}
		return liveLoaded;
	}

	public static boolean isChunkBlockTicking(ServerLevel level, int chunkX, int chunkZ) {
		FullChunkStatus status = getStoredChunkStatus(level, chunkX, chunkZ);
		if (status != null && status.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
			return true;
		}
		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}
		boolean ticking = level.getChunkSource().isPositionTicking(packChunk(chunkX, chunkZ));
		if (ticking) {
			putChunkStatus(levelId(level), packChunk(chunkX, chunkZ), FullChunkStatus.BLOCK_TICKING);
		}
		return ticking;
	}

	public static boolean isChunkEntityTicking(ServerLevel level, int chunkX, int chunkZ) {
		FullChunkStatus status = getStoredChunkStatus(level, chunkX, chunkZ);
		if (status != null && status.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
			return true;
		}
		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}
		boolean ticking = level.areEntitiesActuallyLoadedAndTicking(ChunkPos.unpack(packChunk(chunkX, chunkZ)));
		if (ticking) {
			putChunkStatus(levelId(level), packChunk(chunkX, chunkZ), FullChunkStatus.ENTITY_TICKING);
		}
		return ticking;
	}

	public static String normalizeLevelId(ServerLevel level) {
		return levelId(level);
	}

	static void putChunkStatus(String levelId, long packedChunk, FullChunkStatus status) {
		if (levelId == null || levelId.isBlank() || status == null) {
			return;
		}
		CHUNK_STATUSES_BY_LEVEL.computeIfAbsent(levelId, ignored -> new LinkedHashMap<>()).put(packedChunk, status);
	}

	static void removeChunk(String levelId, long packedChunk) {
		if (levelId == null || levelId.isBlank()) {
			return;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId);
		if (chunks == null || chunks.remove(packedChunk) == null) {
			return;
		}
		if (chunks.isEmpty()) {
			CHUNK_STATUSES_BY_LEVEL.remove(levelId);
		}
	}

	static void notifyChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkLoaded(level, chunkX, chunkZ);
		}
	}

	static void notifyChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkUnloaded(level, chunkX, chunkZ);
		}
	}

	static String levelId(ServerLevel level) {
		return level == null ? "" : MadokuSchedulerManager.normalizeLevelIdentifier(level.dimension().toString());
	}

	static long packChunk(int chunkX, int chunkZ) {
		return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
	}

	private static FullChunkStatus getStoredChunkStatus(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) {
			return null;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId(level));
		return chunks == null ? null : chunks.get(packChunk(chunkX, chunkZ));
	}
}
