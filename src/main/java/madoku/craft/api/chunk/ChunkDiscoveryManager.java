package madoku.craft.api.chunk;

import com.google.gson.JsonObject;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.data.DataWorldChunkManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChunkDiscoveryManager {
	private static final String CHUNK_SCHEDULER_OWNER_ID = "madoku_chunks";
	private static final String TASK_TYPE_CHUNK_REFRESH = "chunk_refresh";
	private static final long CHUNK_REFRESH_MIN_INTERVAL_TICKS = 1L;
	private static final long CHUNK_REFRESH_MAX_INTERVAL_TICKS = 20L;
	private static final int CHUNK_COLUMN_COUNT = 16 * 16;

	private static final List<MadokuChunkManager.ProcessorChunkKey> DISCOVERY_LOADED_CHUNKS = new ArrayList<>();
	private static final Set<MadokuChunkManager.ProcessorChunkKey> DISCOVERY_LOADED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Map<MadokuChunkManager.ProcessorChunkKey, CachedChunkColumns> DISCOVERY_COLUMNS_CACHE = new LinkedHashMap<>();
	private static final Map<MadokuChunkManager.ProcessorChunkKey, ChunkDiscoveryProgress> DISCOVERY_PROGRESS_BY_CHUNK = new LinkedHashMap<>();
	private static final MadokuChunkManager.ChunkDiscoverySnapshot REUSABLE_DISCOVERY_SNAPSHOT = MadokuChunkManager.ChunkDiscoverySnapshot.reusable(CHUNK_COLUMN_COUNT);

	private static volatile String chunkSchedulerId = "";
	private static volatile boolean refreshTaskScheduled = false;
	private static volatile boolean serverStopping = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile int discoveryChunkScanCursor = 0;
	private static volatile boolean discoveryChunksSeeded = false;

	private ChunkDiscoveryManager() {
	}

	public static void initialize() {
		MadokuSchedulerManager.registerTaskHandler(TASK_TYPE_CHUNK_REFRESH, ChunkDiscoveryManager::runChunkRefreshTask);
		ServerChunkEvents.CHUNK_LOAD.register(ChunkDiscoveryManager::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(ChunkDiscoveryManager::onChunkUnload);
	}

	public static void reset() {
		clearRuntimeState();
		chunkSchedulerId = "";
		refreshTaskScheduled = false;
		serverStopping = false;
		lastAutosaveBucket = Long.MIN_VALUE;
		MadokuSchedulerManager.clearAdaptiveDelayState(CHUNK_SCHEDULER_OWNER_ID);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		clearRuntimeState();
		if (!ChunkConfigManager.isChunkDiscoveryEnabled()) {
			serverStopping = false;
			lastAutosaveBucket = Long.MIN_VALUE;
			return;
		}
		serverStopping = false;
		long autoSaveIntervalTicks = DataWorldChunkManager.getAutoSaveIntervalTicks();
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		MadokuSchedulerManager.clearAdaptiveDelayState(CHUNK_SCHEDULER_OWNER_ID);
		if (!ChunkConfigManager.isChunkDiscoveryEnabled()) {
			chunkSchedulerId = "";
			refreshTaskScheduled = false;
			serverStopping = false;
			discoveryChunkScanCursor = 0;
			discoveryChunksSeeded = false;
			return;
		}
		serverStopping = false;
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
		seedLoadedChunks(server);
		if (!ChunkProcessorManager.hasActiveChunkProcessors()) {
			chunkSchedulerId = "";
			refreshTaskScheduled = false;
			return;
		}
		chunkSchedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(CHUNK_SCHEDULER_OWNER_ID)
		);
		refreshTaskScheduled = MadokuSchedulerManager.hasQueuedTask(chunkSchedulerId, TASK_TYPE_CHUNK_REFRESH);
		if (!refreshTaskScheduled) {
			requestChunkRefresh(server, resolveChunkRefreshInterval(server));
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !ChunkConfigManager.isChunkDiscoveryEnabled()) {
			return;
		}
		long autoSaveIntervalTicks = DataWorldChunkManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket == lastAutosaveBucket) {
			return;
		}
		lastAutosaveBucket = bucket;
		savePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		if (server == null || !ChunkConfigManager.isChunkDiscoveryEnabled()) {
			return;
		}
		serverStopping = true;
		savePersistedData(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || !ChunkConfigManager.isChunkDiscoveryEnabled()) {
			return;
		}
	}

	static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level == null || chunk == null) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		if (!ChunkConfigManager.isChunkDiscoveryEnabled()) {
			MadokuChunkManager.notifyChunkLoaded(level, chunkPos.x(), chunkPos.z());
			return;
		}
		String loadedLevelId = MadokuChunkManager.levelId(level);
		MadokuChunkManager.putChunkStatus(loadedLevelId, chunkPos.pack(), FullChunkStatus.FULL);
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(loadedLevelId, chunkPos.x(), chunkPos.z());
		addSharedDiscoveryLoadedChunk(chunkKey);
		resetDiscoveryProgress(chunkKey, 0);
		ChunkProcessorManager.onChunkLoaded(level, chunkPos.x(), chunkPos.z());
		MadokuChunkManager.notifyChunkLoaded(level, chunkPos.x(), chunkPos.z());
	}

	static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		if (serverStopping) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		if (!ChunkConfigManager.isChunkDiscoveryEnabled()) {
			MadokuChunkManager.notifyChunkUnloaded(level, chunkPos.x(), chunkPos.z());
			return;
		}
		String unloadedLevelId = MadokuChunkManager.levelId(level);
		MadokuChunkManager.removeChunk(unloadedLevelId, chunkPos.pack());
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(unloadedLevelId, chunkPos.x(), chunkPos.z());
		removeCachedChunkColumns(chunkKey);
		removeDiscoveryProgress(chunkKey);
		removeSharedDiscoveryLoadedChunk(chunkKey);
		ChunkProcessorManager.onChunkUnloaded(level, chunkPos.x(), chunkPos.z());
		MadokuChunkManager.notifyChunkUnloaded(level, chunkPos.x(), chunkPos.z());
	}

	static void runChunkRefreshTask(MinecraftServer server, MadokuSchedulerManager.TaskContext context, JsonObject payload) {
		if (context != null) {
			chunkSchedulerId = context.getSchedulerId();
		}
		refreshTaskScheduled = false;
		if (server == null || !ChunkConfigManager.isChunkDiscoveryEnabled() || !ChunkProcessorManager.hasActiveChunkProcessors()) {
			return;
		}
		long refreshIntervalTicks = resolveChunkRefreshInterval(server);
		int columnsPerRefresh = ChunkConfigManager.resolveAdaptiveChunkWorkUnits(refreshIntervalTicks);
		runSharedChunkDiscoverySteps(server, columnsPerRefresh);
		requestChunkRefresh(server, refreshIntervalTicks);
	}

	private static void seedLoadedChunks(MinecraftServer server) {
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		DISCOVERY_COLUMNS_CACHE.clear();
		DISCOVERY_PROGRESS_BY_CHUNK.clear();
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
				}
			});
		}
		discoveryChunksSeeded = true;
	}

	private static boolean runSharedChunkDiscoverySteps(MinecraftServer server, int columnsPerRefresh) {
		int safeSteps = Math.max(1, columnsPerRefresh);
		boolean completedRefreshCycle = false;
		for (int i = 0; i < safeSteps; i++) {
			completedRefreshCycle |= runSharedChunkDiscoveryStep(server);
			if (completedRefreshCycle) {
				break;
			}
		}
		return completedRefreshCycle;
	}

	private static boolean runSharedChunkDiscoveryStep(MinecraftServer server) {
		if (server == null || !ChunkConfigManager.isChunkDiscoveryEnabled()) {
			return false;
		}
		seedSharedDiscoveryChunksIfNeeded(server);
		if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			discoveryChunkScanCursor = 0;
			return false;
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
			return false;
		}
		boolean completedChunkCycle = runChunkDiscoveryCallbacks(server, selectedChunk);
		if (!completedChunkCycle) {
			return false;
		}
		boolean completedRefreshCycle = selectedIndex + 1 >= DISCOVERY_LOADED_CHUNKS.size();
		discoveryChunkScanCursor = completedRefreshCycle ? 0 : selectedIndex + 1;
		return completedRefreshCycle;
	}

	private static void seedSharedDiscoveryChunksIfNeeded(MinecraftServer server) {
		if (server == null || discoveryChunksSeeded || !ChunkConfigManager.isChunkDiscoveryEnabled()) {
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

	private static boolean runChunkDiscoveryCallbacks(MinecraftServer server, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (server == null || chunkKey == null || chunkKey.levelId().isBlank() || !ChunkConfigManager.isChunkDiscoveryEnabled()) {
			return false;
		}
		ServerLevel world = MadokuChunkManager.resolveLevel(server, chunkKey.levelId());
		if (world == null || !MadokuChunkManager.isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
			removeCachedChunkColumns(chunkKey);
			removeSharedDiscoveryLoadedChunk(chunkKey);
			return false;
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
			return false;
		}

		ChunkDiscoveryProgress progress = getOrCreateDiscoveryProgress(chunkKey);
		if (progress == null) {
			return false;
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
			ChunkProcessorManager.onChunkDiscoveryFinished(world, chunkKey.chunkX(), chunkKey.chunkZ());
			progress.started = false;
			progress.reset(0);
			return true;
		}
		progress.reset(columnIndex + 1);
		return false;
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
		DISCOVERY_COLUMNS_CACHE.clear();
		DISCOVERY_PROGRESS_BY_CHUNK.clear();
		discoveryChunkScanCursor = 0;
		discoveryChunksSeeded = false;
	}


	private static void requestChunkRefresh(MinecraftServer server, long delayTicks) {
		if (server == null
			|| refreshTaskScheduled
			|| !ChunkConfigManager.isChunkDiscoveryEnabled()
			|| !ChunkProcessorManager.hasActiveChunkProcessors()) {
			return;
		}
		String schedulerId = ensureChunkSchedulerExists();
		if (enqueueChunkRefresh(schedulerId, delayTicks)) {
			refreshTaskScheduled = true;
			return;
		}
		chunkSchedulerId = MadokuSchedulerManager.createOrGetScheduler(
			MadokuSchedulerManager.SchedulerBinding.global(CHUNK_SCHEDULER_OWNER_ID)
		);
		if (enqueueChunkRefresh(chunkSchedulerId, delayTicks)) {
			refreshTaskScheduled = true;
		}
	}

	private static long resolveChunkRefreshInterval(MinecraftServer server) {
		return MadokuSchedulerManager.resolveAdaptiveDelayTicks(
			server,
			CHUNK_SCHEDULER_OWNER_ID,
			CHUNK_REFRESH_MIN_INTERVAL_TICKS,
			CHUNK_REFRESH_MAX_INTERVAL_TICKS
		);
	}

	private static String ensureChunkSchedulerExists() {
		if (chunkSchedulerId == null || chunkSchedulerId.isBlank()) {
			chunkSchedulerId = MadokuSchedulerManager.createOrGetScheduler(
				MadokuSchedulerManager.SchedulerBinding.global(CHUNK_SCHEDULER_OWNER_ID)
			);
		}
		return chunkSchedulerId;
	}

	private static boolean enqueueChunkRefresh(String schedulerId, long delayTicks) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		MadokuSchedulerManager.EnqueueStatus status = MadokuSchedulerManager.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_CHUNK_REFRESH,
			new JsonObject(),
			MadokuSchedulerManager.TickDomain.GAMEPLAY
		);
		return status == MadokuSchedulerManager.EnqueueStatus.ACCEPTED
			|| status == MadokuSchedulerManager.EnqueueStatus.QUEUE_FULL;
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
