package madoku.craft.hunger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.levels.MadokuLevels;
import madoku.craft.network.HungerHudSync;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuHungerManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHungerManager.class);

	private static final int VANILLA_MAX_HUNGER_POINTS = 20;
	private static final int MAX_LEVEL_BONUS_HUNGER_POINTS = 20;
	private static final int MAX_CONFIG_HUNGER_POINTS = 8192;
	private static final long HUNGER_EFFECT_INTERVAL_TICKS = 20L;
	private static final long SATURATION_EFFECT_INTERVAL_TICKS = 20L;
	private static final long PENDING_HUNGER_IDLE_TIMEOUT_TICKS = 1500L;
	private static final long HUNGER_PLAYER_TICK_MIN_INTERVAL = 1L;
	private static final long HUNGER_PLAYER_TICK_MAX_INTERVAL = 5L;
	private static final int ACTION_INTERVAL_TICKS = 10;

	private static final String DATA_FOLDER_NAME = "madoku-craft-hunger";
	private static final String DATA_FILE_NAME = "madoku-hunger";
	private static final String TASK_TYPE_HUNGER_PLAYER_TICK = "hunger_player_tick";
	private static final String HUNGER_PLAYER_TICK_SCHEDULER_KEY = "hunger_player_tick";

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static volatile HungerConfigManager.Settings settings = HungerConfigManager.Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static long lastProcessedGameplayTick = Long.MIN_VALUE;
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private MadokuHungerManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_HUNGER_PLAYER_TICK, MadokuHungerManager::runPlayerTickTask);
		ServerPlayerEvents.JOIN.register(MadokuHungerManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuHungerManager::handlePlayerRespawn);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> handleBlockBreak(player));
	}

	public static void reset() {
		PLAYER_STATES.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		lastProcessedGameplayTick = Long.MIN_VALUE;
		schedulerId = "";
		tickQueued = false;
		SchedulerManagerSystem.clearAdaptiveDelayState(HUNGER_PLAYER_TICK_SCHEDULER_KEY);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		loadStaticConfig();
		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(data);
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !settings.hunger.enabled) {
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

	public static void onServerStarted(MinecraftServer server) {
		ensureQueued(server);
	}

	public static boolean isEnabled() {
		return settings.hunger.enabled;
	}

	public static boolean isSaturationEnabled() {
		return settings.hunger.enabled && settings.saturation.enabled;
	}

	public static boolean isHungerEffectEnabled() {
		return settings.hunger.enabled && settings.hungerEffect.enabled;
	}

	public static int getCurrentHungerPoints(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		if (!settings.hunger.enabled) {
			return clampVanillaFood(player.getFoodData().getFoodLevel());
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		return clampInternalHunger(state.hungerPoints, maxHungerPoints);
	}

	public static int getEffectiveHungerPoints(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		if (!settings.hunger.enabled) {
			return clampVanillaFood(player.getFoodData().getFoodLevel());
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		long totalHunger = Math.max(0L, (long) state.hungerPoints) + Math.max(0L, (long) state.pendingHunger);
		return (int) Math.min((long) maxHungerPoints, totalHunger);
	}

	public static int getMaximumHungerPoints(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		return Math.max(1, settings.hunger.enabled ? resolveMaximumHungerPoints(player) : VANILLA_MAX_HUNGER_POINTS);
	}

	public static int getConfiguredMaximumHungerPoints() {
		return Math.max(1, settings.hunger.maxHunger);
	}

	public static void handleMaximumHungerChanged(ServerPlayer player) {
		if (player == null || !settings.hunger.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(player);
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		state.hungerPoints = clampInternalHunger(state.hungerPoints, maxHungerPoints);
		state.pendingHunger = normalizePendingHunger(state.pendingHunger);
		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(player, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
	}

	public static boolean hasEnoughFoodToDoExhaustiveManoeuvres(Player player) {
		if (player == null || !settings.hunger.enabled) {
			return true;
		}
		if (player.getAbilities().mayfly) {
			return true;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			int maxHungerPoints = resolveMaximumHungerPoints(serverPlayer);
			PlayerState state = PLAYER_STATES.computeIfAbsent(serverPlayer.getUUID(), ignored -> new PlayerState());
			initializeHungerFromPlayer(serverPlayer, state, maxHungerPoints);
			return !isAtOrBelowSprintThreshold(state.hungerPoints, maxHungerPoints);
		}

		float vanillaFoodRatio = (float) clampVanillaFood(player.getFoodData().getFoodLevel()) / (float) VANILLA_MAX_HUNGER_POINTS;
		return vanillaFoodRatio > (float) settings.hunger.starvationPenalty.starvationPenaltyPercentage;
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		if (!isHungerEffectEnabled() || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}
		return effect == MobEffects.HUNGER.value();
	}

	public static boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) {
		if (player == null || !settings.hunger.enabled || ignoreHunger) {
			return true;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(player);
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		return (long) Math.max(0, state.hungerPoints) + (long) Math.max(0, state.pendingHunger) < (long) maxHungerPoints;
	}

	public static void onFoodConsumed(ServerPlayer player, int nutrition) {
		if (player == null || nutrition <= 0 || !settings.hunger.enabled) {
			return;
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);

		int before = state.pendingHunger;
		state.pendingHunger = safeAdd(before, nutrition);
		if (state.pendingHunger == before) {
			return;
		}

		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		scheduleNextPendingAllocation(state, MadokuTicks.getGameplayTicks());
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
	}

	public static int drainHunger(ServerPlayer player, int amount) {
		if (player == null || amount <= 0) {
			return 0;
		}
		if (isExemptFromHungerDrain(player)) {
			return 0;
		}

		if (!settings.hunger.enabled) {
			return drainVanillaFood(player, amount);
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		int drained = Math.min(amount, state.hungerPoints);
		if (drained <= 0) {
			return 0;
		}

		state.hungerPoints -= drained;
		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(player, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
		return drained;
	}

	private static void runPlayerTickTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		tickQueued = false;
		if (server == null || context == null) {
			return;
		}

		schedulerId = context.getSchedulerId();
		long gameplayTick = context.getNowTick();
		for (long tick = lastProcessedGameplayTick == Long.MIN_VALUE ? gameplayTick : lastProcessedGameplayTick + 1L; tick <= gameplayTick; tick++) {
			onGameplayTick(server, tick);
		}
		lastProcessedGameplayTick = gameplayTick;
		ensureQueued(server);
	}

	private static void ensureQueued(MinecraftServer server) {
		if (server == null || tickQueued) {
			return;
		}

		String currentSchedulerId = ensureScheduler();
		long delayTicks = SchedulerManagerSystem.resolveAdaptiveDelayTicks(
			server,
			HUNGER_PLAYER_TICK_SCHEDULER_KEY,
			HUNGER_PLAYER_TICK_MIN_INTERVAL,
			HUNGER_PLAYER_TICK_MAX_INTERVAL
		);
		if (SchedulerManagerSystem.hasQueuedTask(currentSchedulerId, TASK_TYPE_HUNGER_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(HUNGER_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue hunger player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(HUNGER_PLAYER_TICK_SCHEDULER_KEY)
		);
		return schedulerId;
	}

	private static boolean enqueue(String targetSchedulerId, long delayTicks) {
		if (targetSchedulerId == null || targetSchedulerId.isBlank()) {
			return false;
		}
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			targetSchedulerId,
			Math.max(0L, delayTicks),
			TASK_TYPE_HUNGER_PLAYER_TICK,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static void onGameplayTick(MinecraftServer server, long gameplayTick) {
		if (server == null || !settings.hunger.enabled) {
			return;
		}
		if (Math.floorMod(gameplayTick, ACTION_INTERVAL_TICKS) != 0L) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			processPlayer(player, gameplayTick);
		}
	}

	private static void processPlayer(ServerPlayer player, long gameplayTick) {
		if (player == null || !player.isAlive() || player.isDeadOrDying()) {
			if (player != null) {
				PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
				state.clearPosition();
			}
			return;
		}

		int maxHungerPoints = resolveMaximumHungerPoints(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeHungerFromPlayer(player, state, maxHungerPoints);

		if (isExemptFromHungerDrain(player)) {
			state.blockBreakProgress = 0L;
			state.movementProgress = 0.0d;
			state.timeProgressTicks = 0L;
			state.markPosition(player.getX(), player.getZ());
			applyFoodState(player, state.hungerPoints, maxHungerPoints);
			enforceStarvationPenalty(player, state, maxHungerPoints);
			state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
			state.lastSyncedPendingHunger = Integer.MIN_VALUE;
			state.lastSyncedMaxHunger = Integer.MIN_VALUE;
			syncHudState(player, state, maxHungerPoints);
			return;
		}

		if (!settings.hungerDepletion.enabled) {
			state.blockBreakProgress = 0L;
			state.movementProgress = 0.0d;
			state.timeProgressTicks = 0L;
		} else {
			if (!settings.hungerDepletion.blockGoal.enabled) {
				state.blockBreakProgress = 0L;
			}
			processMovementGoal(player, state, gameplayTick, maxHungerPoints);
			processTimeGoal(player, state, gameplayTick, maxHungerPoints);
		}

		processHungerEffect(player, state, gameplayTick, maxHungerPoints);
		processSaturationEffect(player, state, gameplayTick, maxHungerPoints);
		processPendingHunger(player, state, gameplayTick, maxHungerPoints);
		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(player, state, maxHungerPoints);
		syncHudState(player, state, maxHungerPoints);
	}

	private static void processMovementGoal(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (!settings.hungerDepletion.movementGoal.enabled) {
			state.movementProgress = 0.0d;
			state.markPosition(player.getX(), player.getZ());
			return;
		}

		double x = player.getX();
		double z = player.getZ();
		if (!state.hasPosition()) {
			state.markPosition(x, z);
			return;
		}

		double dx = x - state.lastX;
		double dz = z - state.lastZ;
		state.markPosition(x, z);
		double horizontalDistance = Math.hypot(dx, dz);
		if (horizontalDistance <= 1.0e-6d) {
			return;
		}

		state.movementProgress += horizontalDistance;
		while (state.movementProgress >= settings.hungerDepletion.movementGoal.value
			&& settings.hungerDepletion.movementGoal.value > 0.0d) {
			state.movementProgress -= settings.hungerDepletion.movementGoal.value;
			int drained = drainStateHunger(state, 1);
			if (drained <= 0) {
				break;
			}
		}

		if (state.movementProgress < 0.0d) {
			state.movementProgress = 0.0d;
		}
		if (state.hungerPoints < maxHungerPoints) {
			applyFoodState(player, state.hungerPoints, maxHungerPoints);
		}
	}

	private static void processTimeGoal(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (!settings.hungerDepletion.timeGoal.enabled) {
			state.timeProgressTicks = 0L;
			return;
		}

		state.timeProgressTicks = Math.min(Long.MAX_VALUE, state.timeProgressTicks + ACTION_INTERVAL_TICKS);
		while (state.timeProgressTicks >= settings.hungerDepletion.timeGoal.value
			&& settings.hungerDepletion.timeGoal.value > 0L) {
			state.timeProgressTicks -= settings.hungerDepletion.timeGoal.value;
			int drained = drainStateHunger(state, 1);
			if (drained <= 0) {
				break;
			}
		}

		if (state.hungerPoints < maxHungerPoints) {
			applyFoodState(player, state.hungerPoints, maxHungerPoints);
		}
	}

	private static void processHungerEffect(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (!settings.hungerEffect.enabled || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (Math.floorMod(gameplayTick, HUNGER_EFFECT_INTERVAL_TICKS) != 0L) {
			return;
		}

		int hungerEffectLevel = getEffectLevel(player, MobEffects.HUNGER);
		if (hungerEffectLevel <= 0) {
			return;
		}

		int drainAmount = resolveEffectAmount(player, settings.hungerEffect, hungerEffectLevel, maxHungerPoints);
		if (drainAmount <= 0) {
			return;
		}

		int drained = drainStateHunger(state, drainAmount);
		if (drained <= 0) {
			return;
		}

		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(player, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
	}

	private static void processSaturationEffect(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (!settings.saturation.enabled || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (Math.floorMod(gameplayTick, SATURATION_EFFECT_INTERVAL_TICKS) != 0L) {
			return;
		}

		int saturationEffectLevel = getEffectLevel(player, MobEffects.SATURATION);
		if (saturationEffectLevel <= 0) {
			return;
		}

		int gainAmount = resolveEffectAmount(player, settings.saturation, saturationEffectLevel, maxHungerPoints);
		if (gainAmount <= 0) {
			return;
		}

		int before = state.hungerPoints;
		state.hungerPoints = clampInternalHunger(safeAdd(before, gainAmount), maxHungerPoints);
		if (state.hungerPoints == before) {
			return;
		}

		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(player, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.hunger.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(player);
		initializeHungerFromPlayer(player, state, maxHungerPoints);
		state.markPosition(player.getX(), player.getZ());
		applyFoodState(player, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(player, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(player, state, maxHungerPoints);
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.hunger.enabled) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(newPlayer);
		state.hungerPoints = clampInternalHunger((int) Math.round(maxHungerPoints * settings.hunger.respawnHungerPercentage), maxHungerPoints);
		state.pendingHunger = 0;
		state.blockBreakProgress = 0L;
		state.movementProgress = 0.0d;
		state.timeProgressTicks = 0L;
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.nextPendingAllocationTick = 0L;
		state.clearPosition();
		state.markPosition(newPlayer.getX(), newPlayer.getZ());
		applyFoodState(newPlayer, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(newPlayer, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(newPlayer, state, maxHungerPoints);
	}

	private static void handleBlockBreak(Player player) {
		if (!settings.hunger.enabled || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!settings.hungerDepletion.enabled || !settings.hungerDepletion.blockGoal.enabled) {
			PlayerState state = PLAYER_STATES.get(serverPlayer.getUUID());
			if (state != null) {
				state.blockBreakProgress = 0L;
			}
			return;
		}
		if (isExemptFromHungerDrain(serverPlayer)) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(serverPlayer.getUUID(), ignored -> new PlayerState());
		int maxHungerPoints = resolveMaximumHungerPoints(serverPlayer);
		initializeHungerFromPlayer(serverPlayer, state, maxHungerPoints);
		state.blockBreakProgress++;
		while (state.blockBreakProgress >= settings.hungerDepletion.blockGoal.value
			&& settings.hungerDepletion.blockGoal.value > 0L) {
			state.blockBreakProgress -= settings.hungerDepletion.blockGoal.value;
			int drained = drainStateHunger(state, 1);
			if (drained <= 0) {
				break;
			}
		}

		applyFoodState(serverPlayer, state.hungerPoints, maxHungerPoints);
		enforceStarvationPenalty(serverPlayer, state, maxHungerPoints);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
		syncHudState(serverPlayer, state, maxHungerPoints);
	}

	private static void enforceStarvationPenalty(ServerPlayer player, PlayerState state, int maxHungerPoints) {
		if (player == null || state == null || !settings.hunger.enabled || !settings.hunger.starvationPenalty.enabled) {
			return;
		}
		if (!isAtOrBelowSprintThreshold(state.hungerPoints, maxHungerPoints) || !player.isSprinting()) {
			return;
		}

		player.setSprinting(false);
	}

	private static void initializeHungerFromPlayer(ServerPlayer player, PlayerState state, int maxHungerPoints) {
		if (state.hungerPoints >= 0) {
			state.hungerPoints = clampInternalHunger(state.hungerPoints, maxHungerPoints);
			state.pendingHunger = normalizePendingHunger(state.pendingHunger);
			return;
		}

		state.hungerPoints = fromVanillaFood(player.getFoodData().getFoodLevel(), maxHungerPoints);
		state.pendingHunger = normalizePendingHunger(state.pendingHunger);
	}

	private static void applyFoodState(ServerPlayer player, int hungerPoints, int maxHungerPoints) {
		if (player == null) {
			return;
		}

		FoodData foodData = player.getFoodData();
		int vanillaFood = toVanillaFood(hungerPoints, maxHungerPoints);
		if (foodData.getFoodLevel() != vanillaFood) {
			foodData.setFoodLevel(vanillaFood);
		}
	}

	private static void syncHudState(ServerPlayer player, PlayerState state, int maxHungerPoints) {
		if (player == null || state == null) {
			return;
		}
		long totalHunger = Math.max(0L, (long) state.hungerPoints) + Math.max(0L, (long) state.pendingHunger);
		int displayCurrentHunger = (int) Math.min((long) Math.max(1, maxHungerPoints), totalHunger);
		int displayPendingHunger = (int) Math.max(0L, totalHunger - (long) displayCurrentHunger);
		if (state.lastSyncedCurrentHunger == displayCurrentHunger
			&& state.lastSyncedPendingHunger == displayPendingHunger
			&& state.lastSyncedMaxHunger == maxHungerPoints) {
			return;
		}
		if (!HungerHudSync.send(player, displayCurrentHunger, displayPendingHunger, maxHungerPoints)) {
			return;
		}
		state.lastSyncedCurrentHunger = displayCurrentHunger;
		state.lastSyncedPendingHunger = displayPendingHunger;
		state.lastSyncedMaxHunger = maxHungerPoints;
	}

	private static int resolveMaximumHungerPoints(ServerPlayer player) {
		int configuredMaximum = Math.max(1, settings.hunger.maxHunger);
		if (player == null || !settings.hunger.enabled) {
			return configuredMaximum;
		}
		return Math.max(1, configuredMaximum + Math.max(0, MadokuLevels.getPlayerHungerBonusPoints(player)));
	}

	private static int resolveEffectAmount(ServerPlayer player, HungerConfigManager.EffectSettings effect, int effectLevel, int maxHungerPoints) {
		if (player == null || effect == null || effectLevel <= 0) {
			return 0;
		}

		double perLevelAmount = effect.type == HungerConfigManager.ValueType.FLAT
			? effect.value
			: (double) maxHungerPoints * effect.value;
		double rawAmount = perLevelAmount * (double) effectLevel;
		return Math.max(1, (int) Math.round(rawAmount));
	}

	private static int drainStateHunger(PlayerState state, int amount) {
		if (state == null || amount <= 0) {
			return 0;
		}
		int drained = Math.min(amount, state.hungerPoints);
		if (drained <= 0) {
			return 0;
		}
		state.hungerPoints -= drained;
		return drained;
	}

	private static void processPendingHunger(ServerPlayer player, PlayerState state, long gameplayTick, int maxHungerPoints) {
		if (player == null || state == null) {
			return;
		}
		if (state.pendingHunger <= 0) {
			state.nextPendingAllocationTick = 0L;
			return;
		}

		clearIdlePendingHunger(player, state, gameplayTick);
		if (state.pendingHunger <= 0) {
			state.nextPendingAllocationTick = 0L;
			return;
		}

		scheduleNextPendingAllocation(state, gameplayTick);
		if (state.hungerPoints >= maxHungerPoints || gameplayTick < state.nextPendingAllocationTick) {
			return;
		}

		int moved = Math.min(1, Math.min(state.pendingHunger, maxHungerPoints - state.hungerPoints));
		if (moved <= 0) {
			return;
		}

		state.pendingHunger -= moved;
		state.hungerPoints += moved;
		state.lastPendingActivityTick = gameplayTick;
		scheduleNextPendingAllocation(state, gameplayTick);
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
	}

	private static void scheduleNextPendingAllocation(PlayerState state, long gameplayTick) {
		if (state == null) {
			return;
		}
		if (state.pendingHunger <= 0) {
			state.nextPendingAllocationTick = 0L;
			return;
		}
		if (state.nextPendingAllocationTick > gameplayTick) {
			return;
		}
		state.nextPendingAllocationTick = gameplayTick + ACTION_INTERVAL_TICKS;
	}

	private static void clearIdlePendingHunger(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (player == null || state == null || state.pendingHunger <= 0) {
			return;
		}
		long idleTicks = gameplayTick - state.lastPendingActivityTick;
		if (idleTicks < PENDING_HUNGER_IDLE_TIMEOUT_TICKS) {
			return;
		}

		state.pendingHunger = 0;
		state.nextPendingAllocationTick = 0L;
		state.lastPendingActivityTick = gameplayTick;
		state.lastSyncedCurrentHunger = Integer.MIN_VALUE;
		state.lastSyncedPendingHunger = Integer.MIN_VALUE;
		state.lastSyncedMaxHunger = Integer.MIN_VALUE;
	}

	private static boolean isExemptFromHungerDrain(ServerPlayer player) {
		return player != null && (player.isCreative() || player.isSpectator());
	}

	private static boolean isAtOrBelowSprintThreshold(int hungerPoints, int maxHungerPoints) {
		int safeMax = Math.max(1, maxHungerPoints);
		int safeHunger = Math.max(0, hungerPoints);
		double threshold = Math.max(0.0d, Math.min(1.0d, settings.hunger.starvationPenalty.starvationPenaltyPercentage));
		return (double) safeHunger / (double) safeMax <= threshold;
	}

	private static int getEffectLevel(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect) {
		if (player == null || effect == null) {
			return 0;
		}
		MobEffectInstance effectInstance = player.getEffect(effect);
		if (effectInstance == null) {
			return 0;
		}
		return Math.max(0, effectInstance.getAmplifier() + 1);
	}

	private static int drainVanillaFood(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}

		FoodData foodData = player.getFoodData();
		int current = Math.max(0, foodData.getFoodLevel());
		int drained = Math.min(amount, current);
		if (drained <= 0) {
			return 0;
		}

		int updated = current - drained;
		foodData.setFoodLevel(updated);
		return drained;
	}

	private static int clampInternalHunger(int value, int maxHungerPoints) {
		int max = Math.max(1, maxHungerPoints);
		return Math.max(0, Math.min(max, value));
	}

	private static int normalizePendingHunger(int value) {
		return Math.max(0, value);
	}

	private static int clampVanillaFood(int value) {
		return Math.max(0, Math.min(VANILLA_MAX_HUNGER_POINTS, value));
	}

	private static int toVanillaFood(int internalHunger, int maxHungerPoints) {
		int clampedInternal = clampInternalHunger(internalHunger, maxHungerPoints);
		if (maxHungerPoints <= 0) {
			return 0;
		}
		double ratio = (double) clampedInternal / (double) maxHungerPoints;
		return clampVanillaFood((int) Math.round(ratio * VANILLA_MAX_HUNGER_POINTS));
	}

	private static int fromVanillaFood(int vanillaFood, int maxHungerPoints) {
		int clampedVanilla = clampVanillaFood(vanillaFood);
		double ratio = (double) clampedVanilla / (double) VANILLA_MAX_HUNGER_POINTS;
		return clampInternalHunger((int) Math.round(ratio * maxHungerPoints), maxHungerPoints);
	}

	private static int safeAdd(int current, int delta) {
		long sum = (long) current + (long) delta;
		if (sum > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (sum < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) sum;
	}

	private static JsonObject createDefaultData() {
		return madoku.craft.config.JsonFormatBuilder.object()
			.array("players", players -> {
			})
			.build();
	}

	private static JsonObject toPersistedData() {
		madoku.craft.config.JsonFormatBuilder.ArrayBuilder players = madoku.craft.config.JsonFormatBuilder.array();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			players.object(player -> player
				.put("uuid", entry.getKey().toString())
				.put("hunger-points", state.hungerPoints)
				.put("pending-hunger", state.pendingHunger)
				.put("block-break-progress", state.blockBreakProgress)
				.put("movement-progress", state.movementProgress)
				.put("time-progress-ticks", state.timeProgressTicks)
				.put("last-pending-activity-tick", Math.max(0L, state.lastPendingActivityTick))
				.put("next-pending-allocation-tick", Math.max(0L, state.nextPendingAllocationTick)));
		}
		return madoku.craft.config.JsonFormatBuilder.object()
			.put("players", players.build())
			.build();
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_STATES.clear();
		if (source == null) {
			return;
		}

		JsonArray players = getArray(source, "players");
		if (players == null) {
			return;
		}

		for (JsonElement element : players) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject playerData = element.getAsJsonObject();
			UUID playerId = parseUuid(getString(playerData, "uuid", ""));
			if (playerId == null) {
				continue;
			}

			PlayerState state = new PlayerState();
			state.hungerPoints = clampInternalHunger(
				(int) getLong(playerData, "hunger-points", settings.hunger.maxHunger),
				MAX_CONFIG_HUNGER_POINTS + MAX_LEVEL_BONUS_HUNGER_POINTS
			);
			state.pendingHunger = Math.max(0, (int) getLong(playerData, "pending-hunger", 0L));
			state.blockBreakProgress = Math.max(0L, getLong(playerData, "block-break-progress", 0L));
			state.movementProgress = Math.max(0.0d, getDouble(playerData, "movement-progress", 0.0d));
			state.timeProgressTicks = Math.max(0L, getLong(playerData, "time-progress-ticks", 0L));
			state.lastPendingActivityTick = Math.max(0L, getLong(playerData, "last-pending-activity-tick", 0L));
			state.nextPendingAllocationTick = Math.max(0L, getLong(playerData, "next-pending-allocation-tick", 0L));
			PLAYER_STATES.put(playerId, state);
		}
	}

	private static void loadStaticConfig() {
		settings = HungerConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		String value = element.getAsString();
		return value == null ? fallback : value.trim();
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static JsonArray getArray(JsonObject object, String key) {
		if (object == null || key == null || key.isBlank()) {
			return null;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonArray()) {
			return null;
		}
		return element.getAsJsonArray();
	}

	private static UUID parseUuid(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(rawValue.trim());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static final class PlayerState {
		private int hungerPoints = -1;
		private int pendingHunger;
		private long blockBreakProgress;
		private double movementProgress;
		private long timeProgressTicks;
		private long lastPendingActivityTick;
		private long nextPendingAllocationTick;
		private int lastSyncedCurrentHunger = Integer.MIN_VALUE;
		private int lastSyncedPendingHunger = Integer.MIN_VALUE;
		private int lastSyncedMaxHunger = Integer.MIN_VALUE;
		private double lastX = Double.NaN;
		private double lastZ = Double.NaN;

		private boolean hasPosition() {
			return !Double.isNaN(lastX) && !Double.isNaN(lastZ);
		}

		private void markPosition(double x, double z) {
			lastX = x;
			lastZ = z;
		}

		private void clearPosition() {
			lastX = Double.NaN;
			lastZ = Double.NaN;
		}
	}
}
