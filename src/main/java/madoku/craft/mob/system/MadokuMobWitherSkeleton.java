package madoku.craft.mob.system;

import com.google.gson.JsonObject;
import madoku.craft.difficulty.system.MadokuRegionalDifficultyManager;
import madoku.craft.luck.MadokuLuck;
import madoku.craft.mixin.AbstractSkeletonArrowInvoker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class MadokuMobWitherSkeleton {
	private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;
	private static final int DEFAULT_CHARGE_UP_TICKS = 10;
	private static final int DEFAULT_WITHER_EFFECT_DURATION_TICKS = 5 * 20;
	private static final String WITHER_SKELETON_VARIANT_TAG_PREFIX = "madoku-craft.wither-skeleton.variant:";
	private static final String WITHER_SKELETON_VARIANT_DEFAULT_KEY = "default";

	private static final Map<UUID, PendingRangedBowCharge> PENDING_RANGED_BOW_CHARGES = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> RANGED_BOW_COOLDOWNS = new ConcurrentHashMap<>();

	private MadokuMobWitherSkeleton() {
	}

	public static void applySpawnOverrides(
		AbstractSkeleton skeleton,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (skeleton == null || world == null || difficulty == null || !MadokuMobManager.isEnabled()) {
			return;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
		if (!readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true)) {
			return;
		}
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return;
		}
		if (readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true)) {
			MadokuMobManager.applyUniversalStatsForRuntime(skeleton, root);
		}
		if (isBowAttackEnabled(skeleton)) {
			ensureBowEquipped(skeleton);
		}
	}

	public static boolean shouldOverrideSpawnRules(AbstractSkeleton skeleton) {
		if (skeleton == null || !MadokuMobManager.isEnabled()) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
		return readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return false;
		}
		boolean modified = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true)
			&& MadokuMobManager.applyUniversalStatsForRuntime(skeleton, root);
		if (isBowAttackEnabled(skeleton)) {
			ensureBowEquipped(skeleton);
		}
		return modified;
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		if (!(entity instanceof AbstractSkeleton skeleton) || skeleton.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return false;
		}
		boolean modified = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true)
			&& MadokuMobManager.applyUniversalDifficultyStatsForRuntime(skeleton, root);
		if (isBowAttackEnabled(skeleton)) {
			ensureBowEquipped(skeleton);
		}
		return modified;
	}

	public static JsonObject resolveRuntimeRoot(AbstractSkeleton skeleton) {
		if (skeleton == null || !MadokuMobManager.isEnabled()) {
			return new JsonObject();
		}
		String fileKey = skeletonFileKey(skeleton);
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return new JsonObject();
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveMobFileSectionForRuntime(fileKey);
		JsonObject variantGroup = resolveWitherSkeletonVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, null, false);
		if (variantGroup.entrySet().isEmpty()) {
			return new JsonObject();
		}
		return mergeWitherSkeletonFileSettings(fileConfigRoot, variantGroup);
	}

	public static boolean isBowAttackEnabled(AbstractSkeleton skeleton) {
		if (skeleton == null || !MadokuMobManager.isEnabled()) {
			return false;
		}
		JsonObject root = resolveRuntimeRoot(skeleton);
		return !root.entrySet().isEmpty() && MadokuMobManager.readMobBehaviorBooleanForRuntime(root, MobConfigManager.FIELD_BOW_ATTACK, false);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		ItemStack main = skeleton.getItemBySlot(EquipmentSlot.MAINHAND);
		if (main.isEmpty() || !main.is(Items.BOW)) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}
	}

	public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		if (skeleton == null || target == null || !target.isAlive() || skeleton.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		if (!isBowAttackEnabled(skeleton)) {
			return false;
		}

		UUID skeletonId = skeleton.getUUID();
		int cooldown = RANGED_BOW_COOLDOWNS.getOrDefault(skeletonId, 0);
		if (cooldown > 0) {
			return true;
		}
		PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
		if (pending != null) {
			return true;
		}

		int chargeUpTicks = resolveBowChargeUpTicks(skeleton);
		if (chargeUpTicks <= 0) {
			if (fireRangedBowArrow(skeleton, target)) {
				RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
			}
			return true;
		}

		PENDING_RANGED_BOW_CHARGES.put(skeletonId, new PendingRangedBowCharge(target.getUUID(), chargeUpTicks));
		return true;
	}

	public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
		if (!isBowAttackEnabled(skeleton)) {
			return -1;
		}
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return -1;
		}
		double interval = readDouble(readMobStatsRoot(root), MobConfigManager.FIELD_ATTACK_INTERVAL, DEFAULT_ATTACK_INTERVAL_TICKS);
		return Math.max(1, (int) Math.round(interval));
	}

	public static int resolveBowChargeUpTicks(Monster attacker) {
		if (!(attacker instanceof AbstractSkeleton skeleton) || !isBowAttackEnabled(skeleton)) {
			return -1;
		}
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return -1;
		}
		double charge = readDouble(readMobStatsRoot(root), MobConfigManager.FIELD_CHARGE_INTERVAL, DEFAULT_CHARGE_UP_TICKS);
		return Math.max(0, (int) Math.round(charge));
	}

	public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
		if (skeleton == null || skeleton.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return;
		}
		if (!isBowAttackEnabled(skeleton)) {
			clearRangedSkeletonRuntimeState(skeleton);
			return;
		}

		UUID skeletonId = skeleton.getUUID();
		Integer cooldown = RANGED_BOW_COOLDOWNS.get(skeletonId);
		if (cooldown != null) {
			if (cooldown <= 1) {
				RANGED_BOW_COOLDOWNS.remove(skeletonId);
			} else {
				RANGED_BOW_COOLDOWNS.put(skeletonId, cooldown - 1);
			}
		}

		PendingRangedBowCharge pending = PENDING_RANGED_BOW_CHARGES.get(skeletonId);
		if (pending == null) {
			return;
		}
		if (pending.remainingTicks() > 1) {
			PENDING_RANGED_BOW_CHARGES.put(skeletonId, pending.withRemainingTicks(pending.remainingTicks() - 1));
			return;
		}

		PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
		if (RANGED_BOW_COOLDOWNS.containsKey(skeletonId)) {
			return;
		}
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		Entity targetEntity = level.getEntity(pending.targetUuid());
		if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		if (fireRangedBowArrow(skeleton, target)) {
			RANGED_BOW_COOLDOWNS.put(skeletonId, resolveBowAttackIntervalTicks(skeleton));
		}
	}

	public static void onEntityCleanup(Entity entity) {
		if (entity instanceof AbstractSkeleton skeleton) {
			clearRangedSkeletonRuntimeState(skeleton);
		}
	}

	public static void resetRuntimeState() {
		PENDING_RANGED_BOW_CHARGES.clear();
		RANGED_BOW_COOLDOWNS.clear();
	}

	public static boolean hasRuntimeState() {
		return !PENDING_RANGED_BOW_CHARGES.isEmpty() || !RANGED_BOW_COOLDOWNS.isEmpty();
	}

	private static JsonObject resolveWitherSkeletonVariantGroupRoot(
		AbstractSkeleton skeleton,
		JsonObject fileConfigRoot,
		JsonObject fileRoot,
		ServerLevelAccessor world,
		boolean spawnContext
	) {
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			clearWitherSkeletonVariantTag(skeleton);
			return new JsonObject();
		}

		boolean variantEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_VARIANT, false);
		if (!variantEnabled) {
			clearWitherSkeletonVariantTag(skeleton);
			return defaultGroup;
		}

		String storedVariant = readStoredWitherSkeletonVariantKey(skeleton);
		if (!storedVariant.isBlank()) {
			if (WITHER_SKELETON_VARIANT_DEFAULT_KEY.equals(storedVariant)) {
				return defaultGroup;
			}
			JsonObject known = resolveWitherSkeletonVariantRootByKey(fileRoot, storedVariant);
			if (!known.entrySet().isEmpty()) {
				return MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, known);
			}
		}

		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		if (!spawnContext || !overrideSpawnRules || world == null) {
			return defaultGroup;
		}

		String selectedVariant = selectWitherSkeletonVariantKey(fileRoot, world);
		if (selectedVariant.isBlank()) {
			selectedVariant = WITHER_SKELETON_VARIANT_DEFAULT_KEY;
		}
		writeWitherSkeletonVariantTag(skeleton, selectedVariant);
		if (WITHER_SKELETON_VARIANT_DEFAULT_KEY.equals(selectedVariant)) {
			return defaultGroup;
		}
		JsonObject selected = resolveWitherSkeletonVariantRootByKey(fileRoot, selectedVariant);
		return selected.entrySet().isEmpty() ? defaultGroup : MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, selected);
	}

	private static JsonObject resolveWitherSkeletonVariantRootByKey(JsonObject fileRoot, String variantKey) {
		return MadokuMobManager.resolveVariantRootByKey(
			fileRoot,
			variantKey,
			MobConfigManager.FIELD_DEFAULT_GROUP,
			MadokuMobWitherSkeleton::isReservedWitherSkeletonGroupKey
		);
	}

	private static String selectWitherSkeletonVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
		return MadokuMobManager.selectWeightedVariantKey(
			fileRoot,
			world == null ? null : world.getRandom(),
			MobConfigManager.FIELD_DEFAULT_GROUP,
			WITHER_SKELETON_VARIANT_DEFAULT_KEY,
			MadokuMobWitherSkeleton::isReservedWitherSkeletonGroupKey,
			variantRoot -> MadokuMobManager.resolveVariantSpawnWeight(variantRoot, 0.0D)
		);
	}

	private static boolean isReservedWitherSkeletonGroupKey(String normalizedKey) {
		if (normalizedKey == null || normalizedKey.isBlank()) {
			return true;
		}
		return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_STATS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_VARIANT))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALE))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_SHARED_COMPONENTS));
	}

	public static boolean applyWitherSkeletonHitEffect(LivingEntity target, Entity attacker) {
		if (target == null || attacker == null || target.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		if (!(attacker instanceof AbstractSkeleton skeleton) || skeleton.getType() != net.minecraft.world.entity.EntityType.WITHER_SKELETON) {
			return false;
		}
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return target.addEffect(new MobEffectInstance(MobEffects.WITHER, DEFAULT_WITHER_EFFECT_DURATION_TICKS), attacker);
		}
		MobEffectInstance effect = resolveConfiguredMobEffectInstance(readMobStatsRoot(root), new MobEffectInstance(MobEffects.WITHER, DEFAULT_WITHER_EFFECT_DURATION_TICKS));
		return effect != null && target.addEffect(effect, attacker);
	}

	private static boolean fireRangedBowArrow(AbstractSkeleton skeleton, LivingEntity target) {
		if (skeleton == null || target == null || !target.isAlive() || !(skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		JsonObject root = resolveRuntimeRoot(skeleton);
		if (root.entrySet().isEmpty()) {
			return false;
		}

		ensureBowEquipped(skeleton);
		InteractionHand bowHand = resolveBowHand(skeleton);
		if (bowHand == null) {
			return false;
		}
		ItemStack bowStack = skeleton.getItemInHand(bowHand);
		ItemStack projectileStack = skeleton.getProjectile(bowStack);
		if (projectileStack.isEmpty()) {
			projectileStack = new ItemStack(Items.ARROW);
		}
		AbstractArrow arrow = ((AbstractSkeletonArrowInvoker) skeleton).madokuCraft$invokeGetArrow(projectileStack, 1.0F, bowStack);
		if (arrow == null) {
			return false;
		}

		double accuracy = resolveScaledAttackAccuracy(
			readDouble(readMobStatsRoot(root), MobConfigManager.FIELD_ATTACK_ACCURACY, 0.7D),
			skeleton.level().getDifficulty(),
			isHardcoreWorld(skeleton.level())
		);
		double rangedDamage = resolveWitherSkeletonRangedDamage(skeleton, root);
		accuracy = MadokuRegionalDifficultyManager.resolveMobAttackAccuracyScaling(skeleton, accuracy);
		ShotVector shot = resolveShotVector(skeleton, arrow, target, accuracy);
		arrow.shoot(shot.vector.x, shot.vector.y, shot.vector.z, 1.6F, 0.0F);
		arrow.setCritArrow(false);
		MadokuMobManager.setProjectileDamageOverride(arrow, (float) Math.max(0.0D, rangedDamage));
		MadokuMobManager.trackManagedMobArrowForRuntime(arrow, level.getServer());
		if (shot.guaranteedHit) {
			MadokuMobManager.startProjectileHoming(arrow, target, level.getServer());
		}
		skeleton.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
		level.addFreshEntity(arrow);
		return true;
	}

	private static double resolveWitherSkeletonRangedDamage(AbstractSkeleton skeleton, JsonObject root) {
		if (skeleton == null) {
			return 0.0D;
		}
		JsonObject difficultyScale = readObject(root, MobConfigManager.FIELD_DIFFICULTY_SCALE);
		double rangedDamage = resolveScaledRangedDamage(
			readDouble(readMobStatsRoot(root), MobConfigManager.FIELD_RANGED_DAMAGE, 6.0D),
			skeleton.level().getDifficulty(),
			isHardcoreWorld(skeleton.level())
		);
		Double rangedDamageScale = readOptionalNonNegative(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE);
		if (rangedDamageScale != null) {
			rangedDamage = resolveDifficultyAdjustedPercentValue(
				skeleton.level().getDifficulty(),
				isHardcoreWorld(skeleton.level()),
				rangedDamage,
				rangedDamageScale,
				0.0D
			);
		}
		return MadokuRegionalDifficultyManager.resolveMobRangedDamageScaling(skeleton, rangedDamage);
	}

	private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
		Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
		return switch (resolved) {
			case PEACEFUL -> -2;
			case EASY -> -1;
			case NORMAL -> 0;
			case HARD -> hardcore ? 2 : 1;
		};
	}

	private static double resolveScaledRangedDamage(double base, Difficulty difficulty, boolean hardcore) {
		return resolveDifficultyAdjustedValue(difficulty, hardcore, Math.max(0.0D, base), 1.0D, 0.0D);
	}

	private static double resolveScaledAttackAccuracy(double base, Difficulty difficulty, boolean hardcore) {
		return Mth.clamp(resolveDifficultyAdjustedValue(difficulty, hardcore, Mth.clamp(base, 0.0D, 1.0D), 0.05D, 0.0D), 0.0D, 1.0D);
	}

	private static double resolveDifficultyAdjustedValue(Difficulty difficulty, boolean hardcore, double baseValue, double step, double minimum) {
		return roundDifficultyScaleValue(Math.max(minimum, baseValue + (step * resolveDifficultyTier(difficulty, hardcore))));
	}

	private static double resolveDifficultyAdjustedPercentValue(Difficulty difficulty, boolean hardcore, double baseValue, double stepRatio, double minimum) {
		double multiplier = 1.0D + (stepRatio * resolveDifficultyTier(difficulty, hardcore));
		return roundDifficultyScaleValue(Math.max(minimum, baseValue * Math.max(0.0D, multiplier)));
	}

	private static double roundDifficultyScaleValue(double value) {
		if (!Double.isFinite(value)) {
			return value;
		}
		double step = isWholeNumber(value) ? 0.05D : 0.005D;
		return Math.round(value / step) * step;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	private static boolean isHardcoreWorld(Level level) {
		return level != null && level.getServer() != null && level.getServer().isHardcore();
	}

	private static InteractionHand resolveBowHand(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return null;
		}
		if (skeleton.getMainHandItem().is(Items.BOW)) {
			return InteractionHand.MAIN_HAND;
		}
		if (skeleton.getOffhandItem().is(Items.BOW)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	private static ShotVector resolveShotVector(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, double accuracy) {
		accuracy = MadokuLuck.reduceHostileRangedAccuracyForTarget(target, accuracy);
		double dx = target.getX() - skeleton.getX();
		double dz = target.getZ() - skeleton.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getY(1.0D / 3.0D) - arrow.getY() + (horizontal * 0.2D);
		Vec3 desired = new Vec3(dx, dy, dz);
		if (desired.lengthSqr() <= 1.0E-6D) {
			return new ShotVector(desired, true);
		}
		double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
		if (skeleton.getRandom().nextDouble() <= clampedAccuracy) {
			return new ShotVector(desired, true);
		}
		return new ShotVector(resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, skeleton), false);
	}

	private static Vec3 resolveMissVector(double velocityX, double velocityY, double velocityZ, double attackAccuracy, LivingEntity shooter) {
		Vec3 desired = new Vec3(velocityX, velocityY, velocityZ);
		if (desired.lengthSqr() <= 1.0E-6D) {
			return desired;
		}
		Vec3 normalized = desired.normalize();
		Vec3 lateral = normalized.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (lateral.lengthSqr() <= 1.0E-6D) {
			lateral = normalized.cross(new Vec3(1.0D, 0.0D, 0.0D));
		}
		if (lateral.lengthSqr() > 1.0E-6D) {
			lateral = lateral.normalize();
		}
		double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
		double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
		double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
		double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
		Vec3 backwardBias = normalized.scale(-0.35D);
		Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
		return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
	}

	private static JsonObject readMobStatsRoot(JsonObject root) {
		return readObject(root, MobConfigManager.FIELD_MOB_STATS);
	}

	private static JsonObject readObject(JsonObject parent, String key) {
		if (parent == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return root.get(key).getAsBoolean();
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isString()) {
			return fallback;
		}
		try {
			String value = root.get(key).getAsString();
			return value == null ? fallback : value;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null) {
			return fallback;
		}
		if (!root.has(key) || !root.get(key).isJsonPrimitive() || !root.get(key).getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = root.get(key).getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static Double readOptionalNonNegative(JsonObject root, String key) {
		double value = readDouble(root, key, Double.NaN);
		return Double.isFinite(value) && value >= 0.0D ? value : null;
	}

	private static MobEffectInstance resolveConfiguredMobEffectInstance(JsonObject statsRoot, MobEffectInstance fallbackEffect) {
		if (statsRoot == null || statsRoot.entrySet().isEmpty() || fallbackEffect == null) {
			return fallbackEffect;
		}
		JsonObject mobEffectRoot = readObject(statsRoot, MobConfigManager.FIELD_MOB_EFFECT);
		if (mobEffectRoot.entrySet().isEmpty()) {
			return fallbackEffect;
		}
		String effectId = normalizeKey(readString(mobEffectRoot, MobConfigManager.FIELD_EFFECT, ""));
		if (effectId.isBlank()) {
			return fallbackEffect;
		}
		Identifier effectIdentifier = Identifier.tryParse(effectId);
		if (effectIdentifier == null || !BuiltInRegistries.MOB_EFFECT.containsKey(effectIdentifier)) {
			return fallbackEffect;
		}
		MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.getValue(effectIdentifier);
		if (mobEffect == null) {
			return fallbackEffect;
		}
		int durationSeconds = Math.max(1, (int) Math.round(readDouble(mobEffectRoot, MobConfigManager.FIELD_DURATION, 0.0D)));
		return new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect), durationSeconds * 20);
	}

	private static JsonObject mergeWitherSkeletonFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
		JsonObject merged = variantRoot == null ? new JsonObject() : variantRoot.deepCopy();
		if (fileRoot == null || fileRoot.entrySet().isEmpty()) {
			return merged;
		}
		copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_CUSTOM_MOB_DROPS);
		copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_DIFFICULTY_SCALING);
		copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_DIFFICULTY_SCALE);
		copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING);
		copyIfMissing(merged, fileRoot, MobConfigManager.FIELD_WEAPON_DAMAGE);
		return merged;
	}

	private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
		if (target == null || source == null || key == null || key.isBlank()) {
			return;
		}
		if (!target.has(key) && source.has(key)) {
			target.add(key, source.get(key).deepCopy());
		}
	}

	private static String readStoredWitherSkeletonVariantKey(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return "";
		}
		for (String tag : skeleton.entityTags()) {
			if (tag == null || !tag.startsWith(WITHER_SKELETON_VARIANT_TAG_PREFIX)) {
				continue;
			}
			String normalized = normalizeKey(tag.substring(WITHER_SKELETON_VARIANT_TAG_PREFIX.length()));
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private static void writeWitherSkeletonVariantTag(AbstractSkeleton skeleton, String variantKey) {
		if (skeleton == null || variantKey == null || variantKey.isBlank()) {
			return;
		}
		clearWitherSkeletonVariantTag(skeleton);
		skeleton.addTag(WITHER_SKELETON_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
	}

	private static void clearWitherSkeletonVariantTag(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		String existing = null;
		for (String tag : skeleton.entityTags()) {
			if (tag != null && tag.startsWith(WITHER_SKELETON_VARIANT_TAG_PREFIX)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			skeleton.removeTag(existing);
		}
	}

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static void clearRangedSkeletonRuntimeState(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return;
		}
		UUID skeletonId = skeleton.getUUID();
		PENDING_RANGED_BOW_CHARGES.remove(skeletonId);
		RANGED_BOW_COOLDOWNS.remove(skeletonId);
	}

	private static String skeletonFileKey(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return "";
		}
		return skeleton.getType() == net.minecraft.world.entity.EntityType.WITHER_SKELETON ? MobConfigManager.FILE_WITHER_SKELETON : "";
	}

	private record PendingRangedBowCharge(UUID targetUuid, int remainingTicks) {
		private PendingRangedBowCharge withRemainingTicks(int remainingTicks) {
			return new PendingRangedBowCharge(targetUuid, remainingTicks);
		}
	}

	private record ShotVector(Vec3 vector, boolean guaranteedHit) {
	}
}
