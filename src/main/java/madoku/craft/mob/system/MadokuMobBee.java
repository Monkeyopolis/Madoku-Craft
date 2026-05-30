package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.scheduler.SchedulerManagerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MadokuMobBee {
	private static final String METRIC_BEE_CROP_MODE = "mob.bee_crop_mode";
	private static final String METRIC_BEE_CROP_GROWTH = "mob.bee_crop_growth";
	private static final String METRIC_BEE_CROP_TARGET = "mob.bee_crop_target";
	private static final String METRIC_BEE_CROP_STATE = "mob.bee_crop_state";
	private static final int DEFAULT_NECTAR_TOTAL_CHARGES = 10;
	private static final int DEFAULT_SEARCH_DURATION_TICKS = 1200;
	private static final int DEFAULT_SEARCH_RADIUS_HORIZONTAL = 12;
	private static final int DEFAULT_SEARCH_RADIUS_VERTICAL = 3;
	private static final double DEFAULT_CROP_REACH_DISTANCE_SQR = 4.0D;
	private static final int DEFAULT_CROP_RESERVATION_TTL_TICKS = 40;
	private static final double DEFAULT_MOVE_SPEED_MODIFIER = 1.1D;
	private static final double DEFAULT_ARRIVAL_THRESHOLD = 0.1D;
	private static final int DEFAULT_POSITION_CHANGE_CHANCE = 25;
	private static final double DEFAULT_HOVER_HEIGHT_WITHIN_FLOWER = 0.6D;
	private static final double DEFAULT_HOVER_POS_OFFSET = 0.33333334D;
	private static final int DEFAULT_CHARGE_INTERVAL_TICKS = 20;
	private static final int DEFAULT_CHARGES_SPEND_DIVISOR = 2;
	private static final double DEFAULT_GROWTH_PERCENT_PER_CHARGE = 2.0D;

	private static final Map<UUID, BeeNectarState> BEE_NECTAR_STATES = new ConcurrentHashMap<>();
	private static final Map<String, BeeCropReservation> BEE_CROP_RESERVATIONS = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE = ConcurrentHashMap.newKeySet();

	private MadokuMobBee() {
	}

	public static void applySpawnOverrides(Mob mob, ServerLevelAccessor world) {
		MadokuMobManager.applyBeeSpawnOverrides(mob, world);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		return MadokuMobManager.applyBeeLoadedEntityOverrides(entity);
	}

	public static boolean isPollinateCropsEnabled(Bee bee) {
		if (bee == null || !MadokuMobManager.isEnabled()) {
			return false;
		}
		if (!MadokuMobManager.isBeeBehaviorOverrideEnabled() || !MadokuMobManager.isBeeGoalsOverrideEnabled()) {
			return false;
		}
		JsonObject behaviorRoot = MadokuMobManager.resolveBeeBehaviorRoot(bee);
		JsonObject pollinateCropsRoot = getObject(behaviorRoot, "pollinate-crops");
		return getBoolean(pollinateCropsRoot, MadokuMobConfigManager.FIELD_ENABLED, false);
	}

	public static void resetRuntimeState() {
		BEE_NECTAR_STATES.clear();
		BEE_CROP_RESERVATIONS.clear();
		BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.clear();
	}

	public static void onEntityCleanup(Entity entity) {
		if (entity == null) {
			return;
		}
		resetBeeNectarCycle(entity.getUUID());
	}

	public static boolean tickRuntime(
		MinecraftServer server,
		Iterable<Entity> trackedEntities,
		boolean mobSystemEnabled,
		boolean beeFileEnabled
	) {
		if (server == null || !mobSystemEnabled || !beeFileEnabled) {
			resetRuntimeState();
			return false;
		}
		long nowTick = server.overworld() == null ? 0L : server.overworld().getGameTime();
		pruneBeeCropReservations(nowTick);
		if (trackedEntities == null) {
			return !BEE_NECTAR_STATES.isEmpty() || !BEE_CROP_RESERVATIONS.isEmpty();
		}
		for (Entity entity : trackedEntities) {
			if (!(entity instanceof Bee bee) || !bee.isAlive() || !(bee.level() instanceof ServerLevel level)) {
				continue;
			}
			if (!MadokuMobManager.isBeeBehaviorOverrideEnabled()) {
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(level, bee, "override_behavior_disabled");
				resetBeeNectarCycle(bee.getUUID());
				continue;
			}
			if (!MadokuMobManager.isBeeGoalsOverrideEnabled()) {
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(level, bee, "override_goals_disabled");
				resetBeeNectarCycle(bee.getUUID());
				continue;
			}
			JsonObject behaviorRoot = MadokuMobManager.resolveBeeBehaviorRoot(bee);
			JsonObject pollinateCropsRoot = getObject(behaviorRoot, "pollinate-crops");
			boolean pollinateCropsEnabled = getBoolean(pollinateCropsRoot, MadokuMobConfigManager.FIELD_ENABLED, false);
			if (!pollinateCropsEnabled) {
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(level, bee, "pollinate-crops disabled");
				resetBeeNectarCycle(bee.getUUID());
				continue;
			}
			// pollinate-crops is the override mode and takes precedence over pollination behavior config.
			JsonObject pollinationRoot = getObject(behaviorRoot, "pollination");
			int nectarTotalCharges = Math.max(1, getInt(pollinateCropsRoot, "nectar_total_charges", DEFAULT_NECTAR_TOTAL_CHARGES));
			int searchDurationTicks = Math.max(1, getInt(pollinateCropsRoot, "search_duration_ticks", DEFAULT_SEARCH_DURATION_TICKS));
			int searchRadiusHorizontal = Math.max(1, getInt(pollinateCropsRoot, "search_radius_horizontal", DEFAULT_SEARCH_RADIUS_HORIZONTAL));
			int searchRadiusVertical = Math.max(0, getInt(pollinateCropsRoot, "search_radius_vertical", DEFAULT_SEARCH_RADIUS_VERTICAL));
			double cropReachDistanceSqr = Math.max(0.25D, getDouble(pollinateCropsRoot, "crop_reach_distance_sqr", DEFAULT_CROP_REACH_DISTANCE_SQR));
			int cropReservationTtlTicks = Math.max(1, getInt(pollinateCropsRoot, "crop_reservation_ttl_ticks", DEFAULT_CROP_RESERVATION_TTL_TICKS));
			double moveSpeedModifier = Math.max(0.05D, getDouble(pollinateCropsRoot, "move_speed_modifier", DEFAULT_MOVE_SPEED_MODIFIER));
			double arrivalThreshold = Math.max(
				0.01D,
				getDouble(
					pollinateCropsRoot,
					"arrival_threshold",
					getDouble(pollinationRoot, "arrival_threshold", DEFAULT_ARRIVAL_THRESHOLD)
				)
			);
			int positionChangeChance = Math.max(
				0,
				Math.min(
					100,
					getInt(
						pollinateCropsRoot,
						"position_change_chance",
						getInt(pollinationRoot, "position_change_chance", DEFAULT_POSITION_CHANGE_CHANCE)
					)
				)
			);
			double hoverHeightWithinFlower = Math.max(
				0.0D,
				getDouble(
					pollinateCropsRoot,
					"hover_height_within_crop",
					getDouble(pollinationRoot, "hover_height_within_flower", DEFAULT_HOVER_HEIGHT_WITHIN_FLOWER)
				)
			);
			double hoverPosOffset = Math.max(
				0.0D,
				getDouble(
					pollinateCropsRoot,
					"hover_pos_offset",
					getDouble(pollinationRoot, "hover_pos_offset", DEFAULT_HOVER_POS_OFFSET)
				)
			);
			int chargeIntervalTicks = Math.max(1, getInt(pollinateCropsRoot, "charge_interval_ticks", DEFAULT_CHARGE_INTERVAL_TICKS));
			int chargesSpendDivisor = Math.max(1, getInt(pollinateCropsRoot, "charges_spend_divisor", DEFAULT_CHARGES_SPEND_DIVISOR));
			double growthPercentPerCharge = Math.max(0.0D, getDouble(pollinateCropsRoot, "growth_percent_per_charge", DEFAULT_GROWTH_PERCENT_PER_CHARGE));
			double arrivalThresholdSqr = arrivalThreshold * arrivalThreshold;
			UUID beeId = bee.getUUID();
			if (!bee.hasNectar()) {
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(level, bee, "hasNectar=false");
				resetBeeNectarCycle(beeId);
				continue;
			}
			if (BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.contains(beeId)) {
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(level, bee, "minimum_locked_until_nectar_reset");
				clearBeeNectarState(beeId);
				continue;
			}

			BeeNectarState state = BEE_NECTAR_STATES.computeIfAbsent(
				beeId,
				id -> BeeNectarState.create(nectarTotalCharges, chargesSpendDivisor, nowTick + searchDurationTicks)
			);
			if (state.chargesRemaining <= 0 || state.searchExpiresAtTick <= nowTick) {
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(
					level,
					bee,
					"state_expired",
					"charges_remaining",
					state.chargesRemaining,
					"search_expires",
					state.searchExpiresAtTick
				);
				clearBeeNectarState(beeId);
				continue;
			}
			if (state.chargesRemaining <= state.minimumChargesRemaining) {
				BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.add(beeId);
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(
					level,
					bee,
					"minimum_charges_reached",
					"charges_remaining",
					state.chargesRemaining,
					"minimum_charges_remaining",
					state.minimumChargesRemaining
				);
				clearBeeNectarState(beeId);
				continue;
			}

			int stayOutTicks = (int) Math.max(0L, state.searchExpiresAtTick - nowTick);
			bee.setStayOutOfHiveCountdown(stayOutTicks);
			if (state.hasTarget() && !isBeeCropTargetStillValid(level, state, beeId, nowTick, cropReservationTtlTicks)) {
				releaseBeeCropReservation(beeId, state.reservedCropKey);
				state.clearTarget();
			}

			if (!state.hasTarget()) {
				BeeCropTarget target = findBeeCropTarget(
					bee,
					level,
					beeId,
					nowTick,
					searchRadiusHorizontal,
					searchRadiusVertical,
					cropReservationTtlTicks
				);
				if (target == null) {
					continue;
				}
				state.assignTarget(target.cropKey(), target.pos(), target.levelId());
				emitBeeTargetDebug(level, bee, "acquired", target.pos(), target.cropKey());
			}

			BlockPos targetPos = BlockPos.of(state.reservedCropPos);
			state.updateHoverTarget(
				bee,
				targetPos,
				hoverPosOffset,
				hoverHeightWithinFlower,
				arrivalThresholdSqr,
				positionChangeChance
			);
			bee.getNavigation().moveTo(
				state.hoverTargetX,
				state.hoverTargetY,
				state.hoverTargetZ,
				moveSpeedModifier
			);

			if (bee.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) > cropReachDistanceSqr) {
				continue;
			}

			BlockState targetState = level.getBlockState(targetPos);
			if (!isBeeSupportedCrop(targetState) || isBeeCropFullyGrown(targetState) || !isFarmlandBelow(level, targetPos)) {
				emitBeeTargetDebug(level, bee, "invalid_target", targetPos.asLong(), state.reservedCropKey);
				releaseBeeCropReservation(beeId, state.reservedCropKey);
				state.clearTarget();
				continue;
			}
			if (!state.canSpendCharge(nowTick)) {
				continue;
			}

			int chargesToSpend = 1;
			boolean farmingEnabled = MadokuFarming.isEnabled();
			boolean appliedGrowth;
			String growthMode;
			double percentGrowth = chargesToSpend * growthPercentPerCharge;
			if (farmingEnabled) {
				appliedGrowth = MadokuFarming.applyExternalGrowthPercent(level, targetPos, percentGrowth, "bee_nectar");
				growthMode = "madoku_farming_percent";
			} else {
				appliedGrowth = growCropBySingleStage(level, targetPos, targetState);
				growthMode = "vanilla_stage_fallback";
			}
			emitBeeGrowthDebug(
				level,
				bee,
				targetPos,
				growthMode,
				farmingEnabled,
				chargesToSpend,
				state.chargesRemaining,
				percentGrowth,
				appliedGrowth
			);
			if (!appliedGrowth) {
				releaseBeeCropReservation(beeId, state.reservedCropKey);
				state.clearTarget();
				continue;
			}

			state.chargesRemaining = Math.max(0, state.chargesRemaining - chargesToSpend);
			state.markChargeSpent(nowTick, chargeIntervalTicks);
			releaseBeeCropReservation(beeId, state.reservedCropKey);
			state.clearTarget();
			if (state.chargesRemaining <= state.minimumChargesRemaining) {
				BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.add(beeId);
				allowBeeHiveReturn(bee);
				emitBeeStateDebug(
					level,
					bee,
					"minimum_charges_reached",
					"charges_remaining",
					state.chargesRemaining,
					"minimum_charges_remaining",
					state.minimumChargesRemaining
				);
				clearBeeNectarState(beeId);
			}
		}
		pruneBeeCropReservations(nowTick);
		return !BEE_NECTAR_STATES.isEmpty() || !BEE_CROP_RESERVATIONS.isEmpty();
	}

	private static BeeCropTarget findBeeCropTarget(
		Bee bee,
		ServerLevel level,
		UUID beeId,
		long nowTick,
		int searchRadiusHorizontal,
		int searchRadiusVertical,
		int cropReservationTtlTicks
	) {
		if (bee == null || level == null || beeId == null) {
			return null;
		}
		BlockPos center = bee.blockPosition();
		BlockPos best = null;
		String bestKey = "";
		double bestProgress = Double.MAX_VALUE;
		int tieCount = 0;
		String levelId = SchedulerManagerSystem.normalizeLevelIdentifier(level.dimension().toString());
		for (BlockPos candidate : BlockPos.betweenClosed(
			center.offset(-searchRadiusHorizontal, -searchRadiusVertical, -searchRadiusHorizontal),
			center.offset(searchRadiusHorizontal, searchRadiusVertical, searchRadiusHorizontal)
		)) {
			BlockState state = level.getBlockState(candidate);
			if (!isBeeSupportedCrop(state) || isBeeCropFullyGrown(state) || !isFarmlandBelow(level, candidate)) {
				continue;
			}
			String cropKey = beeCropKey(levelId, candidate.asLong());
			BeeCropReservation reservation = BEE_CROP_RESERVATIONS.get(cropKey);
			if (reservation != null && (reservation.expiresAtTick <= nowTick)) {
				BEE_CROP_RESERVATIONS.remove(cropKey, reservation);
				reservation = null;
			}
			if (reservation != null && !beeId.equals(reservation.beeId)) {
				continue;
			}
			double progress = getCropGrowthProgress(state);
			if (progress < bestProgress - 1.0E-6D) {
				best = candidate.immutable();
				bestKey = cropKey;
				bestProgress = progress;
				tieCount = 1;
				continue;
			}
			if (Math.abs(progress - bestProgress) <= 1.0E-6D) {
				tieCount++;
				// Reservoir sampling: uniform random among equal-growth candidates.
				if (bee.getRandom().nextInt(tieCount) == 0) {
					best = candidate.immutable();
					bestKey = cropKey;
				}
			}
		}
		if (best == null || bestKey.isBlank()) {
			return null;
		}
		BEE_CROP_RESERVATIONS.put(bestKey, new BeeCropReservation(beeId, nowTick + cropReservationTtlTicks));
		return new BeeCropTarget(levelId, best.asLong(), bestKey);
	}

	private static boolean isBeeCropTargetStillValid(
		ServerLevel level,
		BeeNectarState state,
		UUID beeId,
		long nowTick,
		int cropReservationTtlTicks
	) {
		if (level == null || state == null || !state.hasTarget() || beeId == null) {
			return false;
		}
		String currentLevelId = SchedulerManagerSystem.normalizeLevelIdentifier(level.dimension().toString());
		if (!currentLevelId.equals(state.reservedLevelId)) {
			return false;
		}
		BlockPos targetPos = BlockPos.of(state.reservedCropPos);
		BlockState stateAtTarget = level.getBlockState(targetPos);
		if (!isBeeSupportedCrop(stateAtTarget) || isBeeCropFullyGrown(stateAtTarget) || !isFarmlandBelow(level, targetPos)) {
			return false;
		}
		BeeCropReservation reservation = BEE_CROP_RESERVATIONS.get(state.reservedCropKey);
		if (reservation == null) {
			BEE_CROP_RESERVATIONS.put(state.reservedCropKey, new BeeCropReservation(beeId, nowTick + cropReservationTtlTicks));
			return true;
		}
		if (!beeId.equals(reservation.beeId)) {
			return false;
		}
		if (reservation.expiresAtTick <= nowTick) {
			BEE_CROP_RESERVATIONS.put(state.reservedCropKey, new BeeCropReservation(beeId, nowTick + cropReservationTtlTicks));
			return true;
		}
		return true;
	}

	private static void clearBeeNectarState(UUID beeId) {
		if (beeId == null) {
			return;
		}
		BeeNectarState removed = BEE_NECTAR_STATES.remove(beeId);
		if (removed == null || !removed.hasTarget()) {
			return;
		}
		releaseBeeCropReservation(beeId, removed.reservedCropKey);
	}

	private static void resetBeeNectarCycle(UUID beeId) {
		if (beeId == null) {
			return;
		}
		BEE_MINIMUM_REACHED_THIS_NECTAR_CYCLE.remove(beeId);
		clearBeeNectarState(beeId);
	}

	private static void releaseBeeCropReservation(UUID beeId, String cropKey) {
		if (beeId == null || cropKey == null || cropKey.isBlank()) {
			return;
		}
		BeeCropReservation reservation = BEE_CROP_RESERVATIONS.get(cropKey);
		if (reservation != null && beeId.equals(reservation.beeId)) {
			BEE_CROP_RESERVATIONS.remove(cropKey, reservation);
		}
	}

	private static void pruneBeeCropReservations(long nowTick) {
		for (Map.Entry<String, BeeCropReservation> entry : BEE_CROP_RESERVATIONS.entrySet()) {
			BeeCropReservation reservation = entry.getValue();
			if (reservation == null || reservation.expiresAtTick <= nowTick) {
				BEE_CROP_RESERVATIONS.remove(entry.getKey(), reservation);
			}
		}
	}

	private static String beeCropKey(String levelId, long pos) {
		return (levelId == null ? "" : levelId) + "|" + pos;
	}

	private static void allowBeeHiveReturn(Bee bee) {
		if (bee == null) {
			return;
		}
		bee.setStayOutOfHiveCountdown(0);
	}

	private static void emitBeeStateDebug(ServerLevel level, Bee bee, String outcome) {
		emitBeeStateDebug(level, bee, outcome, "", "", "", "");
	}

	private static void emitBeeStateDebug(
		ServerLevel level,
		Bee bee,
		String outcome,
		String fieldOneName,
		Object fieldOneValue,
		String fieldTwoName,
		Object fieldTwoValue
	) {
		if (level == null || bee == null || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_BEE_CROP_STATE)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_BEE_CROP_STATE, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.tick(level.getGameTime())
			.world(level.dimension().toString())
			.subject("bee:" + bee.getUUID())
			.field("outcome", outcome)
			.field("is_baby", bee.isBaby())
			.field("has_nectar", bee.hasNectar());
		if (fieldOneName != null && !fieldOneName.isBlank()) {
			event.field(fieldOneName, fieldOneValue);
		}
		if (fieldTwoName != null && !fieldTwoName.isBlank()) {
			event.field(fieldTwoName, fieldTwoValue);
		}
		event.log();
	}

	private static void emitBeeTargetDebug(ServerLevel level, Bee bee, String outcome, long cropPos, String cropKey) {
		if (level == null || bee == null || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_BEE_CROP_TARGET)) {
			return;
		}
		MadokuDebug.event(METRIC_BEE_CROP_TARGET, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.tick(level.getGameTime())
			.world(level.dimension().toString())
			.subject("bee:" + bee.getUUID())
			.field("outcome", outcome)
			.field("crop_pos", cropPos)
			.field("crop_key", cropKey == null ? "" : cropKey)
			.field("is_baby", bee.isBaby())
			.log();
	}

	private static void emitBeeGrowthDebug(
		ServerLevel level,
		Bee bee,
		BlockPos cropPos,
		String growthMode,
		boolean farmingEnabled,
		int chargesSpent,
		int chargesBefore,
		double percentGrowth,
		boolean appliedGrowth
	) {
		if (level == null || bee == null) {
			return;
		}
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_BEE_CROP_MODE)) {
			MadokuDebug.event(METRIC_BEE_CROP_MODE, MadokuDebug.Domain.MOB)
				.side(MadokuDebug.Side.SERVER)
				.tick(level.getGameTime())
				.world(level.dimension().toString())
				.subject("bee:" + bee.getUUID())
				.field("farming_enabled", farmingEnabled)
				.field("growth_mode", growthMode)
				.field("has_nectar", bee.hasNectar())
				.field("is_baby", bee.isBaby())
				.log();
		}
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_BEE_CROP_GROWTH)) {
			return;
		}
		MadokuDebug.event(METRIC_BEE_CROP_GROWTH, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.tick(level.getGameTime())
			.world(level.dimension().toString())
			.subject("bee:" + bee.getUUID())
			.field("growth_mode", growthMode)
			.field("farming_enabled", farmingEnabled)
			.field("applied", appliedGrowth)
			.field("charges_spent", chargesSpent)
			.field("charges_before", chargesBefore)
			.field("percent_growth_requested", percentGrowth)
			.field("crop_pos", cropPos == null ? "unknown" : cropPos.asLong())
			.log();
	}

	private static boolean isFarmlandBelow(ServerLevel level, BlockPos cropPos) {
		if (level == null || cropPos == null) {
			return false;
		}
		return level.getBlockState(cropPos.below()).is(Blocks.FARMLAND);
	}

	private static boolean isBeeSupportedCrop(BlockState state) {
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		return block == Blocks.WHEAT
			|| block == Blocks.CARROTS
			|| block == Blocks.POTATOES
			|| block == Blocks.BEETROOTS
			|| block == Blocks.MELON_STEM
			|| block == Blocks.PUMPKIN_STEM;
	}

	private static boolean isBeeCropFullyGrown(BlockState state) {
		IntegerProperty ageProperty = findAgeProperty(state);
		if (ageProperty == null) {
			return true;
		}
		Integer age = state.getValue(ageProperty);
		int max = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
		return age != null && age >= max;
	}

	private static double getCropGrowthProgress(BlockState state) {
		IntegerProperty ageProperty = findAgeProperty(state);
		if (ageProperty == null) {
			return 1.0D;
		}
		Integer age = state.getValue(ageProperty);
		int max = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
		if (age == null || max <= 0) {
			return 1.0D;
		}
		return Math.max(0.0D, Math.min(1.0D, age / (double) max));
	}

	private static boolean growCropBySingleStage(ServerLevel level, BlockPos cropPos, BlockState state) {
		if (level == null || cropPos == null || state == null) {
			return false;
		}
		IntegerProperty ageProperty = findAgeProperty(state);
		if (ageProperty == null) {
			return false;
		}
		Integer age = state.getValue(ageProperty);
		int max = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
		if (age == null || age >= max) {
			return false;
		}
		BlockState updated = state.setValue(ageProperty, age + 1);
		return level.setBlock(cropPos, updated, 2);
	}

	private static IntegerProperty findAgeProperty(BlockState state) {
		if (state == null) {
			return null;
		}
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty integerProperty && "age".equals(property.getName())) {
				return integerProperty;
			}
		}
		return null;
	}

	private static JsonObject getObject(JsonObject root, String key) {
		if (root == null || key == null) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static int getInt(JsonObject root, String key, int fallback) {
		if (root == null || key == null) {
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

	private static double getDouble(JsonObject root, String key, double fallback) {
		if (root == null || key == null) {
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

	private record BeeCropReservation(UUID beeId, long expiresAtTick) {}
	private record BeeCropTarget(String levelId, long pos, String cropKey) {}
	private static final class BeeNectarState {
		private int chargesRemaining;
		private final int minimumChargesRemaining;
		private long searchExpiresAtTick;
		private long nextChargeAllowedAtTick;
		private String reservedCropKey;
		private long reservedCropPos;
		private String reservedLevelId;
		private double hoverTargetX;
		private double hoverTargetY;
		private double hoverTargetZ;
		private boolean hasHoverTarget;

		private BeeNectarState(int chargesRemaining, int minimumChargesRemaining, long searchExpiresAtTick) {
			this.chargesRemaining = Math.max(0, chargesRemaining);
			this.minimumChargesRemaining = Math.max(0, minimumChargesRemaining);
			this.searchExpiresAtTick = Math.max(0L, searchExpiresAtTick);
			this.nextChargeAllowedAtTick = 0L;
			this.reservedCropKey = "";
			this.reservedCropPos = Long.MIN_VALUE;
			this.reservedLevelId = "";
			this.hoverTargetX = 0.0D;
			this.hoverTargetY = 0.0D;
			this.hoverTargetZ = 0.0D;
			this.hasHoverTarget = false;
		}

		private static BeeNectarState create(int chargesRemaining, int chargesSpendDivisor, long searchExpiresAtTick) {
			int sanitizedCharges = Math.max(1, chargesRemaining);
			int minimumChargesRemaining = chargesSpendDivisor <= 1
				? 0
				: (int) Math.ceil(sanitizedCharges / (double) chargesSpendDivisor);
			return new BeeNectarState(sanitizedCharges, minimumChargesRemaining, searchExpiresAtTick);
		}

		private void assignTarget(String cropKey, long cropPos, String levelId) {
			this.reservedCropKey = cropKey == null ? "" : cropKey;
			this.reservedCropPos = cropPos;
			this.reservedLevelId = levelId == null ? "" : levelId;
			clearHoverTarget();
		}

		private void clearTarget() {
			this.reservedCropKey = "";
			this.reservedCropPos = Long.MIN_VALUE;
			this.reservedLevelId = "";
			clearHoverTarget();
		}

		private boolean hasTarget() {
			return reservedCropPos != Long.MIN_VALUE && !reservedCropKey.isBlank();
		}

		private boolean canSpendCharge(long nowTick) {
			return nowTick >= nextChargeAllowedAtTick;
		}

		private void markChargeSpent(long nowTick, int chargeIntervalTicks) {
			long sanitizedNow = Math.max(0L, nowTick);
			int sanitizedInterval = Math.max(1, chargeIntervalTicks);
			this.nextChargeAllowedAtTick = sanitizedNow + sanitizedInterval;
		}

		private void updateHoverTarget(
			Bee bee,
			BlockPos cropPos,
			double hoverPosOffset,
			double hoverHeightWithinFlower,
			double arrivalThresholdSqr,
			int positionChangeChance
		) {
			if (bee == null || cropPos == null) {
				clearHoverTarget();
				return;
			}
			double centerX = cropPos.getX() + 0.5D;
			double centerY = cropPos.getY() + hoverHeightWithinFlower;
			double centerZ = cropPos.getZ() + 0.5D;
			if (!hasHoverTarget) {
				assignRandomHoverTarget(bee, centerX, centerY, centerZ, hoverPosOffset);
				return;
			}
			if (bee.distanceToSqr(hoverTargetX, hoverTargetY, hoverTargetZ) > Math.max(0.0001D, arrivalThresholdSqr)) {
				return;
			}
			if (positionChangeChance > 0 && bee.getRandom().nextInt(100) < positionChangeChance) {
				assignRandomHoverTarget(bee, centerX, centerY, centerZ, hoverPosOffset);
			}
		}

		private void assignRandomHoverTarget(Bee bee, double centerX, double centerY, double centerZ, double hoverPosOffset) {
			double offset = Math.max(0.0D, hoverPosOffset);
			double deltaX = (bee.getRandom().nextDouble() * 2.0D - 1.0D) * offset;
			double deltaZ = (bee.getRandom().nextDouble() * 2.0D - 1.0D) * offset;
			this.hoverTargetX = centerX + deltaX;
			this.hoverTargetY = centerY;
			this.hoverTargetZ = centerZ + deltaZ;
			this.hasHoverTarget = true;
		}

		private void clearHoverTarget() {
			this.hoverTargetX = 0.0D;
			this.hoverTargetY = 0.0D;
			this.hoverTargetZ = 0.0D;
			this.hasHoverTarget = false;
		}
	}
}
