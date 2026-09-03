package madoku.craft.java.core.enchant;

import madoku.craft.java.core.enchant.BooksConfigAPIManager.EnchantmentDefinition;
import madoku.craft.mixin.attributes.LootPoolSingletonContainerAccessor;
import madoku.craft.mixin.attributes.NestedLootTableAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.apache.commons.lang3.mutable.MutableFloat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static madoku.craft.java.core.enchant.BooksConfigAPIManager.AQUA_AFFINITY_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.BANE_OF_ARTHROPODS_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.BLAST_PROTECTION_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.BREACH_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.DEPTH_STRIDER_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.EFFICIENCY_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.FEATHER_FALLING_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.FORTUNE_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.LOOTING_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.FIRE_ASPECT_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.FIRE_PROTECTION_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.FLAME_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.INFINITY_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.PROJECTILE_PROTECTION_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.PROTECTION_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.SOUL_SPEED_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.SWEEPING_EDGE_ID;
import static madoku.craft.java.core.enchant.BooksConfigAPIManager.THORNS_ID;

/** Runtime group that owns configured enchantment books and enchantment effects. */
public final class EnchantBooksAPIManager {
	private static final ThreadLocal<Float> CONFIGURED_DAMAGE_REDUCTION_PERCENT = new ThreadLocal<>();
	private static final ThreadLocal<Float> VANILLA_DAMAGE_PROTECTION = new ThreadLocal<>();
	private static final ThreadLocal<LuckOfTheSeaContext> LUCK_OF_THE_SEA_CONTEXT = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> SOUL_SPEED_LOCATION_CONTEXT = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> THORNS_POST_ATTACK_CONTEXT = new ThreadLocal<>();
	private static final ThreadLocal<Float> INCOMING_DAMAGE_CONTEXT = new ThreadLocal<>();
	private static final Map<UUID, SmiteVulnerabilityState> SMITE_VULNERABILITY_BY_ENTITY = new ConcurrentHashMap<>();
	private static final String UNBREAKING_BASE_MAX_DAMAGE_KEY = "madoku_craft_unbreaking_base_max_damage";
	private static final String UNBREAKING_BASE_DAMAGE_KEY = "madoku_craft_unbreaking_base_damage";
	private static final String UNBREAKING_APPLIED_MAX_DAMAGE_KEY = "madoku_craft_unbreaking_applied_max_damage";
	private static final String UNBREAKING_APPLIED_DAMAGE_KEY = "madoku_craft_unbreaking_applied_damage";
	private static final String UNBREAKING_APPLIED_LEVEL_KEY = "madoku_craft_unbreaking_applied_level";

	private EnchantBooksAPIManager() {
	}

	static void reset() {
		CONFIGURED_DAMAGE_REDUCTION_PERCENT.remove();
		VANILLA_DAMAGE_PROTECTION.remove();
		LUCK_OF_THE_SEA_CONTEXT.remove();
		SOUL_SPEED_LOCATION_CONTEXT.remove();
		THORNS_POST_ATTACK_CONTEXT.remove();
		INCOMING_DAMAGE_CONTEXT.remove();
		SMITE_VULNERABILITY_BY_ENTITY.clear();
	}

	static void onServerTick(MinecraftServer server) {
		if (server == null || SMITE_VULNERABILITY_BY_ENTITY.isEmpty()) return;

		SMITE_VULNERABILITY_BY_ENTITY.keySet().removeIf(uuid -> {
			for (ServerLevel level : server.getAllLevels()) {
				Entity entity = level.getEntity(uuid);
				if (entity instanceof LivingEntity livingEntity && livingEntity.hasEffect(MobEffects.GLOWING)) return false;
			}
			return true;
		});
	}

	/** Creates a new book using only enabled, configured enchantment definitions. */
	static ItemStack createEnchantedBook(Player player, int enchantmentCount) {
		if (player == null || enchantmentCount <= 0 || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return ItemStack.EMPTY;

		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> available = selectEnchantments(player.getRandom(), registry, enchantmentCount);
		if (available.isEmpty()) return ItemStack.EMPTY;

		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Holder<Enchantment> enchantment : available) enchantments.set(enchantment, 1);
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
		return result;
	}

	static boolean canUpgradeByLevels(ItemStack input, int levels) {
		if (input == null || input.isEmpty() || levels <= 0 || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
		int availableUpgrades = 0;
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			EnchantmentDefinition definition = BooksConfigAPIManager.definitionForHolder(enchantment);
			int current = enchantments.getLevel(enchantment);
			if (definition != null && definition.enabled && current < definition.maximumLevel) {
				availableUpgrades += definition.maximumLevel - current;
			}
			if (availableUpgrades >= levels) {
				return true;
			}
		}
		return false;
	}

