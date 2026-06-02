package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.loot.system.EquipmentConfigManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MadokuMobZombieVillager {
	private static final String METRIC_ZOMBIE_VILLAGER_EQUIPMENT = "mob.zombie_villager_equipment_runtime";
	private static final String METRIC_ZOMBIE_VILLAGER_DROPS = "mob.zombie_villager_drop_runtime";

	private MadokuMobZombieVillager() {
	}

	public static void applySpawnOverrides(
		ZombieVillager zombieVillager,
		ServerLevelAccessor world,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason
	) {
		if (zombieVillager == null || world == null || difficulty == null) {
			return;
		}
		String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
		if (!MadokuMobManager.isEnabled()) {
			EquipmentLoadoutResult result = applySpawnEquipmentLoadoutWhenMobSystemDisabled(zombieVillager, world.getRandom());
			emitZombieVillagerEquipmentDebug(zombieVillager, "spawn_mob_system_disabled", fileKey, result);
			return;
		}
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveZombieVillagerRootForRuntime(zombieVillager.getType());
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		if (defaultGroup.entrySet().isEmpty()) {
			return;
		}

		JsonObject adult = resolveAgeVariantRoot(defaultGroup, false);
		JsonObject baby = resolveAgeVariantRoot(defaultGroup, true);
		boolean overrideSpawnRules = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_SPAWN_RULES, true);
		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean babyEnabled = readBoolean(fileConfigRoot, MobConfigManager.FIELD_MOB_BABY, true);

		if (overrideSpawnRules) {
			boolean shouldBeBaby = false;
			if (babyEnabled) {
				double babyChance = MadokuMobManager.resolveZombieBabyChanceForRuntime(
					MadokuMobManager.readSpawnRuleDoubleForRuntime(adult, MobConfigManager.FIELD_SPAWN_WEIGHT, 90.0D),
					MadokuMobManager.readSpawnRuleDoubleForRuntime(baby, MobConfigManager.FIELD_SPAWN_WEIGHT, 10.0D),
					difficulty,
					zombieVillager
				);
				shouldBeBaby = world.getRandom().nextFloat() < babyChance;
			}
			zombieVillager.setBaby(shouldBeBaby);
		}

		JsonObject variant = zombieVillager.isBaby() ? baby : adult;
		variant = mergeZombieVillagerFileSettings(fileRoot, variant);
		if (overrideSpawnRules) {
			EquipmentLoadoutResult result = applySpawnEquipmentSetLoadout(zombieVillager, variant, world.getRandom());
			emitZombieVillagerEquipmentDebug(zombieVillager, "spawn_mob_system_enabled", fileKey, result);
		}
		applyWeaponDamagePolicy(zombieVillager, variant);
		applyZombieVillagerBehaviorToggles(zombieVillager, fileConfigRoot, variant);
		if (overrideStats) {
			MadokuMobManager.applyUniversalStatsForRuntime(zombieVillager, variant);
		}
	}

	public static boolean applyLoadedEntityOverrides(LivingEntity entity) {
		if (!(entity instanceof ZombieVillager zombieVillager) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}

		JsonObject fileConfigRoot = MadokuMobManager.resolveMobFileConfigRootForRuntime(fileKey);
		JsonObject fileRoot = MadokuMobManager.resolveZombieVillagerRootForRuntime(zombieVillager.getType());
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		JsonObject variant = resolveAgeVariantRoot(defaultGroup, zombieVillager.isBaby());
		variant = mergeZombieVillagerFileSettings(fileRoot, variant);

		boolean overrideStats = readBoolean(fileConfigRoot, MobConfigManager.FIELD_OVERRIDE_STATS, true);
		boolean modified = overrideStats && MadokuMobManager.applyUniversalStatsForRuntime(zombieVillager, variant);
		applyWeaponDamagePolicy(zombieVillager, variant);
		applyZombieVillagerBehaviorToggles(zombieVillager, fileConfigRoot, variant);
		return modified;
	}

	static boolean isCustomMobDropsEnabled(LivingEntity entity) {
		if (!(entity instanceof ZombieVillager zombieVillager) || entity.level().isClientSide() || !MadokuMobManager.isEnabled()) {
			return false;
		}
		String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return false;
		}
		JsonObject resolved = resolveActiveZombieVillagerRoot(zombieVillager);
		boolean enabled = readBoolean(resolved, MobConfigManager.FIELD_CUSTOM_MOB_DROPS, true);
		emitZombieVillagerDropsDebug(zombieVillager, "custom_drop_gate", fileKey, enabled, "");
		return enabled;
	}

	static String resolveMobDropsConfigReference(LivingEntity entity) {
		if (!(entity instanceof ZombieVillager zombieVillager) || !MadokuMobManager.isEnabled()) {
			return "";
		}
		String fileKey = MobConfigManager.FILE_ZOMBIE_VILLAGER;
		if (!MadokuMobManager.isMobFileEnabledForRuntime(fileKey)) {
			return "";
		}
		JsonObject resolved = resolveActiveZombieVillagerRoot(zombieVillager);
		JsonObject statsRoot = readObject(resolved, MobConfigManager.FIELD_MOB_STATS);
		String reference = readString(statsRoot, MobConfigManager.FIELD_MOB_DROPS, "");
		emitZombieVillagerDropsDebug(zombieVillager, "custom_drop_reference", fileKey, true, reference);
		return reference;
	}

	private static JsonObject resolveActiveZombieVillagerRoot(ZombieVillager zombieVillager) {
		if (zombieVillager == null) {
			return new JsonObject();
		}
		JsonObject fileRoot = MadokuMobManager.resolveZombieVillagerRootForRuntime(zombieVillager.getType());
		JsonObject defaultGroup = readObject(fileRoot, MobConfigManager.FIELD_DEFAULT_GROUP);
		JsonObject variant = resolveAgeVariantRoot(defaultGroup, zombieVillager.isBaby());
		return mergeZombieVillagerFileSettings(fileRoot, variant);
	}

	private static JsonObject mergeZombieVillagerFileSettings(JsonObject fileRoot, JsonObject variantRoot) {
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

	private static EquipmentLoadoutResult applySpawnEquipmentLoadoutWhenMobSystemDisabled(ZombieVillager zombieVillager, RandomSource random) {
		if (zombieVillager == null || random == null) {
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
			zombieVillager,
			equipmentReference,
			EquipmentConfigManager.customEntityEquipmentChanceWhenMobSystemDisabled(),
			random
		);
	}

	private static EquipmentLoadoutResult applySpawnEquipmentSetLoadout(ZombieVillager zombieVillager, JsonObject variantRoot, RandomSource random) {
		if (zombieVillager == null || variantRoot == null || random == null) {
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
		return applyEquipmentSetLoadout(zombieVillager, equipmentReference, chancePercent, random);
	}

	private static EquipmentLoadoutResult applyEquipmentSetLoadout(
		ZombieVillager zombieVillager,
		String equipmentReference,
		double chancePercent,
		RandomSource random
	) {
		if (zombieVillager == null || random == null) {
			return new EquipmentLoadoutResult(false, "invalid_inputs", equipmentReference, chancePercent, "none", 0, 0);
		}
		if (chancePercent <= 0.0D || random.nextDouble() * 100.0D >= chancePercent) {
			return new EquipmentLoadoutResult(false, "chance_failed", equipmentReference, chancePercent, "none", 0, 0);
		}
		EquipmentConfigManager.EquipmentProfile profile = EquipmentConfigManager.resolveProfile(equipmentReference, zombieVillager.getType());
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
		MadokuMobManager.clearArmorSlotsForRuntime(zombieVillager);
		for (Map.Entry<EquipmentSlot, ItemStack> entry : rolledBySlot.entrySet()) {
			zombieVillager.setItemSlot(entry.getKey(), entry.getValue());
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

	private static void applyZombieVillagerBehaviorToggles(ZombieVillager zombieVillager, JsonObject fileRoot, JsonObject variantRoot) {
		if (zombieVillager == null) {
			return;
		}
		JsonObject behaviorRoot = MadokuMobManager.readMobBehaviorRootForRuntime(variantRoot);
		JsonObject goalsRoot = MadokuMobManager.readMobGoalsRootForRuntime(variantRoot);
		boolean goalsEnabled = readBoolean(goalsRoot, MobConfigManager.FIELD_ENABLED, true);

		boolean overrideBehavior = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_BEHAVIOR, true);
		boolean overrideGoals = readBoolean(fileRoot, MobConfigManager.FIELD_OVERRIDE_GOALS, true);

		if (overrideBehavior) {
			zombieVillager.setCanPickUpLoot(MadokuMobManager.readMobBehaviorBooleanForRuntime(variantRoot, MobConfigManager.FIELD_CAN_PICK_UP_LOOT, false));
		}
		if (overrideBehavior || overrideGoals) {
			boolean canBreakDoors = overrideGoals && goalsEnabled && hasGoalEnabled(goalsRoot, "break_door")
				? readGoalEnabled(goalsRoot, "break_door", false)
				: readBoolean(behaviorRoot, MobConfigManager.FIELD_CAN_BREAK_DOORS, false);
			zombieVillager.setCanBreakDoors(canBreakDoors);
		}
		if (overrideBehavior) {
			boolean callsReinforcements = readBoolean(behaviorRoot, "calls_reinforcements_when_hurt", !zombieVillager.isBaby());
			if (!callsReinforcements) {
				MadokuMobManager.disableZombieReinforcementsForRuntime(zombieVillager);
			}
		}
	}

	private static void applyWeaponDamagePolicy(ZombieVillager zombieVillager, JsonObject resolvedRoot) {
		if (zombieVillager == null || resolvedRoot == null) {
			return;
		}
		boolean weaponDamageEnabled = readBoolean(resolvedRoot, MobConfigManager.FIELD_WEAPON_DAMAGE, true);
		if (weaponDamageEnabled) {
			return;
		}
		stripHeldAttackDamageModifiers(zombieVillager, EquipmentSlot.MAINHAND);
		stripHeldAttackDamageModifiers(zombieVillager, EquipmentSlot.OFFHAND);
	}

	private static void stripHeldAttackDamageModifiers(ZombieVillager zombieVillager, EquipmentSlot slot) {
		if (zombieVillager == null || slot == null) {
			return;
		}
		ItemStack stack = zombieVillager.getItemBySlot(slot);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack normalized = stack.copy();
		normalized.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
		zombieVillager.setItemSlot(slot, normalized);
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

	private static void emitZombieVillagerEquipmentDebug(
		ZombieVillager zombieVillager,
		String phase,
		String fileKey,
		EquipmentLoadoutResult result
	) {
		if (zombieVillager == null || result == null || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_ZOMBIE_VILLAGER_EQUIPMENT)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_ZOMBIE_VILLAGER_EQUIPMENT, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("zombie_villager:" + zombieVillager.getUUID())
			.field("phase", phase)
			.field("file_key", fileKey)
			.field("mob_type", zombieVillager.getType().toShortString())
			.field("is_baby", zombieVillager.isBaby())
			.field("applied", result.applied())
			.field("reason", result.reason())
			.field("equipment_ref", result.equipmentReference())
			.field("chance_percent", result.chancePercent())
			.field("armor_set", result.armorSet())
			.field("equipped_pieces", result.equippedPieces())
			.field("required_pieces", result.requiredPieces());
		if (zombieVillager.level() instanceof ServerLevel level) {
			event.tick(level.getGameTime()).world(level.dimension().toString());
		}
		event.log();
	}

	private static void emitZombieVillagerDropsDebug(
		ZombieVillager zombieVillager,
		String phase,
		String fileKey,
		boolean customDropsEnabled,
		String configuredReference
	) {
		if (zombieVillager == null || !MadokuDebug.shouldEmit(MadokuDebug.Domain.MOB, METRIC_ZOMBIE_VILLAGER_DROPS)) {
			return;
		}
		MadokuDebug.EventBuilder event = MadokuDebug.event(METRIC_ZOMBIE_VILLAGER_DROPS, MadokuDebug.Domain.MOB)
			.side(MadokuDebug.Side.SERVER)
			.subject("zombie_villager:" + zombieVillager.getUUID())
			.field("phase", phase)
			.field("file_key", fileKey)
			.field("mob_type", zombieVillager.getType().toShortString())
			.field("is_baby", zombieVillager.isBaby())
			.field("custom_drops_enabled", customDropsEnabled)
			.field("configured_reference", configuredReference == null || configuredReference.isBlank() ? "unset" : configuredReference);
		if (zombieVillager.level() instanceof ServerLevel level) {
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

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
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

	private static String resolveDefaultMobEquipmentReference() {
		return "minecraft-equipment-zombie-villager.json";
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
