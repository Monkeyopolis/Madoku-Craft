package madoku.craft.core.rarity;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.attributes.MadokuLuckManager;
import madoku.craft.core.rarity.RarityTierManager.Tier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Orchestrates the Madoku Rarity API subsystem and exposes its shared helpers. */
public final class MadokuRarityManager {
	private MadokuRarityManager() {
	}

	public static void initialize() {
		RarityConfigManager.initialize();
		RarityRuntimeManager.initialize();
	}

	public static void reset() {
		RarityRuntimeManager.reset();
		RarityConfigManager.reset();
	}

	public static boolean isEnabled() {
		return RarityConfigManager.isEnabled();
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) {
		RarityRuntimeManager.applyGeneratedRarity(stack, randomSource, null);
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) {
		RarityRuntimeManager.applyGeneratedRarity(stack, randomSource, luckPlayer);
	}

	public static void applyConfiguredRarity(ItemStack stack, Tier rarity) {
		RarityRuntimeManager.applyConfiguredRarity(stack, rarity);
	}

	/** Keeps the rarity color when vanilla replaces an item's custom name, such as in an anvil. */
	public static void preserveRarityOnRename(ItemStack source, ItemStack target) {
		RarityRuntimeManager.preserveRarityOnRename(source, target);
	}

	public static Tier detectAppliedRarity(ItemStack stack) {
		return RarityRuntimeManager.detectAppliedRarity(stack);
	}

	/** Resolves the configured rarity weight for systems that opt into Madoku Rarity and Luck. */
	public static double resolveWeight(Tier tier, double luckStat, boolean useMadokuLuck) {
		RarityConfigManager.RaritySettings rarity = RarityConfigManager.settings(tier);
		if (rarity == null || !rarity.enabled || rarity.weight <= 0) {
			return 0.0D;
		}

		double luckWeight = 0.0D;
		if (useMadokuLuck && RarityConfigManager.useMadokuLuck()
			&& MadokuAttributesManager.isEnabled() && MadokuLuckManager.isEnabled()) {
			luckWeight = Double.isFinite(luckStat)
				? Math.max(0.0D, luckStat) * Math.max(0.0D, rarity.weightAdjustment)
				: 0.0D;
		}
		return Math.max(0.0D, rarity.weight + luckWeight);
	}

	public static double resolveWeight(Tier tier, ServerPlayer player, boolean useMadokuLuck) {
		return resolveWeight(
			tier,
			player == null ? 0.0D : MadokuLuckManager.resolveLootLuckStat(player),
			useMadokuLuck && player != null
		);
	}

	public static double resolveWeightMultiplier(Tier tier, double luckStat, boolean useMadokuLuck) {
		RarityConfigManager.RaritySettings rarity = RarityConfigManager.settings(tier);
		if (rarity == null || !rarity.enabled || rarity.weight <= 0) {
			return 0.0D;
		}
		return resolveWeight(tier, luckStat, useMadokuLuck) / rarity.weight;
	}
}
