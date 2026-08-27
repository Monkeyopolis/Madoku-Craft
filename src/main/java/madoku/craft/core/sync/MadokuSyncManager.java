package madoku.craft.core.sync;

import net.minecraft.server.MinecraftServer;

/** Public API facade for Madoku server-to-client synchronization. */
public final class MadokuSyncManager {
	private static volatile boolean initialized;

	private MadokuSyncManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		SyncGlobalManager.initialize();
		SyncWorldManager.initialize();
		SyncPlayerManager.initialize();
		initialized = true;
	}

	/** Registers the payload types used by the client environment. */
	public static void initializeClient() {
		SyncGlobalManager.initializeClient();
	}

	public static void reset() {
		SyncPlayerManager.reset();
		SyncWorldManager.reset();
		SyncGlobalManager.reset();
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static void onServerStarted(MinecraftServer server) {
		SyncGlobalManager.onServerStarted(server);
		SyncWorldManager.onServerStarted(server);
		SyncPlayerManager.onServerStarted(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		SyncPlayerManager.onServerStopping(server);
		SyncWorldManager.onServerStopping(server);
		SyncGlobalManager.onServerStopping(server);
	}

	/** Returns whether the periodic world synchronization pass should run now. */
	public static boolean shouldRunWorldSync(MinecraftServer server) {
		return SyncWorldManager.shouldRunPeriodicSync(server);
	}
}
