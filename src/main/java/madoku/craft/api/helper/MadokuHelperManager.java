package madoku.craft.api.helper;

import net.minecraft.server.MinecraftServer;

/** Runtime API subsystem orchestrating shared helper groups. */
public final class MadokuHelperManager {
	private MadokuHelperManager() {
	}

	public static void initialize() {
		HelperProjectileManager.initialize();
	}

	public static void reset() {
		HelperProjectileManager.reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		HelperProjectileManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) {
		HelperProjectileManager.tick(server);
	}
}
