package madoku.craft.oxygen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.MadokuAttributes;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.core.Holder;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MadokuOxygen {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuOxygen.class);

	private static final String OXYGEN_CONFIG_DIRECTORY_NAME = "madoku-oxygen";
	private static final String OXYGEN_CONFIG_FILE_NAME = "madoku-oxygen";
	private static final String DATA_FOLDER_NAME = "madoku-craft-oxygen";
	private static final String DATA_FILE_NAME = "madoku-oxygen";
	private static final String TASK_TYPE_OXYGEN_PLAYER_TICK = "oxygen_player_tick";
	private static final String OXYGEN_PLAYER_TICK_SCHEDULER_KEY = "oxygen_player_tick";
	private static final long OXYGEN_PLAYER_TICK_DELAY = 1L;
	private static final String VANILLA_BREATH_OF_THE_NAUTILUS_DESCRIPTION_ID = "effect.minecraft.breath_of_the_nautilus";
	private static final int VANILLA_MAX_AIR_SUPPLY_TICKS = 300;
	private static final long TICKS_PER_SECOND = Math.max(1L, MadokuTicks.TICKS_PER_SECOND);
	private static final double DEFAULT_MAXIMUM_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION = 2.0d;
	private static final double DEFAULT_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION = 1.0d;
	private static final long DEFAULT_DROWNING_DAMAGE_INTERVAL_TICKS = 20L;
	private static final double DEFAULT_DROWNING_DAMAGE_AMOUNT = 1.0d;

	private static final Map<UUID, PlayerState> PLAYER_STATES = new HashMap<>();
	private static final Map<UUID, Long> NEXT_PROCESS_TICKS_BY_PLAYER = new HashMap<>();
	private static final Set<UUID> CUSTOM_DROWNING_DAMAGE_PLAYERS = new HashSet<>();
	private static volatile Settings settings = Settings.defaults();
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile String schedulerId = "";
	private static volatile boolean tickQueued;

	private MadokuOxygen() {
	}

	public static void initialize() {
		loadStaticConfig();
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_OXYGEN_PLAYER_TICK, MadokuOxygen::runPlayerTickTask);
		ServerPlayerEvents.JOIN.register(MadokuOxygen::handlePlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuOxygen::handlePlayerRespawn);
	}

	public static void reset() {
		PLAYER_STATES.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		CUSTOM_DROWNING_DAMAGE_PLAYERS.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
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
		ensureQueued(server, OXYGEN_PLAYER_TICK_DELAY);
	}

	public static boolean shouldSuppressVanillaDrowningDamage(ServerPlayer player, DamageSource source) {
		if (player == null || source == null || !settings.enabled) {
			return false;
		}
		if (!source.is(DamageTypes.DROWN)) {
			return false;
		}
		return !CUSTOM_DROWNING_DAMAGE_PLAYERS.contains(player.getUUID());
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
		ensureQueued(server, OXYGEN_PLAYER_TICK_DELAY);
	}

	private static void ensureQueued(MinecraftServer server, long delayTicks) {
		if (server == null || tickQueued) {
			return;
		}

		String currentSchedulerId = ensureScheduler();
		if (SchedulerManagerSystem.hasQueuedTask(currentSchedulerId, TASK_TYPE_OXYGEN_PLAYER_TICK)) {
			tickQueued = true;
			return;
		}
		if (enqueue(currentSchedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(OXYGEN_PLAYER_TICK_SCHEDULER_KEY)
		);
		if (enqueue(schedulerId, delayTicks)) {
			tickQueued = true;
			return;
		}

		LOGGER.error("Failed to enqueue oxygen player tick task.");
	}

	private static String ensureScheduler() {
		String current = schedulerId;
		if (current != null && !current.isBlank()) {
			return current;
		}
		schedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(OXYGEN_PLAYER_TICK_SCHEDULER_KEY)
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
			TASK_TYPE_OXYGEN_PLAYER_TICK,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		int baseMaximumOxygenTicks = settings.maximumOxygenTicks;
		if (entity == null) {
			return baseMaximumOxygenTicks;
		}

		int extraEffectLevels = getTotalOxygenBoostLevels(entity);
		double multiplier = 1.0d + Math.max(0, extraEffectLevels) * settings.maximumOxygenGainPerEffectLevelFraction;
		double boostedMaximum = baseMaximumOxygenTicks * multiplier;
		if (boostedMaximum >= Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return Math.max(1, (int) Math.round(boostedMaximum));
	}

	private static void onPlayerTick(MinecraftServer server, ServerPlayer player, long gameplayTick) {
		if (server == null || player == null) {
			return;
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

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeOxygenFromPlayer(player, state, oxygenCapTicks);

		if (!player.isAlive() || player.isDeadOrDying()) {
			return false;
		}

		if (player.isSpectator() || player.getAbilities().invulnerable) {
			state.oxygenTicks = oxygenCapTicks;
			state.lastKnownOxygenBoostLevels = getTotalOxygenBoostLevels(player);
			applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
			return true;
		}

		int currentBoostLevels = getTotalOxygenBoostLevels(player);
		boolean boostedThisTick = applyOxygenGainFromBoostIncrease(
			state,
			currentBoostLevels,
			oxygenCapTicks
		);

		if (shouldDrainOxygen(player)) {
			if (!boostedThisTick) {
				int drained = Math.min(settings.oxygenDrainPerTick, state.oxygenTicks);
				state.oxygenTicks = Math.max(0, state.oxygenTicks - drained);
			}
			if (state.oxygenTicks > 0) {
				applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
			} else if (player.getAirSupply() > 0) {
				// Force zero vanilla air so custom drowning damage stays authoritative.
				player.setAirSupply(0);
			}
			applyCustomDrowningDamage(player, state, gameplayTick);
			return true;
		}

		if (!boostedThisTick) {
			int recovered = Math.min(settings.oxygenRecoveryPerTick, oxygenCapTicks - state.oxygenTicks);
			state.oxygenTicks = Math.min(oxygenCapTicks, state.oxygenTicks + recovered);
		}
		state.lastDrowningDamageTick = Long.MIN_VALUE;
		applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
		return true;
	}

	private static void applyCustomDrowningDamage(ServerPlayer player, PlayerState state, long gameplayTick) {
		if (player == null || state == null || state.oxygenTicks > 0) {
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying()) {
			return;
		}
		if (!player.isEyeInFluid(FluidTags.WATER)) {
			return;
		}
		if (settings.drowningDamageAmount <= 0.0d) {
			return;
		}
		if (state.lastDrowningDamageTick != Long.MIN_VALUE
			&& gameplayTick - state.lastDrowningDamageTick < settings.drowningDamageIntervalTicks) {
			return;
		}

		state.lastDrowningDamageTick = gameplayTick;
		UUID playerId = player.getUUID();
		CUSTOM_DROWNING_DAMAGE_PLAYERS.add(playerId);
		try {
			player.hurtServer(player.level(), player.damageSources().drown(), (float) settings.drowningDamageAmount);
		} finally {
			CUSTOM_DROWNING_DAMAGE_PLAYERS.remove(playerId);
		}
	}

	private static boolean shouldDrainOxygen(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (!player.isEyeInFluid(FluidTags.WATER)) {
			return false;
		}
		if (player.canBreatheUnderwater()) {
			return false;
		}
		// Water effects increase max oxygen pool but do not pause drain.
		return true;
	}

	private static int getVanillaBreathOfTheNautilusLevel(LivingEntity entity) {
		if (entity == null) {
			return 0;
		}

		int bestLevel = 0;
		for (MobEffectInstance effectInstance : entity.getActiveEffects()) {
			if (effectInstance == null || effectInstance.getEffect() == null) {
				continue;
			}
			String descriptionId = effectInstance.getEffect().value().getDescriptionId();
			if (descriptionId == null) {
				continue;
			}
			if (VANILLA_BREATH_OF_THE_NAUTILUS_DESCRIPTION_ID.equals(descriptionId)) {
				bestLevel = Math.max(bestLevel, effectInstance.getAmplifier() + 1);
			}
		}
		return Math.max(0, bestLevel);
	}

	private static int getEffectLevel(LivingEntity entity, Holder<MobEffect> effect) {
		if (entity == null || effect == null) {
			return 0;
		}
		MobEffectInstance effectInstance = entity.getEffect(effect);
		if (effectInstance == null) {
			return 0;
		}
		return Math.max(0, effectInstance.getAmplifier() + 1);
	}

	private static int getTotalOxygenBoostLevels(LivingEntity entity) {
		if (entity == null) {
			return 0;
		}
		return getEffectLevel(entity, MobEffects.WATER_BREATHING)
			+ getEffectLevel(entity, MobEffects.CONDUIT_POWER)
			+ getVanillaBreathOfTheNautilusLevel(entity);
	}

	private static boolean applyOxygenGainFromBoostIncrease(
		PlayerState state,
		int currentBoostLevels,
		int oxygenCapTicks
	) {
		if (state == null) {
			return false;
		}

		int safeCurrentBoostLevels = Math.max(0, currentBoostLevels);
		if (state.lastKnownOxygenBoostLevels < 0) {
			state.lastKnownOxygenBoostLevels = safeCurrentBoostLevels;
			return false;
		}

		int gainedLevels = safeCurrentBoostLevels - state.lastKnownOxygenBoostLevels;
		state.lastKnownOxygenBoostLevels = safeCurrentBoostLevels;
		if (gainedLevels <= 0) {
			return false;
		}
		if (settings.oxygenGainPerEffectLevelFraction <= 0.0d) {
			return false;
		}

		// Current oxygen gain scales from configured base max oxygen, not boosted cap.
		int oxygenGain = (int) Math.round(settings.maximumOxygenTicks * settings.oxygenGainPerEffectLevelFraction * gainedLevels);
		if (oxygenGain <= 0) {
			return false;
		}

		state.oxygenTicks = clampInt(state.oxygenTicks + oxygenGain, 0, oxygenCapTicks);
		state.lastDrowningDamageTick = Long.MIN_VALUE;
		return true;
	}

	private static void initializeOxygenFromPlayer(ServerPlayer player, PlayerState state, int oxygenCapTicks) {
		if (player == null || state == null) {
			return;
		}

		if (state.oxygenTicks >= 0) {
			if (!shouldDrainOxygen(player)) {
				state.oxygenTicks = oxygenCapTicks;
			} else {
			state.oxygenTicks = clampInt(state.oxygenTicks, 0, oxygenCapTicks);
			}
			if (state.lastKnownOxygenBoostLevels < 0) {
				state.lastKnownOxygenBoostLevels = getTotalOxygenBoostLevels(player);
			}
			return;
		}

		int observedAirSupply = decodeAirSupplyFromVanillaHud(player.getAirSupply(), oxygenCapTicks);
		state.oxygenTicks = shouldDrainOxygen(player) ? observedAirSupply : oxygenCapTicks;
		state.lastKnownOxygenBoostLevels = getTotalOxygenBoostLevels(player);
	}

	private static void handlePlayerJoin(ServerPlayer player) {
		if (player == null || !settings.enabled) {
			return;
		}

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(player);
		PlayerState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
		initializeOxygenFromPlayer(player, state, oxygenCapTicks);
		state.oxygenTicks = clampInt(state.oxygenTicks, 0, oxygenCapTicks);
		state.lastDrowningDamageTick = Long.MIN_VALUE;
		state.lastKnownOxygenBoostLevels = getTotalOxygenBoostLevels(player);
		applyVanillaCompatibleAirSupply(player, state.oxygenTicks, oxygenCapTicks);
		NEXT_PROCESS_TICKS_BY_PLAYER.put(player.getUUID(), 0L);
	}

	private static void handlePlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !settings.enabled) {
			return;
		}

		int oxygenCapTicks = getMaximumOxygenTicksForEntity(newPlayer);
		PlayerState state = PLAYER_STATES.computeIfAbsent(newPlayer.getUUID(), ignored -> new PlayerState());
		state.oxygenTicks = oxygenCapTicks;
		state.lastDrowningDamageTick = Long.MIN_VALUE;
		state.lastKnownOxygenBoostLevels = getTotalOxygenBoostLevels(newPlayer);
		applyVanillaCompatibleAirSupply(newPlayer, state.oxygenTicks, oxygenCapTicks);
		NEXT_PROCESS_TICKS_BY_PLAYER.put(newPlayer.getUUID(), 0L);
	}

	private static void applyVanillaCompatibleAirSupply(ServerPlayer player, int oxygenTicks, int oxygenCapTicks) {
		if (player == null) {
			return;
		}
		int encodedAir = encodeAirSupplyForVanillaHud(oxygenTicks, oxygenCapTicks);
		if (player.getAirSupply() != encodedAir) {
			player.setAirSupply(encodedAir);
		}
	}

	private static int encodeAirSupplyForVanillaHud(int oxygenTicks, int oxygenCapTicks) {
		int safeCap = Math.max(1, oxygenCapTicks);
		int safeOxygen = clampInt(oxygenTicks, 0, safeCap);
		if (safeCap <= VANILLA_MAX_AIR_SUPPLY_TICKS) {
			return safeOxygen;
		}
		double ratio = safeOxygen / (double) safeCap;
		return clampInt(
			(int) Math.round(ratio * VANILLA_MAX_AIR_SUPPLY_TICKS),
			0,
			VANILLA_MAX_AIR_SUPPLY_TICKS
		);
	}

	private static int decodeAirSupplyFromVanillaHud(int observedAirSupply, int oxygenCapTicks) {
		int safeCap = Math.max(1, oxygenCapTicks);
		int clampedObserved = clampInt(observedAirSupply, 0, safeCap);
		if (safeCap <= VANILLA_MAX_AIR_SUPPLY_TICKS) {
			return clampedObserved;
		}
		if (clampedObserved > VANILLA_MAX_AIR_SUPPLY_TICKS) {
			// Backward compatibility for any pre-scale values still in memory.
			return clampedObserved;
		}
		double ratio = clampedObserved / (double) VANILLA_MAX_AIR_SUPPLY_TICKS;
		return clampInt((int) Math.round(ratio * safeCap), 0, safeCap);
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add("players", new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		int oxygenCapTicks = settings.maximumOxygenTicks;

		JsonObject root = createDefaultData();

		JsonArray players = new JsonArray();
		for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
			PlayerState state = entry.getValue();
			if (!state.hasPersistableState(oxygenCapTicks)) {
				continue;
			}

			JsonObject player = new JsonObject();
			player.addProperty("uuid", entry.getKey().toString());
			player.addProperty("oxygen-ticks", state.oxygenTicks);
			players.add(player);
		}
		root.add("players", players);
		return root;
	}

	private static void applyPersistedData(JsonObject source) {
		PLAYER_STATES.clear();
		NEXT_PROCESS_TICKS_BY_PLAYER.clear();
		if (source == null) {
			return;
		}

		int oxygenCapTicks = settings.maximumOxygenTicks;
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
			long persistedOxygenTicks = getLong(playerData, "oxygen-ticks", oxygenCapTicks);
			if (persistedOxygenTicks < 0L) {
				persistedOxygenTicks = 0L;
			}
			if (persistedOxygenTicks > Integer.MAX_VALUE) {
				persistedOxygenTicks = Integer.MAX_VALUE;
			}
			state.oxygenTicks = (int) persistedOxygenTicks;
			PLAYER_STATES.put(playerId, state);
		}
	}

	private static void loadStaticConfig() {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = MadokuAttributes.prepareSystemConfigFile(OXYGEN_CONFIG_DIRECTORY_NAME, OXYGEN_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings configured = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured.withEnabled(MadokuAttributes.isEnabled());
		} catch (IOException | RuntimeException exception) {
			settings = fallback.withEnabled(MadokuAttributes.isEnabled());
			LOGGER.error("Failed to load MadokuOxygen static config; using defaults.", exception);
		}
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

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class PlayerState {
		private int oxygenTicks = -1;
		private long lastDrowningDamageTick = Long.MIN_VALUE;
		private int lastKnownOxygenBoostLevels = -1;

		private boolean hasPersistableState(int oxygenCapTicks) {
			return oxygenTicks >= 0 && oxygenTicks != oxygenCapTicks;
		}
	}

	private static final class Settings {
		private final boolean enabled;
		private final long schedulerTickInterval;
		private final int maximumOxygenTicks;
		private final int oxygenDrainPerTick;
		private final int oxygenRecoveryPerTick;
		private final double maximumOxygenGainPerEffectLevelFraction;
		private final double oxygenGainPerEffectLevelFraction;
		private final long drowningDamageIntervalTicks;
		private final double drowningDamageAmount;

		private Settings(
			boolean enabled,
			long schedulerTickInterval,
			int maximumOxygenTicks,
			int oxygenDrainPerTick,
			int oxygenRecoveryPerTick,
			double maximumOxygenGainPerEffectLevelFraction,
			double oxygenGainPerEffectLevelFraction,
			long drowningDamageIntervalTicks,
			double drowningDamageAmount
		) {
			this.enabled = enabled;
			this.schedulerTickInterval = schedulerTickInterval;
			this.maximumOxygenTicks = maximumOxygenTicks;
			this.oxygenDrainPerTick = oxygenDrainPerTick;
			this.oxygenRecoveryPerTick = oxygenRecoveryPerTick;
			this.maximumOxygenGainPerEffectLevelFraction = maximumOxygenGainPerEffectLevelFraction;
			this.oxygenGainPerEffectLevelFraction = oxygenGainPerEffectLevelFraction;
			this.drowningDamageIntervalTicks = drowningDamageIntervalTicks;
			this.drowningDamageAmount = drowningDamageAmount;
		}

		private static Settings defaults() {
			return new Settings(
				true,
				1L,
				(int) (20L * TICKS_PER_SECOND),
				1,
				5,
				DEFAULT_MAXIMUM_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION,
				DEFAULT_OXYGEN_GAIN_PER_EFFECT_LEVEL_FRACTION,
				DEFAULT_DROWNING_DAMAGE_INTERVAL_TICKS,
				DEFAULT_DROWNING_DAMAGE_AMOUNT
			);
		}

		private static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();

			boolean enabled = getBoolean(source, "enabled", defaults.enabled);
			long schedulerTickInterval = defaults.schedulerTickInterval;
			int maximumOxygenTicks = (int) clampLong(
				getLong(source, "maximum-oxygen", defaults.maximumOxygenTicks),
				1L,
				20L * 60L * 60L
			);
			int oxygenDrainPerTick = defaults.oxygenDrainPerTick;
			int oxygenRecoveryPerTick = defaults.oxygenRecoveryPerTick;
			double oxygenGainPerEffectLevelFraction = defaults.oxygenGainPerEffectLevelFraction;
			double maximumOxygenGainPerEffectLevelFraction = defaults.maximumOxygenGainPerEffectLevelFraction;
			long drowningDamageIntervalTicks = defaults.drowningDamageIntervalTicks;
			double drowningDamageAmount = defaults.drowningDamageAmount;

			return new Settings(
				enabled,
				schedulerTickInterval,
				maximumOxygenTicks,
				oxygenDrainPerTick,
				oxygenRecoveryPerTick,
				maximumOxygenGainPerEffectLevelFraction,
				oxygenGainPerEffectLevelFraction,
				drowningDamageIntervalTicks,
				drowningDamageAmount
			);
		}

		private JsonObject toConfigJson() {
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("maximum-oxygen", maximumOxygenTicks);
			return root;
		}

		private Settings withEnabled(boolean attributesEnabled) {
			return new Settings(
				attributesEnabled && enabled,
				schedulerTickInterval,
				maximumOxygenTicks,
				oxygenDrainPerTick,
				oxygenRecoveryPerTick,
				maximumOxygenGainPerEffectLevelFraction,
				oxygenGainPerEffectLevelFraction,
				drowningDamageIntervalTicks,
				drowningDamageAmount
			);
		}

		private static long clampLong(long value, long min, long max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}
