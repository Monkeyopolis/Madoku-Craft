package madoku.craft.java.utility.smelting;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.scheduler.SchedulerAPIManager;
import madoku.craft.mixin.utility.smelting.AbstractFurnaceServerTickInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Runtime subsystem that applies the configured smelting behavior. */
public final class MadokuSmeltingManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuSmeltingManager.class);
	private static final String TASK_TYPE_SMELTING_TICK = "utility_smelting_furnace_tick";
	private static final String ADAPTIVE_INTERVAL_OWNER = "utility-smelting";
	private static final int MINIMUM_COOK_TICKS = 20;
	private static final int BASE_FURNACE_COOK_TICKS = 200;
	private static final int BASE_SMOKER_COOK_TICKS = 100;
	private static final int BASE_BLAST_FURNACE_COOK_TICKS = 100;

	private static volatile Map<BlockEntityType<?>, FurnaceBehavior> behaviorByBlockEntityType = Map.of();
	private static volatile Map<RecipeType<?>, FurnaceBehavior> behaviorByRecipeType = Map.of();
	private static final Map<FurnaceKey, String> schedulerIds = new HashMap<>();
	private static final Set<FurnaceKey> scheduledFurnaces = new HashSet<>();
	private static final Set<FurnaceKey> advancingFurnaces = new HashSet<>();
	private static final Map<FurnaceKey, Long> lastProcessedWorldTime = new HashMap<>();
	private static final Map<FurnaceKey, Long> lastProcessedGameTime = new HashMap<>();

	private MadokuSmeltingManager() {
	}

	public static void initialize() {
		SchedulerAPIManager.registerTaskHandler(TASK_TYPE_SMELTING_TICK, MadokuSmeltingManager::runScheduledFurnaceTask);
		resetRuntimeState();
		rebuildRules(SmeltingConfigManager.getSettings());
	}

	public static void reset() {
		resetRuntimeState();
		behaviorByBlockEntityType = Map.of();
		behaviorByRecipeType = Map.of();
		SmeltingConfigManager.reset();
	}

	public static boolean isEnabled() {
		SmeltingConfigManager.Settings settings = SmeltingConfigManager.getSettings();
		return settings.enabled() && settings.hasEnabledGroup();
	}

	public static int getCookTimeTicks(AbstractFurnaceBlockEntity furnace, int originalTicks) {
		if (!isEnabled() || furnace == null) return originalTicks;
		FurnaceBehavior behavior = behaviorByBlockEntityType.get(furnace.getType());
		return behavior == null ? originalTicks : Math.max(MINIMUM_COOK_TICKS, toTicks(behavior.smeltingSpeed()));
	}

	public static int getCookTimeTicks(RecipeType<?> recipeType, int originalTicks) {
		if (!isEnabled() || recipeType == null) return originalTicks;
		FurnaceBehavior behavior = behaviorByRecipeType.get(recipeType);
		return behavior == null ? originalTicks : Math.max(MINIMUM_COOK_TICKS, toTicks(behavior.smeltingSpeed()));
	}

	public static int getAdjustedFuelTicks(AbstractFurnaceBlockEntity furnace, ItemStack stack, int originalTicks) {
		if (!isEnabled() || furnace == null || stack == null || stack.isEmpty() || originalTicks <= 0) return originalTicks;
		FurnaceBehavior behavior = behaviorByBlockEntityType.get(furnace.getType());
		if (behavior == null) return originalTicks;
		double speedFactor = behavior.smeltingSpeed() / getBaseCookTicks(furnace);
		return Math.max(1, toTicks(originalTicks * speedFactor * behavior.fuelEfficiency()));
	}

	public static void onServerStarted(MinecraftServer server) {
		resetRuntimeState();
	}

	public static void onServerStopped(MinecraftServer server) {
		resetRuntimeState();
		MadokuUtilityManager.clearAdaptiveInterval(ADAPTIVE_INTERVAL_OWNER);
	}

	public static void onFurnaceServerTick(ServerLevel level, BlockPos blockPos, BlockState blockState, AbstractFurnaceBlockEntity furnace) {
		if (!isEnabled() || level == null || blockPos == null || furnace == null || !behaviorByBlockEntityType.containsKey(furnace.getType())) return;
		MinecraftServer server = level.getServer();
		FurnaceKey key = FurnaceKey.from(level, blockPos);
		if (server == null || key == null) return;
		if (!shouldTrackFurnace(furnace, blockState)) {
			lastProcessedWorldTime.remove(key);
			lastProcessedGameTime.remove(key);
			return;
		}
		if (advancingFurnaces.contains(key)) {
			return;
		}

		// This hook runs after vanilla has processed the current real furnace tick.
		// Use per-furnace absolute clocks so command/sleep jumps cannot be lost while
		// an adaptive scheduler task is waiting in the queue.
		long currentWorldTime = level.getOverworldClockTime();
		long previousWorldTime = lastProcessedWorldTime.getOrDefault(key, currentWorldTime);
		long currentGameTime = level.getGameTime();
		long previousGameTime = lastProcessedGameTime.getOrDefault(key, currentGameTime);
		long worldTimeDelta = Math.max(0L, currentWorldTime - previousWorldTime);
		long gameTimeDelta = Math.max(0L, currentGameTime - previousGameTime);
		lastProcessedWorldTime.put(key, currentWorldTime);
		lastProcessedGameTime.put(key, currentGameTime);
		advanceFurnaceTicks(level, blockPos, Math.max(0L, worldTimeDelta - gameTimeDelta));
		requestFurnaceProcessing(server, key);
	}

	private static void runScheduledFurnaceTask(MinecraftServer server, SchedulerAPIManager.TaskContext context, JsonObject payload) {
		if (server == null || context == null || !isEnabled()) return;
		FurnaceKey key = FurnaceKey.from(context.getBinding());
		if (key == null) return;
		schedulerIds.put(key, context.getSchedulerId());
		scheduledFurnaces.remove(key);
		ServerLevel level = resolveLevel(server, key.levelId());
		if (level == null) {
			clearFurnaceRuntimeState(key);
			return;
		}
		BlockPos blockPos = BlockPos.of(key.blockPosLong());
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
			clearFurnaceRuntimeState(key);
			return;
		}

		// The normal furnace server-tick hook processes forwarded time immediately.
		// This task remains as a fallback for a furnace whose hook was delayed.
		long currentWorldTime = level.getOverworldClockTime();
		long previousWorldTime = lastProcessedWorldTime.getOrDefault(key, currentWorldTime);
		long currentGameTime = level.getGameTime();
		long previousGameTime = lastProcessedGameTime.getOrDefault(key, currentGameTime);
		long worldTimeDelta = Math.max(0L, currentWorldTime - previousWorldTime);
		long gameTimeDelta = Math.max(0L, currentGameTime - previousGameTime);
		lastProcessedWorldTime.put(key, currentWorldTime);
		lastProcessedGameTime.put(key, currentGameTime);
		advanceFurnaceTicks(level, blockPos, Math.max(0L, worldTimeDelta - gameTimeDelta));

		if (!shouldTrackFurnace(furnace, level.getBlockState(blockPos))) {
			lastProcessedWorldTime.remove(key);
			lastProcessedGameTime.remove(key);
		}
	}

	private static void requestFurnaceProcessing(MinecraftServer server, FurnaceKey key) {
		if (key == null || scheduledFurnaces.contains(key)) return;
		String schedulerId = schedulerIds.computeIfAbsent(key, value -> SchedulerAPIManager.createOrGetScheduler(value.toBinding()));
		long delay = MadokuUtilityManager.resolveAdaptiveInterval(server, ADAPTIVE_INTERVAL_OWNER, 1L, 20L);
		SchedulerAPIManager.EnqueueStatus status = SchedulerAPIManager.enqueue(
			schedulerId, Math.max(0L, delay), TASK_TYPE_SMELTING_TICK, new JsonObject(), SchedulerAPIManager.TickDomain.GAMEPLAY
		);
		if (status == SchedulerAPIManager.EnqueueStatus.ACCEPTED || status == SchedulerAPIManager.EnqueueStatus.QUEUE_FULL) {
			scheduledFurnaces.add(key);
		} else {
			LOGGER.error("Failed to enqueue smelting scheduler task for furnace={} @ {}", key.levelId(), key.blockPosLong());
		}
	}

	private static void advanceFurnaceTicks(ServerLevel level, BlockPos blockPos, long extraTicks) {
		FurnaceKey key = FurnaceKey.from(level, blockPos);
		if (key == null || extraTicks <= 0L || !advancingFurnaces.add(key)) {
			return;
		}
		try {
			for (long step = 0L; step < extraTicks; step++) {
				BlockEntity blockEntity = level.getBlockEntity(blockPos);
				if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) break;
				AbstractFurnaceServerTickInvoker.madokuCraft$invokeServerTick(level, blockPos, level.getBlockState(blockPos), furnace);
			}
		} finally {
			advancingFurnaces.remove(key);
		}
	}

	private static boolean shouldTrackFurnace(AbstractFurnaceBlockEntity furnace, BlockState state) {
		if (furnace == null || state == null || !behaviorByBlockEntityType.containsKey(furnace.getType())) return false;
		boolean lit = state.hasProperty(BlockStateProperties.LIT) && Boolean.TRUE.equals(state.getValue(BlockStateProperties.LIT));
		return lit || (!furnace.getItem(0).isEmpty() && !furnace.getItem(1).isEmpty());
	}

	private static void rebuildRules(SmeltingConfigManager.Settings settings) {
		Map<BlockEntityType<?>, FurnaceBehavior> byBlockEntityType = new LinkedHashMap<>();
		Map<RecipeType<?>, FurnaceBehavior> byRecipeType = new LinkedHashMap<>();
		addRule(settings.enabled(), settings.furnace(), RecipeType.SMELTING, byBlockEntityType, byRecipeType);
		addRule(settings.enabled(), settings.blastFurnace(), RecipeType.BLASTING, byBlockEntityType, byRecipeType);
		addRule(settings.enabled(), settings.smoker(), RecipeType.SMOKING, byBlockEntityType, byRecipeType);
		behaviorByBlockEntityType = Map.copyOf(byBlockEntityType);
		behaviorByRecipeType = Map.copyOf(byRecipeType);
	}

	private static void addRule(boolean systemEnabled, SmeltingConfigManager.FurnaceSettings settings, RecipeType<?> recipeType,
		Map<BlockEntityType<?>, FurnaceBehavior> byBlockEntityType, Map<RecipeType<?>, FurnaceBehavior> byRecipeType) {
		if (!systemEnabled || settings == null || !settings.enabled()) return;
		Identifier id = Identifier.tryParse(JSONAPIManager.normalizeRegistryIdentifierForLookup(settings.blockId()));
		if (id == null || !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) return;
		FurnaceBehavior behavior = new FurnaceBehavior(settings.smeltingSpeed(), settings.fuelEfficiency());
		byBlockEntityType.put(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id), behavior);
		byRecipeType.put(recipeType, behavior);
	}

	private static ServerLevel resolveLevel(MinecraftServer server, String levelId) {
		Identifier id = Identifier.tryParse(levelId);
		if (id == null) id = Identifier.tryParse(SchedulerAPIManager.normalizeLevelIdentifier(levelId));
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	private static void clearFurnaceRuntimeState(FurnaceKey key) {
		scheduledFurnaces.remove(key);
		advancingFurnaces.remove(key);
		schedulerIds.remove(key);
		lastProcessedWorldTime.remove(key);
		lastProcessedGameTime.remove(key);
	}

	private static void resetRuntimeState() {
		schedulerIds.clear();
		scheduledFurnaces.clear();
		advancingFurnaces.clear();
		lastProcessedWorldTime.clear();
		lastProcessedGameTime.clear();
	}

	private static int getBaseCookTicks(AbstractFurnaceBlockEntity furnace) {
		if (furnace instanceof SmokerBlockEntity) return BASE_SMOKER_COOK_TICKS;
		if (furnace instanceof BlastFurnaceBlockEntity) return BASE_BLAST_FURNACE_COOK_TICKS;
		return BASE_FURNACE_COOK_TICKS;
	}

	private static int toTicks(double value) {
		return !Double.isFinite(value) || value <= 0.0 ? 0 : Math.max(1, (int) Math.round(value));
	}

	public static String describeRecipeType(RecipeType<?> recipeType) {
		Identifier id = recipeType == null ? null : BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
		return id == null ? "unknown" : id.toString();
	}

	private record FurnaceBehavior(double smeltingSpeed, double fuelEfficiency) { }

	private record FurnaceKey(String levelId, long blockPosLong) {
		private static FurnaceKey from(ServerLevel level, BlockPos blockPos) {
			return level == null || blockPos == null ? null : new FurnaceKey(
				SchedulerAPIManager.normalizeLevelIdentifier(level.dimension().toString()), blockPos.asLong());
		}

		private static FurnaceKey from(SchedulerAPIManager.SchedulerBinding binding) {
			if (binding == null || binding.getEventType() != SchedulerAPIManager.EventType.BLOCK_ENTITY
				|| binding.getLevelId() == null || binding.getBlockPosLong() == null) return null;
			return new FurnaceKey(binding.getLevelId(), binding.getBlockPosLong());
		}

		private SchedulerAPIManager.SchedulerBinding toBinding() {
			return SchedulerAPIManager.SchedulerBinding.blockEntity(TASK_TYPE_SMELTING_TICK, levelId, blockPosLong);
		}

	}
}
