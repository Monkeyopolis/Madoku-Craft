package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.loot.system.EquipmentConfigManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MadokuMobHusk {
	private static final String METRIC_HUSK_EQUIPMENT = "mob.husk_equipment_runtime";
	private static final String METRIC_HUSK_DROPS = "mob.husk_drop_runtime";
	private static final String HUSK_VARIANT_DEFAULT_KEY = "default";
	private static final double SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP = 2.0D;

	private MadokuMobHusk() {
	}

	public static void applySpawnOverrides(
		Husk husk,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (husk == null || world == null || difficulty == null) {
			return;
		}
		String fileKey = fileKeyForType(husk.getType());
		if (fileKey.isBlank()) {
			return;
		}
		if (!MadokuMobManager.isEnabled()) {
			EquipmentLoadoutResult result = applySpawnEquipmentLoadoutWhenMobSystemDisabled(husk, world.getRandom());
			emitHuskEquipmentDebug(husk, "spawn_mob_system_disabled", fileKey, result);
			return;
		}
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveHuskRootForRuntime(husk.getType());
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return;
		}

		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);

		JsonObject variantGroup = resolveHuskVariantGroupRoot(fileConfigRoot, fileRoot, world, true);
		JsonObject adult = resolveAgeVariantRoot(variantGroup, false);
		JsonObject baby = resolveAgeVariantRoot(variantGroup, true);
		boolean babyEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_BABY, true);
		if (overrideSpawnRules) {
			boolean shouldBeBaby = false;
			if (babyEnabled) {
				double babyChance = resolveHuskBabyChanceForRuntime(
					readSpawnWeight(adult, 95.0D),
					readSpawnWeight(baby, 5.0D),
					difficulty,
					husk
				);
				shouldBeBaby = world.getRandom().nextFloat() < babyChance;
			}
			husk.setBaby(shouldBeBaby);
		}

		JsonObject variant = husk.isBaby() ? baby : adult;
		variant = mergeHuskFileSettings(fileRoot, variant);
		if (overrideSpawnRules) {
			EquipmentLoadoutResult result = applySpawnEquipmentSetLoadout(husk, variant, world.getRandom());
			emitHuskEquipmentDebug(husk, "spawn_mob_system_enabled", fileKey, result);
		}
		applyWeaponDamagePolicy(husk, variant);
		applyHuskBehaviorToggles(husk, fileConfigRoot, variant);
		if (overrideStats) {
			MadokuMobManager.applyUniversalStatsForRuntime(husk, variant);
		}
	}

	public static boolean shouldOverrideSpawnRules(Husk husk) {
		if (husk == null || husk.getType() != EntityType.HUSK) {
			return false;
		}
		if (!MadokuMobManager.isEnabled() || !MadokuMobManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_HUSK)) {
			return false;
		}
		JsonObject huskFileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_HUSK);
		return readBoolean(huskFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof Husk husk) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(husk.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveHuskRootForRuntime(husk.getType());
		JsonObject variant = resolveAgeVariantRoot(readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP), husk.isBaby());
		variant = mergeHuskFileSettings(fileRoot, variant);

		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalBaseStatsForRuntime(husk, variant);
		applyWeaponDamagePolicy(husk, variant);
		applyHuskBehaviorToggles(husk, fileConfigRoot, variant);
		return modified;
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		if (!(entity instanceof Husk husk) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(husk.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveHuskRootForRuntime(husk.getType());
		JsonObject variant = resolveAgeVariantRoot(readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP), husk.isBaby());
		variant = mergeHuskFileSettings(fileRoot, variant);

		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalDifficultyStatsForRuntime(husk, variant);
		applyWeaponDamagePolicy(husk, variant);
		applyHuskBehaviorToggles(husk, fileConfigRoot, variant);
		return modified;
	}

	static boolean isCustomMobDropsEnabled(LivingEntity entity) {
		if (!(entity instanceof Husk husk) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(husk.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject resolved = resolveActiveHuskRoot(husk);
		boolean enabled = readBoolean(
			resolved,
			MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
			true
		);
		emitHuskDropsDebug(husk, "custom_drop_gate", fileKey, enabled, "");
		return enabled;
	}

	static String resolveMobDropsConfigReference(LivingEntity entity) {
		if (!(entity instanceof Husk husk) || !MadokuMobManager.isEnabled()) {
			return "";
		}
		String fileKey = fileKeyForType(husk.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return "";
		}
		JsonObject resolved = resolveActiveHuskRoot(husk);
		JsonObject statsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_STATS);
		String reference = readString(statsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
		emitHuskDropsDebug(husk, "custom_drop_reference", fileKey, true, reference);
		return reference;
	}

	private static JsonObject resolveActiveHuskRoot(Husk husk) {
		if (husk == null) {
			return new JsonObject();
		}
		JsonObject fileRoot = MadokuMobManager.resolveHuskRootForRuntime(husk.getType());
		JsonObject variant = resolveAgeVariantRoot(readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP), husk.isBaby());
		return mergeHuskFileSettings(fileRoot, variant);
	}

	private static JsonObject mergeHuskFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
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

	private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(Husk husk, RandomSource random) {
		if (husk == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
		}
		if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
			return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
		}
		String equipmentReference = resolveDefaultMobEquipmentReference();
		if (equipmentReference.isBlank()) {
			return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
		}
		return applyEquipmentSetLoadout(
			husk,
			equipmentReference,
			EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
			random
		);
	}

	private static EquipmentLoadoutResult applySpawnEquipmentSetLoadout(Husk husk, JsonObject variantRoot, RandomSource random) {
		if (husk == null || variantRoot == null || random == null) {
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
		return applyEquipmentSetLoadout(husk, equipmentReference, chancePercent, random);
	}

	private static EquipmentLoadoutResult applyEquipmentSetLoadout(Husk husk, String equipmentReference, double chancePercent, RandomSource random) {
		if (husk == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
		}
		if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
			return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
		}
		EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, husk.getType());
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
		MadokuMobManager.clearArmorSlotsForRuntime(husk);
		for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
			husk.setItemSlot(entry.getKey(), entry.getValue());
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

	private static void applyHuskBehaviorToggles(Husk husk, JsonObject fileRoot, JsonObject variantRoot) {
		if (husk == null) {
			return;
		}
		JsonObject behaviorRoot = MadokuMobManager.readMobBehaviorRootForRuntime(variantRoot);
		JsonObject goalsRoot = MadokuMobManager.readMobGoalsRootForRuntime(variantRoot);
		boolean goalsEnabled = readBoolean(goalsRoot, MobConfigManager.FIELD_ENABLED, true);

		boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		boolean overrideGoals = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_GOALS, true);

		if (overrideBehavior) {
			husk.setCanPickUpLoot(MadokuMobManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, true));
		}
		if (overrideBehavior || overrideGoals) {
			boolean canBreakDoors = overrideGoals && goalsEnabled && hasGoalEnabled(goalsRoot, "break_door")
				? readGoalEnabled(goalsRoot, "break_door", true)
				: readBoolean(behaviorRoot, MobConfigManager.FIELD_CAN_BREAK_DOORS, true);
			husk.setCanBreakDoors(canBreakDoors);
		}
		if (overrideBehavior) {
			boolean callsReinforcements = readBoolean(behaviorRoot, "calls_reinforcements_when_hurt", true);
			if (!callsReinforcements) {
				disableHuskReinforcementsForRuntime(husk);
			}
		}
	}

	private static void disableHuskReinforcementsForRuntime(Husk husk) {
		MadokuMobManager.disableZombieReinforcementsForRuntime(husk);
	}

	private static JsonObject resolveHuskVariantGroupRoot(
		JsonObject fileConfigRoot,
		JsonObject fileRoot,
		ServerLevelAccessor world,
		boolean spawnContext
	) {
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return new JsonObject();
		}
		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		if (!spawnContext || world == null || !overrideSpawnRules) {
			return defaultGroup;
		}

		String selectedVariant = selectHuskVariantKey(fileRoot, world);
		if (selectedVariant.isBlank() || HUSK_VARIANT_DEFAULT_KEY.equals(selectedVariant)) {
			return defaultGroup;
		}
		JsonObject selected = resolveHuskVariantRootByKey(fileRoot, selectedVariant);
		return selected.entrySet().isEmpty() ? defaultGroup : resolveEffectiveHuskVariantGroup(defaultGroup, selected);
	}

	private static JsonObject resolveEffectiveHuskVariantGroup(JsonObject defaultGroup, JsonObject variantGroup) {
		if (variantGroup == null || variantGroup.entrySet().isEmpty()) {
			return defaultGroup == null ? new JsonObject() : defaultGroup.deepCopy();
		}
		boolean sharedComponents = readBoolean(variantGroup, MobConfigManager.FIELD_SHARED_COMPONENTS, false);
		if (!sharedComponents) {
			return variantGroup;
		}
		JsonObject merged = defaultGroup == null ? new JsonObject() : defaultGroup.deepCopy();
		deepMergeOverride(merged, variantGroup);
		return merged;
	}

	private static String selectHuskVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
		double defaultWeight = Math.max(0.0D, resolveHuskVariantSpawnWeight(readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP), 100.0D));
		List<HuskVariantWeight> weightedVariants = new java.util.ArrayList<>();
		double total = defaultWeight;
		for (Map.Entry<String, JsonObject> entry : collectHuskVariantRoots(fileRoot).entrySet()) {
			double weight = Math.max(0.0D, resolveHuskVariantSpawnWeight(entry.getValue(), 0.0D));
			if (weight <= 0.0D) {
				continue;
			}
			total += weight;
			weightedVariants.add(new HuskVariantWeight(entry.getKey(), weight));
		}
		if (total <= 0.0D || world == null) {
			return HUSK_VARIANT_DEFAULT_KEY;
		}
		double roll = world.getRandom().nextDouble() * total;
		if (roll < defaultWeight) {
			return HUSK_VARIANT_DEFAULT_KEY;
		}
		roll -= defaultWeight;
		for (HuskVariantWeight variant : weightedVariants) {
			if (roll < variant.weight()) {
				return variant.key();
			}
			roll -= variant.weight();
		}
		return HUSK_VARIANT_DEFAULT_KEY;
	}

	private static double resolveHuskVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		if (variantRoot == null || variantRoot.entrySet().isEmpty()) {
			return fallback;
		}
		double direct = readSpawnWeight(variantRoot, Double.NaN);
		if (Double.isFinite(direct)) {
			return direct;
		}
		JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
		double nested = readDouble(spawnRules, MobConfigManager.FIELD_SPAWN_WEIGHT, Double.NaN);
		return Double.isFinite(nested) ? nested : fallback;
	}

	private static Map<String, JsonObject> collectHuskVariantRoots(JsonObject fileRoot) {
		Map<String, JsonObject> variants = new java.util.LinkedHashMap<>();
		if (fileRoot == null) {
			return variants;
		}
		for (Map.Entry<String, JsonElement> entry : fileRoot.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			if (isHuskConfigRootKey(entry.getKey())) {
				continue;
			}
			variants.put(normalizeKey(entry.getKey()), entry.getValue().getAsJsonObject());
		}
		return variants;
	}

	private static boolean isHuskConfigRootKey(String key) {
		String normalizedKey = normalizeKey(key);
		return normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DEFAULT_GROUP))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_ENABLED))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_STATS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_BEHAVIOR))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_OVERRIDE_GOALS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_BABY))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_MOB_VARIANT))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_CUSTOM_MOB_DROPS))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_DIFFICULTY_SCALE))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING))
			|| normalizedKey.equals(normalizeKey(MobConfigManager.FIELD_WEAPON_DAMAGE));
	}

	private static JsonObject resolveHuskVariantRootByKey(JsonObject fileRoot, String variantKey) {
		if (fileRoot == null || variantKey == null || variantKey.isBlank()) {
			return new JsonObject();
		}
		JsonObject variant = collectHuskVariantRoots(fileRoot).get(normalizeKey(variantKey));
		return variant == null ? new JsonObject() : variant;
	}

	private static double resolveHuskBabyChanceForRuntime(
		double adultWeight,
		double babyWeight,
		DifficultyInstance difficulty,
		Husk husk
	) {
		if (difficulty == null || husk == null) {
			return 0.0D;
		}
		return resolveBabyChance(
			adultWeight,
			babyWeight,
			difficulty.getDifficulty(),
			isHardcoreWorld(husk.level())
		);
	}

	private static double resolveBabyChance(double adultWeight, double babyWeight, Difficulty difficulty, boolean hardcore) {
		SpawnWeightPair shifted = resolveDifficultyShiftedSpawnWeights(
			adultWeight,
			babyWeight,
			difficulty,
			hardcore,
			SPECIAL_SPAWN_WEIGHT_DIFFICULTY_STEP
		);
		double total = shifted.regularWeight + shifted.specialWeight;
		return total <= 0.0D ? 0.05D : Mth.clamp(shifted.specialWeight / total, 0.0D, 1.0D);
	}

	private static SpawnWeightPair resolveDifficultyShiftedSpawnWeights(
		double regularWeight,
		double specialWeight,
		Difficulty difficulty,
		boolean hardcore,
		double difficultyStep
	) {
		double regular = Math.max(0.0D, regularWeight);
		double special = Math.max(0.0D, specialWeight);
		if (difficulty == null) {
			return new SpawnWeightPair(regular, special);
		}
		double shift = Math.max(0.0D, difficultyStep) * resolveDifficultyTier(difficulty, hardcore);
		if (shift > 0.0D) {
			double transferred = Math.min(shift, regular);
			regular -= transferred;
			special += transferred;
		} else if (shift < 0.0D) {
			double transferred = Math.min(-shift, special);
			special -= transferred;
			regular += transferred;
		}
		return new SpawnWeightPair(regular, special);
	}

	private static boolean isHardcoreWorld(net.minecraft.world.level.Level level) {
		return level != null && level.getServer() != null && level.getServer().isHardcore();
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

	private static double readSpawnWeight(JsonObject root, double fallback) {
		JsonObject spawnRules = readObject(root, MobConfigManager.FIELD_SPAWN_RULES);
		return readDouble(spawnRules, MobConfigManager.FIELD_SPAWN_WEIGHT, fallback);
	}

	private static void applyWeaponDamagePolicy(Husk husk, JsonObject resolvedRoot) {
		if (husk == null || resolvedRoot == null) {
			return;
		}
		boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
		if (weaponDamageEnabled) {
			return;
		}
		stripHeldAttackDamageModifiers(husk, EquipmentSlot.MAINHAND);
		stripHeldAttackDamageModifiers(husk, EquipmentSlot.OFFHAND);
	}

	public static boolean applyHungerAttackEffect(Husk husk, LivingEntity target, MobEffectInstance effect, Entity attacker) {
		if (husk == null || target == null || effect == null || attacker == null) {
			return false;
		}
		String fileKey = fileKeyForType(husk.getType());
		if (fileKey.isBlank()) {
			return target.addEffect(effect, attacker);
		}
		if (!MadokuMobManager.isEnabled() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return target.addEffect(effect, attacker);
		}

		JsonObject fileRoot = MadokuMobManager.resolveHuskRootForRuntime(husk.getType());
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		JsonObject variant = resolveAgeVariantRoot(defaultGroup, husk.isBaby());
		variant = mergeHuskFileSettings(fileRoot, variant);
		JsonObject behaviorRoot = MadokuMobManager.readMobBehaviorRootForRuntime(variant);
		boolean appliesHungerOnHit = readBoolean(behaviorRoot, MobConfigManager.FIELD_APPLIES_HUNGER_ON_HIT, true);
		return appliesHungerOnHit && target.addEffect(effect, attacker);
	}

	private static void stripHeldAttackDamageModifiers(Husk husk, EquipmentSlot slot) {
		if (husk == null || slot == null) {
			return;
		}
		ItemStack stack = husk.getItemBySlot(slot);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack normalized = stack.copy();
		normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		husk.setItemSlot(slot, normalized);
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

	private static void emitHuskEquipmentDebug(Husk husk, String phase, String fileKey, EquipmentLoadoutResult result) {
		if (husk == null || result == null || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_HUSK_EQUIPMENT)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_HUSK_EQUIPMENT, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("husk:" + husk.getUUID())
			.field("phase", phase)
			.field("file_key", fileKey)
			.field("mob_type", husk.getType().toShortString())
			.field("is_baby", husk.isBaby())
			.field("applied", result.applied())
			.field("reason", result.reason())
			.field("equipment_ref", result.equipmentReference())
			.field("chance_percent", result.chancePercent())
			.field("armor_set", result.armorSet())
			.field("equipped_pieces", result.equippedPieces())
			.field("required_pieces", result.requiredPieces());
		if (husk.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static void emitHuskDropsDebug(Husk husk, String phase, String fileKey, boolean customDropsEnabled, String configuredReference) {
		if (husk == null || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_HUSK_DROPS)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_HUSK_DROPS, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("husk:" + husk.getUUID())
			.field("phase", phase)
			.field("file_key", fileKey)
			.field("mob_type", husk.getType().toShortString())
			.field("is_baby", husk.isBaby())
			.field("custom_drops_enabled", customDropsEnabled)
			.field("configured_reference", configuredReference == null || configuredReference.isBlank() ? "unset" : configuredReference);
		if (husk.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static JsonObject resolveAgeVariantRoot(JsonObject defaultGroupRoot, boolean baby) {
		if (defaultGroupRoot == null || defaultGroupRoot.entrySet().isEmpty()) {
			return new JsonObject();
		}
		JsonObject sharedRoot = defaultGroupRoot.deepCopy();
		sharedRoot.remove(MobConfigManager.FIELD_ADULT_GROUP);
		sharedRoot.remove(MobConfigManager.FIELD_BABY_GROUP);
		JsonObject ageOverride = readObject(
			defaultGroupRoot,
			baby ? MobConfigManager.FIELD_BABY_GROUP : MobConfigManager.FIELD_ADULT_GROUP
		);
		if (ageOverride.entrySet().isEmpty()) {
			return sharedRoot;
		}
		return mergeJsonWithOverride(sharedRoot, ageOverride);
	}

	private static JsonObject mergeJsonWithOverride(JsonObject base, JsonObject override) {
		JsonObject merged = base == null ? new JsonObject() : base.deepCopy();
		if (override == null) {
			return merged;
		}
		deepMergeOverride(merged, override);
		return merged;
	}

	private static void deepMergeOverride(JsonObject target, JsonObject override) {
		if (target == null || override == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value != null && value.isJsonObject() && target.has(key) && target.get(key).isJsonObject()) {
				deepMergeOverride(target.getAsJsonObject(key), value.getAsJsonObject());
				continue;
			}
			target.add(key, value == null ? JsonNull.INSTANCE : value.deepCopy());
		}
	}

	private static void copyIfMissing(JsonObject target, JsonObject source, String key) {
		if (target == null || source == null || key == null || key.isBlank()) {
			return;
		}
		if (!target.has(key) && source.has(key)) {
			target.add(key, source.get(key).deepCopy());
		}
	}

	private static boolean hasGoalEnabled(JsonObject goalsRoot, String goalKey) {
		if (goalsRoot == null || goalKey == null || goalKey.isBlank()) {
			return false;
		}
		JsonElement goal = goalsRoot.get(goalKey);
		return goal != null && goal.isJsonObject() && goal.getAsJsonObject().has(MobConfigManager.FIELD_ENABLED);
	}

	private static boolean readGoalEnabled(JsonObject goalsRoot, String goalKey, boolean fallback) {
		if (goalsRoot == null || goalKey == null || goalKey.isBlank()) {
			return fallback;
		}
		JsonElement goal = goalsRoot.get(goalKey);
		if (goal == null || !goal.isJsonObject()) {
			return fallback;
		}
		return readBoolean(goal.getAsJsonObject(), MobConfigManager.FIELD_ENABLED, fallback);
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

	private static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String fileKeyForType(EntityType<?> type) {
		return type == EntityType.HUSK ? MobConfigManager.FILE_HUSK : "";
	}

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-husk.json";
	}

	private record EquipmentLoadoutResult(
		boolean applied,
		String reason,
		String equipmentReference,
		double chancePercent,
		String armorSet,
		int equippedPieces,
		int requiredPieces
	) {}

	private record SpawnWeightPair(double regularWeight, double specialWeight) {}

	private record HuskVariantWeight(String key, double weight) {}

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
