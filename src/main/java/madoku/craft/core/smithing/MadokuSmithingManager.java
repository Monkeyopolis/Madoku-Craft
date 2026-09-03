package madoku.craft.core.smithing;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the smithing subsystem through its public API contract. */
public final class MadokuSmithingManager {
	private MadokuSmithingManager() {
	}

	public static void initialize() { SmithingAPIManager.initialize(); }
	public static void reset() { SmithingAPIManager.reset(); }
	public static void onServerStarted(MinecraftServer server) { SmithingAPIManager.onServerStarted(server); }
}
