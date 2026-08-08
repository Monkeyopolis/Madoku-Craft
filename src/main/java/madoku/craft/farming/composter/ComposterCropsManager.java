package madoku.craft.farming.composter;

import madoku.craft.item.system.MadokuItem;
import madoku.craft.item.system.MadokuItemConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Group runtime for items that can be processed by a composter. */
public final class ComposterCropsManager {
	private ComposterCropsManager() {
	}

	public static boolean isComposterItem(Item item) {
		return item != null
			&& MadokuItem.isEnabled()
			&& MadokuItem.hasCategory(item, MadokuItemConfig.CATEGORY_COMPOSTER);
	}

	public static boolean isComposterItem(ItemStack stack) {
		return stack != null && !stack.isEmpty() && isComposterItem(stack.getItem());
	}

	public static int getAdjustment(ItemStack stack) {
		if (!isComposterItem(stack)) return 0;
		return MadokuItem.getComposterAdjustment(stack);
	}
}
