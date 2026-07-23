package madoku.craft.mob;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the mob, entity, regional-difficulty, and world-difficulty subsystems. */
public final class MadokuMobManager {
	private MadokuMobManager() {
	}

	public static void initialize() {
		MobConfigManager.initialize();
		MobWorldDifficultyManager.initialize();
		MobRegionalDifficultyManager.initialize();
		MobEntityManager.initialize();
	}

	public static void onServerStarted(MinecraftServer server) {
		MobWorldDifficultyManager.onServerStarted(server);
		MobRegionalDifficultyManager.onServerStarted(server);
		MobEntityManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) {
		MobRegionalDifficultyManager.onServerTick(server);
		MobEntityManager.onServerTick(server);
	}

	public static void onServerStopped() {
		MobEntityManager.onServerStopped();
		MobRegionalDifficultyManager.onServerStopped();
		MobWorldDifficultyManager.onServerStopped();
		MobConfigManager.reset();
	}

	public static void broadcastDifficultyNow(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyNow(server);
	}

	public static void broadcastDifficultyIfChanged(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyIfChanged(server);
	}
}
