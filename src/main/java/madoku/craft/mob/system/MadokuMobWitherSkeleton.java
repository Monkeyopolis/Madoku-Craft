package madoku.craft.mob.system;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Locale;

public final class MadokuMobWitherSkeleton {
	private static final int DEFAULT_WITHER_EFFECT_DURATION_TICKS = 5 * 20;
	private static final String WITHER_SKELETON_VARIANT_TAG_PREFIX = "madoku-craft.wither-skeleton.variant:";
	private static final String WITHER_SKELETON_VARIANT_DEFAULT_KEY = "default";

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
		JsonObject fileRoot = MadokuMobManager.resolveMobFileSectionForRuntime(MobConfigManager.FILE_WITHER_SKELETON);
		JsonObject variantGroup = resolveWitherSkeletonVariantGroupRoot(skeleton, fileConfigRoot, fileRoot, world, true);
		if (variantGroup.entrySet().isEmpty()) {
			return;
		}
		JsonObject root = mergeWitherSkeletonFileSettings(fileConfigRoot, variantGroup);
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
		return MadokuMobSkeleton.isBowAttackEnabled(skeleton);
	}

	public static void ensureBowEquipped(AbstractSkeleton skeleton) {
		MadokuMobSkeleton.ensureBowEquipped(skeleton);
	}

	public static boolean applyRangedSkeletonBowAttack(AbstractSkeleton skeleton, LivingEntity target, float pullProgress) {
		return MadokuMobSkeleton.applyRangedSkeletonBowAttack(skeleton, target, pullProgress);
	}

	public static int resolveBowAttackIntervalTicks(AbstractSkeleton skeleton) {
		return MadokuMobSkeleton.resolveBowAttackIntervalTicks(skeleton);
	}

	public static int resolveBowChargeUpTicks(Monster attacker) {
		return MadokuMobSkeleton.resolveBowChargeUpTicks(attacker);
	}

	public static void tickRangedSkeletonRuntime(AbstractSkeleton skeleton) {
		MadokuMobSkeleton.tickRangedSkeletonRuntime(skeleton);
	}

	public static void onEntityCleanup(Entity entity) {
		MadokuMobSkeleton.onEntityCleanup(entity);
	}

	public static void resetRuntimeState() {
		MadokuMobSkeleton.resetRuntimeState();
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

	private static String skeletonFileKey(AbstractSkeleton skeleton) {
		if (skeleton == null) {
			return "";
		}
		return skeleton.getType() == net.minecraft.world.entity.EntityType.WITHER_SKELETON ? MobConfigManager.FILE_WITHER_SKELETON : "";
	}
}
