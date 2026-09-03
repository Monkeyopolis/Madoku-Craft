package madoku.craft.java.core.season;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the seasonal subsystem through its public API contract. */
public final class MadokuSeasonManager {
	private MadokuSeasonManager() {
	}

	public static void initialize() { SeasonAPIManager.initialize(); }
	public static void reset() { SeasonAPIManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { SeasonAPIManager.onServerStarted(server); }
	public static void onServerTick(MinecraftServer server) { SeasonAPIManager.onServerTick(server); }
	public static void onServerStartTick(MinecraftServer server) { SeasonAPIManager.onServerStartTick(server); }
}
