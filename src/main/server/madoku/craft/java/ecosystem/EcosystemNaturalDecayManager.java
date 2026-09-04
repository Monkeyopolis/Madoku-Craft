package madoku.craft.java.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.chunk.ChunkAPIManager;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class EcosystemNaturalDecayManager {
	public static final String CHUNK_PROCESSOR_ID = "ecosystem_natural_decay";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalDecayManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "madoku-natural-decay";

	private static volatile NaturalDecayConfigManager.Settings settings = NaturalDecayConfigManager.defaults();
	private static final Predicate<BlockState> NATURAL_LEAF_STATE = state -> state != null && state.is(BlockTags.LEAVES);
	static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, EcosystemAPIManager.TreeDecayCandidateState>> treeDecayCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, Long>> treeDecayTargetOwnersByChunk = new LinkedHashMap<>();

	private static final ChunkAPIManager.ChunkProcessor CHUNK_PROCESSOR = new ChunkAPIManager.ChunkProcessor() {
		@Override
		public boolean acceptsRandomPosition(ServerLevel level, BlockPos position) {
			return (EcosystemAPIManager.candidateMaskAt(level, position) & EcosystemAPIManager.CANDIDATE_DECAY) != 0;
		}

		@Override
		public void handleRandomPosition(ServerLevel level, BlockPos position, RandomSource random) {
			EcosystemNaturalDecayManager.handleRandomPosition(level, position);
		}
	};

	private EcosystemNaturalDecayManager() {
	}

	public static void initialize() {
		loadConfig();
		ChunkAPIManager.registerChunkProcessor(CHUNK_PROCESSOR_ID, CHUNK_PROCESSOR);
	}

	public static void reset() {
		clearTrackedCandidateState();
	}

	static void clearTrackedCandidateState() {
		treeDecayCandidatesByChunk.clear();
		treeDecayTargetOwnersByChunk.clear();
	}

	static void evictTrackedCandidateState(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.remove(chunkKey);
		treeDecayTargetOwnersByChunk.remove(chunkKey);
		if (candidates != null) {
			for (EcosystemAPIManager.TreeDecayCandidateState candidate : candidates.values()) {
				if (candidate != null) {
					EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY);
				}
			}
		}
		EcosystemAPIManager.syncChunkProcessorTracking(chunkKey);
	}

	static Set<EcosystemAPIManager.ChunkRefKey> collectTrackedChunkKeys() {
		return new java.util.LinkedHashSet<>(treeDecayCandidatesByChunk.keySet());
	}

	static Collection<EcosystemAPIManager.TreeDecayCandidateState> getTreeDecayCandidates(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : treeDecayCandidatesByChunk.getOrDefault(chunkKey, Map.of()).values();
	}

	static void putTreeDecayCandidate(EcosystemAPIManager.ChunkRefKey chunkKey, EcosystemAPIManager.TreeDecayCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		if (candidates.containsKey(candidate.leafPos)) return;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		if (targetOwners.containsKey(candidate.targetPos)) return;
		candidates.put(candidate.leafPos, candidate);
		targetOwners.put(candidate.targetPos, candidate.leafPos);
		EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY);
		syncChunkProcessorTracking(chunkKey);
	}

	static void syncChunkProcessorTracking(EcosystemAPIManager.ChunkRefKey chunkKey) {
		EcosystemAPIManager.syncChunkProcessorTracking(chunkKey);
	}

	static JsonObject createChunkPersistedData(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		Collection<EcosystemAPIManager.TreeDecayCandidateState> treeDecayCandidateList = getTreeDecayCandidates(chunkKey);
		if (treeDecayCandidateList == null || treeDecayCandidateList.isEmpty()) {
			return null;
		}

		JSONFormatAPIManager.ArrayBuilder treeDecayCandidates = JSONFormatAPIManager.array();
		for (EcosystemAPIManager.TreeDecayCandidateState candidate : treeDecayCandidateList) {
			if (candidate != null) {
				treeDecayCandidates.add(candidate.toJson());
			}
		}

		return EcosystemAPIManager.buildChunkPersistedData(builder -> builder
			.put("tree-decay-candidates", treeDecayCandidates.build()));
	}

	static void applyPersistedData(JsonObject source) {
		if (source == null || source.isJsonNull()) {
			return;
		}

		JsonElement treeDecayCandidatesElement = source.get("tree-decay-candidates");
		if (treeDecayCandidatesElement != null && treeDecayCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeDecayCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.TreeDecayCandidateState candidate = EcosystemAPIManager.TreeDecayCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeDecayCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	static boolean tryApplyTreeDecayAtTarget(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null) {
			return false;
		}

		Block leafLitter = EcosystemConfigManager.resolveBlock(EcosystemAPIManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return false;
		}

		BlockState current = world.getBlockState(targetPos);
		if (current == null) {
			return false;
		}

		if (current.isAir()) {
			BlockState placed = setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
			if (!placed.canSurvive(world, targetPos)) {
				return false;
			}
			world.setBlockAndUpdate(targetPos, placed);
			EcosystemAPIManager.invalidateCachedGroundPosition();
			return true;
		}
		if (current.getBlock() != leafLitter) {
			return false;
		}

		int amount = getLeafLitterAmount(current);
		int maxAmount = getLeafLitterMaxAmount(current);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = setLeafLitterAmount(current, amount + 1);
		if (updated == current) {
			return false;
		}
		world.setBlockAndUpdate(targetPos, updated);
		EcosystemAPIManager.invalidateCachedGroundPosition();
		return true;
	}

	static boolean isNaturallyGeneratedLeaf(BlockState state) {
		if (state == null || !state.hasProperty(LeavesBlock.PERSISTENT)) {
			return false;
		}
		Boolean persistent = state.getValue(LeavesBlock.PERSISTENT);
		return persistent == null || !persistent;
	}

	static int getLeafLitterAmount(BlockState state) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return 1;
		}
		Integer value = state.getValue(amountProperty);
		return value == null ? 1 : Math.max(1, value);
	}

	static int getLeafLitterMaxAmount(BlockState state) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null) {
			return 4;
		}
		int max = 1;
		for (Integer value : amountProperty.getPossibleValues()) {
			if (value != null && value > max) {
				max = value;
			}
		}
		return Math.max(1, max);
	}

	static IntegerProperty findLeafLitterAmountProperty(BlockState state) {
		if (state == null) {
			return null;
		}
		IntegerProperty fallback = null;
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (!(property instanceof IntegerProperty integerProperty)) {
				continue;
			}
			if ("segment_amount".equals(integerProperty.getName())) {
				return integerProperty;
			}
			if (fallback == null && NaturalGrowthConfigManager.propertyNameLooksLikeAmount(integerProperty.getName())) {
				fallback = integerProperty;
			}
		}
		return fallback;
	}

	static BlockState setLeafLitterAmount(BlockState state, int targetAmount) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return state;
		}

		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (Integer value : amountProperty.getPossibleValues()) {
			if (value == null) {
				continue;
			}
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) {
			return state;
		}

		int clamped = Math.max(min, Math.min(max, targetAmount));
		return state.setValue(amountProperty, clamped);
	}

	public static NaturalDecayConfigManager.Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return EcosystemAPIManager.isEnabled() && settings.isEnabled();
	}

	public static void syncChunkProcessorActivation() {
		ChunkAPIManager.setChunkProcessorActive(CHUNK_PROCESSOR_ID, isEnabled());
	}

	/** Discovers decay in the same column selected for Growth and Erosion. */
	static void discoverColumn(
		ServerLevel world,
		LevelChunk chunk,
		int chunkX,
		int chunkZ,
		int columnIndex,
		EcosystemAPIManager.DiscoveryChunkState discoveryState
	) {
		if (world == null || chunk == null || discoveryState == null || !isEnabled()) {
			return;
		}

		LevelChunkSection[] sections = chunk.getSections();
		int localX = columnIndex & 15;
		int localZ = columnIndex >> 4;
		BlockPos cachedColumnTarget = null;
		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			LevelChunkSection section = sections[sectionIndex];
			if (section == null || !section.maybeHas(NATURAL_LEAF_STATE)) {
				continue;
			}

			for (int localY = 0; localY < 16; localY++) {
				BlockState state = section.getBlockState(localX, localY, localZ);
				if (state == null || !state.is(BlockTags.LEAVES) || !isNaturallyGeneratedLeaf(state)) {
					continue;
				}
				int sectionY = world.getMinSectionY() + sectionIndex;
				BlockPos leafPos = new BlockPos(
					(chunkX << 4) + localX,
					(sectionY << 4) + localY,
					(chunkZ << 4) + localZ
				);
				BlockPos targetPos = cachedColumnTarget;
				if (targetPos == null) {
					targetPos = resolveTreeDecayTargetPos(world, leafPos, state);
					if (targetPos != null) {
						cachedColumnTarget = targetPos;
					}
				}
				if (targetPos != null) {
					pickTreeDecayCandidateForPosition(world, chunkX, chunkZ, leafPos.asLong(), targetPos.asLong());
				}
			}
		}
		discoveryState.decaySectionIndex = sections.length;
		discoveryState.decayBlockIndex = 0;
	}

	static void handleRandomPosition(ServerLevel world, BlockPos position) {
		// Random ticking may only advance an existing source-leaf candidate.
		// Leaf/target discovery belongs exclusively to discoverChunk.
		if (world == null || position == null || !isEnabled()) {
			return;
		}
		long currentAbsoluteDayTime = EcosystemAPIManager.resolveCachedAbsoluteDayTime(world);
		processTreeDecayCandidateInChunk(
			world,
			position.getX() >> 4,
			position.getZ() >> 4,
			currentAbsoluteDayTime,
			position.asLong()
		);
	}

	static double resolveTreeDecayRequiredTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			 normalizedSeasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalDecaySettings.treeDecayForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static BlockPos resolveTreeDecayTargetPos(ServerLevel world, BlockPos leafPos, BlockState leafState) {
		if (world == null || leafPos == null || leafState == null || !EcosystemAPIManager.isNaturalDecayEnabled()) {
			return null;
		}
		if (!leafState.is(BlockTags.LEAVES) || !EcosystemAPIManager.isNaturallyGeneratedLeaf(leafState)) {
			return null;
		}
		return resolveLeafLitterTargetPos(world, leafPos);
	}

	static boolean isValidTreeDecayTargetCandidate(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null || !EcosystemAPIManager.isNaturalDecayEnabled()) {
			return false;
		}
		Block leafLitter = EcosystemConfigManager.resolveBlock(EcosystemAPIManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return false;
		}

		BlockState state = world.getBlockState(targetPos);
		if (state == null) {
			return false;
		}
		if (state.getBlock() == leafLitter) {
			return hasLeafLitterSupportBlock(world, targetPos)
				&& EcosystemAPIManager.getLeafLitterAmount(state) < EcosystemAPIManager.getLeafLitterMaxAmount(state);
		}
		if (state.isAir()) {
			BlockState placed = EcosystemAPIManager.setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
			return hasLeafLitterSupportBlock(world, targetPos)
				&& placed.canSurvive(world, targetPos);
		}
		return false;
	}

	static void pickTreeDecayCandidateForPosition(ServerLevel world, int chunkX, int chunkZ, long leafPos, long targetPos) {
		if (world == null || !EcosystemAPIManager.isNaturalDecayEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> existingCandidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (leafPos == Long.MIN_VALUE || targetPos == Long.MIN_VALUE || !isValidTreeDecayTargetCandidate(world, BlockPos.of(targetPos))) {
			return;
		}
		if (existingCandidates != null && existingCandidates.containsKey(leafPos)) return;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.get(chunkKey);
		if (targetOwners != null && targetOwners.containsKey(targetPos)) return;

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		double requiredDecayTicks = resolveTreeDecayRequiredTicks(world, seasonId);
		if (requiredDecayTicks <= 0.0d) {
			return;
		}

		putTreeDecayCandidate(chunkKey, new EcosystemAPIManager.TreeDecayCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				leafPos,
				targetPos,
				seasonId,
				requiredDecayTicks,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world)
		));
		EcosystemAPIManager.dirty = true;
	}

	private static double randomDaysToTicks(EcosystemConfigManager.DayRange range) {
		if (range == null) {
			return -1.0d;
		}
		return EcosystemNaturalGrowthManager.randomDaysToTicks(range);
	}

	private static BlockPos resolveLeafLitterTargetPos(ServerLevel world, BlockPos leafPos) {
		if (world == null || leafPos == null) {
			return null;
		}

		Block leafLitter = EcosystemConfigManager.resolveBlock(EcosystemAPIManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return null;
		}
		BlockState singleLitter = EcosystemAPIManager.setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
		int minY = Math.max(world.getMinY() + 1, leafPos.getY() - EcosystemAPIManager.TREE_DECAY_MAX_DROP_DISTANCE);

		for (int y = leafPos.getY() - 1; y >= minY; y--) {
			BlockPos pos = new BlockPos(leafPos.getX(), y, leafPos.getZ());
			BlockState state = world.getBlockState(pos);
			if (state == null) {
				continue;
			}

			if (state.is(BlockTags.LEAVES)) {
				continue;
			}

			if (state.getBlock() == leafLitter) {
				return hasLeafLitterSupportBlock(world, pos)
					&& EcosystemAPIManager.getLeafLitterAmount(state) < EcosystemAPIManager.getLeafLitterMaxAmount(state)
					? pos
					: null;
			}

			if (!state.isAir()) {
				continue;
			}

			if (hasLeafLitterSupportBlock(world, pos) && singleLitter.canSurvive(world, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean hasLeafLitterSupportBlock(ServerLevel world, BlockPos litterPos) {
		if (world == null || litterPos == null) {
			return false;
		}
		BlockState belowState = world.getBlockState(litterPos.below());
		return belowState != null && EcosystemAPIManager.LEAF_LITTER_SUPPORT_BLOCKS.contains(belowState.getBlock());
	}

	static void processTreeDecayCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		processTreeDecayCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime, Long.MIN_VALUE);
	}

	static void processTreeDecayCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime, long selectedTargetPosition) {
		if (world == null || !isEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.get(chunkKey);
		Iterator<EcosystemAPIManager.TreeDecayCandidateState> iterator = candidates.values().iterator();
		while (iterator.hasNext()) {
			EcosystemAPIManager.TreeDecayCandidateState candidate = iterator.next();
			if (candidate == null) {
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}
			if (!candidate.levelId.equals(EcosystemAPIManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			BlockPos targetPos = BlockPos.of(candidate.targetPos);
			if (selectedTargetPosition != Long.MIN_VALUE && candidate.leafPos != selectedTargetPosition) {
				continue;
			}

			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressDecayTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredDecayTicks
			);
			boolean progressChanged = candidate.progressDecayTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressDecayTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
			if (progressChanged) {
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
			double currentProgress = advanced.progressGrowthTicks();
			if (currentProgress + 1e-6d < candidate.requiredDecayTicks) {
				continue;
			}

			if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY);
				iterator.remove();
				if (targetOwners != null) targetOwners.remove(candidate.targetPos);
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			if (currentProgress + 1e-6d >= candidate.requiredDecayTicks) {
				boolean applied = tryApplyTreeDecayAtTarget(world, targetPos);
				if (applied) {
					EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY);
					iterator.remove();
					if (targetOwners != null) targetOwners.remove(candidate.targetPos);
					removedAny = true;
					EcosystemAPIManager.markChunkDirty(chunkKey);
				}
			}
		}

		if (candidates.isEmpty()) {
			treeDecayCandidatesByChunk.remove(chunkKey);
			treeDecayTargetOwnersByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
		}
	}

	private static void loadConfig() {
		NaturalDecayConfigManager.Settings fallback = NaturalDecayConfigManager.defaults();
		JsonObject defaults = NaturalDecayConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, defaults);
			settings = NaturalDecayConfigManager.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, NaturalDecayConfigManager.toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalDecayManager config; using defaults.", exception);
		}
	}

}
