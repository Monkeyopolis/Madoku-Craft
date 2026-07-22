package madoku.craft.mob.system;

import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import net.minecraft.server.MinecraftServer;

/** Orchestrates the mob, entity, regional-difficulty, and world-difficulty subsystems. */
public final class MadokuMobManager {
	private MadokuMobManager() {
	}

	public static void initialize() {
		MadokuMetaDataManager.MainSystemMetadata metadata = MadokuMetaDataManager.mainSystem(
			"mob",
			MadokuMetaDataManager.subSystem("entity",
				MadokuMetaDataManager.group("behaviors"),
				MadokuMetaDataManager.group("components"),
				MadokuMetaDataManager.group("spawn-rules"),
				MadokuMetaDataManager.group("goals"),
				MadokuMetaDataManager.group("config")),
			MadokuMetaDataManager.subSystem("regional-difficulty",
				MadokuMetaDataManager.group("structures"),
				MadokuMetaDataManager.group("biomes"),
				MadokuMetaDataManager.group("time"),
				MadokuMetaDataManager.group("config")),
			MadokuMetaDataManager.subSystem("world-difficulty",
				MadokuMetaDataManager.group("config")),
			MadokuMetaDataManager.subSystem("payload", MadokuMetaDataManager.group("manager")),
			MadokuMetaDataManager.subSystem("config", MadokuMetaDataManager.group("manager"))
		);
		MadokuMetaDataManager.registerMainSystem(metadata);
		MadokuDebugManager.bootstrapMainSystem(metadata);
		MadokuConfigManager.initialize();
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
		MadokuConfigManager.reset();
	}

	public static void broadcastDifficultyNow(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyNow(server);
	}

	public static void broadcastDifficultyIfChanged(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyIfChanged(server);
	}
}
