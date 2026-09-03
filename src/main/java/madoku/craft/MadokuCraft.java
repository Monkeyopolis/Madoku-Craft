package madoku.craft;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.attributes.HealthAPIManager;
import madoku.craft.attributes.HungerAPIManager;
import madoku.craft.block.MadokuBlocks;
import madoku.craft.core.MadokuCoreManager;
import madoku.craft.core.data.ChunkDataAPIManager;
import madoku.craft.core.data.MadokuChunkDataProvider;
import madoku.craft.core.json.JSONAPIManager;
import madoku.craft.core.rarity.RarityAPIManager;
import madoku.craft.core.rarity.MadokuRarityProvider;
import madoku.craft.core.season.SeasonAPIManager;
import madoku.craft.core.sync.SyncConfigAPIManager;
import madoku.craft.core.time.TimeAPIManager;
import madoku.craft.mob.MadokuMobManager;
import madoku.craft.ecosystem.MadokuEcosystemManager;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.farming.MadokuFarmingManager;
import madoku.craft.items.ItemsAPIManager;
import madoku.craft.items.MadokuItemsProvider;
import madoku.craft.levels.MadokuLevelsManager;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import madoku.craft.pet.PetAPIManager;
import madoku.craft.pet.MadokuPetProvider;
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
		ItemsAPIManager.registerProvider(new MadokuItemsProvider());
		ItemsAPIManager.initialize();
		MadokuFarmingManager.initialize();
		MadokuEcosystemManager.initialize();
		MadokuAttributesManager.initialize();
		RarityAPIManager.registerProvider(new MadokuRarityProvider());
		RarityAPIManager.initialize();
		ChunkDataAPIManager.registerProvider(new MadokuChunkDataProvider());
		ChunkDataAPIManager.initialize();
		MadokuLevelsManager.initialize();
		PetAPIManager.registerProvider(new MadokuPetProvider());
		PetAPIManager.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(TimeAPIManager::shouldAllowResettingTime);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SyncConfigAPIManager.resetClientSynchronizedState();
			SeasonAPIManager.reset();
			MadokuEntities.reset();
			MadokuFarmingManager.reset();
			MadokuEcosystemManager.reset();
			ItemsAPIManager.reset();
			ChunkDataAPIManager.reset();
			HealthAPIManager.reset();
			HungerAPIManager.reset();
			MadokuLevelsManager.reset();
			PetAPIManager.reset();
			MadokuCoreManager.reset();
			MadokuCoreManager.loadPersistedData(server);
			MadokuEntities.loadPersistedData(server);
			MadokuFarmingManager.loadPersistedData(server);
			MadokuEcosystemManager.loadPersistedData(server);
			ChunkDataAPIManager.loadPersistedData(server);
			MadokuCoreManager.onServerStarted(server);
			MadokuFarmingManager.onServerStarted(server);
			MadokuEcosystemManager.onServerStarted(server);
			MadokuSmeltingManager.onServerStarted();
			HealthAPIManager.loadPersistedData(server);
			HungerAPIManager.loadPersistedData(server);
			MadokuLevelsManager.loadPersistedData(server);
			PetAPIManager.loadPersistedData(server);
			ItemsAPIManager.onServerStarted(server);
			HungerAPIManager.onServerStarted(server);
			HealthAPIManager.onServerStarted(server);
			MadokuEntities.onServerStarted(server);
			PetAPIManager.onServerStarted(server);
			MadokuMobManager.onServerStarted(server);
			TimeAPIManager.broadcastWorldTimeNow(server);
			MadokuMobManager.broadcastDifficultyNow(server);
			SeasonAPIManager.broadcastWorldSeasonNow(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MadokuCoreManager.onServerStopping(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SyncConfigAPIManager.resetClientSynchronizedState();
			SeasonAPIManager.reset();
			MadokuEntities.reset();
			MadokuFarmingManager.reset();
			MadokuEcosystemManager.reset();
			ItemsAPIManager.reset();
			MadokuCoreManager.reset();
			ChunkDataAPIManager.reset();
			MadokuSmeltingManager.onServerStopped();
			MadokuMobManager.onServerStopped();
			HealthAPIManager.reset();
			HungerAPIManager.reset();
			MadokuLevelsManager.reset();
			PetAPIManager.reset();
			JSONAPIManager.clearRuntimeState();
		});

		ServerTickEvents.START_SERVER_TICK.register(server -> TimeAPIManager.refreshSleepTickIncrement(server));
		ServerTickEvents.START_SERVER_TICK.register(server -> SeasonAPIManager.onServerStartTick(server));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tickIncrement = TimeAPIManager.getCachedSleepTickIncrement();
			TimeAPIManager.advance(server, tickIncrement);
			TimeAPIManager.update(server);
			MadokuCoreManager.onServerTick(server);
			MadokuCoreManager.autosavePersistedData(server);
			SeasonAPIManager.onServerTick(server);
			MadokuMobManager.onServerTick(server);
			MadokuLevelsManager.flushDirtySyncs(server);
			if (MadokuCoreManager.shouldRunWorldSync(server)) {
				TimeAPIManager.broadcastWorldTimeIfChanged(server);
				MadokuMobManager.broadcastDifficultyIfChanged(server);
				SeasonAPIManager.broadcastWorldSeasonIfChanged(server);
				SeasonAPIManager.syncPlayerClimateIfChanged(server);
			}
		});
	}
}
