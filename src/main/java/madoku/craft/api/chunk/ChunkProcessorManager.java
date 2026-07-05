package madoku.craft.api.chunk;

import madoku.craft.clock.MadokuTicks;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

final class ChunkProcessorManager {
	private static final long PROCESSOR_ROUND_ROBIN_MIN_INTERVAL_TICKS = 1L;
	private static final long PROCESSOR_ROUND_ROBIN_MAX_INTERVAL_TICKS = 20L;
	private static final long DIRTY_REQUEUE_COOLDOWN_TICKS = 20L;
	private static final String PROCESSOR_ROUND_ROBIN_SCHEDULER_OWNER_PREFIX = "madoku_chunks_processor_round_robin_";

	private static final Map<String, ChunkProcessorRuntime> CHUNK_PROCESSORS = new LinkedHashMap<>();
	private static final Set<String> ACTIVE_CHUNK_PROCESSOR_IDS = new LinkedHashSet<>();
	private static final ThreadLocal<Integer> INTERNAL_PROCESSOR_MUTATION_DEPTH = ThreadLocal.withInitial(() -> 0);

	private ChunkProcessorManager() {
	}

	public static void reset() {
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime != null) {
				runtime.resetState();
			}
		}
		clearProcessorRoundRobinAdaptiveState();
		INTERNAL_PROCESSOR_MUTATION_DEPTH.remove();
	}

	public static void registerChunkProcessor(String processorId, MadokuChunkManager.ChunkProcessor processor) {
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
		trackChunkForProcessor(processorId, MadokuChunkManager.normalizeLevelId(level), chunkX, chunkZ);
	}

	public static void trackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null) {
			return;
		}
		trackChunkWithState(runtime, new MadokuChunkManager.ProcessorChunkKey(levelId == null ? "" : levelId, chunkX, chunkZ));
	}

	public static void untrackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		untrackChunkForProcessor(processorId, MadokuChunkManager.normalizeLevelId(level), chunkX, chunkZ);
	}

	public static void untrackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null) {
			return;
		}
		untrackChunkWithState(runtime, new MadokuChunkManager.ProcessorChunkKey(levelId == null ? "" : levelId, chunkX, chunkZ));
	}

	public static boolean isInternalProcessorMutationActive() {
		return INTERNAL_PROCESSOR_MUTATION_DEPTH.get() > 0;
	}

	public static void onWorldPositionChanged(ServerLevel level, int chunkX, int chunkZ) {
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(
			MadokuChunkManager.normalizeLevelId(level),
			chunkX,
			chunkZ
		);
		markTrackedChunkDirtyForAllProcessors(chunkKey);
	}

	public static void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(
			MadokuChunkManager.normalizeLevelId(level),
			chunkX,
			chunkZ
		);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			if (runtime.trackedChunksWithState.contains(chunkKey)) {
				addLoadedTrackedChunk(runtime, chunkKey);
			}
		}
	}

	public static void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(
			MadokuChunkManager.normalizeLevelId(level),
			chunkX,
			chunkZ
		);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			removeLoadedTrackedChunk(runtime, chunkKey);
		}
	}

	public static void refreshTrackedChunks(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null) {
				continue;
			}
			List<MadokuChunkManager.ProcessorChunkKey> trackedChunks = new ArrayList<>(runtime.trackedChunkCycle);
			for (MadokuChunkManager.ProcessorChunkKey chunkKey : trackedChunks) {
				if (chunkKey == null || chunkKey.levelId().isBlank()) {
					continue;
				}
				ServerLevel world = MadokuChunkManager.resolveLevel(server, chunkKey.levelId());
				if (world == null || !runtime.processor.acceptsWorld(world)) {
					removeLoadedTrackedChunk(runtime, chunkKey);
					continue;
				}
				if (MadokuChunkManager.isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
					addLoadedTrackedChunk(runtime, chunkKey);
				} else {
					removeLoadedTrackedChunk(runtime, chunkKey);
				}
			}
		}
	}

	public static boolean hasActiveChunkProcessors() {
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

	public static void clearProcessorRoundRobinAdaptiveState() {
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.id == null || runtime.id.isBlank()) {
				continue;
			}
			SchedulerManagerSystem.clearAdaptiveDelayState(processorRoundRobinAdaptiveOwnerId(runtime.id));
		}
	}

	public static Set<String> getActiveChunkProcessorIdsView() {
		return Collections.unmodifiableSet(ACTIVE_CHUNK_PROCESSOR_IDS);
	}

	public static MadokuChunkManager.ChunkProcessor getChunkProcessor(String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		return runtime == null ? null : runtime.processor;
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

	private static void processOneActiveTrackedChunk(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (runtime == null || runtime.processor == null || server == null) {
			return;
		}
		MadokuChunkManager.ProcessorChunkKey dirtyTrackedChunk = pollNextDirtyTrackedChunk(server, runtime);
		if (dirtyTrackedChunk != null) {
			ServerLevel world = MadokuChunkManager.resolveLevel(server, dirtyTrackedChunk.levelId());
			if (world != null && MadokuChunkManager.isChunkLoaded(world, dirtyTrackedChunk.chunkX(), dirtyTrackedChunk.chunkZ())) {
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
		MadokuChunkManager.ProcessorChunkKey selectedChunk = runtime.loadedTrackedChunkCycle.get(selectedIndex);
		ServerLevel world = MadokuChunkManager.resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && MadokuChunkManager.isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
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
		for (MadokuChunkManager.ProcessorChunkKey chunkKey : runtime.trackedChunkCycle) {
			if (chunkKey == null || chunkKey.levelId().isBlank()) {
				continue;
			}
			ServerLevel world = MadokuChunkManager.resolveLevel(server, chunkKey.levelId());
			if (world == null || !runtime.processor.acceptsWorld(world)) {
				continue;
			}
			if (MadokuChunkManager.isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
				addLoadedTrackedChunk(runtime, chunkKey);
			}
		}
	}

	private static void markTrackedChunkDirtyForAllProcessors(MadokuChunkManager.ProcessorChunkKey chunkKey) {
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

	private static void trackChunkWithState(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (runtime.trackedChunksWithState.add(chunkKey)) {
			runtime.trackedChunkCycle.add(chunkKey);
		}
		if (MadokuChunkManager.isKnownLoadedChunk(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ())) {
			addLoadedTrackedChunk(runtime, chunkKey);
			markTrackedChunkDirty(runtime, chunkKey);
		}
	}

	private static void untrackChunkWithState(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
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

	private static void addLoadedTrackedChunk(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank()) {
			return;
		}
		if (!runtime.loadedTrackedChunkKeys.add(chunkKey)) {
			return;
		}
		runtime.loadedTrackedChunkCycle.add(chunkKey);
		markTrackedChunkDirty(runtime, chunkKey);
	}

	private static void removeLoadedTrackedChunk(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
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

	private static void markTrackedChunkDirty(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
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

	private static void removeTrackedChunkDirty(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null) {
			return;
		}
		if (!runtime.dirtyTrackedChunkKeys.remove(chunkKey)) {
			return;
		}
		runtime.dirtyTrackedChunks.remove(chunkKey);
	}

	private static MadokuChunkManager.ProcessorChunkKey pollNextDirtyTrackedChunk(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (runtime == null) {
			return null;
		}
		while (!runtime.dirtyTrackedChunks.isEmpty()) {
			MadokuChunkManager.ProcessorChunkKey chunkKey = runtime.dirtyTrackedChunks.pollFirst();
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
			ServerLevel world = MadokuChunkManager.resolveLevel(server, chunkKey.levelId());
			if (world == null || !MadokuChunkManager.isChunkLoaded(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
				removeLoadedTrackedChunk(runtime, chunkKey);
				continue;
			}
			return chunkKey;
		}
		return null;
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

	private static String normalizeProcessorId(String processorId) {
		return processorId == null ? "" : processorId.trim().toLowerCase();
	}

	private static final class ChunkProcessorRuntime {
		private final String id;
		private final MadokuChunkManager.ChunkProcessor processor;
		private final Set<MadokuChunkManager.ProcessorChunkKey> trackedChunksWithState = new LinkedHashSet<>();
		private final List<MadokuChunkManager.ProcessorChunkKey> trackedChunkCycle = new ArrayList<>();
		private final Set<MadokuChunkManager.ProcessorChunkKey> loadedTrackedChunkKeys = new LinkedHashSet<>();
		private final List<MadokuChunkManager.ProcessorChunkKey> loadedTrackedChunkCycle = new ArrayList<>();
		private final Set<MadokuChunkManager.ProcessorChunkKey> dirtyTrackedChunkKeys = new LinkedHashSet<>();
		private final Deque<MadokuChunkManager.ProcessorChunkKey> dirtyTrackedChunks = new ArrayDeque<>();
		private final Map<MadokuChunkManager.ProcessorChunkKey, Long> dirtyTrackedLastEnqueueTicks = new LinkedHashMap<>();
		private int activeChunkProcessCursor = 0;
		private long nextRoundRobinProcessGameplayTick = Long.MIN_VALUE;

		private ChunkProcessorRuntime(String id, MadokuChunkManager.ChunkProcessor processor) {
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
}
