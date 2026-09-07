package madoku.craft.java.core.smithing;

import net.minecraft.world.item.ItemStack;

/** Optional Items and Pets behavior used by Core's smithing transaction. */
public interface SmithingFeatureAdapter {
	default boolean isItemsEnabled() {
		return false;
	}

	default boolean isRarityCategoryItem(ItemStack stack) {
		return false;
	}

	default boolean areItemLevelsEnabled() {
		return false;
	}

	default void setItemLevel(ItemStack stack, int level) {
	}

	default Integer getItemLevel(ItemStack stack) {
		return null;
	}

	default int getItemStartingLevel() {
		return 1;
	}

	default int getItemMaximumLevel() {
		return 1;
	}

	default boolean isPetsEnabled() {
		return false;
	}

	default boolean isPetItem(ItemStack stack) {
		return false;
	}

	default int petLevel(ItemStack stack) {
		return 1;
	}

	default int maxPetLevel() {
		return 1;
	}

	default void setPetLevel(ItemStack stack, int level) {
	}
}
