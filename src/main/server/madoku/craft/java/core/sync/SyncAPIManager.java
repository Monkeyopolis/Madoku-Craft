package madoku.craft.java.core.sync;

import net.minecraft.server.MinecraftServer;

/** Public contract for server-to-client synchronization. */
public final class SyncAPIManager {
	private static final SyncProvider UNAVAILABLE_PROVIDER = new SyncProvider() { };
	private static volatile SyncProvider provider = UNAVAILABLE_PROVIDER;

	private SyncAPIManager() { }

	public static void registerProvider(SyncProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Sync provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void initializeClient() { provider.initializeClient(); }
	public static void reset() { provider.reset(); }
	public static boolean isInitialized() { return provider.isInitialized(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerStopping(MinecraftServer server) { provider.onServerStopping(server); }
	public static boolean shouldRunWorldSync(MinecraftServer server) { return provider.shouldRunWorldSync(server); }
}
