package madoku.craft.java.core.iteminput;

import madoku.craft.java.core.rarity.RarityAPIManager;
import net.minecraft.world.item.ItemStack;

/** Optional item behavior used when Core creates stacks through item-input commands. */
public interface ItemInputFeatureAdapter {
	default boolean isPetItem(ItemStack stack) { return false; }
	default RarityAPIManager.Tier petRarity(ItemStack stack) { return null; }
	default void applyItemLevel(ItemStack stack, int level) { }
	default void applyPetLore(ItemStack stack) { }
}
