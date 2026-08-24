package madoku.craft.api.rarity;

import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.item.system.MadokuItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import madoku.craft.api.rarity.RarityTierManager.Tier;

public final class RarityRuntimeManager {
	private static final String DURABILITY_PREFIX = "Durability:";
	private static final double ATTACK_DAMAGE_SCALING_FACTOR = 0.50D;
	private static final double ATTACK_SPEED_SCALING_FACTOR = 0.25D;
	private static final double MINING_SPEED_SCALING_FACTOR = 0.25D;
	private static final double ARMOR_SCALING_FACTOR = 0.50D;
	private static final double ARMOR_TOUGHNESS_SCALING_FACTOR = 0.50D;
	private static volatile boolean initialized;

	private RarityRuntimeManager() {
	}

	public static void initialize() {
		initialized = true;
	}

	public static void reset() {
		initialized = false;
	}

	public static List<ItemStack> applyCraftedRarity(ServerPlayer player, ItemStack stack) {
		if (!isEnabled() || player == null || stack == null || stack.isEmpty()
			|| !MadokuItem.isRarityCategoryItem(stack) || detectAppliedRarity(stack) != null) {
			return List.of();
		}

		int craftedAmount = Math.max(1, stack.getCount());
		if (craftedAmount == 1) {
			rollAndApplySingle(player.getRandom(), stack, player, MadokuItem.useMadokuLuck());
			return List.of();
		}

		ItemStack base = stack.copy();
		base.setCount(1);
		stack.setCount(1);
		rollAndApplySingle(player.getRandom(), stack, player, MadokuItem.useMadokuLuck());

		List<ItemStack> extras = new ArrayList<>(craftedAmount - 1);
		for (int index = 1; index < craftedAmount; index++) {
			ItemStack extra = base.copy();
			rollAndApplySingle(player.getRandom(), extra, player, MadokuItem.useMadokuLuck());
			extras.add(extra);
		}
		return extras;
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) {
		if (!isEnabled() || stack == null || stack.isEmpty()
			|| !MadokuItem.isRarityCategoryItem(stack) || detectAppliedRarity(stack) != null) {
			return;
		}

		ServerPlayer resolvedPlayer = luckPlayer == null ? MadokuLuckManager.resolveActiveDropPlayer() : luckPlayer;
		RandomSource random = randomSource == null ? RandomSource.create() : randomSource;
		rollAndApplySingle(random, stack, resolvedPlayer, MadokuItem.useMadokuLuck());
	}

	public static void applyConfiguredRarity(ItemStack stack, Tier rarity) {
		if (!isEnabled() || stack == null || stack.isEmpty() || rarity == null
			|| detectAppliedRarity(stack) != null) {
			return;
		}
		applyRarityToStack(stack, rarity);
	}

	public static void deliverCraftExtras(ServerPlayer player, List<ItemStack> extras) {
		if (player == null || extras == null || extras.isEmpty()) {
			return;
		}
		for (ItemStack extra : extras) {
			if (extra != null && !extra.isEmpty() && !player.getInventory().add(extra)) {
				player.drop(extra, false);
			}
		}
	}

	public static ItemStack createSmithingUpgradeResult(ItemStack baseStack, ItemStack vanillaResult) {
		if (!isEnabled() || baseStack == null || baseStack.isEmpty() || vanillaResult == null || vanillaResult.isEmpty()
			|| !MadokuItem.isRarityCategoryItem(baseStack) || !MadokuItem.isRarityCategoryItem(vanillaResult)) {
			return vanillaResult;
		}

		Tier sourceRarity = detectAppliedRarity(baseStack);
		if (sourceRarity == null) {
			return vanillaResult;
		}
		ItemStack rebuiltResult = vanillaResult.copy();
		applyRarityToStack(rebuiltResult, sourceRarity);
		return rebuiltResult;
	}

	public static void updateDurabilityLore(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || !MadokuItem.isRarityCategoryItem(stack)) {
			return;
		}
		int maxDurability = stack.getMaxDamage();
		if (maxDurability <= 0) {
			return;
		}

