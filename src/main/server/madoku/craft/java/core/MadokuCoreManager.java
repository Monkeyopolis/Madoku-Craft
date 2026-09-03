package madoku.craft.java.core;

import madoku.craft.java.core.chunk.ChunkAPIManager;
import madoku.craft.java.core.data.DataAPIManager;
import madoku.craft.java.core.data.MadokuDataProvider;
import madoku.craft.java.core.enchant.EnchantAPIManager;
import madoku.craft.java.core.enchant.MadokuEnchantProvider;
import madoku.craft.java.core.loot.MadokuLootTableProvider;
import madoku.craft.java.core.smithing.MadokuSmithingProvider;
import madoku.craft.java.core.helper.HelperAPIManager;
import madoku.craft.java.core.helper.BlockDropContextAPIManager;
import madoku.craft.java.core.helper.MadokuBlockDropContextProvider;
import madoku.craft.java.core.helper.MadokuHelperProvider;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.loot.LootTableAPIManager;
import madoku.craft.java.core.recipes.RecipesAPIManager;
import madoku.craft.java.core.scheduler.SchedulerAPIManager;
import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.smithing.SmithingAPIManager;
import madoku.craft.java.core.sync.SyncAPIManager;
import madoku.craft.java.core.sync.MadokuSyncProvider;
import madoku.craft.java.core.scheduler.MadokuSchedulerProvider;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.core.time.MadokuTimeProvider;
import madoku.craft.java.ecosystem.MadokuEcosystemManager;

import java.nio.file.Path;

/**
 * Top-level orchestrator for the Madoku Craft core subsystems.
 */
public final class MadokuCoreManager {
	public static final String CORE_FOLDER_NAME = "madoku-craft-core";

	private MadokuCoreManager() {
	}

	/** Initializes the shared core services and all core subsystems. */
	public static void initialize() {
		BlockDropContextAPIManager.registerProvider(new MadokuBlockDropContextProvider());
		HelperAPIManager.registerProvider(new MadokuHelperProvider());
		HelperAPIManager.initialize();
		JSONAPIManager.initialize();
		getCoreRootDirectory();
		DataAPIManager.registerProvider(new MadokuDataProvider());
		DataAPIManager.initialize();
		TimeAPIManager.registerProvider(new MadokuTimeProvider());
		TimeAPIManager.initialize();
		ChunkAPIManager.initialize();
		SeasonAPIManager.initialize();
		SchedulerAPIManager.registerProvider(new MadokuSchedulerProvider());
		SchedulerAPIManager.initialize();
		SyncAPIManager.registerProvider(new MadokuSyncProvider());
		SyncAPIManager.initialize();
		RecipesAPIManager.initialize();
		LootTableAPIManager.registerProvider(new MadokuLootTableProvider());
		LootTableAPIManager.initialize();
		EnchantAPIManager.registerProvider(new MadokuEnchantProvider());
		EnchantAPIManager.initialize();
		SmithingAPIManager.registerProvider(new MadokuSmithingProvider());
		SmithingAPIManager.initialize();
	}

	/** Returns the root directory shared by core subsystems. */
	public static Path getCoreRootDirectory() {
		return JSONAPIManager.getOrCreateGlobalSystemDirectory(CORE_FOLDER_NAME);
	}

	/** Resets runtime state for the core services and all core subsystems. */
	public static void reset() {
		HelperAPIManager.reset();
		DataAPIManager.reset();
		JSONAPIManager.reset();
		TimeAPIManager.reset();
		SeasonAPIManager.reset();
		ChunkAPIManager.reset();
		SchedulerAPIManager.reset();
		SyncAPIManager.reset();
		RecipesAPIManager.reset();
		LootTableAPIManager.reset();
		EnchantAPIManager.reset();
		SmithingAPIManager.reset();
	}

	public static void loadPersistedData(net.minecraft.server.MinecraftServer server) {
		DataAPIManager.loadPersistedData(server);
		ChunkAPIManager.loadPersistedData(server);
		SchedulerAPIManager.loadPersistedData(server);
	}

	public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		HelperAPIManager.onServerStarted(server);
		DataAPIManager.onServerStarted(server);
		TimeAPIManager.onServerStarted(server);
		ChunkAPIManager.onServerStarted(server);
		SeasonAPIManager.onServerStarted(server);
		SyncAPIManager.onServerStarted(server);
		RecipesAPIManager.initialize();
		LootTableAPIManager.initialize();
		EnchantAPIManager.initialize();
		SmithingAPIManager.onServerStarted(server);
	}

	public static void onServerTick(net.minecraft.server.MinecraftServer server) {
		HelperAPIManager.onServerTick(server);
		EnchantAPIManager.onServerTick(server);
		ChunkAPIManager.onServerTick(server);
		MadokuEcosystemManager.onServerTick(server);
		if (TimeAPIManager.isEnabled()) {
			SchedulerAPIManager.onClockTick(server);
		} else {
			SchedulerAPIManager.onServerTick(server);
		}
	}

	public static boolean shouldRunWorldSync(net.minecraft.server.MinecraftServer server) {
		return SyncAPIManager.shouldRunWorldSync(server);
	}

	public static void autosavePersistedData(net.minecraft.server.MinecraftServer server) {
		DataAPIManager.autosavePersistedData(server);
		ChunkAPIManager.autosavePersistedData(server);
	}

	public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
		DataAPIManager.onServerStopping(server);
		TimeAPIManager.onServerStopping(server);
		ChunkAPIManager.onServerStopping(server);
		SyncAPIManager.onServerStopping(server);
	}

	public static void savePersistedData(net.minecraft.server.MinecraftServer server) {
		DataAPIManager.savePersistedData(server);
		ChunkAPIManager.savePersistedData(server);
	}
}
