package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Runtime implementation and orchestrator for shared data services. */
public final class MadokuDataManager {
	private static volatile boolean initialized;

	private MadokuDataManager() { }

	public static void initialize() {
		DataSaveCoordinatorManager.initialize();
		DataSystemsAPIManager.initialize();
		DataWorldAPIManager.initialize();
		DataWorldChunkAPIManager.initialize();
		DataPlayerAPIManager.initialize();
		initialized = true;
	}
	public static void reset() {
		DataWorldAPIManager.reset();
		DataWorldChunkAPIManager.reset();
		DataPlayerAPIManager.reset();
		DataSystemsAPIManager.reset();
		DataSaveCoordinatorManager.reset();
		initialized = false;
	}
	public static boolean isInitialized() { return initialized; }
	public static void loadPersistedData(MinecraftServer server) {
		DataWorldAPIManager.loadPersistedData(server);
		DataWorldChunkAPIManager.loadPersistedData(server);
		DataPlayerAPIManager.loadPersistedData(server);
	}
	public static void onServerStarted(MinecraftServer server) {
		DataWorldAPIManager.onServerStarted(server);
		DataWorldChunkAPIManager.onServerStarted(server);
		DataPlayerAPIManager.onServerStarted(server);
	}
	public static void autosavePersistedData(MinecraftServer server) { DataSaveCoordinatorManager.autosave(server); }
	public static void onServerStopping(MinecraftServer server) { DataSaveCoordinatorManager.saveAndWait(server); }
	public static void savePersistedData(MinecraftServer server) { DataSaveCoordinatorManager.saveAndWait(server); }
}
