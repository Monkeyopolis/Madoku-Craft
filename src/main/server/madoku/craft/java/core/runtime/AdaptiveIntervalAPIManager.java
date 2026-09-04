package madoku.craft.java.core.runtime;

import net.minecraft.server.MinecraftServer;

/** Public contract for load-aware intervals used by direct server-tick systems. */
public final class AdaptiveIntervalAPIManager {
	private static final AdaptiveIntervalProvider UNAVAILABLE_PROVIDER = new AdaptiveIntervalProvider() { };
	private static volatile AdaptiveIntervalProvider provider = UNAVAILABLE_PROVIDER;

	private AdaptiveIntervalAPIManager() { }

	public static void registerProvider(AdaptiveIntervalProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Adaptive interval provider must not be null.");
		provider = candidate;
	}

	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static long resolve(String systemId, MinecraftServer server, long minimumIntervalTicks, long maximumIntervalTicks) {
		return provider.resolve(systemId, server, minimumIntervalTicks, maximumIntervalTicks);
	}
	public static void clearSystem(String systemId) { provider.clearSystem(systemId); }
	public static void clearAll() { provider.clearAll(); }
}
