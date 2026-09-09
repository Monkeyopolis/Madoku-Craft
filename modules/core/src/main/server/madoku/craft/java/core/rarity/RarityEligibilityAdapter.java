package madoku.craft.java.core.rarity;

import net.minecraft.world.item.ItemStack;

/** Identifies stacks that belong to a module allowed to use Core rarity. */
public interface RarityEligibilityAdapter {
	default boolean isEligible(ItemStack stack) {
		return false;
	}
}
