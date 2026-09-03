package madoku.craft.core.helper;

import net.minecraft.server.MinecraftServer;

/** Runtime API subsystem orchestrating shared helper groups. */
public final class HelperAPIManager {
	private HelperAPIManager() {
	}

	public static void initialize() {
		HelperProjectileAPIManager.initialize();
	}

	public static void reset() {
		HelperProjectileAPIManager.reset();
	}

	public static void onServerStarted(MinecraftServer server) {
		HelperProjectileAPIManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) {
		HelperProjectileAPIManager.tick(server);
	}
}



