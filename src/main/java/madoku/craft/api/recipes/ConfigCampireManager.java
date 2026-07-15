package madoku.craft.api.recipes;

public final class ConfigCampireManager {
	private static volatile boolean initialized;
	private ConfigCampireManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
}
