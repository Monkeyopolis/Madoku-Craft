package madoku.craft.java;

import madoku.craft.java.attributes.MadokuAttributesManager;
import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.HungerAPIManager;
import madoku.craft.java.block.MadokuBlocks;
import madoku.craft.java.core.MadokuCoreManager;
import madoku.craft.java.core.data.ChunkDataAPIManager;
import madoku.craft.java.core.data.MadokuChunkDataProvider;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.rarity.MadokuRarityProvider;
import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.sync.SyncConfigAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.mob.MadokuMobManager;
import madoku.craft.java.ecosystem.MadokuEcosystemManager;
import madoku.craft.java.entity.MadokuEntities;
import madoku.craft.java.farming.MadokuFarmingManager;
import madoku.craft.java.items.ItemsAPIManager;
import madoku.craft.java.items.MadokuItemsProvider;
import madoku.craft.java.levels.MadokuLevelsManager;
import madoku.craft.java.pet.PetAPIManager;
import madoku.craft.java.utility.smelting.MadokuUtilityManager;
import madoku.craft.java.pet.MadokuPetProvider;
import madoku.craft.java.worldgen.MadokuWorldgen;
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
			MadokuUtilityManager.onServerStarted(server);
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
			MadokuUtilityManager.onServerStopped(server);
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
			MadokuAttributesManager.onServerTick(server);
			ItemsAPIManager.onServerTick(server);
			MadokuEntities.onServerTick(server);
			PetAPIManager.onServerTick(server);
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
