package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.loot.system.EquipmentConfigManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MadokuMobZombie {
	private static final String ZOMBIE_VARIANT_TAG_PREFIX = "madoku-craft.zombie.variant:";
	private static final String ZOMBIE_VARIANT_DEFAULT_KEY = "default";

	private MadokuMobZombie() {
	}

	public static void applySpawnOverrides(
		Zombie zombie,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (zombie == null || world == null || difficulty == null) {
			return;
		}
		boolean mobSystemEnabled = MadokuMobManager.isEnabled();
		String fileKey = fileKeyForType(zombie.getType());
		if (fileKey.isBlank()) {
			return;
		}
		if (!mobSystemEnabled) {
			applySpawnEquipmentLoadoutWhenMobSystemDisabled(zombie, world.getRandom());
			return;
		}
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveZombieRootForRuntime(zombie.getType());
		JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, world, true);
		JsonObject adult = MadokuMobManager.resolveAgeVariantRoot(variantGroup, false);
		JsonObject baby = MadokuMobManager.resolveAgeVariantRoot(variantGroup, true);
		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean babyEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_BABY, true);

		if (overrideSpawnRules) {
			boolean shouldBeBaby = false;
			if (babyEnabled) {
				double babyChance = MadokuMobManager.resolveAgeVariantChanceForRuntime(
					MadokuMobManager.readSpawnRuleDoubleForRuntime(adult, MobConfigManager.FIELD_SPAWN_WEIGHT, 95.0D),
					MadokuMobManager.readSpawnRuleDoubleForRuntime(baby, MobConfigManager.FIELD_SPAWN_WEIGHT, 5.0D),
					difficulty,
					zombie
				);
				shouldBeBaby = world.getRandom().nextFloat() < babyChance;
			}
			zombie.setBaby(shouldBeBaby);
			clearVanillaZombieJockeyMount(zombie);
		}

		JsonObject variant = zombie.isBaby() ? baby : adult;
		variant = mergeZombieFileSettings(fileRoot, variant);
		if (overrideSpawnRules && applyConfiguredZombieAlternativeMobReplacement(zombie, variant, spawnReason)) {
			return;
		}
		if (overrideSpawnRules) {
			MadokuMobManager.applyConfiguredMobJockey(zombie, world, difficulty, variant, spawnReason, true, zombie.isBaby());
			applySpawnEquipmentSetLoadout(zombie, variant, world.getRandom());
		}
		applyWeaponDamagePolicy(zombie, variant);
		applyZombieBehaviorToggles(zombie, fileConfigRoot, variant);
		if (overrideStats) {
			MadokuMobManager.applyUniversalStatsForRuntime(zombie, variant);
		}
	}

	public static boolean shouldOverrideSpawnRules(Zombie zombie) {
		if (zombie == null || zombie.getType() != madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return false;
		}
		if (!MadokuMobManager.isEnabled() || !MadokuMobManager.isMobFileEnabledForRuntime(MobConfigManager.FILE_ZOMBIE)) {
			return false;
		}
		JsonObject zombieFileRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(MobConfigManager.FILE_ZOMBIE);
		return readBoolean(zombieFileRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(zombie.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveZombieRootForRuntime(zombie.getType());
		JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, zombie.isBaby());
		variant = mergeZombieFileSettings(fileRoot, variant);
		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalBaseStatsForRuntime(zombie, variant);
		applyWeaponDamagePolicy(zombie, variant);
		applyZombieBehaviorToggles(zombie, fileConfigRoot, variant);
		modified |= applyConfiguredZombieAlternativeMobReplacement(zombie, variant, EntitySpawnReason.NATURAL);
		return modified;
	}

	public static boolean applyLoadedEntityDifficultyOverrides(LivingEntity entity) {
		if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(zombie.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveZombieRootForRuntime(zombie.getType());
		JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, zombie.isBaby());
		variant = mergeZombieFileSettings(fileRoot, variant);
		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalDifficultyStatsForRuntime(zombie, variant);
		return modified;
	}

	static boolean isCustomMobDropsEnabled(LivingEntity entity) {
		if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = fileKeyForType(zombie.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject resolved = resolveActiveZombieRoot(zombie, fileConfigRoot);
		boolean enabled = readBoolean(
			resolved,
			MobConfigManager.FIELD_CUSTOM_MOB_DROPS,
			true
		);
		return enabled;
	}

	static String resolveMobDropsConfigReference(LivingEntity entity) {
		if (!(entity instanceof Zombie zombie) || !MadokuMobManager.isEnabled()) {
			return "";
		}
		String fileKey = fileKeyForType(zombie.getType());
		if (fileKey.isBlank() || !MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return "";
		}
		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject resolved = resolveActiveZombieRoot(zombie, fileConfigRoot);
		JsonObject statsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_STATS);
		String reference = readString(statsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
		return reference;
	}

	private static JsonObject resolveActiveZombieRoot(Zombie zombie, JsonObject fileConfigRoot) {
		if (zombie == null) {
			return new JsonObject();
		}
		JsonObject fileRoot = MadokuMobManager.resolveZombieRootForRuntime(zombie.getType());
		JsonObject variantGroup = resolveZombieVariantGroupRoot(zombie, fileConfigRoot, fileRoot, null, false);
		JsonObject variant = MadokuMobManager.resolveAgeVariantRoot(variantGroup, zombie.isBaby());
		return mergeZombieFileSettings(fileRoot, variant);
	}

	private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(Zombie zombie, RandomSource random) {
		if (zombie == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", "", 0.0D, "none", 0, 0);
		}
		if (!EquipmentConfigManager.isCustomEntityEquipmentEnabled()) {
			return new EquipmentLoadoutResult(false, "custom_entity_equipment_disabled", "", 0.0D, "none", 0, 0);
		}
		String equipmentReference = resolveDefaultMobEquipmentReference(zombie.getType());
		if (equipmentReference.isBlank()) {
			return new EquipmentLoadoutResult(false, "default_reference_blank", equipmentReference, 0.0D, "none", 0, 0);
		}
		return applyEquipmentSetLoadout(
			zombie,
			equipmentReference,
			EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
			random
		);
	}

	private static EquipmentLoadoutResult applySpawnEquipmentSetLoadout(Zombie zombie, JsonObject variantRoot, RandomSource random) {
		if (zombie == null || variantRoot == null || random == null) {
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
		return applyEquipmentSetLoadout(zombie, equipmentReference, chancePercent, random);
	}

	private static boolean applyConfiguredZombieAlternativeMobReplacement(
		Zombie zombie,
		JsonObject variantRoot,
		EntitySpawnReason spawnReason
	) {
		if (zombie == null || variantRoot == null || zombie.getType() != madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return false;
		}
		JsonObject spawnRules = readObject(variantRoot, MobConfigManager.FIELD_SPAWN_RULES);
		JsonObject alternativeMobRoot = readObject(spawnRules, MobConfigManager.FIELD_SPAWN_ALTERNATIVE_MOB);
		if (alternativeMobRoot.entrySet().isEmpty() || !readBoolean(alternativeMobRoot, MobConfigManager.FIELD_ENABLED, false)) {
			return false;
		}
		EntityType<?> replacementType = MadokuMobManager.resolveConfiguredMobEntityType(alternativeMobRoot, zombie.isBaby());
		if (replacementType == null || replacementType == madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return false;
		}
		MadokuMobManager.queueZombieReplacement(zombie, replacementType, spawnReason);
		return true;
	}

	private static void clearVanillaZombieJockeyMount(Zombie zombie) {
		if (zombie == null) {
			return;
		}
		Entity vehicle = zombie.getVehicle();
		if (vehicle == null || vehicle.getType() != madoku.craft.entity.MadokuEntityTypes.CHICKEN) {
			return;
		}
		zombie.stopRiding();
		if (vehicle.isAlive()) {
			vehicle.discard();
		}
	}

	private static EquipmentLoadoutResult applyEquipmentSetLoadout(Zombie zombie, String equipmentReference, double chancePercent, RandomSource random) {
		if (zombie == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
		}
		if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
			return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
		}
		EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, zombie.getType());
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
		MadokuMobManager.clearArmorSlotsForRuntime(zombie);
		for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
			zombie.setItemSlot(entry.getKey(), entry.getValue());
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

	private static JsonObject resolveZombieVariantGroupRoot(
		Zombie zombie,
		JsonObject fileConfigRoot,
		JsonObject fileRoot,
		ServerLevelAccessor world,
		boolean spawnContext
	) {
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			clearZombieVariantTag(zombie);
			return new JsonObject();
		}
		boolean variantEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_VARIANT, false);
		if (!variantEnabled) {
			clearZombieVariantTag(zombie);
			return defaultGroup;
		}

		String storedVariant = readStoredZombieVariantKey(zombie);
		if (!storedVariant.isBlank()) {
			if (ZOMBIE_VARIANT_DEFAULT_KEY.equals(storedVariant)) {
				return defaultGroup;
			}
			JsonObject known = resolveZombieVariantRootByKey(fileRoot, storedVariant);
			if (!known.entrySet().isEmpty()) {
				return MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, known);
			}
		}

		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		if (!spawnContext || !overrideSpawnRules || world == null) {
			return defaultGroup;
		}

		String selectedVariant = selectZombieVariantKey(fileRoot, world);
		if (selectedVariant.isBlank()) {
			selectedVariant = ZOMBIE_VARIANT_DEFAULT_KEY;
		}
		writeZombieVariantTag(zombie, selectedVariant);
		if (ZOMBIE_VARIANT_DEFAULT_KEY.equals(selectedVariant)) {
			return defaultGroup;
		}
		JsonObject selected = resolveZombieVariantRootByKey(fileRoot, selectedVariant);
		return selected.entrySet().isEmpty() ? defaultGroup : MadokuMobManager.resolveSharedVariantGroupRoot(defaultGroup, selected);
	}

	@SuppressWarnings("unused")
	private static JsonObject resolveAgeVariantRoot(JsonObject variantGroupRoot, boolean baby) {
		return MadokuMobManager.resolveAgeVariantRoot(variantGroupRoot, baby);
	}

	private static JsonObject mergeZombieFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
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

	private static String selectZombieVariantKey(JsonObject fileRoot, ServerLevelAccessor world) {
		return MadokuMobManager.selectWeightedVariantKey(
			fileRoot,
			world == null ? null : world.getRandom(),
			MobConfigManager.FIELD_DEFAULT_GROUP,
			ZOMBIE_VARIANT_DEFAULT_KEY,
			MadokuMobZombie::isReservedZombieGroupKey,
			variantRoot -> resolveZombieVariantSpawnWeight(variantRoot, 0.0D)
		);
	}

	private static double resolveZombieVariantSpawnWeight(JsonObject variantRoot, double fallback) {
		return MadokuMobManager.resolveVariantSpawnWeight(variantRoot, fallback);
	}

	private static boolean isReservedZombieGroupKey(String normalizedKey) {
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

	private static JsonObject resolveZombieVariantRootByKey(JsonObject fileRoot, String variantKey) {
		return MadokuMobManager.resolveVariantRootByKey(
			fileRoot,
			variantKey,
			MobConfigManager.FIELD_DEFAULT_GROUP,
			MadokuMobZombie::isReservedZombieGroupKey
		);
	}

	private static String readStoredZombieVariantKey(Zombie zombie) {
		if (zombie == null) {
			return "";
		}
		for (String tag : zombie.entityTags()) {
			if (tag == null || !tag.startsWith(ZOMBIE_VARIANT_TAG_PREFIX)) {
				continue;
			}
			String normalized = normalizeKey(tag.substring(ZOMBIE_VARIANT_TAG_PREFIX.length()));
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private static void writeZombieVariantTag(Zombie zombie, String variantKey) {
		if (zombie == null || variantKey == null || variantKey.isBlank()) {
			return;
		}
		clearZombieVariantTag(zombie);
		zombie.addTag(ZOMBIE_VARIANT_TAG_PREFIX + normalizeKey(variantKey));
	}

	private static void clearZombieVariantTag(Zombie zombie) {
		if (zombie == null) {
			return;
		}
		String existing = null;
		for (String tag : zombie.entityTags()) {
			if (tag != null && tag.startsWith(ZOMBIE_VARIANT_TAG_PREFIX)) {
				existing = tag;
				break;
			}
		}
		if (existing != null) {
			zombie.removeTag(existing);
		}
	}

	private static void applyZombieBehaviorToggles(Zombie zombie, JsonObject fileRoot, JsonObject variantRoot) {
		if (zombie == null) {
			return;
		}
		JsonObject behaviorRoot = MadokuMobManager.readMobBehaviorRootForRuntime(variantRoot);
		boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);

		if (overrideBehavior) {
			zombie.setCanPickUpLoot(MadokuMobManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, false));
		}
		if (overrideBehavior) {
			boolean callsReinforcements = readBoolean(behaviorRoot, MobConfigManager.FIELD_CALLS_REINFORCEMENTS_WHEN_HURT, !zombie.isBaby());
			if (!callsReinforcements) {
				MadokuMobManager.disableZombieReinforcementsForRuntime(zombie);
			}
		}
	}

	private static void applyWeaponDamagePolicy(Zombie zombie, JsonObject resolvedZombieRoot) {
		if (zombie == null || resolvedZombieRoot == null) {
			return;
		}
		boolean weaponDamageEnabled = readBoolean(resolvedZombieRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
		if (weaponDamageEnabled) {
			return;
		}
		stripHeldAttackDamageModifiers(zombie, EquipmentSlot.MAINHAND);
		stripHeldAttackDamageModifiers(zombie, EquipmentSlot.OFFHAND);
	}

	private static void stripHeldAttackDamageModifiers(Zombie zombie, EquipmentSlot slot) {
		if (zombie == null || slot == null) {
			return;
		}
		ItemStack stack = zombie.getItemBySlot(slot);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack normalized = stack.copy();
		normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		zombie.setItemSlot(slot, normalized);
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
		if (type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return MobConfigManager.FILE_ZOMBIE;
		}
		return "";
	}

	private static String resolveDefaultMobEquipmentReference(EntityType<?> type) {
		if (type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return "minecraft-equipment-zombie.json";
		}
		return "";
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


