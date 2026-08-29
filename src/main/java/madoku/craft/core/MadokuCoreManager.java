package madoku.craft.core;

import madoku.craft.core.chunk.MadokuChunkManager;
import madoku.craft.core.data.MadokuDataManager;
import madoku.craft.core.enchant.MadokuEnchantManager;
import madoku.craft.core.smithing.MadokuSmithingManager;
import madoku.craft.core.helper.MadokuHelperManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.loot.MadokuLootTableManager;
import madoku.craft.core.recipes.MadokuRecipesManager;
import madoku.craft.core.scheduler.MadokuSchedulerManager;
import madoku.craft.core.season.MadokuSeasonManager;
import madoku.craft.core.sync.MadokuSyncManager;
import madoku.craft.core.time.MadokuTimeManager;
import madoku.craft.ecosystem.MadokuEcosystemManager;

import java.nio.file.Path;

public final class MadokuCoreManager {
	public static final String API_FOLDER_NAME = "madoku-craft-api";

	private MadokuCoreManager() {
	}

	public static void initialize() {
		MadokuHelperManager.initialize();
		MadokuJSONManager.initialize();
		getApiRootDirectory();
		MadokuDataManager.initialize();
		MadokuTimeManager.initialize();
		MadokuChunkManager.initialize();
		MadokuSeasonManager.initialize();
		MadokuSchedulerManager.initialize();
		MadokuSyncManager.initialize();
		MadokuRecipesManager.initialize();
		MadokuLootTableManager.initialize();
		MadokuEnchantManager.initialize();
		MadokuSmithingManager.initialize();
	}

	public static Path getApiRootDirectory() {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(API_FOLDER_NAME);
	}

	public static void reset() {
		MadokuHelperManager.reset();
		MadokuDataManager.reset();
		MadokuJSONManager.reset();
		MadokuTimeManager.reset();
		MadokuSeasonManager.reset();
		MadokuChunkManager.reset();
		MadokuSchedulerManager.reset();
		MadokuSyncManager.reset();
		MadokuRecipesManager.reset();
		MadokuLootTableManager.reset();
		MadokuEnchantManager.reset();
		MadokuSmithingManager.reset();
	}

	public static void loadPersistedData(net.minecraft.server.MinecraftServer server) {
		MadokuDataManager.loadPersistedData(server);
		MadokuChunkManager.loadPersistedData(server);
		MadokuSchedulerManager.loadPersistedData(server);
	}

	public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		MadokuHelperManager.onServerStarted(server);
		MadokuDataManager.onServerStarted(server);
		MadokuTimeManager.onServerStarted(server);
		MadokuChunkManager.onServerStarted(server);
		MadokuSeasonManager.onServerStarted(server);
		MadokuSyncManager.onServerStarted(server);
		MadokuRecipesManager.initialize();
		MadokuLootTableManager.initialize();
		MadokuEnchantManager.initialize();
		MadokuSmithingManager.onServerStarted(server);
	}

	public static void onServerTick(net.minecraft.server.MinecraftServer server) {
		MadokuHelperManager.onServerTick(server);
		MadokuEnchantManager.onServerTick(server);
		MadokuChunkManager.onServerTick(server);
		MadokuEcosystemManager.onServerTick(server);
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
