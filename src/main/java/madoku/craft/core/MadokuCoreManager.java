package madoku.craft.core;

import madoku.craft.core.chunk.ChunkAPIManager;
import madoku.craft.core.data.DataAPIManager;
import madoku.craft.core.enchant.EnchantAPIManager;
import madoku.craft.core.helper.HelperAPIManager;
import madoku.craft.core.json.JSONAPIManager;
import madoku.craft.core.loot.LootTableAPIManager;
import madoku.craft.core.recipes.RecipesAPIManager;
import madoku.craft.core.scheduler.SchedulerAPIManager;
import madoku.craft.core.season.SeasonAPIManager;
import madoku.craft.core.smithing.SmithingAPIManager;
import madoku.craft.core.sync.SyncAPIManager;
import madoku.craft.core.time.TimeAPIManager;
import madoku.craft.ecosystem.EcosystemAPIManager;

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
		HelperAPIManager.initialize();
		JSONAPIManager.initialize();
		getCoreRootDirectory();
		DataAPIManager.initialize();
		TimeAPIManager.initialize();
		ChunkAPIManager.initialize();
		SeasonAPIManager.initialize();
		SchedulerAPIManager.initialize();
		SyncAPIManager.initialize();
		RecipesAPIManager.initialize();
		LootTableAPIManager.initialize();
		EnchantAPIManager.initialize();
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
		EcosystemAPIManager.onServerTick(server);
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
