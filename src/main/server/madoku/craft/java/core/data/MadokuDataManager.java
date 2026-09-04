package madoku.craft.java.core.data;

import net.minecraft.server.MinecraftServer;

/** Runtime implementation and orchestrator for shared data services. */
public final class MadokuDataManager {
	private static volatile boolean initialized;

	private MadokuDataManager() { }

	public static void initialize() {
		MadokuSavedDataManager.registerProvider(new MadokuSavedDataProvider());
		MadokuSavedDataManager.initialize();
		WorldDataAPIManager.registerProvider(new MadokuWorldDataProvider());
		WorldDataAPIManager.initialize();
		WorldChunkDataAPIManager.registerProvider(new MadokuWorldChunkDataProvider());
		WorldChunkDataAPIManager.initialize();
		PlayerDataAPIManager.registerProvider(new MadokuPlayerDataProvider());
		PlayerDataAPIManager.initialize();
		DataSaveCoordinatorManager.initialize();
		DataSystemsAPIManager.initialize();
		initialized = true;
	}
	public static void reset() {
		WorldDataAPIManager.reset();
		WorldChunkDataAPIManager.reset();
		PlayerDataAPIManager.reset();
		DataSystemsAPIManager.reset();
		DataSaveCoordinatorManager.reset();
		MadokuSavedDataManager.reset();
		initialized = false;
	}
	public static boolean isInitialized() { return initialized; }
	public static void loadPersistedData(MinecraftServer server) {
		WorldDataAPIManager.loadPersistedData(server);
		WorldChunkDataAPIManager.loadPersistedData(server);
		PlayerDataAPIManager.loadPersistedData(server);
	}
	public static void onServerStarted(MinecraftServer server) {
		WorldDataAPIManager.onServerStarted(server);
		WorldChunkDataAPIManager.onServerStarted(server);
		PlayerDataAPIManager.onServerStarted(server);
	}
	public static void autosavePersistedData(MinecraftServer server) { DataSaveCoordinatorManager.autosave(server); }
	public static void onServerStopping(MinecraftServer server) { DataSaveCoordinatorManager.saveAndWait(server); }
	public static void savePersistedData(MinecraftServer server) { DataSaveCoordinatorManager.saveAndWait(server); }
}
