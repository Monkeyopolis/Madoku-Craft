package madoku.craft.api.data;

import net.minecraft.server.MinecraftServer;

/** Runtime API subsystem orchestrating managed data groups. */
public final class MadokuDataManager {
	private static volatile boolean initialized;

	private MadokuDataManager() {
	}

	public static void initialize() {
		DataSaveCoordinatorManager.initialize();
		DataSystemsManager.initialize();
		DataWorldManager.initialize();
		DataWorldChunkManager.initialize();
		DataPlayerManager.initialize();
		initialized = true;
	}

	public static void reset() {
		DataWorldManager.reset();
		DataWorldChunkManager.reset();
		DataPlayerManager.reset();
		DataSystemsManager.reset();
		DataSaveCoordinatorManager.reset();
		initialized = false;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static void loadPersistedData(MinecraftServer server) {
		DataWorldManager.loadPersistedData(server);
		DataWorldChunkManager.loadPersistedData(server);
		DataPlayerManager.loadPersistedData(server);
	}

	public static void onServerStarted(MinecraftServer server) {
		DataWorldManager.onServerStarted(server);
		DataWorldChunkManager.onServerStarted(server);
		DataPlayerManager.onServerStarted(server);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		DataSaveCoordinatorManager.autosave(server);
	}

	public static void onServerStopping(MinecraftServer server) {
		DataSaveCoordinatorManager.saveAndWait(server);
	}

	public static void savePersistedData(MinecraftServer server) {
		DataSaveCoordinatorManager.saveAndWait(server);
	}
}
