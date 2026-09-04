package madoku.craft.java.utility.smelting;

import madoku.craft.java.core.scheduler.SchedulerAPIManager;
import net.minecraft.server.MinecraftServer;

/** Orchestrates Madoku Utility and its runtime/configuration subsystems. */
public final class MadokuUtilityManager {
	private MadokuUtilityManager() {
	}

	public static void initialize() {
		UtilityConfigManager.initialize();
		SmeltingAPIManager.registerProvider(new MadokuSmeltingProvider());
		MadokuSmeltingManager.initialize();
	}

	public static void reset() {
		MadokuSmeltingManager.reset();
		SmeltingAPIManager.unregisterProvider();
		UtilityConfigManager.reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		MadokuSmeltingManager.onServerStarted(server);
	}

	public static void onServerStopped(MinecraftServer server) {
		MadokuSmeltingManager.onServerStopped(server);
	}

	/** Shared adaptive scheduling helper for utility runtime systems. */
	public static long resolveAdaptiveInterval(MinecraftServer server, String owner, long minimum, long maximum) {
		return SchedulerAPIManager.resolveAdaptiveDelayTicks(server, owner, minimum, maximum);
	}

	public static void clearAdaptiveInterval(String owner) {
		SchedulerAPIManager.clearAdaptiveDelayState(owner);
	}
}
