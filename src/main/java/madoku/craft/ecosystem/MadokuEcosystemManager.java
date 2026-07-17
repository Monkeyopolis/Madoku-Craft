package madoku.craft.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.data.DataWorldChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.json.JSONFormatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class MadokuEcosystemManager {
	private static final String DATA_SYSTEM_ID = "ecosystem";
	private static final String DEBUG_MAIN_SYSTEM = "ecosystem";
	private static final String DEBUG_SUB_SYSTEM = "ecosystem-manager";

	private static final String FIELD_GROUND_BLOCKS = "ground-blocks";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_BLOCK_POS = "block-pos";
	private static final String FIELD_MODE = "mode";
	private static final String FIELD_REQUIRED_GROWTH_TICKS = "required-growth-ticks";
	private static final String FIELD_PROGRESS_GROWTH_TICKS = "progress-growth-ticks";
	private static final String FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME = "last-processed-absolute-day-time";
	private static final String FIELD_TREE_CANDIDATES = "tree-candidates";
	private static final String FIELD_CACTUS_CANDIDATES = "cactus-candidates";
	private static final String FIELD_GRASS_CANDIDATES = "grass-candidates";
	private static final String FIELD_DESERT_FOLIAGE_GROWTH_CANDIDATES = "desert-foliage-growth-candidates";
	private static final String FIELD_FOLIAGE_CANDIDATES = "foliage-candidates";
	private static final String FIELD_TREE_DECAY_CANDIDATES = "tree-decay-candidates";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_TREE_GROUND_POS = "tree-ground-pos";
	private static final String FIELD_CACTUS_GROUND_POS = "cactus-ground-pos";
	private static final String FIELD_GRASS_GROUND_POS = "grass-ground-pos";
	private static final String FIELD_FOLIAGE_GROUND_POS = "foliage-ground-pos";
	private static final String FIELD_TREE_DECAY_TARGET_POS = "tree-decay-target-pos";
	private static final String FIELD_TREE_TYPE = "tree-type";
	private static final String FIELD_FOLIAGE_TYPE = "foliage-type";
	private static final String FIELD_INITIAL_SEASON_ID = "initial-season-id";
	private static final String FIELD_EROSION_RULE_ID = "erosion-rule-id";
	static final String BLOCK_ID_LEAF_LITTER = "minecraft:leaf_litter";
	private static final String FOLIAGE_TYPE_WILDFLOWERS = NaturalGrowthConfigManager.FIELD_WILDFLOWERS;
	static final int TREE_DECAY_MAX_DROP_DISTANCE = 16;
	static final Set<Block> DESERT_FOLIAGE_GROWTH_GROUND_BLOCKS = Set.of(
		Blocks.DIRT,
		Blocks.COARSE_DIRT,
		Blocks.RED_SAND,
		Blocks.SAND,
		Blocks.GRASS_BLOCK
	);
	static final Set<Block> CACTUS_GROWTH_GROUND_BLOCKS = Set.of(
		Blocks.SAND,
		Blocks.RED_SAND
	);

	private static final String MODE_WET = "wet";
	private static final String MODE_SURFACE_DIRT = "surface_dirt";
	private static final long ABSOLUTE_TIME_ROLLBACK_RESET_TICKS = 20L;
	static final Set<Block> TRACKABLE_WET_GROUND_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.ROOTED_DIRT,
		Blocks.DIRT_PATH,
		Blocks.PODZOL,
		Blocks.MYCELIUM,
		Blocks.COARSE_DIRT
	);
	static final Set<Block> TRACKABLE_TREE_GROUND_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.PODZOL,
		Blocks.MUD
	);
	static final Set<Block> LEAF_LITTER_SUPPORT_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.PODZOL,
		Blocks.MYCELIUM,
		Blocks.COARSE_DIRT,
		Blocks.ROOTED_DIRT
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
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	static volatile boolean dirty = false;
	private static volatile boolean loadingPersistedData = false;
	private static volatile boolean ecosystemEnabled = true;
	static volatile NaturalGrowthConfigManager.Settings naturalGrowthSettings = NaturalGrowthConfigManager.defaults();
	static volatile NaturalErosionConfigManager.Settings naturalErosionSettings = NaturalErosionConfigManager.defaults();
	static volatile NaturalDecayConfigManager.Settings naturalDecaySettings = NaturalDecayConfigManager.defaults();
	static volatile List<NaturalErosionConfigManager.NamedErosionRule> cachedErosionRules = List.of();
	private static volatile long lastUnifiedDiscoveryTick = Long.MIN_VALUE;
	private static volatile String lastUnifiedDiscoveryLevelId = "";
	private static volatile int lastUnifiedDiscoveryChunkX = Integer.MIN_VALUE;
	private static volatile int lastUnifiedDiscoveryChunkZ = Integer.MIN_VALUE;
	private static volatile long lastUnifiedDiscoveryCompletionTick = Long.MIN_VALUE;
	private static volatile String lastUnifiedDiscoveryCompletionLevelId = "";
	private static volatile int lastUnifiedDiscoveryCompletionChunkX = Integer.MIN_VALUE;
	private static volatile int lastUnifiedDiscoveryCompletionChunkZ = Integer.MIN_VALUE;
	private static final ThreadLocal<Integer> CHUNK_TRACKING_SYNC_BATCH_DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Set<ChunkRefKey>> CHUNK_TRACKING_SYNC_BATCH_KEYS = ThreadLocal.withInitial(LinkedHashSet::new);
	private static final Set<ChunkRefKey> PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Set<ChunkRefKey> LOADED_PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Set<ChunkRefKey> DIRTY_CHUNK_KEYS = new LinkedHashSet<>();

	static final Map<String, DirtState> dirtBlocksByKey = new LinkedHashMap<>();
	static final Map<ChunkRefKey, Set<String>> dirtKeysByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, ChunkDiscoveryAccumulator> discoveryAccumulatorsByChunk = new LinkedHashMap<>();

	private static final MadokuChunkManager.ChunkLifecycleListener CHUNK_LISTENER = new MadokuChunkManager.ChunkLifecycleListener() {
		@Override
		public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
			loadPersistedChunkData(level, chunkX, chunkZ);
		}

		@Override
		public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
			discoveryAccumulatorsByChunk.remove(new ChunkRefKey(levelId(level), chunkX, chunkZ));
		}
	};

	MadokuEcosystemManager() {
	}

	public static void initialize() {
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.ECOSYSTEM);
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.ECOSYSTEM);
		EcosystemConfigManager.initialize();
		EcosystemNaturalGrowthManager.initialize();
		EcosystemNaturalErosionManager.initialize();
		EcosystemNaturalDecayManager.initialize();
		loadConfig();
		MadokuChunkManager.registerChunkLifecycleListener(CHUNK_LISTENER);
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("initialize")
			.field("data-system", DATA_SYSTEM_ID));
	}

	public static void reset() {
		final int previousTrackedDirt = dirtBlocksByKey.size();
		final int previousTrackedChunks = dirtKeysByChunk.size();
		final int previousDiscoveryAccumulators = discoveryAccumulatorsByChunk.size();
		EcosystemConfigManager.reset();
		EcosystemNaturalGrowthManager.reset();
		EcosystemNaturalErosionManager.reset();
		EcosystemNaturalDecayManager.reset();
		dirtBlocksByKey.clear();
		dirtKeysByChunk.clear();
		EcosystemNaturalGrowthManager.clearTrackedCandidateState();
		EcosystemNaturalDecayManager.clearTrackedCandidateState();
		discoveryAccumulatorsByChunk.clear();
		PERSISTED_CHUNK_KEYS.clear();
		LOADED_PERSISTED_CHUNK_KEYS.clear();
		DIRTY_CHUNK_KEYS.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
		loadingPersistedData = false;
		resetUnifiedDiscoveryState();
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("reset")
			.field("tracked-dirt", previousTrackedDirt)
			.field("tracked-chunks", previousTrackedChunks)
			.field("discovery-accumulators", previousDiscoveryAccumulators));
	}

	public static boolean isEnabled() {
		return ecosystemEnabled;
	}

	private static NaturalGrowthConfigManager.Settings currentGrowthSettings() {
		return naturalGrowthSettings == null ? NaturalGrowthConfigManager.defaults() : naturalGrowthSettings;
	}

	private static NaturalErosionConfigManager.Settings currentErosionSettings() {
		return naturalErosionSettings == null ? NaturalErosionConfigManager.defaults() : naturalErosionSettings;
	}

	private static NaturalDecayConfigManager.Settings currentDecaySettings() {
		return naturalDecaySettings == null ? NaturalDecayConfigManager.defaults() : naturalDecaySettings;
	}

	static boolean isNaturalGrowthEnabled() {
		return isEnabled() && currentGrowthSettings().isEnabled();
	}

	static boolean isNaturalErosionEnabled() {
		return isEnabled() && currentErosionSettings().isEnabled();
	}

	static boolean isNaturalDecayEnabled() {
		return isEnabled() && currentDecaySettings().isEnabled();
	}

	static boolean isBlockGrowthEnabled() {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isNaturalGrowthEnabled() && settings.blockGrowth() != null && settings.blockGrowth().isEnabled();
	}

	static boolean isFoliageGrowthEnabled() {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isNaturalGrowthEnabled() && settings.foliageGrowth() != null && settings.foliageGrowth().isEnabled();
	}

	static boolean isDesertFoliageGrowthEnabled() {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isNaturalGrowthEnabled() && settings.desertFoliageGrowth() != null && settings.desertFoliageGrowth().isEnabled();
	}

	static boolean isVegetationGrowthEnabled() {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isNaturalGrowthEnabled() && settings.vegetationGrowth() != null && settings.vegetationGrowth().isEnabled();
	}

	static boolean isCactusGrowthEnabled() {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isNaturalGrowthEnabled() && settings.cactusGrowth() != null && settings.cactusGrowth().isEnabled();
	}

	private static boolean isTreeGrowthEnabled() {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isNaturalGrowthEnabled() && settings.treeGrowth() != null && settings.treeGrowth().isEnabled();
	}

	static boolean isTreeGrowthEnabled(String treeType) {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isTreeGrowthEnabled() && settings.treeGrowth() != null && settings.treeGrowth().isEnabled(treeType);
	}

	static boolean isVegetationGrowthEnabled(String foliageType) {
		NaturalGrowthConfigManager.Settings settings = currentGrowthSettings();
		return isVegetationGrowthEnabled() && settings.vegetationGrowth() != null && settings.vegetationGrowth().isEnabled(foliageType);
	}

	private static void syncChunkProcessorActivation() {
		EcosystemNaturalGrowthManager.syncChunkProcessorActivation();
		EcosystemNaturalErosionManager.syncChunkProcessorActivation();
		EcosystemNaturalDecayManager.syncChunkProcessorActivation();
	}

	private static void loadConfig() {
		naturalGrowthSettings = EcosystemNaturalGrowthManager.getSettings();
		naturalErosionSettings = EcosystemNaturalErosionManager.getSettings();
		naturalDecaySettings = EcosystemNaturalDecayManager.getSettings();
		ecosystemEnabled = EcosystemConfigManager.getSettings().enabled();
		refreshErosionRuleCache();
	}

	private static void refreshErosionRuleCache() {
		List<NaturalErosionConfigManager.NamedErosionRule> rules = NaturalErosionConfigManager.erosionRulesInPriority(naturalErosionSettings);
		if (rules == null || rules.isEmpty()) {
			cachedErosionRules = List.of();
			return;
		}
		List<NaturalErosionConfigManager.NamedErosionRule> normalized = new ArrayList<>(rules.size());
		for (NaturalErosionConfigManager.NamedErosionRule rule : rules) {
			if (rule == null || rule.rule() == null) {
				continue;
			}
			normalized.add(rule);
		}
		cachedErosionRules = normalized.isEmpty() ? List.of() : List.copyOf(normalized);
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		syncChunkProcessorActivation();
		for (ServerLevel level : server.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachReadyToSendChunk((LevelChunk chunk) -> {
				if (chunk != null) loadPersistedChunkData(level, chunk.getPos().x, chunk.getPos().z);
			});
		}
		EcosystemNaturalGrowthManager.onServerStarted(server);
		EcosystemNaturalErosionManager.onServerStarted(server);
		EcosystemNaturalDecayManager.onServerStarted(server);
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("server-started")
			.field("enabled", isEnabled())
			.field("growth", isNaturalGrowthEnabled())
			.field("erosion", isNaturalErosionEnabled())
			.field("decay", isNaturalDecayEnabled()));
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		syncChunkProcessorActivation();
		PERSISTED_CHUNK_KEYS.clear();
		DIRTY_CHUNK_KEYS.clear();
		loadingPersistedData = true;
		try {
			if (!isEnabled()) {
				dirtBlocksByKey.clear();
				dirtKeysByChunk.clear();
				EcosystemNaturalGrowthManager.clearTrackedCandidateState();
				EcosystemNaturalDecayManager.clearTrackedCandidateState();
				discoveryAccumulatorsByChunk.clear();
				EcosystemNaturalGrowthManager.reset();
				EcosystemNaturalErosionManager.reset();
				EcosystemNaturalDecayManager.reset();
				dirty = false;
				resetUnifiedDiscoveryState();
				return;
			}

			dirtBlocksByKey.clear();
			dirtKeysByChunk.clear();
			EcosystemNaturalGrowthManager.clearTrackedCandidateState();
			EcosystemNaturalDecayManager.clearTrackedCandidateState();
			discoveryAccumulatorsByChunk.clear();
			resetUnifiedDiscoveryState();
			EcosystemNaturalGrowthManager.reset();
			EcosystemNaturalErosionManager.reset();
			EcosystemNaturalDecayManager.reset();

			long autoSaveIntervalTicks = DataWorldChunkManager.getAutoSaveIntervalTicks();
			lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
			dirty = false;
			DIRTY_CHUNK_KEYS.clear();
			emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
				.subject("load-persisted-data")
				.field("auto-save-ticks", autoSaveIntervalTicks)
				.field("dirty", dirty)
				.field("chunk-files", 0)
				.field("persisted-chunks", PERSISTED_CHUNK_KEYS.size()));
		} finally {
			loadingPersistedData = false;
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}

		long autoSaveIntervalTicks = DataWorldChunkManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket == lastAutosaveBucket) {
			return;
		}

		lastAutosaveBucket = bucket;
		if (dirty) {
			savePersistedData(server);
		}
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("autosave")
			.field("bucket", bucket)
			.field("dirty", dirty));
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}

		Set<ChunkRefKey> currentChunkKeys = collectCurrentChunkKeys();
		Set<ChunkRefKey> dirtyChunkKeys = collectDirtyChunkKeys();
		Set<ChunkRefKey> staleChunkKeys = new LinkedHashSet<>(PERSISTED_CHUNK_KEYS);
		staleChunkKeys.removeAll(currentChunkKeys);
		int writtenChunkFiles = 0;
		for (ChunkRefKey chunkKey : dirtyChunkKeys) {
			if (!currentChunkKeys.contains(chunkKey)) {
				continue;
			}
			JsonObject chunkData = createChunkPersistedData(chunkKey);
			if (chunkData == null) {
				continue;
			}
			DataWorldChunkManager.setChunkSystemData(
				new DataWorldChunkManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID,
				chunkData
			);
			writtenChunkFiles++;
		}
		for (ChunkRefKey chunkKey : staleChunkKeys) {
			DataWorldChunkManager.removeChunkSystemData(
				new DataWorldChunkManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID
			);
		}
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(currentChunkKeys);
		DIRTY_CHUNK_KEYS.clear();
		final int writtenChunkFileCount = writtenChunkFiles;
		dirty = false;
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("save-persisted-data")
			.field("dirty", dirty)
			.field("chunk-files", writtenChunkFileCount)
			.field("persisted-chunks", currentChunkKeys.size())
			.field("deleted-chunks", staleChunkKeys.size()));
	}

	static void beginUnifiedDiscoveryForChunk(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		discoveryAccumulatorsByChunk.put(chunkKey, new ChunkDiscoveryAccumulator());
		emitEcosystemDebug("ecosystem.discovery", builder -> builder
			.subject("begin-unified-discovery")
			.field("level-id", chunkKey.levelId())
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ));
	}

	static void runUnifiedDiscoveryForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuChunkManager.ChunkDiscoverySnapshot snapshot
	) {
		if (world == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}
		long gameplayTick = MadokuTimeManager.getGameplayTicks();
		String worldLevelId = levelId(world);
		if (lastUnifiedDiscoveryTick == gameplayTick
			&& chunkX == lastUnifiedDiscoveryChunkX
			&& chunkZ == lastUnifiedDiscoveryChunkZ
			&& worldLevelId.equals(lastUnifiedDiscoveryLevelId)) {
			return;
		}
		lastUnifiedDiscoveryTick = gameplayTick;
		lastUnifiedDiscoveryLevelId = worldLevelId;
		lastUnifiedDiscoveryChunkX = chunkX;
		lastUnifiedDiscoveryChunkZ = chunkZ;
		emitEcosystemDebug("ecosystem.discovery", builder -> builder
			.subject("run-unified-discovery")
			.field("level-id", worldLevelId)
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("tick", gameplayTick));

		ChunkDiscoveryAccumulator accumulator = getOrCreateDiscoveryAccumulator(world, chunkX, chunkZ);
		if (accumulator == null || snapshot == null || (snapshot.motionColumns().isEmpty() && snapshot.surfaceColumns().isEmpty())) {
			return;
		}

		accumulateTrackablesInChunk(world, chunkX, chunkZ, snapshot, accumulator);
	}

	static void finishUnifiedDiscoveryForChunk(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}
		long gameplayTick = MadokuTimeManager.getGameplayTicks();
		String worldLevelId = levelId(world);
		if (lastUnifiedDiscoveryCompletionTick == gameplayTick
			&& chunkX == lastUnifiedDiscoveryCompletionChunkX
			&& chunkZ == lastUnifiedDiscoveryCompletionChunkZ
			&& worldLevelId.equals(lastUnifiedDiscoveryCompletionLevelId)) {
			return;
		}
		lastUnifiedDiscoveryCompletionTick = gameplayTick;
		lastUnifiedDiscoveryCompletionLevelId = worldLevelId;
		lastUnifiedDiscoveryCompletionChunkX = chunkX;
		lastUnifiedDiscoveryCompletionChunkZ = chunkZ;

		ChunkDiscoveryAccumulator accumulator = removeDiscoveryAccumulator(world, chunkX, chunkZ);
		if (accumulator == null) {
			return;
		}

		beginChunkTrackingSyncBatch();
		try {
			finalizeTrackablesInChunkDiscovery(world, chunkX, chunkZ, accumulator);
		} finally {
			endChunkTrackingSyncBatch();
		}
	}

	private static void accumulateTrackablesInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuChunkManager.ChunkDiscoverySnapshot snapshot,
		ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || snapshot == null || accumulator == null) {
			return;
		}
		if (snapshot.motionColumns().isEmpty() && snapshot.surfaceColumns().isEmpty()) {
			return;
		}
		EcosystemNaturalGrowthManager.discoverTrackablesInChunk(world, chunkX, chunkZ, snapshot, accumulator);
		EcosystemNaturalErosionManager.discoverTrackablesInChunk(world, chunkX, chunkZ, snapshot, accumulator);
		EcosystemNaturalDecayManager.discoverTrackablesInChunk(world, chunkX, chunkZ, snapshot, accumulator);
	}

	private static void finalizeTrackablesInChunkDiscovery(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || accumulator == null) {
			return;
		}
		EcosystemNaturalGrowthManager.finalizeTrackablesInChunkDiscovery(world, chunkX, chunkZ, accumulator);
		EcosystemNaturalErosionManager.finalizeTrackablesInChunkDiscovery(world, chunkX, chunkZ, accumulator);
		EcosystemNaturalDecayManager.finalizeTrackablesInChunkDiscovery(world, chunkX, chunkZ, accumulator);
	}

	static final class ChunkDiscoveryAccumulator {
		final Set<Long> treeGroundCandidates = new LinkedHashSet<>();
		final Set<Long> cactusGroundCandidates = new LinkedHashSet<>();
		final Set<Long> grassGroundCandidates = new LinkedHashSet<>();
		final Set<Long> desertFoliageGrowthGroundCandidates = new LinkedHashSet<>();
		final Set<Long> wildflowerGroundCandidates = new LinkedHashSet<>();
		final Set<Long> pinkPetalGroundCandidates = new LinkedHashSet<>();
		final Set<Long> wetSeedPositions = new LinkedHashSet<>();
		final Set<Long> treeDecayLeafCandidates = new LinkedHashSet<>();
	}

	private static void loadPersistedChunkData(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null || !isEnabled()) return;
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), chunkX, chunkZ);
		if (chunkKey.levelId().isBlank() || !LOADED_PERSISTED_CHUNK_KEYS.add(chunkKey)) return;
		JsonObject source = DataWorldChunkManager.getChunkSystemData(level, chunkX, chunkZ, DATA_SYSTEM_ID);
		if (source == null || source.isEmpty()) return;
		source.addProperty(FIELD_LEVEL_ID, chunkKey.levelId());
		source.addProperty(FIELD_CHUNK_X, chunkX);
		source.addProperty(FIELD_CHUNK_Z, chunkZ);
		applyPersistedData(source);
	}

	static boolean trackDirtCandidateForMode(ServerLevel world, BlockPos dirtPos, BlockState state, String mode) {
		if (world == null || dirtPos == null || state == null || mode == null || mode.isBlank()) {
			return false;
		}
		if (!isModeEnabled(mode)) {
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

		String erosionRuleId = "";
		double requiredGrowthTicks;
		if (MODE_SURFACE_DIRT.equals(mode)) {
			requiredGrowthTicks = EcosystemNaturalGrowthManager.resolveSurfaceDirtRequiredGrowthTicks(world);
		} else {
			NaturalErosionConfigManager.NamedErosionRule erosionRule = resolveErosionRule(world, dirtPos, state, "");
			if (erosionRule == null || erosionRule.rule() == null) {
				return false;
			}
			erosionRuleId = erosionRule.ruleId();
			requiredGrowthTicks = EcosystemNaturalGrowthManager.randomDaysToTicks(erosionRule.rule().erosionTime());
		}
		double startingProgress = existing != null && mode.equals(existing.mode) ? existing.progressGrowthTicks : 0.0d;

		putDirtState(key, new DirtState(
			levelId(world),
			dirtPos.asLong(),
			mode,
			erosionRuleId,
			requiredGrowthTicks,
			Math.max(0.0d, Math.min(requiredGrowthTicks, startingProgress)),
			resolveAbsoluteDayTime(world)
		));
		dirty = true;
		return true;
	}

	static boolean isTrackableGroundBlock(BlockState state) {
		return EcosystemNaturalErosionManager.isTrackableGroundBlock(state);
	}

	static boolean isCandidateForMode(ServerLevel world, BlockPos blockPos, BlockState state, String mode) {
		if (!isModeEnabled(mode)) {
			return false;
		}
		if (MODE_WET.equals(mode)) {
			return EcosystemNaturalErosionManager.isWaterErosionEnabled() && EcosystemNaturalErosionManager.isWetTrackedCandidate(world, blockPos, state);
		}
		if (MODE_SURFACE_DIRT.equals(mode)) {
			return isBlockGrowthEnabled() && EcosystemNaturalGrowthManager.isSurfaceDirtCandidate(world, blockPos, state);
		}
		return false;
	}

	static boolean isModeEnabled(String mode) {
		if (MODE_WET.equals(mode)) {
			return EcosystemNaturalErosionManager.isWaterErosionEnabled();
		}
		if (MODE_SURFACE_DIRT.equals(mode)) {
			return isBlockGrowthEnabled();
		}
		return false;
	}

	private static double defaultErosionGrowthTicks() {
		for (NaturalErosionConfigManager.NamedErosionRule rule : cachedErosionRules) {
			if (!rule.rule().enabled()) {
				continue;
			}
			double ticks = EcosystemNaturalGrowthManager.randomDaysToTicks(rule.rule().erosionTime());
			if (ticks > 0.0d) {
				return ticks;
			}
		}
		return 7.0d * MadokuTimeManager.MINECRAFT_TICKS_PER_CYCLE;
	}

	static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType) {
		return EcosystemNaturalGrowthManager.tryGrowTreeAtGround(world, groundPos, treeType);
	}

	static boolean isLavaMagmaSourceBlockId(String blockId) {
		return EcosystemNaturalErosionManager.isLavaMagmaSourceBlockId(blockId);
	}

	static void requestEcosystemProcessing(MinecraftServer server, long delayTicks) {
		if (!isEnabled()) {
			return;
		}
		EcosystemNaturalGrowthManager.requestProcessing(server, delayTicks);
		EcosystemNaturalErosionManager.requestProcessing(server, delayTicks);
		EcosystemNaturalDecayManager.requestProcessing(server, delayTicks);
	}

	private static void resetUnifiedDiscoveryState() {
		lastUnifiedDiscoveryTick = Long.MIN_VALUE;
		lastUnifiedDiscoveryLevelId = "";
		lastUnifiedDiscoveryChunkX = Integer.MIN_VALUE;
		lastUnifiedDiscoveryChunkZ = Integer.MIN_VALUE;
		lastUnifiedDiscoveryCompletionTick = Long.MIN_VALUE;
		lastUnifiedDiscoveryCompletionLevelId = "";
		lastUnifiedDiscoveryCompletionChunkX = Integer.MIN_VALUE;
		lastUnifiedDiscoveryCompletionChunkZ = Integer.MIN_VALUE;
		discoveryAccumulatorsByChunk.clear();
	}

	private static void emitEcosystemDebug(String metricId, Consumer<MadokuDebugManager.EventBuilder> customizer) {
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

	private static ChunkDiscoveryAccumulator getOrCreateDiscoveryAccumulator(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null) {
			return null;
		}
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		return discoveryAccumulatorsByChunk.computeIfAbsent(chunkKey, ignored -> new ChunkDiscoveryAccumulator());
	}

	private static ChunkDiscoveryAccumulator removeDiscoveryAccumulator(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null) {
			return null;
		}
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		return discoveryAccumulatorsByChunk.remove(chunkKey);
	}

	private static DirtState putDirtState(String key, DirtState value) {
		DirtState previous = dirtBlocksByKey.put(key, value);
		ChunkRefKey previousChunkKey = null;
		if (previous != null) {
			previousChunkKey = chunkRefForPos(previous.levelId, previous.dirtPos);
			removeChunkIndex(dirtKeysByChunk, previousChunkKey, key);
			markChunkDirty(previousChunkKey);
		}
		ChunkRefKey nextChunkKey = null;
		if (value != null) {
			nextChunkKey = chunkRefForPos(value.levelId, value.dirtPos);
			addChunkIndex(dirtKeysByChunk, nextChunkKey, key);
			markChunkDirty(nextChunkKey);
		}
		if (previousChunkKey != null) {
			syncChunkProcessorTracking(previousChunkKey);
		}
		if (nextChunkKey != null) {
			syncChunkProcessorTracking(nextChunkKey);
		}
		return previous;
	}

	static DirtState removeDirtStateByKey(String key) {
		DirtState removed = dirtBlocksByKey.remove(key);
		if (removed != null) {
			ChunkRefKey chunkKey = chunkRefForPos(removed.levelId, removed.dirtPos);
			removeChunkIndex(dirtKeysByChunk, chunkKey, key);
			markChunkDirty(chunkKey);
			syncChunkProcessorTracking(chunkKey);
		}
		return removed;
	}

	private static void addChunkIndex(Map<ChunkRefKey, Set<String>> indexMap, ChunkRefKey chunkKey, String entryKey) {
		if (indexMap == null || chunkKey == null || entryKey == null || entryKey.isBlank()) {
			return;
		}
		indexMap.computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>()).add(entryKey);
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
	}

	static void syncChunkProcessorTracking(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		if (isChunkTrackingSyncBatchActive()) {
			CHUNK_TRACKING_SYNC_BATCH_KEYS.get().add(chunkKey);
			return;
		}
		EcosystemNaturalGrowthManager.syncChunkProcessorTracking(chunkKey);
		EcosystemNaturalErosionManager.syncChunkProcessorTracking(chunkKey);
		EcosystemNaturalDecayManager.syncChunkProcessorTracking(chunkKey);
	}

	static void markChunkDirty(ChunkRefKey chunkKey) {
		if (chunkKey == null || loadingPersistedData) {
			return;
		}
		DIRTY_CHUNK_KEYS.add(chunkKey);
		dirty = true;
	}

	static void markChunkDirty(ServerLevel level, int chunkX, int chunkZ) {
		if (level != null) markChunkDirty(new ChunkRefKey(levelId(level), chunkX, chunkZ));
	}

	private static Set<ChunkRefKey> collectDirtyChunkKeys() {
		return new LinkedHashSet<>(DIRTY_CHUNK_KEYS);
	}

	private static void beginChunkTrackingSyncBatch() {
		CHUNK_TRACKING_SYNC_BATCH_DEPTH.set(CHUNK_TRACKING_SYNC_BATCH_DEPTH.get() + 1);
	}

	private static void endChunkTrackingSyncBatch() {
		int depth = CHUNK_TRACKING_SYNC_BATCH_DEPTH.get() - 1;
		if (depth > 0) {
			CHUNK_TRACKING_SYNC_BATCH_DEPTH.set(depth);
			return;
		}

		CHUNK_TRACKING_SYNC_BATCH_DEPTH.remove();
		Set<ChunkRefKey> pendingKeys = CHUNK_TRACKING_SYNC_BATCH_KEYS.get();
		List<ChunkRefKey> keysToSync = new ArrayList<>(pendingKeys);
		pendingKeys.clear();
		CHUNK_TRACKING_SYNC_BATCH_KEYS.remove();
		for (ChunkRefKey pendingKey : keysToSync) {
			syncChunkProcessorTracking(pendingKey);
		}
	}

	private static boolean isChunkTrackingSyncBatchActive() {
		return CHUNK_TRACKING_SYNC_BATCH_DEPTH.get() > 0;
	}

	static boolean chunkHasDirtMode(ChunkRefKey chunkKey, String mode) {
		if (chunkKey == null || mode == null || mode.isBlank()) {
			return false;
		}
		Set<String> keys = dirtKeysByChunk.get(chunkKey);
		if (keys == null || keys.isEmpty()) {
			return false;
		}
		for (String key : keys) {
			DirtState dirt = dirtBlocksByKey.get(key);
			if (dirt != null && mode.equals(dirt.mode)) {
				return true;
			}
		}
		return false;
	}

	private static String dirtKey(ServerLevel world, BlockPos dirtPos) {
		return levelId(world) + "|" + (dirtPos == null ? -1L : dirtPos.asLong());
	}

	static String levelId(ServerLevel world) {
		String dimensionId = DataWorldChunkManager.dimensionId(world);
		return dimensionId.isBlank() ? "" : dimensionId;
	}

	static ChunkRefKey chunkRefForPos(String levelId, long packedBlockPos) {
		return new ChunkRefKey(levelId, BlockPos.getX(packedBlockPos) >> 4, BlockPos.getZ(packedBlockPos) >> 4);
	}

	private static long resolveAbsoluteDayTime(ServerLevel world) {
		if (world == null) {
			return MadokuTimeManager.getCurrentAbsoluteDayTime();
		}
		return MadokuTimeManager.getCurrentAbsoluteDayTime(world);
	}

	static long normalizePreviousAbsoluteTick(long previousAbsoluteTick, long currentAbsoluteTick) {
		long safePrevious = Math.max(0L, previousAbsoluteTick);
		long safeCurrent = Math.max(0L, currentAbsoluteTick);
		if (safePrevious > safeCurrent + ABSOLUTE_TIME_ROLLBACK_RESET_TICKS) {
			return safeCurrent;
		}
		return safePrevious;
	}

	static boolean tryGrowGrassAtGround(ServerLevel world, BlockPos groundPos) {
		return EcosystemNaturalGrowthManager.tryGrowGrassAtGround(world, groundPos);
	}

	static boolean tryGrowFoliageAtGround(ServerLevel world, BlockPos groundPos, String foliageType) {
		return EcosystemNaturalGrowthManager.tryGrowFoliageAtGround(world, groundPos, foliageType);
	}

	static boolean tryGrowDesertFoliageAtGround(ServerLevel world, BlockPos groundPos) {
		return EcosystemNaturalGrowthManager.tryGrowDesertFoliageAtGround(world, groundPos);
	}

	static boolean tryGrowCactusAtGround(ServerLevel world, BlockPos groundPos) {
		return EcosystemNaturalGrowthManager.tryGrowCactusAtGround(world, groundPos);
	}

	static boolean tryApplyTreeDecayAtTarget(ServerLevel world, BlockPos targetPos) {
		return EcosystemNaturalDecayManager.tryApplyTreeDecayAtTarget(world, targetPos);
	}

	static boolean isNaturallyGeneratedLeaf(BlockState state) {
		return EcosystemNaturalDecayManager.isNaturallyGeneratedLeaf(state);
	}

	static int getFoliageAmount(BlockState state) {
		return EcosystemNaturalGrowthManager.getFoliageAmount(state);
	}

	static int getFoliageMaxAmount(BlockState state) {
		return EcosystemNaturalGrowthManager.getFoliageMaxAmount(state);
	}

	static IntegerProperty findFoliageAmountProperty(BlockState state) {
		return EcosystemNaturalGrowthManager.findFoliageAmountProperty(state);
	}

	static BlockState setFoliageAmount(BlockState state, int targetAmount) {
		return EcosystemNaturalGrowthManager.setFoliageAmount(state, targetAmount);
	}

	static int getLeafLitterAmount(BlockState state) {
		return EcosystemNaturalDecayManager.getLeafLitterAmount(state);
	}

	static int getLeafLitterMaxAmount(BlockState state) {
		return EcosystemNaturalDecayManager.getLeafLitterMaxAmount(state);
	}

	static IntegerProperty findLeafLitterAmountProperty(BlockState state) {
		return EcosystemNaturalDecayManager.findLeafLitterAmountProperty(state);
	}

	static BlockState setLeafLitterAmount(BlockState state, int targetAmount) {
		return EcosystemNaturalDecayManager.setLeafLitterAmount(state, targetAmount);
	}

	static Block resolveWetGroundReplacementBlock(ServerLevel world, BlockPos pos, BlockState state, String preferredRuleId) {
		return EcosystemNaturalErosionManager.resolveWetGroundReplacementBlock(world, pos, state, preferredRuleId);
	}

	static NaturalErosionConfigManager.NamedErosionRule resolveErosionRule(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		String preferredRuleId
	) {
		return EcosystemNaturalErosionManager.resolveErosionRule(world, pos, state, preferredRuleId);
	}

	static Block resolveSurfaceDirtGrowthBlock(ServerLevel world, BlockPos pos) {
		return EcosystemNaturalGrowthManager.resolveSurfaceDirtGrowthBlock(world, pos);
	}

	private static ChunkRefKey applyPersistedData(JsonObject source) {
		if (source == null || source.isJsonNull()) {
			return null;
		}

		String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
		if (levelId.isEmpty()) {
			return null;
		}
		int chunkX = (int) getLong(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
		int chunkZ = (int) getLong(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
		if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
			return null;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId, chunkX, chunkZ);

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
		EcosystemNaturalGrowthManager.applyPersistedData(source);
		EcosystemNaturalDecayManager.applyPersistedData(source);

		PERSISTED_CHUNK_KEYS.add(chunkKey);
		return chunkKey;
	}

	private static JsonObject createChunkPersistedData(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}

		JSONFormatManager.ArrayBuilder dirtBlocks = JSONFormatManager.array();
		Set<String> dirtKeys = dirtKeysByChunk.get(chunkKey);
		if (dirtKeys != null) {
			for (String key : dirtKeys) {
				DirtState dirt = dirtBlocksByKey.get(key);
				if (dirt != null) {
					dirtBlocks.add(dirt.toJson());
				}
			}
		}

		JsonObject growthData = EcosystemNaturalGrowthManager.createChunkPersistedData(chunkKey);
		JsonObject decayData = EcosystemNaturalDecayManager.createChunkPersistedData(chunkKey);
		boolean hasData = (dirtKeys != null && !dirtKeys.isEmpty()) || growthData != null || decayData != null;
		if (!hasData) {
			return null;
		}

		JSONFormatManager.ObjectBuilder builder = JSONFormatManager.object()
			.put(FIELD_LEVEL_ID, chunkKey.levelId())
			.put(FIELD_CHUNK_X, chunkKey.chunkX())
			.put(FIELD_CHUNK_Z, chunkKey.chunkZ())
			.put(FIELD_GROUND_BLOCKS, dirtBlocks.build());
		if (growthData != null) {
			builder.put(FIELD_TREE_CANDIDATES, growthData.get(FIELD_TREE_CANDIDATES))
				.put(FIELD_CACTUS_CANDIDATES, growthData.get(FIELD_CACTUS_CANDIDATES))
				.put(FIELD_GRASS_CANDIDATES, growthData.get(FIELD_GRASS_CANDIDATES))
				.put(FIELD_DESERT_FOLIAGE_GROWTH_CANDIDATES, growthData.get(FIELD_DESERT_FOLIAGE_GROWTH_CANDIDATES))
				.put(FIELD_FOLIAGE_CANDIDATES, growthData.get(FIELD_FOLIAGE_CANDIDATES));
		}
		if (decayData != null) {
			builder.put(FIELD_TREE_DECAY_CANDIDATES, decayData.get(FIELD_TREE_DECAY_CANDIDATES));
		}
		return builder.build();
	}

	static JsonObject buildChunkPersistedData(Consumer<JSONFormatManager.ObjectBuilder> writer) {
		if (writer == null) {
			return null;
		}
		JSONFormatManager.ObjectBuilder builder = JSONFormatManager.object();
		writer.accept(builder);
		return builder.build();
	}

	private static Set<ChunkRefKey> collectCurrentChunkKeys() {
		Set<ChunkRefKey> keys = new LinkedHashSet<>();
		keys.addAll(dirtKeysByChunk.keySet());
		keys.addAll(EcosystemNaturalGrowthManager.collectTrackedChunkKeys());
		keys.addAll(EcosystemNaturalDecayManager.collectTrackedChunkKeys());
		return keys;
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

	record ChunkRefKey(String levelId, int chunkX, int chunkZ) {
	}

	record SpreadNode(BlockPos pos, int depth) {
	}

	record TreeCandidateOption(long groundPos, String treeType, double requiredGrowthTicks) {
	}

	static final class CactusCandidateState {
		final String levelId;
		final int chunkX;
		final int chunkZ;
		final long groundPos;
		final String initialSeasonId;
		final double requiredGrowthTicks;
		double progressGrowthTicks;
		long lastProcessedAbsoluteDayTime;

		CactusCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			this.initialSeasonId = EcosystemConfigManager.normalize(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		JsonObject toJson() {
			return JSONFormatManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_CACTUS_GROUND_POS, groundPos)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.build();
		}

		static CactusCandidateState fromJson(JsonElement element) {
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
			long groundPos = getLong(source, FIELD_CACTUS_GROUND_POS, Long.MIN_VALUE);
			if (groundPos == Long.MIN_VALUE) {
				return null;
			}
			String initialSeasonId = EcosystemConfigManager.normalize(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, EcosystemNaturalGrowthManager.resolveCactusRequiredGrowthTicks(null, initialSeasonId))
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new CactusCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	static final class GrassCandidateState {
		final String levelId;
		final int chunkX;
		final int chunkZ;
		final long groundPos;
		final String initialSeasonId;
		final double requiredGrowthTicks;
		double progressGrowthTicks;
		long lastProcessedAbsoluteDayTime;

		GrassCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			this.initialSeasonId = EcosystemConfigManager.normalize(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		JsonObject toJson() {
			return JSONFormatManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_GRASS_GROUND_POS, groundPos)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.build();
		}

		static GrassCandidateState fromJson(JsonElement element) {
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
			long groundPos = getLong(source, FIELD_GRASS_GROUND_POS, Long.MIN_VALUE);
			if (groundPos == Long.MIN_VALUE) {
				return null;
			}
			String initialSeasonId = EcosystemConfigManager.normalize(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, EcosystemNaturalGrowthManager.resolveGrassRequiredGrowthTicks(null, initialSeasonId))
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new GrassCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	static final class FoliageCandidateState {
		final String levelId;
		final int chunkX;
		final int chunkZ;
		final long groundPos;
		final String foliageType;
		final String initialSeasonId;
		final double requiredGrowthTicks;
		double progressGrowthTicks;
		long lastProcessedAbsoluteDayTime;

		FoliageCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String foliageType,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
			this.foliageType = normalizedFoliageType.isBlank() ? FOLIAGE_TYPE_WILDFLOWERS : normalizedFoliageType;
			this.initialSeasonId = EcosystemConfigManager.normalize(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		JsonObject toJson() {
			return JSONFormatManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_FOLIAGE_GROUND_POS, groundPos)
				.put(FIELD_FOLIAGE_TYPE, foliageType)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.build();
		}

		static FoliageCandidateState fromJson(JsonElement element) {
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
				long groundPos = getLong(source, FIELD_FOLIAGE_GROUND_POS, Long.MIN_VALUE);
				if (groundPos == Long.MIN_VALUE) {
					return null;
				}
				String foliageType = NaturalGrowthConfigManager.normalizeFoliageType(getString(source, FIELD_FOLIAGE_TYPE, FOLIAGE_TYPE_WILDFLOWERS));
				String initialSeasonId = EcosystemConfigManager.normalize(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
				double requiredGrowthTicks = getDouble(
					source,
					FIELD_REQUIRED_GROWTH_TICKS,
					Math.max(1.0d, EcosystemNaturalGrowthManager.resolveFoliageRequiredGrowthTicks(null, foliageType, initialSeasonId))
				);
				double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
				long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
				return new FoliageCandidateState(
					levelId,
					chunkX,
					chunkZ,
					groundPos,
					foliageType,
					initialSeasonId,
					requiredGrowthTicks,
					progressGrowthTicks,
					lastProcessedAbsoluteDayTime
			);
		}
	}

	static final class TreeDecayCandidateState {
		final String levelId;
		final int chunkX;
		final int chunkZ;
		final long leafPos;
		final String initialSeasonId;
		final double requiredDecayTicks;
		double progressDecayTicks;
		long lastProcessedAbsoluteDayTime;

		TreeDecayCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long leafPos,
			String initialSeasonId,
			double requiredDecayTicks,
			double progressDecayTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.leafPos = leafPos;
			this.initialSeasonId = EcosystemConfigManager.normalize(initialSeasonId);
			this.requiredDecayTicks = Math.max(1.0d, requiredDecayTicks);
			this.progressDecayTicks = Math.max(0.0d, Math.min(this.requiredDecayTicks, progressDecayTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

			JsonObject toJson() {
				return JSONFormatManager.object()
					.put(FIELD_LEVEL_ID, levelId)
					.put(FIELD_CHUNK_X, chunkX)
					.put(FIELD_CHUNK_Z, chunkZ)
					.put(FIELD_TREE_DECAY_TARGET_POS, leafPos)
					.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
					.put(FIELD_REQUIRED_GROWTH_TICKS, requiredDecayTicks)
					.put(FIELD_PROGRESS_GROWTH_TICKS, progressDecayTicks)
					.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
					.build();
		}

		static TreeDecayCandidateState fromJson(JsonElement element) {
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
			long leafPos = getLong(source, FIELD_TREE_DECAY_TARGET_POS, Long.MIN_VALUE);
			if (leafPos == Long.MIN_VALUE) {
				return null;
			}
			String initialSeasonId = EcosystemConfigManager.normalize(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredDecayTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, EcosystemNaturalDecayManager.resolveTreeDecayRequiredTicks(null, initialSeasonId))
			);
			double progressDecayTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new TreeDecayCandidateState(
				levelId,
				chunkX,
				chunkZ,
				leafPos,
				initialSeasonId,
				requiredDecayTicks,
				progressDecayTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	static final class TreeCandidateState {
		final String levelId;
		final int chunkX;
		final int chunkZ;
		final long groundPos;
		final String treeType;
		final String initialSeasonId;
		final double requiredGrowthTicks;
		double progressGrowthTicks;
		long lastProcessedAbsoluteDayTime;

		TreeCandidateState(
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
			this.initialSeasonId = EcosystemConfigManager.normalize(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		JsonObject toJson() {
			return JSONFormatManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_TREE_GROUND_POS, groundPos)
				.put(FIELD_TREE_TYPE, treeType)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.build();
		}

		static TreeCandidateState fromJson(JsonElement element) {
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
			String initialSeasonId = EcosystemConfigManager.normalize(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, EcosystemNaturalGrowthManager.resolveTreeRequiredGrowthTicks(treeType, initialSeasonId))
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

	static final class DirtState {
		final String levelId;
		final long dirtPos;
		final String mode;
		final String erosionRuleId;
		double requiredGrowthTicks;
		double progressGrowthTicks;
		long lastProcessedAbsoluteDayTime;

		DirtState(
			String levelId,
			long dirtPos,
			String mode,
			String erosionRuleId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.dirtPos = dirtPos;
			this.mode = MODE_SURFACE_DIRT.equals(mode) ? MODE_SURFACE_DIRT : MODE_WET;
			this.erosionRuleId = erosionRuleId == null ? "" : erosionRuleId.trim();
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		String key() {
			return levelId + "|" + dirtPos;
		}

		JsonObject toJson() {
			return JSONFormatManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_BLOCK_POS, dirtPos)
				.put(FIELD_MODE, mode)
				.put(FIELD_EROSION_RULE_ID, erosionRuleId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.build();
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
			String erosionRuleId = getString(source, FIELD_EROSION_RULE_ID, "");
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				MODE_SURFACE_DIRT.equals(mode) ? 3.0d * MadokuTimeManager.MINECRAFT_TICKS_PER_CYCLE : defaultErosionGrowthTicks()
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new DirtState(
				levelId,
				dirtPos,
				mode,
				erosionRuleId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}
}
