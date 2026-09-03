package madoku.craft.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.core.chunk.ChunkAPIManager;
import madoku.craft.core.data.DataWorldChunkAPIManager;
import madoku.craft.core.json.JSONFormatAPIManager;
import madoku.craft.core.scheduler.SchedulerAdaptiveIntervalAPIManager;
import madoku.craft.core.time.TimeAPIManager;
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

public final class EcosystemAPIManager {
	private static final String DATA_SYSTEM_ID = "ecosystem";
	private static final String FIELD_GROUND_BLOCKS = "ground-blocks";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_BLOCK_POS = "block-pos";
	private static final String FIELD_MODE = "mode";
	private static final String FIELD_REQUIRED_GROWTH_TICKS = "required-growth-ticks";
	private static final String FIELD_PROGRESS_GROWTH_TICKS = "progress-growth-ticks";
	private static final String FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME = "last-processed-absolute-day-time";
	private static final String FIELD_STARTED_ABSOLUTE_DAY_TIME = "started-absolute-day-time";
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
	private static final String FIELD_TREE_DECAY_SOURCE_POS = "tree-decay-source-pos";
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
	private static final Set<ChunkRefKey> PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Set<ChunkRefKey> RETAINED_UNLOADED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Set<ChunkRefKey> LOADED_PERSISTED_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Set<ChunkRefKey> LOADED_DISCOVERY_CHUNK_KEYS = new LinkedHashSet<>();
	private static final Set<ChunkRefKey> DIRTY_CHUNK_KEYS = new LinkedHashSet<>();
	private static final List<DiscoveryChunkState> PERIODIC_DISCOVERY_CHUNKS = new ArrayList<>();
	private static final Set<ChunkRefKey> PERIODIC_DISCOVERY_CHUNK_KEYS = new LinkedHashSet<>();
	private static final String DISCOVERY_INTERVAL_SYSTEM_ID = "ecosystem.discovery";
	private static final long DISCOVERY_MIN_INTERVAL_TICKS = 1L;
	private static final long DISCOVERY_MAX_INTERVAL_TICKS = 20L;
	private static final int DISCOVERY_MAX_WORK_UNITS_PER_TICK = 32;
	private static final int PERIODIC_DISCOVERY_CHECKPOINT_HOUR = 6;
	private static long lastPeriodicDiscoveryDay = Long.MIN_VALUE;
	private static int periodicDiscoveryCursor;
	static final Map<String, DirtState> dirtBlocksByKey = new LinkedHashMap<>();
	static final Map<ChunkRefKey, Set<String>> dirtKeysByChunk = new LinkedHashMap<>();
	static final Map<ColumnRefKey, Set<String>> dirtKeysByColumn = new LinkedHashMap<>();
	private static final Map<CandidatePositionKey, Integer> CANDIDATE_POSITION_MASKS = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, Set<CandidatePositionKey>> CANDIDATE_POSITION_KEYS_BY_CHUNK = new LinkedHashMap<>();
	static final int CANDIDATE_DIRT = 1;
	static final int CANDIDATE_TREE = 1 << 1;
	static final int CANDIDATE_CACTUS = 1 << 2;
	static final int CANDIDATE_GRASS = 1 << 3;
	static final int CANDIDATE_FOLIAGE = 1 << 4;
	static final int CANDIDATE_DECAY = 1 << 5;
	static final int CANDIDATE_WET = 1 << 6;

	private static final ChunkAPIManager.ChunkLifecycleListener CHUNK_LISTENER = new ChunkAPIManager.ChunkLifecycleListener() {
		@Override
		public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
			scheduleLoadedChunkDiscovery(level, chunkX, chunkZ);
		}

