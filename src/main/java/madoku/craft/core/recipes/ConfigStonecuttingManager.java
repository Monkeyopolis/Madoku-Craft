package madoku.craft.core.recipes;

public final class ConfigStonecuttingManager {
	private static volatile boolean initialized;
	private ConfigStonecuttingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
}
