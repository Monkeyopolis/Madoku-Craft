package madoku.craft.java.core.enchant;

import net.minecraft.world.item.ItemStack;

/** Optional item integration used by Core's enchantment transactions. */
public interface EnchantItemAdapter {
	void updateDurabilityLore(ItemStack stack);
}
