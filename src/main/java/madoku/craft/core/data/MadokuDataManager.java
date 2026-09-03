package madoku.craft.core.data;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the data subsystem through its public API contract. */
public final class MadokuDataManager {
	private MadokuDataManager() {
	}

	public static void initialize() { DataAPIManager.initialize(); }
	public static void reset() { DataAPIManager.reset(); }
	public static boolean isInitialized() { return DataAPIManager.isInitialized(); }
	public static void loadPersistedData(MinecraftServer server) { DataAPIManager.loadPersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { DataAPIManager.onServerStarted(server); }
	public static void autosavePersistedData(MinecraftServer server) { DataAPIManager.autosavePersistedData(server); }
	public static void onServerStopping(MinecraftServer server) { DataAPIManager.onServerStopping(server); }
	public static void savePersistedData(MinecraftServer server) { DataAPIManager.savePersistedData(server); }
}
