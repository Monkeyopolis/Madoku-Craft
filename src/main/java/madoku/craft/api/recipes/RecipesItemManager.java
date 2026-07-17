package madoku.craft.api.recipes;

import net.minecraft.world.item.ItemStack;

/** Runtime group for recipes whose result is a non-block item. */
public final class RecipesItemManager {
	private static volatile boolean initialized;
	private RecipesItemManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static boolean isItemResult(ItemStack stack) {
		return stack != null && !stack.isEmpty() && !RecipesBlockManager.isBlockResult(stack);
	}
}
