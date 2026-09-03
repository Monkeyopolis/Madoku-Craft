package madoku.craft.java.core.sync;

import net.minecraft.server.MinecraftServer;

/** Runtime implementation and orchestrator for synchronization services. */
public final class MadokuSyncManager {
	private static volatile boolean initialized;

	private MadokuSyncManager() { }

	public static void initialize() {
		if (initialized) return;
		SyncGlobalManager.initialize();
		SyncConfigAPIManager.initialize();
		SyncWorldAPIManager.initialize();
		SyncPlayerAPIManager.initialize();
		initialized = true;
	}
	public static void initializeClient() { SyncGlobalManager.initializeClient(); }
	public static void reset() {
		SyncPlayerAPIManager.reset();
		SyncWorldAPIManager.reset();
		SyncGlobalManager.reset();
		initialized = false;
	}
	public static boolean isInitialized() { return initialized; }
	public static void onServerStarted(MinecraftServer server) {
		SyncGlobalManager.onServerStarted(server);
		SyncWorldAPIManager.onServerStarted(server);
		SyncPlayerAPIManager.onServerStarted(server);
	}
	public static void onServerStopping(MinecraftServer server) {
		SyncPlayerAPIManager.onServerStopping(server);
		SyncWorldAPIManager.onServerStopping(server);
		SyncGlobalManager.onServerStopping(server);
	}
	public static boolean shouldRunWorldSync(MinecraftServer server) { return SyncWorldAPIManager.shouldRunPeriodicSync(server); }
}