	/** Distributes allocated upgrade levels randomly across configured enchantments. */
	static ItemStack upgradeEnchantedBook(ItemStack input, int levels, RandomSource random) {
		if (input == null || input.isEmpty() || levels <= 0 || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return ItemStack.EMPTY;

		ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(existing);
		boolean changed = false;
		RandomSource resolvedRandom = random == null ? RandomSource.create() : random;
		for (int upgrade = 0; upgrade < levels; upgrade++) {
			List<Holder<Enchantment>> eligible = new ArrayList<>();
			int lowestLevel = Integer.MAX_VALUE;
			for (Holder<Enchantment> enchantment : existing.keySet()) {
				EnchantmentDefinition definition = BooksConfigAPIManager.definitionForHolder(enchantment);
				if (definition == null || !definition.enabled) continue;

				int current = upgraded.getLevel(enchantment);
				if (current >= definition.maximumLevel) continue;
				if (current < lowestLevel) {
					lowestLevel = current;
					eligible.clear();
				}
				if (current == lowestLevel) eligible.add(enchantment);
			}
			if (eligible.isEmpty()) break;

			Holder<Enchantment> selected = eligible.get(resolvedRandom.nextInt(eligible.size()));
			upgraded.set(selected, upgraded.getLevel(selected) + 1);
			changed = true;
		}
		if (!changed) return ItemStack.EMPTY;

		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		EnchantmentHelper.setEnchantments(result, upgraded.toImmutable());
		return result;
	}

	static ItemStack upgradeEnchantedBook(ItemStack input, int levels) {
		return upgradeEnchantedBook(input, levels, RandomSource.create());
	}

	/** Replaces vanilla Aqua Affinity's attribute amount with the configured percentage. */
	public static AttributeModifier applyConfiguredAquaAffinityModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(AQUA_AFFINITY_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
		return new AttributeModifier(
			vanillaModifier.id(),
			Math.max(0.0D, adjustment / 100.0D),
			vanillaModifier.operation()
		);
	}

	/** Replaces vanilla Depth Strider's water-movement efficiency with its configured percentage. */
	public static AttributeModifier applyConfiguredDepthStriderModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(DEPTH_STRIDER_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
		AttributeModifier configuredModifier = new AttributeModifier(
			vanillaModifier.id(),
			Math.max(0.0D, adjustment / 100.0D),
			vanillaModifier.operation()
		);
		return configuredModifier;
	}

	/** Replaces vanilla Efficiency's mining-efficiency attribute amount with its configured value. */
	public static AttributeModifier applyConfiguredEfficiencyModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(EFFICIENCY_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
		AttributeModifier configuredModifier = new AttributeModifier(
			vanillaModifier.id(),
			Math.max(0.0D, adjustment),
			vanillaModifier.operation()
		);
		return configuredModifier;
	}

	/** Replaces vanilla Fire Protection's burning-time attribute multiplier. */
	public static AttributeModifier applyConfiguredFireProtectionModifier(
		int level,
		String slot,
		AttributeModifier vanillaModifier
	) {
		if (vanillaModifier == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(FIRE_PROTECTION_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double configuredPercent = BooksConfigAPIManager.resolveAdjustment(
			definition.baseDuration,
			definition.levelDuration,
			Math.max(1, level)
		);
		AttributeModifier configuredModifier = new AttributeModifier(
			vanillaModifier.id(),
			-configuredPercent / 100.0D,
			vanillaModifier.operation()
		);
		return configuredModifier;
	}

	/** Replaces vanilla Soul Speed movement speed with the configured percentage. */
	public static AttributeModifier applyConfiguredSoulSpeedModifier(
		int level,
		String slot,
		AttributeModifier vanillaModifier
	) {
		boolean customEnchantmentsEnabled = EnchantConfigAPIManager.areCustomEnchantmentsEnabled();
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(SOUL_SPEED_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		if (vanillaModifier == null || !customEnchantmentsEnabled || !definitionEnabled) {
			return vanillaModifier;
		}

		int resolvedLevel = Math.max(1, level);
		double configuredSpeedIncrease = BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			resolvedLevel
		);
		// Movement Speed's vanilla base is 0.1, so 40 percentage points become a 0.04 additive attribute amount.
		double configuredAmount = configuredSpeedIncrease / 1000.0D;
		return new AttributeModifier(vanillaModifier.id(), configuredAmount, vanillaModifier.operation());
	}

	/** Replaces vanilla Sweeping Edge's sweep-damage ratio with the configured percentage. */
	public static AttributeModifier applyConfiguredSweepingEdgeModifier(
		int level,
		AttributeModifier vanillaModifier
	) {
		boolean customEnchantmentsEnabled = EnchantConfigAPIManager.areCustomEnchantmentsEnabled();
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(SWEEPING_EDGE_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		if (vanillaModifier == null || !customEnchantmentsEnabled || !definitionEnabled) {
			return vanillaModifier;
		}

		int resolvedLevel = Math.max(1, level);
		double configuredSweepingDamage = BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			resolvedLevel
		);
		double configuredAmount = configuredSweepingDamage / 100.0D;
		return new AttributeModifier(vanillaModifier.id(), configuredAmount, vanillaModifier.operation());
	}

	/** Marks the active location-change call so only Soul Speed's damage effect can be suppressed. */
	public static void beginSoulSpeedLocationChangedEffects(Enchantment enchantment) {
		if (BooksConfigAPIManager.shouldOverrideSoulSpeed(enchantment)) {
			SOUL_SPEED_LOCATION_CONTEXT.set(Boolean.TRUE);
		} else {
			SOUL_SPEED_LOCATION_CONTEXT.remove();
		}
	}

	public static void endSoulSpeedLocationChangedEffects() {
		SOUL_SPEED_LOCATION_CONTEXT.remove();
	}

	/** Returns whether the current location-change effect is Soul Speed's vanilla durability effect. */
	public static boolean shouldCancelSoulSpeedDurabilityChange(
		int level,
		EnchantedItemInUse itemSource,
		Entity entity
	) {
		boolean cancel = Boolean.TRUE.equals(SOUL_SPEED_LOCATION_CONTEXT.get());
		return cancel;
	}

	/** Replaces vanilla Unbreaking with a reversible max/current durability increase. */
	public static void reconcileConfiguredUnbreaking(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;

		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag existingData = customData == null ? null : customData.copyTag();
		boolean tracked = existingData != null
			&& existingData.contains(UNBREAKING_BASE_MAX_DAMAGE_KEY)
			&& existingData.contains(UNBREAKING_BASE_DAMAGE_KEY)
			&& existingData.contains(UNBREAKING_APPLIED_MAX_DAMAGE_KEY)
			&& existingData.contains(UNBREAKING_APPLIED_DAMAGE_KEY)
			&& existingData.contains(UNBREAKING_APPLIED_LEVEL_KEY);

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.UNBREAKING_ID);
		int level = resolveLevel(stack, BooksConfigAPIManager.UNBREAKING_ID);
		boolean configured = EnchantConfigAPIManager.areCustomEnchantmentsEnabled()
			&& definition != null
			&& definition.enabled
			&& level > 0
			&& stack.isDamageableItem()
			&& BooksConfigAPIManager.isCompatible(definition, stack);

		if (!configured) {
			if (tracked) restoreUnbreakingBaseDurability(stack, existingData);
			return;
		}

		int currentMaxDamage = stack.getMaxDamage();
		int currentDamage = Math.max(0, stack.getDamageValue());
		int baseMaxDamage = tracked
			? readStoredInt(existingData, UNBREAKING_BASE_MAX_DAMAGE_KEY, currentMaxDamage)
			: currentMaxDamage;
		int baseDamage = tracked
			? readStoredInt(existingData, UNBREAKING_BASE_DAMAGE_KEY, currentDamage)
			: currentDamage;
		int appliedMaxDamage = tracked
			? readStoredInt(existingData, UNBREAKING_APPLIED_MAX_DAMAGE_KEY, currentMaxDamage)
			: currentMaxDamage;
		int appliedDamage = tracked
			? readStoredInt(existingData, UNBREAKING_APPLIED_DAMAGE_KEY, currentDamage)
			: currentDamage;
		int appliedLevel = tracked
			? readStoredInt(existingData, UNBREAKING_APPLIED_LEVEL_KEY, level)
			: level;

		if (tracked && appliedLevel == level && currentMaxDamage == appliedMaxDamage) return;
		if (tracked) {
			baseDamage = clampDamage(baseDamage + currentDamage - appliedDamage, baseMaxDamage);
		}

		double increasePercent = BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		);
		double multiplier = 1.0D + increasePercent / 100.0D;
		int targetMaxDamage = scaleDurability(baseMaxDamage, multiplier);
		int targetDamage = Math.min(
			targetMaxDamage,
			scaleDurability(baseDamage, multiplier)
		);

		stack.set(DataComponents.MAX_DAMAGE, targetMaxDamage);
		stack.setDamageValue(targetDamage);
		CompoundTag updatedData = existingData == null ? new CompoundTag() : existingData;
		updatedData.putInt(UNBREAKING_BASE_MAX_DAMAGE_KEY, baseMaxDamage);
		updatedData.putInt(UNBREAKING_BASE_DAMAGE_KEY, baseDamage);
		updatedData.putInt(UNBREAKING_APPLIED_MAX_DAMAGE_KEY, targetMaxDamage);
		updatedData.putInt(UNBREAKING_APPLIED_DAMAGE_KEY, targetDamage);
		updatedData.putInt(UNBREAKING_APPLIED_LEVEL_KEY, level);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(updatedData));
	}

