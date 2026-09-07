package madoku.craft.java.core.rarity;

import net.minecraft.world.item.ItemStack;

/** Optional item behavior used by Core's generic rarity runtime. */
public interface RarityItemAdapter {
	default boolean isRarityCategoryItem(ItemStack stack) {
		return false;
	}

	default void applyRarityScaling(ItemStack stack, double multiplier) {
	}

	default void updateDurabilityLore(ItemStack stack) {
	}
}
