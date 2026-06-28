package madoku.craft.health;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import madoku.craft.MadokuCraft;
import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.hunger.MadokuHungerManager;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MadokuHealthManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuHealthManager.class);
	private static final float EPSILON = 1.0e-4f;
	private static final float HEALTH_ROUND_STEP = 0.125f;
	private static final int VANILLA_MAX_HUNGER_POINTS = 20;

	private static final String DATA_FOLDER_NAME = "madoku-craft-health";
	private static final String DATA_FILE_NAME = "madoku-health";
	private static final String TASK_TYPE_HEALTH_PLAYER_TICK = "health_player_tick";
	private static final String HEALTH_PLAYER_TICK_SCHEDULER_KEY = "health_player_tick";
	private static final long HEALTH_PLAYER_TICK_DELAY = 1L;
	private static final float DEATH_RESPAWN_HEALTH_RATIO = 0.5f;
	private static final Identifier LOW_HUNGER_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_health_low_hunger_max_health");
	private static final Identifier HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "madoku_health_health_boost_max_health");
	private static final long WITHER_TICK_INTERVAL = 20L;
	private static final long REGEN_TICK_INTERVAL = 20L;
	private static final long POISON_TICK_INTERVAL = 20L;
	private static final float EFFECT_DAMAGE_AMOUNT = 1.0f;
	private static final float POISON_DAMAGE_PER_LEVEL = EFFECT_DAMAGE_AMOUNT;
	private static final float WITHER_DAMAGE_PER_LEVEL = EFFECT_DAMAGE_AMOUNT;
	private static final float REGEN_FRACTION_PER_LEVEL = 0.05f;
	private static final double HEALTH_BOOST_MAX_HEALTH_FRACTION_PER_LEVEL = 0.10d;
	private static final float ABSORPTION_FRACTION_PER_LEVEL = 0.20f;
	private static final float POISON_MIN_HEALTH = 1.0f;
	private static final float LOW_HUNGER_STEP_RATIO = 0.05f;
	private static final double MAX_HEALTH_REDUCTION_PER_STEP = 0.10d;
	private static final double PENDING_HEALTH_PER_HUNGER = 1.0d;
	private static final double PENDING_HEALTH_APPLY_AMOUNT = 1.0d;

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static final Map<UUID, Long> NEXT_PROCESS_TICKS_BY_PLAYER = new HashMap<>();
	private static volatile HealthConfigManager.Settings settings = HealthConfigManager.Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static long lastNaturalRegenDisableTick = Long.MIN_VALUE;
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private MadokuHealthManager() {
	}

	public static void initialize() {
		loadStaticConfig();
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_HEALTH_PLAYER_TICK, MadokuHealthManager::runPlayerTickTask);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MadokuHealthManager::handleAfterPlayerDamage);
		ServerPlayerEvents.JOIN.register(MadokuHealthManager::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuHealthManager::handlePlayerRespawn);
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static void reset() {
		PLAYER_STATES.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
		lastNaturalRegenDisableTick = Long.MIN_VALUE;
		schedulerId = "";
		tickQueued = false;
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

	public static void onServerStarted(MinecraftServer server) {
		ensureQueued(server, HEALTH_PLAYER_TICK_DELAY);
	}

	private static void runPlayerTickTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		tickQueued = false;
		if (server == null || context == null) {
			return;
		}

		schedulerId = context.getSchedulerId();
		long gameplayTick = context.getNowTick();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			onPlayerTick(server, player, gameplayTick);
		}
		ensureQueued(server, HEALTH_PLAYER_TICK_DELAY);
	}

	private static void ensureQueued(MinecraftServer server, long delayTicks) {
		if (server == null || tickQueued) {
			return;
		}

		String currentSchedulerId = ensureScheduler();
		if (SchedulerManagerSystem.hasQueuedTask(currentSchedulerId, TASK_TYPE_HEALTH_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(HEALTH_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue health player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(HEALTH_PLAYER_TICK_SCHEDULER_KEY)
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
			TASK_TYPE_HEALTH_PLAYER_TICK,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static void onPlayerTick(MinecraftServer server, ServerPlayer player, long gameplayTick) {
		if (server == null || player == null) {
			return;
		}

		if (gameplayTick != lastNaturalRegenDisableTick) {
			disableVanillaNaturalRegen(server, gameplayTick);
			lastNaturalRegenDisableTick = gameplayTick;
		}
		if (!settings.enabled) {
			return;
		}

		UUID playerId = player.getUUID();
		long nextProcessTick = NEXT_PROCESS_TICKS_BY_PLAYER.getOrDefault(playerId, 0L);
		if (gameplayTick < nextProcessTick) {
			return;
		}

		boolean stillActive = processPlayer(player, gameplayTick);
		if (stillActive) {
			NEXT_PROCESS_TICKS_BY_PLAYER.put(playerId, gameplayTick + Math.max(1L, settings.schedulerTickInterval));
		} else {
			NEXT_PROCESS_TICKS_BY_PLAYER.remove(playerId);
		}
	}

	private static boolean processPlayer(ServerPlayer player, long gameplayTick) {
		if (player == null) {
			return false;
		}

		UUID playerId = player.getUUID();
		boolean actionTick = Math.floorMod(gameplayTick, Math.max(1, settings.actionIntervalTicks)) == 0;
		PlayerState state = PLAYER_STATES.computeIfAbsent(playerId, ignored -> new PlayerState());
		if (!state.onlineThisSession) {
			state.onlineThisSession = true;
			state.lastPendingActivityTick = gameplayTick;
		}
		if (!player.isAlive() || player.isDeadOrDying()) {
			// Never run the heal/drain loop while the player is dead; this can interfere with respawn.
			state.pendingHealth = 0.0f;
			state.highHungerDrainActive = false;
			return false;
		}

		applyLowHungerMaxHealthScaling(player, state, gameplayTick);
		applyHealthBoostScaling(player, state, gameplayTick);
		applyAbsorptionScaling(player, state, gameplayTick);
		processStatusEffects(player, state, gameplayTick);
		if (actionTick) {
			collectPendingHealthFromHunger(player, state, gameplayTick);
			applyPendingHealth(player, state, gameplayTick);
		}
		clearIdlePendingHealth(state, playerId, gameplayTick);
		return true;
	}

	private static void processStatusEffects(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (player == null) {
			return;
		}

		int poisonLevel = getEffectLevel(player, MobEffects.POISON);
		if (poisonLevel > 0) {
			if (Math.floorMod(gameplayTick, POISON_TICK_INTERVAL) == 0L) {
				applyPoisonTick(player, gameplayTick, poisonLevel);
			}
		}

		int witherLevel = getEffectLevel(player, MobEffects.WITHER);
		if (witherLevel > 0 && Math.floorMod(gameplayTick, WITHER_TICK_INTERVAL) == 0L) {
			applyWitherTick(player, gameplayTick, witherLevel);
		}

		int regenerationLevel = getEffectLevel(player, MobEffects.REGENERATION);
		if (regenerationLevel > 0 && Math.floorMod(gameplayTick, REGEN_TICK_INTERVAL) == 0L) {
			applyRegenerationTick(player, state, gameplayTick, regenerationLevel);
		}
	}

	private static void applyPoisonTick(ServerPlayer player, long gameplayTick, int poisonLevel) {
		float current = player.getHealth();
		if (current <= POISON_MIN_HEALTH + EPSILON) {
			return;
		}

		float damage = POISON_DAMAGE_PER_LEVEL * Math.max(1, poisonLevel);
		float target = quantizeHealth(Math.max(POISON_MIN_HEALTH, current - damage));
		if (target >= current - EPSILON) {
			return;
		}

		player.setHealth(target);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_poison_tick")) {
			MadokuDebug.event("health.effect_poison_tick", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("level", poisonLevel)
				.field("damage", formatFloat(damage))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void applyWitherTick(ServerPlayer player, long gameplayTick, int witherLevel) {
		float damage = WITHER_DAMAGE_PER_LEVEL * Math.max(1, witherLevel);
		player.hurtServer(player.level(), player.damageSources().wither(), damage);
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_wither_tick")) {
			MadokuDebug.event("health.effect_wither_tick", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("level", witherLevel)
				.field("damage", formatFloat(damage))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void applyRegenerationTick(ServerPlayer player, PlayerState state, long gameplayTick, int regenerationLevel) {
		float maxHealth = player.getMaxHealth();
		float current = player.getHealth();
		if (current >= maxHealth - EPSILON) {
			return;
		}

		float healing = maxHealth * REGEN_FRACTION_PER_LEVEL * regenerationLevel;
		if (healing <= EPSILON) {
			return;
		}

		float target = quantizeHealth(Math.min(maxHealth, current + healing));
		if (target <= current + EPSILON) {
			return;
		}

		player.setHealth(target);
		state.lastPendingActivityTick = gameplayTick;

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_regeneration_tick")) {
			MadokuDebug.event("health.effect_regeneration_tick", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("healed", formatFloat(target - current))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void collectPendingHealthFromHunger(ServerPlayer player, PlayerState state, long gameplayTick) {
		float missingHealth = player.getMaxHealth() - player.getHealth();
		float hungerRatio = hungerRatio(player.getFoodData().getFoodLevel());
		boolean hasRecoveryNeed = missingHealth > EPSILON;

		if (hungerRatio > settings.hungerDrainRatio && hasRecoveryNeed) {
			state.highHungerDrainActive = true;
		}
		if (hungerRatio <= settings.hungerDrainRatio || !hasRecoveryNeed) {
			state.highHungerDrainActive = false;
		}
		if (!state.highHungerDrainActive) {
			return;
		}

		int drained = drainFood(player, 1);
		if (drained <= 0) {
			state.highHungerDrainActive = false;
			return;
		}

		state.pendingHealth += drained * (float) PENDING_HEALTH_PER_HUNGER;
		state.lastPendingActivityTick = gameplayTick;
		if (hungerRatio(player.getFoodData().getFoodLevel()) <= settings.hungerDrainRatio) {
			state.highHungerDrainActive = false;
		}

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.pending_collected")) {
			MadokuDebug.event("health.pending_collected", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("hunger_drained", drained)
				.field("pending_health", formatFloat(state.pendingHealth))
				.log();
		}
	}

	private static void applyPendingHealth(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (state.pendingHealth <= EPSILON) {
			return;
		}

		float missingHealth = player.getMaxHealth() - player.getHealth();
		if (missingHealth <= EPSILON) {
			return;
		}

		float healing = Math.min((float) PENDING_HEALTH_APPLY_AMOUNT, Math.min(state.pendingHealth, missingHealth));
		if (healing <= EPSILON) {
			return;
		}

		float targetHealth = quantizeHealth(player.getHealth() + healing);
		targetHealth = Math.min(player.getMaxHealth(), targetHealth);
		targetHealth = Math.max(player.getHealth(), targetHealth);
		float appliedHealing = targetHealth - player.getHealth();
		if (appliedHealing <= EPSILON) {
			return;
		}

		player.setHealth(targetHealth);
		state.pendingHealth -= appliedHealing;
		if (state.pendingHealth < EPSILON) {
			state.pendingHealth = 0.0f;
		}
		state.lastPendingActivityTick = gameplayTick;

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.pending_applied")) {
			MadokuDebug.event("health.pending_applied", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("healed", formatFloat(appliedHealing))
				.field("pending_health", formatFloat(state.pendingHealth))
				.field("health", formatFloat(player.getHealth()))
				.log();
		}
	}

	private static void clearIdlePendingHealth(PlayerState state, UUID playerId, long gameplayTick) {
		if (state.pendingHealth <= EPSILON) {
			return;
		}

		long idleTicks = gameplayTick - state.lastPendingActivityTick;
		if (idleTicks < settings.pendingIdleTimeoutTicks) {
			return;
		}

		state.pendingHealth = 0.0f;
		state.highHungerDrainActive = false;
		state.lastPendingActivityTick = gameplayTick;

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.pending_idle_cleared")) {
			MadokuDebug.event("health.pending_idle_cleared", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + playerId)
				.log();
		}
	}

	private static void applyLowHungerMaxHealthScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		double targetBaseMaxHealth = settings.maximumHealth;
		if (Math.abs(maxHealthAttribute.getBaseValue() - targetBaseMaxHealth) > 1.0e-5d) {
			maxHealthAttribute.setBaseValue(targetBaseMaxHealth);
		}

		double targetMultiplier = calculateMaxHealthMultiplier(hungerRatio(player.getFoodData().getFoodLevel()));
		if (Math.abs(targetMultiplier - state.appliedMaxHealthMultiplier) > 1.0e-5d) {
			if (Math.abs(targetMultiplier - 1.0d) <= 1.0e-5d) {
				maxHealthAttribute.removeModifier(LOW_HUNGER_MAX_HEALTH_MODIFIER_ID);
			} else {
				maxHealthAttribute.addOrUpdateTransientModifier(
					new AttributeModifier(
						LOW_HUNGER_MAX_HEALTH_MODIFIER_ID,
						targetMultiplier - 1.0d,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
					)
				);
			}

			state.appliedMaxHealthMultiplier = targetMultiplier;
			if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.max_health_scaled")) {
				MadokuDebug.event("health.max_health_scaled", MadokuDebug.Domain.HEALTH)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.subject("player:" + player.getUUID())
					.field("multiplier", String.format("%.3f", targetMultiplier))
					.field("food_level", player.getFoodData().getFoodLevel())
					.log();
			}
		}

		float maxHealth = player.getMaxHealth();
		if (player.getHealth() > maxHealth + EPSILON) {
			player.setHealth(Math.min(maxHealth, quantizeHealth(maxHealth)));
		}
	}

	private static void applyHealthBoostScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealthAttribute == null) {
			return;
		}

		int healthBoostLevel = getEffectLevel(player, MobEffects.HEALTH_BOOST);
		double referenceMaxHealth = player.getMaxHealth() - state.appliedHealthBoostAmount;
		if (referenceMaxHealth < 0.0d) {
			referenceMaxHealth = player.getMaxHealth();
		}
		double targetAmount = healthBoostLevel <= 0
			? 0.0d
			: referenceMaxHealth * HEALTH_BOOST_MAX_HEALTH_FRACTION_PER_LEVEL * healthBoostLevel;

		boolean modifierChanged = false;
		boolean missingExpectedModifier = targetAmount > 1.0e-5d && !maxHealthAttribute.hasModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID);
		if (Math.abs(targetAmount - state.appliedHealthBoostAmount) > 1.0e-5d || missingExpectedModifier) {
			maxHealthAttribute.removeModifier(HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID);
			if (targetAmount > 1.0e-5d) {
				maxHealthAttribute.addOrUpdateTransientModifier(
					new AttributeModifier(
						HEALTH_BOOST_MAX_HEALTH_MODIFIER_ID,
						targetAmount,
						AttributeModifier.Operation.ADD_VALUE
					)
				);
			}
			state.appliedHealthBoostAmount = targetAmount;
			modifierChanged = true;
		}

		if (player.getHealth() > player.getMaxHealth() + EPSILON) {
			player.setHealth(Math.min(player.getMaxHealth(), quantizeHealth(player.getMaxHealth())));
		}

		if (modifierChanged && MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_health_boost_scaled")) {
			MadokuDebug.event("health.effect_health_boost_scaled", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("bonus_amount", String.format("%.3f", state.appliedHealthBoostAmount))
				.field("max_health", formatFloat(player.getMaxHealth()))
				.log();
		}
	}

	private static void applyAbsorptionScaling(ServerPlayer player, PlayerState state, long gameplayTick) {
		int absorptionLevel = getEffectLevel(player, MobEffects.ABSORPTION);
		float targetAbsorption = absorptionLevel <= 0
			? 0.0f
			: quantizeHealth(player.getMaxHealth() * ABSORPTION_FRACTION_PER_LEVEL * absorptionLevel);

		if (Math.abs(targetAbsorption - state.appliedAbsorptionAmount) <= EPSILON) {
			return;
		}

		player.setAbsorptionAmount(targetAbsorption);
		state.appliedAbsorptionAmount = targetAbsorption;
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.effect_absorption_scaled")) {
			MadokuDebug.event("health.effect_absorption_scaled", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(gameplayTick)
				.subject("player:" + player.getUUID())
				.field("absorption", formatFloat(targetAbsorption))
				.log();
		}
	}

	private static int drainFood(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}
		if (MadokuHungerManager.isEnabled()) {
			return MadokuHungerManager.drainHunger(player, amount);
		}

		FoodData foodData = player.getFoodData();
		int current = Math.max(0, foodData.getFoodLevel());
		int drained = Math.min(amount, current);
		if (drained <= 0) {
			return 0;
		}

		int updated = current - drained;
		foodData.setFoodLevel(updated);
		foodData.setSaturation(Math.max(0.0f, Math.min(foodData.getSaturationLevel(), updated)));
		return drained;
	}

	private static double calculateMaxHealthMultiplier(float hungerRatio) {
		if (hungerRatio >= settings.hungerPenaltyRatio) {
			return 1.0d;
		}

		double belowThreshold = settings.hungerPenaltyRatio - hungerRatio;
		int steps = (int) Math.ceil((belowThreshold - EPSILON) / LOW_HUNGER_STEP_RATIO);
		steps = Math.max(0, steps);
		double reduction = steps * MAX_HEALTH_REDUCTION_PER_STEP;
		double multiplier = 1.0d - reduction;
		if (multiplier < settings.healthPenaltyRatio) {
			return settings.healthPenaltyRatio;
		}
		return Math.min(1.0d, multiplier);
	}

	private static float hungerRatio(int foodLevel) {
		int clamped = Math.max(0, Math.min(VANILLA_MAX_HUNGER_POINTS, foodLevel));
		return (float) clamped / (float) VANILLA_MAX_HUNGER_POINTS;
	}

	private static void disableVanillaNaturalRegen(MinecraftServer server, long gameplayTick) {
		for (ServerLevel level : server.getAllLevels()) {
			boolean wasEnabled = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
			level.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
			if (wasEnabled && MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.vanilla_regen_disabled")) {
				MadokuDebug.event("health.vanilla_regen_disabled", MadokuDebug.Domain.HEALTH)
					.side(MadokuDebug.Side.SERVER)
					.tick(gameplayTick)
					.world(level.dimension().toString())
					.subject("world:" + level.dimension())
					.log();
			}
		}
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive) {
			return;
		}

		float targetHealth = Math.max(0.5f, newPlayer.getMaxHealth() * DEATH_RESPAWN_HEALTH_RATIO);
		targetHealth = Math.min(newPlayer.getMaxHealth(), quantizeHealth(targetHealth));
		newPlayer.setHealth(targetHealth);

		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		state.pendingHealth = 0.0f;
		state.highHungerDrainActive = newPlayer.getHealth() + EPSILON < newPlayer.getMaxHealth();
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.appliedMaxHealthMultiplier = 1.0d;
		state.appliedHealthBoostAmount = 0.0d;
		state.appliedAbsorptionAmount = 0.0f;
		state.onlineThisSession = true;
		NEXT_PROCESS_TICKS_BY_PLAYER.put(newPlayer.getUUID(), 0L);

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.respawn_half_health")) {
			MadokuDebug.event("health.respawn_half_health", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(MadokuTicks.getGameplayTicks())
				.subject("player:" + newPlayer.getUUID())
				.field("health", formatFloat(newPlayer.getHealth()))
				.field("max_health", formatFloat(newPlayer.getMaxHealth()))
				.log();
		}
	}

	private static void handleAfterPlayerDamage(
		net.minecraft.world.entity.LivingEntity entity,
		net.minecraft.world.damagesource.DamageSource source,
		float baseDamageTaken,
		float damageTaken,
		boolean blocked
	) {
		if (!(entity instanceof ServerPlayer player) || damageTaken <= EPSILON) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.highHungerDrainActive = player.getHealth() + EPSILON < player.getMaxHealth();
		NEXT_PROCESS_TICKS_BY_PLAYER.put(player.getUUID(), 0L);

		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.HEALTH, "health.damage_detected")) {
			MadokuDebug.event("health.damage_detected", MadokuDebug.Domain.HEALTH)
				.side(MadokuDebug.Side.SERVER)
				.tick(MadokuTicks.getGameplayTicks())
				.subject("player:" + player.getUUID())
				.field("damage_taken", formatFloat(damageTaken))
				.field("blocked", blocked)
				.log();
		}
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		state.lastPendingActivityTick = MadokuTicks.getGameplayTicks();
		state.highHungerDrainActive = player.getHealth() + EPSILON < player.getMaxHealth();
		applyImmediateEffectOverrides(player, state, MadokuTicks.getGameplayTicks());
		NEXT_PROCESS_TICKS_BY_PLAYER.put(player.getUUID(), 0L);
	}

	public static void handlePlayerEffectsChanged(ServerPlayer player) {
		if (player == null) {
			return;
		}

		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		state.onlineThisSession = true;
		long gameplayTick = MadokuTicks.getGameplayTicks();
		state.lastPendingActivityTick = gameplayTick;
		applyImmediateEffectOverrides(player, state, gameplayTick);
		NEXT_PROCESS_TICKS_BY_PLAYER.put(player.getUUID(), 0L);
	}

	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return effect == MobEffects.POISON.value()
			|| effect == MobEffects.WITHER.value()
			|| effect == MobEffects.REGENERATION.value()
			|| effect == MobEffects.ABSORPTION.value();
	}

	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		if (!settings.enabled || !(entity instanceof ServerPlayer) || effect == null) {
			return false;
		}

		return effect == MobEffects.HEALTH_BOOST.value()
			|| effect == MobEffects.ABSORPTION.value();
	}

	private static void applyImmediateEffectOverrides(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (!settings.enabled || player == null || state == null) {
			return;
		}
		applyLowHungerMaxHealthScaling(player, state, gameplayTick);
		applyHealthBoostScaling(player, state, gameplayTick);
		applyAbsorptionScaling(player, state, gameplayTick);
	}

	private static int getEffectLevel(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		if (player == null || effect == null) {
			return 0;
		}
		MobEffectInstance effectInstance = player.getEffect(effect);
		if (effectInstance == null) {
			return 0;
		}
		return Math.max(0, effectInstance.getAmplifier() + 1);
	}

	private static JsonObject createDefaultData() {
		return JsonFormatBuilder.object()
			.array("players", players -> {
			})
			.build();
	}

	private static JsonObject toPersistedData() {
		JsonFormatBuilder.ArrayBuilder players = JsonFormatBuilder.array();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			if (!state.hasPersistableState()) {
				continue;
			}

			players.object(player -> player
				.put("uuid", entry.getKey().toString())
				.put("pending-health", state.pendingHealth)
				.put("high-hunger-drain-active", state.highHungerDrainActive)
				.put("last-pending-activity-tick", Math.max(0L, state.lastPendingActivityTick)));
		}
		return JsonFormatBuilder.object()
			.put("players", players.build())
			.build();
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_STATES.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();

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
			state.pendingHealth = Math.max(0.0f, (float) getDouble(playerData, "pending-health", 0.0d));
			state.highHungerDrainActive = getBoolean(playerData, "high-hunger-drain-active", false);
			state.lastPendingActivityTick = Math.max(0L, getLong(playerData, "last-pending-activity-tick", 0L));
			PLAYER_STATES.put(playerId, state);
		}
	}

	private static void loadStaticConfig() {
		settings = HealthConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
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

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
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

	private static String formatFloat(float value) {
		return String.format("%.3f", value);
	}

	private static float quantizeHealth(float value) {
		if (value <= 0.0f) {
			return 0.0f;
		}
		return Math.round(value / HEALTH_ROUND_STEP) * HEALTH_ROUND_STEP;
	}

	private static final class PlayerState {
		private float pendingHealth;
		private boolean highHungerDrainActive;
		private long lastPendingActivityTick;
		private double appliedMaxHealthMultiplier = 1.0d;
		private double appliedHealthBoostAmount;
		private float appliedAbsorptionAmount;
		private boolean onlineThisSession;

		private boolean hasPersistableState() {
			return pendingHealth > EPSILON || highHungerDrainActive;
		}
	}

}
