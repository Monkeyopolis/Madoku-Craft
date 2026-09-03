package madoku.craft.java.core.rarity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Built-in provider backed by the Madoku rarity implementation. */
public final class MadokuRarityProvider implements RarityProvider {
	private static RarityTierAPIManager.Tier toInternal(RarityAPIManager.Tier tier) {
		return tier == null ? null : RarityTierAPIManager.Tier.valueOf(tier.name());
	}

	private static RarityAPIManager.Tier fromInternal(RarityTierAPIManager.Tier tier) {
		return tier == null ? null : RarityAPIManager.Tier.valueOf(tier.name());
	}

	@Override public void initialize() { MadokuRarityManager.initialize(); }
	@Override public void reset() { MadokuRarityManager.reset(); }
	@Override public boolean isEnabled() { return MadokuRarityManager.isEnabled(); }
	@Override public String createClientSyncSnapshot() { return MadokuRarityManager.createClientSyncSnapshot(); }
	@Override public void applyClientSyncSnapshot(String snapshot) { MadokuRarityManager.applyClientSyncSnapshot(snapshot); }
	@Override public void resetClientSyncState() { MadokuRarityManager.resetClientSyncState(); }
	@Override public void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) { MadokuRarityManager.applyGeneratedRarity(stack, randomSource); }
	@Override public void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) { MadokuRarityManager.applyGeneratedRarity(stack, randomSource, luckPlayer); }
	@Override public void applyConfiguredRarity(ItemStack stack, RarityAPIManager.Tier rarity) { MadokuRarityManager.applyConfiguredRarity(stack, toInternal(rarity)); }
	@Override public void preserveRarityOnRename(ItemStack source, ItemStack target) { MadokuRarityManager.preserveRarityOnRename(source, target); }
	@Override public RarityAPIManager.Tier detectAppliedRarity(ItemStack stack) { return fromInternal(MadokuRarityManager.detectAppliedRarity(stack)); }
	@Override public RarityAPIManager.Tier fromString(String value) { return fromInternal(RarityTierAPIManager.fromString(value)); }
	@Override public boolean isRarityItem(ItemStack stack) { return MadokuRarityManager.isRarityItem(stack); }
	@Override public double resolveWeight(RarityAPIManager.Tier tier, double luckStat, boolean useMadokuLuck) { return MadokuRarityManager.resolveWeight(toInternal(tier), luckStat, useMadokuLuck); }
	@Override public double resolveWeight(RarityAPIManager.Tier tier, ServerPlayer player, boolean useMadokuLuck) { return MadokuRarityManager.resolveWeight(toInternal(tier), player, useMadokuLuck); }
	@Override public double resolveWeightMultiplier(RarityAPIManager.Tier tier, double luckStat, boolean useMadokuLuck) { return MadokuRarityManager.resolveWeightMultiplier(toInternal(tier), luckStat, useMadokuLuck); }
}
