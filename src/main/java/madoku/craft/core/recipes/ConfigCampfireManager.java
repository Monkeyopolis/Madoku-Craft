package madoku.craft.core.recipes;

public final class ConfigCampfireManager {
	private static volatile boolean initialized;
	private ConfigCampfireManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
}
