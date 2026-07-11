package madoku.craft.ecosystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.api.season.SeasonBiomeClimateManager;
import madoku.craft.api.season.SeasonConfigManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Chunk processor for reversible seasonal flood and drought changes. */
public final class EcosystemSeasonalChangesManager {
	private static final String PROCESSOR_ID = "ecosystem-seasonal-changes";
	private static final String SCHEDULER_OWNER_ID = "ecosystem_seasonal_changes_process_gameplay";
	private static final String TASK_TYPE = "ecosystem_seasonal_changes_process_gameplay_tick";
	private static final String FIELD_SEASONAL_ORIGINAL_BLOCKS = "seasonal-original-blocks";
	private static final String FIELD_SEASONAL_FLOOD_LEVEL = "seasonal-flood-level";
	private static final String FIELD_SEASONAL_DROUGHT_LEVEL = "seasonal-drought-level";
	private static final Map<String, OriginalBlock> ORIGINAL_BLOCKS = new LinkedHashMap<>();
	private static final Map<String, Integer> FLOOD_LEVEL_BY_CHUNK = new LinkedHashMap<>();
	private static final Map<String, Integer> DROUGHT_LEVEL_BY_CHUNK = new LinkedHashMap<>();
	private static final Map<MadokuEcosystemManager.ChunkRefKey, Map<Long, SeasonalColumn>> SEASONAL_COLUMNS_BY_CHUNK = new LinkedHashMap<>();
	private static final long MIN_INTERVAL_TICKS = 1L;
	private static final long MAX_INTERVAL_TICKS = 20L;
	private static volatile String schedulerId = "";
	private static volatile boolean taskScheduled = false;

