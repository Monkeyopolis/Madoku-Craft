package madoku.craft.core.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import madoku.craft.core.scheduler.MadokuSchedulerManager;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class MadokuChunkManager {
	private static final Map<String, Map<Long, ChunkStatusEntry>> CHUNK_STATUSES_BY_LEVEL = new HashMap<>();
	private static final Map<String, Map<Long, Long>> CHUNK_LIFECYCLE_GENERATIONS_BY_LEVEL = new HashMap<>();
	private static final List<ChunkLifecycleListener> CHUNK_LIFECYCLE_LISTENERS = new CopyOnWriteArrayList<>();
	private static final Queue<PendingChunkEvent> PENDING_CHUNK_EVENTS = new ConcurrentLinkedQueue<>();
	private static final int MAX_PENDING_CHUNK_EVENTS_PER_TICK = 256;

	private MadokuChunkManager() {
	}

	public interface ChunkLifecycleListener {
		default String profilerName() {
			String className = getClass().getName();
			int packageSeparator = className.lastIndexOf('.');
			String simpleName = packageSeparator >= 0 ? className.substring(packageSeparator + 1) : className;
			int anonymousClassSeparator = simpleName.indexOf('$');
			return anonymousClassSeparator > 0 ? simpleName.substring(0, anonymousClassSeparator) : simpleName;
		}

		void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ);

		void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ);
	}

	public interface ChunkProcessor {
		/**
		 * World applicability is evaluated once per dimension for each processor snapshot. Implementations
		 * should keep this result stable for the lifetime of the dimension, or toggle their processor to
		 * invalidate the snapshot when applicability changes.
		 */
		default boolean acceptsWorld(ServerLevel level) {
			return true;
		}

		/** Optional position-level filter for hot random-tick dispatch. */
		default boolean acceptsRandomPosition(ServerLevel level, BlockPos position) {
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
		CHUNK_LIFECYCLE_GENERATIONS_BY_LEVEL.clear();
		PENDING_CHUNK_EVENTS.clear();
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
		CHUNK_LIFECYCLE_GENERATIONS_BY_LEVEL.clear();
		ChunkLifecycleManager.loadPersistedData(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		ChunkLifecycleManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (int processed = 0; processed < MAX_PENDING_CHUNK_EVENTS_PER_TICK; processed++) {
			PendingChunkEvent event = PENDING_CHUNK_EVENTS.poll();
			if (event == null) {
				return;
			}
			if (!isCurrentLifecycleEvent(event)) {
				continue;
			}
			if (event.loaded()) {
				if (!isChunkAccessible(event.level(), event.chunkX(), event.chunkZ())) {
					continue;
				}
				notifyChunkLoaded(event.level(), event.chunkX(), event.chunkZ());
			} else {
				notifyChunkUnloaded(event.level(), event.chunkX(), event.chunkZ());
			}
		}
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
		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}
		String levelId = levelId(level);
		long packedChunk = packChunk(chunkX, chunkZ);
		long currentTick = level.getGameTime();
		ChunkStatusEntry cached = getChunkStatusEntry(levelId, packedChunk);
		if (cached != null && cached.lastLiveProbeTick == currentTick) {
			return cached.status.isOrAfter(FullChunkStatus.BLOCK_TICKING);
		}
		boolean blockTicking = level.getChunkSource().isPositionTicking(packedChunk);
		updateLiveChunkStatus(levelId, packedChunk, currentTick, blockTicking ? FullChunkStatus.BLOCK_TICKING : FullChunkStatus.FULL);
		return blockTicking;
	}

	public static boolean isChunkEntityTicking(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null || !isChunkAccessible(level, chunkX, chunkZ)) {
			return false;
		}
		String levelId = levelId(level);
		long packedChunk = packChunk(chunkX, chunkZ);
		long currentTick = level.getGameTime();
		ChunkStatusEntry cached = getChunkStatusEntry(levelId, packedChunk);
		if (cached != null && cached.lastLiveProbeTick == currentTick) {
			return cached.status.isOrAfter(FullChunkStatus.ENTITY_TICKING);
		}
		boolean entityTicking = level.areEntitiesActuallyLoadedAndTicking(ChunkPos.unpack(packedChunk));
		boolean blockTicking = entityTicking || level.getChunkSource().isPositionTicking(packedChunk);
		updateLiveChunkStatus(
			levelId,
			packedChunk,
			currentTick,
			entityTicking
				? FullChunkStatus.ENTITY_TICKING
				: blockTicking ? FullChunkStatus.BLOCK_TICKING : FullChunkStatus.FULL
		);
		return entityTicking;
	}

	public static String normalizeLevelId(ServerLevel level) {
		return levelId(level);
	}

	static void putChunkStatus(String levelId, long packedChunk, FullChunkStatus status) {
		if (levelId == null || levelId.isBlank() || status == null) {
			return;
		}
		CHUNK_STATUSES_BY_LEVEL.computeIfAbsent(levelId, ignored -> new HashMap<>())
			.put(packedChunk, new ChunkStatusEntry(status, Long.MIN_VALUE));
	}

	static void removeChunk(String levelId, long packedChunk) {
		if (levelId == null || levelId.isBlank()) {
			return;
		}
		Map<Long, ChunkStatusEntry> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId);
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

	static void enqueueChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		if (level != null) {
			PENDING_CHUNK_EVENTS.add(new PendingChunkEvent(
				level,
				chunkX,
				chunkZ,
				true,
				nextLifecycleGeneration(level, chunkX, chunkZ)
			));
		}
	}

	static void notifyChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkUnloaded(level, chunkX, chunkZ);
		}
	}

	static void enqueueChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		if (level != null) {
			PENDING_CHUNK_EVENTS.add(new PendingChunkEvent(
				level,
				chunkX,
				chunkZ,
				false,
				nextLifecycleGeneration(level, chunkX, chunkZ)
			));
		}
	}

	private static long nextLifecycleGeneration(ServerLevel level, int chunkX, int chunkZ) {
		String levelId = levelId(level);
		long packedChunk = packChunk(chunkX, chunkZ);
		Map<Long, Long> generations = CHUNK_LIFECYCLE_GENERATIONS_BY_LEVEL.computeIfAbsent(
			levelId,
			ignored -> new HashMap<>()
		);
		long next = generations.getOrDefault(packedChunk, 0L) + 1L;
		generations.put(packedChunk, next);
		return next;
	}

	private static boolean isCurrentLifecycleEvent(PendingChunkEvent event) {
		if (event == null || event.level() == null) {
			return false;
		}
		Map<Long, Long> generations = CHUNK_LIFECYCLE_GENERATIONS_BY_LEVEL.get(levelId(event.level()));
		return generations != null
			&& Long.valueOf(event.generation()).equals(generations.get(packChunk(event.chunkX(), event.chunkZ())));
	}

	static String levelId(ServerLevel level) {
		return level == null ? "" : MadokuSchedulerManager.normalizeLevelIdentifier(level.dimension().toString());
	}

	static long packChunk(int chunkX, int chunkZ) {
		return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
	}

	private static ChunkStatusEntry getChunkStatusEntry(String levelId, long packedChunk) {
		Map<Long, ChunkStatusEntry> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId);
		return chunks == null ? null : chunks.get(packedChunk);
	}

	private static void updateLiveChunkStatus(String levelId, long packedChunk, long liveProbeTick, FullChunkStatus status) {
		Map<Long, ChunkStatusEntry> chunks = CHUNK_STATUSES_BY_LEVEL.computeIfAbsent(levelId, ignored -> new HashMap<>());
		ChunkStatusEntry previous = chunks.get(packedChunk);
		if (previous == null) {
			chunks.put(packedChunk, new ChunkStatusEntry(status, liveProbeTick));
		} else {
			previous.status = status;
			previous.lastLiveProbeTick = liveProbeTick;
		}
	}

	private static FullChunkStatus getStoredChunkStatus(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) {
			return null;
		}
		ChunkStatusEntry entry = getChunkStatusEntry(levelId(level), packChunk(chunkX, chunkZ));
		return entry == null ? null : entry.status;
	}

	private static final class ChunkStatusEntry {
		private FullChunkStatus status;
		private long lastLiveProbeTick;

		private ChunkStatusEntry(FullChunkStatus status, long lastLiveProbeTick) {
			this.status = status;
			this.lastLiveProbeTick = lastLiveProbeTick;
		}
	}

	private record PendingChunkEvent(ServerLevel level, int chunkX, int chunkZ, boolean loaded, long generation) { }
}
