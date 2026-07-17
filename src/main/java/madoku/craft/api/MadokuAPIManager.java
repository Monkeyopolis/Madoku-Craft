package madoku.craft.api;

import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.data.MadokuDataManager;
import madoku.craft.api.scheduler.MadokuSchedulerManager;
import madoku.craft.api.sync.MadokuSyncManager;
import madoku.craft.api.recipes.MadokuRecipesManager;

import java.nio.file.Path;

public final class MadokuAPIManager {
	public static final String API_FOLDER_NAME = "madoku-craft-api";

	private MadokuAPIManager() {
	}

	public static void initialize() {
		MadokuJSONManager.initialize();
		MadokuMetaDataManager.initialize();
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.API);
		getApiRootDirectory();
		MadokuDataManager.initialize();
		MadokuDebugManager.initialize();
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.API);
		MadokuTimeManager.initialize();
		MadokuChunkManager.initialize();
		MadokuSeasonManager.initialize();
		MadokuSchedulerManager.initialize();
		MadokuSyncManager.initialize();
		MadokuRecipesManager.initialize();
	}

	public static Path getApiRootDirectory() {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(API_FOLDER_NAME);
	}

	public static void reset() {
		MadokuDataManager.reset();
		MadokuJSONManager.reset();
		MadokuTimeManager.reset();
		MadokuSeasonManager.reset();
		MadokuDebugManager.resetSession();
		MadokuChunkManager.reset();
		MadokuSchedulerManager.reset();
		MadokuSyncManager.reset();
		MadokuRecipesManager.reset();
	}

	public static void loadPersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuDataManager.loadPersistedData(server);
		MadokuChunkManager.loadPersistedData(server);
		MadokuSchedulerManager.loadPersistedData(server);
	}

	public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		MadokuDataManager.onServerStarted(server);
		MadokuTimeManager.onServerStarted(server);
		MadokuChunkManager.onServerStarted(server);
		MadokuSeasonManager.onServerStarted(server);
		MadokuSyncManager.onServerStarted(server);
	}

	public static void onServerTick(net.minecraft.server.MinecraftServer server) {
		if (MadokuTimeManager.isEnabled()) {
			MadokuSchedulerManager.onClockTick(server);
		} else {
			MadokuSchedulerManager.onServerTick(server);
		}
	}

	public static boolean shouldRunWorldSync(net.minecraft.server.MinecraftServer server) {
		return MadokuSyncManager.shouldRunWorldSync(server);
	}

	public static void autosavePersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuDataManager.autosavePersistedData(server);
		MadokuChunkManager.autosavePersistedData(server);
	}

	public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
		MadokuDataManager.onServerStopping(server);
		MadokuTimeManager.onServerStopping(server);
		MadokuChunkManager.onServerStopping(server);
		MadokuSyncManager.onServerStopping(server);
	}

	public static void savePersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuDataManager.savePersistedData(server);
		MadokuChunkManager.savePersistedData(server);
	}
}
