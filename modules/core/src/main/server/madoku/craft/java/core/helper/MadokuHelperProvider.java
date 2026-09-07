package madoku.craft.java.core.helper;

import net.minecraft.server.MinecraftServer;

/** Built-in provider backed by Madoku shared helper services. */
public final class MadokuHelperProvider implements HelperProvider {
	@Override public void initialize() { MadokuHelperManager.initialize(); }
	@Override public void reset() { MadokuHelperManager.reset(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuHelperManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuHelperManager.onServerTick(server); }
}
