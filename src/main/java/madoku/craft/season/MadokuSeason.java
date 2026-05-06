package madoku.craft.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.chunk.ChunkManagerSystem;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MadokuSeason {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuSeason.class);

	private static final String SEASON_CONFIG_FOLDER_NAME = "madoku-craft-season";
	private static final String SEASON_CONFIG_FILE_NAME = "madoku-season";
	private static final String DATA_FOLDER_NAME = "madoku-craft-season";
	private static final String DATA_FILE_NAME = "madoku-season";
	private static final String BIOME_FOLDER_NAME = "biomes";
	private static final String FIELD_SEASONAL_QUEUE = "seasonal-queue";
	private static final String TASK_TYPE_SEASON_SCAN = "season_scan";
	private static final String TASK_TYPE_SEASON_PROCESS = "season_process";
	private static final String SEASON_SCAN_SCHEDULER_KEY = "madoku-season-scan";
	private static final String SEASON_PROCESS_SCHEDULER_KEY = "madoku-season-process";
	private static final long SEASON_YEAR_DAYS = MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS * 4L;
	private static final int WATER_QUEUE_MAX_SIZE = 640000;
	private static final int WATER_SURFACE_SCAN_DEPTH = 2;
	private static final int TEMPERATE_TRANSITION_START_DAY = MadokuSeasonConfig.DEFAULT_DAYS_PER_WEEK * 2;

	private static final Map<String, BiomeClimateRecord> BIOME_CLIMATE_CACHE = new ConcurrentHashMap<>();
	private static final ChunkManagerSystem.ChunkLifecycleListener SEASON_CHUNK_LISTENER = new ChunkManagerSystem.ChunkLifecycleListener() {
		@Override
		public void onChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
			MadokuSeason.onTrackedChunkLoaded(level, chunkX, chunkZ);
		}

		@Override
		public void onChunkUnloaded(ServerLevel level, int chunkX, int chunkZ) {
			MadokuSeason.onTrackedChunkUnloaded(level, chunkX, chunkZ);
		}
	};

	private static volatile Settings settings = Settings.defaults();
	private static volatile SeasonState lastProcessedState = SeasonState.empty();
	private static volatile String seasonScanSchedulerId = "";
	private static volatile String seasonProcessSchedulerId = "";
	private static volatile boolean seasonScanTaskScheduled = false;
	private static volatile boolean seasonProcessTaskScheduled = false;
	private static volatile int seasonScanCursor = 0;
	private static volatile boolean discoveryChunksSeeded = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static final LinkedHashMap<Long, SeasonWaterWork> PENDING_WATER_WORK = new LinkedHashMap<>();
	private static final ArrayList<Long> PENDING_WATER_WORK_ORDER = new ArrayList<>();
	private static final ArrayList<Long> DISCOVERY_LOADED_CHUNKS = new ArrayList<>();
	private static final LinkedHashSet<Long> DISCOVERY_LOADED_CHUNK_KEYS = new LinkedHashSet<>();

	private MadokuSeason() {
	}

	public static void initialize() {
		loadStaticConfig();
		ChunkManagerSystem.registerChunkLifecycleListener(SEASON_CHUNK_LISTENER);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_SEASON_SCAN, MadokuSeason::runSeasonScanTask);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_SEASON_PROCESS, MadokuSeason::runSeasonProcessTask);
	}

	public static void reset() {
		BIOME_CLIMATE_CACHE.clear();
		lastProcessedState = SeasonState.empty();
		seasonScanSchedulerId = "";
		seasonProcessSchedulerId = "";
		seasonScanTaskScheduled = false;
		seasonProcessTaskScheduled = false;
		seasonScanCursor = 0;
		discoveryChunksSeeded = false;
		lastAutosaveBucket = Long.MIN_VALUE;
		PENDING_WATER_WORK.clear();
		PENDING_WATER_WORK_ORDER.clear();
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
	}

	public static void onServerStarted(MinecraftServer server) {
		seasonScanSchedulerId = "";
		seasonProcessSchedulerId = "";
		seasonScanTaskScheduled = false;
		seasonProcessTaskScheduled = false;
		seasonScanSchedulerId = ensureSeasonScanSchedulerExists();
		seasonProcessSchedulerId = ensureSeasonProcessSchedulerExists();
		if (seasonScanSchedulerId.isBlank() || seasonProcessSchedulerId.isBlank()) {
			return;
		}
		SchedulerManagerSystem.clearQueuedRequests(seasonScanSchedulerId);
		SchedulerManagerSystem.clearQueuedRequests(seasonProcessSchedulerId);
		rebuildSeasonQueue(server);
	}

	public static void onServerTick(MinecraftServer server) {
		requestSeasonProcessProcessing(server, MadokuSeasonConfig.DEFAULT_SEASON_PROCESS_INTERVAL_TICKS);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		SeasonState persistedState = parsePersistedState(data);
		if (persistedState != null) {
			lastProcessedState = persistedState;
		} else {
			lastProcessedState = resolveCurrentState(server.overworld());
		}
		loadPendingWaterWork(data);
		seasonScanCursor = 0;
		discoveryChunksSeeded = false;
		DISCOVERY_LOADED_CHUNKS.clear();
		DISCOVERY_LOADED_CHUNK_KEYS.clear();
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static SeasonState getCurrentState() {
		return resolveDisplayState();
	}

	public static SeasonState getCurrentState(ServerLevel world) {
		return resolveCurrentState(world);
	}

	public static Season getCurrentSeason() {
		return getCurrentState().season();
	}

	public static Season getCurrentSeason(ServerLevel world) {
		return getCurrentState(world).season();
	}

	public static String getCurrentSeasonId() {
		return getCurrentSeason().id;
	}

	public static String getCurrentSeasonId(ServerLevel world) {
		return getCurrentSeason(world).id;
	}

	public static String getCurrentSeasonDisplayName() {
		return capitalizeSeasonId(getCurrentSeasonId());
	}

	public static String getCurrentSeasonDisplayName(ServerLevel world) {
		return capitalizeSeasonId(getCurrentSeasonId(world));
	}

	public static int getCurrentSeasonDay() {
		return getCurrentState().seasonDay();
	}

	public static int getCurrentSeasonDay(ServerLevel world) {
		return getCurrentState(world).seasonDay();
	}

	public static int getCurrentSeasonWeek() {
		return getCurrentState().week();
	}

	public static int getCurrentSeasonWeek(ServerLevel world) {
		return getCurrentState(world).week();
	}

	public static BiomeClimate resolveBiomeClimate(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return BiomeClimate.TEMPERATE;
		}

		if (!settings.enabled || !settings.biomeOverridesEnabled) {
			return resolveNativeBiomeClimate(world, pos);
		}

		Identifier biomeId = resolveBiomeId(world, pos);
		if (biomeId == null) {
			return resolveNativeBiomeClimate(world, pos);
		}

		BiomeClimateRecord cached = BIOME_CLIMATE_CACHE.get(biomeId.toString());
		if (cached != null) {
			return cached.classification();
		}

		BiomeClimateRecord loaded = loadBiomeClimateRecord(world, pos, biomeId);
		BIOME_CLIMATE_CACHE.put(biomeId.toString(), loaded);
		return loaded.classification();
	}

	public static BiomeClimate resolveNativeBiomeClimate(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return BiomeClimate.TEMPERATE;
		}

		Holder<Biome> biomeEntry = world.getBiome(pos);
		Biome biome = biomeEntry.value();
		Biome.Precipitation precipitation = resolveNativeBiomePrecipitation(biome);
		float temperature = biome.getBaseTemperature();
		return classifyBiome(precipitation, temperature, settings);
	}

	public static BiomeMoisture resolveBiomeMoisture(Biome biome) {
		if (biome == null) {
			return BiomeMoisture.WET;
		}

		return resolveBiomeMoisture(resolveNativeBiomePrecipitation(biome));
	}

	public static BiomeClimate resolveBiomeClimate(Biome biome) {
		if (biome == null) {
			return BiomeClimate.TEMPERATE;
		}

		return classifyBiome(null, biome.getBaseTemperature(), settings);
	}

	public static BiomeMoisture resolveBiomeMoisture(Biome.Precipitation precipitation) {
		if (precipitation == null) {
			return BiomeMoisture.WET;
		}

		return switch (precipitation) {
			case NONE -> BiomeMoisture.DRY;
			case RAIN -> BiomeMoisture.WET;
			case SNOW -> BiomeMoisture.SNOWY;
		};
	}

	public static double resolveWaterFreezeProgress(Season season, BiomeClimate climate, int seasonDay) {
		Season safeSeason = season == null ? Season.SPRING : season;
		BiomeClimate safeClimate = climate == null ? BiomeClimate.TEMPERATE : climate;
		int safeSeasonDay = clampSeasonDay(seasonDay);
		return switch (safeClimate) {
			case COLD -> resolveColdWaterProgress(safeSeason);
			case TEMPERATE -> resolveTemperateWaterProgress(safeSeason, safeSeasonDay);
			case HOT -> resolveHotWaterProgress(safeSeason);
		};
	}

	public static SeasonalPrecipitation resolveSeasonalPrecipitation(Biome biome) {
		if (biome == null) {
			return SeasonalPrecipitation.DRY;
		}

		SeasonState state = getCurrentState();
		BiomeClimate climate = resolveBiomeClimate(biome);
		BiomeMoisture moisture = resolveBiomeMoisture(biome);
		return resolveSeasonalPrecipitation(state.season(), climate, moisture);
	}

	public static SeasonalPrecipitation resolveSeasonalPrecipitation(Season season, BiomeClimate climate, BiomeMoisture moisture) {
		Season safeSeason = season == null ? Season.SPRING : season;
		BiomeClimate safeClimate = climate == null ? BiomeClimate.TEMPERATE : climate;
		BiomeMoisture safeMoisture = moisture == null ? BiomeMoisture.WET : moisture;

		return switch (safeMoisture) {
			case DRY -> resolveDrySeasonalPrecipitation(safeSeason, safeClimate);
			case WET -> resolveWetSeasonalPrecipitation(safeSeason, safeClimate);
			case SNOWY -> resolveSnowySeasonalPrecipitation(safeSeason, safeClimate);
		};
	}

	private static double resolveTemperateWaterProgress(Season season, int seasonDay) {
		return switch (season) {
			case SPRING -> seasonDay < TEMPERATE_TRANSITION_START_DAY ? 1.0d : 0.0d;
			case SUMMER -> 0.0d;
			case FALL -> seasonDay < TEMPERATE_TRANSITION_START_DAY ? 0.0d : 1.0d;
			case WINTER -> 1.0d;
		};
	}

	private static double resolveColdWaterProgress(Season season) {
		return switch (season) {
			case SPRING -> 1.0d;
			case SUMMER -> 0.0d;
			case FALL -> 1.0d;
			case WINTER -> 1.0d;
		};
	}

	private static double resolveHotWaterProgress(Season season) {
		return switch (season) {
			case SPRING -> 0.0d;
			case SUMMER -> 0.0d;
			case FALL -> 0.0d;
			case WINTER -> 1.0d;
		};
	}

	private static SeasonalPrecipitation resolveDrySeasonalPrecipitation(Season season, BiomeClimate climate) {
		return switch (climate) {
			case COLD -> SeasonalPrecipitation.SNOW;
			case TEMPERATE -> SeasonalPrecipitation.DRY;
			case HOT -> season == Season.SUMMER ? SeasonalPrecipitation.RAIN : SeasonalPrecipitation.DRY;
		};
	}

	private static SeasonalPrecipitation resolveWetSeasonalPrecipitation(Season season, BiomeClimate climate) {
		return switch (climate) {
			case COLD -> switch (season) {
				case SPRING -> SeasonalPrecipitation.RAIN;
				case SUMMER -> SeasonalPrecipitation.DRY;
				case FALL, WINTER -> SeasonalPrecipitation.SNOW;
			};
			case TEMPERATE -> switch (season) {
				case SPRING, SUMMER, FALL -> SeasonalPrecipitation.RAIN;
				case WINTER -> SeasonalPrecipitation.SNOW;
			};
			case HOT -> switch (season) {
				case SPRING, FALL -> SeasonalPrecipitation.RAIN;
				case SUMMER -> SeasonalPrecipitation.DRY;
				case WINTER -> SeasonalPrecipitation.SNOW;
			};
		};
	}

	private static SeasonalPrecipitation resolveSnowySeasonalPrecipitation(Season season, BiomeClimate climate) {
		return switch (climate) {
			case COLD -> switch (season) {
				case SPRING -> SeasonalPrecipitation.DRY;
				case SUMMER, FALL -> SeasonalPrecipitation.RAIN;
				case WINTER -> SeasonalPrecipitation.SNOW;
			};
			case TEMPERATE -> switch (season) {
				case SPRING -> SeasonalPrecipitation.DRY;
				case SUMMER, FALL -> SeasonalPrecipitation.RAIN;
				case WINTER -> SeasonalPrecipitation.SNOW;
			};
			case HOT -> switch (season) {
				case SPRING -> SeasonalPrecipitation.DRY;
				case SUMMER -> SeasonalPrecipitation.RAIN;
				case FALL, WINTER -> SeasonalPrecipitation.SNOW;
			};
		};
	}

	private static void runSeasonScanTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			seasonScanSchedulerId = context.getSchedulerId();
		}
		seasonScanTaskScheduled = false;

		if (server == null || !settings.enabled) {
			return;
		}

		ServerLevel world = resolveSeasonWorld(server);
		if (world == null) {
			return;
		}

		SeasonState currentState = refreshSeasonState(world);
		seedDiscoveryChunksIfNeeded(world);
		if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			seasonScanCursor = 0;
			return;
		}

		int selectedIndex = Math.floorMod(seasonScanCursor, DISCOVERY_LOADED_CHUNKS.size());
		Long packedChunk = DISCOVERY_LOADED_CHUNKS.get(selectedIndex);
		if (packedChunk == null) {
			removeDiscoveryLoadedChunkAt(selectedIndex);
			if (!DISCOVERY_LOADED_CHUNKS.isEmpty()) {
				seasonScanCursor = Math.min(seasonScanCursor, DISCOVERY_LOADED_CHUNKS.size() - 1);
				requestSeasonScanProcessing(server, 1L);
			} else {
				seasonScanCursor = 0;
			}
			return;
		}

		int chunkX = unpackChunkX(packedChunk);
		int chunkZ = unpackChunkZ(packedChunk);
		if (!ChunkManagerSystem.isChunkLoaded(world, chunkX, chunkZ)) {
			removeDiscoveryLoadedChunk(packedChunk);
			if (!DISCOVERY_LOADED_CHUNKS.isEmpty()) {
				seasonScanCursor = Math.min(seasonScanCursor, DISCOVERY_LOADED_CHUNKS.size() - 1);
				requestSeasonScanProcessing(server, 1L);
			} else {
				seasonScanCursor = 0;
			}
			return;
		}

		SeasonWaterScanResult scanResult = scanSeasonalWaterChunk(world, chunkX, chunkZ, currentState);
		emitSeasonQueueScanDebug(world, currentState, 1, scanResult.columnsScanned(), PENDING_WATER_WORK.size(), scanResult.enqueuedBlocks());
		boolean completedCycle = selectedIndex + 1 >= DISCOVERY_LOADED_CHUNKS.size();
		seasonScanCursor = completedCycle ? 0 : selectedIndex + 1;
		if (!DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			requestSeasonScanProcessing(server, 1L);
		}
		if (scanResult.enqueuedBlocks() > 0 || !PENDING_WATER_WORK_ORDER.isEmpty()) {
			requestSeasonProcessProcessing(server, 1L);
		}
	}

	private static void runSeasonProcessTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			seasonProcessSchedulerId = context.getSchedulerId();
		}
		seasonProcessTaskScheduled = false;

		if (server == null || !settings.enabled) {
			return;
		}

		ServerLevel world = resolveSeasonWorld(server);
		if (world == null) {
			requestSeasonProcessProcessing(server, MadokuSeasonConfig.DEFAULT_SEASON_PROCESS_INTERVAL_TICKS);
			return;
		}

		SeasonState currentState = refreshSeasonState(world);

		SeasonWaterWork work = pollNextSeasonWaterWork();
		if (work == null) {
			requestSeasonProcessProcessing(server, MadokuSeasonConfig.DEFAULT_SEASON_PROCESS_INTERVAL_TICKS);
			return;
		}

		boolean changed = processSeasonWaterWork(world, work);
		emitSeasonQueueProcessDebug(world, currentState, work, changed, PENDING_WATER_WORK.size());
		requestSeasonProcessProcessing(server, MadokuSeasonConfig.DEFAULT_SEASON_PROCESS_INTERVAL_TICKS);
	}

	private static SeasonState refreshSeasonState(ServerLevel world) {
		SeasonState previousState = lastProcessedState;
		SeasonState currentState = resolveCurrentState(world);
		boolean seasonTransition = previousState.absoluteDay() >= 0L && previousState.season() != currentState.season();
		if (seasonTransition) {
			emitSeasonTransitionDebug(previousState, currentState);
		}
		if (!currentState.equals(previousState)) {
			lastProcessedState = currentState;
		}
		if (seasonTransition) {
			rebuildSeasonQueue(world == null ? null : world.getServer());
		}
		return currentState;
	}

	private static void rebuildSeasonQueue(MinecraftServer server) {
		PENDING_WATER_WORK.clear();
		PENDING_WATER_WORK_ORDER.clear();
		seasonScanTaskScheduled = false;
		seasonProcessTaskScheduled = false;
		seasonScanCursor = 0;

		if (server == null) {
			return;
		}

		String scanSchedulerId = ensureSeasonScanSchedulerExists();
		String processSchedulerId = ensureSeasonProcessSchedulerExists();
		if (!scanSchedulerId.isBlank()) {
			SchedulerManagerSystem.clearQueuedRequests(scanSchedulerId);
		}
		if (!processSchedulerId.isBlank()) {
			SchedulerManagerSystem.clearQueuedRequests(processSchedulerId);
		}

		ServerLevel world = resolveSeasonWorld(server);
		SeasonState currentState = resolveCurrentState(world);
		if (currentState.absoluteDay() >= 0L) {
			lastProcessedState = currentState;
		}
		if (world != null) {
			syncDiscoveryLoadedChunks(world);
			emitSeasonQueueScanDebug(world, currentState, DISCOVERY_LOADED_CHUNKS.size(), 0, PENDING_WATER_WORK.size(), 0);
		}

		savePersistedData(server);
		if (!DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			requestSeasonScanProcessing(server, 1L);
		}
		requestSeasonProcessProcessing(server, 1L);
	}

	private static void emitSeasonTransitionDebug(SeasonState previousState, SeasonState currentState) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.transition")) {
			return;
		}

		MadokuDebug.event("season.transition", MadokuDebug.Domain.SEASON)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.subject("season")
			.field("from", previousState.season().id)
			.field("to", currentState.season().id)
			.field("absolute_day", currentState.absoluteDay())
			.field("season_day", currentState.seasonDay())
			.field("week", currentState.week())
			.field("day_in_week", currentState.dayInWeek())
			.field("cycle_day", currentState.cycleDay())
			.field("progress", formatProgress(currentState.progress()))
			.log();
	}

	private static void emitSeasonQueueScanDebug(
		ServerLevel world,
		SeasonState state,
		int loadedChunkCount,
		int columnsScanned,
		int queueSize,
		int enqueuedBlocks
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.queue_scan")) {
			return;
		}

		MadokuDebug.event("season.queue_scan", MadokuDebug.Domain.SEASON)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject("season_water_scan")
			.field("season", state == null ? "unknown" : state.season().id)
			.field("week", state == null ? "0" : state.week())
			.field("loaded_chunks", loadedChunkCount)
			.field("columns_scanned", columnsScanned)
			.field("queue_size", queueSize)
			.field("enqueued_blocks", enqueuedBlocks)
			.field("queue_target", WATER_QUEUE_MAX_SIZE)
			.log();
	}

	private static void emitSeasonQueueProcessDebug(
		ServerLevel world,
		SeasonState state,
		SeasonWaterWork work,
		boolean changed,
		int queueSize
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.SEASON, "season.queue_process")) {
			return;
		}

		BlockPos pos = BlockPos.of(work.blockPosLong());
		MadokuDebug.event("season.queue_process", MadokuDebug.Domain.SEASON)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject("block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ())
			.field("season", state == null ? "unknown" : state.season().id)
			.field("queued_season", work.queuedSeasonId())
			.field("action", work.action().id)
			.field("changed", changed)
			.field("queue_size", queueSize)
			.log();
	}

	private static void onTrackedChunkLoaded(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null || !settings.enabled || !isSeasonWorld(world)) {
			return;
		}

		refreshSeasonState(world);
		removeSeasonWaterWorkForChunk(chunkX, chunkZ);
		discoveryChunksSeeded = true;
		addDiscoveryLoadedChunk(packChunk(chunkX, chunkZ));
		requestSeasonScanProcessing(world.getServer(), 1L);
	}

	private static void onTrackedChunkUnloaded(ServerLevel world, int chunkX, int chunkZ) {
		if (world == null || !isSeasonWorld(world)) {
			return;
		}
		removeSeasonWaterWorkForChunk(chunkX, chunkZ);
		discoveryChunksSeeded = true;
		removeDiscoveryLoadedChunk(packChunk(chunkX, chunkZ));
	}

	private static void seedDiscoveryChunksIfNeeded(ServerLevel world) {
		if (discoveryChunksSeeded || world == null) {
			return;
		}
		discoveryChunksSeeded = true;
		syncDiscoveryLoadedChunks(world);
	}

	private static void syncDiscoveryLoadedChunks(ServerLevel world) {
		if (world == null) {
			DISCOVERY_LOADED_CHUNKS.clear();
			DISCOVERY_LOADED_CHUNK_KEYS.clear();
			seasonScanCursor = 0;
			return;
		}

		LinkedHashSet<Long> liveLoaded = new LinkedHashSet<>();
		for (Long packedChunk : ChunkManagerSystem.getLoadedChunkPositions(world)) {
			if (packedChunk != null) {
				liveLoaded.add(packedChunk);
			}
		}

		for (Long packedChunk : liveLoaded) {
			addDiscoveryLoadedChunk(packedChunk);
		}

		for (int index = DISCOVERY_LOADED_CHUNKS.size() - 1; index >= 0; index--) {
			Long existing = DISCOVERY_LOADED_CHUNKS.get(index);
			if (existing == null || !liveLoaded.contains(existing)) {
				removeDiscoveryLoadedChunkAt(index);
			}
		}
		if (DISCOVERY_LOADED_CHUNKS.isEmpty()) {
			seasonScanCursor = 0;
		} else {
			seasonScanCursor = Math.min(Math.max(0, seasonScanCursor), DISCOVERY_LOADED_CHUNKS.size() - 1);
		}
	}

	private static void addDiscoveryLoadedChunk(long packedChunk) {
		if (!DISCOVERY_LOADED_CHUNK_KEYS.add(packedChunk)) {
			return;
		}
		DISCOVERY_LOADED_CHUNKS.add(packedChunk);
	}

	private static void removeDiscoveryLoadedChunk(long packedChunk) {
		if (!DISCOVERY_LOADED_CHUNK_KEYS.remove(packedChunk)) {
			return;
		}
		for (int index = 0; index < DISCOVERY_LOADED_CHUNKS.size(); index++) {
			Long existing = DISCOVERY_LOADED_CHUNKS.get(index);
			if (existing != null && existing.longValue() == packedChunk) {
				removeDiscoveryLoadedChunkAt(index);
				break;
			}
		}
	}

	private static void removeDiscoveryLoadedChunkAt(int index) {
		if (index < 0 || index >= DISCOVERY_LOADED_CHUNKS.size()) {
			return;
		}
		Long removed = DISCOVERY_LOADED_CHUNKS.remove(index);
		if (removed != null) {
			DISCOVERY_LOADED_CHUNK_KEYS.remove(removed);
		}
		if (seasonScanCursor > index) {
			seasonScanCursor--;
		}
	}

	private static SeasonWaterScanResult scanSeasonalWaterChunk(ServerLevel world, int chunkX, int chunkZ, SeasonState state) {
		if (world == null || state == null || !ChunkManagerSystem.isChunkLoaded(world, chunkX, chunkZ)) {
			return new SeasonWaterScanResult(0, 0);
		}

		int columnsScanned = 0;
		int enqueuedBlocks = 0;
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				if (PENDING_WATER_WORK.size() >= WATER_QUEUE_MAX_SIZE) {
					return new SeasonWaterScanResult(columnsScanned, enqueuedBlocks);
				}

				columnsScanned++;
				SeasonWaterWork work = scanSeasonalWaterColumn(world, chunkX, chunkZ, localX, localZ, state);
				if (enqueueSeasonWaterWork(work)) {
					enqueuedBlocks++;
				}
			}
		}
		return new SeasonWaterScanResult(columnsScanned, enqueuedBlocks);
	}

	private static SeasonWaterWork scanSeasonalWaterColumn(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		int localX,
		int localZ,
		SeasonState state
	) {
		int minBlockX = chunkX << 4;
		int minBlockZ = chunkZ << 4;
		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
		int worldX = minBlockX + localX;
		int worldZ = minBlockZ + localZ;
		int columnTopY = Math.min(
			world.getMaxY() - 1,
			world.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1
		);
		int columnBottomY = Math.max(world.getMinY(), columnTopY - WATER_SURFACE_SCAN_DEPTH);

		for (int worldY = columnTopY; worldY >= columnBottomY; worldY--) {
			mutablePos.set(worldX, worldY, worldZ);
			BlockState blockState = world.getBlockState(mutablePos);
			if (!isSeasonalWaterCandidate(blockState) && !isWaterloggedFreezeCandidate(blockState)) {
				continue;
			}

			BiomeClimate climate = resolveBiomeClimate(world, mutablePos);
			if (!isTransitionScanWindow(state, climate)) {
				continue;
			}
			double freezeProgress = resolveWaterFreezeProgress(state.season(), climate, state.seasonDay());
			boolean shouldFreeze = shouldFreezeColumn(worldX, worldZ, freezeProgress);
			SeasonWaterAction action = resolveSeasonWaterAction(blockState, shouldFreeze);
			if (action == null) {
				return null;
			}

			long packedPos = BlockPos.asLong(worldX, worldY, worldZ);
			if (PENDING_WATER_WORK.containsKey(packedPos)) {
				return null;
			}

			return new SeasonWaterWork(
				packedPos,
				action,
				state.season().id,
				state.seasonDay(),
				MadokuTicks.getGameplayTicks()
			);
		}

		return null;
	}

	private static SeasonWaterAction resolveSeasonWaterAction(BlockState blockState, boolean shouldFreeze) {
		if (blockState == null) {
			return null;
		}
		if (isStillWaterBlock(blockState)) {
			return shouldFreeze ? SeasonWaterAction.TO_ICE : null;
		}
		if (isWaterloggedFreezeCandidate(blockState)) {
			return shouldFreeze ? SeasonWaterAction.TO_ICE_CLEAR : null;
		}
		if (blockState.is(Blocks.ICE)) {
			return shouldFreeze ? null : SeasonWaterAction.TO_WATER;
		}
		if (blockState.is(Blocks.SNOW)) {
			return shouldFreeze ? null : SeasonWaterAction.TO_AIR;
		}
		return null;
	}

	private static boolean enqueueSeasonWaterWork(SeasonWaterWork work) {
		if (work == null) {
			return false;
		}
		if (PENDING_WATER_WORK.containsKey(work.blockPosLong()) || PENDING_WATER_WORK.size() >= WATER_QUEUE_MAX_SIZE) {
			return false;
		}

		PENDING_WATER_WORK.put(work.blockPosLong(), work);
		PENDING_WATER_WORK_ORDER.add(work.blockPosLong());
		return true;
	}

	private static int removeSeasonWaterWorkForChunk(int chunkX, int chunkZ) {
		if (PENDING_WATER_WORK_ORDER.isEmpty()) {
			return 0;
		}

		int removed = 0;
		for (int index = PENDING_WATER_WORK_ORDER.size() - 1; index >= 0; index--) {
			Long blockPosLong = PENDING_WATER_WORK_ORDER.get(index);
			if (blockPosLong == null) {
				PENDING_WATER_WORK_ORDER.remove(index);
				continue;
			}

			BlockPos blockPos = BlockPos.of(blockPosLong);
			if ((blockPos.getX() >> 4) != chunkX || (blockPos.getZ() >> 4) != chunkZ) {
				continue;
			}

			PENDING_WATER_WORK_ORDER.remove(index);
			if (PENDING_WATER_WORK.remove(blockPosLong) != null) {
				removed++;
			}
		}
		return removed;
	}

	private static SeasonWaterWork pollNextSeasonWaterWork() {
		if (PENDING_WATER_WORK_ORDER.isEmpty()) {
			return null;
		}

		int selectedIndex = ThreadLocalRandom.current().nextInt(PENDING_WATER_WORK_ORDER.size());
		int lastIndex = PENDING_WATER_WORK_ORDER.size() - 1;
		Long selectedKey = PENDING_WATER_WORK_ORDER.get(selectedIndex);
		Long lastKey = PENDING_WATER_WORK_ORDER.get(lastIndex);
		if (selectedIndex != lastIndex) {
			PENDING_WATER_WORK_ORDER.set(selectedIndex, lastKey);
		}
		PENDING_WATER_WORK_ORDER.remove(lastIndex);
		return selectedKey == null ? null : PENDING_WATER_WORK.remove(selectedKey);
	}

	private static boolean processSeasonWaterWork(ServerLevel world, SeasonWaterWork work) {
		if (world == null || work == null) {
			return false;
		}

		BlockPos blockPos = BlockPos.of(work.blockPosLong());
		if (!ChunkManagerSystem.isChunkLoaded(world, blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
			return false;
		}

		BlockState currentState = world.getBlockState(blockPos);
		if (work.action() == SeasonWaterAction.TO_ICE && isStillWaterBlock(currentState)) {
			world.setBlockAndUpdate(blockPos, Blocks.ICE.defaultBlockState());
			return true;
		}
		if (work.action() == SeasonWaterAction.TO_ICE_CLEAR && isWaterloggedFreezeCandidate(currentState)) {
			world.setBlockAndUpdate(blockPos, Blocks.ICE.defaultBlockState());
			return true;
		}
		if (work.action() == SeasonWaterAction.TO_WATER && currentState.is(Blocks.ICE)) {
			world.setBlockAndUpdate(blockPos, Blocks.WATER.defaultBlockState());
			return true;
		}
		if (work.action() == SeasonWaterAction.TO_AIR && currentState.is(Blocks.SNOW)) {
			world.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
			return true;
		}
		return false;
	}

	private static boolean shouldFreezeColumn(int worldX, int worldZ, double freezeProgress) {
		if (freezeProgress <= 0.0d) {
			return false;
		}
		if (freezeProgress >= 1.0d) {
			return true;
		}
		return freezeProgress >= columnBias(worldX, worldZ);
	}

	private static boolean isTransitionScanWindow(SeasonState state, BiomeClimate climate) {
		if (state == null || climate == null) {
			return false;
		}

		return switch (climate) {
			case TEMPERATE -> isTemperateTransitionWindow(state);
			case COLD -> state.season() == Season.SUMMER || state.season() == Season.FALL;
			case HOT -> state.season() == Season.SPRING || state.season() == Season.WINTER;
		};
	}

	private static boolean isTemperateTransitionWindow(SeasonState state) {
		if (state == null) {
			return false;
		}

		return switch (state.season()) {
			case SPRING -> state.week() >= 3;
			case SUMMER -> state.week() <= 2;
			case FALL -> state.week() >= 3;
			case WINTER -> state.week() <= 2;
		};
	}

	private static double columnBias(int worldX, int worldZ) {
		long mixed = 0x9E3779B97F4A7C15L;
		mixed ^= (long) worldX * 0x632BE59BD9B4E019L;
		mixed ^= (long) worldZ * 0x8CB92BA72F3D8DD7L;
		mixed ^= mixed >>> 33;
		mixed *= 0xFF51AFD7ED558CCDL;
		mixed ^= mixed >>> 33;
		mixed *= 0xC4CEB9FE1A85EC53L;
		mixed ^= mixed >>> 33;
		long positive = mixed & Long.MAX_VALUE;
		return positive / (double) Long.MAX_VALUE;
	}

	private static boolean isSeasonalWaterCandidate(BlockState blockState) {
		return blockState != null
			&& (blockState.is(Blocks.WATER)
			|| blockState.is(Blocks.ICE)
			|| blockState.is(Blocks.SNOW));
	}

	private static boolean isStillWaterBlock(BlockState blockState) {
		return blockState != null
			&& blockState.is(Blocks.WATER)
			&& blockState.getFluidState().isSource();
	}

	private static boolean isWaterloggedFreezeCandidate(BlockState blockState) {
		if (blockState == null) {
			return false;
		}
		if (blockState.is(Blocks.WATER) || blockState.is(Blocks.ICE) || blockState.is(Blocks.SNOW)) {
			return false;
		}
		if (!blockState.getFluidState().is(FluidTags.WATER) || !blockState.getFluidState().isSource()) {
			return false;
		}
		// Restrict to non-occluding waterlogged blocks (seagrass, kelp, coral fans, etc.)
		// so solid waterlogged structures are not overwritten by seasonal ice.
		return !blockState.canOcclude();
	}

	private static boolean isSeasonWorld(ServerLevel world) {
		if (world == null) {
			return false;
		}
		MinecraftServer server = world.getServer();
		ServerLevel seasonWorld = resolveSeasonWorld(server);
		return seasonWorld != null && seasonWorld.dimension().equals(world.dimension());
	}

	private record SeasonWaterWork(
		long blockPosLong,
		SeasonWaterAction action,
		String queuedSeasonId,
		int queuedSeasonDay,
		long queuedAtTick
	) {
	}

	private record SeasonWaterScanResult(int columnsScanned, int enqueuedBlocks) {
	}

	private enum SeasonWaterAction {
		TO_ICE("ice"),
		TO_ICE_CLEAR("ice_clear"),
		TO_WATER("water"),
		TO_AIR("air");

		private final String id;

		SeasonWaterAction(String id) {
			this.id = id;
		}

		private static SeasonWaterAction fromId(String value) {
			if (value == null) {
				return null;
			}

			String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
			for (SeasonWaterAction action : values()) {
				if (action.id.equals(normalized)) {
					return action;
				}
			}
			return null;
		}
	}

	private static int unpackChunkX(long packedChunk) {
		return (int) (packedChunk >> 32);
	}

	private static int unpackChunkZ(long packedChunk) {
		return (int) packedChunk;
	}

	private static long packChunk(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	private static void requestSeasonProcessProcessing(MinecraftServer server, long delay) {
		requestSeasonTask(server, delay, TASK_TYPE_SEASON_PROCESS);
	}

	private static void requestSeasonScanProcessing(MinecraftServer server, long delay) {
		requestSeasonTask(server, delay, TASK_TYPE_SEASON_SCAN);
	}

	private static void requestSeasonTask(MinecraftServer server, long delay, String taskType) {
		if (server == null || !settings.enabled) {
			return;
		}

		boolean scanTask = TASK_TYPE_SEASON_SCAN.equals(taskType);
		if (scanTask && seasonScanTaskScheduled) {
			return;
		}
		if (!scanTask && seasonProcessTaskScheduled) {
			return;
		}

		String schedulerId = scanTask ? ensureSeasonScanSchedulerExists() : ensureSeasonProcessSchedulerExists();
		if (schedulerId.isBlank()) {
			return;
		}
		if (enqueueSeasonTask(schedulerId, delay, taskType)) {
			if (scanTask) {
				seasonScanTaskScheduled = true;
			} else {
				seasonProcessTaskScheduled = true;
			}
			return;
		}

		String recreatedSchedulerId = scanTask
			? SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(SEASON_SCAN_SCHEDULER_KEY))
			: SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(SEASON_PROCESS_SCHEDULER_KEY));
		if (scanTask) {
			seasonScanSchedulerId = recreatedSchedulerId;
		} else {
			seasonProcessSchedulerId = recreatedSchedulerId;
		}
		if (enqueueSeasonTask(recreatedSchedulerId, delay, taskType)) {
			if (scanTask) {
				seasonScanTaskScheduled = true;
			} else {
				seasonProcessTaskScheduled = true;
			}
			return;
		}

		LOGGER.error("Failed to enqueue MadokuSeason scheduler task type {}.", taskType);
	}

	private static String ensureSeasonScanSchedulerExists() {
		if (seasonScanSchedulerId == null || seasonScanSchedulerId.isBlank()) {
			seasonScanSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(SEASON_SCAN_SCHEDULER_KEY)
			);
		}
		return seasonScanSchedulerId;
	}

	private static String ensureSeasonProcessSchedulerExists() {
		if (seasonProcessSchedulerId == null || seasonProcessSchedulerId.isBlank()) {
			seasonProcessSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(SEASON_PROCESS_SCHEDULER_KEY)
			);
		}
		return seasonProcessSchedulerId;
	}

	private static boolean enqueueSeasonTask(String schedulerId, long delay, String taskType) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delay),
			taskType,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static ServerLevel resolveSeasonWorld(MinecraftServer server) {
		return server == null ? null : server.overworld();
	}

	private static SeasonState resolveDisplayState() {
		SeasonState snapshot = lastProcessedState;
		if (!MadokuTime.isEnabled() && snapshot.absoluteDay() >= 0L) {
			return snapshot;
		}
		return resolveCurrentState();
	}

	private static SeasonState resolveCurrentState() {
		return resolveCurrentState(null);
	}

	private static SeasonState resolveCurrentState(ServerLevel world) {
		long absoluteDayTime = MadokuTime.getCurrentAbsoluteDayTime(world);
		long absoluteDay = Math.max(0L, MadokuTime.getDay(absoluteDayTime));
		return resolveStateForAbsoluteDay(absoluteDay);
	}

	private static SeasonState resolveStateForAbsoluteDay(long absoluteDay) {
		long normalizedDay = Math.max(0L, absoluteDay);
		long cycleDay = Math.floorMod(normalizedDay, SEASON_YEAR_DAYS);
		Season season = Season.fromIndex((int) (cycleDay / MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS));
		int seasonDay = (int) (cycleDay % MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS);
		int week = (seasonDay / MadokuSeasonConfig.DEFAULT_DAYS_PER_WEEK) + 1;
		int dayInWeek = (seasonDay % MadokuSeasonConfig.DEFAULT_DAYS_PER_WEEK) + 1;
		double progress = normalizeSeasonProgress(seasonDay, MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS);
		return new SeasonState(absoluteDay, cycleDay, season, seasonDay, week, dayInWeek, progress);
	}

	private static double normalizeSeasonProgress(int seasonDay, int seasonLengthDays) {
		if (seasonLengthDays <= 1) {
			return 1.0d;
		}
		int clamped = Math.max(0, Math.min(seasonLengthDays - 1, seasonDay));
		return (double) clamped / (double) (seasonLengthDays - 1);
	}

	private static String formatProgress(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0.0d, Math.min(1.0d, value)));
	}

	private static int clampSeasonDay(int seasonDay) {
		return Math.max(0, Math.min(MadokuSeasonConfig.DEFAULT_SEASON_LENGTH_DAYS - 1, seasonDay));
	}

	private static BiomeClimateRecord loadBiomeClimateRecord(ServerLevel world, net.minecraft.core.BlockPos pos, Identifier biomeId) {
		Holder<Biome> biomeEntry = world.getBiome(pos);
		Biome biome = biomeEntry.value();
		Biome.Precipitation precipitation = resolveNativeBiomePrecipitation(biome);
		float temperature = biome.getBaseTemperature();
		BiomeClimate nativeClimate = classifyBiome(precipitation, temperature, settings);
		Path file = resolveBiomeFile(biomeId);
		JsonObject defaults = MadokuSeasonConfig.buildBiomeDefaults(
			biomeId.toString(),
			nativeClimate.id,
			nativeClimate.id,
			temperature,
			precipitation.name()
		);

		try {
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(file, defaults);
			BiomeClimateRecord parsed = parseBiomeClimateRecord(normalized, biomeId, nativeClimate, temperature, precipitation);
			if (parsed != null) {
				return parsed;
			}
			JsonStaticSystem.writeManagedFile(file, defaults, defaults);
		} catch (IOException exception) {
			LOGGER.error("Failed to load MadokuSeason biome climate file {}", file, exception);
		}

		return new BiomeClimateRecord(biomeId, nativeClimate, nativeClimate, temperature, precipitation);
	}

	private static BiomeClimateRecord parseBiomeClimateRecord(
		JsonObject source,
		Identifier biomeId,
		BiomeClimate defaultClimate,
		float temperature,
		Biome.Precipitation precipitation
	) throws IOException {
		if (source == null) {
			return null;
		}

		String configuredBiomeId = getString(source, MadokuSeasonConfig.FIELD_BIOME_ID, biomeId.toString());
		String defaultClassification = normalizeClimateId(getString(source, MadokuSeasonConfig.FIELD_DEFAULT_CLASSIFICATION, defaultClimate.id));
		String classificationText = normalizeClimateId(getString(source, MadokuSeasonConfig.FIELD_CLASSIFICATION, defaultClassification));
		BiomeClimate classification = BiomeClimate.fromId(classificationText);
		BiomeClimate normalizedDefault = BiomeClimate.fromId(defaultClassification);
		boolean dirty = false;

		if (!biomeId.toString().equals(configuredBiomeId)) {
			dirty = true;
		}
		if (normalizedDefault == null) {
			normalizedDefault = defaultClimate;
			dirty = true;
		}
		if (classification == null) {
			classification = normalizedDefault;
			dirty = true;
		}

		String precipitationText = normalizeKey(getString(source, MadokuSeasonConfig.FIELD_BIOME_PRECIPITATION, precipitation.name()));
		if (precipitationText.isEmpty()) {
			precipitationText = precipitation.name().toLowerCase();
			dirty = true;
		}

		double recordedTemperature = getDouble(source, MadokuSeasonConfig.FIELD_BIOME_TEMPERATURE, temperature);
		if (Double.isNaN(recordedTemperature) || Double.isInfinite(recordedTemperature)) {
			recordedTemperature = temperature;
			dirty = true;
		}

		if (dirty) {
			JsonObject cleaned = MadokuSeasonConfig.buildBiomeDefaults(
				biomeId.toString(),
				normalizedDefault.id,
				classification.id,
				recordedTemperature,
				precipitationText
			);
			Path file = resolveBiomeFile(biomeId);
			JsonStaticSystem.writeManagedFile(file, cleaned, cleaned);
		}

		return new BiomeClimateRecord(biomeId, normalizedDefault, classification, (float) recordedTemperature, precipitation);
	}

	private static BiomeClimate classifyBiome(Biome.Precipitation precipitation, float temperature, Settings currentSettings) {
		Settings safeSettings = currentSettings == null ? Settings.defaults() : currentSettings;
		if (temperature <= safeSettings.coldTemperatureThreshold || precipitation == Biome.Precipitation.SNOW) {
			return BiomeClimate.COLD;
		}
		if (temperature >= safeSettings.hotTemperatureThreshold) {
			return BiomeClimate.HOT;
		}
		if (precipitation == Biome.Precipitation.NONE) {
			return BiomeClimate.HOT;
		}
		return BiomeClimate.TEMPERATE;
	}

	private static Biome.Precipitation resolveNativeBiomePrecipitation(Biome biome) {
		if (biome == null) {
			return Biome.Precipitation.RAIN;
		}

		if (!biome.hasPrecipitation()) {
			return Biome.Precipitation.NONE;
		}
		if (biome.getBaseTemperature() <= settings.coldTemperatureThreshold) {
			return Biome.Precipitation.SNOW;
		}
		return Biome.Precipitation.RAIN;
	}

	private static Identifier resolveBiomeId(ServerLevel world, net.minecraft.core.BlockPos pos) {
		try {
			Holder<Biome> biomeEntry = world.getBiome(pos);
			return biomeEntry.unwrapKey()
				.map(ResourceKey::identifier)
				.orElseGet(() -> {
					Registry<Biome> biomeRegistry = world.registryAccess().lookupOrThrow(Registries.BIOME);
					return biomeRegistry.getKey(biomeEntry.value());
				});
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static Path resolveBiomeFile(Identifier biomeId) {
		Path directory = resolveBiomeDirectory();
		String fileKey = MadokuSeasonConfig.normalizeKey(biomeId == null ? "" : biomeId.toString());
		return resolveJsonFile(directory, fileKey);
	}

	private static Path resolveBiomeDirectory() {
		Path directory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(SEASON_CONFIG_FOLDER_NAME).resolve(BIOME_FOLDER_NAME);
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create season biome directory: " + directory, exception);
		}
		return directory;
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add(FIELD_SEASONAL_QUEUE, new com.google.gson.JsonArray());
		return root;
	}

	private static SeasonState parsePersistedState(JsonObject source) {
		return null;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		root.add(FIELD_SEASONAL_QUEUE, serializePendingWaterWork());
		return root;
	}

	private static void loadPendingWaterWork(JsonObject source) {
		PENDING_WATER_WORK.clear();
		PENDING_WATER_WORK_ORDER.clear();
		if (source == null) {
			return;
		}

		JsonElement element = source.get(FIELD_SEASONAL_QUEUE);
		if (element == null || !element.isJsonArray()) {
			return;
		}

		for (JsonElement queueElement : element.getAsJsonArray()) {
			if (queueElement == null || !queueElement.isJsonObject()) {
				continue;
			}

			JsonObject queueObject = queueElement.getAsJsonObject();
			Long blockPosLong = getLongObject(queueObject, "block-pos", null);
			String actionId = getString(queueObject, "action", "");
			String queuedSeasonId = getString(queueObject, "queued-season", "unknown");
			int queuedSeasonDay = getInt(queueObject, "queued-season-day", 0);
			long queuedAtTick = getLong(queueObject, "queued-at-tick", 0L);
			SeasonWaterAction action = SeasonWaterAction.fromId(actionId);
			if (blockPosLong == null || action == null) {
				continue;
			}

			if (PENDING_WATER_WORK.putIfAbsent(
				blockPosLong,
				new SeasonWaterWork(blockPosLong, action, queuedSeasonId, queuedSeasonDay, queuedAtTick)
			) == null) {
				PENDING_WATER_WORK_ORDER.add(blockPosLong);
			}
		}
	}

	private static com.google.gson.JsonArray serializePendingWaterWork() {
		com.google.gson.JsonArray queue = new com.google.gson.JsonArray();
		for (SeasonWaterWork work : PENDING_WATER_WORK.values()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("block-pos", work.blockPosLong());
			entry.addProperty("action", work.action().id);
			entry.addProperty("queued-season", work.queuedSeasonId());
			entry.addProperty("queued-season-day", work.queuedSeasonDay());
			entry.addProperty("queued-at-tick", work.queuedAtTick());
			queue.add(entry);
		}
		return queue;
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path directory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(SEASON_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, SEASON_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JsonObject cleaned = loaded.toConfigJson();
			JsonStaticSystem.writeManagedFile(configFile, cleaned, defaults);
			settings = loaded;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load MadokuSeason static config; using defaults.", exception);
		}
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsLong();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static Long getLongObject(JsonObject object, String key, Long fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsLong();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsInt();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}

		try {
			return element.getAsDouble();
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}

		String value = element.getAsString();
		return value == null ? fallback : value;
	}

	private static String normalizeKey(String value) {
		return MadokuSeasonConfig.normalizeKey(value);
	}

	private static String normalizeClimateId(String value) {
		return MadokuSeasonConfig.normalizeClassification(value);
	}

	private static String capitalizeSeasonId(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}
		String normalized = value.trim().toLowerCase();
		return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private enum Season {
		SPRING("spring"),
		SUMMER("summer"),
		FALL("fall"),
		WINTER("winter");

		private final String id;

		Season(String id) {
			this.id = id;
		}

		private static Season fromIndex(int index) {
			Season[] values = values();
			if (index < 0 || index >= values.length) {
				return SPRING;
			}
			return values[index];
		}
	}

	public enum BiomeClimate {
		COLD("cold"),
		TEMPERATE("temperate"),
		HOT("hot");

		private final String id;

		BiomeClimate(String id) {
			this.id = id;
		}

		private static BiomeClimate fromId(String value) {
			if (value == null) {
				return null;
			}

			String normalized = value.trim().toLowerCase();
			for (BiomeClimate climate : values()) {
				if (climate.id.equals(normalized)) {
					return climate;
				}
			}
			return null;
		}
	}

	public enum BiomeMoisture {
		DRY,
		WET,
		SNOWY;
	}

	public enum SeasonalPrecipitation {
		DRY(Biome.Precipitation.NONE),
		RAIN(Biome.Precipitation.RAIN),
		SNOW(Biome.Precipitation.SNOW);

		private final Biome.Precipitation vanilla;

		SeasonalPrecipitation(Biome.Precipitation vanilla) {
			this.vanilla = vanilla;
		}

		public Biome.Precipitation vanilla() {
			return vanilla;
		}
	}

	public record SeasonState(
		long absoluteDay,
		long cycleDay,
		Season season,
		int seasonDay,
		int week,
		int dayInWeek,
		double progress
	) {
		private static SeasonState empty() {
			return new SeasonState(-1L, 0L, Season.SPRING, 0, 1, 1, 0.0d);
		}
	}

	private record BiomeClimateRecord(
		Identifier biomeId,
		BiomeClimate defaultClimate,
		BiomeClimate classification,
		float temperature,
		Biome.Precipitation precipitation
	) {
	}

	private record Settings(
		boolean enabled,
		boolean biomeOverridesEnabled,
		double coldTemperatureThreshold,
		double hotTemperatureThreshold
	) {
		private static Settings defaults() {
			return new Settings(
				true,
				true,
				MadokuSeasonConfig.DEFAULT_COLD_TEMPERATURE_THRESHOLD,
				MadokuSeasonConfig.DEFAULT_HOT_TEMPERATURE_THRESHOLD
			);
		}

		private static Settings fromJson(JsonObject source) {
			boolean enabled = getBoolean(source, MadokuSeasonConfig.FIELD_ENABLED, true);
			boolean biomeOverridesEnabled = getBoolean(source, MadokuSeasonConfig.FIELD_BIOME_OVERRIDES_ENABLED, true);
			double coldThreshold = getDouble(source, MadokuSeasonConfig.FIELD_COLD_TEMPERATURE_THRESHOLD, MadokuSeasonConfig.DEFAULT_COLD_TEMPERATURE_THRESHOLD);
			double hotThreshold = getDouble(source, MadokuSeasonConfig.FIELD_HOT_TEMPERATURE_THRESHOLD, MadokuSeasonConfig.DEFAULT_HOT_TEMPERATURE_THRESHOLD);

			if (!Double.isFinite(coldThreshold) || !Double.isFinite(hotThreshold) || coldThreshold >= hotThreshold) {
				coldThreshold = MadokuSeasonConfig.DEFAULT_COLD_TEMPERATURE_THRESHOLD;
				hotThreshold = MadokuSeasonConfig.DEFAULT_HOT_TEMPERATURE_THRESHOLD;
			}

			return new Settings(enabled, biomeOverridesEnabled, coldThreshold, hotThreshold);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty(MadokuSeasonConfig.FIELD_ENABLED, enabled);
			root.addProperty(MadokuSeasonConfig.FIELD_BIOME_OVERRIDES_ENABLED, biomeOverridesEnabled);
			root.addProperty(MadokuSeasonConfig.FIELD_COLD_TEMPERATURE_THRESHOLD, coldTemperatureThreshold);
			root.addProperty(MadokuSeasonConfig.FIELD_HOT_TEMPERATURE_THRESHOLD, hotTemperatureThreshold);
			return root;
		}
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null) {
			return fallback;
		}

		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}

		return element.getAsBoolean();
	}
}
