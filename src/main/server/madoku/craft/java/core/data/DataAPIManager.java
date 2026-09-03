package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Public contract for the shared data subsystem. */
public final class DataAPIManager {
	private static final DataProvider UNAVAILABLE_PROVIDER = new DataProvider() { };
	private static volatile DataProvider provider = UNAVAILABLE_PROVIDER;

	private DataAPIManager() { }

	public static void registerProvider(DataProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Data provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static boolean isInitialized() { return provider.isInitialized(); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void onServerStopping(MinecraftServer server) { provider.onServerStopping(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
}
