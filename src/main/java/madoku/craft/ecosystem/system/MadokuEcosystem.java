package madoku.craft.ecosystem.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.chunk.ChunkManagerSystem;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.season.MadokuSeason;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MadokuEcosystem {
	private static final String DATA_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String DATA_FILE_NAME = "madoku-ecosystem";
	private static final String ECOSYSTEM_DISCOVERY_SCHEDULER_OWNER_ID = "ecosystem_discovery_gameplay";
	private static final String ECOSYSTEM_PROCESS_SCHEDULER_OWNER_ID = "ecosystem_process_gameplay";
	private static final String TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK = "ecosystem_discovery_gameplay_tick";
	private static final String TASK_TYPE_ECOSYSTEM_PROCESS_TICK = "ecosystem_process_gameplay_tick";
	private static final long ECOSYSTEM_SCHEDULER_INTERVAL_TICKS = 5L;
	private static final String CHUNK_PROCESSOR_ID = "ecosystem_dirt_to_sand";

	private static final String FIELD_GROUND_BLOCKS = "ground-blocks";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_BLOCK_POS = "block-pos";
	private static final String FIELD_MODE = "mode";
	private static final String FIELD_REQUIRED_GROWTH_TICKS = "required-growth-ticks";
	private static final String FIELD_PROGRESS_GROWTH_TICKS = "progress-growth-ticks";
	private static final String FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME = "last-processed-absolute-day-time";
	private static final String FIELD_TREE_CANDIDATES = "tree-candidates";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_TREE_GROUND_POS = "tree-ground-pos";
	private static final String FIELD_TREE_TYPE = "tree-type";
	private static final String FIELD_INITIAL_SEASON_ID = "initial-season-id";

	private static final String MODE_WET = "wet";
	private static final String MODE_SURFACE_DIRT = "surface_dirt";
	private static final int WET_MIN_DAYS = 7;
	private static final int WET_MAX_DAYS = 11;
	private static final int SURFACE_SPRING_MIN_DAYS = 1;
	private static final int SURFACE_SPRING_MAX_DAYS = 3;
	private static final int SURFACE_SUMMER_FALL_MIN_DAYS = 3;
	private static final int SURFACE_SUMMER_FALL_MAX_DAYS = 5;
	private static final int SURFACE_WINTER_MIN_DAYS = 7;
	private static final int SURFACE_WINTER_MAX_DAYS = 9;
	private static final int WET_SPREAD_RADIUS_BLOCKS = 2;
	private static final int TREE_OAK_SPRING_MIN_DAYS = 3;
	private static final int TREE_OAK_SPRING_MAX_DAYS = 9;
	private static final int TREE_OAK_SUMMER_MIN_DAYS = 7;
	private static final int TREE_OAK_SUMMER_MAX_DAYS = 13;
	private static final int TREE_OAK_FALL_MIN_DAYS = 11;
	private static final int TREE_OAK_FALL_MAX_DAYS = 17;
	private static final int TREE_SPRUCE_WINTER_MIN_DAYS = 3;
	private static final int TREE_SPRUCE_WINTER_MAX_DAYS = 9;
	private static final int TREE_SPRUCE_SPRING_MIN_DAYS = 11;
	private static final int TREE_SPRUCE_SPRING_MAX_DAYS = 17;
	private static final int TREE_SPRUCE_FALL_MIN_DAYS = 7;
	private static final int TREE_SPRUCE_FALL_MAX_DAYS = 13;
	private static final int TREE_BIRCH_MIN_DAYS = 7;
	private static final int TREE_BIRCH_MAX_DAYS = 13;
	private static final int TREE_JUNGLE_SUMMER_MIN_DAYS = 3;
	private static final int TREE_JUNGLE_SUMMER_MAX_DAYS = 9;
	private static final int TREE_MANGROVE_SUMMER_MIN_DAYS = 3;
	private static final int TREE_MANGROVE_SUMMER_MAX_DAYS = 9;
	private static final int TREE_MANGROVE_SPRING_FALL_MIN_DAYS = 11;
	private static final int TREE_MANGROVE_SPRING_FALL_MAX_DAYS = 17;
	private static final int TREE_ACACIA_SPRING_FALL_MIN_DAYS = 7;
	private static final int TREE_ACACIA_SPRING_FALL_MAX_DAYS = 13;
	private static final int TREE_DARK_OAK_SPRING_SUMMER_MIN_DAYS = 7;
	private static final int TREE_DARK_OAK_SPRING_SUMMER_MAX_DAYS = 13;
	private static final int TREE_PALE_OAK_FALL_WINTER_MIN_DAYS = 7;
	private static final int TREE_PALE_OAK_FALL_WINTER_MAX_DAYS = 13;
	private static final int TREE_CHERRY_SPRING_FALL_MIN_DAYS = 3;
	private static final int TREE_CHERRY_SPRING_FALL_MAX_DAYS = 9;
	private static final int TREE_CHERRY_SUMMER_WINTER_MIN_DAYS = 11;
	private static final int TREE_CHERRY_SUMMER_WINTER_MAX_DAYS = 17;
	private static final long ABSOLUTE_TIME_ROLLBACK_RESET_TICKS = 20L;
	private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[] {
		Direction.NORTH,
		Direction.SOUTH,
		Direction.EAST,
		Direction.WEST
	};
	private static final Set<Block> TRACKABLE_WET_GROUND_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.ROOTED_DIRT,
		Blocks.DIRT_PATH,
		Blocks.PODZOL,
		Blocks.MYCELIUM,
		Blocks.COARSE_DIRT
	);
	private static final Set<Block> TRACKABLE_TREE_GROUND_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.PODZOL,
		Blocks.MUD
	);
	private static final String TREE_TYPE_OAK = "oak";
	private static final String TREE_TYPE_SPRUCE = "spruce";
	private static final String TREE_TYPE_BIRCH = "birch";
	private static final String TREE_TYPE_JUNGLE = "jungle";
	private static final String TREE_TYPE_MANGROVE = "mangrove";
	private static final String TREE_TYPE_ACACIA = "acacia";
	private static final String TREE_TYPE_DARK_OAK = "dark_oak";
	private static final String TREE_TYPE_PALE_OAK = "pale_oak";
	private static final String TREE_TYPE_CHERRY = "cherry";

	private static volatile String ecosystemDiscoverySchedulerId = "";
	private static volatile String ecosystemProcessSchedulerId = "";
	private static volatile boolean ecosystemDiscoveryTaskScheduled = false;
	private static volatile boolean ecosystemProcessTaskScheduled = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile boolean dirty = false;

	private static final Map<String, DirtState> dirtBlocksByKey = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, Set<String>> dirtKeysByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, TreeCandidateState> treeCandidatesByChunk = new LinkedHashMap<>();

	private static final ChunkManagerSystem.ChunkProcessor CHUNK_PROCESSOR = new ChunkManagerSystem.ChunkProcessor() {
		@Override
		public void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ) {
			discoverTrackableBlocksInChunk(level, chunkX, chunkZ);
		}

		@Override
		public void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
			if (level == null || !ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ)) {
				return;
			}
			long currentAbsoluteDayTime = resolveAbsoluteDayTime(level);
			processDirtInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
			processTreeCandidateInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		}
	};

	private MadokuEcosystem() {
	}

	public static void initialize() {
		ChunkManagerSystem.registerChunkProcessor(CHUNK_PROCESSOR_ID, CHUNK_PROCESSOR);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK, MadokuEcosystem::runEcosystemDiscoveryTask);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_ECOSYSTEM_PROCESS_TICK, MadokuEcosystem::runEcosystemProcessTask);
	}

	public static void reset() {
		dirtBlocksByKey.clear();
		dirtKeysByChunk.clear();
		treeCandidatesByChunk.clear();
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_ID);
		ecosystemDiscoverySchedulerId = "";
		ecosystemProcessSchedulerId = "";
		ecosystemDiscoveryTaskScheduled = false;
		ecosystemProcessTaskScheduled = false;
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_ID);
		ecosystemDiscoverySchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_DISCOVERY_SCHEDULER_OWNER_ID)
		);
		ecosystemProcessSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_PROCESS_SCHEDULER_OWNER_ID)
		);
		SchedulerManagerSystem.clearQueuedRequests(ecosystemDiscoverySchedulerId);
		SchedulerManagerSystem.clearQueuedRequests(ecosystemProcessSchedulerId);
		requestEcosystemProcessing(server, 1L);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(data);
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		dirty = false;
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

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
	}

	public static void trackPlacedDirtBlock(ServerLevel world, BlockPos dirtPos) {
		if (world == null || dirtPos == null) {
			return;
		}
		if (trackAndSpreadAtPosition(world, dirtPos)) {
			requestEcosystemProcessing(world.getServer(), ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
		}
	}

	public static void syncDirtTrackingAroundBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return;
		}

		boolean changed = false;
		String atKey = dirtKey(world, pos);
		DirtState trackedAt = dirtBlocksByKey.get(atKey);
		if (trackedAt != null && !isCandidateForMode(world, pos, world.getBlockState(pos), trackedAt.mode)) {
			removeDirtStateByKey(atKey);
			changed = true;
			dirty = true;
		}

		BlockPos belowPos = pos.below();
		String belowKey = dirtKey(world, belowPos);
		DirtState trackedBelow = dirtBlocksByKey.get(belowKey);
		if (trackedBelow != null && !isCandidateForMode(world, belowPos, world.getBlockState(belowPos), trackedBelow.mode)) {
			removeDirtStateByKey(belowKey);
			changed = true;
			dirty = true;
		}

		if (trackAndSpreadAtPosition(world, pos)) {
			changed = true;
		}
		if (trackAndSpreadAtPosition(world, belowPos)) {
			changed = true;
		}

		if (changed) {
			requestEcosystemProcessing(world.getServer(), ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
		}
	}

	private static void runEcosystemDiscoveryTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			ecosystemDiscoverySchedulerId = context.getSchedulerId();
		}
		ecosystemDiscoveryTaskScheduled = false;

		if (server == null) {
			return;
		}

		emitEcosystemSchedulerTickDebug(server, context, "discovery_start");
		// Re-enqueue first so a processing error cannot break the ecosystem loop chain.
		requestEcosystemDiscoveryTask(server, ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
		ChunkManagerSystem.runChunkProcessorDiscoveryStep(server, CHUNK_PROCESSOR_ID);
		emitEcosystemSchedulerTickDebug(server, context, "discovery_end");
	}

	private static void runEcosystemProcessTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			ecosystemProcessSchedulerId = context.getSchedulerId();
		}
		ecosystemProcessTaskScheduled = false;
		if (server == null) {
			return;
		}

		emitEcosystemSchedulerTickDebug(server, context, "process_start");
		requestEcosystemProcessTask(server, ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
		ChunkManagerSystem.runChunkProcessorProcessingStep(server, CHUNK_PROCESSOR_ID);
		emitEcosystemSchedulerTickDebug(server, context, "process_end");
	}

	private static void discoverTrackableBlocksInChunk(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null) {
			return;
		}
		int minX = chunkX << 4;
		int minZ = chunkZ << 4;
		int minY = world.getMinY();
		int maxY = world.getMaxY() - 1;
		Set<Long> wetSeedPositions = new LinkedHashSet<>();
		Set<Long> treeGroundCandidates = new LinkedHashSet<>();

		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldX = minX + localX;
				int worldZ = minZ + localZ;
				int topY = Math.min(maxY, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1);
				if (topY < minY) {
					continue;
				}

				BlockPos topPos = new BlockPos(worldX, topY, worldZ);
				discoverTrackableAtPosition(world, topPos, wetSeedPositions);
				discoverTrackableAtPosition(world, topPos.below(), wetSeedPositions);
				discoverTrackableAtPosition(world, topPos.below(2), wetSeedPositions);
				collectTreeGroundCandidate(world, topPos, treeGroundCandidates);
				collectTreeGroundCandidate(world, topPos.below(), treeGroundCandidates);
				collectTreeGroundCandidate(world, topPos.below(2), treeGroundCandidates);
			}
		}

		if (!wetSeedPositions.isEmpty()) {
			spreadWetTrackingFromSeeds(world, wetSeedPositions);
		}
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		emitTreeChunkDiscoveryDebug(world, chunkKey, treeGroundCandidates.size(), wetSeedPositions.size());
		pickTreeCandidateForChunk(world, chunkX, chunkZ, treeGroundCandidates);
	}

	private static void processDirtInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		String worldLevelId = levelId(world);
		ChunkRefKey targetChunkKey = new ChunkRefKey(worldLevelId, chunkX, chunkZ);
		Set<String> chunkDirtKeys = new LinkedHashSet<>(dirtKeysByChunk.getOrDefault(targetChunkKey, Set.of()));
		if (chunkDirtKeys.isEmpty()) {
			return;
		}

		List<String> removeKeys = new ArrayList<>();
		for (String dirtEntryKey : chunkDirtKeys) {
			DirtState dirt = dirtBlocksByKey.get(dirtEntryKey);
			if (dirt == null || !dirt.levelId.equals(worldLevelId)) {
				continue;
			}

			BlockPos dirtPos = BlockPos.of(dirt.dirtPos);
			if (!targetChunkKey.equals(chunkRefForPos(worldLevelId, dirt.dirtPos))) {
				continue;
			}

			BlockState state = world.getBlockState(dirtPos);
			if (!isTrackableGroundBlock(state)) {
				removeKeys.add(dirtEntryKey);
				continue;
			}

			if (!isCandidateForMode(world, dirtPos, state, dirt.mode)) {
				removeKeys.add(dirtEntryKey);
				continue;
			}

			double requiredTicks = Math.max(1.0d, dirt.requiredGrowthTicks);

			long previousAbsolute = normalizePreviousAbsoluteTick(dirt.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(requiredTicks, dirt.progressGrowthTicks + elapsedTicks);
				if (updatedProgress > dirt.progressGrowthTicks) {
					dirt.progressGrowthTicks = updatedProgress;
					dirty = true;
				}
			}
			dirt.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (dirt.progressGrowthTicks + 1e-6d >= requiredTicks) {
				Block replacement = MODE_SURFACE_DIRT.equals(dirt.mode)
					? resolveSurfaceDirtGrowthBlock(world, dirtPos)
					: resolveWetGroundReplacementBlock(world, dirtPos);
				if (replacement != null && replacement != state.getBlock()) {
					world.setBlockAndUpdate(dirtPos, replacement.defaultBlockState());
				}
				removeKeys.add(dirtEntryKey);
			}
		}

		for (String key : removeKeys) {
			if (removeDirtStateByKey(key) != null) {
				dirty = true;
			}
		}
	}

	private static void processTreeCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		TreeCandidateState candidate = treeCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		if (!candidate.levelId.equals(levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			removeTreeCandidate(chunkKey);
			dirty = true;
			emitTreeCandidateInvalidatedDebug(world, chunkKey, candidate, "chunk_mismatch");
			return;
		}

		BlockPos groundPos = BlockPos.of(candidate.groundPos);
		BlockState groundState = world.getBlockState(groundPos);
		if (!isValidTreeGroundCandidate(world, groundPos, groundState, candidate.treeType)) {
			removeTreeCandidate(chunkKey);
			dirty = true;
			emitTreeCandidateInvalidatedDebug(world, chunkKey, candidate, "ground_invalid");
			return;
		}

		long previousAbsolute = normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
		long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
		long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
		if (elapsedTicks > 0L) {
			double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				dirty = true;
				emitTreeCandidateProgressDebug(world, chunkKey, candidate, elapsedTicks);
			}
		}
		candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

		if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
			boolean grown = tryGrowTreeAtGround(world, groundPos, candidate.treeType);
			removeTreeCandidate(chunkKey);
			dirty = true;
			emitTreeGrowthResultDebug(world, chunkKey, candidate, grown);
			if (grown) {
				requestEcosystemProcessing(world.getServer(), ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
			}
		}
	}

	private static boolean trackExposedDirtCandidate(ServerLevel world, BlockPos dirtPos) {
		if (world == null || dirtPos == null) {
			return false;
		}
		BlockState state = world.getBlockState(dirtPos);
		String mode = resolveCandidateMode(world, dirtPos, state);
		return trackDirtCandidateForMode(world, dirtPos, state, mode);
	}

	private static boolean trackDirtCandidateForMode(ServerLevel world, BlockPos dirtPos, BlockState state, String mode) {
		if (world == null || dirtPos == null || state == null || mode == null || mode.isBlank()) {
			return false;
		}
		if (!isCandidateForMode(world, dirtPos, state, mode)) {
			return false;
		}

		String key = dirtKey(world, dirtPos);
		DirtState existing = dirtBlocksByKey.get(key);
		if (existing != null && mode.equals(existing.mode)) {
			return false;
		}

		double requiredGrowthTicks = MODE_SURFACE_DIRT.equals(mode)
			? resolveSurfaceDirtRequiredGrowthTicks(world)
			: resolveWetRequiredGrowthTicks();
		double startingProgress = existing != null && mode.equals(existing.mode) ? existing.progressGrowthTicks : 0.0d;

		putDirtState(key, new DirtState(
			levelId(world),
			dirtPos.asLong(),
			mode,
			requiredGrowthTicks,
			Math.max(0.0d, Math.min(requiredGrowthTicks, startingProgress)),
			resolveAbsoluteDayTime(world)
		));
		dirty = true;
		return true;
	}

	private static boolean trackAndSpreadAtPosition(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}

		BlockState state = world.getBlockState(pos);
		boolean changed = trackExposedDirtCandidate(world, pos);
		if (isWetSeedCandidate(world, pos, state)) {
			changed |= spreadWetTrackingFromSeed(world, pos);
		}
		return changed;
	}

	private static void discoverTrackableAtPosition(ServerLevel world, BlockPos pos, Set<Long> wetSeedPositions) {
		if (world == null || pos == null) {
			return;
		}

		BlockState state = world.getBlockState(pos);
		trackExposedDirtCandidate(world, pos);
		if (wetSeedPositions != null && isWetSeedCandidate(world, pos, state)) {
			wetSeedPositions.add(pos.asLong());
		}
	}

	private static void collectTreeGroundCandidate(ServerLevel world, BlockPos pos, Set<Long> outPositions) {
		if (world == null || pos == null || outPositions == null) {
			return;
		}
		BlockState state = world.getBlockState(pos);
		if (!isTrackableTreeGroundBlock(state)) {
			return;
		}
		if (isSubmerged(world, pos)) {
			return;
		}
		outPositions.add(pos.asLong());
	}

	private static void pickTreeCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeGroundCandidates) {
		if (world == null || treeGroundCandidates == null || treeGroundCandidates.isEmpty()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		if (treeCandidatesByChunk.containsKey(chunkKey)) {
			emitTreeCandidateScanDebug(world, chunkKey, normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world)), treeGroundCandidates.size(), 0, "existing_candidate");
			return;
		}

		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		List<TreeCandidateOption> options = new ArrayList<>();
		for (Long packedPos : treeGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			BlockState groundState = world.getBlockState(groundPos);
			for (String treeType : resolveTreeTypesForBiomeAndSeason(world, groundPos, seasonId)) {
				if (treeType == null || treeType.isBlank()) {
					continue;
				}
				if (!isValidTreeGroundCandidate(world, groundPos, groundState, treeType)) {
					continue;
				}
				options.add(new TreeCandidateOption(groundPos.asLong(), treeType));
			}
		}

		if (options.isEmpty()) {
			emitTreeCandidateScanDebug(world, chunkKey, seasonId, treeGroundCandidates.size(), 0, "no_options");
			return;
		}

		emitTreeCandidateScanDebug(world, chunkKey, seasonId, treeGroundCandidates.size(), options.size(), "picked");
		TreeCandidateOption selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));
		double requiredGrowthTicks = resolveTreeRequiredGrowthTicks(selected.treeType(), seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		TreeCandidateState candidate = new TreeCandidateState(
			levelId(world),
			chunkX,
			chunkZ,
			selected.groundPos(),
			selected.treeType(),
			seasonId,
			requiredGrowthTicks,
			0.0d,
			resolveAbsoluteDayTime(world)
		);
		putTreeCandidate(chunkKey, candidate);
		dirty = true;
		emitTreeCandidatePickedDebug(world, chunkKey, candidate, options.size());
	}

	private static boolean isTrackableGroundBlock(BlockState state) {
		return state != null && TRACKABLE_WET_GROUND_BLOCKS.contains(state.getBlock());
	}

	private static boolean isTrackableTreeGroundBlock(BlockState state) {
		return state != null && TRACKABLE_TREE_GROUND_BLOCKS.contains(state.getBlock());
	}

	private static String resolveCandidateMode(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isTrackableGroundBlock(state)) {
			return "";
		}
		if (isWetSeedCandidate(world, blockPos, state)) {
			return MODE_WET;
		}
		if (isSurfaceDirtCandidate(world, blockPos, state)) {
			return MODE_SURFACE_DIRT;
		}
		return "";
	}

	private static boolean isCandidateForMode(ServerLevel world, BlockPos blockPos, BlockState state, String mode) {
		if (MODE_WET.equals(mode)) {
			return isWetTrackedCandidate(world, blockPos, state);
		}
		if (MODE_SURFACE_DIRT.equals(mode)) {
			return isSurfaceDirtCandidate(world, blockPos, state);
		}
		return false;
	}

	private static boolean isWetSeedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isTrackableGroundBlock(state)) {
			return false;
		}
		if (isSubmerged(world, blockPos)) {
			return false;
		}
		return isAdjacentToSurfaceWater(world, blockPos);
	}

	private static boolean isWetTrackedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isTrackableGroundBlock(state)) {
			return false;
		}
		return !isSubmerged(world, blockPos);
	}

	private static boolean isSurfaceDirtCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || state.getBlock() != Blocks.DIRT) {
			return false;
		}
		if (isSubmerged(world, blockPos)) {
			return false;
		}
		BlockState aboveState = world.getBlockState(blockPos.above());
		return aboveState != null && aboveState.isAir();
	}

	private static List<String> resolveTreeTypesForBiomeAndSeason(ServerLevel world, BlockPos groundPos, String seasonId) {
		if (world == null || groundPos == null) {
			return List.of();
		}

		List<String> treeTypes = new ArrayList<>(2);
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(groundPos);
		if (isSpruceBiome(biomeHolder) && isSpruceSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_SPRUCE);
		}
		if (isBirchBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_BIRCH);
		}
		if (isJungleBiome(biomeHolder) && isJungleSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_JUNGLE);
		}
		if (isMangroveBiome(biomeHolder) && isMangroveSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_MANGROVE);
		}
		if (isAcaciaBiome(biomeHolder) && isAcaciaSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_ACACIA);
		}
		if (isDarkOakBiome(biomeHolder) && isDarkOakSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_DARK_OAK);
		}
		if (isPaleOakBiome(biomeHolder) && isPaleOakSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_PALE_OAK);
		}
		if (isCherryBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_CHERRY);
		}
		if (isOakBiome(biomeHolder) && isOakSeason(seasonId)) {
			treeTypes.add(TREE_TYPE_OAK);
		}
		return treeTypes;
	}

	private static boolean isOakSeason(String seasonId) {
		return "spring".equals(seasonId) || "summer".equals(seasonId) || "fall".equals(seasonId);
	}

	private static boolean isSpruceSeason(String seasonId) {
		return "winter".equals(seasonId) || "spring".equals(seasonId) || "fall".equals(seasonId);
	}

	private static boolean isJungleSeason(String seasonId) {
		return "summer".equals(seasonId);
	}

	private static boolean isMangroveSeason(String seasonId) {
		return "summer".equals(seasonId) || "spring".equals(seasonId) || "fall".equals(seasonId);
	}

	private static boolean isAcaciaSeason(String seasonId) {
		return "spring".equals(seasonId) || "fall".equals(seasonId);
	}

	private static boolean isDarkOakSeason(String seasonId) {
		return "spring".equals(seasonId) || "summer".equals(seasonId);
	}

	private static boolean isPaleOakSeason(String seasonId) {
		return "fall".equals(seasonId) || "winter".equals(seasonId);
	}

	private static double resolveTreeRequiredGrowthTicks(String treeType, String seasonId) {
		if (TREE_TYPE_OAK.equals(treeType)) {
			if ("spring".equals(seasonId)) {
				return randomDaysToTicks(TREE_OAK_SPRING_MIN_DAYS, TREE_OAK_SPRING_MAX_DAYS);
			}
			if ("summer".equals(seasonId)) {
				return randomDaysToTicks(TREE_OAK_SUMMER_MIN_DAYS, TREE_OAK_SUMMER_MAX_DAYS);
			}
			if ("fall".equals(seasonId)) {
				return randomDaysToTicks(TREE_OAK_FALL_MIN_DAYS, TREE_OAK_FALL_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_BIRCH.equals(treeType)) {
			return randomDaysToTicks(TREE_BIRCH_MIN_DAYS, TREE_BIRCH_MAX_DAYS);
		}

		if (TREE_TYPE_JUNGLE.equals(treeType)) {
			if ("summer".equals(seasonId)) {
				return randomDaysToTicks(TREE_JUNGLE_SUMMER_MIN_DAYS, TREE_JUNGLE_SUMMER_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_MANGROVE.equals(treeType)) {
			if ("summer".equals(seasonId)) {
				return randomDaysToTicks(TREE_MANGROVE_SUMMER_MIN_DAYS, TREE_MANGROVE_SUMMER_MAX_DAYS);
			}
			if ("spring".equals(seasonId) || "fall".equals(seasonId)) {
				return randomDaysToTicks(TREE_MANGROVE_SPRING_FALL_MIN_DAYS, TREE_MANGROVE_SPRING_FALL_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_ACACIA.equals(treeType)) {
			if ("spring".equals(seasonId) || "fall".equals(seasonId)) {
				return randomDaysToTicks(TREE_ACACIA_SPRING_FALL_MIN_DAYS, TREE_ACACIA_SPRING_FALL_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_DARK_OAK.equals(treeType)) {
			if ("spring".equals(seasonId) || "summer".equals(seasonId)) {
				return randomDaysToTicks(TREE_DARK_OAK_SPRING_SUMMER_MIN_DAYS, TREE_DARK_OAK_SPRING_SUMMER_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_PALE_OAK.equals(treeType)) {
			if ("fall".equals(seasonId) || "winter".equals(seasonId)) {
				return randomDaysToTicks(TREE_PALE_OAK_FALL_WINTER_MIN_DAYS, TREE_PALE_OAK_FALL_WINTER_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_CHERRY.equals(treeType)) {
			if ("spring".equals(seasonId) || "fall".equals(seasonId)) {
				return randomDaysToTicks(TREE_CHERRY_SPRING_FALL_MIN_DAYS, TREE_CHERRY_SPRING_FALL_MAX_DAYS);
			}
			if ("summer".equals(seasonId) || "winter".equals(seasonId)) {
				return randomDaysToTicks(TREE_CHERRY_SUMMER_WINTER_MIN_DAYS, TREE_CHERRY_SUMMER_WINTER_MAX_DAYS);
			}
			return -1.0d;
		}

		if (TREE_TYPE_SPRUCE.equals(treeType)) {
			if ("winter".equals(seasonId)) {
				return randomDaysToTicks(TREE_SPRUCE_WINTER_MIN_DAYS, TREE_SPRUCE_WINTER_MAX_DAYS);
			}
			if ("spring".equals(seasonId)) {
				return randomDaysToTicks(TREE_SPRUCE_SPRING_MIN_DAYS, TREE_SPRUCE_SPRING_MAX_DAYS);
			}
			if ("fall".equals(seasonId)) {
				return randomDaysToTicks(TREE_SPRUCE_FALL_MIN_DAYS, TREE_SPRUCE_FALL_MAX_DAYS);
			}
			return -1.0d;
		}

		return -1.0d;
	}

	private static double randomDaysToTicks(int minDays, int maxDays) {
		if (minDays <= 0 || maxDays <= 0 || maxDays < minDays) {
			return -1.0d;
		}
		int days = ThreadLocalRandom.current().nextInt(minDays, maxDays + 1);
		return Math.max(1.0d, days * MadokuTime.MINECRAFT_TICKS_PER_CYCLE);
	}

	private static boolean isValidTreeGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String treeType) {
		if (world == null || groundPos == null || groundState == null || treeType == null || treeType.isBlank()) {
			return false;
		}
		if (!isTrackableTreeGroundBlock(groundState)) {
			return false;
		}
		if (isSubmerged(world, groundPos)) {
			return false;
		}

		BlockPos saplingPos = groundPos.above();
		BlockState aboveState = world.getBlockState(saplingPos);
		boolean aboveFree = aboveState.isAir() || aboveState.is(Blocks.SNOW);
		if (!aboveFree) {
			return false;
		}
		return isTreeTypeNaturalForBiome(world, groundPos, treeType);
	}

	private static boolean isTreeTypeNaturalForBiome(ServerLevel world, BlockPos pos, String treeType) {
		if (world == null || pos == null || treeType == null || treeType.isBlank()) {
			return false;
		}

		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (TREE_TYPE_SPRUCE.equals(treeType)) {
			return isSpruceBiome(biomeHolder);
		}
		if (TREE_TYPE_BIRCH.equals(treeType)) {
			return isBirchBiome(biomeHolder);
		}
		if (TREE_TYPE_JUNGLE.equals(treeType)) {
			return isJungleBiome(biomeHolder);
		}
		if (TREE_TYPE_MANGROVE.equals(treeType)) {
			return isMangroveBiome(biomeHolder);
		}
		if (TREE_TYPE_ACACIA.equals(treeType)) {
			return isAcaciaBiome(biomeHolder);
		}
		if (TREE_TYPE_DARK_OAK.equals(treeType)) {
			return isDarkOakBiome(biomeHolder);
		}
		if (TREE_TYPE_PALE_OAK.equals(treeType)) {
			return isPaleOakBiome(biomeHolder);
		}
		if (TREE_TYPE_CHERRY.equals(treeType)) {
			return isCherryBiome(biomeHolder);
		}
		if (TREE_TYPE_OAK.equals(treeType)) {
			return isOakBiome(biomeHolder);
		}
		return false;
	}

	private static boolean isJungleBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.JUNGLE)
			|| biomeHolder.is(Biomes.SPARSE_JUNGLE)
			|| biomeHolder.is(Biomes.BAMBOO_JUNGLE);
	}

	private static boolean isMangroveBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.SWAMP);
	}

	private static boolean isAcaciaBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.SAVANNA)
			|| biomeHolder.is(Biomes.SAVANNA_PLATEAU)
			|| biomeHolder.is(Biomes.WINDSWEPT_SAVANNA);
	}

	private static boolean isDarkOakBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.DARK_FOREST);
	}

	private static boolean isPaleOakBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.PALE_GARDEN);
	}

	private static boolean isCherryBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.CHERRY_GROVE);
	}

	private static boolean isBirchBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.DARK_FOREST)
			|| biomeHolder.is(Biomes.FOREST)
			|| biomeHolder.is(Biomes.BIRCH_FOREST)
			|| biomeHolder.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
			|| biomeHolder.is(Biomes.MEADOW);
	}

	private static boolean isOakBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.BAMBOO_JUNGLE)
			|| biomeHolder.is(Biomes.DARK_FOREST)
			|| biomeHolder.is(Biomes.FOREST)
			|| biomeHolder.is(Biomes.JUNGLE)
			|| biomeHolder.is(Biomes.SPARSE_JUNGLE)
			|| biomeHolder.is(Biomes.PLAINS)
			|| biomeHolder.is(Biomes.RIVER)
			|| biomeHolder.is(Biomes.SAVANNA)
			|| biomeHolder.is(Biomes.SWAMP)
			|| biomeHolder.is(Biomes.WOODED_BADLANDS)
			|| biomeHolder.is(Biomes.WINDSWEPT_FOREST)
			|| biomeHolder.is(Biomes.MEADOW);
	}

	private static boolean isSpruceBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.GROVE)
			|| biomeHolder.is(Biomes.WINDSWEPT_FOREST)
			|| biomeHolder.is(Biomes.TAIGA)
			|| biomeHolder.is(Biomes.SNOWY_PLAINS)
			|| biomeHolder.is(Biomes.SNOWY_TAIGA)
			|| biomeHolder.is(Biomes.OLD_GROWTH_PINE_TAIGA)
			|| biomeHolder.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA);
	}

	private static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType) {
		if (world == null || groundPos == null || treeType == null || treeType.isBlank()) {
			return false;
		}
		BlockPos treePos = groundPos.above();
		BlockState aboveState = world.getBlockState(treePos);
		if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW)) {
			return false;
		}

		BlockState replacedState = aboveState;
		if (aboveState.is(Blocks.SNOW)) {
			world.setBlockAndUpdate(treePos, Blocks.AIR.defaultBlockState());
		}

		ResourceKey<ConfiguredFeature<?, ?>> featureKey = treeFeatureKeyForType(treeType);
		if (featureKey == null) {
			if (replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
				world.setBlockAndUpdate(treePos, replacedState);
			}
			return false;
		}

		HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = world.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
		java.util.Optional<Holder.Reference<ConfiguredFeature<?, ?>>> featureHolder = configuredFeatures.get(featureKey);
		if (featureHolder.isEmpty()) {
			if (replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
				world.setBlockAndUpdate(treePos, replacedState);
			}
			return false;
		}

		boolean placed = featureHolder.get().value().place(world, world.getChunkSource().getGenerator(), world.getRandom(), treePos);
		if (!placed && replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
			world.setBlockAndUpdate(treePos, replacedState);
		}
		return placed;
	}

	private static ResourceKey<ConfiguredFeature<?, ?>> treeFeatureKeyForType(String treeType) {
		if (TREE_TYPE_BIRCH.equals(treeType)) {
			return TreeFeatures.BIRCH;
		}
		if (TREE_TYPE_JUNGLE.equals(treeType)) {
			return TreeFeatures.JUNGLE_TREE;
		}
		if (TREE_TYPE_MANGROVE.equals(treeType)) {
			return TreeFeatures.MANGROVE;
		}
		if (TREE_TYPE_ACACIA.equals(treeType)) {
			return TreeFeatures.ACACIA;
		}
		if (TREE_TYPE_DARK_OAK.equals(treeType)) {
			return TreeFeatures.DARK_OAK;
		}
		if (TREE_TYPE_PALE_OAK.equals(treeType)) {
			return TreeFeatures.PALE_OAK;
		}
		if (TREE_TYPE_CHERRY.equals(treeType)) {
			return TreeFeatures.CHERRY;
		}
		if (TREE_TYPE_SPRUCE.equals(treeType)) {
			return TreeFeatures.SPRUCE;
		}
		return TreeFeatures.OAK;
	}

	private static boolean isSubmerged(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return world.getFluidState(pos).is(FluidTags.WATER) || world.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	private static boolean isAdjacentToSurfaceWater(ServerLevel world, BlockPos blockPos) {
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

	private static boolean spreadWetTrackingFromSeed(ServerLevel world, BlockPos seedPos) {
		if (world == null || seedPos == null) {
			return false;
		}
		Set<Long> seedPositions = new LinkedHashSet<>();
		seedPositions.add(seedPos.asLong());
		return spreadWetTrackingFromSeeds(world, seedPositions);
	}

	private static boolean spreadWetTrackingFromSeeds(ServerLevel world, Set<Long> seedPositions) {
		if (world == null || seedPositions == null || seedPositions.isEmpty()) {
			return false;
		}

		boolean changed = false;
		Set<Long> visited = new LinkedHashSet<>();
		ArrayDeque<SpreadNode> queue = new ArrayDeque<>();
		for (Long packedPos : seedPositions) {
			if (packedPos == null) {
				continue;
			}
			if (!visited.add(packedPos)) {
				continue;
			}
			queue.addLast(new SpreadNode(BlockPos.of(packedPos), 0));
		}

		while (!queue.isEmpty()) {
			SpreadNode current = queue.removeFirst();
			BlockPos currentPos = current.pos();
			if (currentPos == null) {
				continue;
			}

			BlockState currentState = world.getBlockState(currentPos);
			if (isWetTrackedCandidate(world, currentPos, currentState)) {
				changed |= trackDirtCandidateForMode(world, currentPos, currentState, MODE_WET);
			}

			if (current.depth() >= WET_SPREAD_RADIUS_BLOCKS) {
				continue;
			}

			for (Direction direction : HORIZONTAL_DIRECTIONS) {
				BlockPos nextPos = currentPos.relative(direction);
				long nextPackedPos = nextPos.asLong();
				if (visited.add(nextPackedPos)) {
					queue.addLast(new SpreadNode(nextPos, current.depth() + 1));
				}
			}
		}

		return changed;
	}

	private static boolean isSurfaceLevelWater(ServerLevel world, BlockPos waterPos) {
		if (world == null || waterPos == null) {
			return false;
		}
		if (!world.getFluidState(waterPos).is(FluidTags.WATER)) {
			return false;
		}
		if (world.getFluidState(waterPos.above()).is(FluidTags.WATER)) {
			return false;
		}
		int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE, waterPos.getX(), waterPos.getZ()) - 1;
		return waterPos.getY() >= topY;
	}

	private static void requestEcosystemProcessing(MinecraftServer server, long delayTicks) {
		requestEcosystemDiscoveryTask(server, delayTicks);
		requestEcosystemProcessTask(server, delayTicks);
	}

	private static void requestEcosystemDiscoveryTask(MinecraftServer server, long delayTicks) {
		if (server == null) {
			return;
		}
		boolean queuedBefore = isEcosystemTaskQueued(ecosystemDiscoverySchedulerId, TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK);
		if (ecosystemDiscoveryTaskScheduled && queuedBefore) {
			emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK, delayTicks, ecosystemDiscoverySchedulerId, queuedBefore, "already_queued");
			return;
		}
		ecosystemDiscoveryTaskScheduled = false;

		String schedulerId = ensureEcosystemDiscoverySchedulerExists();
		SchedulerManagerSystem.EnqueueStatus firstStatus = enqueueEcosystemTask(schedulerId, delayTicks, TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK);
		if (isAcceptedEnqueueStatus(firstStatus)) {
			ecosystemDiscoveryTaskScheduled = true;
			emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK, delayTicks, schedulerId, queuedBefore, firstStatus.name().toLowerCase());
			return;
		}

		ecosystemDiscoverySchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_DISCOVERY_SCHEDULER_OWNER_ID)
		);
		SchedulerManagerSystem.EnqueueStatus secondStatus = enqueueEcosystemTask(ecosystemDiscoverySchedulerId, delayTicks, TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK);
		if (isAcceptedEnqueueStatus(secondStatus)) {
			ecosystemDiscoveryTaskScheduled = true;
			emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK, delayTicks, ecosystemDiscoverySchedulerId, queuedBefore, secondStatus.name().toLowerCase());
			return;
		}
		emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK, delayTicks, ecosystemDiscoverySchedulerId, queuedBefore, secondStatus.name().toLowerCase());
	}

	private static void requestEcosystemProcessTask(MinecraftServer server, long delayTicks) {
		if (server == null) {
			return;
		}
		boolean queuedBefore = isEcosystemTaskQueued(ecosystemProcessSchedulerId, TASK_TYPE_ECOSYSTEM_PROCESS_TICK);
		if (ecosystemProcessTaskScheduled && queuedBefore) {
			emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_PROCESS_TICK, delayTicks, ecosystemProcessSchedulerId, queuedBefore, "already_queued");
			return;
		}
		ecosystemProcessTaskScheduled = false;

		String schedulerId = ensureEcosystemProcessSchedulerExists();
		SchedulerManagerSystem.EnqueueStatus firstStatus = enqueueEcosystemTask(schedulerId, delayTicks, TASK_TYPE_ECOSYSTEM_PROCESS_TICK);
		if (isAcceptedEnqueueStatus(firstStatus)) {
			ecosystemProcessTaskScheduled = true;
			emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_PROCESS_TICK, delayTicks, schedulerId, queuedBefore, firstStatus.name().toLowerCase());
			return;
		}

		ecosystemProcessSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_PROCESS_SCHEDULER_OWNER_ID)
		);
		SchedulerManagerSystem.EnqueueStatus secondStatus = enqueueEcosystemTask(ecosystemProcessSchedulerId, delayTicks, TASK_TYPE_ECOSYSTEM_PROCESS_TICK);
		if (isAcceptedEnqueueStatus(secondStatus)) {
			ecosystemProcessTaskScheduled = true;
			emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_PROCESS_TICK, delayTicks, ecosystemProcessSchedulerId, queuedBefore, secondStatus.name().toLowerCase());
			return;
		}
		emitEcosystemScheduleRequestDebug(TASK_TYPE_ECOSYSTEM_PROCESS_TICK, delayTicks, ecosystemProcessSchedulerId, queuedBefore, secondStatus.name().toLowerCase());
	}

	private static boolean isEcosystemTaskQueued(String schedulerIdInput, String taskType) {
		String schedulerId = schedulerIdInput == null ? "" : schedulerIdInput.trim();
		if (schedulerId.isEmpty()) {
			return false;
		}
		return SchedulerManagerSystem.hasQueuedTask(schedulerId, taskType);
	}

	private static String ensureEcosystemDiscoverySchedulerExists() {
		if (ecosystemDiscoverySchedulerId == null || ecosystemDiscoverySchedulerId.isBlank()) {
			ecosystemDiscoverySchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_DISCOVERY_SCHEDULER_OWNER_ID)
			);
		}
		return ecosystemDiscoverySchedulerId;
	}

	private static String ensureEcosystemProcessSchedulerExists() {
		if (ecosystemProcessSchedulerId == null || ecosystemProcessSchedulerId.isBlank()) {
			ecosystemProcessSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_PROCESS_SCHEDULER_OWNER_ID)
			);
		}
		return ecosystemProcessSchedulerId;
	}

	private static SchedulerManagerSystem.EnqueueStatus enqueueEcosystemTask(String schedulerId, long delayTicks, String taskType) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return SchedulerManagerSystem.EnqueueStatus.SCHEDULER_NOT_FOUND;
		}

		return SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			taskType,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
	}

	private static boolean isAcceptedEnqueueStatus(SchedulerManagerSystem.EnqueueStatus status) {
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static void emitEcosystemSchedulerTickDebug(
		MinecraftServer server,
		SchedulerManagerSystem.TaskContext context,
		String phase
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.scheduler_tick")) {
			return;
		}

		MadokuDebug.event("ecosystem.scheduler_tick", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(server != null && server.overworld() != null ? server.overworld().dimension().toString() : "")
			.subject("ecosystem_scheduler")
			.field("phase", phase == null || phase.isBlank() ? "unknown" : phase)
			.field("scheduler_id", context == null ? "" : context.getSchedulerId())
			.field("request_id", context == null ? -1L : context.getRequestId())
			.field("task_discovery_scheduled", ecosystemDiscoveryTaskScheduled)
			.field("task_process_scheduled", ecosystemProcessTaskScheduled)
			.field("task_discovery_queued", isEcosystemTaskQueued(ecosystemDiscoverySchedulerId, TASK_TYPE_ECOSYSTEM_DISCOVERY_TICK))
			.field("task_process_queued", isEcosystemTaskQueued(ecosystemProcessSchedulerId, TASK_TYPE_ECOSYSTEM_PROCESS_TICK))
			.log();
	}

	private static void emitEcosystemScheduleRequestDebug(
		String taskType,
		long delayTicks,
		String schedulerId,
		boolean queuedBefore,
		String outcome
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.scheduler_enqueue")) {
			return;
		}

		MadokuDebug.event("ecosystem.scheduler_enqueue", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.subject("ecosystem_scheduler")
			.field("task_type", taskType == null ? "" : taskType)
			.field("delay_ticks", Math.max(0L, delayTicks))
			.field("scheduler_id", schedulerId == null ? "" : schedulerId)
			.field("queued_before", queuedBefore)
			.field("task_discovery_scheduled", ecosystemDiscoveryTaskScheduled)
			.field("task_process_scheduled", ecosystemProcessTaskScheduled)
			.field("outcome", outcome == null || outcome.isBlank() ? "unknown" : outcome)
			.log();
	}

	private static DirtState putDirtState(String key, DirtState value) {
		DirtState previous = dirtBlocksByKey.put(key, value);
		if (previous != null) {
			removeChunkIndex(dirtKeysByChunk, chunkRefForPos(previous.levelId, previous.dirtPos), key);
		}
		if (value != null) {
			addChunkIndex(dirtKeysByChunk, chunkRefForPos(value.levelId, value.dirtPos), key);
		}
		return previous;
	}

	private static DirtState removeDirtStateByKey(String key) {
		DirtState removed = dirtBlocksByKey.remove(key);
		if (removed != null) {
			removeChunkIndex(dirtKeysByChunk, chunkRefForPos(removed.levelId, removed.dirtPos), key);
		}
		return removed;
	}

	private static TreeCandidateState putTreeCandidate(ChunkRefKey chunkKey, TreeCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return null;
		}

		TreeCandidateState previous = treeCandidatesByChunk.put(chunkKey, candidate);
		ChunkManagerSystem.trackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		return previous;
	}

	private static TreeCandidateState removeTreeCandidate(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}

		TreeCandidateState removed = treeCandidatesByChunk.remove(chunkKey);
		if (removed == null) {
			return null;
		}

		Set<String> dirtKeys = dirtKeysByChunk.get(chunkKey);
		if (dirtKeys == null || dirtKeys.isEmpty()) {
			ChunkManagerSystem.untrackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}
		return removed;
	}

	private static void addChunkIndex(Map<ChunkRefKey, Set<String>> indexMap, ChunkRefKey chunkKey, String entryKey) {
		if (indexMap == null || chunkKey == null || entryKey == null || entryKey.isBlank()) {
			return;
		}
		indexMap.computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>()).add(entryKey);
		ChunkManagerSystem.trackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
	}

	private static void removeChunkIndex(Map<ChunkRefKey, Set<String>> indexMap, ChunkRefKey chunkKey, String entryKey) {
		if (indexMap == null || chunkKey == null || entryKey == null || entryKey.isBlank()) {
			return;
		}
		Set<String> keys = indexMap.get(chunkKey);
		if (keys == null) {
			return;
		}
		keys.remove(entryKey);
		if (!keys.isEmpty()) {
			return;
		}
		indexMap.remove(chunkKey);
		if (!treeCandidatesByChunk.containsKey(chunkKey)) {
			ChunkManagerSystem.untrackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}
	}

	private static String dirtKey(ServerLevel world, BlockPos dirtPos) {
		return levelId(world) + "|" + (dirtPos == null ? -1L : dirtPos.asLong());
	}

	private static String levelId(ServerLevel world) {
		return ChunkManagerSystem.normalizeLevelId(world);
	}

	private static ChunkRefKey chunkRefForPos(String levelId, long packedBlockPos) {
		return new ChunkRefKey(levelId, BlockPos.getX(packedBlockPos) >> 4, BlockPos.getZ(packedBlockPos) >> 4);
	}

	private static long resolveAbsoluteDayTime(ServerLevel world) {
		if (world == null) {
			return MadokuTime.getCurrentAbsoluteDayTime();
		}
		return MadokuTime.getCurrentAbsoluteDayTime(world);
	}

	private static long normalizePreviousAbsoluteTick(long previousAbsoluteTick, long currentAbsoluteTick) {
		long safePrevious = Math.max(0L, previousAbsoluteTick);
		long safeCurrent = Math.max(0L, currentAbsoluteTick);
		if (safePrevious > safeCurrent + ABSOLUTE_TIME_ROLLBACK_RESET_TICKS) {
			return safeCurrent;
		}
		return safePrevious;
	}

	private static double resolveWetRequiredGrowthTicks() {
		int days = ThreadLocalRandom.current().nextInt(WET_MIN_DAYS, WET_MAX_DAYS + 1);
		return Math.max(1.0d, days * MadokuTime.MINECRAFT_TICKS_PER_CYCLE);
	}

	private static double resolveSurfaceDirtRequiredGrowthTicks(ServerLevel world) {
		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		int minDays = SURFACE_SUMMER_FALL_MIN_DAYS;
		int maxDays = SURFACE_SUMMER_FALL_MAX_DAYS;
		if ("spring".equals(seasonId)) {
			minDays = SURFACE_SPRING_MIN_DAYS;
			maxDays = SURFACE_SPRING_MAX_DAYS;
		} else if ("winter".equals(seasonId)) {
			minDays = SURFACE_WINTER_MIN_DAYS;
			maxDays = SURFACE_WINTER_MAX_DAYS;
		}
		int days = ThreadLocalRandom.current().nextInt(minDays, maxDays + 1);
		return Math.max(1.0d, days * MadokuTime.MINECRAFT_TICKS_PER_CYCLE);
	}

	private static String normalizeSeasonId(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase();
	}

	private static void emitTreeCandidatePickedDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		int optionCount
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_picked")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_candidate_picked", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("season", candidate == null ? "unknown" : candidate.initialSeasonId)
			.field("ground_pos", formatBlockPos(pos))
			.field("required_ticks", candidate == null ? 0.0d : candidate.requiredGrowthTicks)
			.field("candidate_options", optionCount)
			.log();
	}

	private static void emitTreeChunkDiscoveryDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		int treeGroundCandidateCount,
		int wetSeedCount
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_chunk_discovery")) {
			return;
		}

		MadokuDebug.event("ecosystem.tree_chunk_discovery", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_ground_candidates", Math.max(0, treeGroundCandidateCount))
			.field("wet_seed_positions", Math.max(0, wetSeedCount))
			.log();
	}

	private static void emitTreeCandidateScanDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		String seasonId,
		int treeGroundCandidateCount,
		int optionCount,
		String action
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_scan")) {
			return;
		}

		MadokuDebug.event("ecosystem.tree_candidate_scan", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("season", seasonId == null || seasonId.isBlank() ? "unknown" : seasonId)
			.field("tree_ground_candidates", Math.max(0, treeGroundCandidateCount))
			.field("options", Math.max(0, optionCount))
			.field("action", action == null || action.isBlank() ? "unknown" : action)
			.log();
	}

	private static void emitTreeCandidateProgressDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		long elapsedTicks
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_progress")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_candidate_progress", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("ground_pos", formatBlockPos(pos))
			.field("elapsed_ticks", Math.max(0L, elapsedTicks))
			.field("progress_ticks", candidate == null ? 0.0d : candidate.progressGrowthTicks)
			.field("required_ticks", candidate == null ? 0.0d : candidate.requiredGrowthTicks)
			.log();
	}

	private static void emitTreeCandidateInvalidatedDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		String reason
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_invalidated")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_candidate_invalidated", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("reason", reason == null ? "unknown" : reason)
			.field("ground_pos", formatBlockPos(pos))
			.log();
	}

	private static void emitTreeGrowthResultDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		boolean grown
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_growth_result")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_growth_result", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("ground_pos", formatBlockPos(pos))
			.field("season", candidate == null ? "unknown" : candidate.initialSeasonId)
			.field("progress_ticks", candidate == null ? 0.0d : candidate.progressGrowthTicks)
			.field("required_ticks", candidate == null ? 0.0d : candidate.requiredGrowthTicks)
			.field("grown", grown)
			.log();
	}

	private static String formatBlockPos(BlockPos pos) {
		if (pos == null) {
			return "unknown";
		}
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static Block resolveWetGroundReplacementBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return Blocks.SAND;
		}

		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (biomeHolder.is(BiomeTags.IS_JUNGLE) || biomeHolder.is(Biomes.SWAMP) || biomeHolder.is(Biomes.MANGROVE_SWAMP)) {
			return Blocks.MUD;
		}
		if (biomeHolder.is(BiomeTags.IS_BADLANDS)) {
			return Blocks.RED_SAND;
		}
		return Blocks.SAND;
	}

	private static Block resolveSurfaceDirtGrowthBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return Blocks.GRASS_BLOCK;
		}

		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (biomeHolder.is(Biomes.MUSHROOM_FIELDS)) {
			return Blocks.MYCELIUM;
		}
		if (biomeHolder.is(BiomeTags.IS_TAIGA) || biomeHolder.is(BiomeTags.IS_JUNGLE)) {
			return Blocks.PODZOL;
		}
		return Blocks.GRASS_BLOCK;
	}

	private static void applyPersistedData(JsonObject source) {
		dirtBlocksByKey.clear();
		dirtKeysByChunk.clear();
		treeCandidatesByChunk.clear();
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_ID);

		if (source == null) {
			return;
		}

		JsonElement dirtBlocksElement = source.get(FIELD_GROUND_BLOCKS);
		if (dirtBlocksElement != null && dirtBlocksElement.isJsonArray()) {
			for (JsonElement element : dirtBlocksElement.getAsJsonArray()) {
				DirtState dirt = DirtState.fromJson(element);
				if (dirt == null) {
					continue;
				}
				putDirtState(dirt.key(), dirt);
			}
		}

		JsonElement treeCandidatesElement = source.get(FIELD_TREE_CANDIDATES);
		if (treeCandidatesElement != null && treeCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeCandidatesElement.getAsJsonArray()) {
				TreeCandidateState candidate = TreeCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add(FIELD_GROUND_BLOCKS, new JsonArray());
		root.add(FIELD_TREE_CANDIDATES, new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		JsonArray dirtBlocks = new JsonArray();
		for (DirtState dirt : dirtBlocksByKey.values()) {
			if (dirt != null) {
				dirtBlocks.add(dirt.toJson());
			}
		}
		root.add(FIELD_GROUND_BLOCKS, dirtBlocks);

		JsonArray treeCandidates = new JsonArray();
		for (TreeCandidateState candidate : treeCandidatesByChunk.values()) {
			if (candidate != null) {
				treeCandidates.add(candidate.toJson());
			}
		}
		root.add(FIELD_TREE_CANDIDATES, treeCandidates);
		return root;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (UnsupportedOperationException | IllegalStateException | NumberFormatException ignored) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (UnsupportedOperationException | IllegalStateException | NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value;
		} catch (UnsupportedOperationException | IllegalStateException ignored) {
			return fallback;
		}
	}

	private record ChunkRefKey(String levelId, int chunkX, int chunkZ) {
	}

	private record SpreadNode(BlockPos pos, int depth) {
	}

	private record TreeCandidateOption(long groundPos, String treeType) {
	}

	private static final class TreeCandidateState {
		private final String levelId;
		private final int chunkX;
		private final int chunkZ;
		private final long groundPos;
		private final String treeType;
		private final String initialSeasonId;
		private final double requiredGrowthTicks;
		private double progressGrowthTicks;
		private long lastProcessedAbsoluteDayTime;

		private TreeCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String treeType,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			if (TREE_TYPE_SPRUCE.equals(treeType)) {
				this.treeType = TREE_TYPE_SPRUCE;
			} else if (TREE_TYPE_BIRCH.equals(treeType)) {
				this.treeType = TREE_TYPE_BIRCH;
			} else if (TREE_TYPE_JUNGLE.equals(treeType)) {
				this.treeType = TREE_TYPE_JUNGLE;
			} else if (TREE_TYPE_MANGROVE.equals(treeType)) {
				this.treeType = TREE_TYPE_MANGROVE;
			} else if (TREE_TYPE_ACACIA.equals(treeType)) {
				this.treeType = TREE_TYPE_ACACIA;
			} else if (TREE_TYPE_DARK_OAK.equals(treeType)) {
				this.treeType = TREE_TYPE_DARK_OAK;
			} else if (TREE_TYPE_PALE_OAK.equals(treeType)) {
				this.treeType = TREE_TYPE_PALE_OAK;
			} else if (TREE_TYPE_CHERRY.equals(treeType)) {
				this.treeType = TREE_TYPE_CHERRY;
			} else {
				this.treeType = TREE_TYPE_OAK;
			}
			this.initialSeasonId = normalizeSeasonId(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_LEVEL_ID, levelId);
			root.addProperty(FIELD_CHUNK_X, chunkX);
			root.addProperty(FIELD_CHUNK_Z, chunkZ);
			root.addProperty(FIELD_TREE_GROUND_POS, groundPos);
			root.addProperty(FIELD_TREE_TYPE, treeType);
			root.addProperty(FIELD_INITIAL_SEASON_ID, initialSeasonId);
			root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks);
			root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static TreeCandidateState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			int chunkX = (int) getLong(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
			int chunkZ = (int) getLong(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
			if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
				return null;
			}
			long groundPos = getLong(source, FIELD_TREE_GROUND_POS, Long.MIN_VALUE);
			if (groundPos == Long.MIN_VALUE) {
				return null;
			}
			String treeType = getString(source, FIELD_TREE_TYPE, TREE_TYPE_OAK);
			String initialSeasonId = normalizeSeasonId(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, resolveTreeRequiredGrowthTicks(treeType, initialSeasonId))
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new TreeCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				treeType,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	private static final class DirtState {
		private final String levelId;
		private final long dirtPos;
		private final String mode;
		private double requiredGrowthTicks;
		private double progressGrowthTicks;
		private long lastProcessedAbsoluteDayTime;

		private DirtState(
			String levelId,
			long dirtPos,
			String mode,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.dirtPos = dirtPos;
			this.mode = MODE_SURFACE_DIRT.equals(mode) ? MODE_SURFACE_DIRT : MODE_WET;
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		private String key() {
			return levelId + "|" + dirtPos;
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_LEVEL_ID, levelId);
			root.addProperty(FIELD_BLOCK_POS, dirtPos);
			root.addProperty(FIELD_MODE, mode);
			root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks);
			root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static DirtState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}
			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			long dirtPos = getLong(source, FIELD_BLOCK_POS, Long.MIN_VALUE);
			if (dirtPos == Long.MIN_VALUE) {
				return null;
			}
			String mode = getString(source, FIELD_MODE, MODE_WET);
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				MODE_SURFACE_DIRT.equals(mode) ? 3.0d * MadokuTime.MINECRAFT_TICKS_PER_CYCLE : resolveWetRequiredGrowthTicks()
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new DirtState(
				levelId,
				dirtPos,
				mode,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}
}
