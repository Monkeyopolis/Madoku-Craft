package madoku.craft.ecosystem;

import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class EcosystemNaturalGrowthManager {
	public static final String SCHEDULER_OWNER_ID = "ecosystem_growth_process_gameplay";
	public static final String TASK_TYPE = "ecosystem_growth_process_gameplay_tick";
	public static final String CHUNK_PROCESSOR_ID = "ecosystem_natural_growth";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalGrowthManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "natural-growth";
	private static final String DEBUG_MAIN_SYSTEM = "ecosystem";
	private static final String DEBUG_SUB_SYSTEM = "ecosystem-natural-growth-manager";
	private static final long MIN_INTERVAL_TICKS = 1L;
	private static final long MAX_INTERVAL_TICKS = 20L;

	private static volatile NaturalGrowthConfigManager.Settings settings = NaturalGrowthConfigManager.defaults();
	private static volatile String schedulerId = "";
	private static volatile boolean taskScheduled = false;

	private static final MadokuChunkManager.ChunkProcessor CHUNK_PROCESSOR = new MadokuChunkManager.ChunkProcessor() {
		@Override
		public boolean requiresSurfaceColumns() {
			return false;
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

	private EcosystemNaturalGrowthManager() {
	}

	public static void initialize() {
		loadConfig();
		MadokuChunkManager.registerChunkProcessor(CHUNK_PROCESSOR_ID, CHUNK_PROCESSOR);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE, EcosystemNaturalGrowthManager::runTask);
		emitGrowthDebug("ecosystem.natural_growth.lifecycle", builder -> builder
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
		emitGrowthDebug("ecosystem.natural_growth.lifecycle", builder -> builder
			.subject("reset")
			.field("scheduler-id", previousSchedulerId)
			.field("task-scheduled", false));
	}

	public static NaturalGrowthConfigManager.Settings getSettings() {
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
		emitGrowthDebug("ecosystem.natural_growth.lifecycle", builder -> builder
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
		emitGrowthDebug("ecosystem.natural_growth.lifecycle", builder -> builder
			.subject("server-stopping")
			.field("scheduler-id", schedulerId)
			.field("task-scheduled", taskScheduled));
		clearSchedulerState();
	}

	public static MadokuChunkManager.ChunkProcessor getChunkProcessor() {
		return CHUNK_PROCESSOR;
	}

	public static void processChunk(ServerLevel level, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		emitGrowthDebug("ecosystem.natural_growth.process_chunk", builder -> builder
			.subject("process-chunk")
			.field("level-id", MadokuEcosystemManager.levelId(level))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("day-time", currentAbsoluteDayTime));
		processDirtInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime, "surface_dirt");
		processTreeCandidateInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		processCactusCandidateInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		processGrassCandidateInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		processDesertFoliageGrowthCandidateInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		processFoliageCandidateInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
	}

	static void processDirtInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		long currentAbsoluteDayTime,
		String targetMode
	) {
		if (!MadokuEcosystemManager.isEnabled() || !MadokuEcosystemManager.isModeEnabled(targetMode)) {
			return;
		}
		String worldLevelId = MadokuEcosystemManager.levelId(world);
		MadokuEcosystemManager.ChunkRefKey targetChunkKey = new MadokuEcosystemManager.ChunkRefKey(worldLevelId, chunkX, chunkZ);
		Set<String> chunkDirtKeys = new LinkedHashSet<>(MadokuEcosystemManager.dirtKeysByChunk.getOrDefault(targetChunkKey, Set.of()));
		if (chunkDirtKeys.isEmpty()) {
			return;
		}

		int evaluated = 0;
		int advanced = 0;
		int grown = 0;
		int removed = 0;
		List<String> removeKeys = new ArrayList<>();
		for (String dirtEntryKey : chunkDirtKeys) {
			evaluated++;
			MadokuEcosystemManager.DirtState dirt = MadokuEcosystemManager.dirtBlocksByKey.get(dirtEntryKey);
			if (dirt == null || !dirt.levelId.equals(worldLevelId)) {
				continue;
			}

			BlockPos dirtPos = BlockPos.of(dirt.dirtPos);
			if (!targetChunkKey.equals(MadokuEcosystemManager.chunkRefForPos(worldLevelId, dirt.dirtPos))) {
				continue;
			}

			BlockState state = world.getBlockState(dirtPos);
			if (!MadokuEcosystemManager.isTrackableGroundBlock(state)) {
				removeKeys.add(dirtEntryKey);
				removed++;
				continue;
			}

			if (!targetMode.equals(dirt.mode)) {
				continue;
			}
			if (!MadokuEcosystemManager.isCandidateForMode(world, dirtPos, state, dirt.mode)) {
				removeKeys.add(dirtEntryKey);
				removed++;
				continue;
			}

			double requiredTicks = Math.max(1.0d, dirt.requiredGrowthTicks);
			long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(dirt.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(requiredTicks, dirt.progressGrowthTicks + elapsedTicks);
				if (updatedProgress > dirt.progressGrowthTicks) {
					dirt.progressGrowthTicks = updatedProgress;
					MadokuEcosystemManager.dirty = true;
					advanced++;
				}
			}
			dirt.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (dirt.progressGrowthTicks + 1e-6d >= requiredTicks) {
				Block replacement = "surface_dirt".equals(dirt.mode)
					? MadokuEcosystemManager.resolveSurfaceDirtGrowthBlock(world, dirtPos)
					: MadokuEcosystemManager.resolveWetGroundReplacementBlock(world, dirtPos, state, dirt.erosionRuleId);
				if (replacement != null && replacement != state.getBlock()) {
					world.setBlockAndUpdate(dirtPos, replacement.defaultBlockState());
				}
				removeKeys.add(dirtEntryKey);
				grown++;
				removed++;
			}
		}

		for (String key : removeKeys) {
			if (MadokuEcosystemManager.removeDirtStateByKey(key) != null) {
				MadokuEcosystemManager.dirty = true;
			}
		}
		final int finalEvaluated = evaluated;
		final int finalAdvanced = advanced;
		final int finalGrown = grown;
		final int finalRemoved = removed;
		emitGrowthDebug("ecosystem.natural_growth.dirt", builder -> builder
			.subject("process-dirt")
			.field("mode", targetMode)
			.field("level-id", worldLevelId)
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("evaluated", finalEvaluated)
			.field("advanced", finalAdvanced)
			.field("grown", finalGrown)
			.field("removed", finalRemoved)
			.field("remaining", chunkDirtKeys.size() - removeKeys.size()));
	}

	static void processTreeCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		MadokuEcosystemManager.TreeCandidateState candidate = MadokuEcosystemManager.treeCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		boolean progressed = false;
		boolean removed = false;
		boolean grew = false;
		if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			MadokuEcosystemManager.removeTreeCandidate(chunkKey);
			MadokuEcosystemManager.dirty = true;
			removed = true;
			emitGrowthDebug("ecosystem.natural_growth.tree", builder -> builder
				.subject("remove-invalid-tree-candidate")
				.field("level-id", candidate.levelId)
				.field("chunk-x", candidate.chunkX)
				.field("chunk-z", candidate.chunkZ)
				.field("tree-type", candidate.treeType));
			return;
		}

		BlockPos groundPos = BlockPos.of(candidate.groundPos);
		BlockState groundState = world.getBlockState(groundPos);
		if (!MadokuEcosystemManager.isValidTreeGroundCandidate(world, groundPos, groundState, candidate.treeType)) {
			MadokuEcosystemManager.removeTreeCandidate(chunkKey);
			MadokuEcosystemManager.dirty = true;
			removed = true;
			emitGrowthDebug("ecosystem.natural_growth.tree", builder -> builder
				.subject("remove-invalid-ground")
				.field("level-id", candidate.levelId)
				.field("chunk-x", chunkX)
				.field("chunk-z", chunkZ)
				.field("tree-type", candidate.treeType));
			return;
		}

		long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
		long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
		long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
		if (elapsedTicks > 0L) {
			double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				MadokuEcosystemManager.dirty = true;
				progressed = true;
			}
		}
		candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

		if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
			boolean grownNow = MadokuEcosystemManager.tryGrowTreeAtGround(world, groundPos, candidate.treeType);
			MadokuEcosystemManager.removeTreeCandidate(chunkKey);
			MadokuEcosystemManager.dirty = true;
			removed = true;
			grew = grownNow;
			if (grownNow) {
				MadokuEcosystemManager.requestEcosystemProcessing(world.getServer(), 1L);
			}
		}
		final boolean finalProgressed = progressed;
		final boolean finalRemoved = removed;
		final boolean finalGrew = grew;
		if (finalProgressed || finalRemoved || finalGrew) {
			emitGrowthDebug("ecosystem.natural_growth.tree", builder -> builder
				.subject("process-tree")
				.field("level-id", candidate.levelId)
				.field("chunk-x", chunkX)
				.field("chunk-z", chunkZ)
				.field("progressed", finalProgressed)
				.field("removed", finalRemoved)
				.field("grown", finalGrew)
				.field("progress", candidate.progressGrowthTicks)
				.field("required", candidate.requiredGrowthTicks));
		}
	}

	static void processCactusCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		MadokuEcosystemManager.CactusCandidateState candidate = MadokuEcosystemManager.cactusCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		boolean progressed = false;
		boolean removed = false;
		boolean grew = false;
		if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			MadokuEcosystemManager.removeCactusCandidate(chunkKey);
			MadokuEcosystemManager.dirty = true;
			removed = true;
			emitGrowthDebug("ecosystem.natural_growth.cactus", builder -> builder
				.subject("remove-invalid-cactus-candidate")
				.field("level-id", candidate.levelId)
				.field("chunk-x", candidate.chunkX)
				.field("chunk-z", candidate.chunkZ));
			return;
		}

		BlockPos groundPos = BlockPos.of(candidate.groundPos);
		BlockState groundState = world.getBlockState(groundPos);
		if (!MadokuEcosystemManager.isValidCactusGroundCandidate(world, groundPos, groundState)) {
			MadokuEcosystemManager.removeCactusCandidate(chunkKey);
			MadokuEcosystemManager.dirty = true;
			removed = true;
			emitGrowthDebug("ecosystem.natural_growth.cactus", builder -> builder
				.subject("remove-invalid-ground")
				.field("level-id", candidate.levelId)
				.field("chunk-x", chunkX)
				.field("chunk-z", chunkZ));
			return;
		}

		long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
		long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
		long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
		if (elapsedTicks > 0L) {
			double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				MadokuEcosystemManager.dirty = true;
				progressed = true;
			}
		}
		candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

		if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
			boolean grownNow = MadokuEcosystemManager.tryGrowCactusAtGround(world, groundPos);
			MadokuEcosystemManager.removeCactusCandidate(chunkKey);
			MadokuEcosystemManager.dirty = true;
			removed = true;
			grew = grownNow;
			if (grownNow) {
				MadokuEcosystemManager.requestEcosystemProcessing(world.getServer(), 1L);
			}
		}
		final boolean finalProgressed = progressed;
		final boolean finalRemoved = removed;
		final boolean finalGrew = grew;
		if (finalProgressed || finalRemoved || finalGrew) {
			emitGrowthDebug("ecosystem.natural_growth.cactus", builder -> builder
				.subject("process-cactus")
				.field("level-id", candidate.levelId)
				.field("chunk-x", chunkX)
				.field("chunk-z", chunkZ)
				.field("progressed", finalProgressed)
				.field("removed", finalRemoved)
				.field("grown", finalGrew)
				.field("progress", candidate.progressGrowthTicks)
				.field("required", candidate.requiredGrowthTicks));
		}
	}

	static void processGrassCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.GrassCandidateState> candidates = MadokuEcosystemManager.grassCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		int progressed = 0;
		int grown = 0;
		int removed = 0;
		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			MadokuEcosystemManager.GrassCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);
			BlockState groundState = world.getBlockState(groundPos);
			if (!MadokuEcosystemManager.isValidGrassGroundCandidate(world, groundPos, groundState)) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				MadokuEcosystemManager.dirty = true;
				progressed++;
			}
			}
			candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
				MadokuEcosystemManager.tryGrowGrassAtGround(world, groundPos);
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				grown++;
				removed++;
			}
		}

		if (candidates.isEmpty()) {
			MadokuEcosystemManager.grassCandidatesByChunk.remove(chunkKey);
			MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
		}
		final int finalProgressed = progressed;
		final int finalGrown = grown;
		final int finalRemoved = removed;
		emitGrowthDebug("ecosystem.natural_growth.grass", builder -> builder
			.subject("process-grass")
			.field("level-id", MadokuEcosystemManager.levelId(world))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("progressed", finalProgressed)
			.field("grown", finalGrown)
			.field("removed", finalRemoved)
			.field("remaining", candidates.size()));
	}

	static void processDesertFoliageGrowthCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.GrassCandidateState> candidates = MadokuEcosystemManager.desertFoliageGrowthCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		int progressed = 0;
		int grown = 0;
		int removed = 0;
		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			MadokuEcosystemManager.GrassCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);
			BlockState groundState = world.getBlockState(groundPos);
			if (!MadokuEcosystemManager.isValidDesertFoliageGrowthGroundCandidate(world, groundPos, groundState)) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				MadokuEcosystemManager.dirty = true;
				progressed++;
			}
			}
			candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
				MadokuEcosystemManager.tryGrowDesertFoliageAtGround(world, groundPos);
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				grown++;
				removed++;
			}
		}

		if (candidates.isEmpty()) {
			MadokuEcosystemManager.desertFoliageGrowthCandidatesByChunk.remove(chunkKey);
			MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
		}
		final int finalProgressed = progressed;
		final int finalGrown = grown;
		final int finalRemoved = removed;
		emitGrowthDebug("ecosystem.natural_growth.desert_foliage", builder -> builder
			.subject("process-desert-foliage")
			.field("level-id", MadokuEcosystemManager.levelId(world))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("progressed", finalProgressed)
			.field("grown", finalGrown)
			.field("removed", finalRemoved)
			.field("remaining", candidates.size()));
	}

	static void processFoliageCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.FoliageCandidateState> candidates = MadokuEcosystemManager.foliageCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		int progressed = 0;
		int grown = 0;
		int removed = 0;
		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			MadokuEcosystemManager.FoliageCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);
			BlockState groundState = world.getBlockState(groundPos);
			if (!MadokuEcosystemManager.isValidFoliageGroundCandidate(world, groundPos, groundState, candidate.foliageType)) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				removed++;
				continue;
			}

			long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				MadokuEcosystemManager.dirty = true;
				progressed++;
			}
			}
			candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
				MadokuEcosystemManager.tryGrowFoliageAtGround(world, groundPos, candidate.foliageType);
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				grown++;
				removed++;
			}
		}

		if (candidates.isEmpty()) {
			MadokuEcosystemManager.foliageCandidatesByChunk.remove(chunkKey);
			MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
		}
		final int finalProgressed = progressed;
		final int finalGrown = grown;
		final int finalRemoved = removed;
		emitGrowthDebug("ecosystem.natural_growth.foliage", builder -> builder
			.subject("process-foliage")
			.field("level-id", MadokuEcosystemManager.levelId(world))
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("progressed", finalProgressed)
			.field("grown", finalGrown)
			.field("removed", finalRemoved)
			.field("remaining", candidates.size()));
	}

	public static void runTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			schedulerId = context.getSchedulerId();
		}
		taskScheduled = false;
		if (server == null || !isEnabled()) {
			return;
		}
		emitGrowthDebug("ecosystem.natural_growth.scheduler", builder -> builder
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
			emitGrowthDebug("ecosystem.natural_growth.scheduler", builder -> builder
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
		emitGrowthDebug("ecosystem.natural_growth.scheduler", builder -> builder
			.subject("request-processing")
			.field("scheduler-id", schedulerId)
			.field("delay-ticks", delayTicks)
			.field("queued-before", queuedBefore)
			.field("accepted", isAccepted(secondStatus)));
	}

	private static long resolveSchedulerInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(server, SCHEDULER_OWNER_ID, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
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
		NaturalGrowthConfigManager.Settings fallback = NaturalGrowthConfigManager.defaults();
		JsonObject defaults = NaturalGrowthConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(file, defaults);
			settings = NaturalGrowthConfigManager.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(file, NaturalGrowthConfigManager.toJson(settings), defaults);
			emitGrowthDebug("ecosystem.natural_growth.config", builder -> builder
				.subject("load-config")
				.field("config-folder", CONFIG_FOLDER_NAME)
				.field("config-file", CONFIG_FILE_NAME)
				.field("enabled", settings.isEnabled()));
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalGrowthManager config; using defaults.", exception);
			emitGrowthDebug("ecosystem.natural_growth.config", builder -> builder
				.subject("load-config-failed")
				.field("config-folder", CONFIG_FOLDER_NAME)
				.field("config-file", CONFIG_FILE_NAME)
				.field("enabled", fallback.isEnabled()));
		}
	}

	private static void emitGrowthDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
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
