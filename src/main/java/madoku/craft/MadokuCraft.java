package madoku.craft;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.time.TimeSleepManager;
import madoku.craft.block.MadokuBlocks;
import madoku.craft.composter.system.MadokuComposter;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.difficulty.system.MadokuRegionalDifficultyManager;
import madoku.craft.ecosystem.MadokuEcosystemManager;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.levels.MadokuLevels;
import madoku.craft.api.data.MadokuChunkDataManager;
import madoku.craft.mob.system.MadokuMobManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.loot.system.MadokuLootTableManager;
import madoku.craft.api.season.MadokuSeasonManager;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.worldgen.MadokuWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class MadokuCraft implements ModInitializer {
	public static final String MOD_ID = "madoku-craft";

	@Override
	public void onInitialize() {
		MadokuAPIManager.initialize();
		MadokuSmeltingManager.initialize();
		MadokuLootTableManager.initialize();
		MadokuRegionalDifficultyManager.initialize();
		MadokuSeasonManager.initialize();
		MadokuWorldgen.initialize();
		MadokuEntities.initialize();
		MadokuBlocks.initialize();
		MadokuItem.initialize();
		MadokuComposter.initialize();
		MadokuFarming.initialize();
		MadokuEcosystemManager.initialize();
		MadokuMobManager.initialize();
		MadokuRarity.initialize();
		MadokuItemStack.initialize();
		MadokuAttributesManager.initialize();
		MadokuChunkDataManager.initialize();
		MadokuLevels.initialize();
		PlayerEntitiesSystem.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(TimeSleepManager::shouldAllowResettingTime);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuSeasonManager.reset();
			MadokuEntities.reset();
			MadokuFarming.reset();
			MadokuEcosystemManager.reset();
			MadokuItem.reset();
			MadokuItemStack.reset();
			MadokuChunkDataManager.reset();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuLevels.reset();
			PlayerEntitiesSystem.reset();
			MadokuAPIManager.reset();
			MadokuAPIManager.loadPersistedData(server);
			MadokuEntities.loadPersistedData(server);
			MadokuFarming.loadPersistedData(server);
			MadokuEcosystemManager.loadPersistedData(server);
			MadokuChunkDataManager.loadPersistedData(server);
			MadokuAPIManager.onServerStarted(server);
			MadokuSeasonManager.onServerStarted(server);
			MadokuFarming.onServerStarted(server);
			MadokuEcosystemManager.onServerStarted(server);
			MadokuSmeltingManager.onServerStarted();
			MadokuHealthManager.loadPersistedData(server);
			MadokuHungerManager.loadPersistedData(server);
			MadokuOxygenManager.loadPersistedData(server);
			MadokuLevels.loadPersistedData(server);
			PlayerEntitiesSystem.loadPersistedData(server);
			MadokuItemStack.loadPersistedData(server);
			MadokuItem.onServerStarted(server);
			MadokuHungerManager.onServerStarted(server);
			MadokuHealthManager.onServerStarted(server);
			MadokuOxygenManager.onServerStarted(server);
			MadokuEntities.onServerStarted(server);
			PlayerEntitiesSystem.onServerStarted(server);
			MadokuMobManager.onServerStarted(server);
			MadokuRegionalDifficultyManager.onServerStarted(server);
			MadokuTimeManager.broadcastWorldTimeNow(server);
			MadokuRegionalDifficultyManager.broadcastDifficultyNow(server);
			MadokuSeasonManager.broadcastWorldSeasonNow(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MadokuAPIManager.onServerStopping(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuSeasonManager.reset();
			MadokuEntities.reset();
			MadokuFarming.reset();
			MadokuEcosystemManager.reset();
			MadokuItem.reset();
			MadokuAPIManager.reset();
			MadokuChunkDataManager.reset();
			MadokuSmeltingManager.onServerStopped();
			MadokuRegionalDifficultyManager.onServerStopped();
			MadokuMobManager.onServerStopped();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuLevels.reset();
			PlayerEntitiesSystem.reset();
			MadokuItemStack.reset();
			MadokuJSONManager.clearRuntimeState();
		});

		ServerTickEvents.START_SERVER_TICK.register(TimeSleepManager::refreshTickIncrement);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tickIncrement = TimeSleepManager.getCachedTickIncrement();
			MadokuTimeManager.advance(server, tickIncrement);
			MadokuTimeManager.update(server);
			MadokuAPIManager.onServerTick(server);
			MadokuFarming.onServerTickIncrement(server, tickIncrement);
			MadokuAPIManager.autosavePersistedData(server);
			MadokuSeasonManager.onServerTick(server);
			MadokuRegionalDifficultyManager.onServerTick(server);
			MadokuMobManager.onServerTick(server);
			MadokuLevels.flushDirtySyncs(server);
			if (MadokuAPIManager.shouldRunWorldSync(server)) {
				MadokuTimeManager.broadcastWorldTimeIfChanged(server);
				MadokuRegionalDifficultyManager.broadcastDifficultyIfChanged(server);
				MadokuSeasonManager.broadcastWorldSeasonIfChanged(server);
			}
		});
	}
}






