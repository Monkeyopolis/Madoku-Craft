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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class EcosystemNaturalDecayManager {
	public static final String CHUNK_PROCESSOR_ID = "ecosystem_natural_decay";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalDecayManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "natural-decay";

	private static volatile NaturalDecayConfigManager.Settings settings = NaturalDecayConfigManager.defaults();
	static final Map<MadokuEcosystemManager.ChunkRefKey, List<MadokuEcosystemManager.TreeDecayCandidateState>> treeDecayCandidatesByChunk = new LinkedHashMap<>();

	private static final MadokuChunkManager.ChunkProcessor CHUNK_PROCESSOR = new MadokuChunkManager.ChunkProcessor() {
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
	}

	static Set<MadokuEcosystemManager.ChunkRefKey> collectTrackedChunkKeys() {
		return new java.util.LinkedHashSet<>(treeDecayCandidatesByChunk.keySet());
	}

	static List<MadokuEcosystemManager.TreeDecayCandidateState> getTreeDecayCandidates(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : treeDecayCandidatesByChunk.getOrDefault(chunkKey, List.of());
	}

	static void putTreeDecayCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey, MadokuEcosystemManager.TreeDecayCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		List<MadokuEcosystemManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (MadokuEcosystemManager.TreeDecayCandidateState existing : candidates) {
			if (existing != null && existing.leafPos == candidate.leafPos) {
				return;
			}
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	static void syncChunkProcessorTracking(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		// Candidate maps are queried directly by random-position dispatch.
	}

	static JsonObject createChunkPersistedData(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		List<MadokuEcosystemManager.TreeDecayCandidateState> treeDecayCandidateList = getTreeDecayCandidates(chunkKey);
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

	static void handleRandomPosition(ServerLevel world, BlockPos position) {
		if (world == null || position == null || !isEnabled()) {
			return;
		}

		long currentAbsoluteDayTime = MadokuEcosystemManager.resolveCachedAbsoluteDayTime(world);
		BlockPos targetPos = resolveTreeDecayTargetPos(world, position, world.getBlockState(position));

		if (targetPos != null) {
			processTreeDecayCandidateInChunk(
				world,
				targetPos.getX() >> 4,
				targetPos.getZ() >> 4,
				currentAbsoluteDayTime,
				targetPos.asLong()
			);

			pickTreeDecayCandidateForChunk(
				world,
				targetPos.getX() >> 4,
				targetPos.getZ() >> 4,
				Set.of(targetPos.asLong())
			);
		} else {
			// Selecting a persisted target also validates and advances it when its source leaf is gone.
			processTreeDecayCandidateInChunk(
				world,
				position.getX() >> 4,
				position.getZ() >> 4,
				currentAbsoluteDayTime,
				position.asLong()
			);
		}
	}

	/** Applies elapsed progress to every persisted decay candidate in a loaded chunk. */
	static void processChunkOnLoad(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null || !isEnabled()) {
			return;
		}
		processTreeDecayCandidateInChunk(
			world,
			chunkX,
			chunkZ,
			MadokuTimeManager.getCurrentAbsoluteDayTime(world)
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

	static void pickTreeDecayCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeDecayLeafCandidates) {
		if (world == null || treeDecayLeafCandidates == null || treeDecayLeafCandidates.isEmpty() || !MadokuEcosystemManager.isNaturalDecayEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.TreeDecayCandidateState> existingCandidates = treeDecayCandidatesByChunk.get(chunkKey);

		List<Long> options = new ArrayList<>();
		for (Long packedPos : treeDecayLeafCandidates) {
			if (packedPos == null) {
				continue;
			}
		BlockPos targetPos = BlockPos.of(packedPos);
		if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
				continue;
			}
			boolean alreadyTracked = false;
			if (existingCandidates != null) {
				for (MadokuEcosystemManager.TreeDecayCandidateState existing : existingCandidates) {
					if (existing != null && existing.leafPos == packedPos.longValue()) {
						alreadyTracked = true;
						break;
					}
				}
			}
			if (alreadyTracked) {
				continue;
			}
			options.add(packedPos);
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(MadokuSeasonManager.getCurrentSeasonId(world));
		double requiredDecayTicks = resolveTreeDecayRequiredTicks(world, seasonId);
		if (requiredDecayTicks <= 0.0d) {
			return;
		}

		int availableSlots = options.size();
		for (int i = 0; i < availableSlots; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedLeafPos = options.remove(selectedIndex);
			treeDecayCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new ArrayList<>())
				.add(new MadokuEcosystemManager.TreeDecayCandidateState(
					MadokuEcosystemManager.levelId(world),
					chunkX,
					chunkZ,
					selectedLeafPos,
					seasonId,
					requiredDecayTicks,
					0.0d,
					MadokuTimeManager.getCurrentAbsoluteDayTime(world)
				));
			syncChunkProcessorTracking(chunkKey);
			MadokuEcosystemManager.dirty = true;
		}
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
		List<MadokuEcosystemManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			MadokuEcosystemManager.TreeDecayCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.markChunkDirty(chunkKey);
				continue;
			}
			if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.markChunkDirty(chunkKey);
				continue;
			}

			BlockPos targetPos = BlockPos.of(candidate.leafPos);
			if (selectedTargetPosition != Long.MIN_VALUE && candidate.leafPos != selectedTargetPosition) {
				continue;
			}
			if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.markChunkDirty(chunkKey);
				continue;
			}

			long previousAbsolute = MadokuEcosystemManager.normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredDecayTicks, candidate.progressDecayTicks + elapsedTicks);
				if (updatedProgress > candidate.progressDecayTicks) {
					candidate.progressDecayTicks = updatedProgress;
					MadokuEcosystemManager.markChunkDirty(chunkKey);
				}
			}
			candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (candidate.progressDecayTicks + 1e-6d >= candidate.requiredDecayTicks) {
				boolean applied = tryApplyTreeDecayAtTarget(world, targetPos);
				if (applied) {
					candidates.remove(index);
					removedAny = true;
					MadokuEcosystemManager.markChunkDirty(chunkKey);
				}
			}
		}

		if (candidates.isEmpty()) {
			treeDecayCandidatesByChunk.remove(chunkKey);
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
