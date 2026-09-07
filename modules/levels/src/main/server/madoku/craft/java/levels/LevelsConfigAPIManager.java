package madoku.craft.java.levels;

/** Public contract for Madoku Levels configuration. */
public final class LevelsConfigAPIManager {
	private static final LevelsConfigProvider UNAVAILABLE_PROVIDER = new LevelsConfigProvider() { };
	private static volatile LevelsConfigProvider provider = UNAVAILABLE_PROVIDER;

	private LevelsConfigAPIManager() {
	}

	public static void registerProvider(LevelsConfigProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Levels config provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reload() { provider.reload(); }
	public static void reset() { provider.reset(); }
	public static LevelsConfigManager.Settings settings() { return provider.settings(); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static LevelsConfigManager.PlayerSettings player() { return provider.player(); }
	public static LevelsConfigManager.StatSettings stat(LevelStat stat) { return provider.stat(stat); }
}
