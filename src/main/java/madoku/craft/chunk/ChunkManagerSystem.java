package madoku.craft.chunk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChunkManagerSystem {
	private static final String DATA_FOLDER_NAME = "madoku-craft-chunks";
	private static final String DATA_FILE_NAME = "madoku-chunks";
	private static final String CHUNK_SCHEDULER_OWNER_ID = "madoku_chunks";
	private static final String DIRTY_DISCOVERY_SCHEDULER_OWNER_ID = "madoku_chunks_dirty_discovery";
	private static final String PROCESSOR_ROUND_ROBIN_SCHEDULER_OWNER_PREFIX = "madoku_chunks_processor_round_robin_";
	private static final String TASK_TYPE_CHUNK_REFRESH = "chunk_refresh";
	private static final long CHUNK_REFRESH_MIN_INTERVAL_TICKS = 4L * 60L * 20L;
	private static final long CHUNK_REFRESH_MAX_INTERVAL_TICKS = 8L * 60L * 20L;
	private static final long DIRTY_DISCOVERY_MIN_INTERVAL_TICKS = 1L;
	private static final long DIRTY_DISCOVERY_MAX_INTERVAL_TICKS = 20L;
	private static final long PROCESSOR_ROUND_ROBIN_MIN_INTERVAL_TICKS = 1L;
	private static final long PROCESSOR_ROUND_ROBIN_MAX_INTERVAL_TICKS = 20L;
	private static final long DIRTY_REQUEUE_COOLDOWN_TICKS = 20L;
	private static final int DIRTY_DISCOVERY_STEPS_PER_REFRESH = 1;
	private static final int DISCOVERY_STEPS_PER_REFRESH = 1;
	private static final int CHUNK_COLUMN_COUNT = 16 * 16;
	private static final String FIELD_LEVELS = "levels";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_CHUNKS = "chunks";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_STATUS = "status";

	private static final Map<String, Map<Long, FullChunkStatus>> CHUNK_STATUSES_BY_LEVEL = new LinkedHashMap<>();
	private static final List<ChunkLifecycleListener> CHUNK_LIFECYCLE_LISTENERS = new CopyOnWriteArrayList<>();
	private static final Map<String, ChunkProcessorRuntime> CHUNK_PROCESSORS = new LinkedHashMap<>();
	private static final Set<String> ACTIVE_CHUNK_PROCESSOR_IDS = new LinkedHashSet<>();
	private static final List<ProcessorChunkKey> DISCOVERY_LOADED_CHUNKS = new ArrayList<>();
	private static final Set<ProcessorChunkKey> DISCOVERY_LOADED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Deque<ProcessorChunkKey> DIRTY_DISCOVERY_CHUNKS = new ArrayDeque<>();
	private static final Set<ProcessorChunkKey> DIRTY_DISCOVERY_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Map<ProcessorChunkKey, Long> DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS = new LinkedHashMap<>();
	private static final Map<ProcessorChunkKey, CachedChunkColumns> DISCOVERY_COLUMNS_CACHE = new LinkedHashMap<>();
	private static final ChunkDiscoverySnapshot REUSABLE_DISCOVERY_SNAPSHOT = ChunkDiscoverySnapshot.reusable(CHUNK_COLUMN_COUNT);
	private static final ThreadLocal<Integer> INTERNAL_PROCESSOR_MUTATION_DEPTH = ThreadLocal.withInitial(() -> 0);

	private static volatile String chunkSchedulerId = "";
	private static volatile boolean refreshTaskScheduled = false;
	private static volatile boolean serverStopping = false;
	private static volatile boolean dirty = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile int discoveryChunkScanCursor = 0;
	private static volatile boolean discoveryChunksSeeded = false;

	private ChunkManagerSystem() {
	}

	public interface ChunkLifecycleListener {
		void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ);

		void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ);
	}

	public interface ChunkProcessor {
		default boolean acceptsWorld(ServerLevel level) {
			return true;
		}

		default boolean requiresMotionColumns() {
			return true;
		}

		default boolean requiresSurfaceColumns() {
			return false;
		}

		// Snapshot data is callback-scoped and backed by reusable storage.
		// Processors must not retain references to snapshot/column instances outside this call.
		void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ, ChunkDiscoverySnapshot snapshot);

		void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ);
	}

	public static final class ChunkDiscoverySnapshot {
		private String levelId;
		private int chunkX;
		private int chunkZ;
		private final List<ColumnSample> motionColumns;
		private final List<ColumnSample> surfaceColumns;
		private boolean hasMotionColumns;
		private boolean hasSurfaceColumns;

		private ChunkDiscoverySnapshot(int capacity) {
			int safeCapacity = Math.max(1, capacity);
			List<ColumnSample> motion = new ArrayList<>(safeCapacity);
			List<ColumnSample> surface = new ArrayList<>(safeCapacity);
			for (int i = 0; i < safeCapacity; i++) {
				motion.add(new ColumnSample());
				surface.add(new ColumnSample());
			}
			this.motionColumns = motion;
			this.surfaceColumns = surface;
			this.levelId = "";
		}

		private static ChunkDiscoverySnapshot reusable(int capacity) {
			return new ChunkDiscoverySnapshot(capacity);
		}

		private void begin(String levelId, int chunkX, int chunkZ, boolean needsMotionColumns, boolean needsSurfaceColumns) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.hasMotionColumns = needsMotionColumns;
			this.hasSurfaceColumns = needsSurfaceColumns;
		}

		public String levelId() {
			return levelId;
		}

		public int chunkX() {
			return chunkX;
		}

		public int chunkZ() {
			return chunkZ;
		}

		public List<ColumnSample> motionColumns() {
			if (!hasMotionColumns) {
				return List.of();
			}
			return motionColumns;
		}

		public boolean hasMotionColumns() {
			return hasMotionColumns;
		}

		public boolean hasSurfaceColumns() {
			return hasSurfaceColumns;
		}

		public List<ColumnSample> surfaceColumns() {
			if (!hasSurfaceColumns) {
				return List.of();
			}
			return surfaceColumns;
		}

		private ColumnSample motionColumnAt(int index) {
			return motionColumns.get(index);
		}

		private ColumnSample surfaceColumnAt(int index) {
			return surfaceColumns.get(index);
		}
	}

	public static final class ColumnSample {
		private int worldX;
		private int worldZ;
		private final int[] yByDepth = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		private final long[] posByDepth = new long[3];
		private final BlockState[] stateByDepth = new BlockState[3];

		private void reset(int worldX, int worldZ) {
			this.worldX = worldX;
			this.worldZ = worldZ;
			Arrays.fill(yByDepth, Integer.MIN_VALUE);
			Arrays.fill(posByDepth, 0L);
			Arrays.fill(stateByDepth, null);
		}

		private void setDepth(int depth, int y, long packedPos, BlockState state) {
			if (depth < 0 || depth >= yByDepth.length) {
				return;
			}
			yByDepth[depth] = y;
			posByDepth[depth] = packedPos;
			stateByDepth[depth] = state;
		}

		private void copyFrom(ColumnSample source) {
			if (source == null) {
				reset(0, 0);
				return;
			}
			this.worldX = source.worldX;
			this.worldZ = source.worldZ;
			System.arraycopy(source.yByDepth, 0, this.yByDepth, 0, this.yByDepth.length);
			System.arraycopy(source.posByDepth, 0, this.posByDepth, 0, this.posByDepth.length);
			System.arraycopy(source.stateByDepth, 0, this.stateByDepth, 0, this.stateByDepth.length);
		}

		public int worldX() {
			return worldX;
		}

		public int worldZ() {
			return worldZ;
		}

		public boolean hasDepth(int depth) {
			return depth >= 0 && depth < yByDepth.length && yByDepth[depth] != Integer.MIN_VALUE;
		}

		public int yAtDepth(int depth) {
			return hasDepth(depth) ? yByDepth[depth] : Integer.MIN_VALUE;
		}

		public long posAtDepth(int depth) {
			return hasDepth(depth) ? posByDepth[depth] : 0L;
		}

		public BlockState stateAtDepth(int depth) {
			return hasDepth(depth) ? stateByDepth[depth] : null;
		}
	}

	public static void initialize() {
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_CHUNK_REFRESH, ChunkManagerSystem::runChunkRefreshTask);
		ServerChunkEvents.CHUNK_LOAD.register(ChunkManagerSystem::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(ChunkManagerSystem::onChunkUnload);
	}

	public static void reset() {
		CHUNK_STATUSES_BY_LEVEL.clear();
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_CHUNKS.clear();
		DIRTY_DISCOVERY_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.clear();
		DISCOVERY_COLUMNS_CACHE.clear();
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime != null) {
				runtime.resetState();
			}
		}
		chunkSchedulerId = "";
		refreshTaskScheduled = false;
		serverStopping = false;
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
		SchedulerManagerSystem.clearAdaptiveDelayState(CHUNK_SCHEDULER_OWNER_ID);
		SchedulerManagerSystem.clearAdaptiveDelayState(DIRTY_DISCOVERY_SCHEDULER_OWNER_ID);
		clearProcessorRoundRobinAdaptiveState();
	}

	public static void registerChunkLifecycleListener(ChunkLifecycleListener listener) {
		if (listener == null || CHUNK_LIFECYCLE_LISTENERS.contains(listener)) {
			return;
		}
		CHUNK_LIFECYCLE_LISTENERS.add(listener);
	}

	public static void registerChunkProcessor(String processorId, ChunkProcessor processor) {
		String normalizedId = normalizeProcessorId(processorId);
		if (normalizedId.isBlank() || processor == null) {
			return;
		}
		CHUNK_PROCESSORS.put(normalizedId, new ChunkProcessorRuntime(normalizedId, processor));
		ACTIVE_CHUNK_PROCESSOR_IDS.add(normalizedId);
	}

	public static void setChunkProcessorActive(String processorId, boolean active) {
		String normalizedId = normalizeProcessorId(processorId);
		if (normalizedId.isBlank() || !CHUNK_PROCESSORS.containsKey(normalizedId)) {
			return;
		}
		if (active) {
			ACTIVE_CHUNK_PROCESSOR_IDS.add(normalizedId);
		} else {
			ACTIVE_CHUNK_PROCESSOR_IDS.remove(normalizedId);
		}
	}

	public static void resetChunkProcessor(String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime != null) {
			runtime.resetState();
		}
	}

	public static void runChunkProcessorProcessingStep(MinecraftServer server, String processorId) {
		String normalizedId = normalizeProcessorId(processorId);
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizedId);
		if (runtime == null || runtime.processor == null || server == null) {
			return;
		}
		if (!ACTIVE_CHUNK_PROCESSOR_IDS.contains(normalizedId)) {
			return;
		}
		processOneActiveTrackedChunk(server, runtime);
	}

	public static boolean isInternalProcessorMutationActive() {
		return INTERNAL_PROCESSOR_MUTATION_DEPTH.get() > 0;
	}

	private static void beginInternalProcessorMutation() {
		INTERNAL_PROCESSOR_MUTATION_DEPTH.set(INTERNAL_PROCESSOR_MUTATION_DEPTH.get() + 1);
	}

	private static void endInternalProcessorMutation() {
		int depth = INTERNAL_PROCESSOR_MUTATION_DEPTH.get() - 1;
		if (depth <= 0) {
			INTERNAL_PROCESSOR_MUTATION_DEPTH.remove();
			return;
		}
		INTERNAL_PROCESSOR_MUTATION_DEPTH.set(depth);
	}

	public static void trackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		trackChunkForProcessor(processorId, levelId(level), chunkX, chunkZ);
	}

	public static void trackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null) {
			return;
		}
		trackChunkWithState(runtime, new ProcessorChunkKey(levelId == null ? "" : levelId, chunkX, chunkZ));
	}

	public static void untrackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		untrackChunkForProcessor(processorId, levelId(level), chunkX, chunkZ);
	}

	public static void untrackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null) {
			return;
		}
		untrackChunkWithState(runtime, new ProcessorChunkKey(levelId == null ? "" : levelId, chunkX, chunkZ));
	}

	public static String normalizeLevelId(ServerLevel level) {
		return levelId(level);
	}

	public static void onWorldPositionChanged(ServerLevel level, BlockPos pos) {
		onWorldPositionChanged(level, pos, null, null);
	}

	public static void onWorldPositionChanged(ServerLevel level, BlockPos pos, BlockState previousState, BlockState nextState) {
		if (level == null || pos == null) {
			return;
		}
		if (previousState != null && nextState != null && previousState == nextState) {
			return;
		}

		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		if (!isChunkLoaded(level, chunkX, chunkZ)) {
			return;
		}

		refreshCachedColumn(level, chunkX, chunkZ, pos.getX(), pos.getZ());
		ProcessorChunkKey chunkKey = new ProcessorChunkKey(levelId(level), chunkX, chunkZ);
		markChunkDiscoveryDirty(chunkKey);
		markTrackedChunkDirtyForAllProcessors(chunkKey);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		// Chunk activity is runtime state; the persisted file is a snapshot and is not restored as live truth.
		DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		CHUNK_STATUSES_BY_LEVEL.clear();
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_CHUNKS.clear();
		DIRTY_DISCOVERY_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.clear();
		DISCOVERY_COLUMNS_CACHE.clear();
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
		serverStopping = false;
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		dirty = false;
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		SchedulerManagerSystem.clearAdaptiveDelayState(CHUNK_SCHEDULER_OWNER_ID);
		SchedulerManagerSystem.clearAdaptiveDelayState(DIRTY_DISCOVERY_SCHEDULER_OWNER_ID);
		clearProcessorRoundRobinAdaptiveState();

		serverStopping = false;
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
		seedLoadedChunks(server);
		if (!hasActiveChunkProcessors()) {
			chunkSchedulerId = "";
			refreshTaskScheduled = false;
			return;
		}
		chunkSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(CHUNK_SCHEDULER_OWNER_ID)
		);
		refreshTaskScheduled = SchedulerManagerSystem.hasQueuedTask(chunkSchedulerId, TASK_TYPE_CHUNK_REFRESH);
		if (!refreshTaskScheduled) {
			requestChunkRefresh(server, 1L);
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket == lastAutosaveBucket) {
			return;
		}

		lastAutosaveBucket = bucket;
		if (dirty) {
			savePersistedData(server);
		}
	}

	public static void onServerStopping(MinecraftServer server) {
		if (server == null) {
			return;
		}

		serverStopping = true;
		savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		refreshTrackedChunks(server);
		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
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
		if (status != null) {
			return status.isOrAfter(FullChunkStatus.BLOCK_TICKING);
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
		if (status != null) {
			return status.isOrAfter(FullChunkStatus.ENTITY_TICKING);
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

	public static FullChunkStatus getChunkStatus(ServerLevel level, int chunkX, int chunkZ) {
		return getStoredChunkStatus(level, chunkX, chunkZ);
	}

	private static List<Long> getLoadedChunkPositions(ServerLevel level) {
		if (level == null) {
			return List.of();
		}

		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId(level));
		if (chunks == null || chunks.isEmpty()) {
			return List.of();
		}

		return new ArrayList<>(chunks.keySet());
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level == null || chunk == null) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		String loadedLevelId = levelId(level);
		putChunkStatus(loadedLevelId, chunkPos.pack(), FullChunkStatus.FULL);
		ProcessorChunkKey chunkKey = new ProcessorChunkKey(loadedLevelId, chunkPos.x(), chunkPos.z());
		addSharedDiscoveryLoadedChunk(chunkKey);
		markChunkDiscoveryDirty(chunkKey);
		markTrackedChunkDirtyForAllProcessors(chunkKey);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			if (runtime.trackedChunksWithState.contains(chunkKey)) {
				addLoadedTrackedChunk(runtime, chunkKey);
			}
		}
		notifyChunkLoaded(level, chunkPos.x(), chunkPos.z());
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		if (serverStopping) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		String unloadedLevelId = levelId(level);
		removeChunk(unloadedLevelId, chunkPos.pack());
		ProcessorChunkKey chunkKey = new ProcessorChunkKey(unloadedLevelId, chunkPos.x(), chunkPos.z());
		removeCachedChunkColumns(chunkKey);
		removeSharedDiscoveryLoadedChunk(chunkKey);
		removeDirtyDiscoveryChunk(chunkKey);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			removeLoadedTrackedChunk(runtime, chunkKey);
		}
		notifyChunkUnloaded(level, chunkPos.x(), chunkPos.z());
	}

	private static void runChunkRefreshTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			chunkSchedulerId = context.getSchedulerId();
		}
		refreshTaskScheduled = false;
		if (server == null || !hasActiveChunkProcessors()) {
			return;
		}

		refreshTrackedChunks(server);
		int discoverySteps = hasPendingDirtyDiscoveryWork()
			? DIRTY_DISCOVERY_STEPS_PER_REFRESH
			: DISCOVERY_STEPS_PER_REFRESH;
		runSharedChunkDiscoverySteps(server, discoverySteps);
		long nextDelay = hasPendingDirtyDiscoveryWork()
			? resolveDirtyDiscoveryInterval(server)
			: resolveChunkRefreshInterval(server);
		requestChunkRefresh(server, nextDelay);
	}

	private static void seedLoadedChunks(MinecraftServer server) {
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_CHUNKS.clear();
		DIRTY_DISCOVERY_CHUNK_KEYS.clear();
		DISCOVERY_COLUMNS_CACHE.clear();
		discoveryChunkScanCursor = 0;
		for (ServerLevel level : server.getAllLevels()) {
			if (level == null) {
				continue;
			}

			String levelId = levelId(level);
			level.getChunkSource().chunkMap.forEachReadyToSendChunk((LevelChunk chunk) -> {
				if (chunk == null) {
					return;
				}

				FullChunkStatus status = resolveChunkStatus(level, chunk.getPos().pack());
				if (status != null) {
					putChunkStatus(levelId, chunk.getPos().pack(), status);
					ProcessorChunkKey chunkKey = new ProcessorChunkKey(levelId, chunk.getPos().x(), chunk.getPos().z());
					addSharedDiscoveryLoadedChunk(chunkKey);
					markChunkDiscoveryDirty(chunkKey);
				}
			});
		}
		discoveryChunksSeeded = true;
	}

	private static void refreshTrackedChunks(MinecraftServer server) {
		List<String> emptyLevels = new ArrayList<>();
		for (Map.Entry<String, Map<Long, FullChunkStatus>> levelEntry : new ArrayList<>(CHUNK_STATUSES_BY_LEVEL.entrySet())) {
			String levelId = levelEntry.getKey();
			ServerLevel level = resolveLevel(server, levelId);
			if (level == null) {
				emptyLevels.add(levelId);
				continue;
			}

			Map<Long, FullChunkStatus> chunks = levelEntry.getValue();
			if (chunks == null || chunks.isEmpty()) {
				emptyLevels.add(levelId);
				continue;
			}

			for (Long packedChunk : new ArrayList<>(chunks.keySet())) {
				if (packedChunk == null) {
					continue;
				}

				FullChunkStatus resolved = resolveChunkStatus(level, packedChunk);
				if (resolved == null) {
					chunks.remove(packedChunk);
					ProcessorChunkKey chunkKey = new ProcessorChunkKey(levelId, unpackChunkX(packedChunk), unpackChunkZ(packedChunk));
					removeCachedChunkColumns(chunkKey);
					removeSharedDiscoveryLoadedChunk(chunkKey);
					removeDirtyDiscoveryChunk(chunkKey);
					for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
						if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
							continue;
						}
						removeLoadedTrackedChunk(runtime, chunkKey);
					}
					dirty = true;
					notifyChunkUnloaded(level, unpackChunkX(packedChunk), unpackChunkZ(packedChunk));
					continue;
				}

				FullChunkStatus existing = chunks.get(packedChunk);
				if (existing != resolved) {
					chunks.put(packedChunk, resolved);
					dirty = true;
				}
			}

			if (chunks.isEmpty()) {
				emptyLevels.add(levelId);
			}
		}

		for (String levelId : emptyLevels) {
			if (CHUNK_STATUSES_BY_LEVEL.remove(levelId) != null) {
				dirty = true;
			}
		}
	}

	private static void requestChunkRefresh(MinecraftServer server, long delayTicks) {
		if (server == null || refreshTaskScheduled || !hasActiveChunkProcessors()) {
			return;
		}

		String schedulerId = ensureChunkSchedulerExists();
		if (enqueueChunkRefresh(schedulerId, delayTicks)) {
			refreshTaskScheduled = true;
			return;
		}

		chunkSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(CHUNK_SCHEDULER_OWNER_ID)
		);
		if (enqueueChunkRefresh(chunkSchedulerId, delayTicks)) {
			refreshTaskScheduled = true;
		}
	}

	private static boolean hasPendingDirtyDiscoveryWork() {
		return !DIRTY_DISCOVERY_CHUNKS.isEmpty();
	}

	private static long resolveChunkRefreshInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(
			server,
			CHUNK_SCHEDULER_OWNER_ID,
			CHUNK_REFRESH_MIN_INTERVAL_TICKS,
			CHUNK_REFRESH_MAX_INTERVAL_TICKS
		);
	}

	private static long resolveDirtyDiscoveryInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(
			server,
			DIRTY_DISCOVERY_SCHEDULER_OWNER_ID,
			DIRTY_DISCOVERY_MIN_INTERVAL_TICKS,
			DIRTY_DISCOVERY_MAX_INTERVAL_TICKS
		);
	}

	private static String ensureChunkSchedulerExists() {
		if (chunkSchedulerId == null || chunkSchedulerId.isBlank()) {
			chunkSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(CHUNK_SCHEDULER_OWNER_ID)
			);
		}
		return chunkSchedulerId;
	}

	private static boolean enqueueChunkRefresh(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}

		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_CHUNK_REFRESH,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static FullChunkStatus getStoredChunkStatus(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) {
			return null;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId(level));
		if (chunks == null) {
			return null;
		}
		return chunks.get(packChunk(chunkX, chunkZ));
	}

	private static void putChunkStatus(String levelId, long packedChunk, FullChunkStatus status) {
		if (levelId == null || levelId.isBlank()) {
			return;
		}
		if (status == null) {
			return;
		}

		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.computeIfAbsent(levelId, ignored -> new LinkedHashMap<>());
		FullChunkStatus existing = chunks.get(packedChunk);
		if (existing == status) {
			return;
		}
		chunks.put(packedChunk, status);
		dirty = true;
	}

	private static void removeChunk(String levelId, long packedChunk) {
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
		dirty = true;
	}

	private static void notifyChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkLoaded(level, chunkX, chunkZ);
		}
	}

	private static void notifyChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		for (ChunkLifecycleListener listener : CHUNK_LIFECYCLE_LISTENERS) {
			listener.onChunkUnloaded(level, chunkX, chunkZ);
		}
	}

	private static void processOneActiveTrackedChunk(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (runtime == null || runtime.processor == null || server == null) {
			return;
		}
		ProcessorChunkKey dirtyTrackedChunk = pollNextDirtyTrackedChunk(server, runtime);
		if (dirtyTrackedChunk != null) {
			ServerLevel world = resolveLevel(server, dirtyTrackedChunk.levelId());
			if (world != null && isChunkLoaded(world, dirtyTrackedChunk.chunkX(), dirtyTrackedChunk.chunkZ())) {
				beginInternalProcessorMutation();
				try {
					runtime.processor.processTrackedChunk(world, dirtyTrackedChunk.chunkX(), dirtyTrackedChunk.chunkZ());
				} finally {
					endInternalProcessorMutation();
				}
				return;
			}
			removeLoadedTrackedChunk(runtime, dirtyTrackedChunk);
		}
		if (runtime.loadedTrackedChunkCycle.isEmpty()) {
			recoverLoadedTrackedChunks(server, runtime);
		}
		if (runtime.loadedTrackedChunkCycle.isEmpty()) {
			runtime.activeChunkProcessCursor = 0;
			return;
		}
		if (!isRoundRobinProcessorStepDue(server, runtime)) {
			return;
		}

		int selectedIndex = Math.floorMod(runtime.activeChunkProcessCursor, runtime.loadedTrackedChunkCycle.size());
		ProcessorChunkKey selectedChunk = runtime.loadedTrackedChunkCycle.get(selectedIndex);
		ServerLevel world = resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		if (!loaded) {
			removeLoadedTrackedChunk(runtime, selectedChunk);
			return;
		}

		beginInternalProcessorMutation();
		try {
			runtime.processor.processTrackedChunk(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		} finally {
			endInternalProcessorMutation();
		}
		scheduleNextRoundRobinProcessorStep(server, runtime);
		boolean completedCycle = selectedIndex + 1 >= runtime.loadedTrackedChunkCycle.size();
		runtime.activeChunkProcessCursor = completedCycle ? 0 : selectedIndex + 1;
	}

	private static void recoverLoadedTrackedChunks(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null || runtime.trackedChunkCycle.isEmpty()) {
			return;
		}
		for (ProcessorChunkKey chunkKey : runtime.trackedChunkCycle) {
			if (chunkKey == null || chunkKey.levelId().isBlank()) {
				continue;
			}
			ServerLevel world = resolveLevel(server, chunkKey.levelId());
			if (world == null || !runtime.processor.acceptsWorld(world)) {
				continue;
			}
			if (isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
				addLoadedTrackedChunk(runtime, chunkKey);
			}
		}
	}

	private static void runSharedChunkDiscoverySteps(MinecraftServer server, int steps) {
		int safeSteps = Math.max(1, steps);
		for (int i = 0; i < safeSteps; i++) {
			runSharedChunkDiscoveryStep(server);
		}
	}

	private static void runSharedChunkDiscoveryStep(MinecraftServer server) {
		if (server == null) {
			return;
		}

		seedSharedDiscoveryChunksIfNeeded(server);
		ProcessorChunkKey dirtyChunk = pollNextDirtyDiscoveryChunk(server);
		if (dirtyChunk != null) {
			runChunkDiscoveryCallbacks(server, dirtyChunk, false);
			return;
		}

		if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			discoveryChunkScanCursor = 0;
			return;
		}

		int selectedIndex = Math.floorMod(discoveryChunkScanCursor, DISCOVERY_LOADED_CHUNKS.size());
		ProcessorChunkKey selectedChunk = DISCOVERY_LOADED_CHUNKS.get(selectedIndex);
		ServerLevel world = resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		if (!loaded) {
			removeCachedChunkColumns(selectedChunk);
			removeSharedDiscoveryLoadedChunk(selectedChunk);
			if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
				discoveryChunkScanCursor = 0;
			} else {
				discoveryChunkScanCursor = Math.min(discoveryChunkScanCursor, DISCOVERY_LOADED_CHUNKS.size() - 1);
			}
			return;
		}

		runChunkDiscoveryCallbacks(server, selectedChunk, true);

		boolean completedCycle = selectedIndex + 1 >= DISCOVERY_LOADED_CHUNKS.size();
		discoveryChunkScanCursor = completedCycle ? 0 : selectedIndex + 1;
	}

	private static void seedSharedDiscoveryChunksIfNeeded(MinecraftServer server) {
		if (server == null || discoveryChunksSeeded) {
			return;
		}
		discoveryChunksSeeded = true;
		for (ServerLevel world : server.getAllLevels()) {
			if (world == null) {
				continue;
			}
			for (Long packedChunk : getLoadedChunkPositions(world)) {
				if (packedChunk == null) {
					continue;
				}
				addSharedDiscoveryLoadedChunk(new ProcessorChunkKey(levelId(world), unpackChunkX(packedChunk), unpackChunkZ(packedChunk)));
			}
		}
	}

	private static void runChunkDiscoveryCallbacks(MinecraftServer server, ProcessorChunkKey chunkKey, boolean forceRescan) {
		if (server == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		ServerLevel world = resolveLevel(server, chunkKey.levelId());
		if (world == null || !isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
			removeCachedChunkColumns(chunkKey);
			removeSharedDiscoveryLoadedChunk(chunkKey);
			removeDirtyDiscoveryChunk(chunkKey);
			return;
		}

		ChunkDiscoverySnapshot discoverySnapshot;
		List<ChunkProcessorRuntime> activeRuntimes = new ArrayList<>();
		boolean needsMotionColumns = false;
		boolean needsSurfaceColumns = false;
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !ACTIVE_CHUNK_PROCESSOR_IDS.contains(runtime.id)) {
				continue;
			}
			if (!runtime.processor.acceptsWorld(world)) {
				continue;
			}
			activeRuntimes.add(runtime);
			needsMotionColumns |= runtime.processor.requiresMotionColumns();
			needsSurfaceColumns |= runtime.processor.requiresSurfaceColumns();
		}
		if (activeRuntimes.isEmpty()) {
			return;
		}

		discoverySnapshot = buildDiscoverySnapshot(
			world,
			chunkKey.chunkX(),
			chunkKey.chunkZ(),
			needsMotionColumns,
			needsSurfaceColumns,
			forceRescan
		);
		for (ChunkProcessorRuntime runtime : activeRuntimes) {
			runtime.processor.discoverLoadedChunk(world, chunkKey.chunkX(), chunkKey.chunkZ(), discoverySnapshot);
		}
	}

	private static ProcessorChunkKey pollNextDirtyDiscoveryChunk(MinecraftServer server) {
		while (!DIRTY_DISCOVERY_CHUNKS.isEmpty()) {
			ProcessorChunkKey chunkKey = DIRTY_DISCOVERY_CHUNKS.pollFirst();
			if (chunkKey == null) {
				continue;
			}
			DIRTY_DISCOVERY_CHUNK_KEYS.remove(chunkKey);
			if (chunkKey.levelId().isBlank()) {
				continue;
			}
			ServerLevel world = resolveLevel(server, chunkKey.levelId());
			if (world == null || !isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
				removeCachedChunkColumns(chunkKey);
				removeSharedDiscoveryLoadedChunk(chunkKey);
				continue;
			}
			return chunkKey;
		}
		return null;
	}

	private static void markChunkDiscoveryDirty(ProcessorChunkKey chunkKey) {
		if (chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!canRequeueDirtyChunk(chunkKey, DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS)) {
			return;
		}
		if (!DIRTY_DISCOVERY_CHUNK_KEYS.add(chunkKey)) {
			return;
		}
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.put(chunkKey, MadokuTicks.getGameplayTicks());
		DIRTY_DISCOVERY_CHUNKS.addLast(chunkKey);
	}

	private static void removeDirtyDiscoveryChunk(ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		if (!DIRTY_DISCOVERY_CHUNK_KEYS.remove(chunkKey)) {
			return;
		}
		DIRTY_DISCOVERY_CHUNKS.remove(chunkKey);
	}

	private static void markTrackedChunkDirtyForAllProcessors(ProcessorChunkKey chunkKey) {
		if (chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null) {
				continue;
			}
			if (!runtime.trackedChunksWithState.contains(chunkKey)) {
				continue;
			}
			markTrackedChunkDirty(runtime, chunkKey);
		}
	}

	private static void markTrackedChunkDirty(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!runtime.loadedTrackedChunkKeys.contains(chunkKey)) {
			return;
		}
		if (!canRequeueDirtyChunk(chunkKey, runtime.dirtyTrackedLastEnqueueTicks)) {
			return;
		}
		if (!runtime.dirtyTrackedChunkKeys.add(chunkKey)) {
			return;
		}
		runtime.dirtyTrackedLastEnqueueTicks.put(chunkKey, MadokuTicks.getGameplayTicks());
		runtime.dirtyTrackedChunks.addLast(chunkKey);
	}

	private static void removeTrackedChunkDirty(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null) {
			return;
		}
		if (!runtime.dirtyTrackedChunkKeys.remove(chunkKey)) {
			return;
		}
		runtime.dirtyTrackedChunks.remove(chunkKey);
	}

	private static ProcessorChunkKey pollNextDirtyTrackedChunk(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (runtime == null) {
			return null;
		}
		while (!runtime.dirtyTrackedChunks.isEmpty()) {
			ProcessorChunkKey chunkKey = runtime.dirtyTrackedChunks.pollFirst();
			if (chunkKey == null) {
				continue;
			}
			runtime.dirtyTrackedChunkKeys.remove(chunkKey);
			if (!runtime.loadedTrackedChunkKeys.contains(chunkKey)) {
				continue;
			}
			if (chunkKey.levelId().isBlank()) {
				continue;
			}
			ServerLevel world = resolveLevel(server, chunkKey.levelId());
			if (world == null || !isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
				removeLoadedTrackedChunk(runtime, chunkKey);
				continue;
			}
			return chunkKey;
		}
		return null;
	}

	private static void addSharedDiscoveryLoadedChunk(ProcessorChunkKey chunkKey) {
		if (chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!DISCOVERY_LOADED_CHUNK_KEYS.add(chunkKey)) {
			return;
		}
		DISCOVERY_LOADED_CHUNKS.add(chunkKey);
	}

	private static ChunkDiscoverySnapshot buildDiscoverySnapshot(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		boolean needsMotionColumns,
		boolean needsSurfaceColumns,
		boolean forceRescan
	) {
		REUSABLE_DISCOVERY_SNAPSHOT.begin(levelId(world), chunkX, chunkZ, needsMotionColumns, needsSurfaceColumns);
		if (world == null || (!needsMotionColumns && !needsSurfaceColumns)) {
			return REUSABLE_DISCOVERY_SNAPSHOT;
		}

		CachedChunkColumns cached = getOrCreateCachedChunkColumns(world, chunkX, chunkZ, forceRescan);
		if (cached == null) {
			return REUSABLE_DISCOVERY_SNAPSHOT;
		}
		copyCachedColumnsToReusableSnapshot(cached, needsMotionColumns, needsSurfaceColumns);

		return REUSABLE_DISCOVERY_SNAPSHOT;
	}

	private static void copyCachedColumnsToReusableSnapshot(
		CachedChunkColumns cached,
		boolean needsMotionColumns,
		boolean needsSurfaceColumns
	) {
		if (cached == null) {
			return;
		}
		int size = Math.min(CHUNK_COLUMN_COUNT, Math.min(cached.motionColumns.size(), cached.surfaceColumns.size()));
		for (int index = 0; index < size; index++) {
			if (needsMotionColumns) {
				REUSABLE_DISCOVERY_SNAPSHOT.motionColumnAt(index).copyFrom(cached.motionColumns.get(index));
			}
			if (needsSurfaceColumns) {
				REUSABLE_DISCOVERY_SNAPSHOT.surfaceColumnAt(index).copyFrom(cached.surfaceColumns.get(index));
			}
		}
	}

	private static CachedChunkColumns getOrCreateCachedChunkColumns(ServerLevel world, int chunkX, int chunkZ, boolean forceRescan) {
		if (world == null || !isChunkLoaded(world, chunkX, chunkZ)) {
			return null;
		}
		ProcessorChunkKey chunkKey = new ProcessorChunkKey(levelId(world), chunkX, chunkZ);
		CachedChunkColumns cached = DISCOVERY_COLUMNS_CACHE.get(chunkKey);
		if (cached == null) {
			cached = new CachedChunkColumns(CHUNK_COLUMN_COUNT);
			DISCOVERY_COLUMNS_CACHE.put(chunkKey, cached);
			forceRescan = true;
		}
		if (forceRescan) {
			rescanChunkColumnsIntoCache(world, chunkX, chunkZ, cached);
		}
		return cached;
	}

	private static void refreshCachedColumn(ServerLevel world, int chunkX, int chunkZ, int worldX, int worldZ) {
		if (world == null || !isChunkLoaded(world, chunkX, chunkZ)) {
			return;
		}
		CachedChunkColumns cached = getOrCreateCachedChunkColumns(world, chunkX, chunkZ, false);
		if (cached == null) {
			return;
		}
		int localX = worldX - (chunkX << 4);
		int localZ = worldZ - (chunkZ << 4);
		if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
			return;
		}
		updateCachedColumnSamples(cached, world, chunkX, chunkZ, localX, localZ);
	}

	private static void removeCachedChunkColumns(ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		DISCOVERY_COLUMNS_CACHE.remove(chunkKey);
	}

	private static void rescanChunkColumnsIntoCache(ServerLevel world, int chunkX, int chunkZ, CachedChunkColumns cached) {
		if (world == null || cached == null) {
			return;
		}
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				updateCachedColumnSamples(cached, world, chunkX, chunkZ, localX, localZ);
			}
		}
	}

	private static void updateCachedColumnSamples(
		CachedChunkColumns cached,
		ServerLevel world,
		int chunkX,
		int chunkZ,
		int localX,
		int localZ
	) {
		if (cached == null || world == null) {
			return;
		}
		int index = (localX << 4) + localZ;
		if (index < 0 || index >= CHUNK_COLUMN_COUNT) {
			return;
		}
		int worldX = (chunkX << 4) + localX;
		int worldZ = (chunkZ << 4) + localZ;
		int minY = world.getMinY();
		int maxY = world.getMaxY() - 1;

		int motionTopY = Math.min(maxY, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1);
		int surfaceTopY = Math.min(maxY, world.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1);
		sampleColumnInto(cached.motionColumns.get(index), world, worldX, worldZ, minY, motionTopY);
		sampleColumnInto(cached.surfaceColumns.get(index), world, worldX, worldZ, minY, surfaceTopY);
	}

	private static void sampleColumnInto(ColumnSample target, ServerLevel world, int worldX, int worldZ, int minY, int topY) {
		if (target == null) {
			return;
		}
		target.reset(worldX, worldZ);
		if (topY < minY || world == null) {
			return;
		}

		for (int depth = 0; depth <= 2; depth++) {
			int y = topY - depth;
			if (y < minY) {
				break;
			}
			long packedPos = net.minecraft.core.BlockPos.asLong(worldX, y, worldZ);
			target.setDepth(depth, y, packedPos, world.getBlockState(net.minecraft.core.BlockPos.of(packedPos)));
		}
	}

	private static void removeSharedDiscoveryLoadedChunk(ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		removeDirtyDiscoveryChunk(chunkKey);
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.remove(chunkKey);
		if (!DISCOVERY_LOADED_CHUNK_KEYS.remove(chunkKey)) {
			return;
		}
		DISCOVERY_LOADED_CHUNKS.remove(chunkKey);
		if (discoveryChunkScanCursor >= DISCOVERY_LOADED_CHUNKS.size()) {
			discoveryChunkScanCursor = 0;
		}
	}

	private static void trackChunkWithState(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (runtime.trackedChunksWithState.add(chunkKey)) {
			runtime.trackedChunkCycle.add(chunkKey);
		}
		if (isKnownLoadedChunk(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ())) {
			addLoadedTrackedChunk(runtime, chunkKey);
			markTrackedChunkDirty(runtime, chunkKey);
		}
	}

	private static void untrackChunkWithState(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!runtime.trackedChunksWithState.remove(chunkKey)) {
			return;
		}
		runtime.trackedChunkCycle.remove(chunkKey);
		removeTrackedChunkDirty(runtime, chunkKey);
		removeLoadedTrackedChunk(runtime, chunkKey);
		runtime.dirtyTrackedLastEnqueueTicks.remove(chunkKey);
	}

	private static void addLoadedTrackedChunk(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!runtime.loadedTrackedChunkKeys.add(chunkKey)) {
			return;
		}
		runtime.loadedTrackedChunkCycle.add(chunkKey);
		markTrackedChunkDirty(runtime, chunkKey);
	}

	private static void removeLoadedTrackedChunk(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null) {
			return;
		}
		removeTrackedChunkDirty(runtime, chunkKey);
		if (!runtime.loadedTrackedChunkKeys.remove(chunkKey)) {
			return;
		}
		runtime.loadedTrackedChunkCycle.remove(chunkKey);
		if (runtime.activeChunkProcessCursor >= runtime.loadedTrackedChunkCycle.size()) {
			runtime.activeChunkProcessCursor = 0;
		}
	}

	private static boolean canRequeueDirtyChunk(ProcessorChunkKey chunkKey, Map<ProcessorChunkKey, Long> lastEnqueueTicks) {
		if (chunkKey == null || lastEnqueueTicks == null) {
			return false;
		}
		long currentTick = MadokuTicks.getGameplayTicks();
		Long lastTick = lastEnqueueTicks.get(chunkKey);
		if (lastTick == null) {
			return true;
		}
		long elapsed = currentTick - lastTick;
		return elapsed < 0L || elapsed >= DIRTY_REQUEUE_COOLDOWN_TICKS;
	}

	private static boolean isRoundRobinProcessorStepDue(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null) {
			return false;
		}
		long currentTick = MadokuTicks.getGameplayTicks();
		if (runtime.nextRoundRobinProcessGameplayTick == Long.MIN_VALUE) {
			return true;
		}
		if (currentTick < runtime.nextRoundRobinProcessGameplayTick) {
			return false;
		}
		return true;
	}

	private static void scheduleNextRoundRobinProcessorStep(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null) {
			return;
		}
		long interval = SchedulerManagerSystem.resolveAdaptiveDelayTicks(
			server,
			processorRoundRobinAdaptiveOwnerId(runtime.id),
			PROCESSOR_ROUND_ROBIN_MIN_INTERVAL_TICKS,
			PROCESSOR_ROUND_ROBIN_MAX_INTERVAL_TICKS
		);
		long currentTick = MadokuTicks.getGameplayTicks();
		runtime.nextRoundRobinProcessGameplayTick = currentTick + Math.max(1L, interval);
	}

	private static String processorRoundRobinAdaptiveOwnerId(String processorId) {
		return PROCESSOR_ROUND_ROBIN_SCHEDULER_OWNER_PREFIX + normalizeProcessorId(processorId);
	}

	private static void clearProcessorRoundRobinAdaptiveState() {
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.id == null || runtime.id.isBlank()) {
				continue;
			}
			SchedulerManagerSystem.clearAdaptiveDelayState(processorRoundRobinAdaptiveOwnerId(runtime.id));
		}
	}

	private static boolean isKnownLoadedChunk(String levelId, int chunkX, int chunkZ) {
		if (levelId == null || levelId.isBlank()) {
			return false;
		}
		Map<Long, FullChunkStatus> chunks = CHUNK_STATUSES_BY_LEVEL.get(levelId);
		if (chunks == null || chunks.isEmpty()) {
			return false;
		}
		FullChunkStatus status = chunks.get(packChunk(chunkX, chunkZ));
		return status != null && status.isOrAfter(FullChunkStatus.FULL);
	}

	private static String normalizeProcessorId(String processorId) {
		return processorId == null ? "" : processorId.trim().toLowerCase();
	}

	private static boolean hasActiveChunkProcessors() {
		if (ACTIVE_CHUNK_PROCESSOR_IDS.isEmpty()) {
			return false;
		}
		for (String processorId : ACTIVE_CHUNK_PROCESSOR_IDS) {
			ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(processorId);
			if (runtime != null && runtime.processor != null) {
				return true;
			}
		}
		return false;
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add(FIELD_LEVELS, new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		JsonArray levels = new JsonArray();
		for (Map.Entry<String, Map<Long, FullChunkStatus>> levelEntry : CHUNK_STATUSES_BY_LEVEL.entrySet()) {
			String levelId = levelEntry.getKey();
			Map<Long, FullChunkStatus> chunks = levelEntry.getValue();
			if (levelId == null || levelId.isBlank() || chunks == null || chunks.isEmpty()) {
				continue;
			}

			JsonObject levelObject = new JsonObject();
			levelObject.addProperty(FIELD_LEVEL_ID, levelId);
			JsonArray chunkArray = new JsonArray();
			List<Map.Entry<Long, FullChunkStatus>> orderedChunks = new ArrayList<>(chunks.entrySet());
			orderedChunks.sort(Comparator.comparingLong(Map.Entry::getKey));
			for (Map.Entry<Long, FullChunkStatus> chunkEntry : orderedChunks) {
				Long packedChunk = chunkEntry.getKey();
				FullChunkStatus status = chunkEntry.getValue();
				if (packedChunk == null || status == null) {
					continue;
				}

				JsonObject chunkObject = new JsonObject();
				chunkObject.addProperty(FIELD_CHUNK_X, unpackChunkX(packedChunk));
				chunkObject.addProperty(FIELD_CHUNK_Z, unpackChunkZ(packedChunk));
				chunkObject.addProperty(FIELD_STATUS, status.name().toLowerCase());
				chunkArray.add(chunkObject);
			}
			if (!chunkArray.isEmpty()) {
				levelObject.add(FIELD_CHUNKS, chunkArray);
				levels.add(levelObject);
			}
		}
		root.add(FIELD_LEVELS, levels);
		return root;
	}

	private static FullChunkStatus resolveChunkStatus(ServerLevel level, long packedChunk) {
		if (level == null) {
			return null;
		}

		int chunkX = unpackChunkX(packedChunk);
		int chunkZ = unpackChunkZ(packedChunk);
		if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
			return null;
		}
		if (level.areEntitiesActuallyLoadedAndTicking(ChunkPos.unpack(packedChunk))) {
			return FullChunkStatus.ENTITY_TICKING;
		}
		if (level.getChunkSource().isPositionTicking(packedChunk)) {
			return FullChunkStatus.BLOCK_TICKING;
		}
		return FullChunkStatus.FULL;
	}

	private static final class CachedChunkColumns {
		private final List<ColumnSample> motionColumns;
		private final List<ColumnSample> surfaceColumns;

		private CachedChunkColumns(int capacity) {
			int safeCapacity = Math.max(1, capacity);
			List<ColumnSample> motion = new ArrayList<>(safeCapacity);
			List<ColumnSample> surface = new ArrayList<>(safeCapacity);
			for (int i = 0; i < safeCapacity; i++) {
				motion.add(new ColumnSample());
				surface.add(new ColumnSample());
			}
			this.motionColumns = motion;
			this.surfaceColumns = surface;
		}
	}

	private static final class ChunkProcessorRuntime {
		private final String id;
		private final ChunkProcessor processor;
		private final Set<ProcessorChunkKey> trackedChunksWithState = new LinkedHashSet<>();
		private final List<ProcessorChunkKey> trackedChunkCycle = new ArrayList<>();
		private final Set<ProcessorChunkKey> loadedTrackedChunkKeys = new LinkedHashSet<>();
		private final List<ProcessorChunkKey> loadedTrackedChunkCycle = new ArrayList<>();
		private final Set<ProcessorChunkKey> dirtyTrackedChunkKeys = new LinkedHashSet<>();
		private final Deque<ProcessorChunkKey> dirtyTrackedChunks = new ArrayDeque<>();
		private final Map<ProcessorChunkKey, Long> dirtyTrackedLastEnqueueTicks = new LinkedHashMap<>();
		private int activeChunkProcessCursor = 0;
		private long nextRoundRobinProcessGameplayTick = Long.MIN_VALUE;

		private ChunkProcessorRuntime(String id, ChunkProcessor processor) {
			this.id = id;
			this.processor = processor;
		}

		private void resetState() {
			trackedChunksWithState.clear();
			trackedChunkCycle.clear();
			loadedTrackedChunkKeys.clear();
			loadedTrackedChunkCycle.clear();
			dirtyTrackedChunkKeys.clear();
			dirtyTrackedChunks.clear();
			dirtyTrackedLastEnqueueTicks.clear();
			activeChunkProcessCursor = 0;
			nextRoundRobinProcessGameplayTick = Long.MIN_VALUE;
		}
	}

	private record ProcessorChunkKey(String levelId, int chunkX, int chunkZ) {
	}

	private static String levelId(ServerLevel level) {
		if (level == null) {
			return "";
		}
		return SchedulerManagerSystem.normalizeLevelIdentifier(level.dimension().toString());
	}

	private static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		if (server == null || levelId == null || levelId.isBlank()) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level != null && levelId.equals(levelId(level))) {
				return level;
			}
		}
		return null;
	}

	private static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	private static int unpackChunkX(long packedChunk) {
		return (int) (packedChunk >> 32);
	}

	private static int unpackChunkZ(long packedChunk) {
		return (int) packedChunk;
	}
}
