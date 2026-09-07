package madoku.craft.java.core.recipes;

import net.minecraft.world.item.ItemStack;

/** Access point for optional item behavior required by the Core recipe subsystem. */
public final class RecipesItemAPIManager {
	private static final RecipesItemAdapter NO_ADAPTER = new RecipesItemAdapter() { };
	private static volatile RecipesItemAdapter adapter = NO_ADAPTER;

	private RecipesItemAPIManager() {
	}

	public static void registerAdapter(RecipesItemAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Recipes item adapter must not be null.");
		}
		adapter = candidate;
	}

	public static void unregisterAdapter() {
		adapter = NO_ADAPTER;
	}

	public static boolean isRarityCategoryItem(ItemStack stack) {
		return adapter.isRarityCategoryItem(stack);
	}

	public static void applyConfiguredItemLevel(ItemStack stack, int level) {
		adapter.applyConfiguredItemLevel(stack, level);
	}

	public static void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) {
		adapter.applyConfiguredItemLevel(stack, level, updateLore);
	}
}
