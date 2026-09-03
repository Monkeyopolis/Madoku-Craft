package madoku.craft.core.helper;

import net.minecraft.server.MinecraftServer;

/** Orchestrates shared helper services through their public API contract. */
public final class MadokuHelperManager {
	private MadokuHelperManager() {
	}

	public static void initialize() { HelperAPIManager.initialize(); }
	public static void reset() { HelperAPIManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { HelperAPIManager.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { HelperAPIManager.onServerTick(server); }
}
