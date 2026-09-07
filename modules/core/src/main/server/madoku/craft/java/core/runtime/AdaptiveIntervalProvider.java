package madoku.craft.java.core.runtime;

import net.minecraft.server.MinecraftServer;

/** Provider contract for load-aware intervals used by direct server-tick systems. */
public interface AdaptiveIntervalProvider {
	default void initialize() { }
	default void reset() { }
	default long resolve(String systemId, MinecraftServer server, long minimumIntervalTicks, long maximumIntervalTicks) {
		return Math.max(1L, minimumIntervalTicks);
	}
	default void clearSystem(String systemId) { }
	default void clearAll() { }
}
