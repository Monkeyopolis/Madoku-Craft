package madoku.craft.java.core.helper;

import net.minecraft.server.MinecraftServer;

/** Runtime implementation and orchestrator for shared helper services. */
public final class MadokuHelperManager {
	private MadokuHelperManager() { }

	public static void initialize() { HelperProjectileAPIManager.initialize(); }
	public static void reset() { HelperProjectileAPIManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { HelperProjectileAPIManager.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { HelperProjectileAPIManager.tick(server); }
}
