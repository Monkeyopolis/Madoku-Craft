package madoku.craft;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.attributes.MadokuHealthManager;
import madoku.craft.attributes.MadokuHungerManager;
import madoku.craft.block.MadokuBlocks;
import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.data.MadokuChunkDataManager;
import madoku.craft.core.json.MadokuJSONManager;
import madoku.craft.core.rarity.MadokuRarityManager;
import madoku.craft.core.season.MadokuSeasonManager;
import madoku.craft.core.sync.SyncConfigManager;
import madoku.craft.core.time.MadokuTimeManager;
import madoku.craft.core.time.TimeSleepManager;
import madoku.craft.mob.MadokuMobManager;
import madoku.craft.ecosystem.MadokuEcosystemManager;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.farming.MadokuFarmingManager;
import madoku.craft.items.MadokuItemsManager;
import madoku.craft.levels.MadokuLevelsManager;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import madoku.craft.pet.MadokuPetManager;
import madoku.craft.worldgen.MadokuWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class MadokuCraft implements ModInitializer {
	public static final String MOD_ID = "madoku-craft";

	@Override
	public void onInitialize() {
		MadokuCoreManager.initialize();
		MadokuSmeltingManager.initialize();
		MadokuMobManager.initialize();
		MadokuWorldgen.initialize();
		MadokuEntities.initialize();
		MadokuBlocks.initialize();
		MadokuItemsManager.initialize();
		MadokuFarmingManager.initialize();
		MadokuEcosystemManager.initialize();
		MadokuAttributesManager.initialize();
		MadokuRarityManager.initialize();
		MadokuChunkDataManager.initialize();
		MadokuLevelsManager.initialize();
		MadokuPetManager.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(TimeSleepManager::shouldAllowResettingTime);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SyncConfigManager.resetClientSynchronizedState();
			MadokuSeasonManager.reset();
			MadokuEntities.reset();
			MadokuFarmingManager.reset();
			MadokuEcosystemManager.reset();
			MadokuItemsManager.reset();
			MadokuChunkDataManager.reset();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuLevelsManager.reset();
			MadokuPetManager.reset();
			MadokuCoreManager.reset();
			MadokuCoreManager.loadPersistedData(server);
			MadokuEntities.loadPersistedData(server);
			MadokuFarmingManager.loadPersistedData(server);
			MadokuEcosystemManager.loadPersistedData(server);
			MadokuChunkDataManager.loadPersistedData(server);
			MadokuCoreManager.onServerStarted(server);
			MadokuFarmingManager.onServerStarted(server);
			MadokuEcosystemManager.onServerStarted(server);
			MadokuSmeltingManager.onServerStarted();
			MadokuHealthManager.loadPersistedData(server);
			MadokuHungerManager.loadPersistedData(server);
			MadokuLevelsManager.loadPersistedData(server);
			MadokuPetManager.loadPersistedData(server);
			MadokuItemsManager.onServerStarted(server);
			MadokuHungerManager.onServerStarted(server);
			MadokuHealthManager.onServerStarted(server);
			MadokuEntities.onServerStarted(server);
			MadokuPetManager.onServerStarted(server);
			MadokuMobManager.onServerStarted(server);
			MadokuTimeManager.broadcastWorldTimeNow(server);
			MadokuMobManager.broadcastDifficultyNow(server);
			MadokuSeasonManager.broadcastWorldSeasonNow(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MadokuCoreManager.onServerStopping(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SyncConfigManager.resetClientSynchronizedState();
			MadokuSeasonManager.reset();
			MadokuEntities.reset();
			MadokuFarmingManager.reset();
			MadokuEcosystemManager.reset();
			MadokuItemsManager.reset();
			MadokuCoreManager.reset();
			MadokuChunkDataManager.reset();
			MadokuSmeltingManager.onServerStopped();
			MadokuMobManager.onServerStopped();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuLevelsManager.reset();
			MadokuPetManager.reset();
			MadokuJSONManager.clearRuntimeState();
		});

		ServerTickEvents.START_SERVER_TICK.register(server -> TimeSleepManager.refreshTickIncrement(server));
		ServerTickEvents.START_SERVER_TICK.register(server -> MadokuSeasonManager.onServerStartTick(server));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tickIncrement = TimeSleepManager.getCachedTickIncrement();
			MadokuTimeManager.advance(server, tickIncrement);
			MadokuTimeManager.update(server);
			MadokuCoreManager.onServerTick(server);
			MadokuCoreManager.autosavePersistedData(server);
			MadokuSeasonManager.onServerTick(server);
			MadokuMobManager.onServerTick(server);
			MadokuLevelsManager.flushDirtySyncs(server);
			if (MadokuCoreManager.shouldRunWorldSync(server)) {
				MadokuTimeManager.broadcastWorldTimeIfChanged(server);
				MadokuMobManager.broadcastDifficultyIfChanged(server);
				MadokuSeasonManager.broadcastWorldSeasonIfChanged(server);
				MadokuSeasonManager.syncPlayerClimateIfChanged(server);
			}
		});
	}
}






