package madoku.craft.api.rarity;

import madoku.craft.items.MadokuItemsManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import madoku.craft.api.rarity.RarityTierManager.Tier;
import madoku.craft.attributes.MadokuLuckManager;

public final class RarityRuntimeManager {
	private static volatile boolean initialized;

	private RarityRuntimeManager() {
	}

	public static void initialize() {
		initialized = true;
	}

	public static void reset() {
		initialized = false;
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) {
		if (!isEnabled() || stack == null || stack.isEmpty()
			|| !MadokuItemsManager.isRarityCategoryItem(stack) || detectAppliedRarity(stack) != null) {
			return;
		}

		ServerPlayer resolvedPlayer = luckPlayer == null ? MadokuLuckManager.resolveActiveDropPlayer() : luckPlayer;
		RandomSource random = randomSource == null ? RandomSource.create() : randomSource;
		rollAndApplySingle(random, stack, resolvedPlayer, RarityConfigManager.useMadokuLuck());
	}

	public static void applyConfiguredRarity(ItemStack stack, Tier rarity) {
		if (!isEnabled() || stack == null || stack.isEmpty() || rarity == null
			|| detectAppliedRarity(stack) != null) {
			return;
		}
		applyRarity(stack, rarity);
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
		applyRarity(stack, rollRandomRarity(random, luckPlayer, useMadokuLuck));
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

	public static void applyRarity(ItemStack stack, Tier rarity) {
		if (stack == null || stack.isEmpty() || rarity == null) {
			return;
		}
		if (rarity != Tier.COMMON) {
			double buffPercent = getRarityStatBuffPercent(rarity);
			if (buffPercent > 0.0D) {
				MadokuItemsManager.applyRarityScaling(stack, multiplierFromBuffPercent(buffPercent));
			}
		}
		MutableComponent coloredName = stack.getItem().getName(stack).copy()
			.withStyle(style -> style.withColor(rarity.color()).withItalic(false));
		stack.set(DataComponents.CUSTOM_NAME, coloredName);
		MadokuItemsManager.updateDurabilityLore(stack);
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
}
