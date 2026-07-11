package madoku.craft;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.time.SleepManager;
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
import madoku.craft.attributes.luck.BlockTrackingManager;
import madoku.craft.mob.system.MadokuMobManager;
import madoku.craft.network.HungerHudSync;
import madoku.craft.network.ItemProfileSync;
import madoku.craft.network.PetAbilityHudSync;
import madoku.craft.network.PetSoundStateSync;
import madoku.craft.network.WorldDifficultySync;
import madoku.craft.network.WorldSeasonSync;
import madoku.craft.network.WorldTimeSync;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.loot.system.MadokuLootTableManager;
import madoku.craft.recipe.system.MadokuRecipe;
import madoku.craft.scheduler.SchedulerManagerSystem;
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
		MadokuRecipe.initialize();
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
		BlockTrackingManager.initialize();
		MadokuLevels.initialize();
		PlayerEntitiesSystem.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(SleepManager::shouldAllowResettingTime);
		WorldTimeSync.initialize();
		WorldDifficultySync.initialize();
		WorldSeasonSync.initialize();
		ItemProfileSync.initialize();
		HungerHudSync.initialize();
		PetAbilityHudSync.initialize();
		PetSoundStateSync.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuSeasonManager.reset();
			MadokuEntities.reset();
			MadokuFarming.reset();
			MadokuEcosystemManager.reset();
			MadokuItem.reset();
			MadokuItemStack.reset();
			BlockTrackingManager.reset();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuLevels.reset();
			PlayerEntitiesSystem.reset();
			MadokuAPIManager.reset();
			SchedulerManagerSystem.reset();
			SchedulerManagerSystem.loadPersistedData(server);
			MadokuAPIManager.loadPersistedData(server);
			MadokuEntities.loadPersistedData(server);
			MadokuFarming.loadPersistedData(server);
			MadokuEcosystemManager.loadPersistedData(server);
			BlockTrackingManager.loadPersistedData(server);
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
			WorldTimeSync.reset();
			WorldDifficultySync.reset();
			WorldSeasonSync.reset();
			WorldTimeSync.broadcastNow(server);
			WorldDifficultySync.broadcastNow(server);
			WorldSeasonSync.broadcastNow(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MadokuEcosystemManager.onServerStopping(server);
			MadokuAPIManager.onServerStopping(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuEntities.savePersistedData(server);
			MadokuFarming.savePersistedData(server);
			MadokuEcosystemManager.savePersistedData(server);
			MadokuAPIManager.savePersistedData(server);
			SchedulerManagerSystem.savePersistedData(server);
			BlockTrackingManager.savePersistedData(server);
			MadokuHealthManager.savePersistedData(server);
			MadokuHungerManager.savePersistedData(server);
			MadokuOxygenManager.savePersistedData(server);
			MadokuLevels.savePersistedData(server);
			PlayerEntitiesSystem.savePersistedData(server);
			MadokuItemStack.savePersistedData(server);
			MadokuSeasonManager.reset();
			MadokuEntities.reset();
			MadokuFarming.reset();
			MadokuEcosystemManager.reset();
			MadokuItem.reset();
			MadokuAPIManager.reset();
			SchedulerManagerSystem.reset();
			BlockTrackingManager.reset();
			MadokuSmeltingManager.onServerStopped();
			MadokuRegionalDifficultyManager.onServerStopped();
			MadokuMobManager.onServerStopped();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuLevels.reset();
			PlayerEntitiesSystem.reset();
			MadokuItemStack.reset();
			WorldTimeSync.reset();
			WorldDifficultySync.reset();
			WorldSeasonSync.reset();
			MadokuJSONManager.clearRuntimeState();
		});

		ServerTickEvents.START_SERVER_TICK.register(SleepManager::refreshTickIncrement);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tickIncrement = SleepManager.getCachedTickIncrement();
			MadokuTimeManager.advance(server, tickIncrement);
			MadokuTimeManager.update(server);
			if (MadokuTimeManager.isEnabled()) {
				SchedulerManagerSystem.onClockTick(server);
			} else {
				SchedulerManagerSystem.onServerTick(server);
			}
			MadokuFarming.onServerTickIncrement(server, tickIncrement);
			SchedulerManagerSystem.autosavePersistedData(server);
			MadokuAPIManager.autosavePersistedData(server);
			MadokuHealthManager.autosavePersistedData(server);
			MadokuHungerManager.autosavePersistedData(server);
			MadokuEntities.autosavePersistedData(server);
			MadokuFarming.autosavePersistedData(server);
			MadokuEcosystemManager.autosavePersistedData(server);
			BlockTrackingManager.autosavePersistedData(server);
			MadokuOxygenManager.autosavePersistedData(server);
			MadokuLevels.autosavePersistedData(server);
			PlayerEntitiesSystem.autosavePersistedData(server);
			MadokuItemStack.autosavePersistedData(server);
			MadokuSeasonManager.onServerTick(server);
			MadokuRegionalDifficultyManager.onServerTick(server);
			MadokuMobManager.onServerTick(server);
			MadokuLevels.flushDirtySyncs(server);
			WorldTimeSync.broadcastIfChanged(server);
			WorldDifficultySync.broadcastIfChanged(server);
			WorldSeasonSync.broadcastIfChanged(server);
		});
	}
}