	private static final MadokuChunkManager.ChunkProcessor PROCESSOR = new MadokuChunkManager.ChunkProcessor() {
		@Override public boolean requiresMotionColumns() { return false; }
		@Override public boolean requiresSurfaceColumns() { return true; }
		@Override public void beginLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
			MadokuEcosystemManager.beginUnifiedDiscoveryForChunk(level, chunkX, chunkZ);
		}
		@Override public void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ, MadokuChunkManager.ChunkDiscoverySnapshot snapshot) {
			MadokuEcosystemManager.runUnifiedDiscoveryForChunk(level, chunkX, chunkZ, snapshot);
		}
		@Override public void finishLoadedChunkDiscovery(ServerLevel level, int chunkX, int chunkZ) {
			MadokuEcosystemManager.finishUnifiedDiscoveryForChunk(level, chunkX, chunkZ);
		}
		@Override public void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
			if (level != null && MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)) {
				processChunk(level, chunkX, chunkZ, MadokuTimeManager.getCurrentAbsoluteDayTime(level));
			}
		}
	};

	private EcosystemSeasonalChangesManager() { }
	public static void initialize() {
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.ECOSYSTEM);
		SeasonalChangesConfigManager.initialize();
		MadokuChunkManager.registerChunkProcessor(PROCESSOR_ID, PROCESSOR);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE, EcosystemSeasonalChangesManager::runTask);
		emitLifecycleDebug("initialize", builder -> builder
			.field("processor-id", PROCESSOR_ID)
			.field("enabled", isEnabled()));
	}
	public static void reset() {
		ORIGINAL_BLOCKS.clear();
		FLOOD_LEVEL_BY_CHUNK.clear();
		DROUGHT_LEVEL_BY_CHUNK.clear();
		SEASONAL_COLUMNS_BY_CHUNK.clear();
		SchedulerManagerSystem.clearAdaptiveDelayState(SCHEDULER_OWNER_ID);
		clearSchedulerState();
		MadokuChunkManager.resetChunkProcessor(PROCESSOR_ID);
		emitLifecycleDebug("reset", builder -> builder.field("processor-id", PROCESSOR_ID));
	}
	public static boolean isEnabled() {
		SeasonalChangesConfigManager.Settings settings = SeasonalChangesConfigManager.getSettings();
		return MadokuEcosystemManager.isEnabled() && MadokuSeasonManager.isEnabled()
			&& (settings.droughtEnabled() || settings.floodEnabled());
	}
	public static void syncChunkProcessorActivation() {
		boolean enabled = isEnabled();
		MadokuChunkManager.setChunkProcessorActive(PROCESSOR_ID, enabled);
		emitLifecycleDebug("processor-activation", builder -> builder.field("processor-id", PROCESSOR_ID).field("enabled", enabled));
	}
	public static void onServerStarted(MinecraftServer server) {
		if (server == null) return;
		SchedulerManagerSystem.clearAdaptiveDelayState(SCHEDULER_OWNER_ID);
		MadokuChunkManager.resetChunkProcessor(PROCESSOR_ID);
		if (!isEnabled()) {
			clearSchedulerState();
			return;
		}
		clearSchedulerState();
		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(SCHEDULER_OWNER_ID));
		SchedulerManagerSystem.clearQueuedRequests(schedulerId);
		syncChunkProcessorActivation();
		requestProcessing(server, 1L);
		emitLifecycleDebug("server-started", builder -> builder
			.field("scheduler-id", schedulerId)
			.field("task-scheduled", taskScheduled)
			.field("enabled", isEnabled()));
	}
	public static void onServerStopping(MinecraftServer server) {
		if (server == null) return;
		SchedulerManagerSystem.clearAdaptiveDelayState(SCHEDULER_OWNER_ID);
		emitLifecycleDebug("server-stopping", builder -> builder
			.field("scheduler-id", schedulerId)
			.field("task-scheduled", taskScheduled));
		clearSchedulerState();
	}

	static void applyPersistedData(JsonObject data) {
		if (data == null) return;
		String levelId = string(data, "level-id");
		int chunkX = integer(data, "chunk-x", Integer.MIN_VALUE);
		int chunkZ = integer(data, "chunk-z", Integer.MIN_VALUE);
		if (levelId.isBlank() || chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) return;
		String chunkKey = chunkKey(levelId, chunkX, chunkZ);
		int seaLevel = SeasonalChangesConfigManager.getSettings().seaLevel();
		JsonElement entries = data.get(FIELD_SEASONAL_ORIGINAL_BLOCKS);
		if (entries instanceof JsonArray array) for (JsonElement element : array) if (element.isJsonObject()) {
			JsonObject item = element.getAsJsonObject(); String key = string(item, "key"); String kind = string(item, "kind");
			if (!key.isBlank()) ORIGINAL_BLOCKS.put(key, new OriginalBlock(kind, booleanValue(item, "flood-origin", false)));
		}
		if (data.has(FIELD_SEASONAL_FLOOD_LEVEL)) FLOOD_LEVEL_BY_CHUNK.put(chunkKey, integer(data, FIELD_SEASONAL_FLOOD_LEVEL, seaLevel));
		if (data.has(FIELD_SEASONAL_DROUGHT_LEVEL)) DROUGHT_LEVEL_BY_CHUNK.put(chunkKey, integer(data, FIELD_SEASONAL_DROUGHT_LEVEL, seaLevel));
	}
	static java.util.Set<MadokuEcosystemManager.ChunkRefKey> collectTrackedChunkKeys() {
		java.util.LinkedHashSet<MadokuEcosystemManager.ChunkRefKey> keys = new java.util.LinkedHashSet<>();
		FLOOD_LEVEL_BY_CHUNK.keySet().forEach(key -> { MadokuEcosystemManager.ChunkRefKey parsed = parseChunkKey(key); if (parsed != null) keys.add(parsed); });
		DROUGHT_LEVEL_BY_CHUNK.keySet().forEach(key -> { MadokuEcosystemManager.ChunkRefKey parsed = parseChunkKey(key); if (parsed != null) keys.add(parsed); });
		ORIGINAL_BLOCKS.keySet().forEach(key -> { TrackedBlock parsed = parse(key); if (parsed != null) keys.add(new MadokuEcosystemManager.ChunkRefKey(parsed.levelId(), parsed.pos().getX() >> 4, parsed.pos().getZ() >> 4)); });
		return keys;
	}
	static void discoverTrackablesInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuChunkManager.ChunkDiscoverySnapshot snapshot,
		MadokuEcosystemManager.ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || snapshot == null || accumulator == null || !isEnabled()) return;
		SeasonalChangesConfigManager.Settings config = SeasonalChangesConfigManager.getSettings();
		int seaLevel = config.seaLevel();
		for (MadokuChunkManager.ColumnSample column : snapshot.surfaceColumns()) {
			if (column == null || !column.hasDepth(0)) continue;
			int x = column.worldX();
			int z = column.worldZ();
			BlockPos climatePos = new BlockPos(x, Math.min(world.getMaxY() - 1, seaLevel), z);
			SeasonBiomeClimateManager.Climate climate = MadokuSeasonManager.resolveBiomeClimate(world, climatePos);
			String biomeId = world.getBiome(climatePos).unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
			boolean ocean = biomeId.contains(":ocean") || biomeId.endsWith("ocean");
			boolean droughtEligible = !ocean && config.droughtEnabled() && climate.humidity() <= 20 && climate.temperature() > 20;
			boolean floodEligible = !ocean && config.floodEnabled() && climate.humidity() >= 80 && climate.temperature() < 80;
			if (droughtEligible || floodEligible) {
				accumulator.seasonalColumns.put(BlockPos.asLong(x, 0, z), new SeasonalColumn(x, z, biomeId, climate.temperature(), climate.humidity(), droughtEligible, floodEligible));
			}
		}
	}

	static void finalizeTrackablesInChunkDiscovery(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		MadokuEcosystemManager.ChunkDiscoveryAccumulator accumulator
	) {
		if (world == null || accumulator == null || !isEnabled()) return;
		MadokuEcosystemManager.ChunkRefKey chunkKey = new MadokuEcosystemManager.ChunkRefKey(
			MadokuEcosystemManager.levelId(world), chunkX, chunkZ);
		if (accumulator.seasonalColumns.isEmpty()) {
			SEASONAL_COLUMNS_BY_CHUNK.remove(chunkKey);
		} else {
			SEASONAL_COLUMNS_BY_CHUNK.put(chunkKey, new LinkedHashMap<>(accumulator.seasonalColumns));
		}
		syncChunkProcessorTracking(chunkKey);
	}

	private static void syncChunkProcessorTracking(MadokuEcosystemManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) return;
		Map<Long, SeasonalColumn> columns = SEASONAL_COLUMNS_BY_CHUNK.get(chunkKey);
		boolean tracked = isEnabled() && columns != null && !columns.isEmpty();
		if (tracked) {
			MadokuChunkManager.trackChunkForProcessor(PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		} else {
			MadokuChunkManager.untrackChunkForProcessor(PROCESSOR_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}
	}
	static JsonObject createChunkPersistedData(MadokuEcosystemManager.ChunkRefKey chunk) {
		if (chunk == null) return null;
		String stateKey = chunkKey(chunk.levelId(), chunk.chunkX(), chunk.chunkZ());
		JSONFormatManager.ArrayBuilder originals = JSONFormatManager.array();
		for (Map.Entry<String, OriginalBlock> entry : ORIGINAL_BLOCKS.entrySet()) {
			TrackedBlock tracked = parse(entry.getKey());
			if (tracked != null && tracked.levelId().equals(chunk.levelId()) && (tracked.pos().getX() >> 4) == chunk.chunkX() && (tracked.pos().getZ() >> 4) == chunk.chunkZ()) {
				originals.object(o -> o.put("key", entry.getKey()).put("kind", entry.getValue().kind()).put("flood-origin", entry.getValue().floodOrigin()));
			}
		}
		boolean hasOriginals = !originals.build().getAsJsonArray().isEmpty();
		boolean hasFloodLevel = FLOOD_LEVEL_BY_CHUNK.containsKey(stateKey);
		boolean hasDroughtLevel = DROUGHT_LEVEL_BY_CHUNK.containsKey(stateKey);
		if (!hasOriginals && !hasFloodLevel && !hasDroughtLevel) return null;
		JSONFormatManager.ObjectBuilder builder = JSONFormatManager.object()
			.put(FIELD_SEASONAL_ORIGINAL_BLOCKS, originals.build());
		if (hasFloodLevel) builder.put(FIELD_SEASONAL_FLOOD_LEVEL, FLOOD_LEVEL_BY_CHUNK.get(stateKey));
		if (hasDroughtLevel) builder.put(FIELD_SEASONAL_DROUGHT_LEVEL, DROUGHT_LEVEL_BY_CHUNK.get(stateKey));
		return builder.build();
	}
	public static MadokuChunkManager.ChunkProcessor getChunkProcessor() { return PROCESSOR; }
	public static void runTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) schedulerId = context.getSchedulerId();
		taskScheduled = false;
		if (server == null || !isEnabled()) return;
		requestProcessing(server, resolveSchedulerInterval(server));
		MadokuChunkManager.runChunkProcessorProcessingStep(server, PROCESSOR_ID);
		emitLifecycleDebug("scheduler-task", builder -> builder.field("scheduler-id", schedulerId).field("processor-id", PROCESSOR_ID));
	}
	public static void requestProcessing(MinecraftServer server, long delayTicks) {
		if (server == null || !isEnabled()) return;
		String currentSchedulerId = ensureSchedulerExists();
		boolean queuedBefore = isTaskQueued(currentSchedulerId);
		if (taskScheduled && queuedBefore) return;
		taskScheduled = false;
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			currentSchedulerId, Math.max(0L, delayTicks), TASK_TYPE, new JsonObject(), SchedulerManagerSystem.TickDomain.GAMEPLAY);
		if (!isAccepted(status)) {
			String refreshedSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(SCHEDULER_OWNER_ID));
			schedulerId = refreshedSchedulerId;
			status = SchedulerManagerSystem.enqueue(
				refreshedSchedulerId, Math.max(0L, delayTicks), TASK_TYPE, new JsonObject(), SchedulerManagerSystem.TickDomain.GAMEPLAY);
		}
		if (isAccepted(status)) taskScheduled = true;
		emitLifecycleDebug("request-processing", builder -> builder
			.field("scheduler-id", currentSchedulerId)
			.field("delay-ticks", delayTicks)
			.field("queued-before", queuedBefore)
			.field("accepted", taskScheduled));
	}
	private static long resolveSchedulerInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(server, SCHEDULER_OWNER_ID, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
	}
	private static String ensureSchedulerExists() {
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(SCHEDULER_OWNER_ID));
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

	private static void processChunk(ServerLevel level, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (level == null || !MadokuChunkManager.isChunkLoaded(level, chunkX, chunkZ)) return;
		SeasonalChangesConfigManager.Settings config = SeasonalChangesConfigManager.getSettings();
		int seaLevel = config.seaLevel();
		long currentDay = Math.max(0L, MadokuTimeManager.getDay(currentAbsoluteDayTime));
		int seasonLengthDays = SeasonConfigManager.getSettings().seasonLengthDays();
		int seasonDay = (int) Math.floorMod(currentDay, seasonLengthDays);
		int adjustmentWindowDays = Math.max(1, seasonLengthDays / 2);
		String chunkKey = chunkKey(level, chunkX, chunkZ);
		int floodLevel = FLOOD_LEVEL_BY_CHUNK.getOrDefault(chunkKey, seaLevel);
		int droughtLevel = DROUGHT_LEVEL_BY_CHUNK.getOrDefault(chunkKey, seaLevel);
		boolean floodDue = floodLevel < level.getMaxY() - 1
			&& transitionDue(seasonDay, floodLevel - seaLevel, config.floodTimeRateDays(), adjustmentWindowDays);
		boolean droughtDue = droughtLevel > level.getMinY()
			&& transitionDue(seasonDay, seaLevel - droughtLevel, config.droughtTimeRateDays(), adjustmentWindowDays);
		boolean flooded = false;
		boolean dried = false;
		Map<Long, SeasonalColumn> seasonalColumns = SEASONAL_COLUMNS_BY_CHUNK.getOrDefault(
			new MadokuEcosystemManager.ChunkRefKey(MadokuEcosystemManager.levelId(level), chunkX, chunkZ), Map.of());
		boolean hasFloodEligibleColumn = config.floodEnabled()
			&& seasonalColumns.values().stream().anyMatch(SeasonalColumn::floodEligible);
		boolean hasDroughtEligibleColumn = config.droughtEnabled()
			&& seasonalColumns.values().stream().anyMatch(SeasonalColumn::droughtEligible);
		boolean floodRestored = false;
		boolean droughtRestored = false;
		boolean floodSpilloverCleared = false;
		for (SeasonalColumn column : seasonalColumns.values()) {
			int x = column.x();
			int z = column.z();
			boolean droughtEligible = config.droughtEnabled() && column.droughtEligible();
			boolean floodEligible = config.floodEnabled() && column.floodEligible();
			String action = droughtEligible && droughtDue ? "drought" : floodEligible && floodDue ? "flood" : "none";
			emitDecisionDebug(level, chunkX, chunkZ, x, z, column.biomeId(), column.temperature(), column.humidity(), false,
				droughtEligible, droughtDue, droughtLevel, floodEligible, floodDue, floodLevel, action);
			if (droughtEligible && droughtDue) {
				applyDrought(level, x, z, droughtLevel, config.droughtDepthRate());
				dried = true;
			}
			if (floodEligible && floodDue) {
				applyFlood(level, x, z, floodLevel, config.floodDepthRate(), seaLevel);
				flooded = true;
			}
			if (floodEligible && (flooded || floodLevel > seaLevel)) {
				floodSpilloverCleared |= clearFloodSpilloverBelowSeaLevel(level, x, z, seaLevel);
			}
		}
		if (!hasFloodEligibleColumn) {
			boolean floodLevelRemoved = FLOOD_LEVEL_BY_CHUNK.remove(chunkKey) != null;
			floodRestored = restoreFloodTrackedChunk(level, chunkX, chunkZ) || floodLevelRemoved;
		} 
		if (!hasDroughtEligibleColumn) {
			boolean droughtLevelRemoved = DROUGHT_LEVEL_BY_CHUNK.remove(chunkKey) != null;
			droughtRestored = restoreDroughtTrackedChunk(level, chunkX, chunkZ) || droughtLevelRemoved;
		}
		if (flooded) {
			FLOOD_LEVEL_BY_CHUNK.put(chunkKey, Math.min(level.getMaxY() - 1, floodLevel + config.floodDepthRate()));
		}
		if (dried) {
			DROUGHT_LEVEL_BY_CHUNK.put(chunkKey, Math.max(level.getMinY(), droughtLevel - config.droughtDepthRate()));
		}
		if (flooded || dried || floodRestored || droughtRestored || floodSpilloverCleared) MadokuEcosystemManager.markChunkDirty(level, chunkX, chunkZ);
		emitLifecycleDebug("processing-step", builder -> builder
			.field("day", currentDay)
			.field("season-day", seasonDay)
			.field("season-length-days", seasonLengthDays)
			.field("adjustment-window-days", adjustmentWindowDays)
			.field("processor-id", PROCESSOR_ID));
		emitProcessingDebug(level, chunkX, chunkZ,
			FLOOD_LEVEL_BY_CHUNK.getOrDefault(chunkKey, seaLevel),
			DROUGHT_LEVEL_BY_CHUNK.getOrDefault(chunkKey, seaLevel), flooded, dried);
	}

	private static void applyDrought(ServerLevel level, int x, int z, int levelY, int amount) {
		for (int i = 0; i < amount; i++) {
			int targetY = levelY - i;
			if (targetY < level.getMinY()) return;
			BlockPos target = new BlockPos(x, targetY, z);
			BlockState current = level.getBlockState(target);
			if (current.hasProperty(BlockStateProperties.WATERLOGGED)
				&& current.getValue(BlockStateProperties.WATERLOGGED)) {
				remember(level, target, current, false);
				level.setBlock(target, current.setValue(BlockStateProperties.WATERLOGGED, false), 3);
			} else if (current.getFluidState().is(FluidTags.WATER)) {
				remember(level, target, current, false);
				level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
			} else if (current.is(Blocks.ICE)) {
				remember(level, target, current, false);
				level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}
	private static void applyFlood(ServerLevel level, int x, int z, int currentLevel, int amount, int seaLevel) {
		rememberFloodBand(level, x, z, currentLevel, amount, seaLevel);
		for (int i = 1; i <= amount; i++) {
			int targetY = currentLevel + i;
			if (targetY >= level.getMaxY()) return;
			BlockPos target = new BlockPos(x, targetY, z);
			BlockState current = level.getBlockState(target);
			if (current.isAir() || (current.getFluidState().is(FluidTags.WATER) && !current.getFluidState().isSource())) {
				remember(level, target, current, true);
				level.setBlock(target, Blocks.WATER.defaultBlockState(), 3);
			} else if (current.hasProperty(BlockStateProperties.WATERLOGGED)
				&& !current.getValue(BlockStateProperties.WATERLOGGED)) {
				remember(level, target, current, true);
				level.setBlock(target, current.setValue(BlockStateProperties.WATERLOGGED, true), 3);
			}
		}
	}
	private static boolean transitionDue(int seasonDay, int completedAdjustments, int rateDays, int adjustmentWindowDays) {
		int nextAdjustmentDay = (completedAdjustments + 1) * Math.max(1, rateDays);
		return nextAdjustmentDay <= adjustmentWindowDays && seasonDay >= nextAdjustmentDay;
	}
	private static boolean restoreFloodTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
		return restoreTrackedChunk(level, chunkX, chunkZ, true);
	}
	private static boolean restoreDroughtTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
		return restoreTrackedChunk(level, chunkX, chunkZ, false);
	}
	private static boolean restoreTrackedChunk(ServerLevel level, int chunkX, int chunkZ, boolean floodOrigin) {
		boolean[] restored = {false};
		ORIGINAL_BLOCKS.entrySet().removeIf(entry -> {
			if (entry.getValue().floodOrigin() != floodOrigin) return false;
			TrackedBlock tracked = parse(entry.getKey());
			if (tracked == null || !tracked.levelId().equals(MadokuChunkManager.normalizeLevelId(level))
				|| (tracked.pos().getX() >> 4) != chunkX || (tracked.pos().getZ() >> 4) != chunkZ) return false;
			restored[0] |= restoreOriginalBlock(level, tracked.pos(), entry.getValue());
			return true;
		});
		return restored[0];
	}
	private static boolean restoreOriginalBlock(ServerLevel level, BlockPos pos, OriginalBlock original) {
		BlockState current = level.getBlockState(pos);
		if (original.floodOrigin() && !isFloodState(current)) return false;
		if (original.kind().equals("air") && !current.isAir()) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); return true; }
		else if (original.kind().equals("water") && !current.getFluidState().is(FluidTags.WATER)) { level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3); return true; }
		else if (original.kind().equals("flowing-water")) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); return true; }
		else if (original.kind().equals("ice")) { level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3); return true; }
		else if (original.kind().equals("waterlogged") && current.hasProperty(BlockStateProperties.WATERLOGGED)) { level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, true), 3); return true; }
		else if (original.kind().equals("waterloggable") && current.hasProperty(BlockStateProperties.WATERLOGGED)) { level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, false), 3); return true; }
		return false;
	}
	private static void rememberFloodBand(ServerLevel level, int x, int z, int currentLevel, int amount, int seaLevel) {
		int maximumTargetY = Math.min(level.getMaxY() - 1, Math.max(seaLevel + 1, currentLevel + amount));
		for (int targetY = Math.max(level.getMinY(), seaLevel - 2); targetY <= maximumTargetY; targetY++) {
			if (targetY < level.getMinY() || targetY >= level.getMaxY()) continue;
			BlockPos target = new BlockPos(x, targetY, z);
			BlockState state = level.getBlockState(target);
			if (state.isAir() || isFloodState(state) || state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				remember(level, target, state, true);
			}
		}
	}
	private static boolean clearFloodSpilloverBelowSeaLevel(ServerLevel level, int x, int z, int seaLevel) {
		boolean cleared = false;
		for (int targetY = Math.max(level.getMinY(), seaLevel - 2); targetY <= seaLevel; targetY++) {
			BlockPos target = new BlockPos(x, targetY, z);
			OriginalBlock original = ORIGINAL_BLOCKS.get(key(level, target));
			if (original == null || !original.floodOrigin() || !isFloodState(level.getBlockState(target))) continue;
			cleared |= restoreOriginalBlock(level, target, original);
		}
		return cleared;
	}
	private static boolean isFloodState(BlockState state) {
		return state != null && (state.getFluidState().is(FluidTags.WATER)
			|| state.is(Blocks.ICE)
			|| (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)));
	}
	private static void remember(ServerLevel level, BlockPos pos, BlockState state, boolean floodOrigin) {
		String kind = state != null && state.hasProperty(BlockStateProperties.WATERLOGGED)
			&& state.getValue(BlockStateProperties.WATERLOGGED) ? "waterlogged"
			: state != null && state.hasProperty(BlockStateProperties.WATERLOGGED) ? "waterloggable"
			: state != null && state.isAir() ? "air"
			: state != null && state.is(Blocks.ICE) ? "ice"
			: state != null && state.getFluidState().is(FluidTags.WATER) && !state.getFluidState().isSource() ? "flowing-water" : "water";
		ORIGINAL_BLOCKS.putIfAbsent(key(level, pos), new OriginalBlock(kind, floodOrigin));
	}
	private static String key(ServerLevel level, BlockPos pos) { return MadokuChunkManager.normalizeLevelId(level) + "|" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ(); }
	private static String chunkKey(ServerLevel level, int chunkX, int chunkZ) { return MadokuChunkManager.normalizeLevelId(level) + ":" + chunkX + ":" + chunkZ; }
	private static String chunkKey(String levelId, int chunkX, int chunkZ) { return levelId + ":" + chunkX + ":" + chunkZ; }
	private static TrackedBlock parse(String key) {
		try {
			int separator = key.indexOf('|');
			if (separator <= 0) return null;
			String[] p = key.substring(separator + 1).split(":");
			return p.length == 3 ? new TrackedBlock(key.substring(0, separator), new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]))) : null;
		} catch (RuntimeException e) { return null; }
	}
	private static MadokuEcosystemManager.ChunkRefKey parseChunkKey(String key) {
		try {
			int last = key.lastIndexOf(':');
			int previous = key.lastIndexOf(':', last - 1);
			if (previous <= 0 || last <= previous) return null;
			return new MadokuEcosystemManager.ChunkRefKey(key.substring(0, previous), Integer.parseInt(key.substring(previous + 1, last)), Integer.parseInt(key.substring(last + 1)));
		} catch (RuntimeException e) { return null; }
	}
	private static String string(JsonObject o, String key) { try { return o != null && o.has(key) ? o.get(key).getAsString() : ""; } catch (RuntimeException e) { return ""; } }
	private static int integer(JsonObject o, String key, int fallback) { try { return o != null && o.has(key) ? o.get(key).getAsInt() : fallback; } catch (RuntimeException e) { return fallback; } }
	private static boolean booleanValue(JsonObject o, String key, boolean fallback) { try { return o != null && o.has(key) ? o.get(key).getAsBoolean() : fallback; } catch (RuntimeException e) { return fallback; } }
	private static void emitLifecycleDebug(String subject, java.util.function.Consumer<MadokuDebugManager.EventBuilder> customizer) {
		MadokuDebugManager.EventBuilder builder = MadokuDebugManager.event(
			"ecosystem.seasonal-changes.lifecycle",
			MadokuMetaDataManager.ECOSYSTEM.mainSystem(),
			"ecosystem-seasonal-changes-manager",
			"lifecycle",
			"state"
		).side(MadokuDebugManager.Side.SERVER).subject(subject);
		if (customizer != null) customizer.accept(builder);
		builder.log();
	}
	private static void emitProcessingDebug(ServerLevel level, int chunkX, int chunkZ, int floodLevel, int droughtLevel, boolean flooded, boolean dried) {
		MadokuDebugManager.event(
			"ecosystem.seasonal-changes.chunk",
			MadokuMetaDataManager.ECOSYSTEM.mainSystem(),
			"ecosystem-seasonal-changes-manager",
			"processing",
			"chunk"
		).side(MadokuDebugManager.Side.SERVER)
			.world(MadokuChunkManager.normalizeLevelId(level))
			.subject("process")
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("flood-level", floodLevel)
			.field("drought-level", droughtLevel)
			.field("flooded", flooded)
			.field("dried", dried)
			.log();
	}
	private static void emitDecisionDebug(ServerLevel level, int chunkX, int chunkZ, int x, int z, String biomeId,
		int temperature, int humidity, boolean ocean, boolean droughtEligible, boolean droughtDue, int droughtLevel,
		boolean floodEligible, boolean floodDue, int floodLevel, String action) {
		if (!MadokuDebugManager.shouldEmit(
			MadokuMetaDataManager.ECOSYSTEM.mainSystem(),
			"ecosystem-seasonal-changes-manager",
			"processing",
			"decision")) return;
		MadokuDebugManager.event(
			"ecosystem.seasonal-changes.decision",
			MadokuMetaDataManager.ECOSYSTEM.mainSystem(),
			"ecosystem-seasonal-changes-manager",
			"processing",
			"decision"
		).side(MadokuDebugManager.Side.SERVER)
			.tick(MadokuTimeManager.getGameplayTicks())
			.world(MadokuChunkManager.normalizeLevelId(level))
			.subject(action)
			.field("chunk-x", chunkX)
			.field("chunk-z", chunkZ)
			.field("column-x", x)
			.field("column-z", z)
			.field("biome", biomeId)
			.field("temperature", temperature)
			.field("humidity", humidity)
			.field("ocean", ocean)
			.field("drought-eligible", droughtEligible)
			.field("drought-due", droughtDue)
			.field("drought-level", droughtLevel)
			.field("flood-eligible", floodEligible)
			.field("flood-due", floodDue)
			.field("flood-level", floodLevel)
			.log();
	}
	private record OriginalBlock(String kind, boolean floodOrigin) { }
	private record TrackedBlock(String levelId, BlockPos pos) { }
	static record SeasonalColumn(int x, int z, String biomeId, int temperature, int humidity, boolean droughtEligible, boolean floodEligible) { }
}
