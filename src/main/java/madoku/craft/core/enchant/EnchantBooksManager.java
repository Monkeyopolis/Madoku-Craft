package madoku.craft.core.enchant;

import madoku.craft.core.enchant.BooksConfigManager.EnchantmentDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.apache.commons.lang3.mutable.MutableFloat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static madoku.craft.core.enchant.BooksConfigManager.AQUA_AFFINITY_ID;
import static madoku.craft.core.enchant.BooksConfigManager.BANE_OF_ARTHROPODS_ID;
import static madoku.craft.core.enchant.BooksConfigManager.BLAST_PROTECTION_ID;
import static madoku.craft.core.enchant.BooksConfigManager.BREACH_ID;
import static madoku.craft.core.enchant.BooksConfigManager.DEPTH_STRIDER_ID;
import static madoku.craft.core.enchant.BooksConfigManager.EFFICIENCY_ID;
import static madoku.craft.core.enchant.BooksConfigManager.FEATHER_FALLING_ID;
import static madoku.craft.core.enchant.BooksConfigManager.FORTUNE_ID;
import static madoku.craft.core.enchant.BooksConfigManager.LOOTING_ID;
import static madoku.craft.core.enchant.BooksConfigManager.FIRE_ASPECT_ID;
import static madoku.craft.core.enchant.BooksConfigManager.FIRE_PROTECTION_ID;
import static madoku.craft.core.enchant.BooksConfigManager.FLAME_ID;
import static madoku.craft.core.enchant.BooksConfigManager.INFINITY_ID;

/** Runtime group that owns configured enchantment books and enchantment effects. */
public final class EnchantBooksManager {
	private EnchantBooksManager() {
	}

