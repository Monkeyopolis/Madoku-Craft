package madoku.craft.mob;

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
			MadokuMetaDataManager.subSystem("entity", MadokuMetaDataManager.entriesFromClass(MobEntityManager.class),
				MadokuMetaDataManager.group("behaviors", MadokuMetaDataManager.entriesFromClass(EntityBehaviorsManager.class)),
				MadokuMetaDataManager.group("components", MadokuMetaDataManager.entriesFromClass(EntityComponentsManager.class)),
				MadokuMetaDataManager.group("spawn-rules", MadokuMetaDataManager.entriesFromClass(EntitySpawnRulesManager.class)),
				MadokuMetaDataManager.group("goals", MadokuMetaDataManager.entriesFromClass(EntityGoalsManager.class)),
				MadokuMetaDataManager.group("config", MadokuMetaDataManager.entriesFromClass(EntityConfigManager.class))),
			MadokuMetaDataManager.subSystem("regional-difficulty", MadokuMetaDataManager.entriesFromClass(MobRegionalDifficultyManager.class),
				MadokuMetaDataManager.group("structures", MadokuMetaDataManager.entriesFromClass(RegionalDifficultyStructuresManager.class)),
				MadokuMetaDataManager.group("biomes", MadokuMetaDataManager.entriesFromClass(RegionalDifficultyBiomesManager.class)),
				MadokuMetaDataManager.group("time", MadokuMetaDataManager.entriesFromClass(RegionalDifficultyTimeManager.class)),
				MadokuMetaDataManager.group("config", MadokuMetaDataManager.entriesFromClass(RegionalDifficultyConfigManager.class))),
			MadokuMetaDataManager.subSystem("world-difficulty", MadokuMetaDataManager.entriesFromClass(MobWorldDifficultyManager.class),
				MadokuMetaDataManager.group("config", MadokuMetaDataManager.entriesFromClass(WorldDifficultyConfigManager.class))),
			MadokuMetaDataManager.subSystem("payload", MadokuMetaDataManager.group("manager", MadokuMetaDataManager.entriesFromClass(MobPayloadManager.class))),
			MadokuMetaDataManager.subSystem("config", MadokuMetaDataManager.group("manager", MadokuMetaDataManager.entriesFromClass(MobConfigManager.class)))
		);
		MadokuMetaDataManager.registerMainSystem(metadata);
		MadokuDebugManager.bootstrapMainSystem(metadata);
		MobConfigManager.initialize();
		MobWorldDifficultyManager.initialize();
		MobRegionalDifficultyManager.initialize();
		MobEntityManager.initialize();
		debugLifecycle("initialize");
	}

	public static void onServerStarted(MinecraftServer server) {
		MobWorldDifficultyManager.onServerStarted(server);
		MobRegionalDifficultyManager.onServerStarted(server);
		MobEntityManager.onServerStarted(server);
		debugLifecycle("server-started");
	}

	public static void onServerTick(MinecraftServer server) {
		MobRegionalDifficultyManager.onServerTick(server);
		MobEntityManager.onServerTick(server);
	}

	public static void onServerStopped() {
		debugLifecycle("server-stopped");
		MobEntityManager.onServerStopped();
		MobRegionalDifficultyManager.onServerStopped();
		MobWorldDifficultyManager.onServerStopped();
		MobConfigManager.reset();
	}

	private static void debugLifecycle(String entry) {
		MadokuDebugManager.event("mob.lifecycle", "mob", "entity", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.subject("MadokuMobManager")
			.log();
	}

	public static void broadcastDifficultyNow(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyNow(server);
	}

	public static void broadcastDifficultyIfChanged(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyIfChanged(server);
	}
}
