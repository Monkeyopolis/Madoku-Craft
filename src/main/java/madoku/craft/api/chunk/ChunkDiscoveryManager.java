package madoku.craft.api.chunk;

import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChunkDiscoveryManager {
	private static final String DATA_FOLDER_NAME = "madoku-craft-chunks";
	private static final String DATA_FILE_NAME = "madoku-chunks";
	private static final String CHUNK_SCHEDULER_OWNER_ID = "madoku_chunks";
	private static final String DIRTY_DISCOVERY_SCHEDULER_OWNER_ID = "madoku_chunks_dirty_discovery";
	private static final String TASK_TYPE_CHUNK_REFRESH = "chunk_refresh";
	private static final long CHUNK_REFRESH_MIN_INTERVAL_TICKS = 4L * 60L * 20L;
	private static final long CHUNK_REFRESH_MAX_INTERVAL_TICKS = 8L * 60L * 20L;
	private static final long DIRTY_DISCOVERY_MIN_INTERVAL_TICKS = 1L;
	private static final long DIRTY_DISCOVERY_MAX_INTERVAL_TICKS = 20L;
	private static final long DIRTY_REQUEUE_COOLDOWN_TICKS = 20L;
	private static final int DIRTY_DISCOVERY_STEPS_PER_REFRESH = 1;
	private static final int DISCOVERY_STEPS_PER_REFRESH = 1;
	private static final int CHUNK_COLUMN_COUNT = 16 * 16;

	private static final List<MadokuChunkManager.ProcessorChunkKey> DISCOVERY_LOADED_CHUNKS = new ArrayList<>();
	private static final Set<MadokuChunkManager.ProcessorChunkKey> DISCOVERY_LOADED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Deque<MadokuChunkManager.ProcessorChunkKey> DIRTY_DISCOVERY_CHUNKS = new ArrayDeque<>();
	private static final Set<MadokuChunkManager.ProcessorChunkKey> DIRTY_DISCOVERY_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Map<MadokuChunkManager.ProcessorChunkKey, Long> DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS = new LinkedHashMap<>();
	private static final Map<MadokuChunkManager.ProcessorChunkKey, CachedChunkColumns> DISCOVERY_COLUMNS_CACHE = new LinkedHashMap<>();
	private static final Map<MadokuChunkManager.ProcessorChunkKey, ChunkDiscoveryProgress> DISCOVERY_PROGRESS_BY_CHUNK = new LinkedHashMap<>();
	private static final MadokuChunkManager.ChunkDiscoverySnapshot REUSABLE_DISCOVERY_SNAPSHOT = MadokuChunkManager.ChunkDiscoverySnapshot.reusable(CHUNK_COLUMN_COUNT);

	private static volatile String chunkSchedulerId = "";
	private static volatile boolean refreshTaskScheduled = false;
	private static volatile boolean serverStopping = false;
	private static volatile boolean dirty = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile int discoveryChunkScanCursor = 0;
	private static volatile boolean discoveryChunksSeeded = false;

	private ChunkDiscoveryManager() {
	}

	public static void initialize() {
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_CHUNK_REFRESH, ChunkDiscoveryManager::runChunkRefreshTask);
		ServerChunkEvents.CHUNK_LOAD.register(ChunkDiscoveryManager::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(ChunkDiscoveryManager::onChunkUnload);
	}

	public static void reset() {
		clearRuntimeState();
		chunkSchedulerId = "";
		refreshTaskScheduled = false;
		serverStopping = false;
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
		SchedulerManagerSystem.clearAdaptiveDelayState(CHUNK_SCHEDULER_OWNER_ID);
		SchedulerManagerSystem.clearAdaptiveDelayState(DIRTY_DISCOVERY_SCHEDULER_OWNER_ID);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		clearRuntimeState();
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
		serverStopping = false;
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
		seedLoadedChunks(server);
		if (!ChunkProcessorManager.hasActiveChunkProcessors()) {
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
		ChunkProcessorManager.refreshTrackedChunks(server);
		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, MadokuChunkManager.toPersistedData());
		dirty = false;
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
		if (!MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)) {
			return;
		}
		refreshCachedColumn(level, chunkX, chunkZ, pos.getX(), pos.getZ());
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(MadokuChunkManager.levelId(level), chunkX, chunkZ);
		markChunkDiscoveryDirty(chunkKey, chunkColumnIndex(pos.getX(), pos.getZ()));
		ChunkProcessorManager.onWorldPositionChanged(level, chunkX, chunkZ);
	}

	public static void refreshTrackedChunks(MinecraftServer server) {
		ChunkProcessorManager.refreshTrackedChunks(server);
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level == null || chunk == null) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		String loadedLevelId = MadokuChunkManager.levelId(level);
		MadokuChunkManager.putChunkStatus(loadedLevelId, chunkPos.pack(), FullChunkStatus.FULL);
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(loadedLevelId, chunkPos.x(), chunkPos.z());
		addSharedDiscoveryLoadedChunk(chunkKey);
		resetDiscoveryProgress(chunkKey, 0);
		markChunkDiscoveryDirty(chunkKey, 0);
		ChunkProcessorManager.onChunkLoaded(level, chunkPos.x(), chunkPos.z());
		MadokuChunkManager.notifyChunkLoaded(level, chunkPos.x(), chunkPos.z());
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		if (serverStopping) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		String unloadedLevelId = MadokuChunkManager.levelId(level);
		MadokuChunkManager.removeChunk(unloadedLevelId, chunkPos.pack());
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(unloadedLevelId, chunkPos.x(), chunkPos.z());
		removeCachedChunkColumns(chunkKey);
		removeDiscoveryProgress(chunkKey);
		removeSharedDiscoveryLoadedChunk(chunkKey);
		removeDirtyDiscoveryChunk(chunkKey);
		ChunkProcessorManager.onChunkUnloaded(level, chunkPos.x(), chunkPos.z());
		MadokuChunkManager.notifyChunkUnloaded(level, chunkPos.x(), chunkPos.z());
	}

	private static void runChunkRefreshTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			chunkSchedulerId = context.getSchedulerId();
		}
		refreshTaskScheduled = false;
		if (server == null || !ChunkProcessorManager.hasActiveChunkProcessors()) {
			return;
		}
		ChunkProcessorManager.refreshTrackedChunks(server);
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
		clearDiscoveryQueues();
		discoveryChunkScanCursor = 0;
		for (ServerLevel level : server.getAllLevels()) {
			if (level == null) {
				continue;
			}
			String levelId = MadokuChunkManager.levelId(level);
			level.getChunkSource().chunkMap.forEachReadyToSendChunk((LevelChunk chunk) -> {
				if (chunk == null) {
					return;
				}
				FullChunkStatus status = MadokuChunkManager.resolveChunkStatus(level, chunk.getPos().pack());
				if (status != null) {
					MadokuChunkManager.putChunkStatus(levelId, chunk.getPos().pack(), status);
					MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(levelId, chunk.getPos().x(), chunk.getPos().z());
					addSharedDiscoveryLoadedChunk(chunkKey);
					resetDiscoveryProgress(chunkKey, 0);
					markChunkDiscoveryDirty(chunkKey, 0);
				}
			});
		}
		discoveryChunksSeeded = true;
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
		MadokuChunkManager.ProcessorChunkKey dirtyChunk = pollNextDirtyDiscoveryChunk(server);
		if (dirtyChunk != null) {
			runChunkDiscoveryCallbacks(server, dirtyChunk);
			return;
		}
		if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			discoveryChunkScanCursor = 0;
			return;
		}
		int selectedIndex = Math.floorMod(discoveryChunkScanCursor, DISCOVERY_LOADED_CHUNKS.size());
		MadokuChunkManager.ProcessorChunkKey selectedChunk = DISCOVERY_LOADED_CHUNKS.get(selectedIndex);
		ServerLevel world = MadokuChunkManager.resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && MadokuChunkManager.isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
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
		runChunkDiscoveryCallbacks(server, selectedChunk);
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
			for (Long packedChunk : MadokuChunkManager.getLoadedChunkPositions(world)) {
				if (packedChunk == null) {
					continue;
				}
				addSharedDiscoveryLoadedChunk(new MadokuChunkManager.ProcessorChunkKey(MadokuChunkManager.levelId(world), MadokuChunkManager.unpackChunkX(packedChunk), MadokuChunkManager.unpackChunkZ(packedChunk)));
			}
		}
	}

	private static void runChunkDiscoveryCallbacks(MinecraftServer server, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (server == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		ServerLevel world = MadokuChunkManager.resolveLevel(server, chunkKey.levelId());
		if (world == null || !MadokuChunkManager.isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
			removeCachedChunkColumns(chunkKey);
			removeSharedDiscoveryLoadedChunk(chunkKey);
			removeDirtyDiscoveryChunk(chunkKey);
			return;
		}

		List<MadokuChunkManager.ChunkProcessor> activeProcessors = new ArrayList<>();
		boolean needsMotionColumns = false;
		boolean needsSurfaceColumns = false;
		for (String processorId : new ArrayList<>(getActiveProcessorIds())) {
			MadokuChunkManager.ChunkProcessor processor = getProcessor(processorId);
			if (processor == null || !processor.acceptsWorld(world)) {
				continue;
			}
			activeProcessors.add(processor);
			needsMotionColumns |= processor.requiresMotionColumns();
			needsSurfaceColumns |= processor.requiresSurfaceColumns();
		}
		if (activeProcessors.isEmpty()) {
			return;
		}

		ChunkDiscoveryProgress progress = getOrCreateDiscoveryProgress(chunkKey);
		if (progress == null) {
			return;
		}
		if (!progress.started) {
			for (MadokuChunkManager.ChunkProcessor processor : activeProcessors) {
				processor.beginLoadedChunkDiscovery(world, chunkKey.chunkX(), chunkKey.chunkZ());
			}
			progress.started = true;
		}

		int columnIndex = progress.nextColumnIndex;
		MadokuChunkManager.ChunkDiscoverySnapshot discoverySnapshot = buildDiscoverySnapshot(
			world,
			chunkKey.chunkX(),
			chunkKey.chunkZ(),
			columnIndex,
			needsMotionColumns,
			needsSurfaceColumns
		);
		for (MadokuChunkManager.ChunkProcessor processor : activeProcessors) {
			processor.discoverLoadedChunk(world, chunkKey.chunkX(), chunkKey.chunkZ(), discoverySnapshot);
		}

		boolean completedCycle = columnIndex + 1 >= CHUNK_COLUMN_COUNT;
		if (completedCycle) {
			for (MadokuChunkManager.ChunkProcessor processor : activeProcessors) {
				processor.finishLoadedChunkDiscovery(world, chunkKey.chunkX(), chunkKey.chunkZ());
			}
			progress.started = false;
			progress.reset(0);
			return;
		}
		progress.reset(columnIndex + 1);
	}

	private static MadokuChunkManager.ProcessorChunkKey pollNextDirtyDiscoveryChunk(MinecraftServer server) {
		while (!DIRTY_DISCOVERY_CHUNKS.isEmpty()) {
			MadokuChunkManager.ProcessorChunkKey chunkKey = DIRTY_DISCOVERY_CHUNKS.pollFirst();
			if (chunkKey == null) {
				continue;
			}
			DIRTY_DISCOVERY_CHUNK_KEYS.remove(chunkKey);
			if (chunkKey.levelId().isBlank()) {
				continue;
			}
			ServerLevel world = MadokuChunkManager.resolveLevel(server, chunkKey.levelId());
			if (world == null || !MadokuChunkManager.isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
				removeCachedChunkColumns(chunkKey);
				removeSharedDiscoveryLoadedChunk(chunkKey);
				continue;
			}
			return chunkKey;
		}
		return null;
	}

	private static void markChunkDiscoveryDirty(MadokuChunkManager.ProcessorChunkKey chunkKey, int columnIndex) {
		if (chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		resetDiscoveryProgress(chunkKey, columnIndex);
		if (!canRequeueDirtyChunk(chunkKey, DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS)) {
			return;
		}
		if (!DIRTY_DISCOVERY_CHUNK_KEYS.add(chunkKey)) {
			return;
		}
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.put(chunkKey, MadokuTicks.getGameplayTicks());
		DIRTY_DISCOVERY_CHUNKS.addLast(chunkKey);
	}

	private static ChunkDiscoveryProgress getOrCreateDiscoveryProgress(MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (chunkKey == null || chunkKey.levelId().isBlank()) {
			return null;
		}
		return DISCOVERY_PROGRESS_BY_CHUNK.computeIfAbsent(chunkKey, ignored -> ChunkDiscoveryProgress.fresh());
	}

	private static void resetDiscoveryProgress(MadokuChunkManager.ProcessorChunkKey chunkKey, int columnIndex) {
		ChunkDiscoveryProgress progress = getOrCreateDiscoveryProgress(chunkKey);
		if (progress == null) {
			return;
		}
		progress.reset(columnIndex);
	}

	private static void removeDiscoveryProgress(MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		DISCOVERY_PROGRESS_BY_CHUNK.remove(chunkKey);
	}

	private static int chunkColumnIndex(int worldX, int worldZ) {
		int localX = worldX & 15;
		int localZ = worldZ & 15;
		return (localX << 4) + localZ;
	}

	private static void removeDirtyDiscoveryChunk(MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		if (!DIRTY_DISCOVERY_CHUNK_KEYS.remove(chunkKey)) {
			return;
		}
		DIRTY_DISCOVERY_CHUNKS.remove(chunkKey);
	}

	private static void addSharedDiscoveryLoadedChunk(MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!DISCOVERY_LOADED_CHUNK_KEYS.add(chunkKey)) {
			return;
		}
		DISCOVERY_LOADED_CHUNKS.add(chunkKey);
	}

	private static MadokuChunkManager.ChunkDiscoverySnapshot buildDiscoverySnapshot(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		int columnIndex,
		boolean needsMotionColumns,
		boolean needsSurfaceColumns
	) {
		REUSABLE_DISCOVERY_SNAPSHOT.beginColumn(MadokuChunkManager.levelId(world), chunkX, chunkZ, columnIndex, needsMotionColumns, needsSurfaceColumns);
		if (world == null || (!needsMotionColumns && !needsSurfaceColumns)) {
			return REUSABLE_DISCOVERY_SNAPSHOT;
		}

		CachedChunkColumns cached = getOrCreateCachedChunkColumns(world, chunkX, chunkZ);
		if (cached == null) {
			return REUSABLE_DISCOVERY_SNAPSHOT;
		}
		int localX = Math.floorDiv(columnIndex, 16);
		int localZ = Math.floorMod(columnIndex, 16);
		updateCachedColumnSamples(cached, world, chunkX, chunkZ, localX, localZ);
		copyCachedColumnToReusableSnapshot(cached, columnIndex, needsMotionColumns, needsSurfaceColumns);

		return REUSABLE_DISCOVERY_SNAPSHOT;
	}

	private static void copyCachedColumnToReusableSnapshot(
		CachedChunkColumns cached,
		int columnIndex,
		boolean needsMotionColumns,
		boolean needsSurfaceColumns
	) {
		if (cached == null || columnIndex < 0 || columnIndex >= CHUNK_COLUMN_COUNT) {
			return;
		}
		if (needsMotionColumns) {
			REUSABLE_DISCOVERY_SNAPSHOT.motionColumnAt(columnIndex).copyFrom(cached.motionColumns.get(columnIndex));
		}
		if (needsSurfaceColumns) {
			REUSABLE_DISCOVERY_SNAPSHOT.surfaceColumnAt(columnIndex).copyFrom(cached.surfaceColumns.get(columnIndex));
		}
	}

	private static CachedChunkColumns getOrCreateCachedChunkColumns(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null || !MadokuChunkManager.isChunkLoaded(world, chunkX, chunkZ)) {
			return null;
		}
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(MadokuChunkManager.levelId(world), chunkX, chunkZ);
		CachedChunkColumns cached = DISCOVERY_COLUMNS_CACHE.get(chunkKey);
		if (cached == null) {
			cached = new CachedChunkColumns(CHUNK_COLUMN_COUNT);
			DISCOVERY_COLUMNS_CACHE.put(chunkKey, cached);
		}
		return cached;
	}

	private static void refreshCachedColumn(ServerLevel world, int chunkX, int chunkZ, int worldX, int worldZ) {
		if (world == null || !MadokuChunkManager.isChunkLoaded(world, chunkX, chunkZ)) {
			return;
		}
		CachedChunkColumns cached = getOrCreateCachedChunkColumns(world, chunkX, chunkZ);
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

	private static void removeCachedChunkColumns(MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		DISCOVERY_COLUMNS_CACHE.remove(chunkKey);
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

	private static void sampleColumnInto(MadokuChunkManager.ColumnSample target, ServerLevel world, int worldX, int worldZ, int minY, int topY) {
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

	private static void removeSharedDiscoveryLoadedChunk(MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		removeDirtyDiscoveryChunk(chunkKey);
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.remove(chunkKey);
		removeDiscoveryProgress(chunkKey);
		if (!DISCOVERY_LOADED_CHUNK_KEYS.remove(chunkKey)) {
			return;
		}
		DISCOVERY_LOADED_CHUNKS.remove(chunkKey);
		if (discoveryChunkScanCursor >= DISCOVERY_LOADED_CHUNKS.size()) {
			discoveryChunkScanCursor = 0;
		}
	}

	private static void clearRuntimeState() {
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_CHUNKS.clear();
		DIRTY_DISCOVERY_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.clear();
		DISCOVERY_COLUMNS_CACHE.clear();
		DISCOVERY_PROGRESS_BY_CHUNK.clear();
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
	}

	private static void clearDiscoveryQueues() {
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_CHUNKS.clear();
		DIRTY_DISCOVERY_CHUNK_KEYS.clear();
		DIRTY_DISCOVERY_LAST_ENQUEUE_TICKS.clear();
		DISCOVERY_COLUMNS_CACHE.clear();
		DISCOVERY_PROGRESS_BY_CHUNK.clear();
	}

	private static void requestChunkRefresh(MinecraftServer server, long delayTicks) {
		if (server == null || refreshTaskScheduled || !ChunkProcessorManager.hasActiveChunkProcessors()) {
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

	private static boolean canRequeueDirtyChunk(MadokuChunkManager.ProcessorChunkKey chunkKey, Map<MadokuChunkManager.ProcessorChunkKey, Long> lastEnqueueTicks) {
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

	private static List<String> getActiveProcessorIds() {
		return new ArrayList<>(ChunkProcessorManager.getActiveChunkProcessorIdsView());
	}

	private static MadokuChunkManager.ChunkProcessor getProcessor(String processorId) {
		return ChunkProcessorManager.getChunkProcessor(processorId);
	}

	private static final class ChunkDiscoveryProgress {
		private int nextColumnIndex;
		private boolean started;

		private ChunkDiscoveryProgress(int nextColumnIndex, boolean started) {
			this.nextColumnIndex = Math.max(0, Math.min(CHUNK_COLUMN_COUNT - 1, nextColumnIndex));
			this.started = started;
		}

		private static ChunkDiscoveryProgress fresh() {
			return new ChunkDiscoveryProgress(0, false);
		}

		private void reset(int nextColumnIndex) {
			this.nextColumnIndex = Math.max(0, Math.min(CHUNK_COLUMN_COUNT - 1, nextColumnIndex));
		}
	}

	private static final class CachedChunkColumns {
		private final List<MadokuChunkManager.ColumnSample> motionColumns;
		private final List<MadokuChunkManager.ColumnSample> surfaceColumns;

		private CachedChunkColumns(int capacity) {
			int safeCapacity = Math.max(1, capacity);
			List<MadokuChunkManager.ColumnSample> motion = new ArrayList<>(safeCapacity);
			List<MadokuChunkManager.ColumnSample> surface = new ArrayList<>(safeCapacity);
			for (int i = 0; i < safeCapacity; i++) {
				motion.add(new MadokuChunkManager.ColumnSample());
				surface.add(new MadokuChunkManager.ColumnSample());
			}
			this.motionColumns = motion;
			this.surfaceColumns = surface;
		}
	}
}
