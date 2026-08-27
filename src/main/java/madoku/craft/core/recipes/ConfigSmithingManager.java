package madoku.craft.core.recipes;

public final class ConfigSmithingManager {
	private static volatile boolean initialized;
	private ConfigSmithingManager() { }
	static void initialize() { initialized = true; }
	static void reset() { initialized = false; }
	public static boolean isInitialized() { return initialized; }
}
