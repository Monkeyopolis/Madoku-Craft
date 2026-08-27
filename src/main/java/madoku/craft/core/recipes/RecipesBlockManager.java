package madoku.craft.core.recipes;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Runtime group for recipes whose result is a block item. */
public final class RecipesBlockManager {
	private static volatile boolean initialized;
	private RecipesBlockManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
	public static boolean isBlockResult(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem;
	}
}
