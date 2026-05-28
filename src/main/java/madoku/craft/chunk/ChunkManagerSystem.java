package madoku.craft.chunk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
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
	private static final String TASK_TYPE_CHUNK_REFRESH = "chunk_refresh";
	private static final long CHUNK_REFRESH_MIN_INTERVAL_TICKS = 1L;
	private static final long CHUNK_REFRESH_MAX_INTERVAL_TICKS = 20L;
	private static final int DISCOVERY_STEPS_PER_REFRESH = 1;
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
	private static final ChunkDiscoverySnapshot REUSABLE_DISCOVERY_SNAPSHOT = ChunkDiscoverySnapshot.reusable(16 * 16);

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

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		// Chunk activity is runtime state; the persisted file is a snapshot and is not restored as live truth.
		DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		CHUNK_STATUSES_BY_LEVEL.clear();
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
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
		removeSharedDiscoveryLoadedChunk(chunkKey);
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
		runSharedChunkDiscoverySteps(server, DISCOVERY_STEPS_PER_REFRESH);
		requestChunkRefresh(server, resolveChunkRefreshInterval(server));
	}

	private static void seedLoadedChunks(MinecraftServer server) {
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
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
					addSharedDiscoveryLoadedChunk(new ProcessorChunkKey(levelId, chunk.getPos().x(), chunk.getPos().z()));
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

	private static long resolveChunkRefreshInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(
			server,
			CHUNK_SCHEDULER_OWNER_ID,
			CHUNK_REFRESH_MIN_INTERVAL_TICKS,
			CHUNK_REFRESH_MAX_INTERVAL_TICKS
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
		if (runtime.loadedTrackedChunkCycle.isEmpty()) {
			recoverLoadedTrackedChunks(server, runtime);
		}
		if (runtime.loadedTrackedChunkCycle.isEmpty()) {
			runtime.activeChunkProcessCursor = 0;
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

		runtime.processor.processTrackedChunk(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
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
		if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			discoveryChunkScanCursor = 0;
			return;
		}

		int selectedIndex = Math.floorMod(discoveryChunkScanCursor, DISCOVERY_LOADED_CHUNKS.size());
		ProcessorChunkKey selectedChunk = DISCOVERY_LOADED_CHUNKS.get(selectedIndex);
		ServerLevel world = resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		if (!loaded) {
			removeSharedDiscoveryLoadedChunk(selectedChunk);
			if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
				discoveryChunkScanCursor = 0;
			} else {
				discoveryChunkScanCursor = Math.min(discoveryChunkScanCursor, DISCOVERY_LOADED_CHUNKS.size() - 1);
			}
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
			boolean completedCycle = selectedIndex + 1 >= DISCOVERY_LOADED_CHUNKS.size();
			discoveryChunkScanCursor = completedCycle ? 0 : selectedIndex + 1;
			return;
		}

		discoverySnapshot = buildDiscoverySnapshot(world, selectedChunk.chunkX(), selectedChunk.chunkZ(), needsMotionColumns, needsSurfaceColumns);
		for (ChunkProcessorRuntime runtime : activeRuntimes) {
			runtime.processor.discoverLoadedChunk(world, selectedChunk.chunkX(), selectedChunk.chunkZ(), discoverySnapshot);
		}

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
		boolean needsSurfaceColumns
	) {
		REUSABLE_DISCOVERY_SNAPSHOT.begin(levelId(world), chunkX, chunkZ, needsMotionColumns, needsSurfaceColumns);
		if (world == null || (!needsMotionColumns && !needsSurfaceColumns)) {
			return REUSABLE_DISCOVERY_SNAPSHOT;
		}

		int minX = chunkX << 4;
		int minZ = chunkZ << 4;
		int minY = world.getMinY();
		int maxY = world.getMaxY() - 1;

		int index = 0;
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldX = minX + localX;
				int worldZ = minZ + localZ;
				ColumnSample motionColumn = REUSABLE_DISCOVERY_SNAPSHOT.motionColumnAt(index);
				ColumnSample surfaceColumn = REUSABLE_DISCOVERY_SNAPSHOT.surfaceColumnAt(index);
				if (needsMotionColumns) {
					int motionTopY = Math.min(maxY, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1);
					sampleColumnInto(motionColumn, world, worldX, worldZ, minY, motionTopY);
				}
				if (needsSurfaceColumns) {
					int surfaceTopY = Math.min(maxY, world.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1);
					sampleColumnInto(surfaceColumn, world, worldX, worldZ, minY, surfaceTopY);
				}
				index++;
			}
		}

		return REUSABLE_DISCOVERY_SNAPSHOT;
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
		removeLoadedTrackedChunk(runtime, chunkKey);
	}

	private static void addLoadedTrackedChunk(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!runtime.loadedTrackedChunkKeys.add(chunkKey)) {
			return;
		}
		runtime.loadedTrackedChunkCycle.add(chunkKey);
	}

	private static void removeLoadedTrackedChunk(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null) {
			return;
		}
		if (!runtime.loadedTrackedChunkKeys.remove(chunkKey)) {
			return;
		}
		runtime.loadedTrackedChunkCycle.remove(chunkKey);
		if (runtime.activeChunkProcessCursor >= runtime.loadedTrackedChunkCycle.size()) {
			runtime.activeChunkProcessCursor = 0;
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

	private static final class ChunkProcessorRuntime {
		private final String id;
		private final ChunkProcessor processor;
		private final Set<ProcessorChunkKey> trackedChunksWithState = new LinkedHashSet<>();
		private final List<ProcessorChunkKey> trackedChunkCycle = new ArrayList<>();
		private final Set<ProcessorChunkKey> loadedTrackedChunkKeys = new LinkedHashSet<>();
		private final List<ProcessorChunkKey> loadedTrackedChunkCycle = new ArrayList<>();
		private int activeChunkProcessCursor = 0;

		private ChunkProcessorRuntime(String id, ChunkProcessor processor) {
			this.id = id;
			this.processor = processor;
		}

		private void resetState() {
			trackedChunksWithState.clear();
			trackedChunkCycle.clear();
			loadedTrackedChunkKeys.clear();
			loadedTrackedChunkCycle.clear();
			activeChunkProcessCursor = 0;
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
