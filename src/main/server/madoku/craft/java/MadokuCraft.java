package madoku.craft.java;

import madoku.craft.java.attributes.MadokuAttributesInitializer;
import madoku.craft.java.attributes.MadokuAttributesManager;
import madoku.craft.java.compat.MadokuCompatInitializer;
import madoku.craft.java.compat.MadokuCompatManager;
import madoku.craft.java.core.MadokuCoreInitializer;
import madoku.craft.java.core.MadokuCoreManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.module.MadokuModuleLifecycleManager;
import madoku.craft.java.ecosystem.MadokuEcosystemInitializer;
import madoku.craft.java.ecosystem.MadokuEcosystemManager;
import madoku.craft.java.farming.MadokuFarmingInitializer;
import madoku.craft.java.farming.MadokuFarmingManager;
import madoku.craft.java.items.MadokuItemsInitializer;
import madoku.craft.java.items.ItemsAPIManager;
import madoku.craft.java.levels.MadokuLevelsInitializer;
import madoku.craft.java.levels.MadokuLevelsManager;
import madoku.craft.java.mob.MadokuMobsInitializer;
import madoku.craft.java.mob.MobAPIManager;
import madoku.craft.java.pet.MadokuPetsInitializer;
import madoku.craft.java.pet.PetAPIManager;
import madoku.craft.java.utility.MadokuUtilityInitializer;
import madoku.craft.java.utility.smelting.MadokuUtilityManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class MadokuCraft implements ModInitializer {
	public static final String MOD_ID = "madoku-craft";

	@Override
	public void onInitialize() {
		MadokuModuleLifecycleManager lifecycle = MadokuModuleLifecycleManager.server();
		lifecycle.register(new MadokuCoreInitializer());
		lifecycle.register(new MadokuUtilityInitializer());
		lifecycle.register(new MadokuMobsInitializer());
		lifecycle.register(new MadokuItemsInitializer());
		lifecycle.register(new MadokuFarmingInitializer());
		lifecycle.register(new MadokuEcosystemInitializer());
		lifecycle.register(new MadokuAttributesInitializer());
		lifecycle.register(new MadokuLevelsInitializer());
		lifecycle.register(new MadokuPetsInitializer());
		lifecycle.register(new MadokuCompatInitializer());

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuFarmingManager.reset();
			MadokuEcosystemManager.reset();
			MadokuUtilityManager.reset();
			ItemsAPIManager.reset();
			MadokuAttributesManager.reset();
			MadokuLevelsManager.reset();
			PetAPIManager.reset();
			MadokuCoreManager.reset();
			MadokuCoreManager.loadPersistedData(server);
			MadokuCoreManager.onServerStarted(server);
			MadokuFarmingManager.loadPersistedData(server);
			MadokuEcosystemManager.loadPersistedData(server);
			MadokuFarmingManager.onServerStarted(server);
			MadokuEcosystemManager.onServerStarted(server);
			MadokuUtilityManager.onServerStarted(server);
			MadokuAttributesManager.loadPersistedData(server);
			MadokuLevelsManager.loadPersistedData(server);
			PetAPIManager.loadPersistedData(server);
			ItemsAPIManager.onServerStarted(server);
			MadokuAttributesManager.onServerStarted(server);
			PetAPIManager.onServerStarted(server);
			MobAPIManager.onServerStarted(server);
			MadokuCompatManager.onServerStarted(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MadokuCoreManager.onServerStopping(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuFarmingManager.reset();
			MadokuEcosystemManager.reset();
			ItemsAPIManager.reset();
			MadokuCoreManager.reset();
			MadokuUtilityManager.onServerStopped(server);
			MadokuUtilityManager.reset();
			MobAPIManager.onServerStopped();
			MadokuAttributesManager.reset();
			MadokuLevelsManager.reset();
			PetAPIManager.reset();
			MadokuCompatManager.onServerStopped();
			JSONAPIManager.clearRuntimeState();
		});

		ServerTickEvents.START_SERVER_TICK.register(MadokuCoreManager::onServerStartTick);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MadokuCoreManager.onServerTick(server);
			MadokuEcosystemManager.onServerTick(server);
			MadokuFarmingManager.onServerTick(server);
			MadokuAttributesManager.onServerTick(server);
			ItemsAPIManager.onServerTick(server);
			PetAPIManager.onServerTick(server);
			MadokuCoreManager.autosavePersistedData(server);
			MobAPIManager.onServerTick(server);
			MadokuCompatManager.onServerTick(server);
			MadokuLevelsManager.flushDirtySyncs(server);
		});
	}
}
