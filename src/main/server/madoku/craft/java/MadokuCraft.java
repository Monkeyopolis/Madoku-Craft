package madoku.craft.java;

import madoku.craft.java.attributes.MadokuAttributesManager;
import madoku.craft.java.compat.MadokuCompatManager;
import madoku.craft.java.core.MadokuCoreManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.utility.smelting.MadokuUtilityManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.mob.MadokuMobManager;
import madoku.craft.java.ecosystem.MadokuEcosystemManager;
import madoku.craft.java.farming.MadokuFarmingManager;
import madoku.craft.java.items.ItemsAPIManager;
import madoku.craft.java.items.MadokuItemsManager;
import madoku.craft.java.levels.MadokuLevelsManager;
import madoku.craft.java.pet.PetAPIManager;
import madoku.craft.java.pet.MadokuPetManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class MadokuCraft implements ModInitializer {
	public static final String MOD_ID = "madoku-craft";

	@Override
	public void onInitialize() {
		MadokuCoreManager.initialize();
		MadokuUtilityManager.initialize();
		MadokuMobManager.initialize();
		MadokuItemsManager.initialize();
		MadokuFarmingManager.initialize();
		MadokuEcosystemManager.initialize();
		MadokuAttributesManager.initialize();
		MadokuLevelsManager.initialize();
		MadokuPetManager.initialize();
		MadokuCompatManager.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(TimeAPIManager::shouldAllowResettingTime);

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
			MadokuMobManager.onServerStarted(server);
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
			MadokuMobManager.onServerStopped();
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
			MadokuAttributesManager.onServerTick(server);
			ItemsAPIManager.onServerTick(server);
			PetAPIManager.onServerTick(server);
			MadokuCoreManager.autosavePersistedData(server);
			MadokuMobManager.onServerTick(server);
			MadokuCompatManager.onServerTick(server);
			MadokuLevelsManager.flushDirtySyncs(server);
		});
	}
}
