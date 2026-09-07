package madoku.craft.java.compat.utility;

import madoku.craft.java.items.ItemsCategoriesAPIManager;
import madoku.craft.java.utility.smelting.SmeltingAPIManager;

/** Connects optional Items fuel configuration to the Utility smelting API. */
public final class MadokuUtilityFeatureAdapters {
	private static boolean initialized;

	private MadokuUtilityFeatureAdapters() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		SmeltingAPIManager.registerFuelAdapter(ItemsCategoriesAPIManager::adjustFuelTicks);
	}
}
