package madoku.craft.api.data;

import net.minecraft.server.MinecraftServer;

/** Runtime API subsystem orchestrating managed data groups. */
public final class MadokuDataManager {
	private static volatile boolean initialized;

	private MadokuDataManager() {
	}

	public static void initialize() {
		DataSystemsManager.initialize();
		DataWorldChunkManager.initialize();
		initialized = true;
	}

	public static void reset() {
		DataWorldChunkManager.reset();
		DataSystemsManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static void loadPersistedData(MinecraftServer server) {
		DataWorldChunkManager.loadPersistedData(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		DataWorldChunkManager.onServerStarted(server);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		DataWorldChunkManager.autosavePersistedData(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		DataWorldChunkManager.onServerStopping(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		DataWorldChunkManager.savePersistedData(server);
	}
}