	private static void restoreUnbreakingBaseDurability(ItemStack stack, CompoundTag data) {
		int currentDamage = Math.max(0, stack.getDamageValue());
		int baseMaxDamage = Math.max(1, readStoredInt(data, UNBREAKING_BASE_MAX_DAMAGE_KEY, stack.getMaxDamage()));
		int baseDamage = readStoredInt(data, UNBREAKING_BASE_DAMAGE_KEY, currentDamage);
		int appliedDamage = readStoredInt(data, UNBREAKING_APPLIED_DAMAGE_KEY, currentDamage);
		int restoredDamage = clampDamage(baseDamage + currentDamage - appliedDamage, baseMaxDamage);
		stack.set(DataComponents.MAX_DAMAGE, baseMaxDamage);
		stack.setDamageValue(restoredDamage);
		data.remove(UNBREAKING_BASE_MAX_DAMAGE_KEY);
		data.remove(UNBREAKING_BASE_DAMAGE_KEY);
		data.remove(UNBREAKING_APPLIED_MAX_DAMAGE_KEY);
		data.remove(UNBREAKING_APPLIED_DAMAGE_KEY);
		data.remove(UNBREAKING_APPLIED_LEVEL_KEY);
		if (data.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		}
	}

	private static int readStoredInt(CompoundTag data, String key, int fallback) {
		return data == null ? fallback : data.getInt(key).orElse(fallback);
	}

	private static int scaleDurability(int value, double multiplier) {
		if (value <= 0 || !Double.isFinite(multiplier)) return Math.max(0, value);
		long scaled = Math.round(value * multiplier);
		return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
	}

	private static int clampDamage(int damage, int maxDamage) {
		return Math.max(0, Math.min(Math.max(0, maxDamage), damage));
	}

	/** Replaces configured Thorns' vanilla chance and effect as one per-piece post-attack roll. */
	public static boolean applyConfiguredThornsPostAttack(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		Entity attacker,
		Enchantment enchantment,
		DamageSource incomingSource
	) {
		if (!BooksConfigAPIManager.shouldOverrideThorns(enchantment)
			|| serverLevel == null || itemSource == null || itemSource.owner() == null || attacker == null) {
			return false;
		}

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(THORNS_ID);
		int resolvedLevel = Math.max(1, level);
		double configuredChance = Math.min(100.0D, BooksConfigAPIManager.resolveAdjustment(
			definition.baseThornsChanceAdjustment,
			definition.levelThornsChanceAdjustment,
			resolvedLevel
		));
		float roll = serverLevel.getRandom().nextFloat() * 100.0F;
		boolean triggered = roll < configuredChance;

		THORNS_POST_ATTACK_CONTEXT.set(Boolean.TRUE);
		try {
			if (triggered) {
				Holder<DamageType> thornsDamageType = serverLevel.registryAccess()
					.lookupOrThrow(Registries.DAMAGE_TYPE)
					.getOrThrow(DamageTypes.THORNS);
				applyConfiguredThornsDamage(
					serverLevel,
					resolvedLevel,
					itemSource,
					attacker,
					thornsDamageType
				);
			}
		} finally {
			THORNS_POST_ATTACK_CONTEXT.remove();
		}

		return true;
	}

	/** Captures the final damage returned by the custom armor-damage calculation for Thorns. */
	public static void capturePostArmorDamage(float amount) {
		if (!Boolean.TRUE.equals(THORNS_POST_ATTACK_CONTEXT.get())) {
			INCOMING_DAMAGE_CONTEXT.set(amount);
		}
	}

	/** Replaces vanilla Thorns' fixed random damage with the configured percentage of the incoming hit. */
	public static boolean applyConfiguredThornsDamage(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		Entity attacker,
		Holder<DamageType> damageType
	) {
		boolean customEnchantmentsEnabled = EnchantConfigAPIManager.areCustomEnchantmentsEnabled();
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(THORNS_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		Float postArmorDamageValue = currentPostArmorDamage();
		boolean thornsContext = Boolean.TRUE.equals(THORNS_POST_ATTACK_CONTEXT.get());
		if (!customEnchantmentsEnabled || !definitionEnabled || !thornsContext || postArmorDamageValue == null
			|| serverLevel == null || attacker == null || itemSource == null || itemSource.owner() == null) {
			return false;
		}

		int resolvedLevel = Math.max(1, level);
		double configuredDamagePercentage = BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			resolvedLevel
		);
		float postArmorDamage = Math.max(0.0F, postArmorDamageValue);
		float configuredDamage = postArmorDamage * (float) (configuredDamagePercentage / 100.0D);
		DamageSource source = new DamageSource(damageType, itemSource.owner());
		attacker.hurtServer(serverLevel, source, configuredDamage);
		return true;
	}

	private static Float currentPostArmorDamage() {
		return INCOMING_DAMAGE_CONTEXT.get();
	}

	/** Replaces vanilla Impaling's aquatic-only bonus with the configured water-or-rain bonus. */
	public static boolean applyConfiguredImpalingDamage(
		Enchantment enchantment,
		int level,
		ItemStack weapon,
		Entity target,
		MutableFloat damage
	) {
		if (!BooksConfigAPIManager.shouldOverrideImpaling(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.IMPALING_ID);
		boolean definitionEnabled = definition != null && definition.enabled;
		boolean compatible = definitionEnabled && BooksConfigAPIManager.isCompatible(definition, weapon);
		int resolvedLevel = Math.max(1, level);
		double configuredBonus = definitionEnabled
			? BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, resolvedLevel)
			: 0.0D;
		boolean inWaterOrRain = target != null && target.isInWaterOrRain();
		boolean aquatic = target != null && target.typeHolder().is(EntityTypeTags.AQUATIC);
		boolean vanillaWouldApply = aquatic;
		boolean applied = compatible && inWaterOrRain && damage != null;
		boolean vanillaBonusCancelled = compatible && (vanillaWouldApply || inWaterOrRain);
		if (applied) damage.add((float) configuredBonus);
		return vanillaBonusCancelled;
	}

