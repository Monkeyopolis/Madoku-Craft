package madoku.craft.core.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Public contract for the rarity subsystem. */
public final class RarityAPIManager {
	private RarityAPIManager() {
	}

	public enum Tier {
		COMMON("common", ChatFormatting.WHITE, "*"),
		RARE("rare", ChatFormatting.DARK_GREEN, "**"),
		EPIC("epic", ChatFormatting.BLUE, "***"),
		LEGENDARY("legendary", ChatFormatting.LIGHT_PURPLE, "****"),
		MYTHIC("mythic", ChatFormatting.GOLD, "*****");

		private final String id;
		private final ChatFormatting color;
		private final String inventoryIndicator;

		Tier(String id, ChatFormatting color, String inventoryIndicator) {
			this.id = id;
			this.color = color;
			this.inventoryIndicator = inventoryIndicator;
		}

		public String id() { return id; }
		public ChatFormatting color() { return color; }
		public String inventoryIndicator() { return inventoryIndicator; }
	}

	private static RarityTierAPIManager.Tier toInternal(Tier tier) {
		return tier == null ? null : RarityTierAPIManager.Tier.valueOf(tier.name());
	}

	private static Tier fromInternal(RarityTierAPIManager.Tier tier) {
		return tier == null ? null : Tier.valueOf(tier.name());
	}

	public static void initialize() { MadokuRarityManager.initialize(); }
	public static void reset() { MadokuRarityManager.reset(); }
	public static boolean isEnabled() { return MadokuRarityManager.isEnabled(); }
	public static String createClientSyncSnapshot() { return MadokuRarityManager.createClientSyncSnapshot(); }
	public static void applyClientSyncSnapshot(String snapshot) { MadokuRarityManager.applyClientSyncSnapshot(snapshot); }
	public static void resetClientSyncState() { MadokuRarityManager.resetClientSyncState(); }
	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) { MadokuRarityManager.applyGeneratedRarity(stack, randomSource); }
	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) { MadokuRarityManager.applyGeneratedRarity(stack, randomSource, luckPlayer); }
	public static void applyConfiguredRarity(ItemStack stack, Tier rarity) { MadokuRarityManager.applyConfiguredRarity(stack, toInternal(rarity)); }
	public static void preserveRarityOnRename(ItemStack source, ItemStack target) { MadokuRarityManager.preserveRarityOnRename(source, target); }
	public static Tier detectAppliedRarity(ItemStack stack) { return fromInternal(MadokuRarityManager.detectAppliedRarity(stack)); }
	public static Tier fromString(String value) { return fromInternal(RarityTierAPIManager.fromString(value)); }
	public static boolean isRarityItem(ItemStack stack) { return MadokuRarityManager.isRarityItem(stack); }
	public static double resolveWeight(Tier tier, double luckStat, boolean useMadokuLuck) { return MadokuRarityManager.resolveWeight(toInternal(tier), luckStat, useMadokuLuck); }
	public static double resolveWeight(Tier tier, ServerPlayer player, boolean useMadokuLuck) { return MadokuRarityManager.resolveWeight(toInternal(tier), player, useMadokuLuck); }
	public static double resolveWeightMultiplier(Tier tier, double luckStat, boolean useMadokuLuck) { return MadokuRarityManager.resolveWeightMultiplier(toInternal(tier), luckStat, useMadokuLuck); }
}
