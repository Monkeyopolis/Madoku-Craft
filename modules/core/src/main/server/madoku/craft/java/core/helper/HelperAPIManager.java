package madoku.craft.java.core.helper;

import net.minecraft.server.MinecraftServer;

/** Public contract for shared helper services. */
public final class HelperAPIManager {
	private static final HelperProvider UNAVAILABLE_PROVIDER = new HelperProvider() { };
	private static volatile HelperProvider provider = UNAVAILABLE_PROVIDER;

	private HelperAPIManager() { }

	public static void registerProvider(HelperProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Helper provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { provider.onServerTick(server); }
}
