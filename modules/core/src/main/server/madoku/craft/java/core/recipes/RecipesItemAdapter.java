package madoku.craft.java.core.recipes;

import net.minecraft.world.item.ItemStack;

/** Optional item integration used by Core's recipe result handling. */
public interface RecipesItemAdapter {
	default boolean isRarityCategoryItem(ItemStack stack) {
		return false;
	}

	default void applyConfiguredItemLevel(ItemStack stack, int level) {
	}

	default void applyConfiguredItemLevel(ItemStack stack, int level, boolean updateLore) {
	}
}