	/** Replaces vanilla Sharpness damage with the configured additive damage. */
	public static boolean applyConfiguredSharpnessDamage(
		Enchantment enchantment,
		int level,
		ItemStack weapon,
		Entity target,
		DamageSource source,
		MutableFloat damage
	) {
		if (!BooksConfigAPIManager.isSharpness(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.SHARPNESS_ID);
		boolean definitionEnabled = BooksConfigAPIManager.shouldOverrideSharpness(enchantment);
		boolean compatible = definitionEnabled && BooksConfigAPIManager.isCompatible(definition, weapon);
		int resolvedLevel = Math.max(1, level);
		double configuredBonus = definitionEnabled
			? BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, resolvedLevel)
			: 0.0D;

		if (!compatible || level <= 0) return false;
		if (damage != null) damage.add((float) configuredBonus);
		return true;
	}

	/** Cancels vanilla Smite's undead-only bonus damage for compatible configured weapons. */
	public static boolean cancelConfiguredSmiteDamage(
		Enchantment enchantment,
		int level,
		ItemStack weapon,
		Entity target,
		DamageSource source,
		MutableFloat damage
	) {
		if (!BooksConfigAPIManager.isSmite(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.SMITE_ID);
		boolean definitionEnabled = BooksConfigAPIManager.shouldOverrideSmite(enchantment);
		boolean compatible = definitionEnabled && BooksConfigAPIManager.isCompatible(definition, weapon);
		return compatible && level > 0;
	}

	/** Applies configured Smite vulnerability and vanilla Glowing after a successful weapon hit. */
	public static boolean applyConfiguredSmiteEffects(
		Enchantment enchantment,
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		Entity target,
		DamageSource source
	) {
		if (!BooksConfigAPIManager.isSmite(enchantment) || !(target instanceof LivingEntity livingTarget)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.SMITE_ID);
		ItemStack weapon = itemSource == null ? ItemStack.EMPTY : itemSource.itemStack();
		boolean definitionEnabled = BooksConfigAPIManager.shouldOverrideSmite(enchantment);
		boolean compatible = definitionEnabled && BooksConfigAPIManager.isCompatible(definition, weapon);
		int resolvedLevel = Math.max(1, level);
		double vulnerabilityPercent = definitionEnabled
			? BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, resolvedLevel)
			: 0.0D;
		double configuredGlowSeconds = definitionEnabled
			? BooksConfigAPIManager.resolveAdjustment(definition.baseDuration, definition.levelDuration, resolvedLevel)
			: 0.0D;
		int glowDurationTicks = Math.max(0, (int) Math.round(configuredGlowSeconds * 20.0D));
		if (!compatible || level <= 0) return false;
		if (glowDurationTicks > 0) {
			livingTarget.addEffect(
				new MobEffectInstance(MobEffects.GLOWING, glowDurationTicks, 0, false, false, true),
				source == null ? null : source.getEntity()
			);
		}
		float vulnerability = (float) Math.max(0.0D, vulnerabilityPercent / 100.0D);
		if (vulnerability > 0.0F && glowDurationTicks > 0) {
			SMITE_VULNERABILITY_BY_ENTITY.put(livingTarget.getUUID(), new SmiteVulnerabilityState(vulnerability));
		} else {
			SMITE_VULNERABILITY_BY_ENTITY.remove(livingTarget.getUUID());
		}
		return true;
	}

	/** Applies Smite vulnerability only while the target still has vanilla Glowing. */
	public static float applyConfiguredSmiteVulnerability(LivingEntity entity, DamageSource source, float amount) {
		if (entity == null || amount <= 0.0F) return amount;

		SmiteVulnerabilityState state = SMITE_VULNERABILITY_BY_ENTITY.get(entity.getUUID());
		if (state == null) return amount;
		if (!entity.hasEffect(MobEffects.GLOWING)) {
			SMITE_VULNERABILITY_BY_ENTITY.remove(entity.getUUID(), state);
			return amount;
		}

		return amount * (1.0F + state.vulnerability);
	}

	/** Replaces vanilla Infinity's unconditional arrow exemption with a configured chance. */
	public static ItemStack applyConfiguredInfinityAmmo(
		ItemStack weapon,
		ItemStack ammo,
		LivingEntity shooter,
		boolean multishotProjectile
	) {
		if (weapon == null || weapon.isEmpty() || ammo == null || ammo.isEmpty()
			|| shooter == null || multishotProjectile
			|| !(ammo.getItem() instanceof ArrowItem)
			|| !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return null;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(INFINITY_ID);
		int level = resolveLevel(weapon, INFINITY_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		boolean compatible = definitionEnabled && level > 0 && BooksConfigAPIManager.isCompatible(definition, weapon);
		if (!compatible) return null;

		double configuredChance = Math.min(100.0D, BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		));
		float roll = shooter.getRandom().nextFloat() * 100.0F;
		boolean preserveAmmo = roll < configuredChance;
		if (!preserveAmmo) return null;

		ItemStack projectile = ammo.copyWithCount(1);
		projectile.set(DataComponents.INTANGIBLE_PROJECTILE, net.minecraft.util.Unit.INSTANCE);
		return projectile;
	}

	/** Returns whether the configured Infinity effect should suppress vanilla ammo-use processing. */
	public static boolean shouldCancelVanillaInfinity(Enchantment enchantment, int level) {
		if (!BooksConfigAPIManager.shouldOverrideInfinity(enchantment)
			|| level <= 0) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(INFINITY_ID);
		return definition != null && definition.enabled;
	}

	/** Suppresses the final vanilla Mending XP-repair amount at the helper boundary. */
	public static boolean applyConfiguredMendingXpRepairOverride(
		ServerLevel serverLevel,
		ItemStack stack,
		int vanillaRepairAmount
	) {
		if (stack == null || stack.isEmpty() || vanillaRepairAmount <= 0
			|| !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.MENDING_ID);
		int level = resolveLevel(stack, BooksConfigAPIManager.MENDING_ID);
		boolean configuredMending = definition != null && definition.enabled && level > 0
			&& BooksConfigAPIManager.isCompatible(definition, stack);
		if (!configuredMending) return false;

		return true;
	}

	/** Replaces Mending's XP repair with a configured chance to prevent durability loss. */
	public static boolean applyConfiguredMendingDurabilityProtection(
		Enchantment enchantment,
		int level,
		ItemStack stack,
		MutableFloat durabilityChange,
		ServerLevel serverLevel
	) {
		if (level <= 0 || stack == null || stack.isEmpty() || durabilityChange == null
			|| !BooksConfigAPIManager.shouldOverrideMending(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.MENDING_ID);
		if (definition == null || !definition.enabled || !BooksConfigAPIManager.isCompatible(definition, stack)) return false;

		double configuredChance = Math.min(100.0D, BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		));
		float incomingChange = durabilityChange.floatValue();
		RandomSource random = serverLevel == null ? RandomSource.create() : serverLevel.getRandom();
		float roll = random.nextFloat() * 100.0F;
		boolean preventDurabilityLoss = incomingChange > 0.0F && roll < configuredChance;
		if (preventDurabilityLoss) durabilityChange.setValue(0.0F);

		return true;
	}

	/** Replaces vanilla Knockback's contribution with configured horizontal strength. */
	public static boolean applyConfiguredKnockback(
		Enchantment enchantment,
		int level,
		ItemStack weapon,
		Entity target,
		DamageSource source,
		MutableFloat knockback
	) {
		if (!BooksConfigAPIManager.shouldOverrideKnockback(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.KNOCKBACK_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		boolean compatible = definitionEnabled && BooksConfigAPIManager.isCompatible(definition, weapon);
		if (!compatible || knockback == null) return false;

		int resolvedLevel = Math.max(1, level);
		double configuredKnockback = BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			resolvedLevel
		);
		knockback.add((float) configuredKnockback);
		return true;
	}

	/** Replaces vanilla Punch's arrow-only contribution with the configured knockback amount. */
	public static boolean applyConfiguredPunch(
		Enchantment enchantment,
		int level,
		ItemStack weapon,
		Entity target,
		DamageSource source,
		MutableFloat knockback
	) {
		if (!BooksConfigAPIManager.shouldOverridePunch(enchantment)
			|| source == null
			|| source.getDirectEntity() == null
			|| !source.getDirectEntity().typeHolder().is(EntityTypeTags.ARROWS)) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.PUNCH_ID);
		boolean compatible = definition != null && definition.enabled
			&& BooksConfigAPIManager.isCompatible(definition, weapon);
		if (!compatible || knockback == null) return false;

		int resolvedLevel = Math.max(1, level);
		double configuredKnockback = BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			resolvedLevel
		);
		knockback.add((float) configuredKnockback);
		return true;
	}

