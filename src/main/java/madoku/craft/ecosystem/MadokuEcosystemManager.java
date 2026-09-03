package madoku.craft.ecosystem;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the ecosystem subsystem through its public API contract. */
public final class MadokuEcosystemManager {
	private MadokuEcosystemManager() {
	}

	public static void initialize() { EcosystemAPIManager.initialize(); }
	public static void reset() { EcosystemAPIManager.reset(); }
	public static void onServerTick(MinecraftServer server) { EcosystemAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) { EcosystemAPIManager.onServerStarted(server); }
	public static void loadPersistedData(MinecraftServer server) { EcosystemAPIManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { EcosystemAPIManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { EcosystemAPIManager.savePersistedData(server); }
}