		int currentDurability = Math.max(0, maxDurability - stack.getDamageValue());
		Component durabilityLine = Component.literal(DURABILITY_PREFIX + " " + currentDurability + "/" + maxDurability)
			.withStyle(ChatFormatting.GRAY);
		ItemLore currentLore = stack.get(DataComponents.LORE);
		List<Component> updatedLines = new ArrayList<>();
		if (currentLore != null) {
			for (Component line : currentLore.lines()) {
				if (!line.getString().startsWith(DURABILITY_PREFIX)) {
					updatedLines.add(line);
				}
			}
		}
		updatedLines.add(durabilityLine);
		stack.set(DataComponents.LORE, new ItemLore(updatedLines));
	}

	public static Tier detectAppliedRarity(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		if (customName == null || customName.getStyle().getColor() == null) {
			return null;
		}

		int rgb = customName.getStyle().getColor().getValue();
		for (Tier tier : Tier.values()) {
			Integer tierColor = TextColor.fromLegacyFormat(tier.color()).getValue();
			if (tierColor != null && tierColor == rgb) {
				return tier;
			}
		}
		return null;
	}

	private static boolean isEnabled() {
		return initialized && MadokuRarityManager.isEnabled();
	}

	private static void rollAndApplySingle(RandomSource random, ItemStack stack, ServerPlayer luckPlayer, boolean useMadokuLuck) {
		applyRarityToStack(stack, rollRandomRarity(random, luckPlayer, useMadokuLuck));
	}

	private static Tier rollRandomRarity(RandomSource random, ServerPlayer luckPlayer, boolean useMadokuLuck) {
		RandomSource resolvedRandom = random == null ? RandomSource.create() : random;
		double luckStat = luckPlayer == null ? 0.0D : MadokuLuckManager.resolveLootLuckStat(luckPlayer);
		boolean luckActive = luckPlayer != null;
		double totalWeight = 0.0D;
		for (Tier tier : Tier.values()) {
			totalWeight += MadokuRarityManager.resolveWeight(tier, luckStat, luckActive && useMadokuLuck);
		}
		if (totalWeight <= 0.0D) {
			return Tier.COMMON;
		}

		double roll = resolvedRandom.nextDouble() * totalWeight;
		Tier fallback = Tier.COMMON;
		for (Tier tier : Tier.values()) {
			double weight = MadokuRarityManager.resolveWeight(tier, luckStat, luckActive && useMadokuLuck);
			if (weight <= 0.0D) {
				continue;
			}
			fallback = tier;
			if (roll < weight) {
				return tier;
			}
			roll -= weight;
		}
		return fallback;
	}

	private static void applyRarityToStack(ItemStack stack, Tier rarity) {
		if (stack == null || stack.isEmpty() || rarity == null) {
			return;
		}
		if (rarity != Tier.COMMON) {
			double buffPercent = getRarityStatBuffPercent(rarity);
			if (buffPercent > 0.0D) {
				applyStatMultiplier(stack, multiplierFromBuffPercent(buffPercent));
			}
		}
		MutableComponent coloredName = stack.getItem().getName(stack).copy()
			.withStyle(style -> style.withColor(rarity.color()).withItalic(false));
		stack.set(DataComponents.CUSTOM_NAME, coloredName);
		updateDurabilityLore(stack);
	}

	private static double getRarityStatBuffPercent(Tier rarity) {
		return switch (rarity) {
			case COMMON -> 0.0D;
			case RARE -> 25.0D;
			case EPIC -> 50.0D;
			case LEGENDARY -> 75.0D;
			case MYTHIC -> 100.0D;
		};
	}

	private static double multiplierFromBuffPercent(double buffPercent) {
		return 1.0D + Math.max(0.0D, buffPercent) / 100.0D;
	}

	private static void applyStatMultiplier(ItemStack stack, double multiplier) {
		scaleMaxDurability(stack, multiplier);
		scaleMainHandAttackAttributes(stack,
			scaledEffectMultiplier(multiplier, ATTACK_DAMAGE_SCALING_FACTOR),
			scaledEffectMultiplier(multiplier, ATTACK_SPEED_SCALING_FACTOR));
		scaleArmorAttributes(stack,
			scaledEffectMultiplier(multiplier, ARMOR_SCALING_FACTOR),
			scaledEffectMultiplier(multiplier, ARMOR_TOUGHNESS_SCALING_FACTOR));
		scaleMiningSpeed(stack, scaledEffectMultiplier(multiplier, MINING_SPEED_SCALING_FACTOR));
	}

	private static double scaledEffectMultiplier(double multiplier, double factor) {
		return 1.0D + (multiplier - 1.0D) * factor;
	}

	private static void scaleMaxDurability(ItemStack stack, double multiplier) {
		Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
		if (maxDamage != null && maxDamage > 0) {
			stack.set(DataComponents.MAX_DAMAGE, Math.max(1, (int) Math.round(maxDamage * multiplier)));
		}
	}

	private static void scaleMainHandAttackAttributes(ItemStack stack, double damageMultiplier, double speedMultiplier) {
		ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (current == null) {
			return;
		}
		boolean changed = false;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
			AttributeModifier modifier = entry.modifier();
			AttributeModifier updated = modifier;
			if (isMainHandAddValue(entry, modifier) && modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)
				&& isAttribute(entry, Attributes.ATTACK_DAMAGE)) {
				double value = Math.round((1.0D + modifier.amount()) * damageMultiplier * 2.0D) / 2.0D;
				updated = new AttributeModifier(modifier.id(), value - 1.0D, modifier.operation());
				changed = true;
			} else if (isMainHandAddValue(entry, modifier) && modifier.id().equals(Item.BASE_ATTACK_SPEED_ID)
				&& isAttribute(entry, Attributes.ATTACK_SPEED)) {
				double value = Math.round((4.0D + modifier.amount()) * speedMultiplier * 10.0D) / 10.0D;
				updated = new AttributeModifier(modifier.id(), value - 4.0D, modifier.operation());
				changed = true;
			}
			builder.add(entry.attribute(), updated, entry.slot(), entry.display());
		}
		if (changed) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		}
	}

	private static void scaleArmorAttributes(ItemStack stack, double armorMultiplier, double toughnessMultiplier) {
		ItemAttributeModifiers current = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (current == null) {
			return;
		}
		boolean changed = false;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
			AttributeModifier modifier = entry.modifier();
			AttributeModifier updated = modifier;
			if (isAddValueModifier(modifier) && isAttribute(entry, Attributes.ARMOR)) {
				updated = new AttributeModifier(modifier.id(), roundQuarter(modifier.amount() * armorMultiplier), modifier.operation());
				changed = true;
			} else if (isAddValueModifier(modifier) && isAttribute(entry, Attributes.ARMOR_TOUGHNESS)) {
				updated = new AttributeModifier(modifier.id(), roundQuarter(modifier.amount() * toughnessMultiplier), modifier.operation());
				changed = true;
			}
			builder.add(entry.attribute(), updated, entry.slot(), entry.display());
		}
		if (changed) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		}
	}

	private static void scaleMiningSpeed(ItemStack stack, double multiplier) {
		Tool current = stack.get(DataComponents.TOOL);
		if (current == null) {
			return;
		}
		boolean changed = false;
		List<Tool.Rule> rules = new ArrayList<>(current.rules().size());
		for (Tool.Rule rule : current.rules()) {
			Optional<Float> speed = rule.speed();
			if (speed.isPresent()) {
				rules.add(new Tool.Rule(rule.blocks(), Optional.of((float) roundHalf(speed.get() * multiplier)), rule.correctForDrops()));
				changed = true;
			} else {
				rules.add(rule);
			}
		}
		float defaultSpeed = (float) roundHalf(current.defaultMiningSpeed() * multiplier);
		changed |= defaultSpeed != current.defaultMiningSpeed();
		if (changed) {
			stack.set(DataComponents.TOOL, new Tool(rules, defaultSpeed, current.damagePerBlock(), current.canDestroyBlocksInCreative()));
		}
	}

	private static boolean isMainHandAddValue(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return entry.slot() == EquipmentSlotGroup.MAINHAND && isAddValueModifier(modifier);
	}

	private static boolean isAddValueModifier(AttributeModifier modifier) {
		return modifier.operation() == AttributeModifier.Operation.ADD_VALUE;
	}

	private static boolean isAttribute(ItemAttributeModifiers.Entry entry, Holder<Attribute> attribute) {
		return entry.attribute().value() == attribute.value();
	}

	private static double roundHalf(double value) {
		return Math.round(value * 2.0D) / 2.0D;
	}

	private static double roundQuarter(double value) {
		return Math.round(value * 4.0D) / 4.0D;
	}
}
