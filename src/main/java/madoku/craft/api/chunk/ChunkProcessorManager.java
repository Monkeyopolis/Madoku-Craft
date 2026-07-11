package madoku.craft.api.chunk;

import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.function.Consumer;

final class ChunkProcessorManager {
	private static final long PROCESSOR_ROUND_ROBIN_MIN_INTERVAL_TICKS = 1L;
	private static final long PROCESSOR_ROUND_ROBIN_MAX_INTERVAL_TICKS = 20L;
	private static final String PROCESSOR_ROUND_ROBIN_SCHEDULER_OWNER_PREFIX = "madoku_chunks_processor_round_robin_";
	private static final String DEBUG_MAIN_SYSTEM = "chunk";
	private static final String DEBUG_SUB_SYSTEM = "chunk-processor-manager";

	private static final Map<String, ChunkProcessorRuntime> CHUNK_PROCESSORS = new LinkedHashMap<>();
	private static final Set<String> ACTIVE_CHUNK_PROCESSOR_IDS = new LinkedHashSet<>();
	private static final Set<MadokuChunkManager.ProcessorChunkKey> DISCOVERED_CHUNK_KEYS = new LinkedHashSet<>();

	private ChunkProcessorManager() {
	}

	public static void reset() {
		final int previousDiscoveredChunkKeys = DISCOVERED_CHUNK_KEYS.size();
		final int previousActiveProcessorCount = ACTIVE_CHUNK_PROCESSOR_IDS.size();
		DISCOVERED_CHUNK_KEYS.clear();
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime != null) {
				runtime.resetState();
			}
		}
		clearProcessorRoundRobinAdaptiveState();
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("reset")
			.field("processors", CHUNK_PROCESSORS.size())
			.field("active", previousActiveProcessorCount)
			.field("discovered", previousDiscoveredChunkKeys));
	}

	public static void registerChunkProcessor(String processorId, MadokuChunkManager.ChunkProcessor processor) {
		String normalizedId = normalizeProcessorId(processorId);
		if (normalizedId.isBlank() || processor == null) {
			return;
		}
		CHUNK_PROCESSORS.put(normalizedId, new ChunkProcessorRuntime(normalizedId, processor));
		ACTIVE_CHUNK_PROCESSOR_IDS.add(normalizedId);
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("register")
			.field("processor-id", normalizedId)
			.field("processors", CHUNK_PROCESSORS.size())
			.field("active", ACTIVE_CHUNK_PROCESSOR_IDS.size()));
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
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("set-active")
			.field("processor-id", normalizedId)
			.field("active", active)
			.field("active-count", ACTIVE_CHUNK_PROCESSOR_IDS.size()));
	}

	public static void resetChunkProcessor(String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime != null) {
			runtime.resetState();
			emitChunkDebug("chunk.processor", builder -> builder
				.subject("reset-processor")
				.field("processor-id", runtime.id));
		}
	}

	public static void onChunkDiscoveryFinished(ServerLevel level, int chunkX, int chunkZ) {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(
			MadokuChunkManager.normalizeLevelId(level),
			chunkX,
			chunkZ
		);
		if (chunkKey.levelId().isBlank()) {
			return;
		}
		DISCOVERED_CHUNK_KEYS.add(chunkKey);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			if (!runtime.trackedChunksWithState.contains(chunkKey)) {
				continue;
			}
			if (MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)) {
				addLoadedTrackedChunk(runtime, chunkKey);
			}
		}
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("discovery-finished")
			.field("level-id", chunkKey.levelId())
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
	}

	public static void runChunkProcessorProcessingStep(MinecraftServer server, String processorId) {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		String normalizedId = normalizeProcessorId(processorId);
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizedId);
		if (runtime == null || runtime.processor == null || server == null) {
			return;
		}
		if (!ACTIVE_CHUNK_PROCESSOR_IDS.contains(normalizedId)) {
			return;
		}
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("run-step")
			.field("processor-id", normalizedId));
		processActiveTrackedChunks(server, runtime);
	}

	public static void trackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		trackChunkForProcessor(processorId, MadokuChunkManager.normalizeLevelId(level), chunkX, chunkZ);
	}

	public static void trackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null) {
			return;
		}
		trackChunkWithState(runtime, new MadokuChunkManager.ProcessorChunkKey(levelId == null ? "" : levelId, chunkX, chunkZ));
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("track")
			.field("processor-id", normalizeProcessorId(processorId))
			.field("level-id", levelId == null ? "" : levelId)
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
	}

	public static void untrackChunkForProcessor(String processorId, ServerLevel level, int chunkX, int chunkZ) {
		untrackChunkForProcessor(processorId, MadokuChunkManager.normalizeLevelId(level), chunkX, chunkZ);
	}

	public static void untrackChunkForProcessor(String processorId, String levelId, int chunkX, int chunkZ) {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		if (runtime == null) {
			return;
		}
		untrackChunkWithState(runtime, new MadokuChunkManager.ProcessorChunkKey(levelId == null ? "" : levelId, chunkX, chunkZ));
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("untrack")
			.field("processor-id", normalizeProcessorId(processorId))
			.field("level-id", levelId == null ? "" : levelId)
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
	}

	public static void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(
			MadokuChunkManager.normalizeLevelId(level),
			chunkX,
			chunkZ
		);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			if (DISCOVERED_CHUNK_KEYS.contains(chunkKey) && runtime.trackedChunksWithState.contains(chunkKey)) {
				addLoadedTrackedChunk(runtime, chunkKey);
			}
		}
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("chunk-loaded")
			.field("level-id", chunkKey.levelId())
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
	}

	public static void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		MadokuChunkManager.ProcessorChunkKey chunkKey = new MadokuChunkManager.ProcessorChunkKey(
			MadokuChunkManager.normalizeLevelId(level),
			chunkX,
			chunkZ
		);
		for (ChunkProcessorRuntime runtime : CHUNK_PROCESSORS.values()) {
			if (runtime == null || runtime.processor == null || !runtime.processor.acceptsWorld(level)) {
				continue;
			}
			DISCOVERED_CHUNK_KEYS.remove(chunkKey);
			removeLoadedTrackedChunk(runtime, chunkKey);
		}
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("chunk-unloaded")
			.field("level-id", chunkKey.levelId())
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
	}

	public static boolean hasActiveChunkProcessors() {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return false;
		}
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
			MadokuSchedulerManager.clearAdaptiveDelayState(processorRoundRobinAdaptiveOwnerId(runtime.id));
		}
	}

	private static void emitChunkDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
		String entry = MadokuDebugManager.resolveCallerMethodName(1);
		if (!MadokuDebugManager.shouldEmit(DEBUG_MAIN_SYSTEM, DEBUG_SUB_SYSTEM, entry)) {
			return;
		}
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(metricId, DEBUG_MAIN_SYSTEM, DEBUG_SUB_SYSTEM, entry)
			.side(MadokuDebugManager.Side.SERVER);
		if (customizer != null) {
			customizer.accept(builder);
		}
		builder.log();
	}

	public static Set<String> getActiveChunkProcessorIdsView() {
		if (!ChunkConfigManager.isChunkProcessorEnabled()) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(ACTIVE_CHUNK_PROCESSOR_IDS);
	}

	public static MadokuChunkManager.ChunkProcessor getChunkProcessor(String processorId) {
		ChunkProcessorRuntime runtime = CHUNK_PROCESSORS.get(normalizeProcessorId(processorId));
		return runtime == null ? null : runtime.processor;
	}

	static void processActiveTrackedChunks(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (runtime == null || runtime.processor == null || server == null || !ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
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

		long intervalTicks = resolveRoundRobinProcessorIntervalTicks(server, runtime);
		int selectedIndex = Math.floorMod(runtime.activeChunkProcessCursor, runtime.loadedTrackedChunkCycle.size());
		MadokuChunkManager.ProcessorChunkKey selectedChunk = runtime.loadedTrackedChunkCycle.get(selectedIndex);
		emitChunkDebug("chunk.processor", builder -> builder
			.subject("process")
			.field("processor-id", runtime.id)
			.field("level-id", selectedChunk.levelId())
			.field("chunk-x", selectedChunk.chunkX())
			.field("chunk-z", selectedChunk.chunkZ())
			.field("loaded-tracked", runtime.loadedTrackedChunkCycle.size()));
		ServerLevel world = MadokuChunkManager.resolveLevel(server, selectedChunk.levelId());
		boolean loaded = world != null && MadokuChunkManager.isChunkLoaded(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		if (!loaded) {
			removeLoadedTrackedChunk(runtime, selectedChunk);
			if (!runtime.loadedTrackedChunkCycle.isEmpty()) {
				scheduleNextRoundRobinProcessorStep(server, runtime, intervalTicks);
			}
			return;
		}

		runtime.processor.processTrackedChunk(world, selectedChunk.chunkX(), selectedChunk.chunkZ());
		boolean completedCycle = selectedIndex + 1 >= runtime.loadedTrackedChunkCycle.size();
		runtime.activeChunkProcessCursor = completedCycle ? 0 : selectedIndex + 1;
		scheduleNextRoundRobinProcessorStep(server, runtime, intervalTicks);
	}

	private static void recoverLoadedTrackedChunks(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null || runtime.trackedChunkCycle.isEmpty() || !ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		for (MadokuChunkManager.ProcessorChunkKey chunkKey : runtime.trackedChunkCycle) {
			if (chunkKey == null || chunkKey.levelId().isBlank()) {
				continue;
			}
			if (!DISCOVERED_CHUNK_KEYS.contains(chunkKey)) {
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

	private static void trackChunkWithState(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank() || !ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		if (runtime.trackedChunksWithState.add(chunkKey)) {
			runtime.trackedChunkCycle.add(chunkKey);
		}
		if (DISCOVERED_CHUNK_KEYS.contains(chunkKey)
			&& MadokuChunkManager.isKnownLoadedChunk(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ())) {
			addLoadedTrackedChunk(runtime, chunkKey);
		}
	}

	private static void untrackChunkWithState(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank() || !ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		if (!runtime.trackedChunksWithState.remove(chunkKey)) {
			return;
		}
		runtime.trackedChunkCycle.remove(chunkKey);
		removeLoadedTrackedChunk(runtime, chunkKey);
	}

	private static void addLoadedTrackedChunk(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || chunkKey.levelId().isBlank() || !ChunkConfigManager.isChunkProcessorEnabled()) {
			return;
		}
		if (!runtime.loadedTrackedChunkKeys.add(chunkKey)) {
			return;
		}
		runtime.loadedTrackedChunkCycle.add(chunkKey);
	}

	private static void removeLoadedTrackedChunk(ChunkProcessorRuntime runtime, MadokuChunkManager.ProcessorChunkKey chunkKey) {
		if (runtime == null || chunkKey == null || !ChunkConfigManager.isChunkProcessorEnabled()) {
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

	private static boolean isRoundRobinProcessorStepDue(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null) {
			return false;
		}
		long currentTick = MadokuTimeManager.getGameplayTicks();
		if (runtime.nextRoundRobinProcessGameplayTick == Long.MIN_VALUE) {
			return true;
		}
		if (currentTick < runtime.nextRoundRobinProcessGameplayTick) {
			return false;
		}
		return true;
	}

	private static void scheduleNextRoundRobinProcessorStep(MinecraftServer server, ChunkProcessorRuntime runtime, long intervalTicks) {
		if (server == null || runtime == null) {
			return;
		}
		long currentTick = MadokuTimeManager.getGameplayTicks();
		runtime.nextRoundRobinProcessGameplayTick = currentTick + Math.max(1L, intervalTicks);
	}

	private static long resolveRoundRobinProcessorIntervalTicks(MinecraftServer server, ChunkProcessorRuntime runtime) {
		if (server == null || runtime == null) {
			return PROCESSOR_ROUND_ROBIN_MIN_INTERVAL_TICKS;
		}
		return MadokuSchedulerManager.resolveAdaptiveDelayTicks(
			server,
			processorRoundRobinAdaptiveOwnerId(runtime.id),
			PROCESSOR_ROUND_ROBIN_MIN_INTERVAL_TICKS,
			PROCESSOR_ROUND_ROBIN_MAX_INTERVAL_TICKS
		);
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
			activeChunkProcessCursor = 0;
			nextRoundRobinProcessGameplayTick = Long.MIN_VALUE;
		}
	}
}
