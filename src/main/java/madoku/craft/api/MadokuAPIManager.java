package madoku.craft.api;

import madoku.craft.api.chunk.MadokuChunkManager;
import madoku.craft.api.debug.MadokuDebugManager;
import madoku.craft.api.metadata.MadokuMetaDataManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.config.JsonManagerSystem;

import java.nio.file.Path;

public final class MadokuAPIManager {
	public static final String API_FOLDER_NAME = "madoku-craft-api";

	private MadokuAPIManager() {
	}

	public static void initialize() {
		MadokuMetaDataManager.initialize();
		MadokuMetaDataManager.registerMainSystem(MadokuMetaDataManager.API);
		getApiRootDirectory();
		MadokuDebugManager.initialize();
		MadokuDebugManager.bootstrapMainSystem(MadokuMetaDataManager.API);
		MadokuTimeManager.initialize();
		MadokuChunkManager.initialize();
		MadokuSeasonManager.initialize();
	}

	public static Path getApiRootDirectory() {
		return JsonManagerSystem.getOrCreateGlobalSystemDirectory(API_FOLDER_NAME);
	}

	public static void reset() {
		MadokuTimeManager.reset();
		MadokuSeasonManager.reset();
		MadokuDebugManager.resetSession();
		MadokuChunkManager.reset();
	}

	public static void loadPersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuChunkManager.loadPersistedData(server);
	}

	public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		MadokuTimeManager.onServerStarted(server);
		MadokuChunkManager.onServerStarted(server);
		MadokuSeasonManager.onServerStarted(server);
	}

	public static void autosavePersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuChunkManager.autosavePersistedData(server);
	}

	public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
		MadokuTimeManager.onServerStopping(server);
		MadokuChunkManager.onServerStopping(server);
	}

	public static void savePersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuChunkManager.savePersistedData(server);
	}
}
