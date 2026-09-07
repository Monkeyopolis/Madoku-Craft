package madoku.craft.java.core.runtime;

import net.minecraft.server.MinecraftServer;

/** Built-in provider backed by the direct-tick adaptive interval implementation. */
public final class MadokuAdaptiveIntervalProvider implements AdaptiveIntervalProvider {
	@Override public long resolve(String systemId, MinecraftServer server, long minimumIntervalTicks, long maximumIntervalTicks) {
		return AdaptiveIntervalManager.resolve(systemId, server, minimumIntervalTicks, maximumIntervalTicks);
	}
	@Override public void clearSystem(String systemId) { AdaptiveIntervalManager.clearSystem(systemId); }
	@Override public void clearAll() { AdaptiveIntervalManager.clearAll(); }
}
