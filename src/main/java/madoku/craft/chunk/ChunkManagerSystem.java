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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

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
	private static final long CHUNK_REFRESH_INTERVAL_TICKS = 5L;
	private static final String FIELD_LEVELS = "levels";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_CHUNKS = "chunks";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_STATUS = "status";

	private static final Map<String, Map<Long, FullChunkStatus>> CHUNK_STATUSES_BY_LEVEL = new LinkedHashMap<>();
	private static final List<ChunkLifecycleListener> CHUNK_LIFECYCLE_LISTENERS = new CopyOnWriteArrayList<>();
	private static final Map<String, ChunkProcessorRuntime> CHUNK_PROCESSORS = new LinkedHashMap<>();

	private static volatile String chunkSchedulerId = "";
	private static volatile boolean refreshTaskScheduled = false;
	private static volatile boolean serverStopping = false;
	private static volatile boolean dirty = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;

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

		void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ);

		void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ);
	}

	public static void initialize() {
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_CHUNK_REFRESH, ChunkManagerSystem::runChunkRefreshTask);
		ServerChunkEvents.CHUNK_LOAD.register(ChunkManagerSystem::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(ChunkManagerSystem::onChunkUnload);
	}

	public static void reset() {
		CHUNK_STATUSES_BY_LEVEL.clear();
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
		CHUNK_PROCESSORS.put(normalizedId, new ChunkProcessorRuntime(processor));
	}

	public static void resetChunkProcessor(String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime != null) {
			runtime.resetState();
		}
	}

	public static void runChunkProcessorDiscoveryStep(MinecraftServer server, String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null || runtime.processor == null || server == null) {
			return;
		}
		discoverOneLoadedChunk(server, runtime);
	}

	public static void runChunkProcessorProcessingStep(MinecraftServer server, String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null || runtime.processor == null || server == null) {
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
		serverStopping = false;
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		dirty = false;
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}

		serverStopping = false;
		seedLoadedChunks(server);
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

	public static List<Long> getLoadedChunkPositions(ServerLevel level) {
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
		putChunkStatus(levelId(level), chunkPos.pack(), FullChunkStatus.FULL);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			runtime.discoveryChunksSeeded = true;
			addDiscoveryLoadedChunk(runtime, new ProcessorChunkKey(levelId(level), chunkPos.x(), chunkPos.z()));
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
		removeChunk(levelId(level), chunkPos.pack());
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			runtime.discoveryChunksSeeded = true;
			removeDiscoveryLoadedChunk(runtime, new ProcessorChunkKey(levelId(level), chunkPos.x(), chunkPos.z()));
		}
		notifyChunkUnloaded(level, chunkPos.x(), chunkPos.z());
	}

	private static void runChunkRefreshTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			chunkSchedulerId = context.getSchedulerId();
		}
		refreshTaskScheduled = false;
		if (server == null) {
			return;
		}

		refreshTrackedChunks(server);
		requestChunkRefresh(server, CHUNK_REFRESH_INTERVAL_TICKS);
	}

	private static void seedLoadedChunks(MinecraftServer server) {
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
				}
			});
		}
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
		if (server == null || refreshTaskScheduled) {
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

	private static void discoverOneLoadedChunk(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null || runtime.processor == null) {
			return;
		}
		seedDiscoveryChunksIfNeeded(server, runtime);
		if (runtime.discoveryLoadedChunks.isEmpty()) {
			runtime.chunkScanCursor = 0;
			return;
		}

		int selectedIndex = Math.floorMod(runtime.chunkScanCursor, runtime.discoveryLoadedChunks.size());
		ProcessorChunkKey selectedChunk = runtime.discoveryLoadedChunks.get(selectedIndex);
		ServerLevel world = resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		if (loaded && runtime.processor.acceptsWorld(world)) {
			runtime.processor.discoverLoadedChunk(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		} else {
			removeDiscoveryLoadedChunk(runtime, selectedChunk);
			if (runtime.discoveryLoadedChunks.isEmpty()) {
				runtime.chunkScanCursor = 0;
			} else {
				runtime.chunkScanCursor = Math.min(runtime.chunkScanCursor, runtime.discoveryLoadedChunks.size() - 1);
			}
			return;
		}

		boolean completedCycle = selectedIndex + 1 >= runtime.discoveryLoadedChunks.size();
		runtime.chunkScanCursor = completedCycle ? 0 : selectedIndex + 1;
	}

	private static void seedDiscoveryChunksIfNeeded(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null || runtime.processor == null || runtime.discoveryChunksSeeded) {
			return;
		}
		runtime.discoveryChunksSeeded = true;
		for (ServerLevel world : server.getAllLevels()) {
			if (world == null || !runtime.processor.acceptsWorld(world)) {
				continue;
			}
			for (Long packedChunk : getLoadedChunkPositions(world)) {
				if (packedChunk == null) {
					continue;
				}
				addDiscoveryLoadedChunk(runtime, new ProcessorChunkKey(levelId(world), unpackChunkX(packedChunk), unpackChunkZ(packedChunk)));
			}
		}
	}

	private static void addDiscoveryLoadedChunk(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!runtime.discoveryLoadedChunkKeys.add(chunkKey)) {
			return;
		}
		runtime.discoveryLoadedChunks.add(chunkKey);
		if (runtime.trackedChunksWithState.contains(chunkKey)) {
			addLoadedTrackedChunk(runtime, chunkKey);
		}
	}

	private static void removeDiscoveryLoadedChunk(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null) {
			return;
		}
		if (!runtime.discoveryLoadedChunkKeys.remove(chunkKey)) {
			removeLoadedTrackedChunk(runtime, chunkKey);
			return;
		}
		runtime.discoveryLoadedChunks.remove(chunkKey);
		removeLoadedTrackedChunk(runtime, chunkKey);
		if (runtime.chunkScanCursor >= runtime.discoveryLoadedChunks.size()) {
			runtime.chunkScanCursor = 0;
		}
	}

	private static void trackChunkWithState(ChunkProcessorRuntime runtime, ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (runtime.trackedChunksWithState.add(chunkKey)) {
			runtime.trackedChunkCycle.add(chunkKey);
		}
		if (runtime.discoveryLoadedChunkKeys.contains(chunkKey)) {
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

	private static String normalizeProcessorId(String processorId) {
		return processorId == null ? "" : processorId.trim().toLowerCase();
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
		private final ChunkProcessor processor;
		private final List<ProcessorChunkKey> discoveryLoadedChunks = new ArrayList<>();
		private final Set<ProcessorChunkKey> discoveryLoadedChunkKeys = new LinkedHashSet<>();
		private final Set<ProcessorChunkKey> trackedChunksWithState = new LinkedHashSet<>();
		private final List<ProcessorChunkKey> trackedChunkCycle = new ArrayList<>();
		private final Set<ProcessorChunkKey> loadedTrackedChunkKeys = new LinkedHashSet<>();
		private final List<ProcessorChunkKey> loadedTrackedChunkCycle = new ArrayList<>();
		private int chunkScanCursor = 0;
		private int activeChunkProcessCursor = 0;
		private boolean discoveryChunksSeeded = false;

		private ChunkProcessorRuntime(ChunkProcessor processor) {
			this.processor = processor;
		}

		private void resetState() {
			discoveryLoadedChunks.clear();
			discoveryLoadedChunkKeys.clear();
			trackedChunksWithState.clear();
			trackedChunkCycle.clear();
			loadedTrackedChunkKeys.clear();
			loadedTrackedChunkCycle.clear();
			chunkScanCursor = 0;
			activeChunkProcessCursor = 0;
			discoveryChunksSeeded = false;
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
