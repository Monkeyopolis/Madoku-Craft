package madoku.craft.ecosystem;

import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class EcosystemNaturalErosionManager {
	public static final String SCHEDULER_OWNER_ID = "ecosystem_erosion_process_gameplay";
	public static final String TASK_TYPE = "ecosystem_erosion_process_gameplay_tick";
	public static final String CHUNK_PROCESSOR_ID = "ecosystem_natural_erosion";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalErosionManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "natural-erosion";
	private static final String DEBUG_MAIN_SYSTEM = "ecosystem";
	private static final String DEBUG_SUB_SYSTEM = "ecosystem-natural-erosion-manager";
	private static final long MIN_INTERVAL_TICKS = 1L;
	private static final long MAX_INTERVAL_TICKS = 20L;
	private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[] {
		Direction.NORTH,
		Direction.SOUTH,
		Direction.EAST,
		Direction.WEST
	};

	private static volatile NaturalErosionConfigManager.Settings settings = NaturalErosionConfigManager.defaults();
	private static volatile String schedulerId = "";
	private static volatile boolean taskScheduled = false;

	private static final MadokuChunkManager.ChunkProcessor CHUNK_PROCESSOR = new MadokuChunkManager.ChunkProcessor() {
		@Override
		public boolean requiresSurfaceColumns() {
			return true;
		}

		@Override
		public void beginLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
			MadokuEcosystemManager.beginUnifiedDiscoveryForChunk(level, chunkX, chunkZ);
		}

		@Override
		public void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ, MadokuChunkManager.ChunkDiscoverySnapshot snapshot) {
			MadokuEcosystemManager.runUnifiedDiscoveryForChunk(level, chunkX, chunkZ, snapshot);
		}

		@Override
		public void finishLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
			MadokuEcosystemManager.finishUnifiedDiscoveryForChunk(level, chunkX, chunkZ);
		}

		@Override
		public void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
			if (level == null || !MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)) {
				return;
			}
			processChunk(level, chunkX, chunkZ, MadokuTime.getCurrentAbsoluteDayTime(level));
		}
	};

	private EcosystemNaturalErosionManager() {
	}

	public static void initialize() {
		loadConfig();
		MadokuChunkManager.registerChunkProcessor(CHUNK_PROCESSOR_ID, CHUNK_PROCESSOR);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE, EcosystemNaturalErosionManager::runTask);
		emitErosionDebug("ecosystem.natural_erosion.lifecycle", builder -> builder
			.subject("initialize")
			.field("config-folder", CONFIG_FOLDER_NAME)
			.field("config-file", CONFIG_FILE_NAME)
			.field("scheduler-owner", SCHEDULER_OWNER_ID)
			.field("enabled", isEnabled()));
	}

	public static void reset() {
		String previousSchedulerId = schedulerId;
		schedulerId = "";
		taskScheduled = false;
		MadokuChunkManager.resetChunkProcessor(CHUNK_PROCESSOR_ID);
		emitErosionDebug("ecosystem.natural_erosion.lifecycle", builder -> builder
			.subject("reset")
			.field("scheduler-id", previousSchedulerId)
			.field("task-scheduled", false));
	}

	public static NaturalErosionConfigManager.Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return MadokuEcosystemManager.isEnabled() && settings.isEnabled();
	}

	public static void syncChunkProcessorActivation() {
		MadokuChunkManager.setChunkProcessorActive(CHUNK_PROCESSOR_ID, isEnabled());
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		SchedulerManagerSystem.clearAdaptiveDelayState(SCHEDULER_OWNER_ID);
		MadokuChunkManager.resetChunkProcessor(CHUNK_PROCESSOR_ID);
		if (!isEnabled()) {
			clearSchedulerState();
			return;
		}
		clearSchedulerState();
		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(SCHEDULER_OWNER_ID)
		);
		SchedulerManagerSystem.clearQueuedRequests(schedulerId);
		requestProcessing(server, 1L);
		emitErosionDebug("ecosystem.natural_erosion.lifecycle", builder -> builder
			.subject("server-started")
			.field("scheduler-id", schedulerId)
			.field("task-scheduled", taskScheduled)
			.field("enabled", isEnabled()));
	}

	public static void onServerStopping(MinecraftServer server) {
		if (server == null) {
			return;
		}
		SchedulerManagerSystem.clearAdaptiveDelayState(SCHEDULER_OWNER_ID);
		emitErosionDebug("ecosystem.natural_erosion.lifecycle", builder -> builder
			.subject("server-stopping")
			.field("scheduler-id", schedulerId)
			.field("task-scheduled", taskScheduled));
		clearSchedulerState();
	}

	public static MadokuChunkManager.ChunkProcessor getChunkProcessor() {
		return CHUNK_PROCESSOR;
	}

	public static void processChunk(ServerLevel level, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		emitErosionDebug("ecosystem.natural_erosion.process_chunk", builder -> builder
			.subject("process-chunk")
			.field("level-id", MadokuEcosystemManager.levelId(level))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("day-time", currentAbsoluteDayTime));
		EcosystemNaturalGrowthManager.processDirtInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime, "wet");
	}

	static void discoverTrackablesInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuChunkManager.ChunkDiscoverySnapshot snapshot,
		MadokuEcosystemManager.ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || snapshot == null || accumulator == null || !MadokuEcosystemManager.isNaturalErosionEnabled()) {
			return;
		}

		for (MadokuChunkManager.ColumnSample column : snapshot.motionColumns()) {
			if (column == null) {
				continue;
			}
			for (int depth = 0; depth <= 2; depth++) {
				if (!column.hasDepth(depth)) {
					continue;
				}
				BlockPos pos = BlockPos.of(column.posAtDepth(depth));
				BlockState state = column.stateAtDepth(depth);
				if (isWetSeedCandidate(world, pos, state)) {
					accumulator.wetSeedPositions.add(pos.asLong());
				}
				if (isLavaMagmaSeedCandidate(world, pos, state)) {
					MadokuEcosystemManager.trackDirtCandidateForMode(world, pos, state, "wet");
				}
			}
		}
	}

	static void finalizeTrackablesInChunkDiscovery(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuEcosystemManager.ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || accumulator == null || !MadokuEcosystemManager.isNaturalErosionEnabled()) {
			return;
		}
		if (!accumulator.wetSeedPositions.isEmpty()) {
			spreadWetTrackingFromSeeds(world, accumulator.wetSeedPositions);
		}
	}

	static boolean isWetSeedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isWaterErosionEnabled() || !MadokuEcosystemManager.isTrackableGroundBlock(state)) {
			return false;
		}
		if (isSubmerged(world, blockPos)) {
			return false;
		}
		return isAdjacentToSurfaceWater(world, blockPos);
	}

	static boolean isLavaMagmaSeedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isLavaErosionEnabled()) {
			return false;
		}
		String sourceBlockId = EcosystemConfigManager.blockId(state.getBlock());
		if (!MadokuEcosystemManager.isLavaMagmaSourceBlockId(sourceBlockId)) {
			return false;
		}
		return isAdjacentToLava(world, blockPos, currentSettings().lavaErosionRadius());
	}

	static boolean isWetTrackedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isWaterErosionEnabled() || !MadokuEcosystemManager.isTrackableGroundBlock(state)) {
			return false;
		}
		if (MadokuEcosystemManager.resolveErosionRule(world, blockPos, state, "") == null) {
			return false;
		}
		return !isSubmerged(world, blockPos);
	}

	static void syncChunkProcessorTracking(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		boolean tracked = MadokuEcosystemManager.chunkHasDirtMode(chunkKey, "wet");
		if (tracked) {
			MadokuChunkManager.trackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		} else {
			MadokuChunkManager.untrackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}
		emitErosionDebug("ecosystem.tracking", builder -> builder
			.subject("sync-erosion-chunk-tracking")
			.field("level-id", chunkKey.levelId())
			.field("chunk-x", chunkKey.chunkX())
			.field("chunk-z", chunkKey.chunkZ())
			.field("tracked", tracked));
	}

	static boolean isSubmerged(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
			|| world.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER);
	}

	static boolean isAdjacentToSurfaceWater(ServerLevel world, BlockPos blockPos) {
		if (world == null || blockPos == null) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (direction == null) {
				continue;
			}
			if (isSurfaceLevelWater(world, blockPos.relative(direction))) {
				return true;
			}
		}
		return false;
	}

	static boolean isAdjacentToLava(ServerLevel world, BlockPos blockPos, int radius) {
		if (world == null || blockPos == null || radius < 0) {
			return false;
		}
		if (world.getFluidState(blockPos).is(net.minecraft.tags.FluidTags.LAVA)) {
			return true;
		}
		if (radius == 0) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (direction == null) {
				continue;
			}
			BlockPos neighborPos = blockPos.relative(direction);
			if (world.getFluidState(neighborPos).is(net.minecraft.tags.FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSurfaceLevelWater(ServerLevel world, BlockPos waterPos) {
		if (world == null || waterPos == null) {
			return false;
		}
		if (!world.getFluidState(waterPos).is(net.minecraft.tags.FluidTags.WATER)) {
			return false;
		}
		if (world.getFluidState(waterPos.above()).is(net.minecraft.tags.FluidTags.WATER)) {
			return false;
		}
		int topY = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, waterPos.getX(), waterPos.getZ()) - 1;
		return waterPos.getY() >= topY;
	}

	public static void runTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			schedulerId = context.getSchedulerId();
		}
		taskScheduled = false;
		if (server == null || !isEnabled()) {
			return;
		}
		emitErosionDebug("ecosystem.natural_erosion.scheduler", builder -> builder
			.subject("run-task")
			.field("scheduler-id", schedulerId)
			.field("task-scheduled", taskScheduled));
		requestProcessing(server, resolveSchedulerInterval(server));
		MadokuChunkManager.runChunkProcessorProcessingStep(server, CHUNK_PROCESSOR_ID);
	}

	public static void requestProcessing(MinecraftServer server, long delayTicks) {
		if (server == null || !isEnabled()) {
			return;
		}
		String currentSchedulerId = ensureSchedulerExists();
		boolean queuedBefore = isTaskQueued(currentSchedulerId);
		if (taskScheduled && queuedBefore) {
			return;
		}
		taskScheduled = false;
		SchedulerManagerSystem.EnqueueStatus firstStatus = SchedulerManagerSystem.enqueue(
			currentSchedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		if (isAccepted(firstStatus)) {
			taskScheduled = true;
			emitErosionDebug("ecosystem.natural_erosion.scheduler", builder -> builder
				.subject("request-processing")
				.field("scheduler-id", currentSchedulerId)
				.field("delay-ticks", delayTicks)
				.field("queued-before", queuedBefore)
				.field("accepted", true));
			return;
		}
		String refreshedSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(SCHEDULER_OWNER_ID)
		);
		schedulerId = refreshedSchedulerId;
		SchedulerManagerSystem.EnqueueStatus secondStatus = SchedulerManagerSystem.enqueue(
			refreshedSchedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		if (isAccepted(secondStatus)) {
			taskScheduled = true;
		}
		emitErosionDebug("ecosystem.natural_erosion.scheduler", builder -> builder
			.subject("request-processing")
			.field("scheduler-id", schedulerId)
			.field("delay-ticks", delayTicks)
			.field("queued-before", queuedBefore)
			.field("accepted", isAccepted(secondStatus)));
	}

	static boolean spreadWetTrackingFromSeeds(ServerLevel world, Set<Long> seedPositions) {
		if (world == null || seedPositions == null || seedPositions.isEmpty()) {
			return false;
		}

		boolean changed = false;
		Set<Long> visited = new LinkedHashSet<>();
		ArrayDeque<MadokuEcosystemManager.SpreadNode> queue = new ArrayDeque<>();
		for (Long packedPos : seedPositions) {
			if (packedPos == null) {
				continue;
			}
			if (!visited.add(packedPos)) {
				continue;
			}
			queue.addLast(new MadokuEcosystemManager.SpreadNode(BlockPos.of(packedPos), 0));
		}

		while (!queue.isEmpty()) {
			MadokuEcosystemManager.SpreadNode current = queue.removeFirst();
			BlockPos currentPos = current.pos();
			if (currentPos == null) {
				continue;
			}

			BlockState currentState = world.getBlockState(currentPos);
			if (isWetTrackedCandidate(world, currentPos, currentState)) {
				changed |= MadokuEcosystemManager.trackDirtCandidateForMode(world, currentPos, currentState, "wet");
			}
			BlockPos abovePos = currentPos.above();
			BlockState aboveState = world.getBlockState(abovePos);
			if (isWetTrackedCandidate(world, abovePos, aboveState)) {
				changed |= MadokuEcosystemManager.trackDirtCandidateForMode(world, abovePos, aboveState, "wet");
			}

			if (current.depth() >= currentSettings().waterErosionRadius()) {
				continue;
			}

			for (Direction direction : HORIZONTAL_DIRECTIONS) {
				BlockPos nextPos = currentPos.relative(direction);
				long nextPackedPos = nextPos.asLong();
				if (visited.add(nextPackedPos)) {
					queue.addLast(new MadokuEcosystemManager.SpreadNode(nextPos, current.depth() + 1));
				}
			}
		}

		return changed;
	}

	private static long resolveSchedulerInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(server, SCHEDULER_OWNER_ID, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
	}

	private static NaturalErosionConfigManager.Settings currentSettings() {
		return settings == null ? NaturalErosionConfigManager.defaults() : settings;
	}

	static boolean isWaterErosionEnabled() {
		NaturalErosionConfigManager.Settings current = currentSettings();
		return isEnabled() && current.waterErosion() != null && current.waterErosion().enabled();
	}

	static boolean isLavaErosionEnabled() {
		NaturalErosionConfigManager.Settings current = currentSettings();
		return isEnabled() && current.lavaErosion() != null && current.lavaErosion().enabled();
	}

	private static String ensureSchedulerExists() {
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(SCHEDULER_OWNER_ID)
			);
		}
		return schedulerId;
	}

	private static boolean isTaskQueued(String schedulerIdInput) {
		String current = schedulerIdInput == null ? "" : schedulerIdInput.trim();
		return !current.isEmpty() && SchedulerManagerSystem.hasQueuedTask(current, TASK_TYPE);
	}

	private static boolean isAccepted(SchedulerManagerSystem.EnqueueStatus status) {
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static void clearSchedulerState() {
		schedulerId = "";
		taskScheduled = false;
	}

	private static void loadConfig() {
		NaturalErosionConfigManager.Settings fallback = NaturalErosionConfigManager.defaults();
		JsonObject defaults = NaturalErosionConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(file, defaults);
			settings = NaturalErosionConfigManager.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(file, NaturalErosionConfigManager.toJson(settings), defaults);
			emitErosionDebug("ecosystem.natural_erosion.config", builder -> builder
				.subject("load-config")
				.field("config-folder", CONFIG_FOLDER_NAME)
				.field("config-file", CONFIG_FILE_NAME)
				.field("enabled", settings.isEnabled()));
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalErosionManager config; using defaults.", exception);
			emitErosionDebug("ecosystem.natural_erosion.config", builder -> builder
				.subject("load-config-failed")
				.field("config-folder", CONFIG_FOLDER_NAME)
				.field("config-file", CONFIG_FILE_NAME)
				.field("enabled", fallback.isEnabled()));
		}
	}

	private static void emitErosionDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
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
}
