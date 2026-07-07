package madoku.craft.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class MadokuEcosystemManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuEcosystemManager.class);
	private static final String DATA_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String DATA_FILE_NAME = "madoku-ecosystem";
	private static final String CHUNK_DATA_FOLDER_NAME = "madoku-ecosystem-chunks";
	private static final String FIELD_CHUNK_FOLDER = "chunk-folder";
	private static final String FIELD_CHUNK_COUNT = "chunk-count";
	private static final String FIELD_CHUNKS = "chunks";
	private static final String FIELD_DATA_FILE = "data-file";
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
	private static final String BLOCK_ID_SHORT_GRASS = "minecraft:short_grass";
	private static final String BLOCK_ID_TALL_GRASS = "minecraft:tall_grass";
	private static final String BLOCK_ID_BUSH = "minecraft:bush";
	private static final String BLOCK_ID_DEAD_BUSH = "minecraft:dead_bush";
	private static final String BLOCK_ID_SHORT_DRY_GRASS = "minecraft:short_dry_grass";
	private static final String BLOCK_ID_TALL_DRY_GRASS = "minecraft:tall_dry_grass";
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
	private static final String EROSION_RULE_ID_LAVA_MAGMA = NaturalErosionConfigManager.FIELD_MAGMA_BLOCK;
	private static final long ABSOLUTE_TIME_ROLLBACK_RESET_TICKS = 20L;
	private static final Set<Block> TRACKABLE_WET_GROUND_BLOCKS = Set.of(
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
	private static final String BIOME_TAG_PREFIX = "#";
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	static volatile boolean dirty = false;
	private static volatile boolean ecosystemEnabled = true;
	static volatile NaturalGrowthConfigManager.Settings naturalGrowthSettings = NaturalGrowthConfigManager.defaults();
	static volatile NaturalErosionConfigManager.Settings naturalErosionSettings = NaturalErosionConfigManager.defaults();
	static volatile NaturalDecayConfigManager.Settings naturalDecaySettings = NaturalDecayConfigManager.defaults();
	private static volatile List<NaturalErosionConfigManager.NamedErosionRule> cachedErosionRules = List.of();
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

	static final Map<String, DirtState> dirtBlocksByKey = new LinkedHashMap<>();
	static final Map<ChunkRefKey, Set<String>> dirtKeysByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, ChunkDiscoveryAccumulator> discoveryAccumulatorsByChunk = new LinkedHashMap<>();

	private static final MadokuChunkManager.ChunkLifecycleListener CHUNK_LISTENER = new MadokuChunkManager.ChunkLifecycleListener() {
		@Override
		public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
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
			.field("data-folder", DATA_FOLDER_NAME)
			.field("data-file", DATA_FILE_NAME));
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
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
		resetUnifiedDiscoveryState();
		EcosystemNaturalGrowthManager.reset();
		EcosystemNaturalErosionManager.reset();
		EcosystemNaturalDecayManager.reset();
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

	private static boolean isErosionRuleEnabled(String ruleId) {
		if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(EcosystemConfigManager.normalize(ruleId))) {
			return EcosystemNaturalErosionManager.isLavaErosionEnabled();
		}
		return EcosystemNaturalErosionManager.isWaterErosionEnabled();
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

		Path indexFile = resolveEcosystemIndexFile(server);
		int loadedChunkFiles = 0;
		boolean loadedFromIndex = false;
		if (Files.isRegularFile(indexFile)) {
			try {
				JsonObject indexData = JsonStaticSystem.readManagedDocument(indexFile).main();
				if (indexData != null) {
					JsonElement chunksElement = indexData.get(FIELD_CHUNKS);
					if (chunksElement != null && chunksElement.isJsonArray()) {
						loadedFromIndex = true;
						for (JsonElement element : chunksElement.getAsJsonArray()) {
							if (element == null || !element.isJsonObject()) {
								continue;
							}
							JsonObject chunkDescriptor = element.getAsJsonObject();
							String levelId = getString(chunkDescriptor, FIELD_LEVEL_ID, "").trim();
							int chunkX = (int) getLong(chunkDescriptor, FIELD_CHUNK_X, Integer.MIN_VALUE);
							int chunkZ = (int) getLong(chunkDescriptor, FIELD_CHUNK_Z, Integer.MIN_VALUE);
							if (levelId.isEmpty() || chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
								continue;
							}
							Path chunkFile = resolveChunkPersistedDataPath(server, new ChunkRefKey(levelId, chunkX, chunkZ));
							if (!Files.isRegularFile(chunkFile)) {
								continue;
							}
							JsonObject source = JsonStaticSystem.readManagedDocument(chunkFile).main();
							if (applyPersistedData(source) != null) {
								loadedChunkFiles++;
							}
						}
					}
				}
			} catch (IOException exception) {
				LOGGER.error("Failed to load ecosystem index data from {}.", indexFile, exception);
				loadedFromIndex = false;
			}
		}
		if (!loadedFromIndex) {
			Path chunkDataRoot = resolveChunkDataRootDirectory(server);
			if (Files.isDirectory(chunkDataRoot)) {
				try (Stream<Path> paths = Files.walk(chunkDataRoot)) {
					for (Path file : (Iterable<Path>) paths
						.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().endsWith(".json"))
						::iterator) {
						JsonObject source = JsonStaticSystem.readManagedDocument(file).main();
						if (applyPersistedData(source) != null) {
							loadedChunkFiles++;
						}
					}
				} catch (IOException exception) {
					LOGGER.error("Failed to load ecosystem chunk data from {}.", chunkDataRoot, exception);
				}
			}
		}

		writeEcosystemIndex(server, loadedChunkFiles, collectChunkKeysForIndex());
		final int loadedChunkFileCount = loadedChunkFiles;
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		dirty = false;
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("load-persisted-data")
			.field("auto-save-ticks", autoSaveIntervalTicks)
			.field("dirty", dirty)
			.field("chunk-files", loadedChunkFileCount)
			.field("persisted-chunks", PERSISTED_CHUNK_KEYS.size()));
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
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
		emitEcosystemDebug("ecosystem.lifecycle", builder -> builder
			.subject("autosave")
			.field("bucket", bucket)
			.field("dirty", dirty));
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled() && !isNaturalDecayEnabled())) {
			return;
		}

		Set<ChunkRefKey> currentChunkKeys = collectChunkKeysForPersistence();
		Set<ChunkRefKey> staleChunkKeys = new LinkedHashSet<>(PERSISTED_CHUNK_KEYS);
		staleChunkKeys.removeAll(currentChunkKeys);
		int writtenChunkFiles = 0;
		for (ChunkRefKey chunkKey : currentChunkKeys) {
			JsonObject chunkData = createChunkPersistedData(chunkKey);
			if (chunkData == null) {
				continue;
			}
			writeChunkPersistedData(server, chunkKey, chunkData);
			writtenChunkFiles++;
		}
		for (ChunkRefKey chunkKey : staleChunkKeys) {
			deleteChunkPersistedData(server, chunkKey);
		}
		PERSISTED_CHUNK_KEYS.clear();
		PERSISTED_CHUNK_KEYS.addAll(currentChunkKeys);
		writeEcosystemIndex(server, writtenChunkFiles, currentChunkKeys);
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
		long gameplayTick = MadokuTicks.getGameplayTicks();
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
		long gameplayTick = MadokuTicks.getGameplayTicks();
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
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		if (block == Blocks.DIRT) {
			return true;
		}
		if (TRACKABLE_WET_GROUND_BLOCKS.contains(block) && EcosystemNaturalErosionManager.isWaterErosionEnabled()) {
			return true;
		}
		String blockId = EcosystemConfigManager.blockId(block);
		if (isLavaMagmaSourceBlockId(blockId) && EcosystemNaturalErosionManager.isLavaErosionEnabled()) {
			return true;
		}
		if (!EcosystemNaturalErosionManager.isWaterErosionEnabled()) {
			return false;
		}
		for (NaturalErosionConfigManager.NamedErosionRule rule : cachedErosionRules) {
			if (rule == null || rule.rule() == null || !rule.rule().enabled()) {
				continue;
			}
			if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(rule.ruleId())) {
				continue;
			}
			if (rule.rule().sourceBlocks().contains(blockId)) {
				return true;
			}
		}
		return false;
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
		return 7.0d * MadokuTime.MINECRAFT_TICKS_PER_CYCLE;
	}

	static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType) {
		if (world == null || groundPos == null || treeType == null || treeType.isBlank() || !isTreeGrowthEnabled(treeType)) {
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

	static boolean isLavaMagmaSourceBlockId(String blockId) {
		if (blockId == null || blockId.isBlank()) {
			return false;
		}
		NaturalErosionConfigManager.NamedErosionRule magmaRule = findErosionRuleById(EROSION_RULE_ID_LAVA_MAGMA);
		return magmaRule != null
			&& magmaRule.rule() != null
			&& magmaRule.rule().enabled()
			&& magmaRule.rule().sourceBlocks().contains(blockId);
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
		}
		ChunkRefKey nextChunkKey = null;
		if (value != null) {
			nextChunkKey = chunkRefForPos(value.levelId, value.dirtPos);
			addChunkIndex(dirtKeysByChunk, nextChunkKey, key);
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
		return MadokuChunkManager.normalizeLevelId(world);
	}

	static ChunkRefKey chunkRefForPos(String levelId, long packedBlockPos) {
		return new ChunkRefKey(levelId, BlockPos.getX(packedBlockPos) >> 4, BlockPos.getZ(packedBlockPos) >> 4);
	}

	private static long resolveAbsoluteDayTime(ServerLevel world) {
		if (world == null) {
			return MadokuTime.getCurrentAbsoluteDayTime();
		}
		return MadokuTime.getCurrentAbsoluteDayTime(world);
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
		if (world == null || groundPos == null) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		if (!world.getBlockState(growPos).isAir()) {
			return false;
		}

		return tryPlaceWeightedFoliageTarget(world, growPos, buildGrassFoliagePlacements());
	}

	static boolean tryGrowFoliageAtGround(ServerLevel world, BlockPos groundPos, String foliageType) {
		if (world == null || groundPos == null || !isVegetationGrowthEnabled(foliageType)) {
			return false;
		}

		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
		Block foliageBlock = EcosystemNaturalGrowthManager.resolveFoliageBlock(normalizedFoliageType);
		if (foliageBlock == null) {
			return false;
		}

		BlockPos foliagePos = groundPos.above();
		BlockState current = world.getBlockState(foliagePos);
		if (current == null) {
			return false;
		}
		if (current.isAir()) {
			BlockState placed = setFoliageAmount(foliageBlock.defaultBlockState(), 1);
			world.setBlockAndUpdate(foliagePos, placed);
			return true;
		}
		if (current.getBlock() != foliageBlock) {
			return false;
		}

		int amount = getFoliageAmount(current);
		int maxAmount = getFoliageMaxAmount(current);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = setFoliageAmount(current, amount + 1);
		if (updated == current) {
			return false;
		}
		world.setBlockAndUpdate(foliagePos, updated);
		return true;
	}

	static boolean tryGrowDesertFoliageAtGround(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null || !isDesertFoliageGrowthEnabled()) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		if (!world.getBlockState(growPos).isAir()) {
			return false;
		}
		return placeSummerDesertTarget(world, growPos);
	}

	static boolean tryGrowCactusAtGround(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null || !isCactusGrowthEnabled()) {
			return false;
		}
		BlockState groundState = world.getBlockState(groundPos);
		if (!EcosystemNaturalGrowthManager.isValidCactusGroundCandidate(world, groundPos, groundState)) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState next = Blocks.CACTUS.defaultBlockState();
		if (!next.canSurvive(world, growPos)) {
			return false;
		}
		world.setBlockAndUpdate(growPos, next);
		return true;
	}

	private static boolean placeSummerDesertTarget(ServerLevel world, BlockPos growPos) {
		return tryPlaceWeightedFoliageTarget(world, growPos, buildDesertFoliagePlacements());
	}

	static boolean tryApplyTreeDecayAtTarget(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null) {
			return false;
		}

		Block leafLitter = EcosystemConfigManager.resolveBlock(BLOCK_ID_LEAF_LITTER);
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
		return true;
	}

	static boolean isNaturallyGeneratedLeaf(BlockState state) {
		if (state == null || !state.hasProperty(LeavesBlock.PERSISTENT)) {
			return false;
		}
		Boolean persistent = state.getValue(LeavesBlock.PERSISTENT);
		return persistent == null || !persistent;
	}

	static int getFoliageAmount(BlockState state) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return 1;
		}
		Integer value = state.getValue(amountProperty);
		return value == null ? 1 : Math.max(1, value);
	}

	static int getFoliageMaxAmount(BlockState state) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
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

	static IntegerProperty findFoliageAmountProperty(BlockState state) {
		if (state == null) {
			return null;
		}
		IntegerProperty fallback = null;
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (!(property instanceof IntegerProperty integerProperty)) {
				continue;
			}
			if ("flower_amount".equals(integerProperty.getName())) {
				return integerProperty;
			}
			if (fallback == null && NaturalGrowthConfigManager.propertyNameLooksLikeAmount(integerProperty.getName())) {
				fallback = integerProperty;
			}
		}
		return fallback;
	}

	static BlockState setFoliageAmount(BlockState state, int targetAmount) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
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

	private static boolean tryPlaceTallGrass(ServerLevel world, BlockPos lowerPos, Block tallGrassBlock) {
		if (world == null || lowerPos == null || tallGrassBlock == null) {
			return false;
		}
		BlockPos upperPos = lowerPos.above();
		if (!world.getBlockState(lowerPos).isAir()) {
			return false;
		}

		BlockState lower = tallGrassBlock.defaultBlockState();
		if (!lower.hasProperty(DoublePlantBlock.HALF)) {
			if (!lower.canSurvive(world, lowerPos)) {
				return false;
			}
			world.setBlockAndUpdate(lowerPos, lower);
			return true;
		}

		if (!world.getBlockState(upperPos).isAir()) {
			return false;
		}

		if (lower.hasProperty(DoublePlantBlock.HALF)) {
			lower = lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
		}
		BlockState upper = tallGrassBlock.defaultBlockState();
		if (upper.hasProperty(DoublePlantBlock.HALF)) {
			upper = upper.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
		}

		world.setBlockAndUpdate(lowerPos, lower);
		world.setBlockAndUpdate(upperPos, upper);
		return true;
	}

	private static boolean tryPlaceWeightedFoliageTarget(ServerLevel world, BlockPos growPos, List<WeightedFoliagePlacement> placements) {
		if (world == null || growPos == null || placements == null || placements.isEmpty()) {
			return false;
		}

		List<WeightedFoliagePlacement> remaining = new ArrayList<>();
		for (WeightedFoliagePlacement placement : placements) {
			if (placement != null && placement.block() != null && placement.weight() > 0) {
				remaining.add(placement);
			}
		}
		if (remaining.isEmpty()) {
			return false;
		}

		while (!remaining.isEmpty()) {
			WeightedFoliagePlacement selected = pickWeightedFoliagePlacement(remaining);
			if (selected == null) {
				return false;
			}

			boolean placed = selected.tall()
				? tryPlaceTallGrass(world, growPos, selected.block())
				: tryPlaceSingleBlock(world, growPos, selected.block());
			if (placed) {
				return true;
			}
			remaining.remove(selected);
		}
		return false;
	}

	private static boolean tryPlaceSingleBlock(ServerLevel world, BlockPos pos, Block block) {
		if (world == null || pos == null || block == null) {
			return false;
		}
		BlockState next = block.defaultBlockState();
		if (!next.canSurvive(world, pos)) {
			return false;
		}
		world.setBlockAndUpdate(pos, next);
		return true;
	}

	private static WeightedFoliagePlacement pickWeightedFoliagePlacement(List<WeightedFoliagePlacement> placements) {
		if (placements == null || placements.isEmpty()) {
			return null;
		}

		int totalWeight = 0;
		for (WeightedFoliagePlacement placement : placements) {
			if (placement == null) {
				continue;
			}
			totalWeight += Math.max(0, placement.weight());
		}
		if (totalWeight <= 0) {
			return null;
		}

		int roll = ThreadLocalRandom.current().nextInt(totalWeight);
		int running = 0;
		for (WeightedFoliagePlacement placement : placements) {
			if (placement == null) {
				continue;
			}
			running += Math.max(0, placement.weight());
			if (roll < running) {
				return placement;
			}
		}
		return placements.get(placements.size() - 1);
	}

	private static List<WeightedFoliagePlacement> buildGrassFoliagePlacements() {
		NaturalGrowthConfigManager.Settings growthSettings = naturalGrowthSettings == null
			? NaturalGrowthConfigManager.defaults()
			: naturalGrowthSettings;
		NaturalGrowthConfigManager.FoliageGrowthSettings foliageGrowth = growthSettings.foliageGrowth();
		List<WeightedFoliagePlacement> placements = new ArrayList<>(3);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_SHORT_GRASS), foliageGrowth == null ? 0 : foliageGrowth.shortGrass().weight(), false);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_TALL_GRASS), foliageGrowth == null ? 0 : foliageGrowth.tallGrass().weight(), true);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_BUSH), foliageGrowth == null ? 0 : foliageGrowth.bush().weight(), false);
		return placements;
	}

	private static List<WeightedFoliagePlacement> buildDesertFoliagePlacements() {
		NaturalGrowthConfigManager.Settings growthSettings = naturalGrowthSettings == null
			? NaturalGrowthConfigManager.defaults()
			: naturalGrowthSettings;
		NaturalGrowthConfigManager.FoliageGrowthSettings desertGrowth = growthSettings.desertFoliageGrowth();
		List<WeightedFoliagePlacement> placements = new ArrayList<>(3);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_SHORT_DRY_GRASS), desertGrowth == null ? 0 : desertGrowth.shortGrass().weight(), false);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_TALL_DRY_GRASS), desertGrowth == null ? 0 : desertGrowth.tallGrass().weight(), true);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_DEAD_BUSH), desertGrowth == null ? 0 : desertGrowth.bush().weight(), false);
		return placements;
	}

	private static void addFoliagePlacement(List<WeightedFoliagePlacement> placements, Block block, int weight, boolean tall) {
		if (placements == null || block == null || weight <= 0) {
			return;
		}
		placements.add(new WeightedFoliagePlacement(block, weight, tall));
	}

	private record WeightedFoliagePlacement(Block block, int weight, boolean tall) {
	}

	static Block resolveWetGroundReplacementBlock(ServerLevel world, BlockPos pos, BlockState state, String preferredRuleId) {
		NaturalErosionConfigManager.NamedErosionRule rule = resolveErosionRule(world, pos, state, preferredRuleId);
		if (rule == null || rule.rule() == null) {
			return null;
		}
		return resolveErosionTargetBlock(rule.ruleId());
	}

	static NaturalErosionConfigManager.NamedErosionRule resolveErosionRule(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		String preferredRuleId
	) {
		if (world == null || pos == null || state == null || !isNaturalErosionEnabled()) {
			return null;
		}
		String blockId = EcosystemConfigManager.blockId(state.getBlock());
		if (blockId.isBlank()) {
			return null;
		}

		NaturalErosionConfigManager.NamedErosionRule magmaRule = findErosionRuleById(EROSION_RULE_ID_LAVA_MAGMA);
		if (magmaRule != null && EcosystemNaturalErosionManager.isLavaErosionEnabled() && matchesLavaMagmaRule(world, pos, blockId, magmaRule.ruleId(), magmaRule.rule())) {
			return magmaRule;
		}

		if (preferredRuleId != null && !preferredRuleId.isBlank()) {
			for (NaturalErosionConfigManager.NamedErosionRule candidate : cachedErosionRules) {
				if (!preferredRuleId.equals(candidate.ruleId())) {
					continue;
				}
				if (!isErosionRuleEnabled(candidate.ruleId())) {
					break;
				}
				if (EROSION_RULE_ID_LAVA_MAGMA.equals(candidate.ruleId())) {
					break;
				}
				if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule())) {
					return candidate;
				}
				break;
			}
		}

		for (NaturalErosionConfigManager.NamedErosionRule candidate : cachedErosionRules) {
			if (!isErosionRuleEnabled(candidate.ruleId())) {
				continue;
			}
			if (EROSION_RULE_ID_LAVA_MAGMA.equals(candidate.ruleId())) {
				continue;
			}
			if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule())) {
				return candidate;
			}
		}
		return null;
	}

	private static NaturalErosionConfigManager.NamedErosionRule findErosionRuleById(String ruleId) {
		if (ruleId == null || ruleId.isBlank()) {
			return null;
		}
		for (NaturalErosionConfigManager.NamedErosionRule candidate : cachedErosionRules) {
			if (candidate == null || candidate.rule() == null) {
				continue;
			}
			if (ruleId.equals(candidate.ruleId())) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean matchesErosionRule(
		ServerLevel world,
		BlockPos pos,
		String sourceBlockId,
		String ruleId,
		NaturalErosionConfigManager.ErosionRuleSettings rule
	) {
		if (world == null || pos == null || sourceBlockId == null || sourceBlockId.isBlank() || rule == null || !rule.enabled()) {
			return false;
		}
		if (!rule.sourceBlocks().contains(sourceBlockId)) {
			return false;
		}
		Block targetBlock = resolveErosionTargetBlock(ruleId);
		if (targetBlock == null) {
			return false;
		}

		List<String> eligibleBiomes = rule.eligibleBiomes();
		if (eligibleBiomes == null || eligibleBiomes.isEmpty()) {
			return true;
		}

		Holder<Biome> biomeHolder = world.getBiome(pos);
		for (String biomeEntry : eligibleBiomes) {
			String normalized = biomeEntry == null ? "" : biomeEntry.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (normalized.startsWith(BIOME_TAG_PREFIX)) {
				normalized = normalized.substring(1);
			}
			Identifier id = Identifier.tryParse(normalized);
			if (id == null) {
				continue;
			}
			if (biomeHolder.is(ResourceKey.create(Registries.BIOME, id))) {
				return true;
			}
			if (biomeHolder.is(TagKey.create(Registries.BIOME, id))) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesLavaMagmaRule(
		ServerLevel world,
		BlockPos pos,
		String sourceBlockId,
		String ruleId,
		NaturalErosionConfigManager.ErosionRuleSettings rule
	) {
		return EcosystemNaturalErosionManager.isLavaErosionEnabled()
			&& matchesErosionRule(world, pos, sourceBlockId, ruleId, rule)
			&& EcosystemNaturalErosionManager.isAdjacentToLava(world, pos, naturalErosionSettings.lavaErosionRadius());
	}

	private static Block resolveErosionTargetBlock(String ruleId) {
		String normalizedRuleId = EcosystemConfigManager.normalize(ruleId);
		String targetBlockId = switch (normalizedRuleId) {
			case NaturalErosionConfigManager.FIELD_MUD -> "minecraft:mud";
			case NaturalErosionConfigManager.FIELD_RED_SAND -> "minecraft:red_sand";
			case NaturalErosionConfigManager.FIELD_SAND -> "minecraft:sand";
			case NaturalErosionConfigManager.FIELD_MAGMA_BLOCK -> "minecraft:magma_block";
			default -> "";
		};
		return EcosystemConfigManager.resolveBlock(targetBlockId);
	}

	static Block resolveSurfaceDirtGrowthBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return Blocks.GRASS_BLOCK;
		}

		NaturalGrowthConfigManager.Settings growthSettings = naturalGrowthSettings == null
			? NaturalGrowthConfigManager.defaults()
			: naturalGrowthSettings;
		NaturalGrowthConfigManager.BlockGrowthSettings blockGrowth = growthSettings.blockGrowth();
		NaturalGrowthConfigManager.DirtGrowthSettings dirtGrowth = blockGrowth == null ? null : blockGrowth.dirt();
		List<String> targetBlocks = dirtGrowth == null ? List.of() : dirtGrowth.targetBlocks();
		for (String targetBlockId : targetBlocks) {
			Block targetBlock = EcosystemConfigManager.resolveBlock(targetBlockId);
			if (targetBlock == null) {
				continue;
			}
			BlockState placed = targetBlock.defaultBlockState();
			if (placed.canSurvive(world, pos)) {
				return targetBlock;
			}
		}
		return Blocks.GRASS_BLOCK;
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

		JsonElement treeCandidatesElement = source.get(FIELD_TREE_CANDIDATES);
		if (treeCandidatesElement != null && treeCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeCandidatesElement.getAsJsonArray()) {
				TreeCandidateState candidate = TreeCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				EcosystemNaturalGrowthManager.putTreeCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement cactusCandidatesElement = source.get(FIELD_CACTUS_CANDIDATES);
		if (cactusCandidatesElement != null && cactusCandidatesElement.isJsonArray()) {
			for (JsonElement element : cactusCandidatesElement.getAsJsonArray()) {
				CactusCandidateState candidate = CactusCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				EcosystemNaturalGrowthManager.putCactusCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement grassCandidatesElement = source.get(FIELD_GRASS_CANDIDATES);
		if (grassCandidatesElement != null && grassCandidatesElement.isJsonArray()) {
			for (JsonElement element : grassCandidatesElement.getAsJsonArray()) {
				GrassCandidateState candidate = GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				EcosystemNaturalGrowthManager.putGrassCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement desertFoliageGrowthCandidatesElement = source.get(FIELD_DESERT_FOLIAGE_GROWTH_CANDIDATES);
		if (desertFoliageGrowthCandidatesElement != null && desertFoliageGrowthCandidatesElement.isJsonArray()) {
			for (JsonElement element : desertFoliageGrowthCandidatesElement.getAsJsonArray()) {
				GrassCandidateState candidate = GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				EcosystemNaturalGrowthManager.putDesertFoliageGrowthCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement foliageCandidatesElement = source.get(FIELD_FOLIAGE_CANDIDATES);
		if (foliageCandidatesElement != null && foliageCandidatesElement.isJsonArray()) {
			for (JsonElement element : foliageCandidatesElement.getAsJsonArray()) {
				FoliageCandidateState candidate = FoliageCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				EcosystemNaturalGrowthManager.putFoliageCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement treeDecayCandidatesElement = source.get(FIELD_TREE_DECAY_CANDIDATES);
		if (treeDecayCandidatesElement != null && treeDecayCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeDecayCandidatesElement.getAsJsonArray()) {
				TreeDecayCandidateState candidate = TreeDecayCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				EcosystemNaturalDecayManager.putTreeDecayCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		PERSISTED_CHUNK_KEYS.add(chunkKey);
		return chunkKey;
	}

	private static JsonObject createChunkPersistedData(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}

		JsonFormatBuilder.ArrayBuilder dirtBlocks = JsonFormatBuilder.array();
		Set<String> dirtKeys = dirtKeysByChunk.get(chunkKey);
		if (dirtKeys != null) {
			for (String key : dirtKeys) {
				DirtState dirt = dirtBlocksByKey.get(key);
				if (dirt != null) {
					dirtBlocks.add(dirt.toJson());
				}
			}
		}

		JsonFormatBuilder.ArrayBuilder treeCandidates = JsonFormatBuilder.array();
		TreeCandidateState treeCandidate = EcosystemNaturalGrowthManager.getTreeCandidate(chunkKey);
		if (treeCandidate != null) {
			treeCandidates.add(treeCandidate.toJson());
		}

		JsonFormatBuilder.ArrayBuilder cactusCandidates = JsonFormatBuilder.array();
		CactusCandidateState cactusCandidate = EcosystemNaturalGrowthManager.getCactusCandidate(chunkKey);
		if (cactusCandidate != null) {
			cactusCandidates.add(cactusCandidate.toJson());
		}

		JsonFormatBuilder.ArrayBuilder grassCandidates = JsonFormatBuilder.array();
		List<GrassCandidateState> grassCandidateList = EcosystemNaturalGrowthManager.getGrassCandidates(chunkKey);
		if (grassCandidateList != null) {
			for (GrassCandidateState candidate : grassCandidateList) {
				if (candidate != null) {
					grassCandidates.add(candidate.toJson());
				}
			}
		}

		JsonFormatBuilder.ArrayBuilder desertFoliageGrowthCandidates = JsonFormatBuilder.array();
		List<GrassCandidateState> desertFoliageGrowthCandidateList = EcosystemNaturalGrowthManager.getDesertFoliageGrowthCandidates(chunkKey);
		if (desertFoliageGrowthCandidateList != null) {
			for (GrassCandidateState candidate : desertFoliageGrowthCandidateList) {
				if (candidate != null) {
					desertFoliageGrowthCandidates.add(candidate.toJson());
				}
			}
		}

		JsonFormatBuilder.ArrayBuilder foliageCandidates = JsonFormatBuilder.array();
		List<FoliageCandidateState> foliageCandidateList = EcosystemNaturalGrowthManager.getFoliageCandidates(chunkKey);
		if (foliageCandidateList != null) {
			for (FoliageCandidateState candidate : foliageCandidateList) {
				if (candidate != null) {
					foliageCandidates.add(candidate.toJson());
				}
			}
		}

		JsonFormatBuilder.ArrayBuilder treeDecayCandidates = JsonFormatBuilder.array();
		List<TreeDecayCandidateState> treeDecayCandidateList = EcosystemNaturalDecayManager.getTreeDecayCandidates(chunkKey);
		if (treeDecayCandidateList != null) {
			for (TreeDecayCandidateState candidate : treeDecayCandidateList) {
				if (candidate != null) {
					treeDecayCandidates.add(candidate.toJson());
				}
			}
		}

		boolean hasData = (dirtKeys != null && !dirtKeys.isEmpty())
			|| treeCandidate != null
			|| cactusCandidate != null
			|| (grassCandidateList != null && !grassCandidateList.isEmpty())
			|| (desertFoliageGrowthCandidateList != null && !desertFoliageGrowthCandidateList.isEmpty())
			|| (foliageCandidateList != null && !foliageCandidateList.isEmpty())
			|| (treeDecayCandidateList != null && !treeDecayCandidateList.isEmpty());
		if (!hasData) {
			return null;
		}

		return JsonFormatBuilder.object()
			.put(FIELD_LEVEL_ID, chunkKey.levelId())
			.put(FIELD_CHUNK_X, chunkKey.chunkX())
			.put(FIELD_CHUNK_Z, chunkKey.chunkZ())
			.put(FIELD_GROUND_BLOCKS, dirtBlocks.build())
			.put(FIELD_TREE_CANDIDATES, treeCandidates.build())
			.put(FIELD_CACTUS_CANDIDATES, cactusCandidates.build())
			.put(FIELD_GRASS_CANDIDATES, grassCandidates.build())
			.put(FIELD_DESERT_FOLIAGE_GROWTH_CANDIDATES, desertFoliageGrowthCandidates.build())
			.put(FIELD_FOLIAGE_CANDIDATES, foliageCandidates.build())
			.put(FIELD_TREE_DECAY_CANDIDATES, treeDecayCandidates.build())
			.build();
	}

	private static Set<ChunkRefKey> collectChunkKeysForPersistence() {
		return collectCurrentChunkKeys();
	}

	private static Set<ChunkRefKey> collectChunkKeysForIndex() {
		return collectCurrentChunkKeys();
	}

	private static Set<ChunkRefKey> collectCurrentChunkKeys() {
		Set<ChunkRefKey> keys = new LinkedHashSet<>();
		keys.addAll(dirtKeysByChunk.keySet());
		keys.addAll(EcosystemNaturalGrowthManager.collectTrackedChunkKeys());
		keys.addAll(EcosystemNaturalDecayManager.collectTrackedChunkKeys());
		return keys;
	}

	private static void writeEcosystemIndex(MinecraftServer server, int chunkFileCount, Set<ChunkRefKey> chunkKeys) {
		if (server == null) {
			return;
		}

		Path indexFile = resolveEcosystemIndexFile(server);
		JsonObject indexData = JsonFormatBuilder.object()
			.put(FIELD_CHUNK_FOLDER, CHUNK_DATA_FOLDER_NAME)
			.put(FIELD_CHUNK_COUNT, chunkFileCount)
			.put(FIELD_CHUNKS, buildChunkDescriptorArray(chunkKeys))
			.build();
		try {
			JsonStaticSystem.writeManagedDocument(indexFile, indexData, new JsonObject());
		} catch (IOException exception) {
			LOGGER.error("Failed to write ecosystem index file at {}.", indexFile, exception);
		}
	}

	private static JsonElement buildChunkDescriptorArray(Set<ChunkRefKey> chunkKeys) {
		JsonFormatBuilder.ArrayBuilder chunks = JsonFormatBuilder.array();
		if (chunkKeys != null) {
			for (ChunkRefKey chunkKey : chunkKeys) {
				if (chunkKey == null) {
					continue;
				}
				chunks.object(chunk -> chunk
					.put(FIELD_LEVEL_ID, chunkKey.levelId())
					.put(FIELD_CHUNK_X, chunkKey.chunkX())
					.put(FIELD_CHUNK_Z, chunkKey.chunkZ())
					.put(FIELD_DATA_FILE, chunkPersistedDataRelativePath(chunkKey)));
			}
		}
		return chunks.build();
	}

	private static void writeChunkPersistedData(MinecraftServer server, ChunkRefKey chunkKey, JsonObject data) {
		if (server == null || chunkKey == null || data == null) {
			return;
		}

		Path file = resolveChunkPersistedDataFile(server, chunkKey);
		try {
			JsonStaticSystem.writeManagedDocument(file, data, new JsonObject());
		} catch (IOException exception) {
			LOGGER.error("Failed to write ecosystem chunk data at {}.", file, exception);
		}
	}

	private static void deleteChunkPersistedData(MinecraftServer server, ChunkRefKey chunkKey) {
		if (server == null || chunkKey == null) {
			return;
		}

		Path file = resolveChunkPersistedDataFile(server, chunkKey);
		try {
			Files.deleteIfExists(file);
		} catch (IOException exception) {
			LOGGER.error("Failed to delete ecosystem chunk data at {}.", file, exception);
		}
	}

	private static Path resolveEcosystemIndexFile(MinecraftServer server) {
		return resolveEcosystemRootDirectory(server).resolve(DATA_FILE_NAME + ".json");
	}

	private static Path resolveEcosystemRootDirectory(MinecraftServer server) {
		return JsonManagerSystem.getOrCreateWorldSystemDirectory(server, DATA_FOLDER_NAME);
	}

	private static Path resolveChunkDataRootDirectory(MinecraftServer server) {
		return resolveEcosystemRootDirectory(server).resolve(CHUNK_DATA_FOLDER_NAME);
	}

	private static Path resolveChunkPersistedDataPath(MinecraftServer server, ChunkRefKey chunkKey) {
		Path chunkRoot = resolveChunkDataRootDirectory(server);
		Path levelDirectory = chunkRoot.resolve(normalizePathPart(chunkKey.levelId(), "level id"));
		return levelDirectory.resolve(chunkPersistedDataFileName(chunkKey));
	}

	private static Path resolveChunkPersistedDataFile(MinecraftServer server, ChunkRefKey chunkKey) {
		Path file = resolveChunkPersistedDataPath(server, chunkKey);
		try {
			Files.createDirectories(file.getParent());
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create ecosystem chunk directory: " + file.getParent(), exception);
		}
		return file;
	}

	private static String chunkPersistedDataRelativePath(ChunkRefKey chunkKey) {
		return normalizePathPart(chunkKey.levelId(), "level id") + "/" + chunkPersistedDataFileName(chunkKey);
	}

	private static String chunkPersistedDataFileName(ChunkRefKey chunkKey) {
		return "chunk_" + chunkKey.chunkX() + "_" + chunkKey.chunkZ() + ".json";
	}

	private static String normalizePathPart(String value, String label) {
		String normalized = value == null ? "" : value.trim();
		StringBuilder builder = new StringBuilder(normalized.length() + 8);
		char previous = 0;
		for (int index = 0; index < normalized.length(); index++) {
			char current = normalized.charAt(index);
			if (Character.isUpperCase(current) && index > 0 && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
				builder.append('-');
			}
			builder.append(Character.toLowerCase(current));
			previous = current;
		}
		normalized = builder.toString();
		normalized = normalized.replace(' ', '-').replace('_', '-').replace('\\', '-').replace('/', '-').replace(':', '-');
		while (normalized.contains("--")) {
			normalized = normalized.replace("--", "-");
		}
		while (normalized.startsWith("-")) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith("-")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank.");
		}
		return normalized;
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
			return JsonFormatBuilder.object()
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

		private static CactusCandidateState fromJson(JsonElement element) {
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
			return JsonFormatBuilder.object()
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

		private static GrassCandidateState fromJson(JsonElement element) {
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
			return JsonFormatBuilder.object()
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

		private static FoliageCandidateState fromJson(JsonElement element) {
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
				return JsonFormatBuilder.object()
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

		private static TreeDecayCandidateState fromJson(JsonElement element) {
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
			return JsonFormatBuilder.object()
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
			return JsonFormatBuilder.object()
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
				MODE_SURFACE_DIRT.equals(mode) ? 3.0d * MadokuTime.MINECRAFT_TICKS_PER_CYCLE : defaultErosionGrowthTicks()
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
