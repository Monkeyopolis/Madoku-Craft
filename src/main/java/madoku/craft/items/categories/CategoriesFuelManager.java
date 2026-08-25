package madoku.craft.items.categories;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared fuel-category predicates for the Items category runtime. */
public final class CategoriesFuelManager {
	private CategoriesFuelManager() { }

	public static boolean matches(Item item, java.util.Set<Item> configuredItems) {
		return item != null && configuredItems != null && configuredItems.contains(item);
	}

	public static boolean matches(ItemStack stack, java.util.Set<Item> configuredItems) {
		return stack != null && !stack.isEmpty() && matches(stack.getItem(), configuredItems);
	}
}
