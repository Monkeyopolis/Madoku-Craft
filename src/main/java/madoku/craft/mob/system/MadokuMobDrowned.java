package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.loot.system.EquipmentConfigManager;
import madoku.craft.difficulty.system.MadokuRegionalDifficultyManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MadokuMobDrowned {
	private static final double MIN_DROWNED_TRIDENT_HOMING_DISTANCE_SQR = 4.0D;
	private static final String DROWNED_VARIANT_TAG_PREFIX = "madoku-craft.drowned.variant:";
	private static final String DROWNED_VARIANT_DEFAULT_KEY = "default";
	private static final String DROWNED_VARIANT_RANGED_KEY = "ranged-drowned";
	private static final String RANGED_TRIDENT_TAG = "madoku-craft.drowned.ranged_trident";

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean invokeTridentMethod(Entity trident, String methodName, Class<?>[] parameterTypes, Object... arguments) {
		if (trident == null || methodName == null || methodName.isBlank()) {
			return false;
		}
		Class<?> type = trident.getClass();
		while (type != null) {
			try {
				java.lang.reflect.Method method = type.getDeclaredMethod(methodName, parameterTypes);
				method.setAccessible(true);
				method.invoke(trident, arguments);
				return true;
			} catch (NoSuchMethodException exception) {
				type = type.getSuperclass();
			} catch (ReflectiveOperationException exception) {
				return false;
			}
		}
		return false;
	}

	private static final Map<UUID, PendingRangedTridentCharge> PENDING_RANGED_TRIDENT_CHARGES = new java.util.HashMap<>();
	private static final Map<UUID, Integer> RANGED_TRIDENT_COOLDOWNS = new java.util.HashMap<>();

	private MadokuMobDrowned() {
	}

	public static void applySpawnOverrides(
		Drowned drowned,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (drowned == null || world == null || difficulty == null) {
			return;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank()) {
			return;
		}
		if (!MadokuMobManager.isEnabled()) {
			applySpawnEquipmentLoadoutWhenMobSystemDisabled(drowned, world.getRandom());
			return;
		}
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveDrownedRootForRuntime(drowned.getType());
		JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, world, true);
		JsonObject defaultGroup = variantGroup;
		if (defaultGroup.entrySet().isEmpty()) {
			return;
		}

		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean babyEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_BABY, true);

		JsonObject adult = MadokuMobManager.resolveAgeVariantRoot(defaultGroup, false);
		JsonObject baby = MadokuMobManager.resolveAgeVariantRoot(defaultGroup, true);
		if (overrideSpawnRules) {
			boolean shouldBeBaby = false;
			if (babyEnabled) {
				double babyChance = MadokuMobManager.resolveAgeVariantChanceForRuntime(
					MadokuMobManager.readSpawnRuleDoubleForRuntime(adult, MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0D),
					MadokuMobManager.readSpawnRuleDoubleForRuntime(baby, MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0D),
					difficulty,
					drowned
				);
				shouldBeBaby = world.getRandom().nextFloat() < babyChance;
			}
			drowned.setBaby(shouldBeBaby);
		}

		JsonObject variant = drowned.isBaby() ? baby : adult;
		variant = mergeDrownedFileSettings(fileRoot, variant);
		if (overrideSpawnRules) {
			applySpawnEquipmentSetLoadout(drowned, variant, world.getRandom());
		}
		ensureRangedDrownedTridentEquipped(drowned);
		applyWeaponDamagePolicy(drowned, variant);
		applyDrownedBehaviorToggles(drowned, fileConfigRoot, variant);
		if (overrideStats) {
			MadokuMobManager.applyUniversalStatsForRuntime(drowned, variant);
		}
	}

	public static boolean shouldOverrideSpawnRules(Drowned drowned) {
		if (drowned == null || drowned.getType() != madoku.craft.entity.MadokuEntityTypes.DROWNED) {
			return false;
		}
		if (!MadokuMobManager.isEnabled() || !MadokuMobManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_DROWNED)) {
			return false;
		}
		JsonObject drownedFileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_DROWNED);
		return readBoolean(drownedFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof Drowned drowned) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveDrownedRootForRuntime(drowned.getType());
		JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, drowned.isBaby());
		variant = mergeDrownedFileSettings(fileRoot, variant);

		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalBaseStatsForRuntime(drowned, variant);
		ensureRangedDrownedTridentEquipped(drowned);
		applyWeaponDamagePolicy(drowned, variant);
		applyDrownedBehaviorToggles(drowned, fileConfigRoot, variant);
		return modified;
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		if (!(entity instanceof Drowned drowned) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveDrownedRootForRuntime(drowned.getType());
		JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, drowned.isBaby());
		variant = mergeDrownedFileSettings(fileRoot, variant);

		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalDifficultyStatsForRuntime(drowned, variant);
		ensureRangedDrownedTridentEquipped(drowned);
		applyWeaponDamagePolicy(drowned, variant);
		applyDrownedBehaviorToggles(drowned, fileConfigRoot, variant);
		return modified;
	}

	public static boolean shouldAllowUnderwaterTargeting(Drowned drowned, LivingEntity target) {
		if (drowned == null || target == null) {
			return true;
		}
		if (drowned.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return true;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return true;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveDrownedRootForRuntime(drowned.getType());
		JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, drowned.isBaby());
		variant = mergeDrownedFileSettings(fileRoot, variant);
		return true;
	}

	public static double resolveSwimmingSpeedForRuntime(Drowned drowned, double fallback) {
		if (drowned == null) {
			return fallback;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isEnabled() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return fallback;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveDrownedRootForRuntime(drowned.getType());
		JsonObject variantGroup = resolveDrownedVariantGroupRoot(drowned, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, drowned.isBaby());
		variant = mergeDrownedFileSettings(fileRoot, variant);
		JsonObject statsRoot = readObject(variant, MobConfigManager.FIELD_MOB_STATS);
		return readDouble(statsRoot, MobConfigManager.FIELD_SWIMMING_SPEED, fallback);
	}

	static boolean isCustomMobDropsEnabled(LivingEntity entity) {
		if (!(entity instanceof Drowned drowned) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject resolved = resolveActiveDrownedRoot(drowned);
		boolean enabled = readBoolean(
			resolved,
			MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
			true
		);
		return enabled;
	}

	static String resolveMobDropsConfigReference(LivingEntity entity) {
		if (!(entity instanceof Drowned drowned) || !MadokuMobManager.isEnabled()) {
			return "";
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return "";
		}
		JsonObject resolved = resolveActiveDrownedRoot(drowned);
		JsonObject statsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_STATS);
		String reference = readString(statsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
		return reference;
	}

	private static JsonObject resolveActiveDrownedRoot(Drowned drowned) {
		if (drowned == null) {
			return new JsonObject();
		}
		JsonObject fileKeyRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_DROWNED);
		JsonObject fileRoot = MadokuMobManager.resolveDrownedRootForRuntime(drowned.getType());
		JsonObject defaultGroup = resolveDrownedVariantGroupRoot(drowned, fileKeyRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(defaultGroup, drowned.isBaby());
		return mergeDrownedFileSettings(fileRoot, variant);
	}

	private static JsonObject resolveDrownedVariantGroupRoot(
		Drowned drowned,
		JsonObject fileConfigRoot,
		JsonObject fileRoot,
		ServerLevelAccessor world,
		boolean spawnContext
	) {
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			clearDrownedVariantTag(drowned);
			return new JsonObject();
		}
		boolean variantEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_VARIANT, false);
		if (!variantEnabled) {
			clearDrownedVariantTag(drowned);
			return defaultGroup;
		}

		String storedVariant = readStoredDrownedVariantKey(drowned);
		if (!storedVariant.isBlank()) {
			if (DROWNED_VARIANT_DEFAULT_KEY.equals(storedVariant)) {
				return defaultGroup;
			}
			JsonObject known = resolveDrownedVariantRootByKey(fileRoot, storedVariant);
			if (!known.entrySet().isEmpty()) {
				return MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, known);
			}
		}

		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		if (!spawnContext || !overrideSpawnRules || world == null) {
			return defaultGroup;
		}

		String selectedVariant = selectDrownedVariantKey(fileRoot, world);
		if (selectedVariant.isBlank()) {
			selectedVariant = DROWNED_VARIANT_DEFAULT_KEY;
		}
		writeDrownedVariantTag(drowned, selectedVariant);
		if (DROWNED_VARIANT_DEFAULT_KEY.equals(selectedVariant)) {
			return defaultGroup;
		}
		JsonObject selected = resolveDrownedVariantRootByKey(fileRoot, selectedVariant);
		return selected.entrySet().isEmpty() ? defaultGroup : MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, selected);
	}

	private static String selectDrownedVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
		return MadokuMobManager.selectWeightedVariantKey(
			fileRoot,
			world == null ? null : world.getRandom(),
			MobConfigManager.FIELD_DEFAULT_GROUP,
			DROWNED_VARIANT_DEFAULT_KEY,
			MadokuMobDrowned::isReservedDrownedGroupKey,
			variantRoot -> resolveDrownedVariantSpawnWeight(variantRoot, 0.0D)
		);
	}

	private static double resolveDrownedVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		return MadokuMobManager.resolveVariantSpawnWeight(variantRoot, fallback);
	}

	private static boolean isReservedDrownedGroupKey(String normalizedKey) {
		if (normalizedKey == null || normalizedKey.isBlank()) {
			return true;
		}
		return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALE))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE));
	}

	private static JsonObject resolveDrownedVariantRootByKey(JsonObject fileRoot, String variantKey) {
		return MadokuMobManager.resolveVariantRootByKey(
			fileRoot,
			variantKey,
			MobConfigManager.FIELD_DEFAULT_GROUP,
			MadokuMobDrowned::isReservedDrownedGroupKey
		);
	}

	private static String readStoredDrownedVariantKey(Drowned drowned) {
		if (drowned == null) {
			return "";
		}
		for (String tag : drowned.entityTags()) {
			if (tag == null || !tag.startsWith(DROWNED_VARIANT_TAG_PREFIX)) {
				continue;
			}
			String normalized = normalizeKey(tag.substring(DROWNED_VARIANT_TAG_PREFIX.length()));
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private static void writeDrownedVariantTag(Drowned drowned, String variantKey) {
		if (drowned == null || variantKey == null || variantKey.isBlank()) {
			return;
		}
		clearDrownedVariantTag(drowned);
		drowned.addTag(DROWNED_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
	}

	private static void clearDrownedVariantTag(Drowned drowned) {
		if (drowned == null) {
			return;
		}
		String existing = null;
		for (String tag : drowned.entityTags()) {
			if (tag != null && tag.startsWith(DROWNED_VARIANT_TAG_PREFIX)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			drowned.removeTag(existing);
		}
	}

	private static JsonObject mergeDrownedFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
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

	private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(Drowned drowned, RandomSource random) {
		if (drowned == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
		}
		if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
			return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
		}
		String equipmentReference = resolveDefaultMobEquipmentReference(drowned.getType());
		if (equipmentReference.isBlank()) {
			return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
		}
		return applyEquipmentSetLoadout(
			drowned,
			equipmentReference,
			EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
			random
		);
	}

	private static EquipmentLoadoutResult applySpawnEquipmentSetLoadout(Drowned drowned, JsonObject variantRoot, RandomSource random) {
		if (drowned == null || variantRoot == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
		}
		if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
			return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
		}
		JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
		JsonObject equipmentSet = readObject(spawnRules, MobConfigManager.FIELD_EQUIPMENT_SET);
		if (equipmentSet.entrySet().isEmpty()) {
			return new EquipmentLoadoutResult(false, "equipment_set_missing", "", 0.0D, "none", 0, 0);
		}
		if (!readBoolean(equipmentSet, MobConfigManager.FIELD_ENABLED, true)) {
			return new EquipmentLoadoutResult(false, "equipment_set_disabled", "", 0.0D, "none", 0, 0);
		}
		double chancePercent = Math.max(0.0D, Math.min(100.0D, readDouble(equipmentSet, MobConfigManager.FIELD_EQUIPMENT_CHANCE, 10.0D)));
		String equipmentReference = readString(equipmentSet, MobConfigManager.FIELD_MOB_EQUIPMENT, "");
		return applyEquipmentSetLoadout(drowned, equipmentReference, chancePercent, random);
	}

	private static EquipmentLoadoutResult applyEquipmentSetLoadout(
		Drowned drowned,
		String equipmentReference,
		double chancePercent,
		RandomSource random
	) {
		if (drowned == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
		}
		if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
			return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
		}
		EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, drowned.getType());
		if (profile == null || !profile.enabled()) {
			return new EquipmentLoadoutResult(false, "profile_missing_or_disabled", equipmentReference, chancePercent, "none", 0, 0);
		}

		ArmorSetSelection selection = rollArmorSetSelection(profile.armorSetWeights(), random);
		if (selection == null) {
			return new EquipmentLoadoutResult(false, "armor_set_roll_failed", equipmentReference, chancePercent, "none", 0, 0);
		}
		Map<EquipmentSlot, ItemStack> rolledBySlot = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : selection.requiredSlots()) {
			ItemStack rolled = rollArmorItemForSlot(profile, slot, random);
			if (rolled.isEmpty()) {
				continue;
			}
			rolledBySlot.put(slot, rolled);
		}
		if (rolledBySlot.isEmpty()) {
			return new EquipmentLoadoutResult(
				false,
				"slot_pool_empty",
				equipmentReference,
				chancePercent,
				selection.name().toLowerCase(Locale.ROOT),
				0,
				selection.requiredSlots().size()
			);
		}
		MadokuMobManager.clearArmorSlotsForRuntime(drowned);
		for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
			drowned.setItemSlot(entry.getKey(), entry.getValue());
		}
		return new EquipmentLoadoutResult(
			true,
			"applied",
			equipmentReference,
			chancePercent,
			selection.name().toLowerCase(Locale.ROOT),
			rolledBySlot.size(),
			selection.requiredSlots().size()
		);
	}

	private static void applyDrownedBehaviorToggles(Drowned drowned, JsonObject fileRoot, JsonObject variantRoot) {
		if (drowned == null) {
			return;
		}
		JsonObject behaviorRoot = MadokuMobManager.readMobBehaviorRootForRuntime(variantRoot);

		boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);

		if (overrideBehavior) {
			drowned.setCanPickUpLoot(MadokuMobManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
		}
		if (overrideBehavior) {
			boolean callsReinforcements = readBoolean(behaviorRoot, MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, false);
			if (!callsReinforcements) {
				MadokuMobManager.disableZombieReinforcementsForRuntime(drowned);
			}
			drowned.setSearchingForLand(true);
		}
	}

	private static void applyWeaponDamagePolicy(Drowned drowned, JsonObject resolvedRoot) {
		if (drowned == null || resolvedRoot == null) {
			return;
		}
		boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
		if (weaponDamageEnabled) {
			return;
		}
		stripHeldAttackDamageModifiers(drowned, EquipmentSlot.MAINHAND);
		stripHeldAttackDamageModifiers(drowned, EquipmentSlot.OFFHAND);
	}

	public static void tickRangedDrownedRuntime(Drowned drowned) {
		if (drowned == null || drowned.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey) || !isRangedDrownedVariant(drowned)) {
			clearRangedDrownedRuntimeState(drowned);
			return;
		}

		UUID drownedId = drowned.getUUID();
		Integer cooldown = RANGED_TRIDENT_COOLDOWNS.get(drownedId);
		if (cooldown != null) {
			if (cooldown <= 1) {
				RANGED_TRIDENT_COOLDOWNS.remove(drownedId);
			} else {
				RANGED_TRIDENT_COOLDOWNS.put(drownedId, cooldown - 1);
			}
		}

		PendingRangedTridentCharge pending = PENDING_RANGED_TRIDENT_CHARGES.get(drownedId);
		if (pending == null) {
			return;
		}
		if (pending.remainingTicks() > 1) {
			PENDING_RANGED_TRIDENT_CHARGES.put(drownedId, pending.withRemainingTicks(pending.remainingTicks() - 1));
			return;
		}

		PENDING_RANGED_TRIDENT_CHARGES.remove(drownedId);
		if (RANGED_TRIDENT_COOLDOWNS.containsKey(drownedId)) {
			return;
		}
		if (!(drowned.level() instanceof ServerLevel level)) {
			return;
		}
		Entity targetEntity = level.getEntity(pending.targetUuid());
		if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		if (fireRangedDrownedTrident(drowned, target)) {
			RANGED_TRIDENT_COOLDOWNS.put(drownedId, resolveTridentAttackIntervalTicks(drowned));
		}
	}

	public static boolean applyRangedDrownedTridentAttack(Drowned drowned, LivingEntity target, float pullProgress) {
		if (drowned == null || target == null || !target.isAlive() || drowned.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(drowned.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey) || !isRangedDrownedVariant(drowned)) {
			return false;
		}
		UUID drownedId = drowned.getUUID();
		int cooldown = RANGED_TRIDENT_COOLDOWNS.getOrDefault(drownedId, 0);
		if (cooldown > 0) {
			return true;
		}
		PendingRangedTridentCharge pending = PENDING_RANGED_TRIDENT_CHARGES.get(drownedId);
		if (pending != null) {
			return true;
		}
		int chargeUpTicks = resolveTridentChargeUpTicks(drowned);
		if (chargeUpTicks <= 0) {
			if (fireRangedDrownedTrident(drowned, target)) {
				RANGED_TRIDENT_COOLDOWNS.put(drownedId, resolveTridentAttackIntervalTicks(drowned));
			}
			return true;
		}
		PENDING_RANGED_TRIDENT_CHARGES.put(drownedId, new PendingRangedTridentCharge(target.getUUID(), chargeUpTicks));
		return true;
	}

	public static int resolveTridentAttackIntervalTicks(Drowned drowned) {
		return resolveTridentAttackIntervalTicks(drowned, 20);
	}

	public static int resolveTridentChargeUpTicks(Drowned drowned) {
		return resolveTridentChargeUpTicks(drowned, 10);
	}

	public static boolean isRangedDrownedVariant(Drowned drowned) {
		return drowned != null && DROWNED_VARIANT_RANGED_KEY.equals(readStoredDrownedVariantKey(drowned));
	}

	public static int resolveTridentGroundClearTicks(net.minecraft.world.entity.projectile.arrow.ThrownTrident trident) {
		if (trident == null) {
			return -1;
		}
		Entity owner = trident.getOwner();
		if (owner instanceof Drowned drowned && isRangedDrownedVariant(drowned)) {
			return resolveTridentGroundClearTicks(drowned);
		}
		return trident.entityTags().contains(RANGED_TRIDENT_TAG) ? 300 : -1;
	}

	public static boolean isRangedDrownedTrident(Entity entity) {
		return entity instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident trident && trident.entityTags().contains(RANGED_TRIDENT_TAG);
	}

	private static boolean fireRangedDrownedTrident(Drowned drowned, LivingEntity target) {
		if (drowned == null || target == null || !target.isAlive() || !(drowned.level() instanceof ServerLevel level)) {
			return false;
		}
		JsonObject activeRoot = resolveActiveDrownedRoot(drowned);
		if (activeRoot.entrySet().isEmpty()) {
			return false;
		}
		JsonObject behaviorRoot = MadokuMobManager.readMobBehaviorRootForRuntime(activeRoot);
		if (!readBoolean(behaviorRoot, MobConfigManager.FIELD_TRIDENT_ATTACK, true)) {
			return false;
		}
		JsonObject statsRoot = readObject(activeRoot, MobConfigManager.FIELD_MOB_STATS);
		double accuracy = resolveTridentAttackAccuracy(drowned, target, readDouble(statsRoot, MobConfigManager.FIELD_ATTACK_ACCURACY, drowned.isBaby() ? 0.5D : 0.7D));
		double baseDamage = readDouble(statsRoot, MobConfigManager.FIELD_RANGED_DAMAGE, drowned.isBaby() ? 3.0D : 6.0D);
		double rangedDamage = resolveTridentRangedDamage(drowned, baseDamage);
		ItemStack tridentStack = drowned.getMainHandItem();
		if (!tridentStack.is(Items.TRIDENT)) {
			tridentStack = new ItemStack(Items.TRIDENT);
		}
		net.minecraft.world.entity.projectile.arrow.ThrownTrident trident = new net.minecraft.world.entity.projectile.arrow.ThrownTrident(level, drowned, tridentStack.copy());
		invokeTridentMethod(trident, "setOwner", new Class<?>[] { Entity.class }, drowned);
		trident.setBaseDamage(Math.max(0.0D, rangedDamage));
		MadokuMobManager.setProjectileDamageOverride(trident, (float) rangedDamage);
		trident.addTag(RANGED_TRIDENT_TAG);

		double dx = target.getX() - drowned.getX();
		double dz = target.getZ() - drowned.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = target.getY(1.0D / 3.0D) - trident.getY() + (horizontal * 0.2D);
		Vec3 desired = new Vec3(dx, dy, dz);
		Vec3 shot = desired;
		boolean homing = false;
		boolean homingEligible = drowned.distanceToSqr(target) > MIN_DROWNED_TRIDENT_HOMING_DISTANCE_SQR;
		if (desired.lengthSqr() > 1.0E-6D) {
			double clampedAccuracy = Mth.clamp(accuracy, 0.0D, 1.0D);
			if (drowned.getRandom().nextDouble() > clampedAccuracy) {
				shot = resolveMissVector(desired.x, desired.y, desired.z, clampedAccuracy, drowned);
			} else if (homingEligible) {
				homing = true;
			}
		}
		trident.shoot(shot.x, shot.y, shot.z, 1.6F, 0.0F);
		trident.setCritArrow(false);
		trident.setNoPhysics(false);
		drowned.playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.0F / (drowned.getRandom().nextFloat() * 0.4F + 0.8F));
		if (homing) {
			MadokuMobManager.startProjectileHoming(trident, target, level.getServer());
		}
		level.addFreshEntity(trident);
		return true;
	}

	private static double resolveTridentAttackAccuracy(Drowned drowned, LivingEntity target, double baseAccuracy) {
		if (drowned == null) {
			return baseAccuracy;
		}
		double accuracy = resolveScaledAttackAccuracy(baseAccuracy, drowned.level().getDifficulty(), isHardcoreWorld(drowned.level()));
		accuracy = MadokuRegionalDifficultyManager.resolveMobAttackAccuracyScaling(drowned, accuracy);
		accuracy = MadokuLuckManager.reduceHostileRangedAccuracyForTarget(target, accuracy);
		return Mth.clamp(accuracy, 0.0D, 1.0D);
	}

	private static double resolveTridentRangedDamage(Drowned drowned, double baseDamage) {
		if (drowned == null) {
			return baseDamage;
		}
		JsonObject difficultyScale = readObject(resolveActiveDrownedRoot(drowned), MobConfigManager.FIELD_DIFFICULTY_SCALE);
		double damage = resolveScaledRangedDamage(baseDamage, drowned.level().getDifficulty(), isHardcoreWorld(drowned.level()));
		Double rangedDamageScale = readOptionalNonNegative(difficultyScale, MobConfigManager.FIELD_DIFFICULTY_SCALE_RANGED_DAMAGE);
		if (rangedDamageScale != null) {
			damage = resolveDifficultyAdjustedPercentValue(
				drowned.level().getDifficulty(),
				isHardcoreWorld(drowned.level()),
				damage,
				rangedDamageScale,
				0.0D
			);
		}
		damage = MadokuRegionalDifficultyManager.resolveMobRangedDamageScaling(drowned, damage);
		return Math.max(0.0D, damage);
	}

	private static int resolveTridentAttackIntervalTicks(Drowned drowned, int fallback) {
		if (drowned == null || drowned.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return fallback;
		}
		JsonObject activeRoot = resolveActiveDrownedRoot(drowned);
		JsonObject statsRoot = readObject(activeRoot, MobConfigManager.FIELD_MOB_STATS);
		double interval = readDouble(statsRoot, MobConfigManager.FIELD_ATTACK_INTERVAL, fallback);
		return Math.max(1, (int) Math.round(interval));
	}

	private static int resolveTridentChargeUpTicks(Drowned drowned, int fallback) {
		if (drowned == null || drowned.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return fallback;
		}
		JsonObject activeRoot = resolveActiveDrownedRoot(drowned);
		JsonObject statsRoot = readObject(activeRoot, MobConfigManager.FIELD_MOB_STATS);
		double charge = readDouble(statsRoot, MobConfigManager.FIELD_CHARGE_INTERVAL, fallback);
		return Math.max(0, (int) Math.round(charge));
	}

	private static int resolveTridentGroundClearTicks(Drowned drowned) {
		if (drowned == null || drowned.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return 300;
		}
		return 300;
	}

	private static void clearRangedDrownedRuntimeState(Drowned drowned) {
		if (drowned == null) {
			return;
		}
		UUID drownedId = drowned.getUUID();
		PENDING_RANGED_TRIDENT_CHARGES.remove(drownedId);
		RANGED_TRIDENT_COOLDOWNS.remove(drownedId);
	}

	private static void ensureRangedDrownedTridentEquipped(Drowned drowned) {
		if (drowned == null || !isRangedDrownedVariant(drowned)) {
			return;
		}
		if (!drowned.getMainHandItem().is(Items.TRIDENT)) {
			drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		}
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
		lateral = lateral.normalize();
		double missFactor = 1.0D - Mth.clamp(attackAccuracy, 0.0D, 1.0D);
		double sideSign = shooter.getRandom().nextBoolean() ? -1.0D : 1.0D;
		double lateralStrength = Mth.lerp(missFactor, 1.4D, 2.4D);
		double verticalStrength = Mth.lerp(missFactor, 0.25D, 0.9D) * (shooter.getRandom().nextBoolean() ? -1.0D : 1.0D);
		Vec3 backwardBias = normalized.scale(-0.35D);
		Vec3 miss = lateral.scale(sideSign * lateralStrength).add(0.0D, verticalStrength, 0.0D).add(backwardBias);
		return miss.lengthSqr() <= 1.0E-6D ? lateral : miss.normalize();
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

	private static int resolveDifficultyTier(Difficulty difficulty, boolean hardcore) {
		Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
		return switch (resolved) {
			case PEACEFUL -> -2;
			case EASY -> -1;
			case NORMAL -> 0;
			case HARD -> hardcore ? 2 : 1;
		};
	}

	private static boolean isHardcoreWorld(net.minecraft.world.level.Level level) {
		return level != null && level.getServer() != null && level.getServer().isHardcore();
	}

	private static void stripHeldAttackDamageModifiers(Drowned drowned, EquipmentSlot slot) {
		if (drowned == null || slot == null) {
			return;
		}
		ItemStack stack = drowned.getItemBySlot(slot);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack normalized = stack.copy();
		normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		drowned.setItemSlot(slot, normalized);
	}

	private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
		if (target == null || source == null || key == null || key.isBlank()) {
			return;
		}
		if (!target.has(key) && source.has(key)) {
			target.add(key, source.get(key).deepCopy());
		}
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null || key == null || key.isBlank()) {
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

	private static Double readOptionalNonNegative(JsonObject root, String key) {
		double value = readDouble(root, key, Double.NaN);
		return Double.isFinite(value) && value >= 0.0D ? value : null;
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		return element.getAsString();
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static String fileKeyForType(net.minecraft.world.entity.EntityType<?> type) {
		return type == madoku.craft.entity.MadokuEntityTypes.DROWNED ? MobConfigManager.FILE_DROWNED : "";
	}

	private static String resolveDefaultMobEquipmentReference(net.minecraft.world.entity.EntityType<?> type) {
		return type == madoku.craft.entity.MadokuEntityTypes.DROWNED ? "minecraft-equipment-drowned.json" : "";
	}

	private static ItemStack rollArmorItemForSlot(
		EquipmentConfigManager.EquipmentProfile profile,
		EquipmentSlot slot,
		RandomSource random
	) {
		if (profile == null || slot == null || random == null) {
			return ItemStack.EMPTY;
		}
		List<EquipmentConfigManager.WeightedArmorEntry> entries = profile.slotEntries().get(slot);
		if (entries == null || entries.isEmpty()) {
			return ItemStack.EMPTY;
		}
		double totalWeight = 0.0D;
		for (EquipmentConfigManager.WeightedArmorEntry entry : entries) {
			if (entry != null) {
				totalWeight += Math.max(0.0D, entry.weight());
			}
		}
		if (totalWeight <= 0.0D) {
			return ItemStack.EMPTY;
		}
		double roll = random.nextDouble() * totalWeight;
		double cursor = 0.0D;
		for (EquipmentConfigManager.WeightedArmorEntry entry : entries) {
			if (entry == null || entry.item() == null || entry.weight() <= 0.0D) {
				continue;
			}
			cursor += entry.weight();
			if (roll < cursor) {
				return new ItemStack(entry.item());
			}
		}
		EquipmentConfigManager.WeightedArmorEntry fallback = entries.get(entries.size() - 1);
		return fallback == null || fallback.item() == null ? ItemStack.EMPTY : new ItemStack(fallback.item());
	}

	private static ArmorSetSelection rollArmorSetSelection(
		EquipmentConfigManager.ArmorSetWeights weights,
		RandomSource random
	) {
		if (weights == null || random == null) {
			return null;
		}
		double partial = Math.max(0.0D, weights.partialSetWeight());
		double half = Math.max(0.0D, weights.halfSetWeight());
		double full = Math.max(0.0D, weights.fullSetWeight());
		double total = partial + half + full;
		if (total <= 0.0D) {
			return null;
		}
		double roll = random.nextDouble() * total;
		if (roll < partial) {
			return ArmorSetSelection.PARTIAL_SET;
		}
		roll -= partial;
		if (roll < half) {
			return ArmorSetSelection.HALF_SET;
		}
		return ArmorSetSelection.FULL_SET;
	}

	private record PendingRangedTridentCharge(UUID targetUuid, int remainingTicks) {
		private PendingRangedTridentCharge withRemainingTicks(int remainingTicks) {
			return new PendingRangedTridentCharge(targetUuid, remainingTicks);
		}
	}

	private record EquipmentLoadoutResult(
		boolean applied,
		String reason,
		String equipmentReference,
		double chancePercent,
		String armorSet,
		int equippedPieces,
		int requiredPieces
	) {
	}

	private enum ArmorSetSelection {
		PARTIAL_SET(List.of(EquipmentSlot.HEAD)),
		HALF_SET(List.of(EquipmentSlot.HEAD, EquipmentSlot.FEET)),
		FULL_SET(List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET));

		private final List<EquipmentSlot> requiredSlots;

		ArmorSetSelection(List<EquipmentSlot> requiredSlots) {
			this.requiredSlots = requiredSlots;
		}

		private List<EquipmentSlot> requiredSlots() {
			return requiredSlots;
		}
	}
}

