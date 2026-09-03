package madoku.craft.ecosystem;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the ecosystem subsystem through its public API contract. */
public final class MadokuEcosystemManager {
	private MadokuEcosystemManager() {
	}

	/** Initializes the shared ecosystem runtime and each ecosystem subsystem. */
	public static void initialize() {
		EcosystemAPIManager.initialize();
		NaturalGrowthAPIManager.registerProvider(new MadokuNaturalGrowthProvider());
		NaturalErosionAPIManager.registerProvider(new MadokuNaturalErosionProvider());
		NaturalDecayAPIManager.registerProvider(new MadokuNaturalDecayProvider());
		NaturalGrowthAPIManager.initialize();
		NaturalErosionAPIManager.initialize();
		NaturalDecayAPIManager.initialize();
		EcosystemAPIManager.refreshSettings();
	}

	/** Resets each ecosystem subsystem and the shared ecosystem runtime. */
	public static void reset() {
		NaturalGrowthAPIManager.reset();
		NaturalErosionAPIManager.reset();
		NaturalDecayAPIManager.reset();
		EcosystemAPIManager.reset();
	}

	public static void onServerTick(MinecraftServer server) { EcosystemAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) { EcosystemAPIManager.onServerStarted(server); }
	public static void loadPersistedData(MinecraftServer server) { EcosystemAPIManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { EcosystemAPIManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { EcosystemAPIManager.savePersistedData(server); }
}
