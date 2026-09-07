package madoku.craft.java.core.smithing;

import net.minecraft.world.item.ItemStack;

/** Access point for optional feature behavior required by the Core smithing subsystem. */
public final class SmithingFeatureAPIManager {
	private static final SmithingFeatureAdapter NO_ADAPTER = new SmithingFeatureAdapter() { };
	private static volatile SmithingFeatureAdapter adapter = NO_ADAPTER;

	private SmithingFeatureAPIManager() {
	}

	public static void registerAdapter(SmithingFeatureAdapter candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Smithing feature adapter must not be null.");
		}
		adapter = candidate;
	}

	public static void unregisterAdapter() {
		adapter = NO_ADAPTER;
	}

	public static boolean isItemsEnabled() { return adapter.isItemsEnabled(); }
	public static boolean isRarityCategoryItem(ItemStack stack) { return adapter.isRarityCategoryItem(stack); }
	public static boolean areItemLevelsEnabled() { return adapter.areItemLevelsEnabled(); }
	public static void setItemLevel(ItemStack stack, int level) { adapter.setItemLevel(stack, level); }
	public static Integer getItemLevel(ItemStack stack) { return adapter.getItemLevel(stack); }
	public static int getItemStartingLevel() { return adapter.getItemStartingLevel(); }
	public static int getItemMaximumLevel() { return adapter.getItemMaximumLevel(); }
	public static boolean isPetsEnabled() { return adapter.isPetsEnabled(); }
	public static boolean isPetItem(ItemStack stack) { return adapter.isPetItem(stack); }
	public static int petLevel(ItemStack stack) { return adapter.petLevel(stack); }
	public static int maxPetLevel() { return adapter.maxPetLevel(); }
	public static void setPetLevel(ItemStack stack, int level) { adapter.setPetLevel(stack, level); }
}
