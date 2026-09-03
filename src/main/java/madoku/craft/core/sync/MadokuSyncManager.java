package madoku.craft.core.sync;

import net.minecraft.server.MinecraftServer;

/** Orchestrates synchronization services through their public API contract. */
public final class MadokuSyncManager {
	private MadokuSyncManager() {
	}

	public static void initialize() { SyncAPIManager.initialize(); }
	public static void reset() { SyncAPIManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { SyncAPIManager.onServerStarted(server); }
	public static void onServerStopping(MinecraftServer server) { SyncAPIManager.onServerStopping(server); }
	public static boolean shouldRunWorldSync(MinecraftServer server) { return SyncAPIManager.shouldRunWorldSync(server); }
}