	/** Creates a new book using only enabled, configured enchantment definitions. */
	static ItemStack createEnchantedBook(Player player, int enchantmentCount) {
		if (player == null || enchantmentCount <= 0 || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return ItemStack.EMPTY;

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
		if (input == null || input.isEmpty() || levels <= 0 || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			EnchantmentDefinition definition = BooksConfigManager.definitionForHolder(enchantment);
			int current = enchantments.getLevel(enchantment);
			if (definition != null && definition.enabled && current < definition.maximumLevel
				&& current + levels <= definition.maximumLevel) {
				return true;
			}
		}
		return false;
	}

	/** Upgrades configured enchantments by the allocated number of levels. */
	static ItemStack upgradeEnchantedBook(ItemStack input, int levels) {
		if (input == null || input.isEmpty() || levels <= 0 || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return ItemStack.EMPTY;

		ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(existing);
		boolean changed = false;
		for (Holder<Enchantment> enchantment : existing.keySet()) {
			EnchantmentDefinition definition = BooksConfigManager.definitionForHolder(enchantment);
			if (definition == null || !definition.enabled) continue;

			int current = existing.getLevel(enchantment);
			int target = Math.min(definition.maximumLevel, current + levels);
			if (target > current) {
				upgraded.set(enchantment, target);
				changed = true;
			}
		}
		if (!changed) return ItemStack.EMPTY;

		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		EnchantmentHelper.setEnchantments(result, upgraded.toImmutable());
		return result;
	}

	/** Replaces vanilla Aqua Affinity's attribute amount with the configured percentage. */
	public static AttributeModifier applyConfiguredAquaAffinityModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigManager.definition(AQUA_AFFINITY_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = BooksConfigManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
		return new AttributeModifier(
			vanillaModifier.id(),
			Math.max(0.0D, adjustment / 100.0D),
			vanillaModifier.operation()
		);
	}

	/** Replaces vanilla Depth Strider's water-movement efficiency with its configured percentage. */
	public static AttributeModifier applyConfiguredDepthStriderModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigManager.definition(DEPTH_STRIDER_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = BooksConfigManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
		AttributeModifier configuredModifier = new AttributeModifier(
			vanillaModifier.id(),
			Math.max(0.0D, adjustment / 100.0D),
			vanillaModifier.operation()
		);
		return configuredModifier;
	}

	/** Replaces vanilla Efficiency's mining-efficiency attribute amount with its configured value. */
	public static AttributeModifier applyConfiguredEfficiencyModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigManager.definition(EFFICIENCY_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = BooksConfigManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
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
		if (vanillaModifier == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = BooksConfigManager.definition(FIRE_PROTECTION_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double configuredPercent = BooksConfigManager.resolveAdjustment(
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

	/** Replaces vanilla Impaling's aquatic-only bonus with the configured water-or-rain bonus. */
	public static boolean applyConfiguredImpalingDamage(
		Enchantment enchantment,
		int level,
		ItemStack weapon,
		Entity target,
		MutableFloat damage
	) {
		if (!BooksConfigManager.shouldOverrideImpaling(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigManager.definition(BooksConfigManager.IMPALING_ID);
		boolean definitionEnabled = definition != null && definition.enabled;
		boolean compatible = definitionEnabled && BooksConfigManager.isCompatible(definition, weapon);
		int resolvedLevel = Math.max(1, level);
		double configuredBonus = definitionEnabled
			? BooksConfigManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, resolvedLevel)
			: 0.0D;
		boolean inWaterOrRain = target != null && target.isInWaterOrRain();
		boolean aquatic = target != null && target.typeHolder().is(EntityTypeTags.AQUATIC);
		boolean vanillaWouldApply = aquatic;
		boolean applied = compatible && inWaterOrRain && damage != null;
		boolean vanillaBonusCancelled = compatible && (vanillaWouldApply || inWaterOrRain);
		if (applied) damage.add((float) configuredBonus);
		return vanillaBonusCancelled;
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
			|| !EnchantConfigManager.areCustomEnchantmentsEnabled()) return null;

		EnchantmentDefinition definition = BooksConfigManager.definition(INFINITY_ID);
		int level = resolveLevel(weapon, INFINITY_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		boolean compatible = definitionEnabled && level > 0 && BooksConfigManager.isCompatible(definition, weapon);
		if (!compatible) return null;

		double configuredChance = Math.min(100.0D, BooksConfigManager.resolveAdjustment(
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
		if (!BooksConfigManager.shouldOverrideInfinity(enchantment)
			|| level <= 0) return false;

		EnchantmentDefinition definition = BooksConfigManager.definition(INFINITY_ID);
		return definition != null && definition.enabled;
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
		if (!BooksConfigManager.shouldOverrideKnockback(enchantment)) return false;

		EnchantmentDefinition definition = BooksConfigManager.definition(BooksConfigManager.KNOCKBACK_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		boolean compatible = definitionEnabled && BooksConfigManager.isCompatible(definition, weapon);
		if (!compatible || knockback == null) return false;

		int resolvedLevel = Math.max(1, level);
		double configuredKnockback = BooksConfigManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			resolvedLevel
		);
		knockback.add((float) configuredKnockback);
		return true;
	}

	/** Returns the configured Knockback amount that contributes to attack strength. */
	public static double resolveConfiguredKnockbackVerticalContribution(DamageSource source) {
		if (source == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return 0.0D;

		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)
			|| source.getDirectEntity() != attacker) return 0.0D;

		ItemStack weapon = attacker.getWeaponItem();
		EnchantmentDefinition definition = BooksConfigManager.definition(BooksConfigManager.KNOCKBACK_ID);
		int level = resolveLevel(weapon, BooksConfigManager.KNOCKBACK_ID);
		if (definition == null || !definition.enabled || level <= 0
			|| !BooksConfigManager.isCompatible(definition, weapon)) return 0.0D;

		return BooksConfigManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		) / 2.0D;
	}

	/** Removes only configured enchantments that are incompatible with an anvil target. */
	public static void removeIncompatibleConfiguredEnchantments(ItemStack target, ItemStack result) {
		if (target == null || target.isEmpty() || result == null || result.isEmpty()
			|| target.is(Items.ENCHANTED_BOOK) || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return;

		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
			EnchantmentHelper.getEnchantmentsForCrafting(result)
		);
		boolean removed = enchantments.keySet().stream().anyMatch(enchantment -> {
			EnchantmentDefinition definition = BooksConfigManager.definitionForHolder(enchantment);
			return definition != null && definition.enabled && !BooksConfigManager.isCompatible(definition, target);
		});
		if (!removed) return;

		enchantments.removeIf(enchantment -> {
			EnchantmentDefinition definition = BooksConfigManager.definitionForHolder(enchantment);
			return definition != null && definition.enabled && !BooksConfigManager.isCompatible(definition, target);
		});
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
	}

	/** Resolves Breach's armor and armor-toughness effectiveness multiplier for an incoming hit. */
	public static double resolveBreachArmorEffectiveness(LivingEntity target, DamageSource source) {
		if (target == null || source == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return 1.0D;

		EnchantmentDefinition definition = BooksConfigManager.definition(BREACH_ID);
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
		if (level <= 0 || !BooksConfigManager.isCompatible(definition, weapon)) return 1.0D;

		double penetration = BooksConfigManager.resolveAdjustment(
			definition.baseArmorPenetration,
			definition.levelArmorPenetration,
			level
		);
		double effectiveness = Math.max(0.0D, Math.min(1.0D, 1.0D - penetration / 100.0D));
		return effectiveness;
	}

	/** Applies configured Bane of Arthropods slowness after a successful weapon hit. */
	public static void applyOnHit(LivingEntity target, DamageSource source) {
		if (target == null || source == null || !target.isAlive() || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return;

		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)) return;

		EnchantmentDefinition definition = BooksConfigManager.definition(BANE_OF_ARTHROPODS_ID);
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
		if (level <= 0 || !BooksConfigManager.isCompatible(definition, weapon)) return;

		int amplifier = Math.max(0, (int) Math.round(BooksConfigManager.resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		)) - 1);
		int durationTicks = Math.max(1, (int) Math.round(BooksConfigManager.resolveAdjustment(
			definition.baseDuration,
			definition.levelDuration,
			level
		) * 20.0D));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, amplifier), attacker);
	}

	/** Replaces vanilla Fire Aspect's burn duration with the configured duration. */
	private static void applyFireAspect(LivingEntity target, ItemStack weapon) {
		EnchantmentDefinition definition = BooksConfigManager.definition(FIRE_ASPECT_ID);
		if (definition == null || !definition.enabled) return;

		int level = resolveLevel(weapon, FIRE_ASPECT_ID);
		if (level <= 0) return;

		boolean compatible = BooksConfigManager.isCompatible(definition, weapon);
		double configuredSeconds = BooksConfigManager.resolveAdjustment(
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
		if (target == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigManager.definition(FLAME_ID);
		int level = resolveLevel(weapon, FLAME_ID);
		boolean definitionPresent = definition != null;
		boolean definitionEnabled = definitionPresent && definition.enabled;
		boolean compatible = definitionEnabled && BooksConfigManager.isCompatible(definition, weapon);
		double configuredSeconds = definitionEnabled
			? BooksConfigManager.resolveAdjustment(definition.baseDuration, definition.levelDuration, Math.max(1, level))
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
			|| !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;

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
		if (stack == null || stack.isEmpty() || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigManager.definition(FORTUNE_ID);
		if (definition == null || !definition.enabled) return false;

		ItemStack resolvedTool = tool == null ? ItemStack.EMPTY : tool;
		int level = resolveLevel(resolvedTool, FORTUNE_ID);
		boolean compatible = level > 0 && BooksConfigManager.isCompatible(definition, resolvedTool);
		int countBefore = stack.getCount();
		double chance = level > 0
			? BooksConfigManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, level)
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
			|| !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;

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
		if (stack == null || stack.isEmpty() || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;

		EnchantmentDefinition definition = BooksConfigManager.definition(LOOTING_ID);
		if (definition == null || !definition.enabled) return false;

		ItemStack resolvedTool = tool == null ? ItemStack.EMPTY : tool;
		int level = resolveLevel(resolvedTool, LOOTING_ID);
		boolean compatible = level > 0 && BooksConfigManager.isCompatible(definition, resolvedTool);
		int countBefore = stack.getCount();
		double chance = level > 0
			? BooksConfigManager.resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, level)
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

	/** Resolves vanilla protection points while replacing configured protection enchantment contributions. */
	public static float resolveDamageProtection(
		net.minecraft.server.level.ServerLevel serverLevel,
		LivingEntity entity,
		DamageSource source
	) {
		if (serverLevel == null || entity == null || source == null) return 0.0F;

		MutableFloat protection = new MutableFloat(0.0F);
		EnchantmentHelper.runIterationOnEquipment(entity, (holder, level, enchantedItem) -> {
			boolean fireDamage = source.is(DamageTypeTags.IS_FIRE);
			boolean bypassesInvulnerability = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
			boolean fireProtection = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(FIRE_PROTECTION_ID))
				.orElse(false);
			EnchantmentDefinition fireProtectionDefinition = BooksConfigManager.definition(FIRE_PROTECTION_ID);
			boolean replaceFireProtection = fireProtection && fireDamage
				&& !bypassesInvulnerability
				&& fireProtectionDefinition != null && fireProtectionDefinition.enabled;
			if (replaceFireProtection) {
				boolean compatible = BooksConfigManager.isCompatible(fireProtectionDefinition, enchantedItem.itemStack());
				if (compatible) {
					double configuredProtection = BooksConfigManager.resolveAdjustment(
						fireProtectionDefinition.baseAdjustment,
						fireProtectionDefinition.levelAdjustment,
						level
					);
					protection.add((float) configuredProtection);
				}
				return;
			}

			boolean featherFalling = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(FEATHER_FALLING_ID))
				.orElse(false);
			EnchantmentDefinition featherFallingDefinition = BooksConfigManager.definition(FEATHER_FALLING_ID);
			boolean fallDamage = source.is(DamageTypeTags.IS_FALL);
			boolean compatible = featherFallingDefinition != null
				&& BooksConfigManager.isCompatible(featherFallingDefinition, enchantedItem.itemStack());
			boolean replaceFeatherFalling = featherFalling && fallDamage
				&& !bypassesInvulnerability
				&& featherFallingDefinition != null && featherFallingDefinition.enabled;
			if (replaceFeatherFalling) {
				if (compatible) {
					// CombatRules uses 25 protection points per 100% reduction.
						double configuredPercent = BooksConfigManager.resolveAdjustment(
						featherFallingDefinition.baseAdjustment,
						featherFallingDefinition.levelAdjustment,
						level
					);
					double protectionPoints = configuredPercent / 4.0D;
					protection.add((float) protectionPoints);
				}
				return;
			}

			boolean blastProtection = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(BLAST_PROTECTION_ID))
				.orElse(false);
			EnchantmentDefinition definition = BooksConfigManager.definition(BLAST_PROTECTION_ID);
			if (blastProtection && source.is(DamageTypeTags.IS_EXPLOSION)
				&& definition != null && definition.enabled) {
				if (BooksConfigManager.isCompatible(definition, enchantedItem.itemStack())) {
					// CombatRules uses 25 protection points per 100% reduction.
					protection.add((float) (BooksConfigManager.resolveAdjustment(
						definition.baseAdjustment,
						definition.levelAdjustment,
						level
					) / 4.0D));
				}
				return;
			}
			holder.value().modifyDamageProtection(
				serverLevel,
				level,
				enchantedItem.itemStack(),
				entity,
				source,
				protection
			);
		});
		return protection.floatValue();
	}

	/** Adds configured Blast Protection resistance to vanilla explosion knockback resistance. */
	public static double resolveExplosionKnockbackResistance(LivingEntity entity, double currentResistance) {
		if (entity == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return currentResistance;
		EnchantmentDefinition definition = BooksConfigManager.definition(BLAST_PROTECTION_ID);
		if (definition == null || !definition.enabled) return currentResistance;

		double adjustment = 0.0D;
		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
		}) {
			ItemStack armor = entity.getItemBySlot(slot);
			if (!BooksConfigManager.isCompatible(definition, armor)) continue;
			int level = resolveLevel(armor, BLAST_PROTECTION_ID);
			if (level > 0) {
				adjustment += BooksConfigManager.resolveAdjustment(
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
		for (Map.Entry<String, EnchantmentDefinition> entry : BooksConfigManager.definitions().entrySet()) {
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
			EnchantmentDefinition existingDefinition = BooksConfigManager.definitionForHolder(existing);
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
