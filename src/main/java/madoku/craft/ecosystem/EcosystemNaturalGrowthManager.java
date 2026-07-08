package madoku.craft.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.season.MadokuSeason;
import madoku.craft.api.time.MadokuTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.tags.TagKey;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class EcosystemNaturalGrowthManager {
	public static final String SCHEDULER_OWNER_ID = "ecosystem_growth_process_gameplay";
	public static final String TASK_TYPE = "ecosystem_growth_process_gameplay_tick";
	public static final String CHUNK_PROCESSOR_ID = "ecosystem_natural_growth";
	private static final String TREE_TYPE_OAK = "oak";
	private static final String TREE_TYPE_SPRUCE = "spruce";
	private static final String TREE_TYPE_BIRCH = "birch";
	private static final String TREE_TYPE_JUNGLE = "jungle";
	private static final String TREE_TYPE_MANGROVE = "mangrove";
	private static final String TREE_TYPE_ACACIA = "acacia";
	private static final String TREE_TYPE_DARK_OAK = "dark_oak";
	private static final String TREE_TYPE_PALE_OAK = "pale_oak";
	private static final String TREE_TYPE_CHERRY = "cherry";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalGrowthManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "natural-growth";
	private static final String DEBUG_MAIN_SYSTEM = "ecosystem";
	private static final String DEBUG_SUB_SYSTEM = "ecosystem-natural-growth-manager";
	private static final long MIN_INTERVAL_TICKS = 1L;
	private static final long MAX_INTERVAL_TICKS = 20L;
	private static final int MAX_GRASS_CANDIDATES_PER_CHUNK = 4;
	private static final int MAX_DESERT_FOLIAGE_GROWTH_CANDIDATES_PER_CHUNK = 4;
	private static final int MAX_FOLIAGE_CANDIDATES_PER_CHUNK = 4;
	private static final String BLOCK_ID_SHORT_GRASS = "minecraft:short_grass";
	private static final String BLOCK_ID_TALL_GRASS = "minecraft:tall_grass";
	private static final String BLOCK_ID_BUSH = "minecraft:bush";
	private static final String BLOCK_ID_DEAD_BUSH = "minecraft:dead_bush";
	private static final String BLOCK_ID_SHORT_DRY_GRASS = "minecraft:short_dry_grass";
	private static final String BLOCK_ID_TALL_DRY_GRASS = "minecraft:tall_dry_grass";

	private static volatile NaturalGrowthConfigManager.Settings settings = NaturalGrowthConfigManager.defaults();
	private static volatile String schedulerId = "";
	private static volatile boolean taskScheduled = false;
	static final Map<MadokuEcosystemManager.ChunkRefKey, MadokuEcosystemManager.TreeCandidateState> treeCandidatesByChunk = new LinkedHashMap<>();
	static final Map<MadokuEcosystemManager.ChunkRefKey, MadokuEcosystemManager.CactusCandidateState> cactusCandidatesByChunk = new LinkedHashMap<>();
	static final Map<MadokuEcosystemManager.ChunkRefKey, List<MadokuEcosystemManager.GrassCandidateState>> grassCandidatesByChunk = new LinkedHashMap<>();
	static final Map<MadokuEcosystemManager.ChunkRefKey, List<MadokuEcosystemManager.GrassCandidateState>> desertFoliageGrowthCandidatesByChunk = new LinkedHashMap<>();
	static final Map<MadokuEcosystemManager.ChunkRefKey, List<MadokuEcosystemManager.FoliageCandidateState>> foliageCandidatesByChunk = new LinkedHashMap<>();

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
			processChunk(level, chunkX, chunkZ, MadokuTimeManager.getCurrentAbsoluteDayTime(level));
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
		clearTrackedCandidateState();
		MadokuChunkManager.resetChunkProcessor(CHUNK_PROCESSOR_ID);
		emitGrowthDebug("ecosystem.natural_growth.lifecycle", builder -> builder
			.subject("reset")
			.field("scheduler-id", previousSchedulerId)
			.field("task-scheduled", false));
	}

	static void clearTrackedCandidateState() {
		treeCandidatesByChunk.clear();
		cactusCandidatesByChunk.clear();
		grassCandidatesByChunk.clear();
		desertFoliageGrowthCandidatesByChunk.clear();
		foliageCandidatesByChunk.clear();
	}

	static Set<MadokuEcosystemManager.ChunkRefKey> collectTrackedChunkKeys() {
		Set<MadokuEcosystemManager.ChunkRefKey> keys = new LinkedHashSet<>();
		keys.addAll(treeCandidatesByChunk.keySet());
		keys.addAll(cactusCandidatesByChunk.keySet());
		keys.addAll(grassCandidatesByChunk.keySet());
		keys.addAll(desertFoliageGrowthCandidatesByChunk.keySet());
		keys.addAll(foliageCandidatesByChunk.keySet());
		return keys;
	}

	static MadokuEcosystemManager.TreeCandidateState getTreeCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? null : treeCandidatesByChunk.get(chunkKey);
	}

	static MadokuEcosystemManager.CactusCandidateState getCactusCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? null : cactusCandidatesByChunk.get(chunkKey);
	}

	static List<MadokuEcosystemManager.GrassCandidateState> getGrassCandidates(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : grassCandidatesByChunk.getOrDefault(chunkKey, List.of());
	}

	static List<MadokuEcosystemManager.GrassCandidateState> getDesertFoliageGrowthCandidates(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : desertFoliageGrowthCandidatesByChunk.getOrDefault(chunkKey, List.of());
	}

	static List<MadokuEcosystemManager.FoliageCandidateState> getFoliageCandidates(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : foliageCandidatesByChunk.getOrDefault(chunkKey, List.of());
	}

	static MadokuEcosystemManager.TreeCandidateState putTreeCandidate(
		MadokuEcosystemManager.ChunkRefKey chunkKey,
		MadokuEcosystemManager.TreeCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return null;
		}
		MadokuEcosystemManager.TreeCandidateState previous = treeCandidatesByChunk.put(chunkKey, candidate);
		syncChunkProcessorTracking(chunkKey);
		return previous;
	}

	static MadokuEcosystemManager.CactusCandidateState putCactusCandidate(
		MadokuEcosystemManager.ChunkRefKey chunkKey,
		MadokuEcosystemManager.CactusCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return null;
		}
		MadokuEcosystemManager.CactusCandidateState previous = cactusCandidatesByChunk.put(chunkKey, candidate);
		syncChunkProcessorTracking(chunkKey);
		return previous;
	}

	static void putGrassCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey, MadokuEcosystemManager.GrassCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		List<MadokuEcosystemManager.GrassCandidateState> candidates = grassCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (MadokuEcosystemManager.GrassCandidateState existing : candidates) {
			if (existing != null && existing.groundPos == candidate.groundPos) {
				return;
			}
		}
		if (candidates.size() >= MAX_GRASS_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	static void putDesertFoliageGrowthCandidate(
		MadokuEcosystemManager.ChunkRefKey chunkKey,
		MadokuEcosystemManager.GrassCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		List<MadokuEcosystemManager.GrassCandidateState> candidates = desertFoliageGrowthCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (MadokuEcosystemManager.GrassCandidateState existing : candidates) {
			if (existing != null && existing.groundPos == candidate.groundPos) {
				return;
			}
		}
		if (candidates.size() >= MAX_DESERT_FOLIAGE_GROWTH_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	static void putFoliageCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey, MadokuEcosystemManager.FoliageCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		List<MadokuEcosystemManager.FoliageCandidateState> candidates = foliageCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (MadokuEcosystemManager.FoliageCandidateState existing : candidates) {
			if (existing != null
				&& existing.groundPos == candidate.groundPos
				&& NaturalGrowthConfigManager.normalizeFoliageType(existing.foliageType).equals(NaturalGrowthConfigManager.normalizeFoliageType(candidate.foliageType))) {
				return;
			}
		}
		if (candidates.size() >= MAX_FOLIAGE_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	static MadokuEcosystemManager.TreeCandidateState removeTreeCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		MadokuEcosystemManager.TreeCandidateState removed = treeCandidatesByChunk.remove(chunkKey);
		if (removed != null) {
			syncChunkProcessorTracking(chunkKey);
		}
		return removed;
	}

	static MadokuEcosystemManager.CactusCandidateState removeCactusCandidate(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		MadokuEcosystemManager.CactusCandidateState removed = cactusCandidatesByChunk.remove(chunkKey);
		if (removed != null) {
			syncChunkProcessorTracking(chunkKey);
		}
		return removed;
	}

	static void syncChunkProcessorTracking(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		boolean tracked = treeCandidatesByChunk.containsKey(chunkKey)
			|| cactusCandidatesByChunk.containsKey(chunkKey)
			|| !grassCandidatesByChunk.getOrDefault(chunkKey, List.of()).isEmpty()
			|| !desertFoliageGrowthCandidatesByChunk.getOrDefault(chunkKey, List.of()).isEmpty()
			|| !foliageCandidatesByChunk.getOrDefault(chunkKey, List.of()).isEmpty()
			|| MadokuEcosystemManager.chunkHasDirtMode(chunkKey, "surface_dirt");
		if (tracked) {
			MadokuChunkManager.trackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		} else {
			MadokuChunkManager.untrackChunkForProcessor(CHUNK_PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}
		emitGrowthDebug("ecosystem.tracking", builder -> builder
			.subject("sync-growth-chunk-tracking")
			.field("level-id", chunkKey.levelId())
			.field("chunk-x", chunkKey.chunkX())
			.field("chunk-z", chunkKey.chunkZ())
			.field("tracked", tracked));
	}

	static JsonObject createChunkPersistedData(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}

		JsonFormatBuilder.ArrayBuilder treeCandidates = JsonFormatBuilder.array();
		MadokuEcosystemManager.TreeCandidateState treeCandidate = getTreeCandidate(chunkKey);
		if (treeCandidate != null) {
			treeCandidates.add(treeCandidate.toJson());
		}

		JsonFormatBuilder.ArrayBuilder cactusCandidates = JsonFormatBuilder.array();
		MadokuEcosystemManager.CactusCandidateState cactusCandidate = getCactusCandidate(chunkKey);
		if (cactusCandidate != null) {
			cactusCandidates.add(cactusCandidate.toJson());
		}

		JsonFormatBuilder.ArrayBuilder grassCandidates = JsonFormatBuilder.array();
		List<MadokuEcosystemManager.GrassCandidateState> grassCandidateList = getGrassCandidates(chunkKey);
		if (grassCandidateList != null) {
			for (MadokuEcosystemManager.GrassCandidateState candidate : grassCandidateList) {
				if (candidate != null) {
					grassCandidates.add(candidate.toJson());
				}
			}
		}

		JsonFormatBuilder.ArrayBuilder desertFoliageGrowthCandidates = JsonFormatBuilder.array();
		List<MadokuEcosystemManager.GrassCandidateState> desertFoliageGrowthCandidateList = getDesertFoliageGrowthCandidates(chunkKey);
		if (desertFoliageGrowthCandidateList != null) {
			for (MadokuEcosystemManager.GrassCandidateState candidate : desertFoliageGrowthCandidateList) {
				if (candidate != null) {
					desertFoliageGrowthCandidates.add(candidate.toJson());
				}
			}
		}

		JsonFormatBuilder.ArrayBuilder foliageCandidates = JsonFormatBuilder.array();
		List<MadokuEcosystemManager.FoliageCandidateState> foliageCandidateList = getFoliageCandidates(chunkKey);
		if (foliageCandidateList != null) {
			for (MadokuEcosystemManager.FoliageCandidateState candidate : foliageCandidateList) {
				if (candidate != null) {
					foliageCandidates.add(candidate.toJson());
				}
			}
		}

		boolean hasData = treeCandidate != null
			|| cactusCandidate != null
			|| (grassCandidateList != null && !grassCandidateList.isEmpty())
			|| (desertFoliageGrowthCandidateList != null && !desertFoliageGrowthCandidateList.isEmpty())
			|| (foliageCandidateList != null && !foliageCandidateList.isEmpty());
		if (!hasData) {
			return null;
		}

		return MadokuEcosystemManager.buildChunkPersistedData(builder -> builder
			.put("tree-candidates", treeCandidates.build())
			.put("cactus-candidates", cactusCandidates.build())
			.put("grass-candidates", grassCandidates.build())
			.put("desert-foliage-growth-candidates", desertFoliageGrowthCandidates.build())
			.put("foliage-candidates", foliageCandidates.build()));
	}

	static void applyPersistedData(JsonObject source) {
		if (source == null || source.isJsonNull()) {
			return;
		}

		JsonElement treeCandidatesElement = source.get("tree-candidates");
		if (treeCandidatesElement != null && treeCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeCandidatesElement.getAsJsonArray()) {
				MadokuEcosystemManager.TreeCandidateState candidate = MadokuEcosystemManager.TreeCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeCandidate(new MadokuEcosystemManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement cactusCandidatesElement = source.get("cactus-candidates");
		if (cactusCandidatesElement != null && cactusCandidatesElement.isJsonArray()) {
			for (JsonElement element : cactusCandidatesElement.getAsJsonArray()) {
				MadokuEcosystemManager.CactusCandidateState candidate = MadokuEcosystemManager.CactusCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putCactusCandidate(new MadokuEcosystemManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement grassCandidatesElement = source.get("grass-candidates");
		if (grassCandidatesElement != null && grassCandidatesElement.isJsonArray()) {
			for (JsonElement element : grassCandidatesElement.getAsJsonArray()) {
				MadokuEcosystemManager.GrassCandidateState candidate = MadokuEcosystemManager.GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putGrassCandidate(new MadokuEcosystemManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement desertFoliageGrowthCandidatesElement = source.get("desert-foliage-growth-candidates");
		if (desertFoliageGrowthCandidatesElement != null && desertFoliageGrowthCandidatesElement.isJsonArray()) {
			for (JsonElement element : desertFoliageGrowthCandidatesElement.getAsJsonArray()) {
				MadokuEcosystemManager.GrassCandidateState candidate = MadokuEcosystemManager.GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putDesertFoliageGrowthCandidate(new MadokuEcosystemManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement foliageCandidatesElement = source.get("foliage-candidates");
		if (foliageCandidatesElement != null && foliageCandidatesElement.isJsonArray()) {
			for (JsonElement element : foliageCandidatesElement.getAsJsonArray()) {
				MadokuEcosystemManager.FoliageCandidateState candidate = MadokuEcosystemManager.FoliageCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putFoliageCandidate(new MadokuEcosystemManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	static boolean isTreeGrowthEnabled(String treeType) {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		return isEnabled() && growthSettings.treeGrowth() != null && growthSettings.treeGrowth().isEnabled(treeType);
	}

	static boolean isVegetationGrowthEnabled(String foliageType) {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		return isEnabled() && growthSettings.vegetationGrowth() != null && growthSettings.vegetationGrowth().isEnabled(foliageType);
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
		Block foliageBlock = resolveFoliageBlock(normalizedFoliageType);
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
		if (world == null || groundPos == null || !MadokuEcosystemManager.isDesertFoliageGrowthEnabled()) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		if (!world.getBlockState(growPos).isAir()) {
			return false;
		}
		return placeSummerDesertTarget(world, growPos);
	}

	static boolean tryGrowCactusAtGround(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null || !MadokuEcosystemManager.isCactusGrowthEnabled()) {
			return false;
		}
		BlockState groundState = world.getBlockState(groundPos);
		if (!isValidCactusGroundCandidate(world, groundPos, groundState)) {
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
			if (fallback == null && propertyNameLooksLikeAmount(integerProperty.getName())) {
				fallback = integerProperty;
			}
		}
		return fallback;
	}

	private static boolean propertyNameLooksLikeAmount(String propertyName) {
		return propertyName != null && propertyName.toLowerCase().contains("amount");
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

	static Block resolveSurfaceDirtGrowthBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return Blocks.GRASS_BLOCK;
		}

		NaturalGrowthConfigManager.Settings growthSettings = settings;
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
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		NaturalGrowthConfigManager.FoliageGrowthSettings foliageGrowth = growthSettings.foliageGrowth();
		List<WeightedFoliagePlacement> placements = new ArrayList<>(3);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_SHORT_GRASS), foliageGrowth == null ? 0 : foliageGrowth.shortGrass().weight(), false);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_TALL_GRASS), foliageGrowth == null ? 0 : foliageGrowth.tallGrass().weight(), true);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_BUSH), foliageGrowth == null ? 0 : foliageGrowth.bush().weight(), false);
		return placements;
	}

	private static List<WeightedFoliagePlacement> buildDesertFoliagePlacements() {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
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

	private static boolean placeSummerDesertTarget(ServerLevel world, BlockPos growPos) {
		return tryPlaceWeightedFoliageTarget(world, growPos, buildDesertFoliagePlacements());
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

	static boolean isBlockGrowthEnabled() {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		return isEnabled() && growthSettings.blockGrowth() != null && growthSettings.blockGrowth().isEnabled();
	}

	record WeightedFoliagePlacement(Block block, int weight, boolean tall) {
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

	static void discoverTrackablesInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuChunkManager.ChunkDiscoverySnapshot snapshot,
		MadokuEcosystemManager.ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || snapshot == null || accumulator == null || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
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
				if (MadokuEcosystemManager.isNaturalErosionEnabled() && EcosystemNaturalErosionManager.isWetSeedCandidate(world, pos, state)) {
					continue;
				}
				MadokuEcosystemManager.trackDirtCandidateForMode(world, pos, state, "surface_dirt");
				if (isTrackableTreeGroundBlock(state)) {
					accumulator.treeGroundCandidates.add(pos.asLong());
				}
				if (isValidCactusGroundCandidate(world, pos, state)) {
					accumulator.cactusGroundCandidates.add(pos.asLong());
				}
				if (isValidGrassGroundCandidate(world, pos, state)) {
					accumulator.grassGroundCandidates.add(pos.asLong());
				}
				if (isValidDesertFoliageGrowthGroundCandidate(world, pos, state)) {
					accumulator.desertFoliageGrowthGroundCandidates.add(pos.asLong());
				}
				if (isValidFoliageGroundCandidate(world, pos, state, NaturalGrowthConfigManager.FIELD_WILDFLOWERS)) {
					accumulator.wildflowerGroundCandidates.add(pos.asLong());
				}
				if (isValidFoliageGroundCandidate(world, pos, state, NaturalGrowthConfigManager.FIELD_PINK_PETALS)) {
					accumulator.pinkPetalGroundCandidates.add(pos.asLong());
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
		if (world == null || accumulator == null || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
			return;
		}
		pickTreeCandidateForChunk(world, chunkX, chunkZ, accumulator.treeGroundCandidates);
		pickCactusCandidateForChunk(world, chunkX, chunkZ, accumulator.cactusGroundCandidates);
		pickGrassCandidateForChunk(world, chunkX, chunkZ, accumulator.grassGroundCandidates);
		pickDesertFoliageGrowthCandidateForChunk(world, chunkX, chunkZ, accumulator.desertFoliageGrowthGroundCandidates);
		pickFoliageCandidateForChunk(world, chunkX, chunkZ, NaturalGrowthConfigManager.FIELD_WILDFLOWERS, accumulator.wildflowerGroundCandidates);
		pickFoliageCandidateForChunk(world, chunkX, chunkZ, NaturalGrowthConfigManager.FIELD_PINK_PETALS, accumulator.pinkPetalGroundCandidates);
	}

	static boolean isTrackableTreeGroundBlock(BlockState state) {
		return state != null && MadokuEcosystemManager.TRACKABLE_TREE_GROUND_BLOCKS.contains(state.getBlock());
	}

	static boolean isSurfaceDirtCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isBlockGrowthEnabled() || state.getBlock() != Blocks.DIRT) {
			return false;
		}
		if (EcosystemNaturalErosionManager.isSubmerged(world, blockPos)) {
			return false;
		}
		BlockState aboveState = world.getBlockState(blockPos.above());
		return aboveState != null && aboveState.isAir();
	}

	static List<String> resolveTreeTypesForBiome(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null) {
			return List.of();
		}

		List<String> treeTypes = new ArrayList<>(2);
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(groundPos);
		if (isSpruceBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_SPRUCE)) {
			treeTypes.add(TREE_TYPE_SPRUCE);
		}
		if (isBirchBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_BIRCH)) {
			treeTypes.add(TREE_TYPE_BIRCH);
		}
		if (isJungleBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_JUNGLE)) {
			treeTypes.add(TREE_TYPE_JUNGLE);
		}
		if (isMangroveBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_MANGROVE)) {
			treeTypes.add(TREE_TYPE_MANGROVE);
		}
		if (isAcaciaBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_ACACIA)) {
			treeTypes.add(TREE_TYPE_ACACIA);
		}
		if (isDarkOakBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_DARK_OAK)) {
			treeTypes.add(TREE_TYPE_DARK_OAK);
		}
		if (isPaleOakBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_PALE_OAK)) {
			treeTypes.add(TREE_TYPE_PALE_OAK);
		}
		if (isCherryBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_CHERRY)) {
			treeTypes.add(TREE_TYPE_CHERRY);
		}
		if (isOakBiome(biomeHolder) && MadokuEcosystemManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_OAK)) {
			treeTypes.add(TREE_TYPE_OAK);
		}
		return treeTypes;
	}

	static double resolveSurfaceDirtRequiredGrowthTicks(ServerLevel world) {
		String seasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalGrowthSettings.dirtGrowthForSeason(seasonId);
		return randomDaysToTicks(range);
	}

	static double resolveTreeRequiredGrowthTicks(String treeType, String seasonId) {
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalGrowthSettings.treeGrowthForSeason(treeType, seasonId);
		return randomDaysToTicks(range);
	}

	static double resolveGrassRequiredGrowthTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalGrowthSettings.grassGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static double resolveDesertFoliageGrowthRequiredTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalGrowthSettings.desertFoliageGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static double resolveCactusRequiredGrowthTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalGrowthSettings.cactusGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static double resolveFoliageRequiredGrowthTicks(ServerLevel world, String foliageType, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = MadokuEcosystemManager.naturalGrowthSettings.foliageGrowthForSeason(foliageType, normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static Block resolveFoliageBlock(String foliageType) {
		if (NaturalGrowthConfigManager.FIELD_PINK_PETALS.equals(foliageType)) {
			return EcosystemConfigManager.resolveBlock("minecraft:pink_petals");
		}
		if (NaturalGrowthConfigManager.FIELD_WILDFLOWERS.equals(foliageType)) {
			return EcosystemConfigManager.resolveBlock("minecraft:wildflowers");
		}
		return null;
	}

	static boolean isFoliageBiome(ServerLevel world, BlockPos pos, String foliageType) {
		if (world == null || pos == null) {
			return false;
		}
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (isDesertOrBadlandsBiome(biomeHolder)) {
			return false;
		}
		if (NaturalGrowthConfigManager.FIELD_PINK_PETALS.equals(foliageType)) {
			return biomeHolder.is(Biomes.CHERRY_GROVE);
		}
		return biomeHolder.is(Biomes.MEADOW)
			|| biomeHolder.is(Biomes.BIRCH_FOREST)
			|| biomeHolder.is(Biomes.OLD_GROWTH_BIRCH_FOREST);
	}

	static boolean isDesertOrBadlandsBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		if (biomeHolder.is(BiomeTags.IS_BADLANDS)) {
			return true;
		}
		if (biomeHolder.is(Biomes.BADLANDS)
			|| biomeHolder.is(Biomes.ERODED_BADLANDS)
			|| biomeHolder.is(Biomes.WOODED_BADLANDS)) {
			return true;
		}
		Identifier badlandsTagId = Identifier.tryParse("minecraft:is_badlands");
		if (badlandsTagId != null && biomeHolder.is(TagKey.create(Registries.BIOME, badlandsTagId))) {
			return true;
		}
		Identifier desertTagId = Identifier.tryParse("minecraft:is_desert");
		if (desertTagId != null && biomeHolder.is(TagKey.create(Registries.BIOME, desertTagId))) {
			return true;
		}
		return biomeHolder.is(Biomes.DESERT);
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

	static boolean isValidTreeGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String treeType) {
		if (world == null || groundPos == null || groundState == null || treeType == null || treeType.isBlank() || !isTreeGrowthEnabled(treeType)) {
			return false;
		}
		if (!isTrackableTreeGroundBlock(groundState)) {
			return false;
		}
		if (EcosystemNaturalErosionManager.isSubmerged(world, groundPos)) {
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

	static boolean isValidGrassGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		if (world == null || groundPos == null || groundState == null || !MadokuEcosystemManager.isFoliageGrowthEnabled()) {
			return false;
		}
		if (groundState.getBlock() != Blocks.GRASS_BLOCK) {
			return false;
		}
		if (EcosystemNaturalErosionManager.isSubmerged(world, groundPos)) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = world.getBlockState(growPos);
		return growState != null && growState.isAir();
	}

	static boolean isValidDesertFoliageGrowthGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		if (world == null || groundPos == null || groundState == null || !MadokuEcosystemManager.isDesertFoliageGrowthEnabled()) {
			return false;
		}
		if (!MadokuEcosystemManager.DESERT_FOLIAGE_GROWTH_GROUND_BLOCKS.contains(groundState.getBlock())) {
			return false;
		}
		if (EcosystemNaturalErosionManager.isSubmerged(world, groundPos)) {
			return false;
		}
		if (!isDesertOrBadlandsBiome(world.getBiome(groundPos))) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = world.getBlockState(growPos);
		return growState != null && growState.isAir();
	}

	static boolean isValidCactusGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		if (world == null || groundPos == null || groundState == null || !MadokuEcosystemManager.isCactusGrowthEnabled()) {
			return false;
		}
		if (!MadokuEcosystemManager.CACTUS_GROWTH_GROUND_BLOCKS.contains(groundState.getBlock())) {
			return false;
		}
		if (EcosystemNaturalErosionManager.isSubmerged(world, groundPos)) {
			return false;
		}
		if (!isDesertOrBadlandsBiome(world.getBiome(groundPos))) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = world.getBlockState(growPos);
		if (growState == null || !growState.isAir()) {
			return false;
		}
		return Blocks.CACTUS.defaultBlockState().canSurvive(world, growPos);
	}

	static boolean isValidFoliageGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String foliageType) {
		if (world == null || groundPos == null || groundState == null || !isVegetationGrowthEnabled(foliageType)) {
			return false;
		}
		if (EcosystemNaturalErosionManager.isSubmerged(world, groundPos)) {
			return false;
		}
		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
		if (normalizedFoliageType.isBlank() || !isFoliageBiome(world, groundPos, normalizedFoliageType)) {
			return false;
		}

		BlockPos foliagePos = groundPos.above();
		BlockState foliageState = world.getBlockState(foliagePos);
		if (foliageState == null) {
			return false;
		}
		Block foliageBlock = resolveFoliageBlock(normalizedFoliageType);
		if (foliageBlock == null) {
			return false;
		}
		if (foliageState.isAir()) {
			if (groundState.getBlock() != Blocks.GRASS_BLOCK) {
				return false;
			}
			BlockState placed = MadokuEcosystemManager.setFoliageAmount(foliageBlock.defaultBlockState(), 1);
			return placed.canSurvive(world, foliagePos);
		}

		if (foliageState.getBlock() != foliageBlock) {
			return false;
		}
		int amount = MadokuEcosystemManager.getFoliageAmount(foliageState);
		int maxAmount = MadokuEcosystemManager.getFoliageMaxAmount(foliageState);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = MadokuEcosystemManager.setFoliageAmount(foliageState, amount + 1);
		return updated != foliageState && updated.canSurvive(world, foliagePos);
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

	static void pickTreeCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeGroundCandidates) {
		if (world == null || treeGroundCandidates == null || treeGroundCandidates.isEmpty() || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		if (treeCandidatesByChunk.containsKey(chunkKey)) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		List<MadokuEcosystemManager.TreeCandidateOption> options = new ArrayList<>();
		for (Long packedPos : treeGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			BlockState groundState = world.getBlockState(groundPos);
			for (String treeType : resolveTreeTypesForBiome(world, groundPos)) {
				if (treeType == null || treeType.isBlank()) {
					continue;
				}
		if (!isValidTreeGroundCandidate(world, groundPos, groundState, treeType)) {
					continue;
				}
				double requiredGrowthTicks = resolveTreeRequiredGrowthTicks(treeType, seasonId);
				if (requiredGrowthTicks <= 0.0d) {
					continue;
				}
				options.add(new MadokuEcosystemManager.TreeCandidateOption(groundPos.asLong(), treeType, requiredGrowthTicks));
			}
		}

		if (options.isEmpty()) {
			return;
		}

		MadokuEcosystemManager.TreeCandidateOption selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));
		treeCandidatesByChunk.put(
			chunkKey,
			new MadokuEcosystemManager.TreeCandidateState(
				MadokuEcosystemManager.levelId(world),
				chunkX,
				chunkZ,
				selected.groundPos(),
				selected.treeType(),
				seasonId,
				selected.requiredGrowthTicks(),
				0.0d,
				MadokuTimeManager.getCurrentAbsoluteDayTime(world)
			)
		);
		syncChunkProcessorTracking(chunkKey);
		MadokuEcosystemManager.dirty = true;
	}

	static void pickCactusCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> cactusGroundCandidates) {
		if (world == null || cactusGroundCandidates == null || cactusGroundCandidates.isEmpty() || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		if (cactusCandidatesByChunk.containsKey(chunkKey)) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveCactusRequiredGrowthTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : cactusGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (!isValidCactusGroundCandidate(world, groundPos, world.getBlockState(groundPos))) {
				continue;
			}
			options.add(packedPos);
		}
		if (options.isEmpty()) {
			return;
		}

		long selectedGroundPos = options.get(ThreadLocalRandom.current().nextInt(options.size()));
		cactusCandidatesByChunk.put(
			chunkKey,
			new MadokuEcosystemManager.CactusCandidateState(
				MadokuEcosystemManager.levelId(world),
				chunkX,
				chunkZ,
				selectedGroundPos,
				seasonId,
				requiredGrowthTicks,
				0.0d,
				MadokuTimeManager.getCurrentAbsoluteDayTime(world)
			)
		);
		syncChunkProcessorTracking(chunkKey);
		MadokuEcosystemManager.dirty = true;
	}

	static void pickGrassCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> grassGroundCandidates) {
		if (world == null || grassGroundCandidates == null || grassGroundCandidates.isEmpty() || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.GrassCandidateState> existingCandidates = grassCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, 4 - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : grassGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidGrassGroundCandidate(world, groundPos, world.getBlockState(groundPos))) {
				boolean alreadyTracked = false;
				if (existingCandidates != null) {
					for (MadokuEcosystemManager.GrassCandidateState existing : existingCandidates) {
						if (existing != null && existing.groundPos == packedPos.longValue()) {
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
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveGrassRequiredGrowthTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			grassCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new ArrayList<>())
				.add(new MadokuEcosystemManager.GrassCandidateState(
					MadokuEcosystemManager.levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					seasonId,
					requiredGrowthTicks,
					0.0d,
					MadokuTimeManager.getCurrentAbsoluteDayTime(world)
				));
			syncChunkProcessorTracking(chunkKey);
			MadokuEcosystemManager.dirty = true;
		}
	}

	static void pickDesertFoliageGrowthCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> groundCandidates) {
		if (world == null || groundCandidates == null || groundCandidates.isEmpty() || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.GrassCandidateState> existingCandidates = desertFoliageGrowthCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, 4 - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : groundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidDesertFoliageGrowthGroundCandidate(world, groundPos, world.getBlockState(groundPos))) {
				boolean alreadyTracked = false;
				if (existingCandidates != null) {
					for (MadokuEcosystemManager.GrassCandidateState existing : existingCandidates) {
						if (existing != null && existing.groundPos == packedPos.longValue()) {
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
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveDesertFoliageGrowthRequiredTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			desertFoliageGrowthCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new ArrayList<>())
				.add(new MadokuEcosystemManager.GrassCandidateState(
					MadokuEcosystemManager.levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					seasonId,
					requiredGrowthTicks,
					0.0d,
					MadokuTimeManager.getCurrentAbsoluteDayTime(world)
				));
			syncChunkProcessorTracking(chunkKey);
			MadokuEcosystemManager.dirty = true;
		}
	}

	static void pickFoliageCandidateForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		String foliageType,
		Set<Long> foliageGroundCandidates
	) {
		if (world == null || foliageGroundCandidates == null || foliageGroundCandidates.isEmpty() || !MadokuEcosystemManager.isNaturalGrowthEnabled()) {
			return;
		}

		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
		if (normalizedFoliageType.isBlank()) {
			return;
		}

		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		List<MadokuEcosystemManager.FoliageCandidateState> existingCandidates = foliageCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, 4 - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : foliageGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidFoliageGroundCandidate(world, groundPos, world.getBlockState(groundPos), normalizedFoliageType)) {
				boolean alreadyTracked = false;
				if (existingCandidates != null) {
					for (MadokuEcosystemManager.FoliageCandidateState existing : existingCandidates) {
						if (existing != null
							&& existing.groundPos == packedPos.longValue()
							&& NaturalGrowthConfigManager.normalizeFoliageType(existing.foliageType).equals(normalizedFoliageType)) {
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
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(MadokuSeason.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveFoliageRequiredGrowthTicks(world, normalizedFoliageType, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			foliageCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new ArrayList<>())
				.add(new MadokuEcosystemManager.FoliageCandidateState(
					MadokuEcosystemManager.levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					normalizedFoliageType,
					seasonId,
					requiredGrowthTicks,
					0.0d,
					MadokuTimeManager.getCurrentAbsoluteDayTime(world)
				));
			syncChunkProcessorTracking(chunkKey);
			MadokuEcosystemManager.dirty = true;
		}
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
			if (!EcosystemNaturalErosionManager.isTrackableGroundBlock(state)) {
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
					? resolveSurfaceDirtGrowthBlock(world, dirtPos)
					: EcosystemNaturalErosionManager.resolveWetGroundReplacementBlock(world, dirtPos, state, dirt.erosionRuleId);
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
		MadokuEcosystemManager.TreeCandidateState candidate = treeCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		boolean progressed = false;
		boolean removed = false;
		boolean grew = false;
		if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			treeCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
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
		if (!isValidTreeGroundCandidate(world, groundPos, groundState, candidate.treeType)) {
			treeCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
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
			boolean grownNow = tryGrowTreeAtGround(world, groundPos, candidate.treeType);
			treeCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
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
		MadokuEcosystemManager.CactusCandidateState candidate = cactusCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		boolean progressed = false;
		boolean removed = false;
		boolean grew = false;
		if (!candidate.levelId.equals(MadokuEcosystemManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			cactusCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
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
		if (!isValidCactusGroundCandidate(world, groundPos, groundState)) {
			cactusCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
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
			boolean grownNow = tryGrowCactusAtGround(world, groundPos);
			cactusCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
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
		List<MadokuEcosystemManager.GrassCandidateState> candidates = grassCandidatesByChunk.get(chunkKey);
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
			if (!isValidGrassGroundCandidate(world, groundPos, groundState)) {
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
				tryGrowGrassAtGround(world, groundPos);
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				grown++;
				removed++;
			}
		}

		if (candidates.isEmpty()) {
			grassCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
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
		List<MadokuEcosystemManager.GrassCandidateState> candidates = desertFoliageGrowthCandidatesByChunk.get(chunkKey);
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
			if (!isValidDesertFoliageGrowthGroundCandidate(world, groundPos, groundState)) {
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
				tryGrowDesertFoliageAtGround(world, groundPos);
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				grown++;
				removed++;
			}
		}

		if (candidates.isEmpty()) {
			desertFoliageGrowthCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
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
		List<MadokuEcosystemManager.FoliageCandidateState> candidates = foliageCandidatesByChunk.get(chunkKey);
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
			if (!isValidFoliageGroundCandidate(world, groundPos, groundState, candidate.foliageType)) {
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
				tryGrowFoliageAtGround(world, groundPos, candidate.foliageType);
				candidates.remove(index);
				removedAny = true;
				MadokuEcosystemManager.dirty = true;
				grown++;
				removed++;
			}
		}

		if (candidates.isEmpty()) {
			foliageCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
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

	static double randomDaysToTicks(EcosystemConfigManager.DayRange range) {
		if (range == null) {
			return -1.0d;
		}
		return randomDaysToTicks(range.minDays(), range.maxDays());
	}

	private static double randomDaysToTicks(double minDays, double maxDays) {
		double min = Math.max(0.0d, minDays);
		double max = Math.max(min, maxDays);
		if (max <= 0.0d) {
			return -1.0d;
		}
		double days = min + ThreadLocalRandom.current().nextDouble(max - min + 1.0d);
		return Math.max(1.0d, days * MadokuTimeManager.MINECRAFT_TICKS_PER_CYCLE);
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
