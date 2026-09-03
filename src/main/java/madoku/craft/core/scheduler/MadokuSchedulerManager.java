package madoku.craft.core.scheduler;

import net.minecraft.server.MinecraftServer;

/** Orchestrates scheduler services through their public API contract. */
public final class MadokuSchedulerManager {
	private MadokuSchedulerManager() {
	}

	public static void initialize() { SchedulerAPIManager.initialize(); }
	public static void reset() { SchedulerAPIManager.reset(); }
	public static void loadPersistedData(MinecraftServer server) { SchedulerAPIManager.loadPersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { SchedulerAPIManager.savePersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { SchedulerAPIManager.autosavePersistedData(server); }
	public static void onClockTick(MinecraftServer server) { SchedulerAPIManager.onClockTick(server); }
	public static void onServerTick(MinecraftServer server) { SchedulerAPIManager.onServerTick(server); }
}
