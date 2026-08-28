package madoku.craft.core.enchant;

import madoku.craft.core.enchant.BooksConfigManager.EnchantmentDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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

	/** Resolves vanilla protection points while replacing configured protection enchantment contributions. */
	public static float resolveDamageProtection(
		net.minecraft.server.level.ServerLevel serverLevel,
		LivingEntity entity,
		DamageSource source
	) {
		if (serverLevel == null || entity == null || source == null) return 0.0F;

		MutableFloat protection = new MutableFloat(0.0F);
		EnchantmentHelper.runIterationOnEquipment(entity, (holder, level, enchantedItem) -> {
			boolean featherFalling = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(FEATHER_FALLING_ID))
				.orElse(false);
			EnchantmentDefinition featherFallingDefinition = BooksConfigManager.definition(FEATHER_FALLING_ID);
			boolean fallDamage = source.is(DamageTypeTags.IS_FALL);
			boolean bypassesInvulnerability = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
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
					float protectionBefore = protection.floatValue();
					protection.add((float) protectionPoints);
					TemporaryEnchantDebug.featherFallingProtection(level, configuredPercent, protectionPoints);
					TemporaryEnchantDebug.featherFallingEvaluation(
						source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse(source.getMsgId()),
						enchantedItem.itemStack().getItem().toString(),
						level,
						fallDamage,
						bypassesInvulnerability,
						true,
						true,
						true,
						configuredPercent,
						protectionPoints,
						protectionBefore,
						protection.floatValue(),
						true
					);
				} else {
					TemporaryEnchantDebug.featherFallingEvaluation(
						source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse(source.getMsgId()),
						enchantedItem.itemStack().getItem().toString(),
						level,
						fallDamage,
						bypassesInvulnerability,
						featherFallingDefinition != null,
						featherFallingDefinition != null && featherFallingDefinition.enabled,
						compatible,
						0.0D,
						0.0D,
						protection.floatValue(),
						protection.floatValue(),
						false
					);
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
