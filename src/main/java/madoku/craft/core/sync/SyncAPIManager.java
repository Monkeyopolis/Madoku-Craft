package madoku.craft.core.sync;

import net.minecraft.server.MinecraftServer;

/** Public API facade for Madoku server-to-client synchronization. */
public final class SyncAPIManager {
	private static volatile boolean initialized;

	private SyncAPIManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		SyncGlobalManager.initialize();
		SyncConfigAPIManager.initialize();
		SyncWorldAPIManager.initialize();
		SyncPlayerAPIManager.initialize();
		initialized = true;
	}

	/** Registers the payload types used by the client environment. */
	public static void initializeClient() {
		SyncGlobalManager.initializeClient();
	}

	public static void reset() {
		SyncPlayerAPIManager.reset();
		SyncWorldAPIManager.reset();
		SyncGlobalManager.reset();
	}

	public static boolean isInitialized() {
		return initialized;
	}

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

	/** Returns whether the periodic world synchronization pass should run now. */
	public static boolean shouldRunWorldSync(MinecraftServer server) {
		return SyncWorldAPIManager.shouldRunPeriodicSync(server);
	}
}