		@Override
		public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
			persistAndEvictChunkState(level, chunkX, chunkZ);
			removeLoadedDiscoveryChunk(level, chunkX, chunkZ);
			removePeriodicDiscoveryChunk(level, chunkX, chunkZ);
		}
	};
	private static String cachedProbeLevelId = "";
	private static long cachedProbeGameTime = Long.MIN_VALUE;
	private static int cachedProbeX;
	private static int cachedProbeZ;
	private static BlockPos cachedGroundPosition;
	private static String cachedAbsoluteTimeLevelId = "";
	private static long cachedAbsoluteTimeGameTime = Long.MIN_VALUE;
	private static long cachedAbsoluteDayTime = Long.MIN_VALUE;

	EcosystemAPIManager() {
	}

	public static void initialize() {
		EcosystemConfigManager.initialize();
		EcosystemNaturalGrowthManager.initialize();
		EcosystemNaturalErosionManager.initialize();
		EcosystemNaturalDecayManager.initialize();
		loadConfig();
		ChunkAPIManager.registerChunkLifecycleListener(CHUNK_LISTENER);
	}

	public static void reset() {
		EcosystemConfigManager.reset();
		EcosystemNaturalGrowthManager.reset();
		EcosystemNaturalErosionManager.reset();
		EcosystemNaturalDecayManager.reset();
		dirtBlocksByKey.clear();
		dirtKeysByChunk.clear();
		dirtKeysByColumn.clear();
		CANDIDATE_POSITION_MASKS.clear();
		CANDIDATE_POSITION_KEYS_BY_CHUNK.clear();
		EcosystemNaturalGrowthManager.clearTrackedCandidateState();
		EcosystemNaturalDecayManager.clearTrackedCandidateState();
		PERSISTED_CHUNK_KEYS.clear();
		RETAINED_UNLOADED_CHUNK_KEYS.clear();
		LOADED_PERSISTED_CHUNK_KEYS.clear();
		LOADED_DISCOVERY_CHUNK_KEYS.clear();
		DIRTY_CHUNK_KEYS.clear();
		PERIODIC_DISCOVERY_CHUNKS.clear();
		PERIODIC_DISCOVERY_CHUNK_KEYS.clear();
		lastPeriodicDiscoveryDay = Long.MIN_VALUE;
		periodicDiscoveryCursor = 0;
		SchedulerAdaptiveIntervalAPIManager.clearSystem(DISCOVERY_INTERVAL_SYSTEM_ID);
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
		loadingPersistedData = false;
		cachedGroundPosition = null;
		cachedProbeLevelId = "";
		cachedProbeGameTime = Long.MIN_VALUE;
		cachedAbsoluteTimeLevelId = "";
		cachedAbsoluteTimeGameTime = Long.MIN_VALUE;
		cachedAbsoluteDayTime = Long.MIN_VALUE;
	}

	public static void onServerTick(MinecraftServer server) {
		if (server == null || !isEnabled()) {
			return;
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}

		long absoluteDayTime = TimeAPIManager.getCurrentAbsoluteDayTime(overworld);
		long currentDay = TimeAPIManager.getDay(absoluteDayTime);
		if (hasPassedPeriodicDiscoveryCheckpoint(absoluteDayTime) && currentDay > lastPeriodicDiscoveryDay) {
			beginPeriodicDiscovery(server);
			lastPeriodicDiscoveryDay = currentDay;
		}

		if (PERIODIC_DISCOVERY_CHUNKS.isEmpty()) {
			return;
		}

		long interval = SchedulerAdaptiveIntervalAPIManager.resolve(
			DISCOVERY_INTERVAL_SYSTEM_ID,
			server,
			DISCOVERY_MIN_INTERVAL_TICKS,
			DISCOVERY_MAX_INTERVAL_TICKS
		);
		processPeriodicDiscoveryWork(resolveDiscoveryWorkUnits(interval));
	}

	private static int resolveDiscoveryWorkUnits(long adaptiveInterval) {
		long clampedInterval = Math.max(DISCOVERY_MIN_INTERVAL_TICKS, Math.min(DISCOVERY_MAX_INTERVAL_TICKS, adaptiveInterval));
		long intervalSpan = DISCOVERY_MAX_INTERVAL_TICKS - DISCOVERY_MIN_INTERVAL_TICKS;
		if (intervalSpan <= 0L) {
			return DISCOVERY_MAX_WORK_UNITS_PER_TICK;
		}
		long workSpan = DISCOVERY_MAX_WORK_UNITS_PER_TICK - 1L;
		return Math.max(1, DISCOVERY_MAX_WORK_UNITS_PER_TICK - (int) Math.round(
			((double) (clampedInterval - DISCOVERY_MIN_INTERVAL_TICKS) * workSpan) / intervalSpan
		));
	}

	private static void beginPeriodicDiscovery(MinecraftServer server) {
		PERIODIC_DISCOVERY_CHUNKS.clear();
		PERIODIC_DISCOVERY_CHUNK_KEYS.clear();
		periodicDiscoveryCursor = 0;
		for (ServerLevel level : server.getAllLevels()) {
			if (level == null) {
				continue;
			}
			level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> {
				if (chunk == null) {
					return;
				}
				ChunkRefKey key = new ChunkRefKey(levelId(level), chunk.getPos().x(), chunk.getPos().z());
				if (!PERIODIC_DISCOVERY_CHUNK_KEYS.add(key)) {
					return;
				}
				PERIODIC_DISCOVERY_CHUNKS.add(new DiscoveryChunkState(level, key, chunk));
			});
		}
	}

	private static void processPeriodicDiscoveryWork(int workUnits) {
		int remaining = Math.max(0, workUnits);
		while (remaining-- > 0 && !PERIODIC_DISCOVERY_CHUNKS.isEmpty()) {
			if (periodicDiscoveryCursor >= PERIODIC_DISCOVERY_CHUNKS.size()) {
				periodicDiscoveryCursor = 0;
			}
			DiscoveryChunkState discoveryChunk = PERIODIC_DISCOVERY_CHUNKS.get(periodicDiscoveryCursor);
			if (!ChunkAPIManager.isChunkAccessible(
				discoveryChunk.level(),
				discoveryChunk.key().chunkX(),
				discoveryChunk.key().chunkZ()
			)) {
				removePeriodicDiscoveryState(periodicDiscoveryCursor);
				continue;
			}

			if (!advanceDiscoveryChunk(discoveryChunk)) {
				// Keep the selected chunk active until its column and decay cursors finish.
				// The adaptive interval controls the one-column rate; queued chunks do not
				// dilute that rate for the chunk currently being discovered.
				continue;
			}
			LOADED_DISCOVERY_CHUNK_KEYS.add(discoveryChunk.key());
			removePeriodicDiscoveryState(periodicDiscoveryCursor);
		}
	}

	private static boolean advanceDiscoveryChunk(DiscoveryChunkState state) {
		if (state == null || state.chunk() == null) {
			return true;
		}
		int columnIndex = state.nextColumn < 256 ? state.nextColumn++ : -1;
		if (columnIndex >= 0) {
			discoverSurfaceColumn(state, columnIndex);
			if (isNaturalDecayEnabled()) {
				EcosystemNaturalDecayManager.discoverColumn(
					state.level(),
					state.chunk(),
					state.key().chunkX(),
					state.key().chunkZ(),
					columnIndex,
					state
				);
			}
		}
		state.decayComplete = !isNaturalDecayEnabled() || state.nextColumn >= 256;
		if (state.nextColumn >= 256 && !state.candidatesFinalized) {
			EcosystemNaturalGrowthManager.finalizeDiscoveryCandidates(state);
			state.candidatesFinalized = true;
		}
		return state.nextColumn >= 256 && state.decayComplete;
	}

	private static void removePeriodicDiscoveryState(int index) {
		if (index < 0 || index >= PERIODIC_DISCOVERY_CHUNKS.size()) {
			return;
		}
		DiscoveryChunkState removed = PERIODIC_DISCOVERY_CHUNKS.remove(index);
		if (removed != null) {
			PERIODIC_DISCOVERY_CHUNK_KEYS.remove(removed.key());
		}
		if (index < periodicDiscoveryCursor) {
			periodicDiscoveryCursor--;
		}
		periodicDiscoveryCursor = Math.max(0, Math.min(periodicDiscoveryCursor, PERIODIC_DISCOVERY_CHUNKS.size()));
	}

	private static void removePeriodicDiscoveryChunk(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null) {
			return;
		}
		ChunkRefKey key = new ChunkRefKey(levelId(level), chunkX, chunkZ);
		for (int index = PERIODIC_DISCOVERY_CHUNKS.size() - 1; index >= 0; index--) {
			if (!PERIODIC_DISCOVERY_CHUNKS.get(index).key().equals(key)) {
				continue;
			}
			removePeriodicDiscoveryState(index);
		}
		periodicDiscoveryCursor = Math.max(0, Math.min(periodicDiscoveryCursor, PERIODIC_DISCOVERY_CHUNKS.size()));
	}

	private static void scheduleLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null || !isEnabled() || !ChunkAPIManager.isChunkAccessible(level, chunkX, chunkZ)) {
			return;
		}
		ChunkRefKey key = new ChunkRefKey(levelId(level), chunkX, chunkZ);
		if (LOADED_DISCOVERY_CHUNK_KEYS.contains(key) || !PERIODIC_DISCOVERY_CHUNK_KEYS.add(key)) {
			return;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
		if (chunk == null) {
			PERIODIC_DISCOVERY_CHUNK_KEYS.remove(key);
			return;
		}
		PERIODIC_DISCOVERY_CHUNKS.add(new DiscoveryChunkState(level, key, chunk));
	}

	private static void removeLoadedDiscoveryChunk(ServerLevel level, int chunkX, int chunkZ) {
		if (level != null) {
			LOADED_DISCOVERY_CHUNK_KEYS.remove(new ChunkRefKey(levelId(level), chunkX, chunkZ));
		}
	}

	private static void discoverSurfaceColumn(DiscoveryChunkState discoveryState, int columnIndex) {
		ServerLevel level = discoveryState == null ? null : discoveryState.level();
		int chunkX = discoveryState == null ? 0 : discoveryState.key().chunkX();
		int chunkZ = discoveryState == null ? 0 : discoveryState.key().chunkZ();
		if (level == null || !isEnabled()) {
			return;
		}
		int localX = columnIndex & 15;
		int localZ = columnIndex >> 4;
		int x = (chunkX << 4) + localX;
		int z = (chunkZ << 4) + localZ;
		int topY = Math.min(
			level.getMaxY() - 1,
			level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
		);
		if (topY < level.getMinY()) {
			return;
		}
		BlockPos groundPos = new BlockPos(x, topY, z);
		SurfaceDiscoverySample sample = new SurfaceDiscoverySample(
			groundPos,
			level.getBlockState(groundPos),
			level.getBlockState(groundPos.above())
		);
		EcosystemNaturalGrowthManager.discoverColumn(level, chunkX, chunkZ, sample, discoveryState);
		EcosystemNaturalErosionManager.discoverColumn(level, chunkX, chunkZ, sample);
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
				if (chunk != null) {
					loadPersistedChunkData(level, chunk.getPos().x(), chunk.getPos().z());
					scheduleLoadedChunkDiscovery(level, chunk.getPos().x(), chunk.getPos().z());
				}
			});
		}
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
				dirtKeysByColumn.clear();
				CANDIDATE_POSITION_MASKS.clear();
				CANDIDATE_POSITION_KEYS_BY_CHUNK.clear();
				EcosystemNaturalGrowthManager.clearTrackedCandidateState();
				EcosystemNaturalDecayManager.clearTrackedCandidateState();
				EcosystemNaturalGrowthManager.reset();
				EcosystemNaturalErosionManager.reset();
				EcosystemNaturalDecayManager.reset();
				dirty = false;
				return;
			}

			dirtBlocksByKey.clear();
			dirtKeysByChunk.clear();
			dirtKeysByColumn.clear();
			CANDIDATE_POSITION_MASKS.clear();
			CANDIDATE_POSITION_KEYS_BY_CHUNK.clear();
			EcosystemNaturalGrowthManager.clearTrackedCandidateState();
			EcosystemNaturalDecayManager.clearTrackedCandidateState();
			EcosystemNaturalGrowthManager.reset();
			EcosystemNaturalErosionManager.reset();
			EcosystemNaturalDecayManager.reset();

			long autoSaveIntervalTicks = DataWorldChunkAPIManager.getAutoSaveIntervalTicks();
			lastAutosaveBucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), autoSaveIntervalTicks);
			dirty = false;
			DIRTY_CHUNK_KEYS.clear();
		} finally {
			loadingPersistedData = false;
		}
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}

		long autoSaveIntervalTicks = DataWorldChunkAPIManager.getAutoSaveIntervalTicks();
		long bucket = Math.floorDiv(TimeAPIManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket == lastAutosaveBucket) {
			return;
		}

		lastAutosaveBucket = bucket;
		if (dirty) {
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}

		Set<ChunkRefKey> currentChunkKeys = collectCurrentChunkKeys();
		currentChunkKeys.addAll(RETAINED_UNLOADED_CHUNK_KEYS);
		Set<ChunkRefKey> dirtyChunkKeys = collectDirtyChunkKeys();
		Set<ChunkRefKey> staleChunkKeys = new LinkedHashSet<>(PERSISTED_CHUNK_KEYS);
		staleChunkKeys.removeAll(currentChunkKeys);
		for (ChunkRefKey chunkKey : dirtyChunkKeys) {
			if (!currentChunkKeys.contains(chunkKey)) {
				continue;
			}
			JsonObject chunkData = createChunkPersistedData(chunkKey);
			if (chunkData == null) {
				continue;
			}
			DataWorldChunkAPIManager.setChunkSystemData(
				new DataWorldChunkAPIManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID,
				chunkData
			);
		}
		for (ChunkRefKey chunkKey : staleChunkKeys) {
			DataWorldChunkAPIManager.removeChunkSystemData(
				new DataWorldChunkAPIManager.ChunkDataKey(chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ()),
				DATA_SYSTEM_ID
			);
		}
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(currentChunkKeys);
		DIRTY_CHUNK_KEYS.clear();
		dirty = false;
	}

	private static void loadPersistedChunkData(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null || !isEnabled()) return;
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), chunkX, chunkZ);
		RETAINED_UNLOADED_CHUNK_KEYS.remove(chunkKey);
		if (chunkKey.levelId().isBlank() || !LOADED_PERSISTED_CHUNK_KEYS.add(chunkKey)) return;
		JsonObject source = DataWorldChunkAPIManager.getChunkSystemData(level, chunkX, chunkZ, DATA_SYSTEM_ID);
		if (source != null && !source.isEmpty()) {
			source.addProperty(FIELD_LEVEL_ID, chunkKey.levelId());
			source.addProperty(FIELD_CHUNK_X, chunkX);
			source.addProperty(FIELD_CHUNK_Z, chunkZ);
			applyPersistedData(source);
		}
	}

	static boolean trackDirtCandidateForMode(ServerLevel world, BlockPos dirtPos, BlockState state, String mode) {
		return trackDirtCandidateForMode(world, dirtPos, state, mode, null);
	}

	static boolean trackDirtCandidateForMode(ServerLevel world, BlockPos dirtPos, BlockState state, String mode, BlockState discoveredAboveState) {
		if (world == null || dirtPos == null || state == null || mode == null || mode.isBlank()) {
			return false;
		}
		if (!isModeEnabled(mode)) {
			return false;
		}
		if (!isCandidateForMode(world, dirtPos, state, mode, discoveredAboveState)) {
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
		return isCandidateForMode(world, blockPos, state, mode, null);
	}

	static boolean isCandidateForMode(ServerLevel world, BlockPos blockPos, BlockState state, String mode, BlockState discoveredAboveState) {
		if (!isModeEnabled(mode)) {
			return false;
		}
		if (MODE_WET.equals(mode)) {
			return EcosystemNaturalErosionManager.isWaterErosionEnabled() && EcosystemNaturalErosionManager.isWetTrackedCandidate(world, blockPos, state);
		}
		if (MODE_SURFACE_DIRT.equals(mode)) {
			return isBlockGrowthEnabled() && EcosystemNaturalGrowthManager.isSurfaceDirtCandidate(world, blockPos, state, discoveredAboveState);
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
		return 7.0d * TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE;
	}

	static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType) {
		return EcosystemNaturalGrowthManager.tryGrowTreeAtGround(world, groundPos, treeType);
	}

	static boolean isLavaMagmaSourceBlockId(String blockId) {
		return EcosystemNaturalErosionManager.isLavaMagmaSourceBlockId(blockId);
	}

	private static DirtState putDirtState(String key, DirtState value) {
		DirtState previous = dirtBlocksByKey.put(key, value);
		ChunkRefKey previousChunkKey = null;
		if (previous != null) {
			previousChunkKey = chunkRefForPos(previous.levelId, previous.dirtPos);
			removeCandidatePositionBit(previous.levelId, previous.dirtPos, MODE_WET.equals(previous.mode) ? CANDIDATE_WET : CANDIDATE_DIRT);
			removeChunkIndex(dirtKeysByChunk, previousChunkKey, key);
			removeColumnIndex(previous, key);
			markChunkDirty(previousChunkKey);
		}
		ChunkRefKey nextChunkKey = null;
		if (value != null) {
			nextChunkKey = chunkRefForPos(value.levelId, value.dirtPos);
			addCandidatePositionBit(value.levelId, value.dirtPos, MODE_WET.equals(value.mode) ? CANDIDATE_WET : CANDIDATE_DIRT);
			addChunkIndex(dirtKeysByChunk, nextChunkKey, key);
			addColumnIndex(value, key);
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
			removeCandidatePositionBit(removed.levelId, removed.dirtPos, MODE_WET.equals(removed.mode) ? CANDIDATE_WET : CANDIDATE_DIRT);
			removeChunkIndex(dirtKeysByChunk, chunkKey, key);
			removeColumnIndex(removed, key);
			markChunkDirty(chunkKey);
			syncChunkProcessorTracking(chunkKey);
		}
		return removed;
	}

	private static void addColumnIndex(DirtState dirt, String entryKey) {
		if (dirt == null) {
			return;
		}
		dirtKeysByColumn.computeIfAbsent(
			new ColumnRefKey(dirt.levelId, BlockPos.getX(dirt.dirtPos), BlockPos.getZ(dirt.dirtPos)),
			ignored -> new LinkedHashSet<>()
		).add(entryKey);
	}

	private static void removeColumnIndex(DirtState dirt, String entryKey) {
		if (dirt == null) {
			return;
		}
		ColumnRefKey columnKey = new ColumnRefKey(dirt.levelId, BlockPos.getX(dirt.dirtPos), BlockPos.getZ(dirt.dirtPos));
		Set<String> keys = dirtKeysByColumn.get(columnKey);
		if (keys == null) {
			return;
		}
		keys.remove(entryKey);
		if (keys.isEmpty()) {
			dirtKeysByColumn.remove(columnKey);
		}
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
		// Candidate masks are maintained incrementally by candidate stores.
	}

	static int candidateMaskAt(ServerLevel world, BlockPos position) {
		if (world == null || position == null) {
			return 0;
		}
		return CANDIDATE_POSITION_MASKS.getOrDefault(
			new CandidatePositionKey(levelId(world), position.asLong()),
			0
		);
	}

	private static void addCandidatePositionMask(String levelId, long position, int bit) {
		if (levelId == null || levelId.isBlank() || position == Long.MIN_VALUE) {
			return;
		}
		CandidatePositionKey key = new CandidatePositionKey(levelId, position);
		CANDIDATE_POSITION_MASKS.put(key, CANDIDATE_POSITION_MASKS.getOrDefault(key, 0) | bit);
		CANDIDATE_POSITION_KEYS_BY_CHUNK
			.computeIfAbsent(chunkRefForPos(levelId, position), ignored -> new LinkedHashSet<>())
			.add(key);
	}

	static void addCandidatePositionBit(String levelId, long position, int bit) {
		addCandidatePositionMask(levelId, position, bit);
	}

	static void removeCandidatePositionBit(String levelId, long position, int bit) {
		if (levelId == null || levelId.isBlank() || position == Long.MIN_VALUE) {
			return;
		}
		CandidatePositionKey key = new CandidatePositionKey(levelId, position);
		int currentMask = CANDIDATE_POSITION_MASKS.getOrDefault(key, 0);
		int nextMask = currentMask & ~bit;
		if (nextMask == currentMask) return;
		if (nextMask == 0) {
			CANDIDATE_POSITION_MASKS.remove(key);
			ChunkRefKey chunkKey = chunkRefForPos(levelId, position);
			Set<CandidatePositionKey> keys = CANDIDATE_POSITION_KEYS_BY_CHUNK.get(chunkKey);
			if (keys != null) {
				keys.remove(key);
				if (keys.isEmpty()) CANDIDATE_POSITION_KEYS_BY_CHUNK.remove(chunkKey);
			}
		} else {
			CANDIDATE_POSITION_MASKS.put(key, nextMask);
		}
	}

	static long deriveCandidateStartTime(long lastProcessedAbsoluteDayTime, double progressTicks) {
		if (lastProcessedAbsoluteDayTime <= 0L || !Double.isFinite(progressTicks) || progressTicks <= 0.0d) {
			return Math.max(0L, lastProcessedAbsoluteDayTime);
		}
		return Math.max(0L, lastProcessedAbsoluteDayTime - (long) progressTicks);
	}

	private static boolean hasPassedPeriodicDiscoveryCheckpoint(long absoluteDayTime) {
		return TimeAPIManager.getClockHour(absoluteDayTime) >= PERIODIC_DISCOVERY_CHECKPOINT_HOUR;
	}

	private static void persistAndEvictChunkState(ServerLevel level, int chunkX, int chunkZ) {
		if (level == null || !isEnabled()) {
			return;
		}
		ChunkRefKey chunkKey = new ChunkRefKey(levelId(level), chunkX, chunkZ);
		if (chunkKey.levelId().isBlank()) {
			return;
		}

		JsonObject chunkData = createChunkPersistedData(chunkKey);
		DataWorldChunkAPIManager.ChunkDataKey dataKey = new DataWorldChunkAPIManager.ChunkDataKey(
			chunkKey.levelId(),
			chunkKey.chunkX(),
			chunkKey.chunkZ()
		);
		if (chunkData != null) {
			DataWorldChunkAPIManager.setChunkSystemData(dataKey, DATA_SYSTEM_ID, chunkData);
			PERSISTED_CHUNK_KEYS.add(chunkKey);
			RETAINED_UNLOADED_CHUNK_KEYS.add(chunkKey);
		} else {
			DataWorldChunkAPIManager.removeChunkSystemData(dataKey, DATA_SYSTEM_ID);
			PERSISTED_CHUNK_KEYS.remove(chunkKey);
			RETAINED_UNLOADED_CHUNK_KEYS.remove(chunkKey);
		}
		DataWorldChunkAPIManager.savePersistedData(level.getServer());

		evictRuntimeChunkState(chunkKey);
	}

	private static void evictRuntimeChunkState(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}

		Set<String> dirtKeys = dirtKeysByChunk.remove(chunkKey);
		if (dirtKeys != null) {
			for (String dirtKey : dirtKeys) {
				DirtState dirt = dirtBlocksByKey.remove(dirtKey);
				if (dirt == null) {
					continue;
				}
				removeCandidatePositionBit(
					dirt.levelId,
					dirt.dirtPos,
					MODE_WET.equals(dirt.mode) ? CANDIDATE_WET : CANDIDATE_DIRT
				);
				removeColumnIndex(dirt, dirtKey);
			}
		}
		dirtKeysByColumn.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());

		EcosystemNaturalGrowthManager.evictTrackedCandidateState(chunkKey);
		EcosystemNaturalDecayManager.evictTrackedCandidateState(chunkKey);

		Set<CandidatePositionKey> positionKeys = CANDIDATE_POSITION_KEYS_BY_CHUNK.remove(chunkKey);
		if (positionKeys != null) {
			for (CandidatePositionKey positionKey : positionKeys) {
				CANDIDATE_POSITION_MASKS.remove(positionKey);
			}
		}
		LOADED_PERSISTED_CHUNK_KEYS.remove(chunkKey);
		DIRTY_CHUNK_KEYS.remove(chunkKey);
		dirty = !DIRTY_CHUNK_KEYS.isEmpty();
	}

	static CandidateProgress advanceCandidateProgress(
		double trackedProgress,
		long lastProcessedAbsoluteDayTime,
		long currentAbsoluteDayTime,
		double requiredTicks
	) {
		double safeRequired = Math.max(1.0d, requiredTicks);
		double safeProgress = Double.isFinite(trackedProgress)
			? Math.max(0.0d, Math.min(safeRequired, trackedProgress))
			: 0.0d;
		long safeLast = Math.max(0L, lastProcessedAbsoluteDayTime);
		long safeCurrent = Math.max(0L, currentAbsoluteDayTime);

		// A rollback is a new clock baseline, not negative growth. Preserve the
		// accumulated progress and let subsequent forward time advance it again.
		if (safeCurrent < safeLast) {
			return new CandidateProgress(
				safeProgress,
				safeCurrent,
				deriveCandidateStartTime(safeCurrent, safeProgress)
			);
		}

		double nextProgress = Math.min(safeRequired, safeProgress + (safeCurrent - safeLast));
		return new CandidateProgress(
			nextProgress,
			safeCurrent,
			deriveCandidateStartTime(safeCurrent, nextProgress)
		);
	}

	static double resolveCandidateProgress(long startedAbsoluteDayTime, long currentAbsoluteDayTime, double requiredTicks) {
		long safeStart = Math.max(0L, startedAbsoluteDayTime);
		long safeCurrent = Math.max(safeStart, currentAbsoluteDayTime);
		return Math.min(Math.max(1.0d, requiredTicks), Math.max(0L, safeCurrent - safeStart));
	}

	record CandidateProgress(double progressGrowthTicks, long lastProcessedAbsoluteDayTime, long startedAbsoluteDayTime) {
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

	static String dirtKey(ServerLevel world, BlockPos dirtPos) {
		return levelId(world) + "|" + (dirtPos == null ? -1L : dirtPos.asLong());
	}

	static String levelId(ServerLevel world) {
		String dimensionId = DataWorldChunkAPIManager.dimensionId(world);
		return dimensionId.isBlank() ? "" : dimensionId;
	}

	static ChunkRefKey chunkRefForPos(String levelId, long packedBlockPos) {
		return new ChunkRefKey(levelId, BlockPos.getX(packedBlockPos) >> 4, BlockPos.getZ(packedBlockPos) >> 4);
	}

	private static long resolveAbsoluteDayTime(ServerLevel world) {
		if (world == null) {
			return TimeAPIManager.getCurrentAbsoluteDayTime();
		}
		return TimeAPIManager.getCurrentAbsoluteDayTime(world);
	}

	static long resolveCachedAbsoluteDayTime(ServerLevel world) {
		if (world == null) {
			return TimeAPIManager.getCurrentAbsoluteDayTime();
		}
		String currentLevelId = levelId(world);
		long currentGameTime = world.getGameTime();
		if (currentGameTime == cachedAbsoluteTimeGameTime && currentLevelId.equals(cachedAbsoluteTimeLevelId)) {
			return cachedAbsoluteDayTime;
		}
		cachedAbsoluteTimeLevelId = currentLevelId;
		cachedAbsoluteTimeGameTime = currentGameTime;
		cachedAbsoluteDayTime = TimeAPIManager.getCurrentAbsoluteDayTime(world);
		return cachedAbsoluteDayTime;
	}

	static BlockPos resolveCachedGroundPosition(ServerLevel world, BlockPos position) {
		if (world == null || position == null) {
			return null;
		}
		String currentLevelId = levelId(world);
		long currentGameTime = world.getGameTime();
		int currentX = position.getX();
		int currentZ = position.getZ();
		if (cachedGroundPosition != null
			&& currentGameTime == cachedProbeGameTime
			&& currentX == cachedProbeX
			&& currentZ == cachedProbeZ
			&& currentLevelId.equals(cachedProbeLevelId)) {
			return cachedGroundPosition;
		}
		int topY = Math.min(
			world.getMaxY() - 1,
			world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, currentX, currentZ) - 1
		);
		BlockPos groundPosition = topY < world.getMinY() ? null : new BlockPos(currentX, topY, currentZ);
		cachedProbeLevelId = currentLevelId;
		cachedProbeGameTime = currentGameTime;
		cachedProbeX = currentX;
		cachedProbeZ = currentZ;
		cachedGroundPosition = groundPosition;
		return groundPosition;
	}

	static void invalidateCachedGroundPosition() {
		cachedGroundPosition = null;
	}

	static long normalizePreviousAbsoluteTick(long previousAbsoluteTick, long currentAbsoluteTick) {
		long safePrevious = Math.max(0L, previousAbsoluteTick);
		long safeCurrent = Math.max(0L, currentAbsoluteTick);
		if (safePrevious > safeCurrent) {
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

		JSONFormatAPIManager.ArrayBuilder dirtBlocks = JSONFormatAPIManager.array();
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

		JSONFormatAPIManager.ObjectBuilder builder = JSONFormatAPIManager.object()
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

	static JsonObject buildChunkPersistedData(Consumer<JSONFormatAPIManager.ObjectBuilder> writer) {
		if (writer == null) {
			return null;
		}
		JSONFormatAPIManager.ObjectBuilder builder = JSONFormatAPIManager.object();
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

	static final class DiscoveryChunkState {
		final ServerLevel level;
		final ChunkRefKey key;
		final LevelChunk chunk;
		int nextColumn;
		int decaySectionIndex;
		int decayBlockIndex;
		boolean decayComplete;
		boolean candidatesFinalized;
		long sampledTreeGroundPos = Long.MIN_VALUE;
		int sampledTreeCandidateCount;
		long sampledCactusGroundPos = Long.MIN_VALUE;
		int sampledCactusCandidateCount;
		final List<Long> sampledGrassGroundPositions = new ArrayList<>();
		int sampledGrassCandidateCount;
		final List<Long> sampledDesertFoliageGroundPositions = new ArrayList<>();
		int sampledDesertFoliageCandidateCount;
		final List<Long> sampledWildflowerGroundPositions = new ArrayList<>();
		int sampledWildflowerCandidateCount;
		final List<Long> sampledPinkPetalGroundPositions = new ArrayList<>();
		int sampledPinkPetalCandidateCount;

		DiscoveryChunkState(ServerLevel level, ChunkRefKey key, LevelChunk chunk) {
			this.level = level;
			this.key = key;
			this.chunk = chunk;
			this.decayComplete = false;
		}

		ServerLevel level() {
			return level;
		}

		ChunkRefKey key() {
			return key;
		}

		LevelChunk chunk() {
			return chunk;
		}
	}

	record SurfaceDiscoverySample(BlockPos groundPos, BlockState groundState, BlockState aboveState) {
	}

	record ColumnRefKey(String levelId, int blockX, int blockZ) {
	}

	record CandidatePositionKey(String levelId, long position) {
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
		long startedAbsoluteDayTime;

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
			this.startedAbsoluteDayTime = deriveCandidateStartTime(this.lastProcessedAbsoluteDayTime, this.progressGrowthTicks);
		}

		JsonObject toJson() {
			return JSONFormatAPIManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_CACTUS_GROUND_POS, groundPos)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.put(FIELD_STARTED_ABSOLUTE_DAY_TIME, startedAbsoluteDayTime)
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
			CactusCandidateState result = new CactusCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
			long started = getLong(source, FIELD_STARTED_ABSOLUTE_DAY_TIME, Long.MIN_VALUE);
			if (started != Long.MIN_VALUE) result.startedAbsoluteDayTime = Math.max(0L, started);
			return result;
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
		long startedAbsoluteDayTime;

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
			this.startedAbsoluteDayTime = deriveCandidateStartTime(this.lastProcessedAbsoluteDayTime, this.progressGrowthTicks);
		}

		JsonObject toJson() {
			return JSONFormatAPIManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_GRASS_GROUND_POS, groundPos)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.put(FIELD_STARTED_ABSOLUTE_DAY_TIME, startedAbsoluteDayTime)
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
			GrassCandidateState result = new GrassCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
			long started = getLong(source, FIELD_STARTED_ABSOLUTE_DAY_TIME, Long.MIN_VALUE);
			if (started != Long.MIN_VALUE) result.startedAbsoluteDayTime = Math.max(0L, started);
			return result;
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
		long startedAbsoluteDayTime;

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
			this.startedAbsoluteDayTime = deriveCandidateStartTime(this.lastProcessedAbsoluteDayTime, this.progressGrowthTicks);
		}

		JsonObject toJson() {
			return JSONFormatAPIManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_FOLIAGE_GROUND_POS, groundPos)
				.put(FIELD_FOLIAGE_TYPE, foliageType)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.put(FIELD_STARTED_ABSOLUTE_DAY_TIME, startedAbsoluteDayTime)
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
				FoliageCandidateState result = new FoliageCandidateState(
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
				long started = getLong(source, FIELD_STARTED_ABSOLUTE_DAY_TIME, Long.MIN_VALUE);
				if (started != Long.MIN_VALUE) result.startedAbsoluteDayTime = Math.max(0L, started);
				return result;
		}
	}

	static final class TreeDecayCandidateState {
		final String levelId;
		final int chunkX;
		final int chunkZ;
		final long leafPos;
		final long targetPos;
		final String initialSeasonId;
		final double requiredDecayTicks;
		double progressDecayTicks;
		long lastProcessedAbsoluteDayTime;
		long startedAbsoluteDayTime;

		TreeDecayCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long leafPos,
			long targetPos,
			String initialSeasonId,
			double requiredDecayTicks,
			double progressDecayTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.leafPos = leafPos;
			this.targetPos = targetPos;
			this.initialSeasonId = EcosystemConfigManager.normalize(initialSeasonId);
			this.requiredDecayTicks = Math.max(1.0d, requiredDecayTicks);
			this.progressDecayTicks = Math.max(0.0d, Math.min(this.requiredDecayTicks, progressDecayTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
			this.startedAbsoluteDayTime = deriveCandidateStartTime(this.lastProcessedAbsoluteDayTime, this.progressDecayTicks);
		}

			JsonObject toJson() {
				return JSONFormatAPIManager.object()
					.put(FIELD_LEVEL_ID, levelId)
					.put(FIELD_CHUNK_X, chunkX)
					.put(FIELD_CHUNK_Z, chunkZ)
					.put(FIELD_TREE_DECAY_SOURCE_POS, leafPos)
					.put(FIELD_TREE_DECAY_TARGET_POS, targetPos)
					.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
					.put(FIELD_REQUIRED_GROWTH_TICKS, requiredDecayTicks)
					.put(FIELD_PROGRESS_GROWTH_TICKS, progressDecayTicks)
					.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
					.put(FIELD_STARTED_ABSOLUTE_DAY_TIME, startedAbsoluteDayTime)
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
			long targetPos = getLong(source, FIELD_TREE_DECAY_TARGET_POS, Long.MIN_VALUE);
			if (targetPos == Long.MIN_VALUE) {
				return null;
			}
			long leafPos = getLong(source, FIELD_TREE_DECAY_SOURCE_POS, targetPos);
			String initialSeasonId = EcosystemConfigManager.normalize(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredDecayTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, EcosystemNaturalDecayManager.resolveTreeDecayRequiredTicks(null, initialSeasonId))
			);
			double progressDecayTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			TreeDecayCandidateState result = new TreeDecayCandidateState(
				levelId,
				chunkX,
				chunkZ,
				leafPos,
				targetPos,
				initialSeasonId,
				requiredDecayTicks,
				progressDecayTicks,
				lastProcessedAbsoluteDayTime
			);
			long started = getLong(source, FIELD_STARTED_ABSOLUTE_DAY_TIME, Long.MIN_VALUE);
			if (started != Long.MIN_VALUE) result.startedAbsoluteDayTime = Math.max(0L, started);
			return result;
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
		long startedAbsoluteDayTime;

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
			this.startedAbsoluteDayTime = deriveCandidateStartTime(this.lastProcessedAbsoluteDayTime, this.progressGrowthTicks);
		}

		JsonObject toJson() {
			return JSONFormatAPIManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_CHUNK_X, chunkX)
				.put(FIELD_CHUNK_Z, chunkZ)
				.put(FIELD_TREE_GROUND_POS, groundPos)
				.put(FIELD_TREE_TYPE, treeType)
				.put(FIELD_INITIAL_SEASON_ID, initialSeasonId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.put(FIELD_STARTED_ABSOLUTE_DAY_TIME, startedAbsoluteDayTime)
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
			TreeCandidateState result = new TreeCandidateState(
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
			long started = getLong(source, FIELD_STARTED_ABSOLUTE_DAY_TIME, Long.MIN_VALUE);
			if (started != Long.MIN_VALUE) result.startedAbsoluteDayTime = Math.max(0L, started);
			return result;
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
		long startedAbsoluteDayTime;

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
			this.startedAbsoluteDayTime = deriveCandidateStartTime(this.lastProcessedAbsoluteDayTime, this.progressGrowthTicks);
		}

		String key() {
			return levelId + "|" + dirtPos;
		}

		JsonObject toJson() {
			return JSONFormatAPIManager.object()
				.put(FIELD_LEVEL_ID, levelId)
				.put(FIELD_BLOCK_POS, dirtPos)
				.put(FIELD_MODE, mode)
				.put(FIELD_EROSION_RULE_ID, erosionRuleId)
				.put(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks)
				.put(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks)
				.put(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime)
				.put(FIELD_STARTED_ABSOLUTE_DAY_TIME, startedAbsoluteDayTime)
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
				MODE_SURFACE_DIRT.equals(mode) ? 3.0d * TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE : defaultErosionGrowthTicks()
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			DirtState result = new DirtState(
				levelId,
				dirtPos,
				mode,
				erosionRuleId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
			long started = getLong(source, FIELD_STARTED_ABSOLUTE_DAY_TIME, Long.MIN_VALUE);
			if (started != Long.MIN_VALUE) result.startedAbsoluteDayTime = Math.max(0L, started);
			return result;
		}
	}
}



