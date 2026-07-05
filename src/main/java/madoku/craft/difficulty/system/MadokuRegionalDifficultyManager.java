package madoku.craft.difficulty.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.chunk.ChunkManagerSystem;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.mixin.MobExperienceAccessor;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class MadokuRegionalDifficultyManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuRegionalDifficultyManager.class);

	private static final String DIFFICULTY_CONFIG_ROOT_FOLDER_NAME = "madoku-craft-regional-difficulty";
	private static final String DIFFICULTY_CONFIG_SETTINGS_FILE_NAME = "madoku-regional-difficulty";
	private static final String DIFFICULTY_CONFIG_RULES_FOLDER_NAME = "madoku-regional-difficulty";
	private static final String DIFFICULTY_CONFIG_MOB_SCALING_FOLDER_NAME = "madoku-scaling";
	private static final String BIOME_RULES_FOLDER_NAME = "biome-rules";
	private static final String STRUCTURE_RULES_FOLDER_NAME = "structure-rules";
	private static final String TIME_RULES_FOLDER_NAME = "time-rules";
	private static final String TASK_TYPE_TIME_TICK = "difficulty_time_tick";
	private static final String TIME_SCHEDULER_OWNER_ID = "madoku-regional-difficulty-time";
	private static final long TIME_SCHEDULER_MIN_INTERVAL_TICKS = 5L;
	private static final long TIME_SCHEDULER_MAX_INTERVAL_TICKS = 100L;

	private static final long TICKS_PER_DAY = 24000L;

	private static volatile Snapshot snapshot = Snapshot.disabled();
	private static volatile String timeSchedulerId = "";
	private static volatile boolean timeTaskScheduled = false;
	private static volatile long cachedTimeDayCount = Long.MIN_VALUE;
	private static volatile int cachedTimeAdjustment = 0;

	private MadokuRegionalDifficultyManager() {
	}

	public static void initialize() {
		loadConfig();
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_TIME_TICK, MadokuRegionalDifficultyManager::runTimeTask);
	}

	public static void onServerStarted(MinecraftServer server) {
		SchedulerManagerSystem.clearAdaptiveDelayState(TIME_SCHEDULER_OWNER_ID);
		timeSchedulerId = "";
		timeTaskScheduled = false;
		cachedTimeDayCount = Long.MIN_VALUE;
		cachedTimeAdjustment = 0;
		refreshCachedTimeAdjustment(server, snapshot);
		timeSchedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(TIME_SCHEDULER_OWNER_ID));
		SchedulerManagerSystem.clearQueuedRequests(timeSchedulerId);
		requestTimeProcessing(server, 1L);
	}

	public static void onServerTick(MinecraftServer server) {
		requestTimeProcessing(server, resolveTimeSchedulerInterval(server));
	}

	public static void onServerStopped() {
		SchedulerManagerSystem.clearAdaptiveDelayState(TIME_SCHEDULER_OWNER_ID);
		timeSchedulerId = "";
		timeTaskScheduled = false;
		cachedTimeDayCount = Long.MIN_VALUE;
		cachedTimeAdjustment = 0;
	}

	public static boolean isEnabled() {
		return snapshot.enabled();
	}

	public static int resolveHudDifficultyLevel(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return 1;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return 1;
		}
		if (!isChunkLoadedAt(world, pos)) {
			return 1;
		}
		Identifier biomeId = resolveBiomeId(world, pos);
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = resolveStructureContext(world, pos, config.structureRuntime());
		int totalAdjustment = Math.max(0, biomeAdjustment + structureContext.adjustment() + timeAdjustment);
		return Math.max(1, 1 + totalAdjustment);
	}

	public static int resolveHudDifficultyLevel(ServerPlayer player) {
		if (player == null) {
			return 1;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return 1;
		}
		return resolveHudDifficultyLevel(serverLevel, player.blockPosition());
	}

	public static int resolveCurrentTimeAdjustment(ServerLevel world) {
		Snapshot config = snapshot;
		if (world == null || !config.enabled() || !config.timeEnabled()) {
			return 0;
		}
		if (cachedTimeDayCount == Long.MIN_VALUE) {
			long dayCount = resolveDifficultyDayCount(world);
			cachedTimeDayCount = dayCount;
			cachedTimeAdjustment = config.timeAdjustment(dayCount);
		}
		return Math.max(0, cachedTimeAdjustment);
	}

	public static long resolveDifficultyDayCount(ServerLevel world) {
		if (world == null) {
			return 0L;
		}
		MinecraftServer server = world.getServer();
		if (server != null) {
			return resolveDifficultyDayCount(server);
		}
		return Math.floorDiv(world.getOverworldClockTime(), TICKS_PER_DAY);
	}

	public static void applySpawnScaling(Mob mob, ServerLevelAccessor worldAccess) {
		if (mob == null || worldAccess == null || !(mob instanceof DifficultyScaledMob scaledMob)) {
			return;
		}

		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(0);
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return;
		}

		ServerLevel world = worldAccess.getLevel();
		if (world == null || world.isClientSide()) {
			return;
		}
		if (!isChunkLoadedAt(world, mob.blockPosition())) {
			return;
		}

		Identifier biomeId = resolveBiomeId(world, mob.blockPosition());
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = resolveStructureContext(world, mob.blockPosition(), config.structureRuntime());
		int structureAdjustment = structureContext.adjustment();
		int baseAdjustment = 1;
		int totalAdjustment = Math.max(0, baseAdjustment + biomeAdjustment + structureAdjustment + timeAdjustment);
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(totalAdjustment);
		if (totalAdjustment <= 0 || isHealthOnlyBoss(mob)) {
			return;
		}

		ResolvedIncrements resolvedIncrements = config.resolveIncrements(mob);
		boolean fullStatScaling = mob instanceof Monster || resolvedIncrements.usesIndividualProfile();
		ScalingApplication applied = applyScalingAdjustments(mob, resolvedIncrements, totalAdjustment, fullStatScaling);
		if (applied == null) {
			return;
		}

		emitSpawnScaled(
			mob,
			biomeId,
			structureContext,
			baseAdjustment,
			biomeAdjustment,
			structureAdjustment,
			timeAdjustment,
			totalAdjustment,
			resolvedIncrements.sourceKey(),
			applied.increments(),
			applied.healthAddition(),
			applied.movementSpeedAddition(),
			applied.scaleAddition(),
			applied.armorAddition(),
			applied.armorBaseBefore(),
			applied.armorBaseAfter(),
			applied.damageAddition(),
			applied.knockbackResistanceAddition(),
			applied.experienceBaseBefore(),
			applied.experienceBaseAfter(),
			applied.experienceDropAddition()
		);
	}

	public static void applySpawnScalingIfUnscaled(Mob mob, ServerLevelAccessor worldAccess) {
		if (!(mob instanceof DifficultyScaledMob scaledMob) || scaledMob.madokuCraft$getSpawnDifficultyAdjustment() > 0) {
			return;
		}
		applySpawnScaling(mob, worldAccess);
	}

	public static int ensureSpawnDifficultyAdjustment(Mob mob, ServerLevelAccessor worldAccess) {
		if (mob == null || worldAccess == null || !(mob instanceof DifficultyScaledMob scaledMob)) {
			return 0;
		}
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(0);
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return 0;
		}
		ServerLevel world = worldAccess.getLevel();
		if (world == null || world.isClientSide()) {
			return 0;
		}
		if (!isChunkLoadedAt(world, mob.blockPosition())) {
			return 0;
		}

		Identifier biomeId = resolveBiomeId(world, mob.blockPosition());
		int biomeAdjustment = config.biomeAdjustment(biomeId);
		int timeAdjustment = resolveTimeAdjustment(world, config);
		StructureContext structureContext = resolveStructureContext(world, mob.blockPosition(), config.structureRuntime());
		int structureAdjustment = structureContext.adjustment();
		int baseAdjustment = 1;
		int totalAdjustment = Math.max(0, baseAdjustment + biomeAdjustment + structureAdjustment + timeAdjustment);
		scaledMob.madokuCraft$setSpawnDifficultyAdjustment(totalAdjustment);
		return totalAdjustment;
	}

	public static void reapplySpawnScalingFromStoredAdjustment(Mob mob) {
		if (mob == null || !(mob instanceof DifficultyScaledMob scaledMob)) {
			return;
		}
		Snapshot config = snapshot;
		if (!config.enabled()) {
			return;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0 || isHealthOnlyBoss(mob)) {
			return;
		}

		ResolvedIncrements resolvedIncrements = config.resolveIncrements(mob);
		boolean fullStatScaling = mob instanceof Monster || resolvedIncrements.usesIndividualProfile();
		applyScalingAdjustments(mob, resolvedIncrements, totalAdjustment, fullStatScaling);
	}

	public static double resolveCreeperExplosionPowerScaling(Mob mob, double baseExplosionPower) {
		if (mob == null || mob.getType() != madoku.craft.entity.MadokuEntityTypes.CREEPER || !snapshot.enabled()) {
			return 0.0D;
		}
		if (!(mob instanceof DifficultyScaledMob scaledMob)) {
			return 0.0D;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return 0.0D;
		}
		ResolvedIncrements resolvedIncrements = snapshot.resolveIncrements(mob);
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double sanitizedBase = Math.max(0.0D, baseExplosionPower);
		double powerAddition = resolveScaledAddition(sanitizedBase, increments.explosionPower(), modes.explosionPowerMode(), totalAdjustment);
		return roundDifficultyScaleValue(sanitizedBase, powerAddition);
	}

	public static double resolveMobRangedDamageScaling(Mob mob, double baseDamage) {
		double sanitizedBase = Math.max(0.0D, baseDamage);
		if (mob == null || !snapshot.enabled()) {
			return sanitizedBase;
		}
		if (!(mob instanceof DifficultyScaledMob scaledMob)) {
			return sanitizedBase;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return sanitizedBase;
		}
		ResolvedIncrements resolvedIncrements = snapshot.resolveIncrements(mob);
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double addition = resolveScaledAddition(sanitizedBase, increments.rangedDamage(), modes.rangedDamageMode(), totalAdjustment);
		double resolved = Math.max(0.0D, sanitizedBase + addition);
		return roundDifficultyScaleValue(sanitizedBase, resolved);
	}

	public static double resolveMobAttackAccuracyScaling(Mob mob, double baseAccuracy) {
		double sanitizedBase = Mth.clamp(baseAccuracy, 0.0D, 1.0D);
		if (mob == null || !snapshot.enabled()) {
			return sanitizedBase;
		}
		if (!(mob instanceof DifficultyScaledMob scaledMob)) {
			return sanitizedBase;
		}
		int totalAdjustment = Math.max(0, scaledMob.madokuCraft$getSpawnDifficultyAdjustment());
		if (totalAdjustment <= 0) {
			return sanitizedBase;
		}
		ResolvedIncrements resolvedIncrements = snapshot.resolveIncrements(mob);
		StatIncrements increments = resolvedIncrements.increments();
		StatModes modes = resolvedIncrements.modes();
		double addition = resolveScaledAddition(sanitizedBase, increments.attackAccuracy(), modes.attackAccuracyMode(), totalAdjustment);
		double resolved = sanitizedBase + addition;
		return Mth.clamp(roundDifficultyScaleValue(sanitizedBase, resolved), 0.0D, 1.0D);
	}

	private static void loadConfig() {
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(DIFFICULTY_CONFIG_ROOT_FOLDER_NAME);
			Path settingsFile = resolveJsonFile(rootDirectory, DIFFICULTY_CONFIG_SETTINGS_FILE_NAME);
				JsonObject settingsRoot = JsonStaticSystem.ensureManagedFile(
					settingsFile,
					RegionalDifficultyConfigManager.buildSettingsDefaults()
				);
				JsonObject settingsScaling = readObject(settingsRoot, RegionalDifficultyConfigManager.FIELD_DIFFICULTY_SCALING);
				double defaultHealthIncrement = readScalingValue(
					settingsScaling,
					RegionalDifficultyConfigManager.FIELD_HEALTH,
					RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT
				);
				double defaultMovementSpeedIncrement = readScalingValue(
					settingsScaling,
					RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED,
					RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT
				);
				double defaultArmorIncrement = readScalingValue(
					settingsScaling,
					RegionalDifficultyConfigManager.FIELD_ARMOR,
					RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT
				);
				double defaultDamageIncrement = readScalingValue(
					settingsScaling,
					RegionalDifficultyConfigManager.FIELD_DAMAGE,
					RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT
				);
					double defaultKnockbackResistanceIncrement = readScalingValue(
						settingsScaling,
						RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE,
						RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT
					);
					double defaultExperienceDropIncrement = readScalingValue(
						settingsScaling,
						RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP,
						RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
					);

			Path rulesDirectory = rootDirectory.resolve(DIFFICULTY_CONFIG_RULES_FOLDER_NAME);
			Path biomeDirectory = rulesDirectory.resolve(BIOME_RULES_FOLDER_NAME);
			Path structureDirectory = rulesDirectory.resolve(STRUCTURE_RULES_FOLDER_NAME);
			Path timeDirectory = rulesDirectory.resolve(TIME_RULES_FOLDER_NAME);

			Map<String, JsonObject> normalizedBiomes = DynamicStaticSystem.ensureManagedFolder(
				biomeDirectory,
				RegionalBiomeDifficultyConfigManager.buildDefaultFileDefaults(),
				ignored -> RegionalBiomeDifficultyConfigManager.buildRuleDefaults(0, List.of()),
				MadokuRegionalDifficultyManager::isSupportedBiomeRuleFile,
				null
			);
			Map<String, JsonObject> normalizedStructures = DynamicStaticSystem.ensureManagedFolder(
				structureDirectory,
				RegionalStructureDifficultyConfigManager.buildDefaultFileDefaults(),
				ignored -> RegionalStructureDifficultyConfigManager.buildRuleDefaults(0, List.of()),
				MadokuRegionalDifficultyManager::isSupportedStructureRuleFile,
				null
			);
			Map<String, JsonObject> normalizedTime = DynamicStaticSystem.ensureManagedFolder(
				timeDirectory,
				RegionalTimeDifficultyConfigManager.buildDefaultFileDefaults(),
				ignored -> RegionalTimeDifficultyConfigManager.buildRuleDefaults(0, RegionalDifficultyConfigManager.TIME_UNBOUNDED_MAX_DAY, 0),
				MadokuRegionalDifficultyManager::isSupportedTimeRuleFile,
				null
			);
				Map<String, JsonObject> normalizedMobScaling = DynamicStaticSystem.ensureManagedFolder(
					rulesDirectory.resolve(DIFFICULTY_CONFIG_MOB_SCALING_FOLDER_NAME),
					RegionalScalingConfigManager.buildDefaultMobScalingFileDefaults(
						defaultHealthIncrement,
						defaultMovementSpeedIncrement,
						defaultArmorIncrement,
						defaultDamageIncrement,
						defaultKnockbackResistanceIncrement,
						defaultExperienceDropIncrement
					),
					RegionalScalingConfigManager::buildDynamicMobScalingDefaults,
					MadokuRegionalDifficultyManager::isSupportedMobScalingFile,
					null
				);

			snapshot = buildSnapshot(settingsRoot, normalizedBiomes, normalizedStructures, normalizedTime, normalizedMobScaling);
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
			LOGGER.error("Failed to load MadokuRegionalDifficultyManager config; disabling difficulty scaling.", exception);
		}
	}

	private static Snapshot buildSnapshot(
		JsonObject settingsRoot,
		Map<String, JsonObject> biomeRulesByFile,
		Map<String, JsonObject> structureRulesByFile,
		Map<String, JsonObject> timeRulesByFile,
		Map<String, JsonObject> mobScalingByFile
	) {
		boolean enabled = readBoolean(settingsRoot, RegionalDifficultyConfigManager.FIELD_ENABLED, true);
		boolean biomesEnabled = readBoolean(settingsRoot, RegionalDifficultyConfigManager.FIELD_BIOMES_ENABLED, true);
		boolean structuresEnabled = readBoolean(settingsRoot, RegionalDifficultyConfigManager.FIELD_STRUCTURES_ENABLED, true);
		boolean timeEnabled = readBoolean(settingsRoot, RegionalDifficultyConfigManager.FIELD_TIME_ENABLED, true);
		int defaultUnknownAdjustment = Math.max(
			0,
			readInt(settingsRoot, RegionalDifficultyConfigManager.FIELD_DEFAULT_UNKNOWN_ADJUSTMENT, RegionalDifficultyConfigManager.DEFAULT_UNKNOWN_ADJUSTMENT)
		);

		JsonObject scaling = readObject(settingsRoot, RegionalDifficultyConfigManager.FIELD_DIFFICULTY_SCALING);
			StatIncrements increments = new StatIncrements(
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_HEALTH, RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_FLYING_SPEED, RegionalDifficultyConfigManager.DEFAULT_FLYING_SPEED_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_SCALE, RegionalDifficultyConfigManager.DEFAULT_SCALE_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_ARMOR, RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_DAMAGE, RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT),
				readScalingValue(scaling, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT),
				0.0D,
				0.0D,
				0.0D
			);
		StatModes globalModes = parseStatModesFromMobScalingRoot(scaling, StatModes.defaults());

		Map<Identifier, Integer> biomeAdjustments = parseGroupedIdentifierAdjustments(
			biomeRulesByFile,
			RegionalDifficultyConfigManager.FIELD_BIOME_LIST,
			defaultUnknownAdjustment
		);
		Map<Identifier, Integer> structureAdjustments = parseGroupedIdentifierAdjustments(
			structureRulesByFile,
			RegionalDifficultyConfigManager.FIELD_STRUCTURE_LIST,
			defaultUnknownAdjustment
		);
		TimeScaling timeScaling = parseTimeScaling(timeRulesByFile, defaultUnknownAdjustment);
		Map<String, ScalingProfile> mobScalingIncrements = parseMobScaling(mobScalingByFile, increments);
		RegionalBiomeDifficultyRuntime biomeRuntime = new RegionalBiomeDifficultyRuntime(
			biomesEnabled,
			defaultUnknownAdjustment,
			Map.copyOf(biomeAdjustments)
		);
		RegionalStructureDifficultyRuntime structureRuntime = new RegionalStructureDifficultyRuntime(
			structuresEnabled,
			defaultUnknownAdjustment,
			Map.copyOf(structureAdjustments)
		);
		RegionalTimeDifficultyRuntime timeRuntime = new RegionalTimeDifficultyRuntime(
			timeEnabled,
			timeScaling.toRuntimeTiers()
		);

		return new Snapshot(
			enabled,
			increments,
			globalModes,
			biomeRuntime,
			structureRuntime,
			timeRuntime,
			Map.copyOf(mobScalingIncrements)
		);
	}

	private static Map<Identifier, Integer> parseGroupedIdentifierAdjustments(
		Map<String, JsonObject> rulesByFile,
		String listField,
		int defaultUnknownAdjustment
	) {
		Map<Identifier, Integer> resolved = new LinkedHashMap<>();
		for (JsonObject root : rulesByFile.values()) {
			if (root == null) {
				continue;
			}

			int adjustment = Math.max(0, readInt(root, RegionalDifficultyConfigManager.FIELD_ADJUSTMENT, defaultUnknownAdjustment));
			Set<Identifier> ids = parseIdentifierList(root.get(listField));
			for (Identifier id : ids) {
				resolved.merge(id, adjustment, Math::max);
			}
		}
		return resolved;
	}

	private static Set<Identifier> parseIdentifierList(JsonElement source) {
		Set<Identifier> parsed = new LinkedHashSet<>();
		if (!(source instanceof JsonArray array)) {
			return parsed;
		}
		for (JsonElement entry : array) {
			if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				continue;
			}
			Identifier identifier = normalizeIdentifier(entry.getAsString());
			if (identifier != null) {
				parsed.add(identifier);
			}
		}
		return parsed;
	}

	private static TimeScaling parseTimeScaling(Map<String, JsonObject> timeRulesByFile, int defaultUnknownAdjustment) {
		List<TimeTier> tiers = new ArrayList<>();
		for (JsonObject root : timeRulesByFile.values()) {
			if (root == null) {
				continue;
			}
			int adjustment = Math.max(0, readInt(root, RegionalDifficultyConfigManager.FIELD_ADJUSTMENT, defaultUnknownAdjustment));
			int minDay = Math.max(0, readInt(root, RegionalDifficultyConfigManager.FIELD_MIN_DAY, 0));
			int configuredMaxDay = readInt(root, RegionalDifficultyConfigManager.FIELD_MAX_DAY, RegionalDifficultyConfigManager.TIME_UNBOUNDED_MAX_DAY);
			int maxDay = configuredMaxDay < 0 ? Integer.MAX_VALUE : Math.max(minDay, configuredMaxDay);
			tiers.add(new TimeTier(minDay, maxDay, adjustment));
		}

		if (tiers.isEmpty()) {
			for (RegionalDifficultyConfigManager.TimeTierDefinition definition : RegionalTimeDifficultyConfigManager.defaultTimeTiers()) {
				int maxDay = definition.maxDay() < 0 ? Integer.MAX_VALUE : Math.max(definition.minDay(), definition.maxDay());
				tiers.add(new TimeTier(Math.max(0, definition.minDay()), maxDay, Math.max(0, definition.adjustment())));
			}
		}

		tiers.sort(Comparator.comparingInt(TimeTier::minDay));
		return new TimeScaling(List.copyOf(tiers));
	}

	private static Map<String, ScalingProfile> parseMobScaling(
		Map<String, JsonObject> mobScalingByFile,
		StatIncrements fallbackIncrements
	) {
		Map<String, ScalingProfile> resolved = new LinkedHashMap<>();
		if (mobScalingByFile == null || mobScalingByFile.isEmpty()) {
			return resolved;
		}
		StatModes fallbackModes = StatModes.defaults();
		for (Map.Entry<String, JsonObject> entry : mobScalingByFile.entrySet()) {
			String fileKey = normalizeFileKey(entry.getKey());
			JsonObject root = entry.getValue();
			if (fileKey.isBlank() || root == null || !readBoolean(root, RegionalDifficultyConfigManager.FIELD_ENABLED, true)) {
				continue;
			}

			StatIncrements increments = parseStatIncrementsFromMobScalingRoot(root, fallbackIncrements);
			StatModes modes = parseStatModesFromMobScalingRoot(root, fallbackModes);
			ScalingProfile profile = new ScalingProfile(increments, modes);
			resolved.put(fileKey, profile);

			Identifier configuredMobId = normalizeIdentifier(readString(root, RegionalDifficultyConfigManager.FIELD_MOB_ID, ""));
			if (configuredMobId != null) {
				for (String alias : MadokuRegionalScalingManager.resolveMobScalingFileKeys(configuredMobId)) {
					if (!alias.isBlank()) {
						resolved.put(alias, profile);
					}
				}
			}
		}
		return resolved;
	}

	private static StatIncrements parseStatIncrementsFromMobScalingRoot(JsonObject root, StatIncrements fallbackIncrements) {
		return new StatIncrements(
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_HEALTH, fallbackIncrements.health()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, fallbackIncrements.movementSpeed()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_FLYING_SPEED, fallbackIncrements.flyingSpeed()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_SCALE, fallbackIncrements.scale()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_ARMOR, fallbackIncrements.armor()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_DAMAGE, fallbackIncrements.damage()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, fallbackIncrements.knockbackResistance()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, fallbackIncrements.experienceDrop()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_RANGED_DAMAGE, fallbackIncrements.rangedDamage()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_ATTACK_ACCURACY, fallbackIncrements.attackAccuracy()),
			readScalingValue(root, RegionalDifficultyConfigManager.FIELD_EXPLOSION_POWER, fallbackIncrements.explosionPower())
		);
	}

	private static StatModes parseStatModesFromMobScalingRoot(JsonObject root, StatModes fallbackModes) {
		return new StatModes(
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_HEALTH, fallbackModes.healthMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED, fallbackModes.movementSpeedMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_FLYING_SPEED, fallbackModes.flyingSpeedMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_SCALE, fallbackModes.scaleMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_ARMOR, fallbackModes.armorMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_DAMAGE, fallbackModes.damageMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_KNOCKBACK_RESISTANCE, fallbackModes.knockbackResistanceMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP, fallbackModes.experienceDropMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_RANGED_DAMAGE, fallbackModes.rangedDamageMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_ATTACK_ACCURACY, fallbackModes.attackAccuracyMode()),
			readScalingMode(root, RegionalDifficultyConfigManager.FIELD_EXPLOSION_POWER, fallbackModes.explosionPowerMode())
		);
	}

	private static boolean isSupportedBiomeRuleFile(String fileKey, JsonObject sourceRoot) {
		return isRuleFileWithArrayField(sourceRoot, RegionalDifficultyConfigManager.FIELD_BIOME_LIST);
	}

	private static boolean isSupportedStructureRuleFile(String fileKey, JsonObject sourceRoot) {
		return isRuleFileWithArrayField(sourceRoot, RegionalDifficultyConfigManager.FIELD_STRUCTURE_LIST);
	}

	private static boolean isSupportedTimeRuleFile(String fileKey, JsonObject sourceRoot) {
		return sourceRoot != null;
	}

	private static boolean isSupportedMobScalingFile(String fileKey, JsonObject sourceRoot) {
		return sourceRoot != null;
	}

	private static boolean isRuleFileWithArrayField(JsonObject sourceRoot, String fieldName) {
		if (sourceRoot == null) {
			return false;
		}
		JsonElement field = sourceRoot.get(fieldName);
		return field != null && field.isJsonArray();
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

	private static int resolveTimeAdjustment(ServerLevel world, Snapshot config) {
		if (world == null || !config.timeEnabled() || !config.enabled()) {
			return 0;
		}
		return resolveCurrentTimeAdjustment(world);
	}

	private static long resolveDifficultyDayCount(MinecraftServer server) {
		if (server == null) {
			return 0L;
		}
		if (MadokuTime.isEnabled()) {
			return Math.floorDiv(MadokuTime.getCurrentAbsoluteDayTime(), TICKS_PER_DAY);
		}
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			return Math.floorDiv(overworld.getOverworldClockTime(), TICKS_PER_DAY);
		}
		return 0L;
	}

	private static void runTimeTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context == null) {
			return;
		}
		timeSchedulerId = context.getSchedulerId();
		timeTaskScheduled = false;

		Snapshot config = snapshot;
		if (!config.enabled() || !config.timeEnabled()) {
			cachedTimeDayCount = Long.MIN_VALUE;
			cachedTimeAdjustment = 0;
			return;
		}
		refreshCachedTimeAdjustment(server, config);
		requestTimeProcessing(server, resolveTimeSchedulerInterval(server));
	}

	private static long resolveTimeSchedulerInterval(MinecraftServer server) {
		return SchedulerManagerSystem.resolveAdaptiveDelayTicks(
			server,
			TIME_SCHEDULER_OWNER_ID,
			TIME_SCHEDULER_MIN_INTERVAL_TICKS,
			TIME_SCHEDULER_MAX_INTERVAL_TICKS
		);
	}

	private static void refreshCachedTimeAdjustment(MinecraftServer server, Snapshot config) {
		if (server == null || config == null || !config.enabled() || !config.timeEnabled()) {
			cachedTimeDayCount = Long.MIN_VALUE;
			cachedTimeAdjustment = 0;
			return;
		}
		long dayCount = resolveDifficultyDayCount(server);
		cachedTimeDayCount = dayCount;
		cachedTimeAdjustment = config.timeAdjustment(dayCount);
	}

	private static void requestTimeProcessing(MinecraftServer server, long delay) {
		Snapshot config = snapshot;
		if (server == null || !config.enabled() || !config.timeEnabled() || timeTaskScheduled) {
			return;
		}

		String schedulerId = ensureTimeSchedulerExists();
		if (enqueueTimeTask(schedulerId, delay)) {
			timeTaskScheduled = true;
			return;
		}

		timeSchedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(TIME_SCHEDULER_OWNER_ID));
		if (enqueueTimeTask(timeSchedulerId, delay)) {
			timeTaskScheduled = true;
			return;
		}
		LOGGER.error("Failed to enqueue MadokuRegionalDifficultyManager time scheduler task.");
	}

	private static String ensureTimeSchedulerExists() {
		if (timeSchedulerId == null || timeSchedulerId.isBlank()) {
			timeSchedulerId = SchedulerManagerSystem.createOrGetScheduler(SchedulerManagerSystem.SchedulerBinding.global(TIME_SCHEDULER_OWNER_ID));
		}
		return timeSchedulerId;
	}

	private static boolean enqueueTimeTask(String schedulerId, long delay) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return false;
		}
		SchedulerManagerSystem.EnqueueStatus status = SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delay),
			TASK_TYPE_TIME_TICK,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static boolean isHealthOnlyBoss(Mob mob) {
		if (mob == null) {
			return false;
		}
		EntityType<?> type = mob.getType();
		return type == madoku.craft.entity.MadokuEntityTypes.ENDER_DRAGON
			|| type == madoku.craft.entity.MadokuEntityTypes.ELDER_GUARDIAN
			|| type == madoku.craft.entity.MadokuEntityTypes.WITHER
			|| type == madoku.craft.entity.MadokuEntityTypes.WARDEN;
	}

	private static StructureContext resolveStructureContext(
		ServerLevel world,
		net.minecraft.core.BlockPos pos,
		RegionalStructureDifficultyRuntime structureRuntime
	) {
		if (world == null || pos == null || structureRuntime == null || !structureRuntime.enabled()) {
			return StructureContext.NONE;
		}
		if (!isChunkLoadedAt(world, pos)) {
			return StructureContext.NONE;
		}
		Map<Identifier, Integer> configuredAdjustments = structureRuntime.adjustments();
		int defaultUnknownAdjustment = structureRuntime.defaultUnknownAdjustment();

		if (!configuredAdjustments.isEmpty()) {
			Predicate<Holder<Structure>> configuredPredicate = entry -> entry.unwrapKey()
				.map(ResourceKey::identifier)
				.map(configuredAdjustments::containsKey)
				.orElse(false);
			StructureStart configuredStart = findStructureContaining(world, pos, configuredPredicate);
			if (isValidStructureStart(configuredStart)) {
				Identifier structureId = resolveStructureId(world, configuredStart);
				return structureContextFromId(structureId, configuredAdjustments, defaultUnknownAdjustment);
			}
		}

		StructureStart start = findStructureContaining(world, pos, entry -> true);
		if (!isValidStructureStart(start)) {
			return StructureContext.NONE;
		}

		Identifier structureId = resolveStructureId(world, start);
		return structureContextFromId(structureId, configuredAdjustments, defaultUnknownAdjustment);
	}

	private static StructureStart findStructureContaining(
		ServerLevel world,
		net.minecraft.core.BlockPos pos,
		Predicate<Holder<Structure>> predicate
	) {
		try {
			return world.structureManager().getStructureWithPieceAt(pos, predicate);
		} catch (RuntimeException exception) {
			return StructureStart.INVALID_START;
		}
	}

	private static boolean isChunkLoadedAt(ServerLevel world, net.minecraft.core.BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		return ChunkManagerSystem.isChunkLoaded(world, chunkX, chunkZ);
	}

	private static boolean isValidStructureStart(StructureStart start) {
		return start != null && start != StructureStart.INVALID_START && start.isValid();
	}

	private static Identifier resolveStructureId(ServerLevel world, StructureStart start) {
		if (world == null || start == null) {
			return null;
		}
		Registry<Structure> structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		return structureRegistry.getKey(start.getStructure());
	}

	private static StructureContext structureContextFromId(
		Identifier structureId,
		Map<Identifier, Integer> configuredAdjustments,
		int defaultUnknownAdjustment
	) {
		if (structureId == null) {
			return new StructureContext(null, defaultUnknownAdjustment);
		}
		return new StructureContext(
			structureId,
			configuredAdjustments.getOrDefault(structureId, defaultUnknownAdjustment)
		);
	}

	private static boolean addAttribute(Mob mob, Holder<Attribute> attribute, double amount) {
		if (mob == null || attribute == null || !Double.isFinite(amount) || amount == 0.0D) {
			return false;
		}
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return false;
		}

		double newBase = instance.getBaseValue() + amount;
		if (attribute.value() == Attributes.KNOCKBACK_RESISTANCE.value()) {
			newBase = Math.max(0.0D, Math.min(1.0D, newBase));
		}
		instance.setBaseValue(newBase);
		return true;
	}

	private static ScalingApplication applyScalingAdjustments(
		Mob mob,
		ResolvedIncrements resolvedIncrements,
		int totalAdjustment,
		boolean fullStatScaling
	) {
		if (mob == null || resolvedIncrements == null || totalAdjustment <= 0) {
			return null;
		}
			StatIncrements increments = resolvedIncrements.increments();
			StatModes modes = resolvedIncrements.modes();
			double armorBaseBefore = readAttributeBaseValue(mob, Attributes.ARMOR);
			double healthAddition = resolveHealthScalingAmount(mob, increments, modes, totalAdjustment);
			double movementSpeedAddition = fullStatScaling ? resolveMovementSpeedScalingAmount(mob, increments, modes, totalAdjustment) : 0.0D;
			double flyingSpeedAddition = fullStatScaling && mob.getType() == madoku.craft.entity.MadokuEntityTypes.BEE
				? resolveFlyingSpeedScalingAmount(mob, increments, modes, totalAdjustment)
				: 0.0D;
			double scaleAddition = fullStatScaling ? resolveScaleScalingAmount(mob, increments, modes, totalAdjustment) : 0.0D;
			double armorAddition = fullStatScaling ? resolveArmorScalingAmount(mob, increments, modes, totalAdjustment) : 0.0D;
			double damageAddition = fullStatScaling ? resolveDamageScalingAmount(mob, increments, modes, totalAdjustment) : 0.0D;
			double knockbackResistanceAddition = fullStatScaling ? resolveKnockbackResistanceScalingAmount(mob, increments, modes, totalAdjustment) : 0.0D;
			int experienceBaseBefore = resolveMobExperienceDrop(mob);
			int experienceDropAddition = resolveExperienceDropScalingAmount(experienceBaseBefore, increments, modes, totalAdjustment);

		boolean healthChanged = addAttribute(mob, Attributes.MAX_HEALTH, healthAddition);
		if (fullStatScaling) {
			addAttribute(mob, Attributes.MOVEMENT_SPEED, movementSpeedAddition);
			if (mob.getType() == madoku.craft.entity.MadokuEntityTypes.BEE) {
				addAttribute(mob, Attributes.FLYING_SPEED, flyingSpeedAddition);
			}
			addAttribute(mob, Attributes.SCALE, scaleAddition);
			addAttribute(mob, Attributes.ARMOR, armorAddition);
			addAttribute(mob, Attributes.ATTACK_DAMAGE, damageAddition);
			addAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, knockbackResistanceAddition);
		}
		int experienceBaseAfter = applyExperienceDropScaling(mob, experienceBaseBefore, experienceDropAddition);
		double armorBaseAfter = readAttributeBaseValue(mob, Attributes.ARMOR);

		if (healthChanged) {
			double maxHealth = mob.getAttributeValue(Attributes.MAX_HEALTH);
			mob.setHealth((float) maxHealth);
		}

		return new ScalingApplication(
			increments,
			healthAddition,
			movementSpeedAddition,
			scaleAddition,
			armorAddition,
			armorBaseBefore,
			armorBaseAfter,
			damageAddition,
			knockbackResistanceAddition,
			experienceBaseBefore,
			experienceBaseAfter,
			experienceDropAddition
		);
	}

	private static double readAttributeBaseValue(Mob mob, Holder<Attribute> attribute) {
		if (mob == null || attribute == null) {
			return 0.0D;
		}
		AttributeInstance instance = mob.getAttribute(attribute);
		return instance == null ? 0.0D : instance.getBaseValue();
	}

	private static double resolveHealthScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentMaxHealth = Math.max(1.0D, mob.getAttributeValue(Attributes.MAX_HEALTH));
		return resolveScaledAddition(currentMaxHealth, increments.health(), modes.healthMode(), totalAdjustment);
	}

	private static double resolveDamageScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentDamage = Math.max(0.0D, mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
		return resolveScaledAddition(currentDamage, increments.damage(), modes.damageMode(), totalAdjustment);
	}

	private static double resolveMovementSpeedScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentSpeed = Math.max(0.0D, mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
		return resolveScaledAddition(currentSpeed, increments.movementSpeed(), modes.movementSpeedMode(), totalAdjustment);
	}

	private static double resolveFlyingSpeedScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentSpeed = Math.max(0.0D, mob.getAttributeValue(Attributes.FLYING_SPEED));
		return resolveScaledAddition(currentSpeed, increments.flyingSpeed(), modes.flyingSpeedMode(), totalAdjustment);
	}

	private static double resolveScaleScalingAmount(
		Mob mob,
		StatIncrements increments,
		StatModes modes,
		int totalAdjustment
	) {
		if (mob == null) {
			return 0.0D;
		}
		double currentScale = Math.max(0.0D, mob.getAttributeValue(Attributes.SCALE));
		return resolveScaledAddition(currentScale, increments.scale(), modes.scaleMode(), totalAdjustment);
	}

	private static double resolveArmorScalingAmount(Mob mob, StatIncrements increments, StatModes modes, int totalAdjustment) {
		double currentArmor = mob == null ? 0.0D : Math.max(0.0D, mob.getAttributeValue(Attributes.ARMOR));
		return resolveScaledAddition(currentArmor, increments.armor(), modes.armorMode(), totalAdjustment);
	}

	private static double resolveKnockbackResistanceScalingAmount(Mob mob, StatIncrements increments, StatModes modes, int totalAdjustment) {
		double currentKnockbackResistance = mob == null ? 0.0D : Math.max(0.0D, mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		return resolveScaledAddition(currentKnockbackResistance, increments.knockbackResistance(), modes.knockbackResistanceMode(), totalAdjustment);
	}

	private static int resolveExperienceDropScalingAmount(int baseExperienceDrop, StatIncrements increments, StatModes modes, int totalAdjustment) {
		if (baseExperienceDrop <= 0) {
			return 0;
		}
		double addition = resolveScaledAddition(baseExperienceDrop, increments.experienceDrop(), modes.experienceDropMode(), totalAdjustment);
		if (!Double.isFinite(addition) || addition <= 0.0D) {
			return 0;
		}
		return Math.max(0, (int) Math.round(addition));
	}

	private static double resolveScaledAddition(double baseValue, double configuredValue, ScalingMode mode, int totalAdjustment) {
		double safeBase = Math.max(0.0D, baseValue);
		double safeConfigured = Math.max(0.0D, configuredValue);
		if (totalAdjustment <= 0 || safeConfigured <= 0.0D) {
			return 0.0D;
		}
		double addition = switch (mode) {
			case MULTIPLY -> safeBase * (safeConfigured / 100.0D) * totalAdjustment;
			case ADD -> safeConfigured * totalAdjustment;
		};
		return roundDifficultyScaleValue(safeBase, addition);
	}

	private static int applyExperienceDropScaling(Mob mob, int baseExperienceDrop, int experienceDropAddition) {
		if (!(mob instanceof MobExperienceAccessor accessor)) {
			return Math.max(0, baseExperienceDrop);
		}
		int resolvedBase = Math.max(0, baseExperienceDrop);
		int resolvedAddition = Math.max(0, experienceDropAddition);
		int scaled = Math.max(0, resolvedBase + resolvedAddition);
		accessor.madokuCraft$setXpReward(scaled);
		return scaled;
	}

	private static int resolveMobExperienceDrop(Mob mob) {
		if (!(mob instanceof MobExperienceAccessor accessor)) {
			return 0;
		}
		return Math.max(0, accessor.madokuCraft$getXpReward());
	}

	private static double roundDifficultyScaleValue(double originalValue, double value) {
		if (!Double.isFinite(value)) {
			return value;
		}
		double step = isWholeNumber(originalValue) ? 0.05D : 0.005D;
		return Math.round(value / step) * step;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	private static Identifier normalizeIdentifier(String rawValue) {
		if (rawValue == null) {
			return null;
		}
		String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return null;
		}
		if (!normalized.contains(":")) {
			normalized = "minecraft:" + normalized;
		}
		return Identifier.tryParse(normalized);
	}

	private static String normalizeFileKey(String rawValue) {
		return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		if (element != null && element.isJsonObject()) {
			return element.getAsJsonObject();
		}
		return new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double readFiniteDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static ScalingMode readScalingMode(JsonObject root, String statField, ScalingMode fallback) {
		if (root == null || statField == null || statField.isBlank()) {
			return fallback;
		}
		JsonElement statElement = root.get(statField);
		if (statElement == null) {
			if (RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED.equals(statField)) {
				statElement = root.get("movement_speed");
			} else if (RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP.equals(statField)) {
				statElement = root.get("experience_drop");
			}
		}
		if (statElement == null) {
			return fallback;
		}
		if (!statElement.isJsonObject()) {
			return fallback;
		}
		JsonObject statObject = statElement.getAsJsonObject();
		String rawType = readString(statObject, RegionalDifficultyConfigManager.FIELD_SCALING_TYPE, "");
		String normalized = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
		if (RegionalDifficultyConfigManager.SCALING_TYPE_MULTIPLY.equals(normalized)) {
			return ScalingMode.MULTIPLY;
		}
		if (RegionalDifficultyConfigManager.SCALING_TYPE_ADD.equals(normalized)) {
			return ScalingMode.ADD;
		}
		return fallback;
	}

	private static double readScalingValue(JsonObject root, String statField, double fallback) {
		if (root == null || statField == null || statField.isBlank()) {
			return fallback;
		}
		JsonElement statElement = root.get(statField);
		if (statElement == null) {
			if (RegionalDifficultyConfigManager.FIELD_MOVEMENT_SPEED.equals(statField)) {
				statElement = root.get("movement_speed");
			} else if (RegionalDifficultyConfigManager.FIELD_EXPERIENCE_DROP.equals(statField)) {
				statElement = root.get("experience_drop");
			}
		}
		if (statElement == null) {
			return fallback;
		}
		if (statElement.isJsonObject()) {
			JsonObject statObject = statElement.getAsJsonObject();
			return readFiniteDouble(statObject, RegionalDifficultyConfigManager.FIELD_SCALING_VALUE, fallback);
		}
		return fallback;
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

	private static void emitConfigLoaded() {
		String metricId = "difficulty.config_loaded";
		if (!MadokuDebug.shouldEmit("difficulty", "regional-difficulty", "regional-difficulty")) {
			return;
		}
		Snapshot config = snapshot;
			MadokuDebug.event(metricId, "difficulty", "regional-difficulty", "regional-difficulty")
				.side(MadokuDebug.Side.SERVER)
				.subject("difficulty:global")
				.field("enabled", config.enabled())
				.field("biomes_enabled", config.biomeRuntime().enabled())
				.field("structures_enabled", config.structureRuntime().enabled())
				.field("time_enabled", config.timeRuntime().enabled())
				.field("biome_rules", config.biomeRuntime().adjustments().size())
				.field("structure_rules", config.structureRuntime().adjustments().size())
				.field("mob_scaling_profiles", config.mobScalingIncrements().size())
				.log();
		}

	private static void emitSpawnScaled(
		Mob mob,
		Identifier biomeId,
		StructureContext structureContext,
		int baseAdjustment,
		int biomeAdjustment,
		int structureAdjustment,
		int timeAdjustment,
		int totalAdjustment,
		String scalingSource,
		StatIncrements increments,
		double healthAddition,
		double movementSpeedAddition,
		double scaleAddition,
		double armorAddition,
		double armorBaseBefore,
		double armorBaseAfter,
		double damageAddition,
		double knockbackResistanceAddition,
		int experienceBaseBefore,
		int experienceBaseAfter,
		int experienceDropAddition
	) {
		String metricId = "difficulty.spawn_scaled";
		if (!MadokuDebug.shouldEmit("difficulty", "regional-difficulty", "regional-difficulty") || mob == null || increments == null) {
			return;
		}

		MadokuDebug.event(metricId, "difficulty", "regional-difficulty", "regional-difficulty")
			.side(MadokuDebug.Side.SERVER)
			.subject("mob:" + mob.getType().toShortString())
			.field("base_adj", baseAdjustment)
			.field("biome", biomeId == null ? "unknown" : biomeId)
			.field("biome_adj", biomeAdjustment)
			.field("structure", structureContext.structureId() == null ? "none" : structureContext.structureId())
			.field("structure_adj", structureAdjustment)
			.field("time_adj", timeAdjustment)
			.field("total_adj", totalAdjustment)
			.field("scaling_source", scalingSource == null || scalingSource.isBlank() ? "global" : scalingSource)
				.field("health_cfg", increments.health())
				.field("movement_speed_cfg", increments.movementSpeed())
				.field("scale_cfg", increments.scale())
				.field("armor_cfg", increments.armor())
			.field("damage_cfg", increments.damage())
				.field("knockback_resistance_cfg", increments.knockbackResistance())
				.field("experience_drop_cfg", increments.experienceDrop())
				.field("ranged_damage_cfg", increments.rangedDamage())
				.field("attack_accuracy_cfg", increments.attackAccuracy())
				.field("explosion_power_cfg", increments.explosionPower())
				.field("health_add", healthAddition)
				.field("movement_speed_add", movementSpeedAddition)
				.field("scale_add", scaleAddition)
				.field("armor_add", armorAddition)
			.field("armor_base_before", armorBaseBefore)
			.field("armor_base_after", armorBaseAfter)
			.field("damage_add", damageAddition)
			.field("knockback_resistance_add", knockbackResistanceAddition)
			.field("experience_drop_base_before", experienceBaseBefore)
			.field("experience_drop_base_after", experienceBaseAfter)
			.field("experience_drop_add", experienceDropAddition)
			.log();
	}

	private record Snapshot(
		boolean enabled,
		StatIncrements increments,
		StatModes globalModes,
		RegionalBiomeDifficultyRuntime biomeRuntime,
		RegionalStructureDifficultyRuntime structureRuntime,
		RegionalTimeDifficultyRuntime timeRuntime,
		Map<String, ScalingProfile> mobScalingIncrements
	) {
			private static Snapshot disabled() {
					return new Snapshot(
						false,
						new StatIncrements(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D),
						StatModes.defaults(),
						new RegionalBiomeDifficultyRuntime(false, RegionalDifficultyConfigManager.DEFAULT_UNKNOWN_ADJUSTMENT, Map.of()),
						new RegionalStructureDifficultyRuntime(false, RegionalDifficultyConfigManager.DEFAULT_UNKNOWN_ADJUSTMENT, Map.of()),
						RegionalTimeDifficultyRuntime.defaults(false),
				Map.of()
			);
		}

			private ResolvedIncrements resolveIncrements(Mob mob) {
				if (mob == null || mobScalingIncrements.isEmpty()) {
					return new ResolvedIncrements(increments, globalModes, "global");
				}
			for (String key : MadokuRegionalScalingManager.resolveMobScalingFileKeys(mob.getType())) {
				ScalingProfile specific = mobScalingIncrements.get(key);
				if (specific != null) {
					return new ResolvedIncrements(specific.increments(), specific.modes(), key);
				}
			}
			return new ResolvedIncrements(increments, globalModes, "global_fallback");
		}

		private int biomeAdjustment(Identifier biomeId) {
			return biomeRuntime.resolveAdjustment(biomeId);
		}

		private boolean timeEnabled() {
			return timeRuntime.enabled();
		}

		private int timeAdjustment(long dayCount) {
			return timeRuntime.resolveAdjustment(dayCount);
		}
	}

	private record StatIncrements(
		double health,
		double movementSpeed,
		double flyingSpeed,
		double scale,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop,
		double rangedDamage,
		double attackAccuracy,
		double explosionPower
	) {
	}

	private record ResolvedIncrements(StatIncrements increments, StatModes modes, String sourceKey) {
		private boolean usesIndividualProfile() {
			return sourceKey != null
				&& !sourceKey.isBlank()
				&& !"global".equals(sourceKey)
				&& !"global_fallback".equals(sourceKey);
		}
	}

	private record ScalingProfile(StatIncrements increments, StatModes modes) {
	}

	private record StatModes(
		ScalingMode healthMode,
		ScalingMode movementSpeedMode,
		ScalingMode flyingSpeedMode,
		ScalingMode scaleMode,
		ScalingMode armorMode,
		ScalingMode damageMode,
		ScalingMode knockbackResistanceMode,
		ScalingMode experienceDropMode,
		ScalingMode rangedDamageMode,
		ScalingMode attackAccuracyMode,
		ScalingMode explosionPowerMode
	) {
		private static StatModes defaults() {
			return new StatModes(
				ScalingMode.MULTIPLY,
				ScalingMode.MULTIPLY,
				ScalingMode.MULTIPLY,
				ScalingMode.MULTIPLY,
				ScalingMode.ADD,
				ScalingMode.MULTIPLY,
				ScalingMode.ADD,
				ScalingMode.MULTIPLY,
				ScalingMode.MULTIPLY,
				ScalingMode.MULTIPLY,
				ScalingMode.MULTIPLY
			);
		}
	}

	private enum ScalingMode {
		ADD,
		MULTIPLY
	}

	private record ScalingApplication(
		StatIncrements increments,
		double healthAddition,
		double movementSpeedAddition,
		double scaleAddition,
		double armorAddition,
		double armorBaseBefore,
		double armorBaseAfter,
		double damageAddition,
		double knockbackResistanceAddition,
		int experienceBaseBefore,
		int experienceBaseAfter,
		int experienceDropAddition
	) {
	}

	private record TimeTier(int minDay, int maxDay, int adjustment) {
	}

	private record TimeScaling(List<TimeTier> tiers) {
		private List<RegionalTimeDifficultyRuntime.TimeTier> toRuntimeTiers() {
			List<RegionalTimeDifficultyRuntime.TimeTier> runtimeTiers = new ArrayList<>();
			for (TimeTier tier : tiers) {
				runtimeTiers.add(new RegionalTimeDifficultyRuntime.TimeTier(tier.minDay(), tier.maxDay(), tier.adjustment()));
			}
			return List.copyOf(runtimeTiers);
		}
	}

		private record StructureContext(Identifier structureId, int adjustment) {
			private static final StructureContext NONE = new StructureContext(null, 0);
		}
	}