	/** Returns the configured Knockback amount that contributes to attack strength. */
	public static double resolveConfiguredKnockbackVerticalContribution(DamageSource source) {
		if (source == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return 0.0D;

		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)
			|| source.getDirectEntity() != attacker) return 0.0D;

		ItemStack weapon = attacker.getWeaponItem();
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.KNOCKBACK_ID);
		int level = resolveLevel(weapon, BooksConfigAPIManager.KNOCKBACK_ID);
		if (definition == null || !definition.enabled || level <= 0
			|| !BooksConfigAPIManager.isCompatible(definition, weapon)) return 0.0D;

		return BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		) / 2.0D;
	}

	/** Captures configured Luck of the Sea while vanilla is selecting a fishing loot table entry. */
	public static void beginConfiguredLuckOfTheSea(LootParams params) {
		LUCK_OF_THE_SEA_CONTEXT.remove();
		if (params == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return;

		ItemInstance toolInstance = params.contextMap().getOptional(LootContextParams.TOOL);
		ItemStack fishingRod = toolInstance instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BooksConfigAPIManager.LUCK_OF_THE_SEA_ID);
		int level = resolveLevel(fishingRod, BooksConfigAPIManager.LUCK_OF_THE_SEA_ID);
		if (definition == null || !definition.enabled || level <= 0
			|| !BooksConfigAPIManager.isCompatible(definition, fishingRod)) return;

		LUCK_OF_THE_SEA_CONTEXT.set(new LuckOfTheSeaContext(
			level,
			BooksConfigAPIManager.resolveAdjustment(
				definition.baseTreasureChanceAdjustment,
				definition.levelTreasureChanceAdjustment,
				level
			),
			BooksConfigAPIManager.resolveAdjustment(
				definition.baseJunkChanceAdjustment,
				definition.levelJunkChanceAdjustment,
				level
			),
			BooksConfigAPIManager.resolveAdjustment(
				definition.baseFishChanceAdjustment,
				definition.levelFishChanceAdjustment,
				level
			)
		));
	}

	/** Clears the temporary fishing context after vanilla loot selection completes. */
	public static void endConfiguredLuckOfTheSea() {
		LUCK_OF_THE_SEA_CONTEXT.remove();
	}

	/** Replaces only the Luck of the Sea portion of a fishing entry's vanilla quality calculation. */
	public static int resolveConfiguredLuckOfTheSeaWeight(
		LootPoolSingletonContainer entryOwner,
		float vanillaLuck,
		int vanillaWeight
	) {
		LuckOfTheSeaContext context = LUCK_OF_THE_SEA_CONTEXT.get();
		if (context == null || !(entryOwner instanceof NestedLootTable nestedLootTable)) return vanillaWeight;

		ResourceKey<LootTable> tableKey = ((NestedLootTableAccessor) (Object) nestedLootTable).madokuCraft$getContents()
			.left()
			.orElse(null);
		if (tableKey == null) return vanillaWeight;

		double configuredAdjustment;
		if (BuiltInLootTables.FISHING_TREASURE.equals(tableKey)) {
			configuredAdjustment = context.treasureAdjustment;
		} else if (BuiltInLootTables.FISHING_JUNK.equals(tableKey)) {
			configuredAdjustment = -context.junkAdjustment;
		} else if (BuiltInLootTables.FISHING_FISH.equals(tableKey)) {
			configuredAdjustment = -context.fishAdjustment;
		} else {
			return vanillaWeight;
		}

		int baseWeight = ((LootPoolSingletonContainerAccessor) (Object) entryOwner).madokuCraft$getWeight();
		int quality = ((LootPoolSingletonContainerAccessor) (Object) entryOwner).madokuCraft$getQuality();
		float playerLuck = vanillaLuck - context.enchantmentLevel;
		int adjustedWeight = Math.max(0, Mth.floor((float) (baseWeight + quality * playerLuck + configuredAdjustment)));
		return adjustedWeight;
	}

	private record LuckOfTheSeaContext(
		int enchantmentLevel,
		double treasureAdjustment,
		double junkAdjustment,
		double fishAdjustment
	) { }

	/** Removes only configured enchantments that are incompatible with an anvil target. */
	public static void removeIncompatibleConfiguredEnchantments(ItemStack target, ItemStack result) {
		if (target == null || target.isEmpty() || result == null || result.isEmpty()
			|| target.is(Items.ENCHANTED_BOOK) || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return;

		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
			EnchantmentHelper.getEnchantmentsForCrafting(result)
		);
		boolean removed = enchantments.keySet().stream().anyMatch(enchantment -> {
			EnchantmentDefinition definition = BooksConfigAPIManager.definitionForHolder(enchantment);
			return definition != null && definition.enabled && !BooksConfigAPIManager.isCompatible(definition, target);
		});
		if (!removed) return;

		enchantments.removeIf(enchantment -> {
			EnchantmentDefinition definition = BooksConfigAPIManager.definitionForHolder(enchantment);
			return definition != null && definition.enabled && !BooksConfigAPIManager.isCompatible(definition, target);
		});
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
	}

	/** Resolves Breach's armor and armor-toughness effectiveness multiplier for an incoming hit. */
	public static double resolveBreachArmorEffectiveness(LivingEntity target, DamageSource source) {
		if (target == null || source == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return 1.0D;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BREACH_ID);
		if (definition == null || !definition.enabled) return 1.0D;

		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)) return 1.0D;

		Entity directEntity = source.getDirectEntity();
		ItemStack weapon;
		if (directEntity == attacker) {
			weapon = attacker.getMainHandItem();
		} else if (directEntity instanceof AbstractArrow projectile) {
			weapon = projectile.getWeaponItem();
		} else {
			return 1.0D;
		}

		int level = resolveLevel(weapon, BREACH_ID);
		if (level <= 0 || !BooksConfigAPIManager.isCompatible(definition, weapon)) return 1.0D;

		double penetration = BooksConfigAPIManager.resolveAdjustment(
			definition.baseArmorPenetration,
			definition.levelArmorPenetration,
			level
		);
		double effectiveness = Math.max(0.0D, Math.min(1.0D, 1.0D - penetration / 100.0D));
		return effectiveness;
	}

	/** Applies configured Bane of Arthropods slowness after a successful weapon hit. */
	public static void applyOnHit(LivingEntity target, DamageSource source) {
		if (target == null || source == null || !target.isAlive() || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return;

		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)) return;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BANE_OF_ARTHROPODS_ID);
		if (definition == null || !definition.enabled) return;

		Entity directEntity = source.getDirectEntity();
		ItemStack weapon;
		if (directEntity == attacker) {
			weapon = attacker.getMainHandItem();
		} else if (directEntity instanceof AbstractArrow projectile) {
			weapon = projectile.getWeaponItem();
		} else {
			return;
		}
		applyFireAspect(target, weapon);
		int level = resolveLevel(weapon, BANE_OF_ARTHROPODS_ID);
		if (level <= 0 || !BooksConfigAPIManager.isCompatible(definition, weapon)) return;

		int amplifier = Math.max(0, (int) Math.round(BooksConfigAPIManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		)) - 1);
		int durationTicks = Math.max(1, (int) Math.round(BooksConfigAPIManager.resolveAdjustment(
			definition.baseDuration,
			definition.levelDuration,
			level
		) * 20.0D));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, amplifier), attacker);
	}

	/** Replaces vanilla Fire Aspect's burn duration with the configured duration. */
	private static void applyFireAspect(LivingEntity target, ItemStack weapon) {
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(FIRE_ASPECT_ID);
		if (definition == null || !definition.enabled) return;

		int level = resolveLevel(weapon, FIRE_ASPECT_ID);
		if (level <= 0) return;

		boolean compatible = BooksConfigAPIManager.isCompatible(definition, weapon);
		double configuredSeconds = BooksConfigAPIManager.resolveAdjustment(
			definition.baseDuration,
			definition.levelDuration,
			level
		);
		int configuredTicks = Math.max(1, (int) Math.round(configuredSeconds * 20.0D));
		boolean fireImmune = target.fireImmune();
		boolean applied = compatible && !fireImmune;
		if (applied) target.setRemainingFireTicks(configuredTicks);
	}

	/** Replaces vanilla Flame's arrow-hit ignition duration with the configured duration. */
	public static boolean applyConfiguredFlame(Entity target, ItemStack weapon, float vanillaSeconds) {
		if (target == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(FLAME_ID);
		int level = resolveLevel(weapon, FLAME_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		boolean compatible = definitionEnabled && BooksConfigAPIManager.isCompatible(definition, weapon);
		double configuredSeconds = definitionEnabled
			? BooksConfigAPIManager.resolveAdjustment(definition.baseDuration, definition.levelDuration, Math.max(1, level))
			: 0.0D;
		boolean fireImmune = target.fireImmune();
		boolean applied = definitionEnabled && level > 0 && compatible && !fireImmune;
		if (applied) {
			int configuredTicks = Math.max(1, (int) Math.round(configuredSeconds * 20.0D));
			target.setRemainingFireTicks(configuredTicks);
		}
		return applied;
	}

	/** Replaces vanilla Fortune's variable bonus with one configured chance to double the stack. */
	public static boolean applyConfiguredFortune(
		Holder<Enchantment> enchantment,
		ItemStack stack,
		LootContext lootContext
	) {
		if (enchantment == null || stack == null || stack.isEmpty()
			|| !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;

		boolean fortune = enchantment.unwrapKey()
			.map(key -> FORTUNE_ID.equals(key.identifier().toString()))
			.orElse(false);
		if (!fortune) return false;

		ItemInstance toolInstance = lootContext == null
			? null
			: lootContext.getOptionalParameter(LootContextParams.TOOL);
		ItemStack tool = toolInstance instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
		RandomSource random = lootContext == null ? null : lootContext.getRandom();
		return applyConfiguredFortuneRoll(tool, stack, random);
	}

	/** Applies the configured Fortune roll to drops generated by Madoku Farming. */
	public static boolean applyConfiguredFortune(ItemStack tool, ItemStack stack, RandomSource random) {
		return applyConfiguredFortuneRoll(tool, stack, random);
	}

	private static boolean applyConfiguredFortuneRoll(
		ItemStack tool,
		ItemStack stack,
		RandomSource random
	) {
		if (stack == null || stack.isEmpty() || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(FORTUNE_ID);
		if (definition == null || !definition.enabled) return false;

		ItemStack resolvedTool = tool == null ? ItemStack.EMPTY : tool;
		int level = resolveLevel(resolvedTool, FORTUNE_ID);
		boolean compatible = level > 0 && BooksConfigAPIManager.isCompatible(definition, resolvedTool);
		int countBefore = stack.getCount();
		double chance = level > 0
			? BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, level)
			: 0.0D;
		chance = Math.min(100.0D, chance);
		float roll = -1.0F;
		boolean doubled = false;
		if (compatible && chance > 0.0D) {
			roll = (random == null ? RandomSource.create() : random).nextFloat() * 100.0F;
			doubled = roll < chance;
			if (doubled) stack.setCount(countBefore * 2);
		}

		return true;
	}

	/** Replaces vanilla Looting's variable bonus with one configured chance to double each drop stack. */
	public static boolean applyConfiguredLooting(
		Holder<Enchantment> enchantment,
		ItemStack stack,
		LootContext lootContext
	) {
		if (enchantment == null || stack == null || stack.isEmpty()
			|| !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;

		boolean looting = enchantment.unwrapKey()
			.map(key -> LOOTING_ID.equals(key.identifier().toString()))
			.orElse(false);
		if (!looting) return false;

		ItemInstance toolInstance = lootContext == null
			? null
			: lootContext.getOptionalParameter(LootContextParams.TOOL);
		ItemStack tool = toolInstance instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
		RandomSource random = lootContext == null ? null : lootContext.getRandom();
		return applyConfiguredLootingRoll(tool, stack, random);
	}

	/** Applies configured Looting to managed mob drops before Madoku Luck scales their quantities. */
	public static boolean applyConfiguredLooting(
		ItemStack tool,
		ItemStack stack,
		RandomSource random
	) {
		return applyConfiguredLootingRoll(tool, stack, random);
	}

	private static boolean applyConfiguredLootingRoll(
		ItemStack tool,
		ItemStack stack,
		RandomSource random
	) {
		if (stack == null || stack.isEmpty() || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigAPIManager.definition(LOOTING_ID);
		if (definition == null || !definition.enabled) return false;

		ItemStack resolvedTool = tool == null ? ItemStack.EMPTY : tool;
		int level = resolveLevel(resolvedTool, LOOTING_ID);
		boolean compatible = level > 0 && BooksConfigAPIManager.isCompatible(definition, resolvedTool);
		int countBefore = stack.getCount();
		double chance = level > 0
			? BooksConfigAPIManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, level)
			: 0.0D;
		chance = Math.min(100.0D, chance);
		float roll = -1.0F;
		boolean doubled = false;
		if (compatible && chance > 0.0D) {
			roll = (random == null ? RandomSource.create() : random).nextFloat() * 100.0F;
			doubled = roll < chance;
			if (doubled) stack.setCount(countBefore * 2);
		}

		return true;
	}

	/** Resolves configured direct damage reduction while bypassing vanilla's EPF calculation. */
	public static float resolveDamageProtection(
		net.minecraft.server.level.ServerLevel serverLevel,
		LivingEntity entity,
		DamageSource source
	) {
		if (serverLevel == null || entity == null || source == null) {
			CONFIGURED_DAMAGE_REDUCTION_PERCENT.set(0.0F);
			VANILLA_DAMAGE_PROTECTION.set(0.0F);
			return 1.0F;
		}
		if (!EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) {
			CONFIGURED_DAMAGE_REDUCTION_PERCENT.remove();
			VANILLA_DAMAGE_PROTECTION.remove();
			float vanillaProtection = EnchantmentHelper.getDamageProtection(serverLevel, entity, source);
			return vanillaProtection;
		}

		MutableFloat damageReductionPercent = new MutableFloat(0.0F);
		MutableFloat vanillaDamageProtection = new MutableFloat(0.0F);
		boolean projectileDamage = source.is(DamageTypeTags.IS_PROJECTILE);
		EnchantmentDefinition projectileProtectionDefinition = BooksConfigAPIManager.definition(PROJECTILE_PROTECTION_ID);
		EnchantmentDefinition protectionDefinition = BooksConfigAPIManager.definition(PROTECTION_ID);
		EnchantmentHelper.runIterationOnEquipment(entity, (holder, level, enchantedItem) -> {
			String enchantmentId = holder.unwrapKey()
				.map(key -> key.identifier().toString())
				.orElse("unknown");
			boolean projectileProtection = PROJECTILE_PROTECTION_ID.equals(enchantmentId);
			boolean compatibleProjectileProtection = projectileProtectionDefinition != null
				&& BooksConfigAPIManager.isCompatible(projectileProtectionDefinition, enchantedItem.itemStack());
			boolean fireDamage = source.is(DamageTypeTags.IS_FIRE);
			boolean bypassesInvulnerability = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
			boolean protection = PROTECTION_ID.equals(enchantmentId);
			boolean compatibleProtection = protectionDefinition != null
				&& BooksConfigAPIManager.isCompatible(protectionDefinition, enchantedItem.itemStack());
			boolean replaceProtection = protection
				&& !bypassesInvulnerability
				&& protectionDefinition != null && protectionDefinition.enabled;
			if (replaceProtection && compatibleProtection) {
				double configuredPercent = BooksConfigAPIManager.resolveAdjustment(
					protectionDefinition.baseAdjustment,
					protectionDefinition.levelAdjustment,
						level
				);
				damageReductionPercent.add((float) configuredPercent);
				return;
			}
			boolean fireProtection = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(FIRE_PROTECTION_ID))
				.orElse(false);
			EnchantmentDefinition fireProtectionDefinition = BooksConfigAPIManager.definition(FIRE_PROTECTION_ID);
			boolean replaceFireProtection = fireProtection && fireDamage
				&& !bypassesInvulnerability
				&& fireProtectionDefinition != null && fireProtectionDefinition.enabled;
			if (replaceFireProtection) {
				boolean compatible = BooksConfigAPIManager.isCompatible(fireProtectionDefinition, enchantedItem.itemStack());
				if (compatible) {
					double configuredProtection = BooksConfigAPIManager.resolveAdjustment(
						fireProtectionDefinition.baseAdjustment,
						fireProtectionDefinition.levelAdjustment,
						level
					);
					damageReductionPercent.add((float) configuredProtection);
					return;
				}
			}

			if (projectileProtection && projectileDamage
				&& projectileProtectionDefinition != null && projectileProtectionDefinition.enabled) {
				boolean compatible = compatibleProjectileProtection;
				if (compatible) {
					damageReductionPercent.add((float) BooksConfigAPIManager.resolveAdjustment(
						projectileProtectionDefinition.baseAdjustment,
						projectileProtectionDefinition.levelAdjustment,
						level
					));
				}
				if (compatible) return;
			}

			boolean featherFalling = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(FEATHER_FALLING_ID))
				.orElse(false);
			EnchantmentDefinition featherFallingDefinition = BooksConfigAPIManager.definition(FEATHER_FALLING_ID);
			boolean fallDamage = source.is(DamageTypeTags.IS_FALL);
			boolean compatible = featherFallingDefinition != null
				&& BooksConfigAPIManager.isCompatible(featherFallingDefinition, enchantedItem.itemStack());
			boolean replaceFeatherFalling = featherFalling && fallDamage
				&& !bypassesInvulnerability
				&& featherFallingDefinition != null && featherFallingDefinition.enabled;
			if (replaceFeatherFalling) {
				if (compatible) {
					double configuredPercent = BooksConfigAPIManager.resolveAdjustment(
						featherFallingDefinition.baseAdjustment,
						featherFallingDefinition.levelAdjustment,
						level
					);
					damageReductionPercent.add((float) configuredPercent);
					return;
				}
			}

			boolean blastProtection = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(BLAST_PROTECTION_ID))
				.orElse(false);
			EnchantmentDefinition definition = BooksConfigAPIManager.definition(BLAST_PROTECTION_ID);
			if (blastProtection && source.is(DamageTypeTags.IS_EXPLOSION)
				&& definition != null && definition.enabled) {
				if (BooksConfigAPIManager.isCompatible(definition, enchantedItem.itemStack())) {
					damageReductionPercent.add((float) (BooksConfigAPIManager.resolveAdjustment(
						definition.baseAdjustment,
						definition.levelAdjustment,
						level
					)));
					return;
				}
			}
			// Vanilla EPF remains active for every enchantment that does not take the configured
			// direct-reduction path above. This includes generic Protection and all fallback cases.
			holder.value().modifyDamageProtection(
				serverLevel,
				level,
				enchantedItem.itemStack(),
				entity,
				source,
				vanillaDamageProtection
			);
		});
		float totalReductionPercent = Mth.clamp(damageReductionPercent.floatValue(), 0.0F, 100.0F);
		CONFIGURED_DAMAGE_REDUCTION_PERCENT.set(totalReductionPercent);
		VANILLA_DAMAGE_PROTECTION.set(Math.max(0.0F, vanillaDamageProtection.floatValue()));
		return 1.0F;
	}

	/** Applies configured percentage reduction and ignores the vanilla EPF argument. */
	public static float applyConfiguredDamageReduction(float damage, float ignoredVanillaProtection) {
		Float configuredPercent = CONFIGURED_DAMAGE_REDUCTION_PERCENT.get();
		Float vanillaProtection = VANILLA_DAMAGE_PROTECTION.get();
		CONFIGURED_DAMAGE_REDUCTION_PERCENT.remove();
		VANILLA_DAMAGE_PROTECTION.remove();
		if (configuredPercent == null && vanillaProtection == null) {
			return CombatRules.getDamageAfterMagicAbsorb(damage, ignoredVanillaProtection);
		}

		float configuredReductionPercent = configuredPercent == null ? 0.0F : configuredPercent;
		float vanillaProtectionValue = vanillaProtection == null ? 0.0F : vanillaProtection;
		float damageAfterVanillaFallback = CombatRules.getDamageAfterMagicAbsorb(damage, vanillaProtectionValue);
		float reduction = Mth.clamp(configuredReductionPercent / 100.0F, 0.0F, 1.0F);
		return damageAfterVanillaFallback * (1.0F - reduction);
	}

	/** Adds configured Blast Protection resistance to vanilla explosion knockback resistance. */
	public static double resolveExplosionKnockbackResistance(LivingEntity entity, double currentResistance) {
		if (entity == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return currentResistance;
		EnchantmentDefinition definition = BooksConfigAPIManager.definition(BLAST_PROTECTION_ID);
		if (definition == null || !definition.enabled) return currentResistance;

		double adjustment = 0.0D;
		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
		}) {
			ItemStack armor = entity.getItemBySlot(slot);
			if (!BooksConfigAPIManager.isCompatible(definition, armor)) continue;
			int level = resolveLevel(armor, BLAST_PROTECTION_ID);
			if (level > 0) {
				adjustment += BooksConfigAPIManager.resolveAdjustment(
					definition.baseKnockbackResistance,
					definition.levelKnockbackResistance,
					level
				) / 100.0D;
			}
		}
		double resolvedResistance = Math.min(1.0D, Math.max(0.0D, currentResistance + adjustment));
		return resolvedResistance;
	}

	private static List<Holder<Enchantment>> selectEnchantments(
		RandomSource random,
		Registry<Enchantment> registry,
		int requestedCount
	) {
		if (registry == null || requestedCount <= 0) return List.of();
		RandomSource source = random == null ? RandomSource.create() : random;
		List<ConfiguredHolder> candidates = new ArrayList<>();
		for (Map.Entry<String, EnchantmentDefinition> entry : BooksConfigAPIManager.definitions().entrySet()) {
			EnchantmentDefinition definition = entry.getValue();
			if (definition == null || !definition.enabled || definition.weight <= 0) continue;

			Holder<Enchantment> holder = resolveHolder(registry, entry.getKey());
			if (holder != null) candidates.add(new ConfiguredHolder(holder, definition));
		}

		List<Holder<Enchantment>> selected = new ArrayList<>();
		int maximum = Math.min(3, requestedCount);
		while (selected.size() < maximum && !candidates.isEmpty()) {
			int totalWeight = 0;
			for (ConfiguredHolder candidate : candidates) totalWeight += Math.max(1, candidate.definition.weight);
			if (totalWeight <= 0) break;

			int pick = source.nextInt(totalWeight);
			int selectedIndex = 0;
			for (int index = 0; index < candidates.size(); index++) {
				pick -= Math.max(1, candidates.get(index).definition.weight);
				if (pick < 0) {
					selectedIndex = index;
					break;
				}
			}

			ConfiguredHolder candidate = candidates.remove(selectedIndex);
			if (hasConfiguredConflict(selected, candidate)) continue;
			selected.add(candidate.holder);
		}
		return selected;
	}

	private static boolean hasConfiguredConflict(List<Holder<Enchantment>> selected, ConfiguredHolder candidate) {
		if (!candidate.definition.conflictingEnchantment || selected.isEmpty()) return false;
		for (Holder<Enchantment> existing : selected) {
			EnchantmentDefinition existingDefinition = BooksConfigAPIManager.definitionForHolder(existing);
			if (existingDefinition == null || !existingDefinition.conflictingEnchantment) return false;
		}
		return !EnchantmentHelper.isEnchantmentCompatible(selected, candidate.holder);
	}

	private static Holder<Enchantment> resolveHolder(Registry<Enchantment> registry, String enchantmentId) {
		for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
			if (entry.getKey().identifier().toString().equals(enchantmentId)) {
				return registry.wrapAsHolder(entry.getValue());
			}
		}
		return null;
	}

	private record SmiteVulnerabilityState(float vulnerability) { }

	private static int resolveLevel(ItemStack stack, String enchantmentId) {
		if (stack == null || stack.isEmpty()) return 0;
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			if (holder.unwrapKey().map(key -> key.identifier().toString().equals(enchantmentId)).orElse(false)) {
				return enchantments.getLevel(holder);
			}
		}
		return 0;
	}

	private record ConfiguredHolder(Holder<Enchantment> holder, EnchantmentDefinition definition) { }
}

