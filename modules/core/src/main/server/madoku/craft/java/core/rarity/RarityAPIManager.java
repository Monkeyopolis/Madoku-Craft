package madoku.craft.java.core.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Public contract for the rarity subsystem. */
public final class RarityAPIManager {
	private static final RarityProvider UNAVAILABLE_PROVIDER = new RarityProvider() { };
	private static volatile RarityProvider provider = UNAVAILABLE_PROVIDER;

	private RarityAPIManager() { }

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

	public static void registerProvider(RarityProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Rarity provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static String createClientSyncSnapshot() { return provider.createClientSyncSnapshot(); }
	public static void applyClientSyncSnapshot(String snapshot) { provider.applyClientSyncSnapshot(snapshot); }
	public static void resetClientSyncState() { provider.resetClientSyncState(); }
	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) { provider.applyGeneratedRarity(stack, randomSource); }
	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource, ServerPlayer luckPlayer) { provider.applyGeneratedRarity(stack, randomSource, luckPlayer); }
	public static void applyConfiguredRarity(ItemStack stack, Tier rarity) { provider.applyConfiguredRarity(stack, rarity); }
	public static void preserveRarityOnRename(ItemStack source, ItemStack target) { provider.preserveRarityOnRename(source, target); }
	public static Tier detectAppliedRarity(ItemStack stack) { return provider.detectAppliedRarity(stack); }
	public static Tier fromString(String value) { return provider.fromString(value); }
	public static boolean isRarityItem(ItemStack stack) { return provider.isRarityItem(stack); }
	public static double resolveWeight(Tier tier, double luckStat, boolean useMadokuLuck) { return provider.resolveWeight(tier, luckStat, useMadokuLuck); }
	public static double resolveWeight(Tier tier, ServerPlayer player, boolean useMadokuLuck) { return provider.resolveWeight(tier, player, useMadokuLuck); }
	public static double resolveWeightMultiplier(Tier tier, double luckStat, boolean useMadokuLuck) { return provider.resolveWeightMultiplier(tier, luckStat, useMadokuLuck); }
}
