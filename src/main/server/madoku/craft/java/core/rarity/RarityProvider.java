package madoku.craft.java.core.rarity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Provider contract implemented by the module that owns Madoku rarity. */
public interface RarityProvider {
	default void initialize() { }
	default void reset() { }
	default boolean isEnabled() { return false; }
	default String createClientSyncSnapshot() { return "{}"; }
	default void applyClientSyncSnapshot(String snapshot) { }
	default void resetClientSyncState() { }
	default void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) { }
	default void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) { }
	default void applyConfiguredRarity(ItemStack stack, RarityAPIManager.Tier rarity) { }
	default void preserveRarityOnRename(ItemStack source, ItemStack target) { }
	default RarityAPIManager.Tier detectAppliedRarity(ItemStack stack) { return null; }
	default RarityAPIManager.Tier fromString(String value) { return null; }
	default boolean isRarityItem(ItemStack stack) { return false; }
	default double resolveWeight(RarityAPIManager.Tier tier, double luckStat, boolean useMadokuLuck) { return 0.0D; }
	default double resolveWeight(RarityAPIManager.Tier tier, ServerPlayer player, boolean useMadokuLuck) { return 0.0D; }
	default double resolveWeightMultiplier(RarityAPIManager.Tier tier, double luckStat, boolean useMadokuLuck) { return 0.0D; }
}
