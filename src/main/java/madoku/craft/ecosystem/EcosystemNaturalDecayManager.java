package madoku.craft.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.api.time.MadokuTimeManager;
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
	private static final String CONFIG_FILE_NAME = "natural-decay";

	private static volatile NaturalDecayConfigManager.Settings settings = NaturalDecayConfigManager.defaults();
	private static final Predicate<BlockState> NATURAL_LEAF_STATE = state -> state != null && state.is(BlockTags.LEAVES);
	static final Map<MadokuEcosystemManager.ChunkRefKey, Map<Long, MadokuEcosystemManager.TreeDecayCandidateState>> treeDecayCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<MadokuEcosystemManager.ChunkRefKey, Map<Long, Long>> treeDecayTargetOwnersByChunk = new LinkedHashMap<>();

	private static final MadokuChunkManager.ChunkProcessor CHUNK_PROCESSOR = new MadokuChunkManager.ChunkProcessor() {
		@Override
		public boolean acceptsRandomPosition(ServerLevel level, BlockPos position) {
			return (MadokuEcosystemManager.candidateMaskAt(level, position) & MadokuEcosystemManager.CANDIDATE_DECAY) != 0;
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
		MadokuChunkManager.registerChunkProcessor(CHUNK_PROCESSOR_ID, CHUNK_PROCESSOR);
	}

	public static void reset() {
		clearTrackedCandidateState();
	}

	static void clearTrackedCandidateState() {
		treeDecayCandidatesByChunk.clear();
		treeDecayTargetOwnersByChunk.clear();
	}

	static Set<MadokuEcosystemManager.ChunkRefKey> collectTrackedChunkKeys() {
		return new java.util.LinkedHashSet<>(treeDecayCandidatesByChunk.keySet());
	}

	static Collection<MadokuEcosystemManager.TreeDecayCandidateState> getTreeDecayCandidates(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : treeDecayCandidatesByChunk.getOrDefault(chunkKey, Map.of()).values();
	}

	static void putTreeDecayCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey, MadokuEcosystemManager.TreeDecayCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		Map<Long, MadokuEcosystemManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		if (candidates.containsKey(candidate.leafPos)) return;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		if (targetOwners.containsKey(candidate.targetPos)) return;
		candidates.put(candidate.leafPos, candidate);
		targetOwners.put(candidate.targetPos, candidate.leafPos);
		MadokuEcosystemManager.addCandidatePositionBit(candidate.levelId, candidate.leafPos, MadokuEcosystemManager.CANDIDATE_DECAY);
		syncChunkProcessorTracking(chunkKey);
	}

	static void syncChunkProcessorTracking(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		MadokuEcosystemManager.syncChunkProcessorTracking(chunkKey);
	}

	static JsonObject createChunkPersistedData(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		Collection<MadokuEcosystemManager.TreeDecayCandidateState> treeDecayCandidateList = getTreeDecayCandidates(chunkKey);
		if (treeDecayCandidateList == null || treeDecayCandidateList.isEmpty()) {
			return null;
		}

		JSONFormatManager.ArrayBuilder treeDecayCandidates = JSONFormatManager.array();
		for (MadokuEcosystemManager.TreeDecayCandidateState candidate : treeDecayCandidateList) {
			if (candidate != null) {
				treeDecayCandidates.add(candidate.toJson());
			}
		}

		return MadokuEcosystemManager.buildChunkPersistedData(builder -> builder
			.put("tree-decay-candidates", treeDecayCandidates.build()));
	}

	static void applyPersistedData(JsonObject source) {
		if (source == null || source.isJsonNull()) {
			return;
		}

		JsonElement treeDecayCandidatesElement = source.get("tree-decay-candidates");
		if (treeDecayCandidatesElement != null && treeDecayCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeDecayCandidatesElement.getAsJsonArray()) {
				MadokuEcosystemManager.TreeDecayCandidateState candidate = MadokuEcosystemManager.TreeDecayCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeDecayCandidate(new MadokuEcosystemManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	static boolean tryApplyTreeDecayAtTarget(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null) {
			return false;
		}

		Block leafLitter = EcosystemConfigManager.resolveBlock(MadokuEcosystemManager.BLOCK_ID_LEAF_LITTER);
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
			MadokuEcosystemManager.invalidateCachedGroundPosition();
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
		MadokuEcosystemManager.invalidateCachedGroundPosition();
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
		return MadokuEcosystemManager.isEnabled() && settings.isEnabled();
	}

	public static void syncChunkProcessorActivation() {
		MadokuChunkManager.setChunkProcessorActive(CHUNK_PROCESSOR_ID, isEnabled());
	}

	/** Discovers decay in the same column selected for Growth and Erosion. */
	static void discoverColumn(
		ServerLevel world,
		LevelChunk chunk,
		int chunkX,
		int chunkZ,
		int columnIndex,
		MadokuEcosystemManager.DiscoveryChunkState discoveryState
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
		long currentAbsoluteDayTime = MadokuEcosystemManager.resolveCachedAbsoluteDayTime(world);
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
			 normalizedSeasonId = EcosystemConfigManager.normalize(MadokuSeasonManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalDecaySettings.treeDecayForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static BlockPos resolveTreeDecayTargetPos(ServerLevel world, BlockPos leafPos, BlockState leafState) {
		if (world == null || leafPos == null || leafState == null || !MadokuEcosystemManager.isNaturalDecayEnabled()) {
			return null;
		}
		if (!leafState.is(BlockTags.LEAVES) || !MadokuEcosystemManager.isNaturallyGeneratedLeaf(leafState)) {
			return null;
		}
		return resolveLeafLitterTargetPos(world, leafPos);
	}

	static boolean isValidTreeDecayTargetCandidate(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null || !MadokuEcosystemManager.isNaturalDecayEnabled()) {
			return false;
		}
		Block leafLitter = EcosystemConfigManager.resolveBlock(MadokuEcosystemManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return false;
		}

		BlockState state = world.getBlockState(targetPos);
		if (state == null) {
			return false;
		}
		if (state.getBlock() == leafLitter) {
			return hasLeafLitterSupportBlock(world, targetPos)
				&& MadokuEcosystemManager.getLeafLitterAmount(state) < MadokuEcosystemManager.getLeafLitterMaxAmount(state);
		}
		if (state.isAir()) {
			BlockState placed = MadokuEcosystemManager.setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
			return hasLeafLitterSupportBlock(world, targetPos)
				&& placed.canSurvive(world, targetPos);
		}
		return false;
	}

	static void pickTreeDecayCandidateForPosition(ServerLevel world, int chunkX, int chunkZ, long leafPos, long targetPos) {
		if (world == null || !MadokuEcosystemManager.isNaturalDecayEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		Map<Long, MadokuEcosystemManager.TreeDecayCandidateState> existingCandidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (leafPos == Long.MIN_VALUE || targetPos == Long.MIN_VALUE || !isValidTreeDecayTargetCandidate(world, BlockPos.of(targetPos))) {
			return;
		}
		if (existingCandidates != null && existingCandidates.containsKey(leafPos)) return;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.get(chunkKey);
		if (targetOwners != null && targetOwners.containsKey(targetPos)) return;

		String seasonId = EcosystemConfigManager.normalize(MadokuSeasonManager.getCurrentSeasonId(world));
		double requiredDecayTicks = resolveTreeDecayRequiredTicks(world, seasonId);
		if (requiredDecayTicks <= 0.0d) {
			return;
		}

		putTreeDecayCandidate(chunkKey, new MadokuEcosystemManager.TreeDecayCandidateState(
				MadokuEcosystemManager.levelId(world),
				chunkX,
				chunkZ,
				leafPos,
				targetPos,
				seasonId,
				requiredDecayTicks,
				0.0d,
				MadokuTimeManager.getCurrentAbsoluteDayTime(world)
		));
		MadokuEcosystemManager.dirty = true;
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

		Block leafLitter = EcosystemConfigManager.resolveBlock(MadokuEcosystemManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return null;
		}
		BlockState singleLitter = MadokuEcosystemManager.setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
		int minY = Math.max(world.getMinY() + 1, leafPos.getY() - MadokuEcosystemManager.TREE_DECAY_MAX_DROP_DISTANCE);

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
					&& MadokuEcosystemManager.getLeafLitterAmount(state) < MadokuEcosystemManager.getLeafLitterMaxAmount(state)
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
		return belowState != null && MadokuEcosystemManager.LEAF_LITTER_SUPPORT_BLOCKS.contains(belowState.getBlock());
	}

	static void processTreeDecayCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		processTreeDecayCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime, Long.MIN_VALUE);
	}

	static void processTreeDecayCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime, long selectedTargetPosition) {
		if (world == null || !isEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		Map<Long, MadokuEcosystemManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.get(chunkKey);
		Iterator<MadokuEcosystemManager.TreeDecayCandidateState> iterator = candidates.values().iterator();
		while (iterator.hasNext()) {
			MadokuEcosystemManager.TreeDecayCandidateState candidate = iterator.next();
			if (candidate == null) {
				iterator.remove();
				removedAny = true;
				MadokuEcosystemManager.markChunkDirty(chunkKey);
				continue;
			}
			if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				MadokuEcosystemManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, MadokuEcosystemManager.CANDIDATE_DECAY);
				iterator.remove();
				removedAny = true;
				MadokuEcosystemManager.markChunkDirty(chunkKey);
				continue;
			}

			BlockPos targetPos = BlockPos.of(candidate.targetPos);
			if (selectedTargetPosition != Long.MIN_VALUE && candidate.leafPos != selectedTargetPosition) {
				continue;
			}

			double currentProgress = MadokuEcosystemManager.resolveCandidateProgress(candidate.startedAbsoluteDayTime, currentAbsoluteDayTime, candidate.requiredDecayTicks);
			if (currentProgress + 1e-6d < candidate.requiredDecayTicks) {
				continue;
			}

			if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
				MadokuEcosystemManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, MadokuEcosystemManager.CANDIDATE_DECAY);
				iterator.remove();
				if (targetOwners != null) targetOwners.remove(candidate.targetPos);
				removedAny = true;
				MadokuEcosystemManager.markChunkDirty(chunkKey);
				continue;
			}

			if (currentProgress + 1e-6d >= candidate.requiredDecayTicks) {
				boolean applied = tryApplyTreeDecayAtTarget(world, targetPos);
				if (applied) {
					MadokuEcosystemManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, MadokuEcosystemManager.CANDIDATE_DECAY);
					iterator.remove();
					if (targetOwners != null) targetOwners.remove(candidate.targetPos);
					removedAny = true;
					MadokuEcosystemManager.markChunkDirty(chunkKey);
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
			Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatManager.ensureManagedFile(file, defaults);
			settings = NaturalDecayConfigManager.fromJson(normalized);
			JSONFormatManager.writeManagedFile(file, NaturalDecayConfigManager.toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalDecayManager config; using defaults.", exception);
		}
	}

}
