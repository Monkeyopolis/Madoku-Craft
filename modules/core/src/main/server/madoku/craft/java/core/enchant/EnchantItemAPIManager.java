package madoku.craft.java.core.enchant;

import net.minecraft.world.item.ItemStack;

/** Access point for optional item behavior required by the Core enchantment subsystem. */
public final class EnchantItemAPIManager {
	private static final EnchantItemAdapter NO_ADAPTER = stack -> { };
	private static volatile EnchantItemAdapter adapter = NO_ADAPTER;

	private EnchantItemAPIManager() {
	}

	public static void registerAdapter(EnchantItemAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Enchant item adapter must not be null.");
		}
		adapter = candidate;
	}

	public static void unregisterAdapter() {
		adapter = NO_ADAPTER;
	}

	public static void updateDurabilityLore(ItemStack stack) {
		adapter.updateDurabilityLore(stack);
	}
}
