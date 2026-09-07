package madoku.craft.java.core.rarity;

import net.minecraft.world.item.ItemStack;

/** Access point for optional item behavior required by Core rarity. */
public final class RarityItemAPIManager {
	private static final RarityItemAdapter NO_ADAPTER = new RarityItemAdapter() { };
	private static volatile RarityItemAdapter adapter = NO_ADAPTER;

	private RarityItemAPIManager() {
	}

	public static void registerAdapter(RarityItemAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Rarity item adapter must not be null.");
		}
		adapter = candidate;
	}

	public static void unregisterAdapter() {
		adapter = NO_ADAPTER;
	}

	public static boolean isRarityCategoryItem(ItemStack stack) {
		return adapter.isRarityCategoryItem(stack);
	}

	public static void applyRarityScaling(ItemStack stack, double multiplier) {
		adapter.applyRarityScaling(stack, multiplier);
	}

	public static void updateDurabilityLore(ItemStack stack) {
		adapter.updateDurabilityLore(stack);
	}
}
