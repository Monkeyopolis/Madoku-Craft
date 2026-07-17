package madoku.craft.api.recipes;

public final class ConfigCraftingManager {
	private static volatile boolean initialized;
	private ConfigCraftingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
}
